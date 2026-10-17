package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.SignalHistoryEntity
import com.example.data.SignalHistoryRepository
import com.example.data.TradeOutcome
import com.example.quant.MoexIssClient
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

object SignalNotificationHelper {
    const val CHANNEL_SIGNALS_ID = "moex_trading_signals_channel"
    const val CHANNEL_RADAR_ID = "moex_background_radar_channel"
    const val RADAR_NOTIFICATION_ID = 9001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val signalChannel = NotificationChannel(
                CHANNEL_SIGNALS_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }

            val radarChannel = NotificationChannel(
                CHANNEL_RADAR_ID,
                "MOEX Фоновый Радар",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Непрерывный мониторинг котировок Мосбиржи в фоне"
                setShowBadge(false)
            }

            manager.createNotificationChannel(signalChannel)
            manager.createNotificationChannel(radarChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyNewTradingSignal(context: Context, signal: QuantSignal) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val emoji = if (signal.direction == SignalDirection.LONG) "🟢 LONG" else "🔴 SHORT"
        val entryFormatted = QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)
        val tpFormatted = QuantSignalEngine.formatPrice(signal.takeProfit, signal.minStep)
        val slFormatted = QuantSignalEngine.formatPrice(signal.stopLoss, signal.minStep)
        val tpPctStr = QuantSignalEngine.formatSignedPercent(abs(signal.tpPercent))
        val slPctStr = "-${String.format(java.util.Locale.US, "%.2f", abs(signal.slPercent))}%"

        val title = "$emoji СИГНАЛ • ${signal.ticker} (${signal.name})"
        val shortSummary = "Вход: $entryFormatted ₽ • TP: $tpFormatted ₽ ($tpPctStr) • SL: $slFormatted ₽ ($slPctStr)"
        val bigText = buildString {
            appendLine("Направление: $emoji (Сила сигнала: ${signal.confidence}%)")
            appendLine("Точка входа (Entry): $entryFormatted ₽")
            appendLine("Тейк-профит (TP): $tpFormatted ₽ ($tpPctStr)")
            appendLine("Стоп-лосс (SL): $slFormatted ₽ ($slPctStr)")
            append("Причина: ${signal.reason}")
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("highlight_ticker", signal.ticker)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            signal.ticker.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SIGNALS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(shortSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (signal.ticker.hashCode() and 0x7FFFFFFF) + 1000,
                notification
            )
        } catch (_: SecurityException) {
            // Ignored if permission was revoked
        }
    }

    fun buildRadarForegroundNotification(context: Context, activeSignalsCount: Int): Notification {
        ensureChannels(context)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_RADAR_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText("Опрос MOEX ISS каждые 10 сек • Активных сигналов: $activeSignalsCount")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}

object SignalEngineCoordinator {
    private val client = MoexIssClient()
    private val pollMutex = Mutex()
    private var initialBackfillCompleted = false

    private val _signals = MutableStateFlow<List<QuantSignal>>(emptyList())
    val signals: StateFlow<List<QuantSignal>> = _signals.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBackgroundRadarEnabled = MutableStateFlow(false)
    val isBackgroundRadarEnabled: StateFlow<Boolean> = _isBackgroundRadarEnabled.asStateFlow()

    fun setBackgroundRadarEnabled(enabled: Boolean) {
        _isBackgroundRadarEnabled.value = enabled
    }

    suspend fun pollOnceAndProcess(
        appContext: Context,
        sendNotificationsForNewSignals: Boolean = true
    ): List<QuantSignal> {
        return pollMutex.withLock {
            val db = AppDatabase.getInstance(appContext)
            val repo = SignalHistoryRepository(db.signalHistoryDao())

            try {
                if (_signals.value.isEmpty()) {
                    _isLoading.value = true
                }
                val fetched = client.fetchAllStockSignals()
                if (fetched.isNotEmpty()) {
                    processSignalsAndHistory(
                        appContext = appContext,
                        repo = repo,
                        freshSignals = fetched,
                        notifyUser = sendNotificationsForNewSignals
                    )
                    _signals.value = fetched
                    _lastSyncTimestamp.value = System.currentTimeMillis()
                    _errorMessage.value = null
                } else if (_signals.value.isEmpty()) {
                    _errorMessage.value = "Ожидание ответа от сервера MOEX ISS..."
                }
                _isLoading.value = false
                _signals.value
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = "Ошибка соединения с MOEX ISS: ${e.localizedMessage ?: "Проверьте сеть"}"
                _signals.value
            }
        }
    }

    private suspend fun processSignalsAndHistory(
        appContext: Context,
        repo: SignalHistoryRepository,
        freshSignals: List<QuantSignal>,
        notifyUser: Boolean
    ) {
        val now = System.currentTimeMillis()
        val existingAll = repo.getAllOnce()
        val isFirstSeed = !initialBackfillCompleted && existingAll.isEmpty()

        // 1. If database is empty on first launch, backfill historical signals from real MOEX candles
        if (!initialBackfillCompleted) {
            initialBackfillCompleted = true
            if (existingAll.isEmpty()) {
                val historicalEntities = mutableListOf<SignalHistoryEntity>()
                for (sig in freshSignals) {
                    val meta = QuantSignalEngine.TOP_MOEX_STOCKS.firstOrNull { it.ticker == sig.ticker }
                        ?: continue
                    if (sig.recentCandles.size >= 25) {
                        val scanned = QuantSignalEngine.scanHistoricalSignalsFromCandles(
                            meta = meta,
                            candles = sig.recentCandles,
                            minStep = sig.minStep
                        )
                        historicalEntities.addAll(scanned)
                    }
                }
                if (historicalEntities.isNotEmpty()) {
                    repo.insertSignals(historicalEntities)
                }
            }
        }

        // 2. Evaluate all currently ACTIVE signals against live price and latest candle high/low
        for (sig in freshSignals) {
            val activeForTicker = repo.getActiveSignalsForTicker(sig.ticker)
            val latestCandle = sig.recentCandles.lastOrNull()
            val checkHigh = maxOf(sig.currentPrice, latestCandle?.high ?: sig.currentPrice)
            val checkLow = minOf(sig.currentPrice, latestCandle?.low ?: sig.currentPrice)

            for (activeTrade in activeForTicker) {
                if (activeTrade.signalType == SignalDirection.LONG.name) {
                    if (checkLow <= activeTrade.stopLoss) {
                        val pnl = ((activeTrade.stopLoss - activeTrade.entryPrice) / activeTrade.entryPrice) * 100.0
                        repo.updateSignal(
                            activeTrade.copy(
                                status = TradeOutcome.HIT_SL.code,
                                exitPrice = activeTrade.stopLoss,
                                resolvedAt = now,
                                pnlPercent = pnl
                            )
                        )
                    } else if (checkHigh >= activeTrade.takeProfit) {
                        val pnl = ((activeTrade.takeProfit - activeTrade.entryPrice) / activeTrade.entryPrice) * 100.0
                        repo.updateSignal(
                            activeTrade.copy(
                                status = TradeOutcome.HIT_TP.code,
                                exitPrice = activeTrade.takeProfit,
                                resolvedAt = now,
                                pnlPercent = pnl
                            )
                        )
                    }
                } else if (activeTrade.signalType == SignalDirection.SHORT.name) {
                    if (checkHigh >= activeTrade.stopLoss) {
                        val pnl = -(((activeTrade.stopLoss - activeTrade.entryPrice) / activeTrade.entryPrice) * 100.0)
                        repo.updateSignal(
                            activeTrade.copy(
                                status = TradeOutcome.HIT_SL.code,
                                exitPrice = activeTrade.stopLoss,
                                resolvedAt = now,
                                pnlPercent = pnl
                            )
                        )
                    } else if (checkLow <= activeTrade.takeProfit) {
                        val pnl = ((activeTrade.entryPrice - activeTrade.takeProfit) / activeTrade.entryPrice) * 100.0
                        repo.updateSignal(
                            activeTrade.copy(
                                status = TradeOutcome.HIT_TP.code,
                                exitPrice = activeTrade.takeProfit,
                                resolvedAt = now,
                                pnlPercent = pnl
                            )
                        )
                    }
                }
            }

            // 3. Log newly generated LONG or SHORT signal if not already active or duplicated
            if (sig.direction == SignalDirection.LONG || sig.direction == SignalDirection.SHORT) {
                val latestRecorded = repo.getLatestSignalForTicker(sig.ticker)
                val isDuplicateActive = latestRecorded != null &&
                    latestRecorded.signalType == sig.direction.name &&
                    (latestRecorded.status == TradeOutcome.ACTIVE.code ||
                        abs(now - latestRecorded.timestamp) < 15 * 60 * 1000L &&
                        abs(latestRecorded.entryPrice - sig.entryPrice) / sig.entryPrice < 0.004)

                if (!isDuplicateActive) {
                    val entity = SignalHistoryEntity(
                        ticker = sig.ticker,
                        stockName = sig.name,
                        signalType = sig.direction.name,
                        entryPrice = sig.entryPrice,
                        takeProfit = sig.takeProfit,
                        stopLoss = sig.stopLoss,
                        confidence = sig.confidence,
                        reason = sig.reason,
                        rsi = sig.rsi14,
                        atr = sig.atr14,
                        timestamp = now,
                        status = TradeOutcome.ACTIVE.code,
                        exitPrice = null,
                        resolvedAt = null,
                        pnlPercent = 0.0
                    )
                    repo.insertSignal(entity)

                    if (notifyUser && !isFirstSeed) {
                        SignalNotificationHelper.notifyNewTradingSignal(appContext, sig)
                    }
                }
            }
        }

        // If on first launch the user has notifications enabled, notify the top active signal once
        if (isFirstSeed && notifyUser) {
            val topSignal = freshSignals.firstOrNull {
                it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
            }
            if (topSignal != null) {
                SignalNotificationHelper.notifyNewTradingSignal(appContext, topSignal)
            }
        }
    }
}

class SignalMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        SignalNotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RADAR) {
            SignalEngineCoordinator.setBackgroundRadarEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        SignalEngineCoordinator.setBackgroundRadarEnabled(true)
        val activeCount = SignalEngineCoordinator.signals.value.count {
            it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
        }
        val notification = SignalNotificationHelper.buildRadarForegroundNotification(this, activeCount)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SignalNotificationHelper.RADAR_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SignalNotificationHelper.RADAR_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Continue polling in service scope even if foreground promotion is restricted
        }

        if (pollingJob?.isActive != true) {
            pollingJob = serviceScope.launch {
                while (isActive) {
                    val list = SignalEngineCoordinator.pollOnceAndProcess(
                        appContext = applicationContext,
                        sendNotificationsForNewSignals = true
                    )
                    val liveCount = list.count {
                        it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
                    }
                    if (SignalNotificationHelper.hasNotificationPermission(applicationContext)) {
                        try {
                            val updatedNotification = SignalNotificationHelper.buildRadarForegroundNotification(
                                applicationContext,
                                liveCount
                            )
                            NotificationManagerCompat.from(applicationContext).notify(
                                SignalNotificationHelper.RADAR_NOTIFICATION_ID,
                                updatedNotification
                            )
                        } catch (_: SecurityException) {
                        }
                    }
                    delay(10_000L)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        SignalEngineCoordinator.setBackgroundRadarEnabled(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_RADAR = "com.example.action.START_RADAR"
        const val ACTION_STOP_RADAR = "com.example.action.STOP_RADAR"

        fun startRadar(context: Context) {
            val intent = Intent(context, SignalMonitorService::class.java).apply {
                action = ACTION_START_RADAR
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun stopRadar(context: Context) {
            val intent = Intent(context, SignalMonitorService::class.java).apply {
                action = ACTION_STOP_RADAR
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, SignalMonitorService::class.java))
            }
        }
    }
}
