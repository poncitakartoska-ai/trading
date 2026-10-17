package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PerformanceSummary
import com.example.data.SignalHistoryEntity
import com.example.data.SignalHistoryRepository
import com.example.quant.QuantSignal
import com.example.quant.SignalDirection
import com.example.service.SignalEngineCoordinator
import com.example.service.SignalMonitorService
import com.example.service.SignalNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TerminalFilterMode(val titleRu: String) {
    ALL("Все"),
    SIGNALS_ONLY("Сигналы LONG/SHORT"),
    HISTORY("История"),
    CALCULATOR("Калькулятор лотов")
}

enum class HistoryOutcomeFilter(val labelRu: String) {
    ALL("Все сделки"),
    HIT_TP("Тейк-профит (TP)"),
    HIT_SL("Стоп-лосс (SL)"),
    ACTIVE("В сделке")
}

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val historyRepository = SignalHistoryRepository(database.signalHistoryDao())

    val signals: StateFlow<List<QuantSignal>> = SignalEngineCoordinator.signals
    val isLoading: StateFlow<Boolean> = SignalEngineCoordinator.isLoading
    val lastSyncTimestamp: StateFlow<Long> = SignalEngineCoordinator.lastSyncTimestamp
    val errorMessage: StateFlow<String?> = SignalEngineCoordinator.errorMessage
    val isBackgroundRadarEnabled: StateFlow<Boolean> = SignalEngineCoordinator.isBackgroundRadarEnabled

    val allHistory: StateFlow<List<SignalHistoryEntity>> = historyRepository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val performanceSummary: StateFlow<PerformanceSummary> = allHistory
        .map { SignalHistoryRepository.computeSummary(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PerformanceSummary()
        )

    private val _secondsUntilNextScan = MutableStateFlow(10)
    val secondsUntilNextScan: StateFlow<Int> = _secondsUntilNextScan.asStateFlow()

    private val _filterMode = MutableStateFlow(TerminalFilterMode.ALL)
    val filterMode: StateFlow<TerminalFilterMode> = _filterMode.asStateFlow()

    private val _historyOutcomeFilter = MutableStateFlow(HistoryOutcomeFilter.ALL)
    val historyOutcomeFilter: StateFlow<HistoryOutcomeFilter> = _historyOutcomeFilter.asStateFlow()

    private val _selectedHistoryTicker = MutableStateFlow<String?>(null)
    val selectedHistoryTicker: StateFlow<String?> = _selectedHistoryTicker.asStateFlow()

    private val _expandedTickers = MutableStateFlow<Set<String>>(emptySet())
    val expandedTickers: StateFlow<Set<String>> = _expandedTickers.asStateFlow()

    private val _depositRubInput = MutableStateFlow("100000")
    val depositRubInput: StateFlow<String> = _depositRubInput.asStateFlow()

    private val _riskPercentInput = MutableStateFlow("1.0")
    val riskPercentInput: StateFlow<String> = _riskPercentInput.asStateFlow()

    init {
        SignalNotificationHelper.ensureChannels(application)
        startRealtimePollingLoop()
    }

    private fun startRealtimePollingLoop() {
        viewModelScope.launch {
            // Immediate initial fetch
            SignalEngineCoordinator.pollOnceAndProcess(
                appContext = getApplication(),
                sendNotificationsForNewSignals = true
            )
            _secondsUntilNextScan.value = 10

            while (isActive) {
                delay(1_000L)
                val next = _secondsUntilNextScan.value - 1
                if (next <= 0) {
                    _secondsUntilNextScan.value = 10
                    if (!SignalEngineCoordinator.isBackgroundRadarEnabled.value) {
                        SignalEngineCoordinator.pollOnceAndProcess(
                            appContext = getApplication(),
                            sendNotificationsForNewSignals = true
                        )
                    }
                } else {
                    _secondsUntilNextScan.value = next
                }
            }
        }
    }

    fun triggerManualScan() {
        viewModelScope.launch {
            _secondsUntilNextScan.value = 10
            SignalEngineCoordinator.pollOnceAndProcess(
                appContext = getApplication(),
                sendNotificationsForNewSignals = true
            )
        }
    }

    fun setFilterMode(mode: TerminalFilterMode) {
        _filterMode.value = mode
    }

    fun setHistoryOutcomeFilter(filter: HistoryOutcomeFilter) {
        _historyOutcomeFilter.value = filter
    }

    fun setSelectedHistoryTicker(ticker: String?) {
        _selectedHistoryTicker.value = ticker
    }

    fun toggleCardExpanded(ticker: String) {
        val current = _expandedTickers.value.toMutableSet()
        if (current.contains(ticker)) {
            current.remove(ticker)
        } else {
            current.add(ticker)
        }
        _expandedTickers.value = current
    }

    fun openStockHistoryScreen(ticker: String) {
        _selectedHistoryTicker.value = ticker
        _filterMode.value = TerminalFilterMode.HISTORY
    }

    fun updateDepositInput(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.length <= 12) {
            _depositRubInput.value = filtered
        }
    }

    fun updateRiskPercentInput(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.length <= 5) {
            _riskPercentInput.value = filtered
        }
    }

    fun startBackgroundRadar() {
        val app = getApplication<Application>()
        SignalMonitorService.startRadar(app)
        SignalEngineCoordinator.setBackgroundRadarEnabled(true)
    }

    fun toggleBackgroundRadar() {
        val app = getApplication<Application>()
        if (SignalEngineCoordinator.isBackgroundRadarEnabled.value) {
            SignalMonitorService.stopRadar(app)
            SignalEngineCoordinator.setBackgroundRadarEnabled(false)
        } else {
            SignalMonitorService.startRadar(app)
            SignalEngineCoordinator.setBackgroundRadarEnabled(true)
            sendLatestSignalNotificationNow()
        }
    }

    fun sendLatestSignalNotificationNow() {
        val app = getApplication<Application>()
        val list = signals.value
        val bestSignal = list.firstOrNull {
            it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
        } ?: list.firstOrNull()
        if (bestSignal != null) {
            SignalNotificationHelper.notifyNewTradingSignal(app, bestSignal)
        }
    }
}
