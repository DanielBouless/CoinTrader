package com.cointrader.app.ui.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrader.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    object Checking   : LoginState()   // app launch — verifying session
    object LoggedOut  : LoginState()   // needs login
    object LoggingIn  : LoginState()   // waiting for Coinbase OAuth callback
    object LoggedIn   : LoginState()   // valid session exists
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Checking)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            _state.value = LoginState.Checking
            val token = authRepository.getValidAccessToken()
            _state.value = if (token != null) LoginState.LoggedIn else LoginState.LoggedOut
        }
    }

    fun login(context: Context) {
        _state.value = LoginState.LoggingIn
        authRepository.launchCoinbaseLogin(context)
    }

    /** Called by MainActivity when cointrader://oauth/callback arrives. */
    fun handleOAuthCallback(uri: Uri) {
        viewModelScope.launch {
            val result = authRepository.handleCallback(uri)
            _state.value = if (result.isSuccess) {
                LoginState.LoggedIn
            } else {
                LoginState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _state.value = LoginState.LoggedOut
    }
}
