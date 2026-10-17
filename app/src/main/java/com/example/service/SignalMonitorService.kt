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
import com.example.data.AppDatabase
import com.example.data.AppSettingsManager
import com.example.data.SignalHistoryDao
import com.example.data.SignalHistoryEntity
import com.example.data.TradeOutcome
import com.example.quant.MoexIssClient
import com.example.quant.MoexNewsEngine
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

class SignalMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private val issClient = MoexIssClient()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val monitorNotification = buildForegroundMonitorNotification(
            statusText = "Quant 2.0 (7 факторов + Новости): мониторинг 15 акций MOEX TQBR"
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    monitorNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, monitorNotification)
            }
        } catch (_: Exception) {
        }

        startBackgroundLoopIfNeeded()
        return START_STICKY
    }

    private fun startBackgroundLoopIfNeeded() {
        if (monitorJob?.isActive == true) return
        val dao = AppDatabase.getInstance(applicationContext).signalHistoryDao()
        AppSettingsManager.ensureInitialized(applicationContext)

        monitorJob = serviceScope.launch {
            while (isActive) {
                val currentSettings = AppSettingsManager.settings.value
                try {
                    val newsOverview = if (currentSettings.useNewsSentimentFilter) {
                        MoexNewsEngine.fetchNewsAndSentiment(emptyList())
                    } else {
                        null
                    }
                    val signals = issClient.fetchAllStockSignals(
                        settings = currentSettings,
                        tickerNewsMap = newsOverview?.tickerIntelligence.orEmpty()
                    )
                    if (signals.isNotEmpty()) {
                        processAndNotifySignals(
                            context = applicationContext,
                            dao = dao,
                            signals = signals,
                            riskAmountRub = 1500.0
                        )
                        val activeCount = signals.count {
                            it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
                        }
                        val updatedTime = signals.firstOrNull()?.updateTime ?: "--:--:--"
                        updateForegroundStatus(
                            "MOEX $updatedTime • Сигналов Quant 2.0: $activeCount из ${signals.size}"
                        )
                    }
                } catch (_: Exception) {
                }
                val intervalMs = (currentSettings.pollIntervalSeconds.coerceIn(5, 60) * 1000L)
                delay(intervalMs)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "MOEX Фоновый Мониторинг",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Непрерывный фоновый опрос котировок Мосбиржи (ISS API)"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Торговые сигналы LONG / SHORT",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Мгновенные push-уведомления о новых сигналах Quant 2.0 с прямой ссылкой на TradingView"
                enableVibration(true)
            }

            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundMonitorNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MOEX Pulse AI • Quant 2.0 Активен")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpen)
            .build()
    }

    private fun updateForegroundStatus(statusText: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundMonitorNotification(statusText))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_MONITOR_ID = "moex_pulse_monitor_channel"
        const val CHANNEL_ALERTS_ID = "moex_pulse_signal_alerts_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 9001
        private const val ACTION_STOP = "com.example.service.STOP_MONITOR"

        fun startServiceSafely(context: Context) {
            try {
                val intent = Intent(context, SignalMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        suspend fun processAndNotifySignals(
            context: Context,
            dao: SignalHistoryDao,
            signals: List<QuantSignal>,
            riskAmountRub: Double = 1500.0
        ): List<SignalHistoryEntity> {
            val nowMs = System.currentTimeMillis()
            val newlyLoggedEntities = mutableListOf<SignalHistoryEntity>()

            val allRecords = dao.getAllHistoryOnce()
            val openSignals = allRecords.filter { it.status == TradeOutcome.ACTIVE.code }
            val signalByTicker = signals.associateBy { it.ticker }

            for (openRecord in openSignals) {
                val live = signalByTicker[openRecord.ticker] ?: continue
                val currentPrice = live.currentPrice
                if (currentPrice <= 0.0) continue

                if (openRecord.signalType == "LONG") {
                    if (currentPrice >= openRecord.takeProfit) {
                        val movePct = ((openRecord.takeProfit - openRecord.entryPrice) / openRecord.entryPrice) * 100.0
                        dao.updateSignal(
                            openRecord.copy(
                                status = TradeOutcome.HIT_TP.code,
                                exitPrice = openRecord.takeProfit,
                                resolvedAt = nowMs,
                                pnlPercent = movePct
                            )
                        )
                    } else if (currentPrice <= openRecord.stopLoss) {
                        val movePct = ((openRecord.stopLoss - openRecord.entryPrice) / openRecord.entryPrice) * 100.0
                        dao.updateSignal(
                            openRecord.copy(
                                status = TradeOutcome.HIT_SL.code,
                                exitPrice = openRecord.stopLoss,
                                resolvedAt = nowMs,
                                pnlPercent = movePct
                            )
                        )
                    }
                } else if (openRecord.signalType == "SHORT") {
                    if (currentPrice <= openRecord.takeProfit) {
                        val movePct = ((openRecord.entryPrice - openRecord.takeProfit) / openRecord.entryPrice) * 100.0
                        dao.updateSignal(
                            openRecord.copy(
                                status = TradeOutcome.HIT_TP.code,
                                exitPrice = openRecord.takeProfit,
                                resolvedAt = nowMs,
                                pnlPercent = movePct
                            )
                        )
                    } else if (currentPrice >= openRecord.stopLoss) {
                        val movePct = -(((openRecord.stopLoss - openRecord.entryPrice) / openRecord.entryPrice) * 100.0)
                        dao.updateSignal(
                            openRecord.copy(
                                status = TradeOutcome.HIT_SL.code,
                                exitPrice = openRecord.stopLoss,
                                resolvedAt = nowMs,
                                pnlPercent = movePct
                            )
                        )
                    }
                }
            }

            val remainingOpenTickers = dao.getAllHistoryOnce()
                .filter { it.status == TradeOutcome.ACTIVE.code }
                .map { it.ticker }
                .toSet()

            for (sig in signals) {
                if (sig.direction != SignalDirection.LONG && sig.direction != SignalDirection.SHORT) {
                    continue
                }
                if (sig.ticker in remainingOpenTickers) {
                    continue
                }

                val latestForTicker = dao.getLatestSignalForTicker(sig.ticker)
                val cooldownExpired = latestForTicker == null ||
                    (nowMs - latestForTicker.timestamp) > 15 * 60 * 1000L ||
                    latestForTicker.signalType != sig.direction.name

                if (cooldownExpired) {
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
                        timestamp = nowMs,
                        status = TradeOutcome.ACTIVE.code,
                        exitPrice = null,
                        resolvedAt = null,
                        pnlPercent = 0.0
                    )
                    val insertedId = dao.insertSignal(entity)
                    newlyLoggedEntities.add(entity.copy(id = insertedId))
                    sendNewSignalPushNotification(
                        context = context,
                        signal = sig,
                        notificationId = insertedId.toInt(),
                        riskAmountRub = riskAmountRub
                    )
                }
            }
            return newlyLoggedEntities
        }

        fun sendNewSignalPushNotification(
            context: Context,
            signal: QuantSignal,
            notificationId: Int,
            riskAmountRub: Double = 1500.0
        ) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val existing = nm.getNotificationChannel(CHANNEL_ALERTS_ID)
                    if (existing == null) {
                        val alertChannel = NotificationChannel(
                            CHANNEL_ALERTS_ID,
                            "Торговые сигналы LONG / SHORT",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Мгновенные push-уведомления о новых сигналах Quant 2.0"
                            enableVibration(true)
                        }
                        nm.createNotificationChannel(alertChannel)
                    }
                }

                val stopDistancePerShare = abs(signal.entryPrice - signal.stopLoss)
                val rewardDistancePerShare = abs(signal.takeProfit - signal.entryPrice)
                val riskPerLot = stopDistancePerShare * signal.lotSize
                val lots = if (riskPerLot > 0.0) max(1, floor(riskAmountRub / riskPerLot).toInt()) else 1

                val potentialProfitRub = rewardDistancePerShare * signal.lotSize * lots
                val potentialLossRub = stopDistancePerShare * signal.lotSize * lots
                val potentialProfitPct = if (signal.entryPrice > 0.0) {
                    (rewardDistancePerShare / signal.entryPrice) * 100.0
                } else 0.0
                val potentialLossPct = if (signal.entryPrice > 0.0) {
                    (stopDistancePerShare / signal.entryPrice) * 100.0
                } else 0.0

                val entryStr = QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)
                val tpStr = QuantSignalEngine.formatPrice(signal.takeProfit, signal.minStep)
                val slStr = QuantSignalEngine.formatPrice(signal.stopLoss, signal.minStep)
                val profitRubStr = String.format(java.util.Locale.US, "%.0f", potentialProfitRub)
                val lossRubStr = String.format(java.util.Locale.US, "%.0f", potentialLossRub)
                val profitPctStr = QuantSignalEngine.formatSignedPercent(potentialProfitPct)
                val lossPctStr = QuantSignalEngine.formatSignedPercent(-potentialLossPct)

                val title = "${signal.direction.badgeText} • ${signal.ticker} (${signal.name}) • ${signal.confidence}% (${signal.confluencePassedCount}/7)"
                val shortBody = "Вход: $entryStr ₽ | TP: $tpStr ₽ ($profitPctStr) | SL: $slStr ₽"
                val expandedBody = buildString {
                    appendLine("Точка входа (Entry): $entryStr ₽")
                    appendLine("Тейк-профит (TP): $tpStr ₽ (Профит: +$profitRubStr ₽ / $profitPctStr)")
                    appendLine("Стоп-лосс (SL): $slStr ₽ (Риск: -$lossRubStr ₽ / $lossPctStr)")
                    appendLine("График TradingView: ${signal.tradingViewUrl}")
                    append("Конфлюэнс Quant 2.0 (${signal.confluencePassedCount}/7): ${signal.reason}")
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("highlight_ticker", signal.ticker)
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    2000 + notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val tvIntent = Intent(Intent.ACTION_VIEW, Uri.parse(signal.tradingViewUrl))
                val pendingTv = PendingIntent.getActivity(
                    context,
                    4000 + notificationId,
                    tvIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(title)
                    .setContentText(shortBody)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingOpen)
                    .addAction(
                        android.R.drawable.ic_menu_view,
                        "График TradingView ↗",
                        pendingTv
                    )
                    .build()

                nm.notify(3000 + (notificationId % 500), notification)
            } catch (_: Exception) {
            }
        }
    }
}
