package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AppSettingsManager
import com.example.data.AppThemeMode
import com.example.data.ChatMessageCloud
import com.example.data.CloudSignalAndChatRepository
import com.example.data.QuantStrategyProfile
import com.example.data.SignalHistoryEntity
import com.example.quant.AiAssistantMode
import com.example.quant.AiTurn
import com.example.quant.GeminiQuantAssistant
import com.example.quant.MarketNewsItem
import com.example.quant.MoexIssClient
import com.example.quant.MoexNewsEngine
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.service.SignalMonitorService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SignalFilter(val title: String) {
    ALL("Все (15)"),
    ACTIVE("Активные"),
    LONG("LONG"),
    SHORT("SHORT"),
    WAIT("WAIT")
}

enum class TerminalTab(val title: String) {
    LIVE_SIGNALS("Сигналы"),
    NEWS_FEED("Новости"),
    HISTORY("История"),
    AI_CHAT("AI Чат"),
    SETTINGS_PC("Настройки")
}

data class PulseUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val signals: List<QuantSignal> = emptyList(),
    val newsItems: List<MarketNewsItem> = emptyList(),
    val isNewsLoading: Boolean = false,
    val selectedNewsTickerFilter: String? = null,
    val marketSentimentIndex: Int = 0,
    val marketRegimeLabel: String = "Анализ новостного фона...",
    val selectedFilter: SignalFilter = SignalFilter.ALL,
    val selectedTab: TerminalTab = TerminalTab.LIVE_SIGNALS,
    val selectedSignal: QuantSignal? = null,
    val historyFilterTicker: String? = null,
    val updatedTime: String = "--:--:--",
    val isLiveConnection: Boolean = true,
    val errorMessage: String? = null,
    val nextRefreshInSec: Int = 10,
    val depositText: String = "150000",
    val riskPercentText: String = "1.0",
    val isAuthenticated: Boolean = false,
    val currentUserUid: String? = null,
    val currentUserEmail: String? = null,
    val selectedAiMode: AiAssistantMode = AiAssistantMode.PRO_ARGON,
    val isAiSending: Boolean = false,
    val cloudSyncError: String? = null
)

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val historyDao = database.signalHistoryDao()
    private val cloudRepository = CloudSignalAndChatRepository(application)
    private val issClient = MoexIssClient()

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    val appSettings: StateFlow<AppSettings> = AppSettingsManager.settings

    val signalHistory: StateFlow<List<SignalHistoryEntity>> = historyDao.getAllHistoryFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _cloudChatMessages = MutableStateFlow<List<ChatMessageCloud>>(emptyList())
    val cloudChatMessages: StateFlow<List<ChatMessageCloud>> = _cloudChatMessages.asStateFlow()

    private var pollingJob: Job? = null
    private var cloudSignalsJob: Job? = null
    private var cloudChatJob: Job? = null
    private var hasBackfilledHistory = false

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        _uiState.update {
            it.copy(
                isAuthenticated = user != null,
                currentUserUid = user?.uid,
                currentUserEmail = user?.email ?: user?.displayName
            )
        }
        if (user != null) {
            startCloudObservers(user.uid)
        } else {
            stopCloudObservers()
        }
    }

    init {
        AppSettingsManager.ensureInitialized(application)
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    currentUserUid = currentUser.uid,
                    currentUserEmail = currentUser.email ?: currentUser.displayName
                )
            }
            startCloudObservers(currentUser.uid)
        }
        SignalMonitorService.startServiceSafely(application)
        startContinuousPolling()
    }

    private fun startCloudObservers(uid: String) {
        cloudSignalsJob?.cancel()
        cloudChatJob?.cancel()

        cloudSignalsJob = viewModelScope.launch {
            try {
                cloudRepository.observeUserSignals(uid).collect { cloudList ->
                    val existingLocal = historyDao.getAllHistoryOnce()
                    val existingKeys = existingLocal.map { "${it.ticker}_${it.entryPrice}" }.toSet()
                    val missingLocally = cloudList.filter {
                        "${it.ticker}_${it.entryPrice}" !in existingKeys
                    }.map { cloud ->
                        SignalHistoryEntity(
                            ticker = cloud.ticker,
                            stockName = cloud.stockName,
                            signalType = cloud.signalType,
                            entryPrice = cloud.entryPrice,
                            takeProfit = cloud.takeProfit,
                            stopLoss = cloud.stopLoss,
                            confidence = cloud.confidence.toInt(),
                            reason = cloud.reason,
                            rsi = 50.0,
                            atr = 1.0,
                            timestamp = cloud.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            status = cloud.status,
                            exitPrice = null,
                            resolvedAt = cloud.updatedAt?.toDate()?.time,
                            pnlPercent = cloud.pnlPercent
                        )
                    }
                    if (missingLocally.isNotEmpty()) {
                        historyDao.insertSignals(missingLocally)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSyncError = e.localizedMessage) }
            }
        }

        cloudChatJob = viewModelScope.launch {
            try {
                cloudRepository.observeChatMessages(uid).collect { msgs ->
                    _cloudChatMessages.value = msgs
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSyncError = e.localizedMessage) }
            }
        }
    }

    private fun stopCloudObservers() {
        cloudSignalsJob?.cancel()
        cloudChatJob?.cancel()
        cloudSignalsJob = null
        cloudChatJob = null
        _cloudChatMessages.value = emptyList()
    }

    fun onAuthStateChanged() {
        val user = FirebaseAuth.getInstance().currentUser
        _uiState.update {
            it.copy(
                isAuthenticated = user != null,
                currentUserUid = user?.uid,
                currentUserEmail = user?.email ?: user?.displayName
            )
        }
        if (user != null) {
            startCloudObservers(user.uid)
        } else {
            stopCloudObservers()
        }
    }

    private fun startContinuousPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchOnce(isManual = false)
                val pollSecs = appSettings.value.pollIntervalSeconds.coerceIn(5, 60)
                for (remaining in pollSecs downTo 1) {
                    _uiState.update { it.copy(nextRefreshInSec = remaining) }
                    delay(1000L)
                }
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            fetchOnce(isManual = true)
        }
    }

    fun refreshNewsNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isNewsLoading = true) }
            val currentSignals = _uiState.value.signals
            val overview = MoexNewsEngine.fetchNewsAndSentiment(currentSignals, forceRefresh = true)
            _uiState.update {
                it.copy(
                    newsItems = overview.items,
                    marketSentimentIndex = overview.marketSentimentIndex,
                    marketRegimeLabel = overview.marketRegimeLabel,
                    isNewsLoading = false
                )
            }
        }
    }

    fun selectFilter(filter: SignalFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun selectTab(tab: TerminalTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectNewsTickerFilter(ticker: String?) {
        _uiState.update { it.copy(selectedNewsTickerFilter = ticker) }
    }

    fun selectHistoryFilterTicker(ticker: String?) {
        _uiState.update {
            it.copy(
                historyFilterTicker = ticker,
                selectedTab = TerminalTab.HISTORY
            )
        }
    }

    fun selectSignalForCalculator(signal: QuantSignal?) {
        _uiState.update { it.copy(selectedSignal = signal) }
    }

    fun updateDepositText(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(depositText = filtered) }
    }

    fun updateRiskPercentText(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(riskPercentText = filtered) }
    }

    fun selectAiMode(mode: AiAssistantMode) {
        _uiState.update { it.copy(selectedAiMode = mode) }
    }

    // Settings actions
    fun setThemeMode(mode: AppThemeMode) {
        AppSettingsManager.updateThemeMode(getApplication(), mode)
    }

    fun setStrategyProfile(profile: QuantStrategyProfile) {
        AppSettingsManager.updateStrategyProfile(getApplication(), profile)
        recalculateHistoryWithCurrentSettings()
    }

    fun setCandleInterval(interval: Int) {
        AppSettingsManager.updateCandleInterval(getApplication(), interval)
        recalculateHistoryWithCurrentSettings()
    }

    fun setMinConfidence(minConf: Int) {
        AppSettingsManager.updateMinConfidence(getApplication(), minConf)
        refreshNow()
    }

    fun setRequireNewsConfirmation(enabled: Boolean) {
        AppSettingsManager.updateUseNewsSentiment(getApplication(), enabled)
        refreshNow()
    }

    fun setStrictVwapMacd(enabled: Boolean) {
        AppSettingsManager.updateUseStrictVwapMacd(getApplication(), enabled)
        refreshNow()
    }

    fun setPollIntervalSec(seconds: Int) {
        AppSettingsManager.updatePollIntervalSeconds(getApplication(), seconds)
        startContinuousPolling()
    }

    fun recalculateHistoryWithCurrentSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            historyDao.clearAllHistory()
            hasBackfilledHistory = false
            fetchOnce(isManual = true)
        }
    }

    fun analyzeNewsWithAi(newsItem: MarketNewsItem) {
        val tickersStr = newsItem.relatedTickers.joinToString(", ").ifBlank { "MOEX" }
        val prompt = "Проанализируй новость [${newsItem.sourceName}]: «${newsItem.title}». " +
            "${newsItem.summary} " +
            "Затронутые тикеры: $tickersStr. Как это повлияет на котировки, стоит ли открывать LONG/SHORT с учетом 7 факторов Quant 2.0 и какие уровни Entry/TP/SL выбрать? Дай прямые ссылки на TradingView."
        _uiState.update {
            it.copy(
                selectedTab = TerminalTab.AI_CHAT,
                selectedAiMode = AiAssistantMode.PRO_ARGON
            )
        }
        sendAiChatPrompt(prompt)
    }

    fun sendAiChatPrompt(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isAiSending) return

        val currentMode = _uiState.value.selectedAiMode
        val currentSignals = _uiState.value.signals
        val currentNews = _uiState.value.newsItems
        val depositVal = _uiState.value.depositText.toDoubleOrNull() ?: 150000.0
        val riskVal = _uiState.value.riskPercentText.toDoubleOrNull() ?: 1.0
        val historySnapshot = _cloudChatMessages.value.map {
            AiTurn(role = it.role, text = it.text)
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiSending = true) }
            try {
                cloudRepository.addChatMessage(
                    role = "user",
                    text = trimmed,
                    modelMode = currentMode.code,
                    sourcesSummary = ""
                )

                val aiResponse = GeminiQuantAssistant.sendMultiTurnMessage(
                    mode = currentMode,
                    conversationHistory = historySnapshot,
                    userPrompt = trimmed,
                    liveSignals = currentSignals,
                    newsItems = currentNews,
                    depositRub = depositVal,
                    riskPercent = riskVal
                )

                val formattedSources = aiResponse.sources.joinToString("\n") { src ->
                    "${src.title}|${src.uri}"
                }

                cloudRepository.addChatMessage(
                    role = "model",
                    text = aiResponse.replyText,
                    modelMode = currentMode.code,
                    sourcesSummary = formattedSources
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSyncError = e.localizedMessage) }
            } finally {
                _uiState.update { it.copy(isAiSending = false) }
            }
        }
    }

    fun clearAiChatHistory() {
        viewModelScope.launch {
            try {
                cloudRepository.clearChatHistory()
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSyncError = e.localizedMessage) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearAllHistory()
        }
    }

    private suspend fun fetchOnce(isManual: Boolean) {
        _uiState.update { state ->
            state.copy(
                isLoading = state.signals.isEmpty(),
                isRefreshing = isManual || state.signals.isNotEmpty(),
                errorMessage = null
            )
        }

        val currentSettings = appSettings.value
        val existingSignals = _uiState.value.signals
        val preliminaryNews = MoexNewsEngine.fetchNewsAndSentiment(existingSignals, forceRefresh = isManual)

        val liveSignals = issClient.fetchAllStockSignals(
            settings = currentSettings,
            tickerNewsMap = preliminaryNews.tickerIntelligence
        )

        if (liveSignals.isNotEmpty()) {
            val context = getApplication<Application>()

            if (!hasBackfilledHistory) {
                hasBackfilledHistory = true
                if (historyDao.getAllHistoryOnce().isEmpty()) {
                    val backfilled = mutableListOf<SignalHistoryEntity>()
                    for (sig in liveSignals) {
                        val meta = QuantSignalEngine.TOP_MOEX_STOCKS.find { it.ticker == sig.ticker } ?: continue
                        val scanned = QuantSignalEngine.scanHistoricalSignalsFromCandles(
                            meta = meta,
                            minStep = sig.minStep,
                            candles = sig.recentCandles,
                            settings = currentSettings,
                            newsScore = sig.newsSentimentScore
                        )
                        backfilled.addAll(scanned)
                    }
                    val sortedBackfill = backfilled.sortedByDescending { it.timestamp }.take(24)
                    if (sortedBackfill.isNotEmpty()) {
                        historyDao.insertSignals(sortedBackfill)
                        sortedBackfill.take(12).forEach { entity ->
                            try {
                                cloudRepository.createSignalLog(
                                    ticker = entity.ticker,
                                    stockName = entity.stockName,
                                    signalType = entity.signalType,
                                    entryPrice = entity.entryPrice,
                                    takeProfit = entity.takeProfit,
                                    stopLoss = entity.stopLoss,
                                    confidence = entity.confidence,
                                    reason = entity.reason,
                                    status = entity.status,
                                    pnlPercent = entity.pnlPercent
                                )
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }

            val depositRub = _uiState.value.depositText.toDoubleOrNull() ?: 150000.0
            val riskPct = _uiState.value.riskPercentText.toDoubleOrNull() ?: 1.0
            val riskAmountRub = depositRub * (riskPct / 100.0)

            val newlyLogged = SignalMonitorService.processAndNotifySignals(
                context = context,
                dao = historyDao,
                signals = liveSignals,
                riskAmountRub = riskAmountRub
            )

            if (newlyLogged.isNotEmpty()) {
                newlyLogged.forEach { entity ->
                    try {
                        cloudRepository.createSignalLog(
                            ticker = entity.ticker,
                            stockName = entity.stockName,
                            signalType = entity.signalType,
                            entryPrice = entity.entryPrice,
                            takeProfit = entity.takeProfit,
                            stopLoss = entity.stopLoss,
                            confidence = entity.confidence,
                            reason = entity.reason,
                            status = entity.status,
                            pnlPercent = entity.pnlPercent
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            val enrichedNews = MoexNewsEngine.fetchNewsAndSentiment(liveSignals, forceRefresh = false)
            val nowTimeStr = liveSignals.firstOrNull()?.updateTime
                ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            _uiState.update { current ->
                val refreshedSelected = current.selectedSignal?.let { prev ->
                    liveSignals.find { it.ticker == prev.ticker }
                }
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    signals = liveSignals,
                    newsItems = enrichedNews.items,
                    marketSentimentIndex = enrichedNews.marketSentimentIndex,
                    marketRegimeLabel = enrichedNews.marketRegimeLabel,
                    selectedSignal = refreshedSelected,
                    updatedTime = nowTimeStr,
                    isLiveConnection = true,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLiveConnection = false,
                    errorMessage = "Не удалось загрузить данные MOEX ISS"
                )
            }
        }
    }

    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        super.onCleared()
    }
}
