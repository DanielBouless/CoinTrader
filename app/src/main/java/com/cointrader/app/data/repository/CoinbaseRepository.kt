package com.cointrader.app.data.repository

import com.cointrader.app.data.api.CoinbaseService
import com.cointrader.app.data.api.models.Candle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.max

// Top crypto pairs to scan by default
val CRYPTO_PAIRS = listOf(
    "BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD", "BNB-USD",
    "DOGE-USD", "ADA-USD", "AVAX-USD", "MATIC-USD", "DOT-USD",
    "LINK-USD", "UNI-USD", "LTC-USD", "BCH-USD", "ATOM-USD",
    "FIL-USD", "NEAR-USD", "ICP-USD", "APT-USD", "ARB-USD",
    "OP-USD", "SHIB-USD", "TRX-USD", "XLM-USD", "ETC-USD"
)

data class WilliamsDmiSignal(
    val symbol: String,
    val currentPrice: Double,
    val williamsR: Double,
    val plusDI: Double,
    val minusDI: Double,
    val isArmed: Boolean,
    val isEntry: Boolean
)

data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val signals: List<WilliamsDmiSignal>
)

@Singleton
class CoinbaseRepository @Inject constructor(
    private val coinbaseService: CoinbaseService
) {
    private val semaphore = Semaphore(5)

    fun scanWilliamsDmi(
        pairs: List<String> = CRYPTO_PAIRS,
        wrPeriod: Int = 14,
        dmiPeriod: Int = 14,
        lowerThreshold: Double = -80.0
    ): Flow<ScanProgress> = flow {
        val signals = mutableListOf<WilliamsDmiSignal>()
        var scanned = 0

        coroutineScope {
            val jobs = pairs.map { symbol ->
                async {
                    semaphore.withPermit {
                        runCatching { fetchAndAnalyze(symbol, wrPeriod, dmiPeriod, lowerThreshold) }
                            .getOrNull()
                    }
                }
            }
            for (job in jobs) {
                val result = job.await()
                scanned++
                if (result != null) signals.add(result)
                emit(ScanProgress(scanned, pairs.size, signals.toList()))
            }
        }
    }

    private suspend fun fetchAndAnalyze(
        symbol: String,
        wrPeriod: Int,
        dmiPeriod: Int,
        lowerThreshold: Double
    ): WilliamsDmiSignal? {
        val end = Instant.now()
        val start = end.minus(365, ChronoUnit.DAYS)

        val response = coinbaseService.getCandles(
            productId = symbol,
            start = start.epochSecond.toString(),
            end = end.epochSecond.toString(),
            granularity = "ONE_DAY"
        )

        val candles = response.candles
            .filter { it.closeDouble > 0 }
            .sortedBy { it.start.toLongOrNull() ?: 0L }

        if (candles.size < max(wrPeriod, dmiPeriod) + 5) return null

        val wr = calculateWilliamsR(candles, wrPeriod)
        val (plusDI, minusDI) = calculateDMI(candles, dmiPeriod)

        if (wr.isEmpty() || plusDI.isEmpty() || minusDI.isEmpty()) return null

        val lastWR = wr.last()
        val lastPlusDI = plusDI.last()
        val lastMinusDI = minusDI.last()
        val prevPlusDI = if (plusDI.size >= 2) plusDI[plusDI.size - 2] else lastPlusDI
        val prevMinusDI = if (minusDI.size >= 2) minusDI[minusDI.size - 2] else lastMinusDI

        // Stage 1: armed when W%R touches lowerThreshold within last 3 candles
        val recentWR = wr.takeLast(3)
        val isArmed = recentWR.any { it <= lowerThreshold }

        // Stage 2: +DI crosses above -DI while armed
        val diCrossedUp = prevPlusDI <= prevMinusDI && lastPlusDI > lastMinusDI
        val isEntry = isArmed && diCrossedUp

        // Return only armed or entry signals (filter noise)
        if (!isArmed && !isEntry) return null

        return WilliamsDmiSignal(
            symbol = symbol,
            currentPrice = candles.last().closeDouble,
            williamsR = lastWR,
            plusDI = lastPlusDI,
            minusDI = lastMinusDI,
            isArmed = isArmed,
            isEntry = isEntry
        )
    }

    // Williams %R: range -100 to 0; -80 and below is oversold
    private fun calculateWilliamsR(candles: List<Candle>, period: Int): List<Double> {
        if (candles.size < period) return emptyList()
        val result = mutableListOf<Double>()
        for (i in period - 1 until candles.size) {
            val window = candles.subList(i - period + 1, i + 1)
            val highestHigh = window.maxOf { it.highDouble }
            val lowestLow = window.minOf { it.lowDouble }
            val close = candles[i].closeDouble
            val wr = if (highestHigh == lowestLow) -50.0
                     else ((highestHigh - close) / (highestHigh - lowestLow)) * -100.0
            result.add(wr)
        }
        return result
    }

    // DMI with Wilder smoothing; returns Pair(+DI list, -DI list)
    private fun calculateDMI(candles: List<Candle>, period: Int): Pair<List<Double>, List<Double>> {
        if (candles.size < period + 1) return Pair(emptyList(), emptyList())

        val trList = mutableListOf<Double>()
        val plusDMList = mutableListOf<Double>()
        val minusDMList = mutableListOf<Double>()

        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prev = candles[i - 1]
            val highDiff = curr.highDouble - prev.highDouble
            val lowDiff = prev.lowDouble - curr.lowDouble
            val plusDM = if (highDiff > lowDiff && highDiff > 0) highDiff else 0.0
            val minusDM = if (lowDiff > highDiff && lowDiff > 0) lowDiff else 0.0
            val tr = maxOf(
                curr.highDouble - curr.lowDouble,
                Math.abs(curr.highDouble - prev.closeDouble),
                Math.abs(curr.lowDouble - prev.closeDouble)
            )
            trList.add(tr)
            plusDMList.add(plusDM)
            minusDMList.add(minusDM)
        }

        fun wilderSmooth(values: List<Double>): List<Double> {
            if (values.size < period) return emptyList()
            val result = mutableListOf<Double>()
            var smoothed = values.take(period).sum()
            result.add(smoothed)
            for (i in period until values.size) {
                smoothed = smoothed - (smoothed / period) + values[i]
                result.add(smoothed)
            }
            return result
        }

        val smoothTR = wilderSmooth(trList)
        val smoothPlusDM = wilderSmooth(plusDMList)
        val smoothMinusDM = wilderSmooth(minusDMList)

        val plusDI = smoothPlusDM.zip(smoothTR).map { (dm, tr) -> if (tr > 0) 100.0 * dm / tr else 0.0 }
        val minusDI = smoothMinusDM.zip(smoothTR).map { (dm, tr) -> if (tr > 0) 100.0 * dm / tr else 0.0 }
        return Pair(plusDI, minusDI)
    }
}
