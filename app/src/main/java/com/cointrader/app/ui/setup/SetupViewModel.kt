package com.cointrader.app.ui.setup

import androidx.lifecycle.ViewModel
import com.cointrader.app.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _hasCredentials = MutableStateFlow(secureStorage.hasCredentials())
    val hasCredentials: StateFlow<Boolean> = _hasCredentials.asStateFlow()

    fun saveCredentials(apiKeyName: String, privateKey: String): Boolean {
        if (apiKeyName.isBlank() || privateKey.isBlank()) return false
        secureStorage.saveApiCredentials(apiKeyName.trim(), privateKey.trim())
        _hasCredentials.value = true
        return true
    }

    fun clearCredentials() {
        secureStorage.clearCredentials()
        _hasCredentials.value = false
    }
}
