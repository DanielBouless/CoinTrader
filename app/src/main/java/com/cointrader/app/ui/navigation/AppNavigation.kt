package com.cointrader.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cointrader.app.ui.setup.SetupScreen
import com.cointrader.app.ui.setup.SetupViewModel
import com.cointrader.app.ui.williams.WilliamsScreen
import com.cointrader.app.ui.williams.WilliamsViewModel

sealed class Screen(val route: String) {
    object Setup    : Screen("setup")
    object Williams : Screen("williams")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Setup.route) {

        composable(Screen.Setup.route) {
            val viewModel: SetupViewModel = hiltViewModel()
            val hasCredentials by viewModel.hasCredentials.collectAsStateWithLifecycle()
            SetupScreen(
                viewModel          = viewModel,
                hasCredentials     = hasCredentials,
                onNavigateToScanner = {
                    navController.navigate(Screen.Williams.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Williams.route) {
            val viewModel: WilliamsViewModel = hiltViewModel()
            WilliamsScreen(
                viewModel = viewModel,
                onLogout  = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Williams.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
