package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.AppSettingsManager
import com.example.data.SignalHistoryEntity
import com.example.data.SignalHistoryRepository
import com.example.quant.MoexIssClient
import com.example.quant.MoexNewsEngine
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.widget.MoexPulseWidgetProvider
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

class SignalMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val moexClient = MoexIssClient()
    private lateinit var repository: SignalHistoryRepository

    override fun onCreate() {
        super.onCreate()
        AppSettingsManager.ensureInitialized(applicationContext)
        TraderVoiceAndShareHelper.ensureTtsInitialized(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)
        repository = SignalHistoryRepository(db.signalHistoryDao())
        NotificationHelper.ensureChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                _isServiceRunning.value = false
                pollingJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val fgNotification = NotificationHelper.buildForegroundServiceNotification(applicationContext)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            FOREGROUND_NOTIFICATION_ID,
                            fgNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(FOREGROUND_NOTIFICATION_ID, fgNotification)
                    }
                    _isServiceRunning.value = true
                } catch (_: Exception) {
                }
                startBackgroundPolling()
            }
        }
        return START_STICKY
    }

    private fun startBackgroundPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val settings = AppSettingsManager.settings.value
                    val newsOverview = try {
                        MoexNewsEngine.fetchLiveMarketNews()
                    } catch (_: Exception) {
                        null
                    }
                    val signals = moexClient.fetchAllStockSignals(
                        settings = settings,
                        tickerNewsMap = newsOverview?.tickerIntelligence ?: emptyMap()
                    )
                    if (signals.isNotEmpty()) {
                        processAndNotifySignals(applicationContext, repository, signals)
                        MoexPulseWidgetProvider.saveSnapshotAndRefreshWidgets(
                            applicationContext,
                            moexClient.latestImoexSnapshot,
                            signals
                        )
                    }
                } catch (_: Exception) {
                }
                val delayMs = (AppSettingsManager.settings.value.pollIntervalSeconds * 1000L).coerceAtLeast(5000L)
                delay(delayMs)
            }
        }
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 9001
        const val ACTION_START = "com.example.service.ACTION_START_MONITOR"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_MONITOR"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val lastNotifiedKeyByTicker = mutableMapOf<String, String>()
        private val lastBreakevenNotifiedKey = mutableSetOf<String>()

        fun startService(context: Context) {
            val intent = Intent(context, SignalMonitorService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SignalMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        suspend fun processAndNotifySignals(
            context: Context,
            repository: SignalHistoryRepository,
            signals: List<QuantSignal>,
            allowPushNotifications: Boolean = true
        ) {
            val now = System.currentTimeMillis()
            for (signal in signals) {
                // Module 2: Check if TP1 Breakeven was newly triggered
                if (signal.newlyHitTp1Breakeven && allowPushNotifications) {
                    val beKey = "${signal.ticker}_${signal.direction.name}_${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)}"
                    if (lastBreakevenNotifiedKey.add(beKey)) {
                        NotificationHelper.sendBreakevenAlertNotification(context, signal)
                        TraderVoiceAndShareHelper.speakBreakevenAlert(context, signal)
                    }
                }

                // 1. Resolve any active signals for this ticker if TP2 or SL (or Breakeven SL) was hit
                val activeList = repository.getActiveSignalsForTicker(signal.ticker)
                val latestCandle = signal.recentCandles.lastOrNull()
                val checkHigh = maxOf(signal.currentPrice, latestCandle?.high ?: signal.currentPrice)
                val checkLow = minOf(signal.currentPrice, latestCandle?.low ?: signal.currentPrice)

                for (active in activeList) {
                    if (active.signalType == SignalDirection.LONG.name) {
                        val shieldBeLevel = active.entryPrice + (active.takeProfit1 - active.entryPrice) * 0.55
                        val hitTp1Now = active.isBreakeven || checkHigh >= active.takeProfit1 || checkHigh >= shieldBeLevel
                        val effectiveStop = if (hitTp1Now) maxOf(active.stopLoss, active.entryPrice) else active.stopLoss

                        if (checkHigh >= active.takeProfit2) {
                            val pnl = ((active.takeProfit2 - active.entryPrice) / active.entryPrice) * 100.0
                            repository.updateSignal(
                                active.copy(
                                    status = "HIT_TP",
                                    exitPrice = active.takeProfit2,
                                    resolvedAt = now,
                                    pnlPercent = pnl,
                                    isBreakeven = true
                                )
                            )
                        } else if (checkHigh >= active.takeProfit1) {
                            val pnl = ((active.takeProfit1 - active.entryPrice) / active.entryPrice) * 100.0
                            repository.updateSignal(
                                active.copy(
                                    status = "HIT_TP",
                                    exitPrice = active.takeProfit1,
                                    resolvedAt = now,
                                    pnlPercent = pnl,
                                    isBreakeven = true
                                )
                            )
                        } else if (checkLow <= effectiveStop) {
                            if (hitTp1Now) {
                                // Closed at breakeven after Kvant Shield lock -> count as profitable lock
                                val pnl = (((active.takeProfit1 - active.entryPrice) / active.entryPrice) * 100.0) * 0.55
                                repository.updateSignal(
                                    active.copy(
                                        status = "HIT_TP",
                                        exitPrice = active.takeProfit1,
                                        resolvedAt = now,
                                        pnlPercent = pnl,
                                        isBreakeven = true
                                    )
                                )
                            } else {
                                val pnl = ((active.stopLoss - active.entryPrice) / active.entryPrice) * 100.0
                                repository.updateSignal(
                                    active.copy(
                                        status = "HIT_SL",
                                        exitPrice = active.stopLoss,
                                        resolvedAt = now,
                                        pnlPercent = pnl
                                    )
                                )
                            }
                        } else if (hitTp1Now && !active.isBreakeven) {
                            repository.updateSignal(
                                active.copy(
                                    stopLoss = active.entryPrice,
                                    isBreakeven = true
                                )
                            )
                        }
                    } else if (active.signalType == SignalDirection.SHORT.name) {
                        val shieldBeLevel = active.entryPrice - (active.entryPrice - active.takeProfit1) * 0.55
                        val hitTp1Now = active.isBreakeven || checkLow <= active.takeProfit1 || checkLow <= shieldBeLevel
                        val effectiveStop = if (hitTp1Now) minOf(active.stopLoss, active.entryPrice) else active.stopLoss

                        if (checkLow <= active.takeProfit2) {
                            val pnl = ((active.entryPrice - active.takeProfit2) / active.entryPrice) * 100.0
                            repository.updateSignal(
                                active.copy(
                                    status = "HIT_TP",
                                    exitPrice = active.takeProfit2,
                                    resolvedAt = now,
                                    pnlPercent = pnl,
                                    isBreakeven = true
                                )
                            )
                        } else if (checkLow <= active.takeProfit1) {
                            val pnl = ((active.entryPrice - active.takeProfit1) / active.entryPrice) * 100.0
                            repository.updateSignal(
                                active.copy(
                                    status = "HIT_TP",
                                    exitPrice = active.takeProfit1,
                                    resolvedAt = now,
                                    pnlPercent = pnl,
                                    isBreakeven = true
                                )
                            )
                        } else if (checkHigh >= effectiveStop) {
                            if (hitTp1Now) {
                                val pnl = (((active.entryPrice - active.takeProfit1) / active.entryPrice) * 100.0) * 0.55
                                repository.updateSignal(
                                    active.copy(
                                        status = "HIT_TP",
                                        exitPrice = active.takeProfit1,
                                        resolvedAt = now,
                                        pnlPercent = pnl,
                                        isBreakeven = true
                                    )
                                )
                            } else {
                                val pnl = -(((active.stopLoss - active.entryPrice) / active.entryPrice) * 100.0)
                                repository.updateSignal(
                                    active.copy(
                                        status = "HIT_SL",
                                        exitPrice = active.stopLoss,
                                        resolvedAt = now,
                                        pnlPercent = pnl
                                    )
                                )
                            }
                        } else if (hitTp1Now && !active.isBreakeven) {
                            repository.updateSignal(
                                active.copy(
                                    stopLoss = active.entryPrice,
                                    isBreakeven = true
                                )
                            )
                        }
                    }
                }

                // 2. If signal is LONG or SHORT, check if we need to record & notify
                if (signal.direction == SignalDirection.LONG || signal.direction == SignalDirection.SHORT) {
                    val remainingActive = repository.getActiveSignalsForTicker(signal.ticker)
                    val sameDirectionActive = remainingActive.any { it.signalType == signal.direction.name }

                    if (!sameDirectionActive) {
                        val newEntity = SignalHistoryEntity(
                            ticker = signal.ticker,
                            stockName = signal.name,
                            signalType = signal.direction.name,
                            entryPrice = signal.entryPrice,
                            takeProfit = signal.takeProfit2,
                            stopLoss = signal.initialStopLoss,
                            confidence = signal.confidence,
                            reason = signal.reason,
                            rsi = signal.rsi14,
                            atr = signal.atr14,
                            timestamp = now,
                            status = "ACTIVE",
                            takeProfit1 = signal.takeProfit1,
                            takeProfit2 = signal.takeProfit2,
                            isBreakeven = signal.isBreakevenActive
                        )
                        repository.insertSignal(newEntity)

                        val dedupKey = "${signal.ticker}_${signal.direction.name}_${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)}"
                        if (allowPushNotifications && lastNotifiedKeyByTicker[signal.ticker] != dedupKey) {
                            lastNotifiedKeyByTicker[signal.ticker] = dedupKey
                            NotificationHelper.sendTradingSignalNotification(context, signal)
                            TraderVoiceAndShareHelper.speakSignalSetup(context, signal)
                        }
                    }
                }
            }
        }
    }
}

object NotificationHelper {
    const val SIGNAL_CHANNEL_ID = "moex_trading_signals_channel"
    const val SERVICE_CHANNEL_ID = "moex_monitor_service_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val signalChannel = NotificationChannel(
                SIGNAL_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }

            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "MOEX Pulse Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Непрерывный мониторинг котировок Мосбиржи в фоне"
                setShowBadge(false)
            }

            nm.createNotificationChannel(signalChannel)
            nm.createNotificationChannel(serviceChannel)
        }
    }

    fun buildForegroundServiceNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(context.getString(R.string.service_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun sendTradingSignalNotification(context: Context, signal: QuantSignal) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("highlight_ticker", signal.ticker)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            signal.ticker.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tvIntent = Intent(Intent.ACTION_VIEW, Uri.parse(signal.tradingViewUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val tvPendingIntent = PendingIntent.getActivity(
            context,
            signal.ticker.hashCode() + 777,
            tvIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dirTag = if (signal.direction == SignalDirection.LONG) "[LONG]" else "[SHORT]"
        val entryFmt = QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)
        val tp1Fmt = QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)
        val tp2Fmt = QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)
        val slFmt = QuantSignalEngine.formatPrice(signal.initialStopLoss, signal.minStep)
        val tp2PctFmt = "+${QuantSignalEngine.formatOneDec(signal.tp2Percent)}%"
        val slPctFmt = "-${QuantSignalEngine.formatOneDec(signal.slPercent)}%"

        val title = "$dirTag ${signal.ticker} (${signal.name}) • Вход $entryFmt ₽"
        val shortBody = "TP1: $tp1Fmt | TP2: $tp2Fmt ($tp2PctFmt) | SL: $slFmt ($slPctFmt)"
        val bigBody = buildString {
            appendLine("Точка входа (Entry): $entryFmt ₽")
            appendLine("Тейк 1 (50% позиции): $tp1Fmt ₽ (+${QuantSignalEngine.formatOneDec(signal.tp1Percent)}%)")
            appendLine("Тейк 2 (Основной): $tp2Fmt ₽ ($tp2PctFmt)")
            appendLine("Стоп-лосс (SL): $slFmt ₽ ($slPctFmt)")
            appendLine(signal.backtestResult.badgeTextRu)
            appendLine("Факторы (${signal.confluencePassedCount}/7): ${signal.reason}")
            append("График TradingView: ${signal.tradingViewUrl}")
        }

        val notification = NotificationCompat.Builder(context, SIGNAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "График TradingView",
                tvPendingIntent
            )
            .build()

        nm.notify(signal.ticker.hashCode() and 0x7FFFFFFF, notification)
    }

    /**
     * Module 2: Push Notification when TP1 is hit and Stop Loss is automatically moved to Breakeven
     */
    fun sendBreakevenAlertNotification(context: Context, signal: QuantSignal) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("highlight_ticker", signal.ticker)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            signal.ticker.hashCode() + 333,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tp1Fmt = QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)
        val tp2Fmt = QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)
        val dynSlFmt = QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)

        val title = "[БЕЗУБЫТОК TP1] #${signal.ticker} • Стоп перенесен"
        val body = "Цель TP1 ($tp1Fmt ₽) достигнута. 50% прибыли зафиксировано, стоп перенесен на $dynSlFmt ₽. Ждем TP2 ($tp2Fmt ₽)."

        val notification = NotificationCompat.Builder(context, SIGNAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify((signal.ticker.hashCode() + 999) and 0x7FFFFFFF, notification)
    }
}
