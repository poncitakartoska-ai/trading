package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quant.SignalDirection
import com.example.service.SignalMonitorService
import com.example.service.SignalNotificationHelper
import com.example.ui.PulseViewModel
import com.example.ui.SignalCard
import com.example.ui.SignalHistoryScreen
import com.example.ui.TerminalFilterMode
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SignalLong
import com.example.ui.theme.SignalLongDim
import com.example.ui.theme.SignalShort
import com.example.ui.theme.SignalWait
import com.example.ui.theme.SignalWaitDim
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalTextMuted
import com.example.ui.theme.TerminalTextPrimary
import com.example.ui.theme.TerminalTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SignalNotificationHelper.ensureChannels(this)
        setContent {
            MyApplicationTheme {
                MoexPulseApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Keep background monitoring alive when app goes to background if notification permission is granted
        if (SignalNotificationHelper.hasNotificationPermission(this)) {
            SignalMonitorService.startRadar(this)
        }
    }
}

@Composable
fun MoexPulseApp(viewModel: PulseViewModel = viewModel()) {
    val context = LocalContext.current
    val signals by viewModel.signals.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val secondsToNext by viewModel.secondsUntilNextScan.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val historyOutcomeFilter by viewModel.historyOutcomeFilter.collectAsStateWithLifecycle()
    val selectedHistoryTicker by viewModel.selectedHistoryTicker.collectAsStateWithLifecycle()
    val expandedTickers by viewModel.expandedTickers.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val performanceSummary by viewModel.performanceSummary.collectAsStateWithLifecycle()
    val depositInput by viewModel.depositRubInput.collectAsStateWithLifecycle()
    val riskPercentInput by viewModel.riskPercentInput.collectAsStateWithLifecycle()
    val isRadarEnabled by viewModel.isBackgroundRadarEnabled.collectAsStateWithLifecycle()

    var hasNotifPerm by remember {
        mutableStateOf(SignalNotificationHelper.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (granted) {
            viewModel.startBackgroundRadar()
            viewModel.sendLatestSignalNotificationNow()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (hasNotifPerm) {
            viewModel.startBackgroundRadar()
        }
    }

    if (filterMode != TerminalFilterMode.ALL) {
        BackHandler {
            viewModel.setFilterMode(TerminalFilterMode.ALL)
        }
    }

    val depositRub = depositInput.toDoubleOrNull()?.coerceAtLeast(1000.0) ?: 100_000.0
    val riskPercent = riskPercentInput.toDoubleOrNull()?.coerceIn(0.1, 25.0) ?: 1.0

    val activeSignalsCount = signals.count {
        it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
    }
    val historyByTicker = remember(allHistory) {
        allHistory.groupBy { it.ticker }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg),
        containerColor = TerminalBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalBg)
        ) {
            // Top Terminal Status Header
            TerminalHeaderBar(
                secondsToNext = secondsToNext,
                lastSyncTimestamp = lastSyncTimestamp,
                activeSignalsCount = activeSignalsCount,
                hasNotifPermission = hasNotifPerm,
                isRadarEnabled = isRadarEnabled,
                onRefreshClick = { viewModel.triggerManualScan() },
                onTogglePushRadar = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.toggleBackgroundRadar()
                    }
                }
            )

            // Filter Mode Bar: Все | Сигналы LONG/SHORT | История | Калькулятор лотов
            TerminalFilterRow(
                currentMode = filterMode,
                totalCount = signals.size,
                activeSignalsCount = activeSignalsCount,
                historyCount = allHistory.size,
                onSelectMode = { viewModel.setFilterMode(it) }
            )

            // Collapsible Risk & Lot Size Calculator Panel
            AnimatedVisibility(visible = filterMode == TerminalFilterMode.CALCULATOR) {
                RiskCalculatorCard(
                    depositInput = depositInput,
                    riskPercentInput = riskPercentInput,
                    depositRub = depositRub,
                    riskPercent = riskPercent,
                    onDepositChange = viewModel::updateDepositInput,
                    onRiskPercentChange = viewModel::updateRiskPercentInput,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Main Body: Either Signal History Screen or Live Stock Signals List
            if (filterMode == TerminalFilterMode.HISTORY) {
                SignalHistoryScreen(
                    allHistory = allHistory,
                    summary = performanceSummary,
                    outcomeFilter = historyOutcomeFilter,
                    selectedTicker = selectedHistoryTicker,
                    onSelectOutcomeFilter = viewModel::setHistoryOutcomeFilter,
                    onSelectTicker = viewModel::setSelectedHistoryTicker,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val displayedSignals = when (filterMode) {
                    TerminalFilterMode.SIGNALS_ONLY -> signals.filter {
                        it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
                    }
                    else -> signals
                }

                if (isLoading && signals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = SignalLong)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Синхронизация со шлюзом MOEX ISS (TQBR)...",
                                color = TerminalTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Расчет EMA 20/50 • RSI (14) • ATR (14) по 15 ликвидным акциям",
                                color = TerminalTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("signals_lazy_column"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (errorMessage != null && signals.isEmpty()) {
                            item {
                                ErrorBanner(
                                    message = errorMessage ?: "",
                                    onRetry = { viewModel.triggerManualScan() }
                                )
                            }
                        }

                        // Compact Summary Strip at top of live feed
                        item {
                            CompactQuantPulseStrip(
                                activeSignalsCount = activeSignalsCount,
                                winRate = performanceSummary.winRatePercent,
                                totalLoggedSignals = performanceSummary.totalSignals,
                                depositRub = depositRub,
                                riskPercent = riskPercent,
                                onOpenHistory = { viewModel.setFilterMode(TerminalFilterMode.HISTORY) },
                                onOpenCalculator = { viewModel.setFilterMode(TerminalFilterMode.CALCULATOR) }
                            )
                        }

                        if (displayedSignals.isEmpty()) {
                            item {
                                EmptySignalsCard(
                                    onShowAll = { viewModel.setFilterMode(TerminalFilterMode.ALL) },
                                    onOpenHistory = { viewModel.setFilterMode(TerminalFilterMode.HISTORY) }
                                )
                            }
                        } else {
                            items(displayedSignals, key = { it.ticker }) { signal ->
                                val tickerHistory = historyByTicker[signal.ticker].orEmpty()
                                SignalCard(
                                    signal = signal,
                                    stockHistory = tickerHistory,
                                    depositRub = depositRub,
                                    riskPercent = riskPercent,
                                    isExpanded = expandedTickers.contains(signal.ticker),
                                    onToggleExpand = { viewModel.toggleCardExpanded(signal.ticker) },
                                    onOpenFullStockHistory = { viewModel.openStockHistoryScreen(signal.ticker) },
                                    onNotifySignal = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            SignalNotificationHelper.notifyNewTradingSignal(context, signal)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalHeaderBar(
    secondsToNext: Int,
    lastSyncTimestamp: Long,
    activeSignalsCount: Int,
    hasNotifPermission: Boolean,
    isRadarEnabled: Boolean,
    onRefreshClick: () -> Unit,
    onTogglePushRadar: () -> Unit
) {
    val syncTimeStr = if (lastSyncTimestamp > 0L) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSyncTimestamp))
    } else {
        "Синхронизация..."
    }

    Surface(
        color = TerminalSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(SignalLong)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "MOEX TQBR • LIVE ОБНОВЛЕНИЕ",
                            color = SignalLong,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MOEX Pulse Quant • Активных: $activeSignalsCount • Обновлено: $syncTimeStr",
                        color = TerminalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Push Notifications & Background Radar Toggle
                    val pushActive = hasNotifPermission && isRadarEnabled
                    Surface(
                        color = if (pushActive) SignalLongDim else TerminalSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (pushActive) SignalLong.copy(alpha = 0.6f) else TerminalBorder
                        ),
                        modifier = Modifier
                            .testTag("toggle_push_radar_button")
                            .clickable(onClick = onTogglePushRadar)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (pushActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                contentDescription = "Push-уведомления в фоне",
                                tint = if (pushActive) SignalLong else TerminalTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (pushActive) "PUSH ON" else "PUSH",
                                color = if (pushActive) SignalLong else TerminalTextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Countdown Pill & Manual Refresh Button
                    Surface(
                        color = TerminalSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TerminalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 9.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${secondsToNext}с",
                                color = TerminalCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = onRefreshClick,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("manual_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Сканировать сейчас",
                                    tint = TerminalTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (10 - secondsToNext).coerceIn(0, 10) / 10f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = TerminalCyan,
                trackColor = TerminalSurfaceElevated,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun TerminalFilterRow(
    currentMode: TerminalFilterMode,
    totalCount: Int,
    activeSignalsCount: Int,
    historyCount: Int,
    onSelectMode: (TerminalFilterMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TerminalFilterMode.entries.forEach { mode ->
            val selected = currentMode == mode
            val labelText = when (mode) {
                TerminalFilterMode.ALL -> "Все ($totalCount)"
                TerminalFilterMode.SIGNALS_ONLY -> "Сигналы LONG/SHORT ($activeSignalsCount)"
                TerminalFilterMode.HISTORY -> "История ($historyCount)"
                TerminalFilterMode.CALCULATOR -> "Калькулятор лотов"
            }
            FilterChip(
                selected = selected,
                onClick = { onSelectMode(mode) },
                modifier = Modifier.testTag("filter_tab_${mode.name.lowercase()}"),
                label = {
                    Text(
                        text = labelText,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = {
                    when (mode) {
                        TerminalFilterMode.ALL -> Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        TerminalFilterMode.SIGNALS_ONLY -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (activeSignalsCount > 0) SignalLong else SignalWait)
                        )
                        TerminalFilterMode.HISTORY -> Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        TerminalFilterMode.CALCULATOR -> Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SignalLongDim,
                    selectedLabelColor = SignalLong,
                    selectedLeadingIconColor = SignalLong,
                    containerColor = TerminalSurface,
                    labelColor = TerminalTextSecondary,
                    iconColor = TerminalTextSecondary
                )
            )
        }
    }
}

@Composable
private fun CompactQuantPulseStrip(
    activeSignalsCount: Int,
    winRate: Double,
    totalLoggedSignals: Int,
    depositRub: Double,
    riskPercent: Double,
    onOpenHistory: () -> Unit,
    onOpenCalculator: () -> Unit
) {
    val riskRub = depositRub * (riskPercent / 100.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = TerminalSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TerminalBorder),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenHistory)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ЖУРНАЛ СИГНАЛОВ",
                        color = TerminalTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalLoggedSignals сделок • Win ${String.format(Locale.US, "%.0f", winRate)}%",
                        color = SignalLong,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TerminalCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Surface(
            color = TerminalSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TerminalBorder),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenCalculator)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "РИСК-МЕНЕДЖМЕНТ",
                        color = TerminalTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Риск: ${String.format(Locale.US, "%.0f", riskRub)} ₽ (${String.format(Locale.US, "%.1f", riskPercent)}%)",
                        color = TerminalCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = SignalWait,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RiskCalculatorCard(
    depositInput: String,
    riskPercentInput: String,
    depositRub: Double,
    riskPercent: Double,
    onDepositChange: (String) -> Unit,
    onRiskPercentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxRiskRub = depositRub * (riskPercent / 100.0)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("risk_calculator_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "КАЛЬКУЛЯТОР РИСКА И ОБЪЕМА ПОЗИЦИИ",
                    color = TerminalTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Риск на сделку: ${String.format(Locale.US, "%.0f", maxRiskRub)} ₽",
                    color = SignalLong,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = depositInput,
                    onValueChange = onDepositChange,
                    label = { Text("Депозит (₽)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("deposit_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalLong,
                        unfocusedBorderColor = TerminalBorder,
                        focusedTextColor = TerminalTextPrimary,
                        unfocusedTextColor = TerminalTextPrimary,
                        focusedLabelColor = SignalLong,
                        unfocusedLabelColor = TerminalTextSecondary
                    )
                )

                OutlinedTextField(
                    value = riskPercentInput,
                    onValueChange = onRiskPercentChange,
                    label = { Text("Риск (%)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("risk_percent_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalCyan,
                        unfocusedBorderColor = TerminalBorder,
                        focusedTextColor = TerminalTextPrimary,
                        unfocusedTextColor = TerminalTextPrimary,
                        focusedLabelColor = TerminalCyan,
                        unfocusedLabelColor = TerminalTextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Быстрый выбор риска:",
                    color = TerminalTextSecondary,
                    fontSize = 11.sp
                )
                listOf("0.5", "1.0", "1.5", "2.0").forEach { preset ->
                    val selected = riskPercentInput == preset
                    Surface(
                        color = if (selected) SignalLongDim else TerminalSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (selected) SignalLong else TerminalBorder),
                        modifier = Modifier.clickable { onRiskPercentChange(preset) }
                    ) {
                        Text(
                            text = "$preset%",
                            color = if (selected) SignalLong else TerminalTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySignalsCard(
    onShowAll: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "В данный момент все бумаги в фазе ⏳ WAIT",
                color = SignalWait,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Алгоритм фильтрует шумовые движения и ждет точного пересечения EMA 20/50 с подтверждением RSI и объема. Вы можете посмотреть уровни всех 15 акций или открыть историю прошлых сигналов.",
                color = TerminalTextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = SignalLongDim,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SignalLong),
                    modifier = Modifier.clickable(onClick = onShowAll)
                ) {
                    Text(
                        text = "Показать все 15 акций",
                        color = SignalLong,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Surface(
                    color = TerminalSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, TerminalCyan),
                    modifier = Modifier.clickable(onClick = onOpenHistory)
                ) {
                    Text(
                        text = "История сигналов",
                        color = TerminalCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        color = TerminalSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SignalShort.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = SignalShort,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = SignalWaitDim,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable(onClick = onRetry)
            ) {
                Text(
                    text = "Повторить",
                    color = SignalWait,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
