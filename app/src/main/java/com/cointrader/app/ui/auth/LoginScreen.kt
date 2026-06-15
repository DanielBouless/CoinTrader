package com.cointrader.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cointrader.app.ui.theme.*

@Composable
fun LoginScreen(
    state: LoginState,
    onLogin: (android.content.Context) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint               = CoinBlue,
                modifier           = Modifier.size(80.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text       = "CoinTrader",
                style      = MaterialTheme.typography.headlineLarge,
                color      = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "Williams %R + DMI Strategy",
                style     = MaterialTheme.typography.bodyLarge,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            when (state) {
                is LoginState.Checking, is LoginState.LoggingIn -> {
                    CircularProgressIndicator(color = CoinBlue)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text  = if (state is LoginState.Checking) "Checking session…"
                                else "Waiting for Coinbase login…",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is LoginState.LoggedOut, is LoginState.Error -> {
                    if (state is LoginState.Error) {
                        Text(
                            text      = state.message,
                            color     = CoinRed,
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    Button(
                        onClick = { onLogin(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape  = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoinBlue)
                    ) {
                        Text(
                            text       = "Connect with Coinbase",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text      = "You'll be taken to Coinbase to log in.\nNo credentials are stored in this app.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = TextSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                else -> { /* LoggedIn — navigation handles this */ }
            }
        }
    }
}
