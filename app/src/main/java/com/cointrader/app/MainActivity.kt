package com.cointrader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cointrader.app.ui.auth.LoginViewModel
import com.cointrader.app.ui.navigation.AppNavigation
import com.cointrader.app.ui.theme.CoinTraderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle OAuth redirect on cold start (app was not running)
        intent?.data?.let { handleOAuthRedirect(it) }

        setContent {
            CoinTraderTheme {
                AppNavigation(loginViewModel = loginViewModel)
            }
        }
    }

    // Handle OAuth redirect when app is already running (singleTask brings it to front)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { handleOAuthRedirect(it) }
    }

    private fun handleOAuthRedirect(uri: Uri) {
        if (uri.scheme == "cointrader" && uri.host == "oauth") {
            loginViewModel.handleOAuthCallback(uri)
        }
    }
}
