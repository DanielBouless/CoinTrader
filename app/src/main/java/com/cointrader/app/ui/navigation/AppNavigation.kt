package com.cointrader.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cointrader.app.ui.auth.LoginScreen
import com.cointrader.app.ui.auth.LoginState
import com.cointrader.app.ui.auth.LoginViewModel
import com.cointrader.app.ui.williams.WilliamsScreen
import com.cointrader.app.ui.williams.WilliamsViewModel

sealed class Screen(val route: String) {
    object Login    : Screen("login")
    object Williams : Screen("williams")
}

@Composable
fun AppNavigation(loginViewModel: LoginViewModel) {
    val navController = rememberNavController()
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()

    // Determine start destination based on current session state
    val startDestination = when (loginState) {
        is LoginState.LoggedIn -> Screen.Williams.route
        else                   -> Screen.Login.route
    }

    NavHost(navController = navController, startDestination = Screen.Login.route) {

        composable(Screen.Login.route) {
            // Once logged in, navigate to the scanner and clear the login back-stack
            if (loginState is LoginState.LoggedIn) {
                navController.navigate(Screen.Williams.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            LoginScreen(
                state   = loginState,
                onLogin = loginViewModel::login
            )
        }

        composable(Screen.Williams.route) {
            // If token becomes invalid (e.g. revoked remotely), bounce back to login
            if (loginState is LoginState.LoggedOut) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Williams.route) { inclusive = true }
                }
            }
            val williamsViewModel: WilliamsViewModel = hiltViewModel()
            WilliamsScreen(
                viewModel = williamsViewModel,
                onLogout  = {
                    loginViewModel.logout()
                }
            )
        }
    }
}
