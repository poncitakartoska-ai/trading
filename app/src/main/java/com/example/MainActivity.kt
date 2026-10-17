package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.TradeOutcome
import com.example.quant.SignalDirection
import com.example.service.SignalMonitorService
import com.example.ui.AiQuantChatScreen
import com.example.ui.AuthGateScreen
import com.example.ui.FilterBar
import com.example.ui.NewsFeedScreen
import com.example.ui.PositionCalculatorSheet
import com.example.ui.PulseViewModel
import com.example.ui.SettingsAndPcScreen
import com.example.ui.SignalCard
import com.example.ui.SignalFilter
import com.example.ui.SignalHistoryScreen
import com.example.ui.TerminalHeaderBar
import com.example.ui.TerminalTab
import com.example.ui.TerminalTabSwitcher
import com.example.ui.signOutFromGoogle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.MoexPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val pulseViewModel: PulseViewModel = viewModel()
            val settings by pulseViewModel.appSettings.collectAsStateWithLifecycle()

            MoexPulseTheme(themeMode = settings.themeMode) {
                MoexPulseTerminalScreen(
                    viewModel = pulseViewModel,
                    settings = settings
                )
            }
        }
    }
}

@Composable
fun MoexPulseTerminalScreen(
    viewModel: PulseViewModel,
    settings: AppSettings
) {
    val palette = LocalAppPalette.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val historyList by viewModel.signalHistory.collectAsStateWithLifecycle()
    val chatMessages by viewModel.cloudChatMessages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            SignalMonitorService.startServiceSafely(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SignalMonitorService.startServiceSafely(context)
        }
    }

    if (state.selectedTab != TerminalTab.LIVE_SIGNALS) {
        BackHandler {
            viewModel.selectTab(TerminalTab.LIVE_SIGNALS)
        }
    }

    if (!state.isAuthenticated) {
        AuthGateScreen(
            onAuthSuccess = { viewModel.onAuthStateChanged() }
        )
        return
    }

    val longCount = remember(state.signals) {
        state.signals.count { it.direction == SignalDirection.LONG }
    }
    val shortCount = remember(state.signals) {
        state.signals.count { it.direction == SignalDirection.SHORT }
    }
    val waitCount = remember(state.signals) {
        state.signals.count { it.direction == SignalDirection.WAIT }
    }

    val filteredSignals = remember(state.signals, state.selectedFilter) {
        when (state.selectedFilter) {
            SignalFilter.ALL -> state.signals
            SignalFilter.ACTIVE -> state.signals.filter {
                it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
            }
            SignalFilter.LONG -> state.signals.filter { it.direction == SignalDirection.LONG }
            SignalFilter.SHORT -> state.signals.filter { it.direction == SignalDirection.SHORT }
            SignalFilter.WAIT -> state.signals.filter { it.direction == SignalDirection.WAIT }
        }
    }

    val historyByTicker = remember(historyList) {
        historyList.groupBy { it.ticker }
    }
    val resolvedHistory = remember(historyList) {
        historyList.filter {
            it.status == TradeOutcome.HIT_TP.code || it.status == TradeOutcome.HIT_SL.code
        }
    }
    val historyWinRatePct = remember(resolvedHistory) {
        if (resolvedHistory.isEmpty()) 0
        else ((resolvedHistory.count { it.status == TradeOutcome.HIT_TP.code }.toDouble() / resolvedHistory.size) * 100).toInt()
    }
    val openHistoryCount = remember(historyList) {
        historyList.count { it.status == TradeOutcome.ACTIVE.code }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
        containerColor = palette.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.background(palette.bg)) {
                TerminalHeaderBar(
                    updatedTime = state.updatedTime,
                    nextRefreshInSec = state.nextRefreshInSec,
                    isRefreshing = state.isRefreshing,
                    isLiveConnection = state.isLiveConnection,
                    longCount = longCount,
                    shortCount = shortCount,
                    waitCount = waitCount,
                    historyWinRatePct = historyWinRatePct,
                    resolvedTradesCount = resolvedHistory.size,
                    notificationsGranted = notificationsGranted,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRefreshClick = { viewModel.refreshNow() },
                    onToggleThemeClick = {
                        val nextMode = if (palette.isDark) AppThemeMode.LIGHT else AppThemeMode.DARK
                        viewModel.setThemeMode(nextMode)
                    },
                    userEmail = state.currentUserEmail,
                    cloudSignalsCount = historyList.size,
                    onSignOutClick = {
                        signOutFromGoogle(
                            credentialManager = credentialManager,
                            onSignOutComplete = { viewModel.onAuthStateChanged() },
                            scope = scope
                        )
                    }
                )
                TerminalTabSwitcher(
                    selectedTab = state.selectedTab,
                    totalHistoryCount = historyList.size,
                    openHistoryCount = openHistoryCount,
                    newsCount = state.newsItems.size,
                    onSelectTab = { viewModel.selectTab(it) }
                )
                if (state.selectedTab == TerminalTab.LIVE_SIGNALS) {
                    FilterBar(
                        selectedFilter = state.selectedFilter,
                        onSelectFilter = { viewModel.selectFilter(it) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (state.selectedTab) {
            TerminalTab.LIVE_SIGNALS -> {
                if (state.isLoading && state.signals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = palette.longColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Загрузка свечей MOEX ISS и 7 факторов Quant 2.0...",
                                color = palette.textSecondary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (filteredSignals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "В выбранном фильтре «${state.selectedFilter.title}» сигналов нет",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Переключитесь на «Все (15)», чтобы видеть уровни и графики TradingView по всем бумагам.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .testTag("signals_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredSignals, key = { it.ticker }) { signal ->
                            SignalCard(
                                signal = signal,
                                tickerHistory = historyByTicker[signal.ticker].orEmpty(),
                                onClick = { viewModel.selectSignalForCalculator(signal) },
                                onOpenTickerHistory = { ticker ->
                                    viewModel.selectHistoryFilterTicker(ticker)
                                }
                            )
                        }
                    }
                }
            }

            TerminalTab.NEWS_FEED -> {
                NewsFeedScreen(
                    newsItems = state.newsItems,
                    selectedTickerFilter = state.selectedNewsTickerFilter,
                    marketSentimentIndex = state.marketSentimentIndex,
                    marketRegimeLabel = state.marketRegimeLabel,
                    isLoading = state.isNewsLoading,
                    onSelectTickerFilter = { viewModel.selectNewsTickerFilter(it) },
                    onRefreshNews = { viewModel.refreshNewsNow() },
                    onAnalyzeNewsWithAi = { newsItem -> viewModel.analyzeNewsWithAi(newsItem) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            TerminalTab.HISTORY -> {
                SignalHistoryScreen(
                    historyList = historyList,
                    filterTicker = state.historyFilterTicker,
                    onClearFilterTicker = { viewModel.selectHistoryFilterTicker(null) },
                    onClearAllHistory = { viewModel.clearHistory() },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            TerminalTab.AI_CHAT -> {
                AiQuantChatScreen(
                    messages = chatMessages,
                    selectedMode = state.selectedAiMode,
                    isSending = state.isAiSending,
                    onSelectMode = { viewModel.selectAiMode(it) },
                    onSendMessage = { prompt -> viewModel.sendAiChatPrompt(prompt) },
                    onClearChat = { viewModel.clearAiChatHistory() },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            TerminalTab.SETTINGS_PC -> {
                SettingsAndPcScreen(
                    settings = settings,
                    onSelectTheme = { viewModel.setThemeMode(it) },
                    onSelectStrategyProfile = { viewModel.setStrategyProfile(it) },
                    onSelectCandleInterval = { viewModel.setCandleInterval(it) },
                    onChangeMinConfidence = { viewModel.setMinConfidence(it) },
                    onToggleNewsConfirmation = { viewModel.setRequireNewsConfirmation(it) },
                    onToggleStrictVwapMacd = { viewModel.setStrictVwapMacd(it) },
                    onSelectPollIntervalSec = { viewModel.setPollIntervalSec(it) },
                    onRecalculateBacktest = { viewModel.recalculateHistoryWithCurrentSettings() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        state.selectedSignal?.let { activeSignal ->
            PositionCalculatorSheet(
                signal = activeSignal,
                depositText = state.depositText,
                riskPercentText = state.riskPercentText,
                onDepositChange = { viewModel.updateDepositText(it) },
                onRiskPercentChange = { viewModel.updateRiskPercentText(it) },
                onDismiss = { viewModel.selectSignalForCalculator(null) }
            )
        }
    }
}
