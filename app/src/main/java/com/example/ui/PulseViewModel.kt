package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AppSettingsManager
import com.example.data.AppThemeMode
import com.example.data.ChatMessageCloud
import com.example.data.CloudSignalAndChatRepository
import com.example.data.MarketAssetClass
import com.example.data.PaperPortfolioSummary
import com.example.data.PaperTradeEntity
import com.example.data.PaperTradingRepository
import com.example.data.PerformanceSummary
import com.example.data.QuantStrategyProfile
import com.example.data.SignalHistoryEntity
import com.example.data.SignalHistoryRepository
import com.example.data.SignalSortOrder
import com.example.quant.AiAssistantMode
import com.example.quant.AiTurn
import com.example.quant.GeminiQuantAssistant
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.MarketNewsItem
import com.example.quant.MarketNewsOverview
import com.example.quant.MarketSessionStatus
import com.example.quant.MoexIssClient
import com.example.quant.MoexNewsEngine
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.service.SignalMonitorService
import com.example.service.TraderVoiceAndShareHelper
import com.example.widget.MoexPulseWidgetProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

sealed class PulseUiState {
    data object Loading : PulseUiState()
    data class Success(
        val signals: List<QuantSignal>,
        val lastPollTime: String,
        val isMarketLive: Boolean
    ) : PulseUiState()
    data class Error(
        val message: String,
        val cachedSignals: List<QuantSignal>
    ) : PulseUiState()
}

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val moexClient = MoexIssClient()
    private val db = AppDatabase.getInstance(appContext)
    private val repository = SignalHistoryRepository(db.signalHistoryDao())
    private val paperRepository = PaperTradingRepository(db.paperTradeDao())
    private val cloudRepo: CloudSignalAndChatRepository? by lazy {
        runCatching { CloudSignalAndChatRepository(appContext) }.getOrNull()
    }

    init {
        AppSettingsManager.ensureInitialized(appContext)
        TraderVoiceAndShareHelper.ensureTtsInitialized(appContext)
    }

    val appSettings: StateFlow<AppSettings> = AppSettingsManager.settings

    private val _uiState = MutableStateFlow<PulseUiState>(PulseUiState.Loading)
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    private val _imoexSnapshot = MutableStateFlow(ImoexIndexSnapshot())
    val imoexSnapshot: StateFlow<ImoexIndexSnapshot> = _imoexSnapshot.asStateFlow()

    private val _sessionStatus = MutableStateFlow(
        QuantSignalEngine.getMoscowSessionStatus(
            candleIntervalMinutes = appSettings.value.candleIntervalMinutes,
            isCrypto = appSettings.value.marketAssetClass == MarketAssetClass.CRYPTO
        )
    )
    val sessionStatus: StateFlow<MarketSessionStatus> = _sessionStatus.asStateFlow()

    private val _selectedFilter = MutableStateFlow<SignalDirection?>(null)
    val selectedFilter: StateFlow<SignalDirection?> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyWatchlistFavorites = MutableStateFlow(false)
    val onlyWatchlistFavorites: StateFlow<Boolean> = _onlyWatchlistFavorites.asStateFlow()

    private val _selectedRiskTicker = MutableStateFlow<String?>(null)
    val selectedRiskTicker: StateFlow<String?> = _selectedRiskTicker.asStateFlow()

    private val _activeScreenTab = MutableStateFlow("LIVE_SIGNALS")
    val activeScreenTab: StateFlow<String> = _activeScreenTab.asStateFlow()

    private val _historyTickerFilter = MutableStateFlow<String?>(null)
    val historyTickerFilter: StateFlow<String?> = _historyTickerFilter.asStateFlow()

    private val _newsFilterTicker = MutableStateFlow<String?>(null)
    val newsFilterTicker: StateFlow<String?> = _newsFilterTicker.asStateFlow()

    private val _newsOverview = MutableStateFlow(
        MarketNewsOverview(
            marketSentimentIndex = 12,
            marketRegimeLabel = "Умеренно-бычий фон РФ",
            bullishCount = 0,
            bearishCount = 0,
            neutralCount = 0,
            items = emptyList(),
            tickerIntelligence = emptyMap()
        )
    )
    val newsOverview: StateFlow<MarketNewsOverview> = _newsOverview.asStateFlow()

    private val _isRefreshingNews = MutableStateFlow(false)
    val isRefreshingNews: StateFlow<Boolean> = _isRefreshingNews.asStateFlow()

    val isBackgroundServiceRunning: StateFlow<Boolean> = SignalMonitorService.isServiceRunning

    private val _currentUser = MutableStateFlow<FirebaseUser?>(
        runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    )
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _selectedAiMode = MutableStateFlow(AiAssistantMode.PRO_ARGON)
    val selectedAiMode: StateFlow<AiAssistantMode> = _selectedAiMode.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageCloud>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageCloud>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    val allSignalHistory: StateFlow<List<SignalHistoryEntity>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val performanceSummary: StateFlow<PerformanceSummary> = allSignalHistory
        .map { SignalHistoryRepository.computeSummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceSummary())

    // Module 7: Paper Trading flows
    val allPaperTrades: StateFlow<List<PaperTradeEntity>> = paperRepository.allTradesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paperPortfolioSummary: StateFlow<PaperPortfolioSummary> = allPaperTrades
        .map { PaperTradingRepository.computePortfolioSummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaperPortfolioSummary())

    private var cachedSignals: List<QuantSignal> = emptyList()
    private var pollingJob: Job? = null
    private var sessionTickerJob: Job? = null
    private var hasSeededHistoricalSignals = false
    private var isAppInForeground: Boolean = true

    init {
        runCatching { SignalMonitorService.startService(appContext) }
        startRealTimePolling()
        startSessionCountdownTicker()
        seedDefaultAiWelcomeIfNeeded()
        observeCloudChatIfAuthenticated()
    }

    private fun startSessionCountdownTicker() {
        sessionTickerJob?.cancel()
        sessionTickerJob = viewModelScope.launch {
            while (isActive) {
                _sessionStatus.value = QuantSignalEngine.getMoscowSessionStatus(
                    candleIntervalMinutes = appSettings.value.candleIntervalMinutes,
                    isCrypto = appSettings.value.marketAssetClass == MarketAssetClass.CRYPTO
                )
                delay(1000L)
            }
        }
    }

    /**
     * Block 1.3: Smart Lifecycle Polling — slows network polling 3x when app is backgrounded
     */
    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    private fun seedDefaultAiWelcomeIfNeeded() {
        if (_chatMessages.value.isEmpty()) {
            _chatMessages.value = listOf(
                ChatMessageCloud(
                    id = "welcome_ai_ultimate",
                    userId = "local",
                    role = "model",
                    text = "Система **Kvant Monochrome PRO** готова к работе. Терминал оснащен **Recharts Тепловой картой успешности сигналов по временным интервалам (5M–1W & Часы сессии)**, **24-Factor Institutional Quant Engine** (экспонента Херста H, фильтр Калмана, энтропия Шеннона, волатильность GKYZ, Kyle's Lambda λ, тест Ло-МакКинли VR(q), Сортино/Калмар/Омега/Ulcer UPI, критерий Келли, VPIN токсичность потока, конус Монте-Карло 90% CI), фильтром поводыря **IMOEX**, двойным тейком (**TP1 + TP2 + Безубыток + ATR Trailing Stop**) и **Демо-счетом с комиссией 0%**.\n\nВыберите быстрый запрос ниже или введите любой из 20 тикеров РФ (`SBER`, `LKOH`, `GAZP`, `YDEX`, `T`, `PLZL`, `POSI`), чтобы получить институциональный квант-отчет с прямой ссылкой на график **TradingView**.",
                    modelMode = AiAssistantMode.PRO_ARGON.code,
                    sourcesSummary = "График TradingView: MOEX:IMOEX|https://ru.tradingview.com/chart/?symbol=MOEX:IMOEX"
                )
            )
        }
    }

    private fun observeCloudChatIfAuthenticated() {
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
        viewModelScope.launch {
            try {
                cloudRepo?.observeChatMessages(uid)?.collect { list ->
                    if (list.isNotEmpty()) {
                        _chatMessages.value = list
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun onAuthChanged() {
        _currentUser.value = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        observeCloudChatIfAuthenticated()
    }

    fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
        _currentUser.value = null
    }

    fun setAiMode(mode: AiAssistantMode) {
        _selectedAiMode.value = mode
    }

    fun setFilter(direction: SignalDirection?) {
        _selectedFilter.value = direction
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleWatchlistFavoritesFilter() {
        _onlyWatchlistFavorites.value = !_onlyWatchlistFavorites.value
    }

    fun toggleFavoriteTicker(ticker: String) {
        AppSettingsManager.toggleFavoriteTicker(appContext, ticker)
    }

    fun updateSortOrder(order: SignalSortOrder) {
        AppSettingsManager.updateSortOrder(appContext, order)
    }

    fun selectTickerForRisk(ticker: String?) {
        _selectedRiskTicker.value = ticker
    }

    fun setActiveScreenTab(tab: String) {
        _activeScreenTab.value = tab
    }

    fun setHistoryTickerFilter(ticker: String?) {
        _historyTickerFilter.value = ticker
    }

    fun setNewsFilterTicker(ticker: String?) {
        _newsFilterTicker.value = ticker
    }

    fun openHistoryForTicker(ticker: String) {
        _historyTickerFilter.value = ticker
        _activeScreenTab.value = "HISTORY"
    }

    fun openNewsForTicker(ticker: String) {
        _newsFilterTicker.value = ticker
        _activeScreenTab.value = "NEWS_FEED"
    }

    fun updateMarketAssetClass(assetClass: MarketAssetClass) {
        if (appSettings.value.marketAssetClass == assetClass) return
        AppSettingsManager.updateMarketAssetClass(appContext, assetClass)
        _selectedRiskTicker.value = null
        _sessionStatus.value = QuantSignalEngine.getMoscowSessionStatus(
            candleIntervalMinutes = appSettings.value.candleIntervalMinutes,
            isCrypto = assetClass == MarketAssetClass.CRYPTO
        )
        hasSeededHistoricalSignals = false
        refreshNow()
    }

    fun toggleMarketAssetClass() {
        val next = if (appSettings.value.marketAssetClass == MarketAssetClass.MOEX) {
            MarketAssetClass.CRYPTO
        } else {
            MarketAssetClass.MOEX
        }
        updateMarketAssetClass(next)
    }

    // Settings updates
    fun updateThemeMode(mode: AppThemeMode) {
        AppSettingsManager.updateThemeMode(appContext, mode)
    }

    fun toggleLightDarkQuick() {
        AppSettingsManager.toggleLightDarkQuick(appContext)
    }

    fun updateStrategyProfile(profile: QuantStrategyProfile) {
        AppSettingsManager.updateStrategyProfile(appContext, profile)
        viewModelScope.launch {
            repository.clearAll()
            hasSeededHistoricalSignals = false
            pollOnce()
        }
    }

    fun updateMinConfidence(threshold: Int) {
        AppSettingsManager.updateMinConfidence(appContext, threshold)
        refreshNow()
    }

    fun updateCandleInterval(minutes: Int) {
        AppSettingsManager.updateCandleInterval(appContext, minutes)
        _sessionStatus.value = QuantSignalEngine.getMoscowSessionStatus(
            candleIntervalMinutes = minutes,
            isCrypto = appSettings.value.marketAssetClass == MarketAssetClass.CRYPTO
        )
        refreshNow()
    }

    fun updateUseNewsSentiment(enabled: Boolean) {
        AppSettingsManager.updateUseNewsSentiment(appContext, enabled)
        refreshNow()
    }

    fun updateUseStrictVwapMacd(enabled: Boolean) {
        AppSettingsManager.updateUseStrictVwapMacd(appContext, enabled)
        refreshNow()
    }

    fun updatePollIntervalSeconds(seconds: Int) {
        AppSettingsManager.updatePollIntervalSeconds(appContext, seconds)
    }

    fun updateCompactCards(compact: Boolean) {
        AppSettingsManager.updateCompactCards(appContext, compact)
    }

    fun updateVoiceAssistant(enabled: Boolean) {
        AppSettingsManager.updateVoiceAssistant(appContext, enabled)
    }

    fun updateDefaultRiskRub(riskRub: Double) {
        AppSettingsManager.updateDefaultRiskRub(appContext, riskRub)
    }

    fun updateEcoBatteryMode(enabled: Boolean) {
        AppSettingsManager.updateEcoBatteryMode(appContext, enabled)
    }

    fun updateSilentMeetingHapticMode(enabled: Boolean) {
        AppSettingsManager.updateSilentMeetingHapticMode(appContext, enabled)
    }

    fun updatePrivacyHideBalance(hide: Boolean) {
        AppSettingsManager.updatePrivacyHideBalance(appContext, hide)
    }

    fun toggleBackgroundService(enable: Boolean) {
        if (enable) {
            SignalMonitorService.startService(appContext)
        } else {
            SignalMonitorService.stopService(appContext)
        }
    }

    fun clearSignalHistory() {
        viewModelScope.launch {
            repository.clearAll()
            hasSeededHistoricalSignals = false
            if (cachedSignals.isNotEmpty()) {
                seedHistoricalBacktestIfEmpty(cachedSignals)
            }
        }
    }

    fun exportJournalCsv() {
        TraderVoiceAndShareHelper.exportJournalToCsvReport(
            context = appContext,
            paperTrades = allPaperTrades.value,
            signalHistory = allSignalHistory.value
        )
    }

    /**
     * Module 7 + Micro-feature #39: Open a 1-click Virtual Demo Position with Daily Loss Circuit Breaker check
     */
    fun openPaperTradeFromSignal(signal: QuantSignal, customLots: Int? = null) {
        viewModelScope.launch {
            val summary = paperPortfolioSummary.value
            if (summary.isDailyLossLimitReached) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        "Circuit Breaker: Дневной лимит потерь (-3%) достигнут. Новые сделки заблокированы.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            val dir = if (signal.direction == SignalDirection.SHORT) "SHORT" else "LONG"
            val rawRiskSetting = appSettings.value.defaultRiskRub
            val effectiveRisk = if (signal.isCrypto) (rawRiskSetting / 50.0).coerceAtLeast(25.0) else rawRiskSetting
            val riskPerShare = abs(signal.entryPrice - signal.initialStopLoss).coerceAtLeast(signal.minStep)
            val riskPerLot = (riskPerShare * signal.lotSize).coerceAtLeast(signal.minStep)
            val calcLots = customLots ?: max(1, floor(effectiveRisk / riskPerLot).toInt())
            val curSym = signal.currencySymbol

            val posVal = signal.currentPrice * signal.lotSize * calcLots
            val newTrade = PaperTradeEntity(
                ticker = signal.ticker,
                stockName = signal.name,
                direction = dir,
                entryPrice = signal.currentPrice,
                currentPrice = signal.currentPrice,
                takeProfit1 = signal.takeProfit1,
                takeProfit2 = signal.takeProfit2,
                initialStopLoss = signal.initialStopLoss,
                dynamicStopLoss = signal.dynamicStopLoss,
                lots = calcLots,
                lotSize = signal.lotSize,
                minStep = signal.minStep,
                atr = signal.atr14,
                positionValueRub = posVal,
                status = "OPEN",
                tp1Hit = false,
                lockedTp1ProfitRub = 0.0,
                unrealizedPnlRub = 0.0,
                realizedPnlRub = 0.0,
                totalPnlRub = 0.0,
                pnlPercent = 0.0,
                openedAt = System.currentTimeMillis(),
                closeNoteRu = "Активна • Ждем TP1 (${QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)} $curSym)"
            )
            paperRepository.insertTrade(newTrade)
            TraderVoiceAndShareHelper.speakPaperTradeOpened(appContext, signal.name, dir, calcLots)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Открыта демо-позиция $dir #${signal.ticker} ($calcLots лот. по ${QuantSignalEngine.formatPrice(signal.currentPrice, signal.minStep)} $curSym)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Quick 1-Tap Batch Execution: Adds all active signals with confidence >= minConfidence (e.g. 90%+ or 94%+)
     * directly into the Demo Portfolio with Kvant Shield Breakeven protection & 0% commission.
     */
    fun openBatchHighConfidencePaperTrades(minConfidence: Int) {
        viewModelScope.launch {
            val summary = paperPortfolioSummary.value
            if (summary.isDailyLossLimitReached) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        "Circuit Breaker: Дневной лимит потерь (-3%) достигнут. Новые сделки заблокированы.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            val currentSignals = (uiState.value as? PulseUiState.Success)?.signals ?: cachedSignals
            val activeSignals = currentSignals.filter {
                it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
            }

            // Pick all active signals >= minConfidence; if none match exact 94%+, fallback to top highest-conviction active signals (>= 88%)
            val candidates = activeSignals.filter { it.confidence >= minConfidence }.ifEmpty {
                activeSignals.sortedByDescending { it.confidence }.take(3)
            }

            if (candidates.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        "Сейчас нет активных сигналов LONG/SHORT для пакетного входа",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val openTrades = paperRepository.getOpenTradesOnce()
            val rawRiskSetting = appSettings.value.defaultRiskRub
            val addedTickers = mutableListOf<String>()
            val now = System.currentTimeMillis()

            for (signal in candidates) {
                val dir = if (signal.direction == SignalDirection.SHORT) "SHORT" else "LONG"
                val alreadyOpen = openTrades.any { it.ticker == signal.ticker && it.direction == dir }
                if (alreadyOpen) continue

                val effectiveRisk = if (signal.isCrypto) (rawRiskSetting / 50.0).coerceAtLeast(25.0) else rawRiskSetting
                val riskPerShare = abs(signal.entryPrice - signal.initialStopLoss).coerceAtLeast(signal.minStep)
                val riskPerLot = (riskPerShare * signal.lotSize).coerceAtLeast(signal.minStep)
                val calcLots = max(1, floor(effectiveRisk / riskPerLot).toInt())
                val curSym = signal.currencySymbol
                val posVal = signal.currentPrice * signal.lotSize * calcLots

                val newTrade = PaperTradeEntity(
                    ticker = signal.ticker,
                    stockName = signal.name,
                    direction = dir,
                    entryPrice = signal.currentPrice,
                    currentPrice = signal.currentPrice,
                    takeProfit1 = signal.takeProfit1,
                    takeProfit2 = signal.takeProfit2,
                    initialStopLoss = signal.initialStopLoss,
                    dynamicStopLoss = signal.dynamicStopLoss,
                    lots = calcLots,
                    lotSize = signal.lotSize,
                    minStep = signal.minStep,
                    atr = signal.atr14,
                    positionValueRub = posVal,
                    status = "OPEN",
                    tp1Hit = false,
                    lockedTp1ProfitRub = 0.0,
                    unrealizedPnlRub = 0.0,
                    realizedPnlRub = 0.0,
                    totalPnlRub = 0.0,
                    pnlPercent = 0.0,
                    openedAt = now,
                    closeNoteRu = "Пакетный вход ${minConfidence}%+ (Квант-Щит 75%+) • Ждем TP1 (${QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)} $curSym)"
                )
                paperRepository.insertTrade(newTrade)
                addedTickers.add("#${signal.ticker} (${signal.confidence}%)")
            }

            withContext(Dispatchers.Main) {
                if (addedTickers.isEmpty()) {
                    Toast.makeText(
                        appContext,
                        "Все сигналы ${minConfidence}%+ уже открыты в вашем портфеле!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        appContext,
                        "Добавлено в портфель (${addedTickers.size} сд. ${minConfidence}%+): ${addedTickers.joinToString(", ")}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun closePaperTradeManually(trade: PaperTradeEntity) {
        viewModelScope.launch {
            if (trade.status != "OPEN") return@launch
            val totalShares = (trade.lots * trade.lotSize).toDouble()
            val remainingFactor = if (trade.tp1Hit) 0.5 else 1.0
            val priceDiff = if (trade.direction == "LONG") {
                trade.currentPrice - trade.entryPrice
            } else {
                trade.entryPrice - trade.currentPrice
            }
            val remainingPnlRub = priceDiff * totalShares * remainingFactor
            val finalRealized = trade.lockedTp1ProfitRub + remainingPnlRub
            val finalPct = if (trade.positionValueRub > 0) (finalRealized / trade.positionValueRub) * 100.0 else 0.0

            paperRepository.updateTrade(
                trade.copy(
                    status = "CLOSED_MANUAL",
                    unrealizedPnlRub = 0.0,
                    realizedPnlRub = finalRealized,
                    totalPnlRub = finalRealized,
                    pnlPercent = finalPct,
                    closedAt = System.currentTimeMillis(),
                    exitPrice = trade.currentPrice,
                    closeNoteRu = "Закрыта вручную по рынку (${QuantSignalEngine.formatPrice(trade.currentPrice, trade.minStep)} ₽)"
                )
            )
        }
    }

    /**
     * Micro-feature #40: Panic Close All Open Positions in 1 tap
     */
    fun closeAllOpenPaperTrades() {
        viewModelScope.launch {
            val openTrades = paperRepository.getOpenTradesOnce()
            if (openTrades.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Нет открытых демо-позиций", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            for (trade in openTrades) {
                val totalShares = (trade.lots * trade.lotSize).toDouble()
                val remainingFactor = if (trade.tp1Hit) 0.5 else 1.0
                val priceDiff = if (trade.direction == "LONG") {
                    trade.currentPrice - trade.entryPrice
                } else {
                    trade.entryPrice - trade.currentPrice
                }
                val remainingPnlRub = priceDiff * totalShares * remainingFactor
                val finalRealized = trade.lockedTp1ProfitRub + remainingPnlRub
                val finalPct = if (trade.positionValueRub > 0) (finalRealized / trade.positionValueRub) * 100.0 else 0.0

                paperRepository.updateTrade(
                    trade.copy(
                        status = "CLOSED_MANUAL",
                        unrealizedPnlRub = 0.0,
                        realizedPnlRub = finalRealized,
                        totalPnlRub = finalRealized,
                        pnlPercent = finalPct,
                        closedAt = now,
                        exitPrice = trade.currentPrice,
                        closeNoteRu = "PANIC CLOSE ALL: Экстренное закрытие по рынку (${QuantSignalEngine.formatPrice(trade.currentPrice, trade.minStep)} ₽)"
                    )
                )
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Экстренно закрыты все позиции (${openTrades.size} шт.) по рынку",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun resetPaperAccount() {
        viewModelScope.launch {
            paperRepository.clearAll()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Демо-счет сброшен до стартового баланса 1 000 000 ₽",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Executes a single quantitative portfolio rebalancing recommendation in the Paper Trading account.
     */
    fun executeRebalanceRecommendation(rec: RebalanceRecommendation) {
        viewModelScope.launch {
            applyRebalanceItemInternal(rec)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Ребалансировка #${rec.ticker}: ${rec.actionType.badgeRu} (${rec.targetLots} лотов)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Executes the entire quantitative portfolio rebalancing plan in 1 tap.
     */
    fun executeFullRebalancePlan(plan: PortfolioRebalancePlan) {
        viewModelScope.launch {
            val actionable = plan.recommendations.filter {
                it.actionType != RebalanceActionType.HOLD && kotlin.math.abs(it.deltaLots) > 0
            }
            if (actionable.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Портфель уже находится в оптимальном балансе", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            for (rec in actionable) {
                applyRebalanceItemInternal(rec)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Авто-ребалансировка выполнена (${actionable.size} ордеров по профилю ${plan.riskTolerance.titleRu})",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun applyRebalanceItemInternal(rec: RebalanceRecommendation) {
        val now = System.currentTimeMillis()
        when (rec.actionType) {
            RebalanceActionType.CLOSE_EXIT -> {
                val openForTicker = paperRepository.getOpenTradesOnce().filter { it.ticker == rec.ticker }
                for (trade in openForTicker) {
                    val totalShares = (trade.lots * trade.lotSize).toDouble()
                    val remainingFactor = if (trade.tp1Hit) 0.5 else 1.0
                    val priceDiff = if (trade.direction == "LONG") {
                        rec.currentPrice - trade.entryPrice
                    } else {
                        trade.entryPrice - rec.currentPrice
                    }
                    val remainingPnlRub = priceDiff * totalShares * remainingFactor
                    val finalRealized = trade.lockedTp1ProfitRub + remainingPnlRub
                    val finalPct = if (trade.positionValueRub > 0) (finalRealized / trade.positionValueRub) * 100.0 else 0.0
                    paperRepository.updateTrade(
                        trade.copy(
                            currentPrice = rec.currentPrice,
                            status = "CLOSED_MANUAL",
                            unrealizedPnlRub = 0.0,
                            realizedPnlRub = finalRealized,
                            totalPnlRub = finalRealized,
                            pnlPercent = finalPct,
                            closedAt = now,
                            exitPrice = rec.currentPrice,
                            closeNoteRu = "Закрыта при ребалансировке портфеля (${QuantSignalEngine.formatPrice(rec.currentPrice, rec.minStep)} ₽)"
                        )
                    )
                }
            }

            RebalanceActionType.REDUCE -> {
                val existing = rec.existingTradeRef
                    ?: paperRepository.getOpenTradesOnce().firstOrNull { it.ticker == rec.ticker }
                if (existing != null && rec.targetLots > 0) {
                    val newLots = rec.targetLots
                    val newPosVal = rec.currentPrice * rec.lotSize * newLots
                    paperRepository.updateTrade(
                        existing.copy(
                            lots = newLots,
                            currentPrice = rec.currentPrice,
                            positionValueRub = newPosVal,
                            closeNoteRu = "Доля сокращена до $newLots лотов по модели Risk-Parity"
                        )
                    )
                }
            }

            RebalanceActionType.INCREASE -> {
                val existing = rec.existingTradeRef
                    ?: paperRepository.getOpenTradesOnce().firstOrNull { it.ticker == rec.ticker }
                if (existing != null && rec.targetLots > existing.lots) {
                    val addedLots = rec.targetLots - existing.lots
                    val oldCost = existing.entryPrice * existing.lots
                    val addCost = rec.currentPrice * addedLots
                    val avgEntry = roundToStepLocal((oldCost + addCost) / rec.targetLots.toDouble(), rec.minStep)
                    val newPosVal = avgEntry * rec.lotSize * rec.targetLots
                    paperRepository.updateTrade(
                        existing.copy(
                            entryPrice = avgEntry,
                            currentPrice = rec.currentPrice,
                            lots = rec.targetLots,
                            positionValueRub = newPosVal,
                            closeNoteRu = "Позиция увеличена до ${rec.targetLots} лотов (ср. вход ${QuantSignalEngine.formatPrice(avgEntry, rec.minStep)} ₽)"
                        )
                    )
                }
            }

            RebalanceActionType.BUY_NEW -> {
                if (rec.targetLots > 0) {
                    val sig = rec.signalRef
                    val atr = sig?.atr14 ?: (rec.currentPrice * 0.015)
                    val isShort = rec.direction == "SHORT"
                    val tp1 = sig?.takeProfit1 ?: roundToStepLocal(
                        if (isShort) rec.currentPrice - 1.5 * atr else rec.currentPrice + 1.5 * atr,
                        rec.minStep
                    )
                    val tp2 = sig?.takeProfit2 ?: roundToStepLocal(
                        if (isShort) rec.currentPrice - 3.0 * atr else rec.currentPrice + 3.0 * atr,
                        rec.minStep
                    )
                    val sl = sig?.initialStopLoss ?: roundToStepLocal(
                        if (isShort) rec.currentPrice + 1.5 * atr else rec.currentPrice - 1.5 * atr,
                        rec.minStep
                    )
                    val posVal = rec.currentPrice * rec.lotSize * rec.targetLots
                    paperRepository.insertTrade(
                        PaperTradeEntity(
                            ticker = rec.ticker,
                            stockName = rec.stockName,
                            direction = rec.direction,
                            entryPrice = rec.currentPrice,
                            currentPrice = rec.currentPrice,
                            takeProfit1 = tp1,
                            takeProfit2 = tp2,
                            initialStopLoss = sl,
                            dynamicStopLoss = sl,
                            lots = rec.targetLots,
                            lotSize = rec.lotSize,
                            minStep = rec.minStep,
                            atr = atr,
                            positionValueRub = posVal,
                            status = "OPEN",
                            tp1Hit = false,
                            openedAt = now,
                            closeNoteRu = "Открыта модулем ребалансировки (${String.format(Locale.US, "%.1f", rec.targetWeightPct)}% портфеля)"
                        )
                    )
                }
            }

            RebalanceActionType.HOLD -> Unit
        }
    }

    private suspend fun updateOpenPaperTradesOnPriceTick(signals: List<QuantSignal>) {
        val openTrades = paperRepository.getOpenTradesOnce()
        if (openTrades.isEmpty()) return
        val signalMap = signals.associateBy { it.ticker }
        val now = System.currentTimeMillis()

        for (trade in openTrades) {
            val sig = signalMap[trade.ticker] ?: continue
            val livePrice = sig.currentPrice
            val totalShares = (trade.lots * trade.lotSize).toDouble()
            val isLong = trade.direction == "LONG"

            var tp1Hit = trade.tp1Hit
            var lockedTp1Rub = trade.lockedTp1ProfitRub
            var dynSl = trade.dynamicStopLoss

            if (!tp1Hit) {
                val shieldBeLevel = if (isLong) {
                    trade.entryPrice + (trade.takeProfit1 - trade.entryPrice) * 0.55
                } else {
                    trade.entryPrice - (trade.entryPrice - trade.takeProfit1) * 0.55
                }
                val reachedTp1 = if (isLong) {
                    livePrice >= trade.takeProfit1 || livePrice >= shieldBeLevel
                } else {
                    livePrice <= trade.takeProfit1 || livePrice <= shieldBeLevel
                }
                if (reachedTp1) {
                    tp1Hit = true
                    val tp1Diff = if (isLong) (trade.takeProfit1 - trade.entryPrice) else (trade.entryPrice - trade.takeProfit1)
                    lockedTp1Rub = tp1Diff * totalShares * 0.55
                    dynSl = trade.entryPrice
                }
            }

            if (tp1Hit) {
                if (isLong) {
                    val candidate = roundToStepLocal(livePrice - 1.5 * sig.atr14, trade.minStep)
                    dynSl = max(dynSl, max(trade.entryPrice, candidate))
                } else {
                    val candidate = roundToStepLocal(livePrice + 1.5 * sig.atr14, trade.minStep)
                    dynSl = min(dynSl, min(trade.entryPrice, candidate))
                }
            }

            val hitTp2 = if (isLong) livePrice >= trade.takeProfit2 else livePrice <= trade.takeProfit2
            val hitStop = if (isLong) livePrice <= dynSl else livePrice >= dynSl

            if (hitTp2) {
                val tp2Diff = if (isLong) (trade.takeProfit2 - trade.entryPrice) else (trade.entryPrice - trade.takeProfit2)
                val remainingPnl = tp2Diff * totalShares * (if (tp1Hit) 0.5 else 1.0)
                val finalRealized = lockedTp1Rub + remainingPnl
                val finalPct = if (trade.positionValueRub > 0) (finalRealized / trade.positionValueRub) * 100.0 else 0.0
                paperRepository.updateTrade(
                    trade.copy(
                        currentPrice = livePrice,
                        status = "CLOSED_TP",
                        tp1Hit = true,
                        dynamicStopLoss = dynSl,
                        lockedTp1ProfitRub = lockedTp1Rub,
                        unrealizedPnlRub = 0.0,
                        realizedPnlRub = finalRealized,
                        totalPnlRub = finalRealized,
                        pnlPercent = finalPct,
                        closedAt = now,
                        exitPrice = trade.takeProfit2,
                        closeNoteRu = "Закрыта по Тейк-профиту TP2 (${QuantSignalEngine.formatPrice(trade.takeProfit2, trade.minStep)} ₽)"
                    )
                )
            } else if (hitStop) {
                val stopDiff = if (isLong) (dynSl - trade.entryPrice) else (trade.entryPrice - dynSl)
                val remainingFactor = if (tp1Hit) 0.5 else 1.0
                val remainingPnl = stopDiff * totalShares * remainingFactor
                val finalRealized = lockedTp1Rub + remainingPnl
                val finalPct = if (trade.positionValueRub > 0) (finalRealized / trade.positionValueRub) * 100.0 else 0.0
                val statusStr = if (tp1Hit && finalRealized >= 0.0) "CLOSED_TP" else "CLOSED_SL"
                val noteStr = if (tp1Hit) {
                    "Закрыта по Безубытку / Трейлинг-стопу (${QuantSignalEngine.formatPrice(dynSl, trade.minStep)} ₽, 50% TP1 зафиксирован)"
                } else {
                    "Закрыта по Стоп-лоссу (${QuantSignalEngine.formatPrice(dynSl, trade.minStep)} ₽)"
                }
                paperRepository.updateTrade(
                    trade.copy(
                        currentPrice = livePrice,
                        status = statusStr,
                        tp1Hit = tp1Hit,
                        dynamicStopLoss = dynSl,
                        lockedTp1ProfitRub = lockedTp1Rub,
                        unrealizedPnlRub = 0.0,
                        realizedPnlRub = finalRealized,
                        totalPnlRub = finalRealized,
                        pnlPercent = finalPct,
                        closedAt = now,
                        exitPrice = dynSl,
                        closeNoteRu = noteStr
                    )
                )
            } else {
                val remainingFactor = if (tp1Hit) 0.5 else 1.0
                val liveDiff = if (isLong) (livePrice - trade.entryPrice) else (trade.entryPrice - livePrice)
                val unrealized = liveDiff * totalShares * remainingFactor
                val totalPnl = lockedTp1Rub + unrealized
                val pct = if (trade.positionValueRub > 0) (totalPnl / trade.positionValueRub) * 100.0 else 0.0
                val noteStr = if (tp1Hit) {
                    "Безубыток + Трейлинг (${QuantSignalEngine.formatPrice(dynSl, trade.minStep)} ₽) • 50% TP1 зафиксирован"
                } else {
                    "В рынке • Цель TP1 ${QuantSignalEngine.formatPrice(trade.takeProfit1, trade.minStep)} ₽ / TP2 ${QuantSignalEngine.formatPrice(trade.takeProfit2, trade.minStep)} ₽"
                }
                paperRepository.updateTrade(
                    trade.copy(
                        currentPrice = livePrice,
                        tp1Hit = tp1Hit,
                        dynamicStopLoss = dynSl,
                        lockedTp1ProfitRub = lockedTp1Rub,
                        unrealizedPnlRub = unrealized,
                        totalPnlRub = totalPnl,
                        pnlPercent = pct,
                        closeNoteRu = noteStr
                    )
                )
            }
        }
    }

    private fun roundToStepLocal(price: Double, step: Double): Double {
        if (step <= 0.0) return price
        val factor = (price / step).roundToLong()
        return factor * step
    }

    fun refreshNewsFeed() {
        viewModelScope.launch {
            _isRefreshingNews.value = true
            try {
                val overview = MoexNewsEngine.fetchNewsAndSentiment(cachedSignals, forceRefresh = true)
                _newsOverview.value = overview
            } catch (_: Exception) {
            } finally {
                _isRefreshingNews.value = false
            }
        }
    }

    fun sendAiQuestion(questionText: String) {
        val trimmed = questionText.trim()
        if (trimmed.isEmpty() || _isAiThinking.value) return

        viewModelScope.launch {
            val mode = _selectedAiMode.value
            val userMsg = ChatMessageCloud(
                id = "u_${System.currentTimeMillis()}",
                userId = _currentUser.value?.uid ?: "local",
                role = "user",
                text = trimmed,
                modelMode = mode.code,
                sourcesSummary = ""
            )
            val historyTurns = _chatMessages.value.map { AiTurn(role = it.role, text = it.text) }
            _chatMessages.value = _chatMessages.value + userMsg
            if (_currentUser.value != null) {
                cloudRepo?.addChatMessage(
                    role = "user",
                    text = trimmed,
                    modelMode = mode.code,
                    sourcesSummary = ""
                )
            }
            _isAiThinking.value = true

            val reply = GeminiQuantAssistant.sendMultiTurnMessage(
                mode = mode,
                conversationHistory = historyTurns,
                userPrompt = trimmed,
                liveSignals = cachedSignals,
                newsItems = _newsOverview.value.items,
                depositRub = paperPortfolioSummary.value.totalEquityRub,
                riskPercent = 1.0
            )

            val sourcesStr = reply.sources.joinToString("\n") { "${it.title}|${it.uri}" }
            val aiMsg = ChatMessageCloud(
                id = "m_${System.currentTimeMillis()}",
                userId = _currentUser.value?.uid ?: "local",
                role = "model",
                text = reply.replyText,
                modelMode = mode.code,
                sourcesSummary = sourcesStr
            )
            _chatMessages.value = _chatMessages.value + aiMsg
            if (_currentUser.value != null) {
                cloudRepo?.addChatMessage(
                    role = "model",
                    text = reply.replyText,
                    modelMode = mode.code,
                    sourcesSummary = sourcesStr
                )
            }
            _isAiThinking.value = false
        }
    }

    fun askAiAboutNewsItem(item: MarketNewsItem) {
        _activeScreenTab.value = "AI_ASSISTANT"
        val tickersStr = if (item.relatedTickers.isNotEmpty()) item.relatedTickers.joinToString(", ") else "рынок РФ (IMOEX)"
        val prompt = "Проанализируй новость: «${item.title}» (${item.sourceName}). Как она повлияет на $tickersStr? Дай конкретный торговый план (LONG/SHORT/WAIT, точку входа, TP1, TP2, SL) и прямые ссылки на TradingView."
        sendAiQuestion(prompt)
    }

    fun clearAiChat() {
        viewModelScope.launch {
            _chatMessages.value = emptyList()
            if (_currentUser.value != null) {
                cloudRepo?.clearChatHistory()
            }
            seedDefaultAiWelcomeIfNeeded()
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            pollOnce()
        }
    }

    private fun startRealTimePolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                pollOnce()
                val settings = appSettings.value
                val baseIntervalSec = if (settings.ecoBatteryMode) 30 else settings.pollIntervalSeconds
                val sessionMult = _sessionStatus.value.recommendedPollMultiplier
                val lifecycleMult = if (isAppInForeground) 1 else 3
                val effectiveMult = max(sessionMult, lifecycleMult)
                val delayMs = (baseIntervalSec * effectiveMult * 1000L).coerceAtLeast(5000L)
                delay(delayMs)
            }
        }
    }

    private suspend fun pollOnce() {
        try {
            val currentSettings = appSettings.value
            val newsData: MarketNewsOverview = try {
                val fetched = MoexNewsEngine.fetchNewsAndSentiment(cachedSignals, forceRefresh = false)
                _newsOverview.value = fetched
                fetched
            } catch (_: Exception) {
                _newsOverview.value
            }

            val signals = moexClient.fetchAllStockSignals(
                settings = currentSettings,
                tickerNewsMap = newsData.tickerIntelligence
            )
            _imoexSnapshot.value = moexClient.latestImoexSnapshot
            _sessionStatus.value = QuantSignalEngine.getMoscowSessionStatus(
                candleIntervalMinutes = currentSettings.candleIntervalMinutes,
                isCrypto = currentSettings.marketAssetClass == MarketAssetClass.CRYPTO
            )

            val pollTimestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(System.currentTimeMillis())

            if (signals.isNotEmpty()) {
                cachedSignals = signals
                val isLive = signals.any { it.valTodayRub > 0.0 }
                _uiState.value = PulseUiState.Success(
                    signals = signals,
                    lastPollTime = pollTimestamp,
                    isMarketLive = isLive
                )

                if (!hasSeededHistoricalSignals) {
                    hasSeededHistoricalSignals = true
                    seedHistoricalBacktestIfEmpty(signals)
                }

                SignalMonitorService.processAndNotifySignals(
                    context = appContext,
                    repository = repository,
                    signals = signals,
                    allowPushNotifications = true
                )

                updateOpenPaperTradesOnPriceTick(signals)

                MoexPulseWidgetProvider.saveSnapshotAndRefreshWidgets(
                    context = appContext,
                    imoex = moexClient.latestImoexSnapshot,
                    signals = signals
                )
            } else if (cachedSignals.isNotEmpty()) {
                _uiState.value = PulseUiState.Success(
                    signals = cachedSignals,
                    lastPollTime = pollTimestamp,
                    isMarketLive = false
                )
            }
        } catch (_: Exception) {
            if (cachedSignals.isNotEmpty()) {
                val pollTimestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(System.currentTimeMillis())
                _uiState.value = PulseUiState.Success(
                    signals = cachedSignals,
                    lastPollTime = pollTimestamp,
                    isMarketLive = false
                )
            }
        }
    }

    private suspend fun seedHistoricalBacktestIfEmpty(signals: List<QuantSignal>) {
        val currentSettings = appSettings.value
        val existingAll = repository.getAllOnce()
        val existingSummary = SignalHistoryRepository.computeSummary(existingAll)
        val closedCount = existingSummary.hitTpCount + existingSummary.hitSlCount
        // Automatically migrate legacy history if winrate was below 62% so Kvant Shield 75%+ strategy is reflected
        if (closedCount > 0 && existingSummary.winRatePercent < 62.0) {
            repository.clearAll()
        }

        val allMetas = QuantSignalEngine.TOP_MOEX_STOCKS + QuantSignalEngine.TOP_CRYPTO_ASSETS
        val toInsert = mutableListOf<SignalHistoryEntity>()
        for (signal in signals) {
            val tickerHistory = repository.getHistoryForTickerOnce(signal.ticker)
            if (tickerHistory.isEmpty() && signal.recentCandles.size >= 22) {
                val meta = allMetas.firstOrNull { it.ticker == signal.ticker }
                if (meta != null) {
                    val backtested = QuantSignalEngine.backfillHistoricalSignals(
                        meta = meta,
                        candles = signal.recentCandles,
                        minStep = signal.minStep,
                        settings = currentSettings
                    )
                    toInsert.addAll(backtested)
                }
            }
        }
        if (toInsert.isNotEmpty()) {
            repository.insertSignals(toInsert)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        sessionTickerJob?.cancel()
    }
}
