package com.cointrader.app.ui.williams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrader.app.data.repository.CoinbaseRepository
import com.cointrader.app.data.repository.ScanPhase
import com.cointrader.app.data.repository.WilliamsDmiSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WilliamsUiState(
    val isLoadingPairs: Boolean = false,
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val pairsLoaded: Int = 0,
    val signals: List<WilliamsDmiSignal> = emptyList(),
    val errorMessage: String? = null,
    val statusLine: String = "Tap Scan to fetch all Coinbase pairs and analyse signals.",
    val wrPeriod: Int = 14,
    val dmiPeriod: Int = 14,
    val lowerThreshold: Double = -80.0
)

@HiltViewModel
class WilliamsViewModel @Inject constructor(
    private val repository: CoinbaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WilliamsUiState())
    val uiState: StateFlow<WilliamsUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        val params = _uiState.value
        _uiState.value = params.copy(
            isLoadingPairs = true,
            isScanning = false,
            scanned = 0,
            total = 0,
            pairsLoaded = 0,
            signals = emptyList(),
            errorMessage = null,
            statusLine = "Fetching all tradeable pairs from Coinbase…"
        )

        scanJob = viewModelScope.launch {
            // Step 1 — fetch full pair list
            val pairsResult = repository.fetchAllTradablePairs()
            if (pairsResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPairs = false,
                    errorMessage = "Could not load pairs: ${pairsResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            val pairs = pairsResult.getOrThrow()
            _uiState.value = _uiState.value.copy(
                isLoadingPairs = false,
                isScanning = true,
                total = pairs.size,
                pairsLoaded = pairs.size,
                statusLine = "Scanning ${pairs.size} pairs…"
            )

            // Step 2 — scan them all
            try {
                repository.scanWilliamsDmi(
                    pairs          = pairs,
                    wrPeriod       = params.wrPeriod,
                    dmiPeriod      = params.dmiPeriod,
                    lowerThreshold = params.lowerThreshold
                ).collect { progress ->
                    val done = progress.phase == ScanPhase.DONE
                    _uiState.value = _uiState.value.copy(
                        isScanning  = !done,
                        scanned     = progress.scanned,
                        total       = progress.total,
                        signals     = progress.signals,
                        statusLine  = if (done)
                            "Done — ${progress.signals.size} signal${if (progress.signals.size != 1) "s" else ""} found across ${progress.total} pairs."
                        else
                            "Scanning ${progress.scanned} / ${progress.total} pairs…"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning   = false,
                    errorMessage = e.message ?: "Scan failed"
                )
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoadingPairs = false,
            isScanning     = false,
            statusLine     = "Scan stopped at ${_uiState.value.scanned} / ${_uiState.value.total} pairs."
        )
    }

    fun updateWrPeriod(period: Int) {
        _uiState.value = _uiState.value.copy(wrPeriod = period.coerceIn(5, 30))
    }

    fun updateDmiPeriod(period: Int) {
        _uiState.value = _uiState.value.copy(dmiPeriod = period.coerceIn(5, 30))
    }

    fun updateLowerThreshold(threshold: Double) {
        _uiState.value = _uiState.value.copy(lowerThreshold = threshold.coerceIn(-99.0, -50.0))
    }
}
