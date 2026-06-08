package com.cointrader.app.ui.williams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrader.app.data.repository.CRYPTO_PAIRS
import com.cointrader.app.data.repository.CoinbaseRepository
import com.cointrader.app.data.repository.ScanProgress
import com.cointrader.app.data.repository.WilliamsDmiSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WilliamsUiState(
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val signals: List<WilliamsDmiSignal> = emptyList(),
    val errorMessage: String? = null,
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
        val state = _uiState.value
        _uiState.value = state.copy(
            isScanning = true,
            scanned = 0,
            total = CRYPTO_PAIRS.size,
            signals = emptyList(),
            errorMessage = null
        )

        scanJob = viewModelScope.launch {
            try {
                repository.scanWilliamsDmi(
                    pairs = CRYPTO_PAIRS,
                    wrPeriod = state.wrPeriod,
                    dmiPeriod = state.dmiPeriod,
                    lowerThreshold = state.lowerThreshold
                ).collect { progress ->
                    _uiState.value = _uiState.value.copy(
                        scanned = progress.scanned,
                        total = progress.total,
                        signals = progress.signals
                    )
                }
                _uiState.value = _uiState.value.copy(isScanning = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = e.message ?: "Scan failed"
                )
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = false)
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
