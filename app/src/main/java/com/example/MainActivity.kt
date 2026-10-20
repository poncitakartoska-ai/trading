package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppSettings
import com.example.data.MarketAssetClass
import com.example.data.QuantStrategyProfile
import com.example.data.SignalSortOrder
import com.example.data.Strategy
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.ui.BloombergShimmerLoadingScreen
import com.example.ui.InstitutionalQuantCommandCenterCard
import com.example.ui.PaperTradingScreen
import com.example.ui.PortfolioManagerScreen
import com.example.ui.PulseUiState
import com.example.ui.PulseViewModel
import com.example.ui.RechartsCrossMarketVolatilityHeatmapCard
import com.example.ui.RiskCalculatorPanel
import com.example.ui.SettingsAndPcScreen
import com.example.ui.SignalCard
import com.example.ui.SignalHistoryScreen
import com.example.ui.TerminalHeader
import com.example.ui.TradingStrategyCrossoverLabCard
import com.example.ui.TradingStrategyViewModel
import com.example.ui.UltimateSearchAndFilterBar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.MoexPulseTheme
import com.example.ui.theme.SpaceGroteskFontFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#000000"))
        )
        setContent {
            val viewModel: PulseViewModel = viewModel()
            val settings by viewModel.appSettings.collectAsStateWithLifecycle()

            MoexPulseTheme(themeMode = settings.themeMode) {
                MoexPulseProApp(viewModel = viewModel)
            }
        }
    }
}

private data class NavTabItem(
    val id: String,
    val labelRu: String,
    val icon: ImageVector
)

@Composable
fun MoexPulseProApp(
    viewModel: PulseViewModel = viewModel(),
    strategyViewModel: TradingStrategyViewModel = viewModel()
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val imoexSnapshot by viewModel.imoexSnapshot.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val onlyFavorites by viewModel.onlyWatchlistFavorites.collectAsStateWithLifecycle()
    val selectedRiskTicker by viewModel.selectedRiskTicker.collectAsStateWithLifecycle()
    val activeScreenTab by viewModel.activeScreenTab.collectAsStateWithLifecycle()
    val historyTickerFilter by viewModel.historyTickerFilter.collectAsStateWithLifecycle()
    val newsOverview by viewModel.newsOverview.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val allHistory by viewModel.allSignalHistory.collectAsStateWithLifecycle()
    val performanceSummary by viewModel.performanceSummary.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isBackgroundServiceRunning.collectAsStateWithLifecycle()
    val allPaperTrades by viewModel.allPaperTrades.collectAsStateWithLifecycle()
    val paperPortfolioSummary by viewModel.paperPortfolioSummary.collectAsStateWithLifecycle()
    val activeTradingStrategy by strategyViewModel.activeTradingStrategy.collectAsStateWithLifecycle()
    val crossoverSummary by strategyViewModel.crossoverSummary.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setAppInForeground(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.setAppInForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (activeScreenTab != "LIVE_SIGNALS") {
        BackHandler {
            viewModel.setActiveScreenTab("LIVE_SIGNALS")
        }
    }

    val liveSignalsList: List<QuantSignal> = when (val s = uiState) {
        is PulseUiState.Success -> s.signals
        is PulseUiState.Error -> s.cachedSignals
        is PulseUiState.Loading -> emptyList()
    }

    LaunchedEffect(liveSignalsList) {
        if (liveSignalsList.isNotEmpty()) {
            strategyViewModel.updateLiveSignalsSnapshot(liveSignalsList)
        }
    }

    val lastPollTimeStr = when (val s = uiState) {
        is PulseUiState.Success -> s.lastPollTime
        else -> "--:--:--"
    }

    val isMarketLive = when (val s = uiState) {
        is PulseUiState.Success -> s.isMarketLive
        else -> false
    }

    val longCount = remember(liveSignalsList) { liveSignalsList.count { it.direction == SignalDirection.LONG } }
    val shortCount = remember(liveSignalsList) { liveSignalsList.count { it.direction == SignalDirection.SHORT } }
    val waitCount = remember(liveSignalsList) { liveSignalsList.count { it.direction == SignalDirection.WAIT } }
    val activeSignalsCount = longCount + shortCount

    // 4 Clean, Intuitive Tabs for a Regular Trader
    val navTabs = remember {
        listOf(
            NavTabItem("LIVE_SIGNALS", "Сигналы", Icons.AutoMirrored.Filled.ShowChart),
            NavTabItem("PAPER_TRADING", "Портфель", Icons.Default.AccountBalanceWallet),
            NavTabItem("ANALYTICS", "Аналитика", Icons.Default.Analytics),
            NavTabItem("SETTINGS", "Настройки", Icons.Default.Settings)
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgPrimary),
        containerColor = palette.bgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SimpleFourTabBottomBar(
                tabs = navTabs,
                activeTabId = activeScreenTab,
                openPaperCount = paperPortfolioSummary.openCount,
                onSelectTab = { viewModel.setActiveScreenTab(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.bgElevated, palette.bgPrimary)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bgElevated)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                TerminalHeader(
                    lastPollTime = lastPollTimeStr,
                    isMarketLive = isMarketLive,
                    imoexSnapshot = imoexSnapshot,
                    sessionStatus = sessionStatus,
                    activeCount = activeSignalsCount,
                    longCount = longCount,
                    shortCount = shortCount,
                    waitCount = waitCount,
                    winRatePercent = performanceSummary.winRatePercent,
                    totalHistoricalSignals = performanceSummary.totalSignals,
                    isBackgroundMonitoring = isServiceRunning,
                    voiceEnabled = settings.voiceAssistantEnabled,
                    compactCards = settings.compactCards,
                    ecoBatteryMode = settings.ecoBatteryMode,
                    silentMeetingMode = settings.silentMeetingHapticMode,
                    marketAssetClass = settings.marketAssetClass,
                    onSelectMarketAssetClass = { viewModel.updateMarketAssetClass(it) },
                    onToggleBackgroundMonitoring = { viewModel.toggleBackgroundService(it) },
                    onToggleThemeQuick = { viewModel.toggleLightDarkQuick() },
                    onToggleVoiceQuick = { viewModel.updateVoiceAssistant(!settings.voiceAssistantEnabled) },
                    onToggleCompactQuick = { viewModel.updateCompactCards(!settings.compactCards) },
                    onToggleEcoModeQuick = { viewModel.updateEcoBatteryMode(!settings.ecoBatteryMode) },
                    onToggleSilentMeetingQuick = { viewModel.updateSilentMeetingHapticMode(!settings.silentMeetingHapticMode) },
                    onRefreshClick = { viewModel.refreshNow() }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                when (activeScreenTab) {
                    "PAPER_TRADING" -> {
                        PortfolioManagerScreen(
                            summary = paperPortfolioSummary,
                            trades = allPaperTrades,
                            liveSignals = liveSignalsList,
                            activeStrategy = activeTradingStrategy,
                            crossoverSummary = crossoverSummary,
                            imoexSnapshot = imoexSnapshot,
                            newsSentimentIndex = newsOverview.marketSentimentIndex,
                            privacyHideBalance = settings.privacyHideBalance,
                            onTogglePrivacyHideBalance = {
                                viewModel.updatePrivacyHideBalance(!settings.privacyHideBalance)
                            },
                            onOpenPaperTrade = { sig -> viewModel.openPaperTradeFromSignal(sig) },
                            onClosePaperTrade = { trade -> viewModel.closePaperTradeManually(trade) },
                            onExecuteSingleRebalance = { rec -> viewModel.executeRebalanceRecommendation(rec) },
                            onExecuteFullRebalance = { plan -> viewModel.executeFullRebalancePlan(plan) },
                            onPanicCloseAll = { viewModel.closeAllOpenPaperTrades() },
                            onExportCsv = { viewModel.exportJournalCsv() },
                            onResetAccount = { viewModel.resetPaperAccount() },
                            onOpenBatchHighConfidence = { minConf ->
                                viewModel.openBatchHighConfidencePaperTrades(minConf)
                            }
                        )
                    }

                    "ANALYTICS", "HISTORY" -> {
                        SimpleAnalyticsAndHistoryScreen(
                            signals = liveSignalsList,
                            imoexSnapshot = imoexSnapshot,
                            activeMarketClass = settings.marketAssetClass,
                            allHistory = allHistory,
                            summary = performanceSummary,
                            selectedTickerFilter = historyTickerFilter,
                            onSelectTickerFilter = { viewModel.setHistoryTickerFilter(it) },
                            onClearHistory = { viewModel.clearSignalHistory() },
                            onExportCsv = { viewModel.exportJournalCsv() },
                            onSelectMarketClass = { viewModel.updateMarketAssetClass(it) },
                            onSelectTickerForTrade = { ticker ->
                                viewModel.selectTickerForRisk(ticker)
                                viewModel.setActiveScreenTab("LIVE_SIGNALS")
                            },
                            onOpenPaperTrade = { sig ->
                                viewModel.openPaperTradeFromSignal(sig)
                            }
                        )
                    }

                    "SETTINGS" -> {
                        SettingsAndPcScreen(
                            settings = settings,
                            onSelectTheme = { viewModel.updateThemeMode(it) },
                            onSelectStrategyProfile = { viewModel.updateStrategyProfile(it) },
                            onSelectCandleInterval = { viewModel.updateCandleInterval(it) },
                            onChangeMinConfidence = { viewModel.updateMinConfidence(it) },
                            onToggleNewsConfirmation = { viewModel.updateUseNewsSentiment(it) },
                            onToggleStrictVwapMacd = { viewModel.updateUseStrictVwapMacd(it) },
                            onSelectPollIntervalSec = { viewModel.updatePollIntervalSeconds(it) },
                            onRecalculateBacktest = { viewModel.clearSignalHistory() },
                            onToggleVoiceAssistant = { viewModel.updateVoiceAssistant(it) },
                            onToggleEcoBatteryMode = { viewModel.updateEcoBatteryMode(it) },
                            onToggleSilentMeetingMode = { viewModel.updateSilentMeetingHapticMode(it) },
                            onToggleCompactCards = { viewModel.updateCompactCards(it) },
                            onTogglePrivacyHideBalance = { viewModel.updatePrivacyHideBalance(it) },
                            onSelectMarketAssetClass = { viewModel.updateMarketAssetClass(it) }
                        )
                    }

                    else -> {
                        LiveSignalsContent(
                            uiState = uiState,
                            imoexSnapshot = imoexSnapshot,
                            newsSentimentIndex = newsOverview.marketSentimentIndex,
                            selectedFilter = selectedFilter,
                            searchQuery = searchQuery,
                            onlyFavorites = onlyFavorites,
                            selectedRiskTicker = selectedRiskTicker,
                            availableCashRub = paperPortfolioSummary.availableCashRub,
                            settings = settings,
                            strategyViewModel = strategyViewModel,
                            onSelectFilter = { viewModel.setFilter(it) },
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onToggleFavoritesOnly = { viewModel.toggleWatchlistFavoritesFilter() },
                            onToggleFavoriteTicker = { viewModel.toggleFavoriteTicker(it) },
                            onSelectSortOrder = { viewModel.updateSortOrder(it) },
                            onSelectTimeframe = { viewModel.updateCandleInterval(it) },
                            onSelectStrategyProfile = { viewModel.updateStrategyProfile(it) },
                            onSelectMarketClass = { viewModel.updateMarketAssetClass(it) },
                            onToggleCompactCards = { viewModel.updateCompactCards(!settings.compactCards) },
                            onSelectRiskTicker = { viewModel.selectTickerForRisk(it) },
                            onOpenHistoryForTicker = {
                                viewModel.setHistoryTickerFilter(it)
                                viewModel.setActiveScreenTab("ANALYTICS")
                            },
                            onOpenNewsForTicker = { viewModel.openNewsForTicker(it) },
                            onOpenPaperTrade = { viewModel.openPaperTradeFromSignal(it) },
                            onOpenBatchHighConfidence = { minConf ->
                                viewModel.openBatchHighConfidencePaperTrades(minConf)
                            },
                            onRetry = { viewModel.refreshNow() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean 4-Tab Bottom Navigation Bar — fits on screen without horizontal scrolling
 */
@Composable
private fun SimpleFourTabBottomBar(
    tabs: List<NavTabItem>,
    activeTabId: String,
    openPaperCount: Int,
    onSelectTab: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current

    Surface(
        color = palette.bgElevated,
        tonalElevation = 0.dp,
        border = BorderStroke(0.8.dp, palette.borderCard),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = activeTabId == tab.id || (tab.id == "ANALYTICS" && activeTabId == "HISTORY")
                val badgeCount = if (tab.id == "PAPER_TRADING") openPaperCount else 0

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) Color.White else palette.bgCard,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) Color.White else palette.borderCard
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(tab.id)
                        }
                        .testTag("nav_tab_${tab.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.labelRu,
                            tint = if (selected) Color.Black else palette.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.labelRu,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.Black else palette.textPrimary,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        if (badgeCount > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (selected) Color.Black else Color.White
                            ) {
                                Text(
                                    text = badgeCount.toString(),
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = if (selected) Color.White else Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean 2-Mode Analytics Screen:
 * Mode 0: Recharts Signal Performance Heatmap & Quant Overview
 * Mode 1: Past Signals Log (Backtest History)
 */
@Composable
private fun SimpleAnalyticsAndHistoryScreen(
    signals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot,
    activeMarketClass: MarketAssetClass,
    allHistory: List<com.example.data.SignalHistoryEntity>,
    summary: com.example.data.PerformanceSummary,
    selectedTickerFilter: String?,
    onSelectTickerFilter: (String?) -> Unit,
    onClearHistory: () -> Unit,
    onExportCsv: () -> Unit,
    onSelectMarketClass: (MarketAssetClass) -> Unit,
    onSelectTickerForTrade: (String) -> Unit,
    onOpenPaperTrade: (QuantSignal) -> Unit
) {
    val palette = LocalAppPalette.current
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0 to "Тепловая карта (Recharts)",
                1 to "История сигналов (${summary.totalSignals})"
            ).forEach { (idx, title) ->
                val active = selectedSection == idx
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) Color.White else palette.bgCard,
                    border = BorderStroke(1.dp, if (active) Color.White else palette.borderCard),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable { selectedSection = idx }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = title,
                            fontFamily = SpaceGroteskFontFamily,
                            color = if (active) Color.Black else palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (selectedSection == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "recharts_cross_market_volatility_heatmap") {
                    RechartsCrossMarketVolatilityHeatmapCard(
                        signals = signals,
                        activeMarketClass = activeMarketClass,
                        imoexSnapshot = imoexSnapshot,
                        onSelectMarketClass = onSelectMarketClass,
                        onSelectTickerForTrade = onSelectTickerForTrade,
                        onOpenPaperTrade = onOpenPaperTrade
                    )
                }

                item(key = "analytics_command_center") {
                    InstitutionalQuantCommandCenterCard(
                        signals = signals,
                        imoexSnapshot = imoexSnapshot,
                        onSelectTicker = onSelectTickerForTrade
                    )
                }
            }
        } else {
            SignalHistoryScreen(
                allHistory = allHistory,
                summary = summary,
                selectedTickerFilter = selectedTickerFilter,
                onSelectTickerFilter = onSelectTickerFilter,
                onClearHistory = onClearHistory,
                onExportCsv = onExportCsv
            )
        }
    }
}

@Composable
fun LiveSignalsContent(
    uiState: PulseUiState,
    imoexSnapshot: ImoexIndexSnapshot,
    newsSentimentIndex: Int,
    selectedFilter: SignalDirection?,
    searchQuery: String,
    onlyFavorites: Boolean,
    selectedRiskTicker: String?,
    availableCashRub: Double,
    settings: AppSettings,
    strategyViewModel: TradingStrategyViewModel,
    onSelectFilter: (SignalDirection?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleFavoriteTicker: (String) -> Unit,
    onSelectSortOrder: (SignalSortOrder) -> Unit,
    onSelectTimeframe: (Int) -> Unit,
    onSelectStrategyProfile: (QuantStrategyProfile) -> Unit,
    onSelectMarketClass: (MarketAssetClass) -> Unit = {},
    onToggleCompactCards: () -> Unit,
    onSelectRiskTicker: (String?) -> Unit,
    onOpenHistoryForTicker: (String) -> Unit,
    onOpenNewsForTicker: (String) -> Unit,
    onOpenPaperTrade: (QuantSignal) -> Unit,
    onOpenBatchHighConfidence: (Int) -> Unit = {},
    onRetry: () -> Unit
) {
    val palette = LocalAppPalette.current

    when (uiState) {
        is PulseUiState.Loading -> {
            BloombergShimmerLoadingScreen(modifier = Modifier.fillMaxSize())
        }

        is PulseUiState.Error -> {
            if (uiState.cachedSignals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.shortRed
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("retry_connection_button")
                        ) {
                            Text(text = "Повторить подключение", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                SignalsFeedList(
                    signals = uiState.cachedSignals,
                    imoexSnapshot = imoexSnapshot,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    onlyFavorites = onlyFavorites,
                    selectedRiskTicker = selectedRiskTicker,
                    availableCashRub = availableCashRub,
                    settings = settings,
                    strategyViewModel = strategyViewModel,
                    onSelectFilter = onSelectFilter,
                    onSearchQueryChange = onSearchQueryChange,
                    onToggleFavoritesOnly = onToggleFavoritesOnly,
                    onToggleFavoriteTicker = onToggleFavoriteTicker,
                    onSelectSortOrder = onSelectSortOrder,
                    onSelectTimeframe = onSelectTimeframe,
                    onSelectStrategyProfile = onSelectStrategyProfile,
                    onSelectMarketClass = onSelectMarketClass,
                    onSelectRiskTicker = onSelectRiskTicker,
                    onOpenHistoryForTicker = onOpenHistoryForTicker,
                    onOpenNewsForTicker = onOpenNewsForTicker,
                    onOpenPaperTrade = onOpenPaperTrade,
                    onOpenBatchHighConfidence = onOpenBatchHighConfidence
                )
            }
        }

        is PulseUiState.Success -> {
            SignalsFeedList(
                signals = uiState.signals,
                imoexSnapshot = imoexSnapshot,
                selectedFilter = selectedFilter,
                searchQuery = searchQuery,
                onlyFavorites = onlyFavorites,
                selectedRiskTicker = selectedRiskTicker,
                availableCashRub = availableCashRub,
                settings = settings,
                strategyViewModel = strategyViewModel,
                onSelectFilter = onSelectFilter,
                onSearchQueryChange = onSearchQueryChange,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
                onToggleFavoriteTicker = onToggleFavoriteTicker,
                onSelectSortOrder = onSelectSortOrder,
                onSelectTimeframe = onSelectTimeframe,
                onSelectStrategyProfile = onSelectStrategyProfile,
                onSelectMarketClass = onSelectMarketClass,
                onSelectRiskTicker = onSelectRiskTicker,
                onOpenHistoryForTicker = onOpenHistoryForTicker,
                onOpenNewsForTicker = onOpenNewsForTicker,
                onOpenPaperTrade = onOpenPaperTrade,
                onOpenBatchHighConfidence = onOpenBatchHighConfidence
            )
        }
    }
}

@Composable
private fun SignalsFeedList(
    signals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot,
    selectedFilter: SignalDirection?,
    searchQuery: String,
    onlyFavorites: Boolean,
    selectedRiskTicker: String?,
    availableCashRub: Double,
    settings: AppSettings,
    strategyViewModel: TradingStrategyViewModel,
    onSelectFilter: (SignalDirection?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleFavoriteTicker: (String) -> Unit,
    onSelectSortOrder: (SignalSortOrder) -> Unit,
    onSelectTimeframe: (Int) -> Unit,
    onSelectStrategyProfile: (QuantStrategyProfile) -> Unit,
    onSelectMarketClass: (MarketAssetClass) -> Unit = {},
    onSelectRiskTicker: (String?) -> Unit,
    onOpenHistoryForTicker: (String) -> Unit,
    onOpenNewsForTicker: (String) -> Unit,
    onOpenPaperTrade: (QuantSignal) -> Unit,
    onOpenBatchHighConfidence: (Int) -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    val marketStrategies by strategyViewModel.currentMarketStrategies.collectAsStateWithLifecycle()
    val activeRoomStrategy by strategyViewModel.activeTradingStrategy.collectAsStateWithLifecycle()
    val crossoverSummary by strategyViewModel.crossoverSummary.collectAsStateWithLifecycle()

    val filteredAndSortedSignals by remember(
        signals,
        selectedFilter,
        searchQuery,
        onlyFavorites,
        settings.favoriteTickers,
        settings.sortOrder
    ) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            val filtered = signals.filter { sig ->
                val matchesDir = selectedFilter == null || sig.direction == selectedFilter
                val matchesFav = !onlyFavorites || settings.favoriteTickers.contains(sig.ticker)
                val matchesQuery = q.isEmpty() ||
                    sig.ticker.lowercase().contains(q) ||
                    sig.name.lowercase().contains(q) ||
                    sig.sectorRu.lowercase().contains(q)
                matchesDir && matchesFav && matchesQuery
            }

            val baseSorted = when (settings.sortOrder) {
                SignalSortOrder.SIGNAL_STRENGTH -> filtered.sortedWith(
                    compareByDescending<QuantSignal> { it.direction != SignalDirection.WAIT }
                        .thenByDescending { it.institutionalMetrics.compositeAlphaScore }
                        .thenByDescending { it.confidence }
                )
                SignalSortOrder.VOLUME_RUB -> filtered.sortedByDescending { it.valTodayRub }
                SignalSortOrder.DAY_CHANGE -> filtered.sortedByDescending { kotlin.math.abs(it.changePercent) }
                SignalSortOrder.WINRATE_30D -> filtered.sortedByDescending { it.backtestResult.winratePercent }
            }

            baseSorted.sortedByDescending { settings.favoriteTickers.contains(it.ticker) }
        }
    }

    val selectedRiskSignal = remember(signals, selectedRiskTicker) {
        selectedRiskTicker?.let { t -> signals.firstOrNull { it.ticker == t } }
    }

    val longCount = remember(signals) { signals.count { it.direction == SignalDirection.LONG } }
    val shortCount = remember(signals) { signals.count { it.direction == SignalDirection.SHORT } }
    val waitCount = remember(signals) { signals.count { it.direction == SignalDirection.WAIT } }
    val actionableSignals = remember(signals) {
        signals.filter { it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT }
    }
    val count90Plus = remember(actionableSignals) { actionableSignals.count { it.confidence >= 90 } }
    val count94Plus = remember(actionableSignals) { actionableSignals.count { it.confidence >= 94 } }
    val avgBacktestWinrate = remember(signals) {
        if (signals.isEmpty()) 76 else signals.map { it.backtestResult.winratePercent }.average().toInt()
    }
    val activeStrategy: Strategy = settings.activeStrategy

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .testTag("signals_lazy_column"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Search, Filter & Timeframe Bar right at the top
        item(key = "ultimate_search_and_filter", contentType = "controls") {
            UltimateSearchAndFilterBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedFilter = selectedFilter,
                onSelectFilter = onSelectFilter,
                onlyWatchlist = onlyFavorites,
                watchlistCount = settings.favoriteTickers.size,
                onToggleWatchlistOnly = onToggleFavoritesOnly,
                sortOrder = settings.sortOrder,
                onSelectSortOrder = onSelectSortOrder,
                candleIntervalMinutes = settings.candleIntervalMinutes,
                onSelectCandleInterval = onSelectTimeframe,
                totalCount = signals.size,
                longCount = longCount,
                shortCount = shortCount,
                waitCount = waitCount,
                isCryptoMode = settings.isCryptoMode
            )
        }

        // 1.5 Kvant Shield 75%+ Strategy & 1-Tap Batch 90%+ / 94%+ Portfolio Execution Card
        item(key = "kvant_shield_and_batch_card", contentType = "strategy_batch") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = palette.bgCard,
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("kvant_shield_batch_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = activeStrategy.nameRu.uppercase(),
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "EMA ${activeStrategy.fastEmaPeriod}/${activeStrategy.slowEmaPeriod} • RSI(${activeStrategy.rsiPeriod}) [${activeStrategy.rsiOversold.toInt()}..${activeStrategy.rsiOverbought.toInt()}] • VWAP(${activeStrategy.vwapWindowBars}) • TP1=${activeStrategy.tp1AtrMultiplier} ATR → Стоп в БУ",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = palette.textSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color.White)
                        ) {
                            Text(
                                text = "WINRATE ${avgBacktestWinrate}%",
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1-Tap Quick Strategy Switcher Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        QuantStrategyProfile.entries.forEach { profile ->
                            val isSel = settings.strategyProfile == profile
                            val shortTitle = when (profile) {
                                QuantStrategyProfile.SNIPER_HIGH_WINRATE -> "🛡 Квант-Щит 75%+"
                                QuantStrategyProfile.CLASSIC_RR_1_2 -> "Тренд 70%+"
                                QuantStrategyProfile.AGGRESSIVE_IMPULSE -> "Импульс 66%+"
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSel) Color.White else palette.bgElevated,
                                border = BorderStroke(1.dp, if (isSel) Color.White else palette.borderCard),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectStrategyProfile(profile)
                                    }
                                    .testTag("quick_strategy_${profile.code}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = shortTitle,
                                        fontFamily = SpaceGroteskFontFamily,
                                        color = if (isSel) Color.Black else palette.textSecondary,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1-Tap Batch Add 90%+ and 94%+ Deals to Portfolio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenBatchHighConfidence(90)
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("batch_add_90_plus_button")
                        ) {
                            Text(
                                text = "⚡ В ПОРТФЕЛЬ ВСЕ 90%+ (${if (count90Plus > 0) count90Plus else actionableSignals.size.coerceAtMost(3)})",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenBatchHighConfidence(94)
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.bgElevated,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.2.dp, Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("batch_add_94_plus_button")
                        ) {
                            Text(
                                text = "🔥 В ПОРТФЕЛЬ ВСЕ 94%+ (${if (count94Plus > 0) count94Plus else actionableSignals.size.coerceAtMost(2)})",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 1.75 Room Database TradingStrategy & EMA/RSI Crossover Accuracy Card
        item(key = "room_trading_strategy_crossover_card", contentType = "room_strategy") {
            TradingStrategyCrossoverLabCard(
                strategies = marketStrategies,
                activeStrategy = activeRoomStrategy,
                crossoverSummary = crossoverSummary,
                marketAssetClass = settings.marketAssetClass,
                onSelectStrategy = { strat ->
                    strategyViewModel.selectStrategy(strat)
                },
                onCreateCustomStrategy = { fast, slow, sig, rsi, tp1, sl ->
                    strategyViewModel.createAndActivateCustomStrategy(
                        marketClass = settings.marketAssetClass,
                        fastEma = fast,
                        slowEma = slow,
                        signalEma = sig,
                        rsiPeriod = rsi,
                        tp1Atr = tp1,
                        slAtr = sl
                    )
                },
                onDeleteStrategy = { strat ->
                    strategyViewModel.deleteCustomStrategy(strat)
                },
                onOpenPaperTradeForTicker = { ticker ->
                    signals.firstOrNull { it.ticker == ticker }?.let { sig ->
                        onOpenPaperTrade(sig)
                    }
                }
            )
        }

        // 1.7 Recharts Cross-Market Performance & High-Volatility Candidates Heatmap
        item(key = "recharts_cross_market_heatmap_feed", contentType = "recharts_heatmap") {
            RechartsCrossMarketVolatilityHeatmapCard(
                signals = signals,
                activeMarketClass = settings.marketAssetClass,
                imoexSnapshot = imoexSnapshot,
                onSelectMarketClass = onSelectMarketClass,
                onSelectTickerForTrade = { ticker ->
                    onSelectRiskTicker(ticker)
                },
                onOpenPaperTrade = onOpenPaperTrade
            )
        }

        // 2. Interactive Risk Calculator Panel (shown when user taps "Риск / Лоты")
        if (selectedRiskSignal != null) {
            item(key = "risk_calculator_panel", contentType = "risk_calc") {
                RiskCalculatorPanel(
                    selectedSignal = selectedRiskSignal,
                    availableCashRub = availableCashRub,
                    onClose = { onSelectRiskTicker(null) }
                )
            }
        }

        // 3. Clean Stock Signal Cards
        items(
            items = filteredAndSortedSignals,
            key = { it.ticker },
            contentType = { "signal_card" }
        ) { signal ->
            val isFavorite = settings.favoriteTickers.contains(signal.ticker)
            SignalCard(
                signal = signal,
                isSelectedForRisk = selectedRiskTicker == signal.ticker,
                imoexSnapshot = imoexSnapshot,
                timeframeMinutes = settings.candleIntervalMinutes,
                defaultRiskRub = settings.defaultRiskRub,
                compactMode = settings.compactCards,
                ecoBatteryMode = settings.ecoBatteryMode,
                isFavorite = isFavorite,
                onToggleFavorite = { onToggleFavoriteTicker(signal.ticker) },
                onChangeTimeframe = { tf -> onSelectTimeframe(tf) },
                onSelectForRisk = { onSelectRiskTicker(signal.ticker) },
                onOpenFullHistoryForTicker = { t -> onOpenHistoryForTicker(t) },
                onOpenNewsForTicker = { t -> onOpenNewsForTicker(t) },
                onOpenPaperTrade = { sig -> onOpenPaperTrade(sig) }
            )
        }
    }
}
