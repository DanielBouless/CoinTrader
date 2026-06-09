package com.cointrader.app.ui.williams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cointrader.app.data.repository.WilliamsDmiSignal
import com.cointrader.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WilliamsScreen(
    onNavigateToSetup: () -> Unit,
    viewModel: WilliamsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CoinTrader", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "Williams %R + DMI",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Tune, contentDescription = "Parameters", tint = TextSecondary)
                    }
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(Icons.Default.Settings, contentDescription = "API Setup", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Collapsible parameter panel
            AnimatedVisibility(visible = showSettings) {
                SettingsPanel(
                    wrPeriod        = state.wrPeriod,
                    dmiPeriod       = state.dmiPeriod,
                    lowerThreshold  = state.lowerThreshold,
                    onWrPeriodChange    = viewModel::updateWrPeriod,
                    onDmiPeriodChange   = viewModel::updateDmiPeriod,
                    onThresholdChange   = viewModel::updateLowerThreshold
                )
            }

            // Status bar + scan button
            ScanHeader(
                state      = state,
                onStartScan = viewModel::startScan,
                onStopScan  = viewModel::stopScan
            )

            // Error banner
            state.errorMessage?.let { err ->
                Text(
                    text  = "⚠ $err",
                    color = CoinRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Results or empty state
            if (state.signals.isEmpty() && !state.isScanning && !state.isLoadingPairs) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Entry signals first, then armed
                    items(
                        state.signals.sortedWith(
                            compareByDescending<WilliamsDmiSignal> { it.isEntry }
                                .thenByDescending { it.plusDI - it.minusDI }
                        )
                    ) { signal ->
                        SignalCard(signal)
                    }
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────

@Composable
private fun ScanHeader(
    state: WilliamsUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val busy = state.isLoadingPairs || state.isScanning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier                = Modifier.fillMaxWidth(),
            horizontalArrangement   = Arrangement.SpaceBetween,
            verticalAlignment       = Alignment.CenterVertically
        ) {
            Text(
                text     = state.statusLine,
                color    = if (busy) CoinBlue else TextSecondary,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )

            Button(
                onClick = if (busy) onStopScan else onStartScan,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (busy) CoinRed else CoinBlue
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.isLoadingPairs) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color    = TextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (busy) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(if (busy) "Stop" else "Scan All")
            }
        }

        // Progress bar shown during actual pair scanning
        if (state.isScanning && state.total > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${state.scanned} / ${state.total}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "${state.signals.size} signal${if (state.signals.size != 1) "s" else ""}",
                    color = if (state.signals.isNotEmpty()) CoinGreen else TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress    = { state.scanned.toFloat() / state.total },
                modifier    = Modifier.fillMaxWidth(),
                color       = CoinBlue,
                trackColor  = DarkCard
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    wrPeriod: Int,
    dmiPeriod: Int,
    lowerThreshold: Double,
    onWrPeriodChange: (Int) -> Unit,
    onDmiPeriodChange: (Int) -> Unit,
    onThresholdChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Strategy Parameters",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))

            SliderRow("W%R Period: $wrPeriod", wrPeriod.toFloat(), 5f..30f) {
                onWrPeriodChange(it.roundToInt())
            }
            SliderRow("DMI Period: $dmiPeriod", dmiPeriod.toFloat(), 5f..30f) {
                onDmiPeriodChange(it.roundToInt())
            }
            SliderRow(
                "Oversold threshold: ${lowerThreshold.roundToInt()}",
                lowerThreshold.toFloat(), -99f..-50f
            ) {
                onThresholdChange(it.toDouble())
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value       = value,
        onValueChange = onValueChange,
        valueRange  = range,
        colors      = SliderDefaults.colors(thumbColor = CoinBlue, activeTrackColor = CoinBlue)
    )
}

@Composable
private fun SignalCard(signal: WilliamsDmiSignal) {
    val priceFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = when {
            signal.currentPrice >= 1000 -> 2
            signal.currentPrice >= 1    -> 4
            else                        -> 8
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (signal.isEntry) DarkCard else DarkSurface
        ),
        border = if (signal.isEntry)
            androidx.compose.foundation.BorderStroke(1.dp, CoinGreen)
        else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: symbol + badge | price
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text       = signal.symbol,
                        style      = MaterialTheme.typography.titleLarge,
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    SignalBadge(
                        text  = if (signal.isEntry) "ENTRY" else "ARMED",
                        color = if (signal.isEntry) CoinGreen else CoinYellow
                    )
                }
                Text(
                    text       = priceFormat.format(signal.currentPrice),
                    style      = MaterialTheme.typography.titleMedium,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarkBackground, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            // Indicator metrics row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("W%R", "%.1f".format(signal.williamsR), CoinYellow)
                MetricColumn("+DI",  "%.1f".format(signal.plusDI),   CoinGreen)
                MetricColumn("−DI",  "%.1f".format(signal.minusDI),  CoinRed)
                MetricColumn(
                    label      = "Trend",
                    value      = if (signal.plusDI > signal.minusDI) "Bullish" else "Bearish",
                    valueColor = if (signal.plusDI > signal.minusDI) CoinGreen else CoinRed
                )
            }
        }
    }
}

@Composable
private fun SignalBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape  = RoundedCornerShape(6.dp),
        color  = color.copy(alpha = 0.15f)
    ) {
        Text(
            text       = text,
            color      = color,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color      = valueColor,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                imageVector     = Icons.Default.Search,
                contentDescription = null,
                tint            = TextSecondary,
                modifier        = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text  = "No signals yet",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Tap Scan All to pull every tradeable pair from Coinbase and check each one for Williams %R + DMI entry signals.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
