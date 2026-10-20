package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.example.data.AppSettingsManager
import com.example.data.PaperTradeEntity
import com.example.data.SignalHistoryEntity
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Module 8: Trader Voice Assistant (Android TextToSpeech in Russian) + Silent Meeting Morse Haptics
 * Module 9: 1-Click Telegram Setup Exporter + CSV Journal Exporter + Instant Price/Ticker Clipboard
 */
object TraderVoiceAndShareHelper {

    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false
    private val pendingSpeechQueue = ArrayList<String>()

    fun ensureTtsInitialized(context: Context) {
        if (tts != null) return
        val appCtx = context.applicationContext
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ruLocale = Locale("ru", "RU")
                val res = tts?.setLanguage(ruLocale)
                isTtsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(1.03f)
                tts?.setPitch(1.0f)
                if (isTtsReady && pendingSpeechQueue.isNotEmpty()) {
                    val toSpeak = ArrayList(pendingSpeechQueue)
                    pendingSpeechQueue.clear()
                    toSpeak.forEach { text ->
                        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "moex_tts_${System.nanoTime()}")
                    }
                }
            }
        }
    }

    /**
     * Micro-feature #45: Silent Haptic Mode ("Без звука на совещании")
     * 2 short pulses = LONG, 1 long pulse = SHORT
     */
    fun triggerMorseSignalVibration(context: Context, direction: SignalDirection) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            val timings = when (direction) {
                SignalDirection.LONG -> longArrayOf(0L, 95L, 85L, 95L) // 2 short pulses (.. LONG)
                SignalDirection.SHORT -> longArrayOf(0L, 340L)         // 1 long pulse (- SHORT)
                SignalDirection.WAIT -> longArrayOf(0L, 45L)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (_: Exception) {
        }
    }

    fun copyToClipboardWithToast(context: Context, labelRu: String, valueText: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(labelRu, valueText))
            Toast.makeText(context, "Скопировано ($labelRu): $valueText", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun speakText(context: Context, text: String, forceEvenIfMuted: Boolean = false) {
        AppSettingsManager.ensureInitialized(context)
        val settings = AppSettingsManager.settings.value
        if (settings.silentMeetingHapticMode && !forceEvenIfMuted) {
            return
        }
        if (!forceEvenIfMuted && !settings.voiceAssistantEnabled) {
            return
        }
        ensureTtsInitialized(context)
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "moex_tts_${System.nanoTime()}")
        } else {
            if (pendingSpeechQueue.size < 3) {
                pendingSpeechQueue.add(text)
            }
        }
    }

    fun speakSignalSetup(context: Context, signal: QuantSignal, forceEvenIfMuted: Boolean = false) {
        AppSettingsManager.ensureInitialized(context)
        if (AppSettingsManager.settings.value.silentMeetingHapticMode) {
            triggerMorseSignalVibration(context, signal.direction)
            if (!forceEvenIfMuted) return
        }
        val dirRu = when (signal.direction) {
            SignalDirection.LONG -> "лонг"
            SignalDirection.SHORT -> "шорт"
            SignalDirection.WAIT -> "ожидание"
        }
        val entryStr = QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)
        val tp1Str = QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)
        val tp2Str = QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)
        val slStr = QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)

        val phrase = if (signal.direction == SignalDirection.WAIT) {
            "По акции ${signal.name} активного сигнала нет. Текущая цена $entryStr рублей, альфа-индекс ${signal.institutionalMetrics.compositeAlphaScore}."
        } else {
            val bePart = if (signal.isBreakevenActive) {
                "Внимание, первый тейк уже взят, стоп-лосс переведен в безубыток на уровень $slStr."
            } else {
                "Точка входа $entryStr, первый тейк $tp1Str, основной тейк $tp2Str, стоп-лосс $slStr."
            }
            "Внимание! Сигнал $dirRu по акции ${signal.name}. $bePart Альфа-рейтинг ${signal.institutionalMetrics.compositeAlphaScore} из ста. Исторический винрейт ${signal.backtestResult.winratePercent} процентов."
        }
        speakText(context, phrase, forceEvenIfMuted = forceEvenIfMuted)
    }

    fun speakBreakevenAlert(context: Context, signal: QuantSignal) {
        triggerMorseSignalVibration(context, SignalDirection.LONG)
        val slStr = QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)
        val phrase = "Защита прибыли по акции ${signal.name}! Первый тейк-профит достигнут. Стоп-лосс автоматически переведен в безубыток на отметку $slStr рублей."
        speakText(context, phrase, forceEvenIfMuted = false)
    }

    fun speakPaperTradeOpened(context: Context, stockName: String, direction: String, lots: Int) {
        val dirEnum = if (direction == "LONG") SignalDirection.LONG else SignalDirection.SHORT
        triggerMorseSignalVibration(context, dirEnum)
        val dirRu = if (direction == "LONG") "лонг" else "шорт"
        val phrase = "Демо-портфель: открыта сделка $dirRu по акции $stockName, объем $lots лотов."
        speakText(context, phrase, forceEvenIfMuted = false)
    }

    /**
     * Module 9: Format institutional setup for 1-click Telegram sharing (Clean Institutional Format)
     */
    fun buildTelegramSetupMessage(
        signal: QuantSignal,
        timeframeMinutes: Int = 10,
        riskRub: Double = 2500.0,
        imoexSnapshot: ImoexIndexSnapshot = ImoexIndexSnapshot()
    ): String {
        val dirTag = when (signal.direction) {
            SignalDirection.LONG -> "[LONG]"
            SignalDirection.SHORT -> "[SHORT]"
            SignalDirection.WAIT -> "[WAIT]"
        }
        val tfLabel = when (timeframeMinutes) {
            60 -> "1ч"
            1440 -> "1д"
            else -> "10м"
        }
        val bt = signal.backtestResult
        val iq = signal.institutionalMetrics

        val riskPerShare = abs(signal.entryPrice - signal.initialStopLoss)
        val riskPerLot = riskPerShare * signal.lotSize
        val recommendedLots = if (riskPerLot > 1e-6 && riskRub > 0) {
            max(1, floor(riskRub / riskPerLot).toInt())
        } else 1
        val totalShares = recommendedLots * signal.lotSize

        val entryFmt = QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)
        val tp1Fmt = QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)
        val tp2Fmt = QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)
        val slFmt = QuantSignalEngine.formatPrice(signal.initialStopLoss, signal.minStep)
        val dynSlFmt = QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)

        val trailingLine = when {
            signal.isTrailingStopActive -> "Трейлинг-стоп: Активен ($dynSlFmt ₽)"
            signal.isBreakevenActive -> "Стоп в Безубытке: Активен ($dynSlFmt ₽)"
            else -> "Трейлинг-стоп: Активация после TP1 ($tp1Fmt ₽)"
        }

        val emaRelation = if (signal.ema20 >= signal.ema50) "EMA20 > EMA50" else "EMA20 < EMA50"
        val imoexSign = QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)

        return buildString {
            appendLine("$dirTag #${signal.ticker} (${signal.name}) — ${signal.direction.label} (ТФ: $tfLabel)")
            appendLine("Alpha Score: ${iq.compositeAlphaScore}/100 (${iq.institutionalRating}) | Hurst H=${String.format(Locale.US, "%.2f", iq.hurstExponent)} | Kelly ${String.format(Locale.US, "%.1f", iq.optimalKellyFractionPct)}%")
            appendLine("Бэктест (30 дн): Winrate ${bt.winratePercent}% • PF ${String.format(Locale.US, "%.2f", bt.profitFactor)} • Матожидание +${String.format(Locale.US, "%.2f", bt.expectancyR)}R (${bt.wins}W / ${bt.losses}L)")
            appendLine("Режим: ${signal.marketRegimeBadge} | Z-Score: ${String.format(Locale.US, "%+.2f", signal.zScore20)}σ | ${signal.smartMoneyNote}")
            appendLine()
            appendLine("Вход (Entry): $entryFmt ₽")
            appendLine("Тейк 1 (50% позиции): $tp1Fmt ₽ (+${QuantSignalEngine.formatOneDec(signal.tp1Percent)}%) [P=${iq.probTouchTp1Pct}%]")
            appendLine("Тейк 2 (Основной): $tp2Fmt ₽ (+${QuantSignalEngine.formatOneDec(signal.tp2Percent)}%) [P=${iq.probTouchTp2Pct}%]")
            appendLine("Стоп-лосс: $slFmt ₽ (-${QuantSignalEngine.formatOneDec(signal.slPercent)}%)")
            appendLine(trailingLine)
            appendLine("Объем на риск ${riskRub.toInt()} ₽: $recommendedLots лотов ($totalShares шт.) • Комиссия 0%")
            if (signal.recentNewsBanner != null) {
                appendLine(signal.recentNewsBanner)
            }
            if (signal.hasHighRiskNewsWarning) {
                appendLine("Внимание: повышенный новостной риск (${signal.highRiskNewsReason ?: "стоп-слова"})")
            }
            appendLine()
            appendLine("Телеметрия: RSI ${QuantSignalEngine.formatOneDec(signal.rsi14)} | $emaRelation | Дельта V x${QuantSignalEngine.formatOneDec(signal.buySellVolumeRatio)} | VPIN ${String.format(Locale.US, "%.2f", iq.vpinToxicity)} | IMOEX $imoexSign")
            append("График TradingView: ${signal.tradingViewUrl}")
        }
    }

    fun shareSetupToTelegram(
        context: Context,
        signal: QuantSignal,
        timeframeMinutes: Int = 10,
        riskRub: Double = 2500.0,
        imoexSnapshot: ImoexIndexSnapshot = ImoexIndexSnapshot()
    ) {
        val message = buildTelegramSetupMessage(
            signal = signal,
            timeframeMinutes = timeframeMinutes,
            riskRub = riskRub,
            imoexSnapshot = imoexSnapshot
        )

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MOEX Setup ${signal.ticker}", message))
        } catch (_: Exception) {
        }

        val telegramPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
        val pm = context.packageManager
        for (pkg in telegramPackages) {
            try {
                val tgIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tgIntent.resolveActivity(pm) != null) {
                    context.startActivity(tgIntent)
                    return
                }
            } catch (_: Exception) {
            }
        }

        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MOEX Pulse AI PRO • #${signal.ticker}")
                putExtra(Intent.EXTRA_TEXT, message)
            }
            val chooser = Intent.createChooser(sendIntent, "Экспорт сетапа #${signal.ticker} в Telegram").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(context, "Сетап #${signal.ticker} скопирован и готов к отправке в Telegram", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Сетап #${signal.ticker} скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Micro-feature #50: Export Demo Account Trades & Signal History to CSV / Text Report
     */
    fun exportJournalToCsvReport(
        context: Context,
        paperTrades: List<PaperTradeEntity>,
        signalHistory: List<SignalHistoryEntity>
    ) {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val csv = buildString {
            appendLine("# MOEX PULSE AI PRO — ЖУРНАЛ СДЕЛОК И СИГНАЛОВ (CSV)")
            appendLine("# Дата выгрузки: ${df.format(Date())}")
            appendLine()
            appendLine("=== ДЕМО-СЧЕТ (PAPER TRADING) ===")
            appendLine("ID;Ticker;Direction;Lots;Entry;CurrentOrExit;TP1;TP2;SL;Status;TotalPnL_RUB;PnL_Pct;OpenedAt")
            for (t in paperTrades) {
                val priceOut = t.exitPrice ?: t.currentPrice
                appendLine("${t.id};${t.ticker};${t.direction};${t.lots};${t.entryPrice};$priceOut;${t.takeProfit1};${t.takeProfit2};${t.dynamicStopLoss};${t.status};${String.format(Locale.US, "%.2f", t.totalPnlRub)};${String.format(Locale.US, "%.2f", t.pnlPercent)}%;${df.format(Date(t.openedAt))}")
            }
            appendLine()
            appendLine("=== ИСТОРИЯ СИГНАЛОВ КВАНТ-РАДАРА ===")
            appendLine("ID;Ticker;Direction;Confidence;Entry;TP1;TP2;SL;Status;PnL_Pct;Timestamp")
            for (h in signalHistory.take(100)) {
                appendLine("${h.id};${h.ticker};${h.signalType};${h.confidence}%;${h.entryPrice};${h.takeProfit1};${h.takeProfit2};${h.stopLoss};${h.status};${String.format(Locale.US, "%.2f", h.pnlPercent)}%;${df.format(Date(h.timestamp))}")
            }
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MOEX_Pulse_CSV_Report", csv))
        } catch (_: Exception) {
        }

        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MOEX Pulse AI PRO — CSV Отчет сделок")
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            val chooser = Intent.createChooser(sendIntent, "Экспорт CSV-отчета сделок").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(context, "CSV-отчет скопирован и открыт для экспорта", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "CSV-отчет скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }
}
