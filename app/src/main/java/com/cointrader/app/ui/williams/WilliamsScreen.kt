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
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = TextSecondary)
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
            // Settings panel
            AnimatedVisibility(visible = showSettings) {
                SettingsPanel(
                    wrPeriod = state.wrPeriod,
                    dmiPeriod = state.dmiPeriod,
                    lowerThreshold = state.lowerThreshold,
                    onWrPeriodChange = viewModel::updateWrPeriod,
                    onDmiPeriodChange = viewModel::updateDmiPeriod,
                    onThresholdChange = viewModel::updateLowerThreshold
                )
            }

            // Scan button + progress
            ScanHeader(
                isScanning = state.isScanning,
                scanned = state.scanned,
                total = state.total,
                signalCount = state.signals.size,
                onStartScan = viewModel::startScan,
                onStopScan = viewModel::stopScan
            )

            state.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = CoinRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.signals.isEmpty() && !state.isScanning) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.signals.sortedByDescending { it.isEntry }) { signal ->
                        SignalCard(signal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(
    isScanning: Boolean,
    scanned: Int,
    total: Int,
    signalCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (isScanning) {
                    Text(
                        text = "Scanning $scanned / $total pairs...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (scanned > 0) {
                    Text(
                        text = "Found $signalCount signal${if (signalCount != 1) "s" else ""} in $total pairs",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Ready to scan ${total} crypto pairs",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                onClick = if (isScanning) onStopScan else onStartScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) CoinRed else CoinBlue
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isScanning) "Stop" else "Scan")
            }
        }

        if (isScanning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) scanned.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = CoinBlue,
                trackColor = DarkCard
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Strategy Parameters", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(12.dp))

            SliderRow(
                label = "W%R Period: $wrPeriod",
                value = wrPeriod.toFloat(),
                range = 5f..30f,
                onValueChange = { onWrPeriodChange(it.roundToInt()) }
            )
            SliderRow(
                label = "DMI Period: $dmiPeriod",
                value = dmiPeriod.toFloat(),
                range = 5f..30f,
                onValueChange = { onDmiPeriodChange(it.roundToInt()) }
            )
            SliderRow(
                label = "Oversold Threshold: ${lowerThreshold.roundToInt()}",
                value = lowerThreshold.toFloat(),
                range = -99f..-50f,
                onValueChange = { onThresholdChange(it.toDouble()) }
            )
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
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = CoinBlue, activeTrackColor = CoinBlue)
    )
}

@Composable
private fun SignalCard(signal: WilliamsDmiSignal) {
    val priceFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = if (signal.currentPrice < 1.0) 6 else 2
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (signal.isEntry) DarkCard else DarkSurface
        ),
        border = if (signal.isEntry) {
            androidx.compose.foundation.BorderStroke(1.dp, CoinGreen)
        } else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = signal.symbol.removeSuffix("-USD"),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    if (signal.isEntry) {
                        SignalBadge("ENTRY", CoinGreen)
                    } else {
                        SignalBadge("ARMED", CoinYellow)
                    }
                }
                Text(
                    text = priceFormat.format(signal.currentPrice),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarkBackground, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("W%R", "%.1f".format(signal.williamsR), CoinYellow)
                MetricColumn("+DI", "%.1f".format(signal.plusDI), CoinGreen)
                MetricColumn("−DI", "%.1f".format(signal.minusDI), CoinRed)
                MetricColumn(
                    "+DI vs −DI",
                    if (signal.plusDI > signal.minusDI) "Bull" else "Bear",
                    if (signal.plusDI > signal.minusDI) CoinGreen else CoinRed
                )
            }
        }
    }
}

@Composable
private fun SignalBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MetricColumn(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No signals yet",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Text(
                text = "Tap Scan to check 25 crypto pairs\nfor Williams %R + DMI signals.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
