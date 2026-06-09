package com.cointrader.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cointrader.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    hasCredentials: Boolean,
    onNavigateToScanner: () -> Unit
) {
    var apiKeyName by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var privateKeyVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasCredentials) {
        if (hasCredentials) onNavigateToScanner()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = CoinBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "CoinTrader",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Text(
                text = "Williams %R + DMI Strategy",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Coinbase API Credentials",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Text(
                        text = "Get your API key from Coinbase Advanced Trade → Settings → API.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = apiKeyName,
                        onValueChange = { apiKeyName = it; errorMessage = null },
                        label = { Text("API Key Name") },
                        placeholder = { Text("organizations/.../apiKeys/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = CoinBlue,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = CoinBlue,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = CoinBlue,
                            textColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it; errorMessage = null },
                        label = { Text("EC Private Key (PEM)") },
                        placeholder = { Text("-----BEGIN EC PRIVATE KEY-----...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 8,
                        visualTransformation = if (privateKeyVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { privateKeyVisible = !privateKeyVisible }) {
                                Icon(
                                    imageVector = if (privateKeyVisible) Icons.Default.Visibility
                                                  else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = CoinBlue,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = CoinBlue,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = CoinBlue,
                            textColor = TextPrimary
                        )
                    )

                    errorMessage?.let { err ->
                        Text(
                            text = err,
                            color = CoinRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = {
                            val saved = viewModel.saveCredentials(apiKeyName, privateKey)
                            if (!saved) errorMessage = "Both fields are required."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoinBlue)
                    ) {
                        Text(
                            text = "Connect & Start Scanning",
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Two-Stage Signal Strategy",
                        style = MaterialTheme.typography.titleMedium,
                        color = CoinBlue
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Stage 1 — Arm: Williams %R touches ≤ −80 (oversold zone) within the last 3 candles.\n\nStage 2 — Entry: +DI crosses above −DI while the signal is armed.\n\nExit: −DI crosses above +DI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
