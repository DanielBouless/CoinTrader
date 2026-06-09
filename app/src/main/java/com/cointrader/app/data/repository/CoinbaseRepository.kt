package com.cointrader.app.data.repository

import com.cointrader.app.data.api.CoinbaseService
import com.cointrader.app.data.api.models.Candle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
    val signals: List<WilliamsDmiSignal>,
    val phase: ScanPhase = ScanPhase.SCANNING
)

enum class ScanPhase { FETCHING_PAIRS, SCANNING, DONE }

@Singleton
class CoinbaseRepository @Inject constructor(
    private val coinbaseService: CoinbaseService
) {
    // Limit concurrent candle requests to stay within Coinbase rate limits
    private val semaphore = Semaphore(8)

    /**
     * Fetches ALL tradeable SPOT product IDs from Coinbase, paginating until exhausted.
     * Filters to non-disabled, "online" products only.
     */
    suspend fun fetchAllTradablePairs(): Result<List<String>> {
        return runCatching {
            val allProducts = mutableListOf<String>()
            var cursor: String? = null

            do {
                val page = coinbaseService.getProducts(limit = 250, cursor = cursor)
                page.products
                    .filter { !it.isDisabled && it.status.equals("online", ignoreCase = true) }
                    .mapTo(allProducts) { it.productId }
                cursor = page.cursor?.takeIf { it.isNotBlank() }
            } while (cursor != null)

            allProducts.sorted()
        }
    }

    /**
     * Scans all provided pairs for Williams %R + DMI two-stage signals.
     * Emits progress as each pair is analysed.
     *
     * Stage 1 (arm):   W%R ≤ lowerThreshold within the last 3 daily candles.
     * Stage 2 (entry): +DI crosses above −DI while the signal is armed.
     * Exit signal:     −DI crosses above +DI (reported when exitAlert = true).
     */
    fun scanWilliamsDmi(
        pairs: List<String>,
        wrPeriod: Int = 14,
        dmiPeriod: Int = 14,
        lowerThreshold: Double = -80.0
    ): Flow<ScanProgress> = flow {
        val signals = mutableListOf<WilliamsDmiSignal>()
        var scanned = 0

        emit(ScanProgress(0, pairs.size, emptyList(), ScanPhase.SCANNING))

        coroutineScope {
            val jobs = pairs.map { symbol ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            fetchAndAnalyze(symbol, wrPeriod, dmiPeriod, lowerThreshold)
                        }.getOrNull()
                    }
                }
            }

            for (job in jobs) {
                val result = job.await()
                scanned++
                if (result != null) signals.add(result)
                emit(ScanProgress(scanned, pairs.size, signals.toList(), ScanPhase.SCANNING))
            }
        }

        emit(ScanProgress(scanned, pairs.size, signals.toList(), ScanPhase.DONE))
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

        // Coinbase returns newest-first; sort oldest-first for indicator maths
        val candles = response.candles
            .filter { it.closeDouble > 0 && it.highDouble > 0 && it.lowDouble > 0 }
            .sortedBy { it.start.toLongOrNull() ?: 0L }

        val minRequired = max(wrPeriod, dmiPeriod) + 5
        if (candles.size < minRequired) return null

        val wr      = calculateWilliamsR(candles, wrPeriod)
        val (plusDI, minusDI) = calculateDMI(candles, dmiPeriod)

        if (wr.isEmpty() || plusDI.size < 2 || minusDI.size < 2) return null

        val lastWR      = wr.last()
        val lastPlusDI  = plusDI.last()
        val lastMinusDI = minusDI.last()
        val prevPlusDI  = plusDI[plusDI.size - 2]
        val prevMinusDI = minusDI[minusDI.size - 2]

        // Stage 1: armed when W%R touched the oversold threshold in the last 3 candles
        val isArmed = wr.takeLast(3).any { it <= lowerThreshold }

        // Stage 2: +DI just crossed above −DI while armed
        val diCrossedUp = prevPlusDI <= prevMinusDI && lastPlusDI > lastMinusDI
        val isEntry     = isArmed && diCrossedUp

        // Only surface armed or entry signals; skip everything else
        if (!isArmed && !isEntry) return null

        return WilliamsDmiSignal(
            symbol       = symbol,
            currentPrice = candles.last().closeDouble,
            williamsR    = lastWR,
            plusDI       = lastPlusDI,
            minusDI      = lastMinusDI,
            isArmed      = isArmed,
            isEntry      = isEntry
        )
    }

    // ── Indicator calculations ──────────────────────────────────────────────

    /** Williams %R in range [−100, 0]; values ≤ −80 are oversold. */
    private fun calculateWilliamsR(candles: List<Candle>, period: Int): List<Double> {
        if (candles.size < period) return emptyList()
        return (period - 1 until candles.size).map { i ->
            val window      = candles.subList(i - period + 1, i + 1)
            val highestHigh = window.maxOf { it.highDouble }
            val lowestLow   = window.minOf { it.lowDouble }
            val close       = candles[i].closeDouble
            if (highestHigh == lowestLow) -50.0
            else ((highestHigh - close) / (highestHigh - lowestLow)) * -100.0
        }
    }

    /**
     * Directional Movement Index using Wilder smoothing.
     * Returns Pair(+DI values, −DI values).
     */
    private fun calculateDMI(candles: List<Candle>, period: Int): Pair<List<Double>, List<Double>> {
        if (candles.size < period + 1) return Pair(emptyList(), emptyList())

        val trList      = ArrayList<Double>(candles.size)
        val plusDMList  = ArrayList<Double>(candles.size)
        val minusDMList = ArrayList<Double>(candles.size)

        for (i in 1 until candles.size) {
            val curr     = candles[i]
            val prev     = candles[i - 1]
            val upMove   = curr.highDouble - prev.highDouble
            val downMove = prev.lowDouble  - curr.lowDouble
            plusDMList.add(if (upMove   > downMove && upMove   > 0) upMove   else 0.0)
            minusDMList.add(if (downMove > upMove   && downMove > 0) downMove else 0.0)
            trList.add(maxOf(
                curr.highDouble - curr.lowDouble,
                Math.abs(curr.highDouble - prev.closeDouble),
                Math.abs(curr.lowDouble  - prev.closeDouble)
            ))
        }

        fun wilderSmooth(values: List<Double>): List<Double> {
            if (values.size < period) return emptyList()
            val result   = ArrayList<Double>(values.size - period + 1)
            var smoothed = values.take(period).sum()
            result.add(smoothed)
            for (i in period until values.size) {
                smoothed = smoothed - smoothed / period + values[i]
                result.add(smoothed)
            }
            return result
        }

        val smoothTR      = wilderSmooth(trList)
        val smoothPlusDM  = wilderSmooth(plusDMList)
        val smoothMinusDM = wilderSmooth(minusDMList)

        val plusDI  = smoothPlusDM.zip(smoothTR).map  { (dm, tr) -> if (tr > 0) 100.0 * dm / tr else 0.0 }
        val minusDI = smoothMinusDM.zip(smoothTR).map { (dm, tr) -> if (tr > 0) 100.0 * dm / tr else 0.0 }
        return Pair(plusDI, minusDI)
    }
}
