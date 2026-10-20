package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettingsManager
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.MoexIssClient
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Module 10: TradingView-styled Black Home Screen Widget for Android
 */
class MoexPulseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, widgetId)
        }
        // Trigger background refresh from MOEX ISS API
        triggerLiveFetchAndRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            triggerLiveFetchAndRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_MOEX_WIDGET"
        private const val PREFS_WIDGET = "moex_pulse_tv_widget_prefs"
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val COLOR_TV_GREEN = Color.parseColor("#089981")
        private val COLOR_TV_RED = Color.parseColor("#F23645")
        private val COLOR_TV_WAIT = Color.parseColor("#B2B5BE")

        fun saveSnapshotAndRefreshWidgets(
            context: Context,
            imoex: ImoexIndexSnapshot,
            signals: List<QuantSignal>
        ) {
            try {
                val top3 = signals.take(3)
                val arr = JSONArray()
                for (s in top3) {
                    val obj = JSONObject().apply {
                        put("ticker", s.ticker)
                        put("name", s.name)
                        put("dir", s.direction.name)
                        put("conf", s.confidence)
                        put("price", QuantSignalEngine.formatPrice(s.currentPrice, s.minStep))
                        put("change", QuantSignalEngine.formatSignedPercent(s.changePercent))
                        put("changeRaw", s.changePercent)
                        put("entry", QuantSignalEngine.formatPrice(s.entryPrice, s.minStep))
                        put("tp1", QuantSignalEngine.formatPrice(s.takeProfit1, s.minStep))
                        put("tp2", QuantSignalEngine.formatPrice(s.takeProfit2, s.minStep))
                        put("sl", QuantSignalEngine.formatPrice(s.dynamicStopLoss, s.minStep))
                        put("be", s.isBreakevenActive)
                        put("tvUrl", s.tradingViewUrl)
                    }
                    arr.put(obj)
                }

                context.applicationContext
                    .getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
                    .edit()
                    .putString("imoex_badge", imoex.statusTextRu)
                    .putString("update_time", signals.firstOrNull()?.updateTime ?: imoex.updateTime)
                    .putString("rows_json", arr.toString())
                    .apply()

                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, MoexPulseWidgetProvider::class.java))
                for (id in ids) {
                    updateSingleWidget(context, mgr, id)
                }
            } catch (_: Exception) {
            }
        }

        fun triggerLiveFetchAndRefresh(context: Context) {
            val appCtx = context.applicationContext
            widgetScope.launch {
                try {
                    AppSettingsManager.ensureInitialized(appCtx)
                    val client = MoexIssClient()
                    val signals = client.fetchAllStockSignals(AppSettingsManager.settings.value)
                    if (signals.isNotEmpty()) {
                        saveSnapshotAndRefreshWidgets(appCtx, client.latestImoexSnapshot, signals)
                    }
                } catch (_: Exception) {
                }
            }
        }

        fun requestPinTradingViewWidget(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, MoexPulseWidgetProvider::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported) {
                val pinnedCallback = PendingIntent.getBroadcast(
                    context,
                    701,
                    Intent(context, MoexPulseWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH_WIDGET
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val ok = mgr.requestPinAppWidget(provider, null, pinnedCallback)
                if (ok) {
                    Toast.makeText(
                        context,
                        "Подтвердите добавление виджета MOEX Pulse PRO на главный экран",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Добавьте виджет «Moex Pulse» через меню виджетов рабочего стола",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    "Зажмите пустое место на рабочем столе → Виджеты → Moex Pulse",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_moex_pulse_tradingview)
            val prefs = context.applicationContext.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)

            val imoexText = prefs.getString("imoex_badge", "IMOEX 2850.0 (+0.4%)") ?: "IMOEX Live"
            val upTime = prefs.getString("update_time", "--:--:--") ?: "--:--:--"
            val rowsJson = prefs.getString("rows_json", null)

            views.setTextViewText(R.id.widget_imoex_badge, imoexText)
            views.setTextViewText(R.id.widget_subtitle_time, "MOEX TQBR • Обновлено $upTime")

            // Click on widget header opens the app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openAppPending)
            views.setOnClickPendingIntent(R.id.widget_imoex_badge, openAppPending)

            // Refresh button broadcast
            val refreshIntent = Intent(context, MoexPulseWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                101,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPending)

            if (!rowsJson.isNullOrBlank()) {
                try {
                    val arr = JSONArray(rowsJson)
                    val rowIds = listOf(
                        RowViewIds(R.id.widget_row_1, R.id.widget_row1_title, R.id.widget_row1_levels, R.id.widget_row1_price, R.id.widget_row1_change),
                        RowViewIds(R.id.widget_row_2, R.id.widget_row2_title, R.id.widget_row2_levels, R.id.widget_row2_price, R.id.widget_row2_change),
                        RowViewIds(R.id.widget_row_3, R.id.widget_row3_title, R.id.widget_row3_levels, R.id.widget_row3_price, R.id.widget_row3_change)
                    )
                    for (i in 0 until minOf(3, arr.length())) {
                        val obj = arr.getJSONObject(i)
                        val rIds = rowIds[i]
                        val ticker = obj.optString("ticker", "SBER")
                        val dir = obj.optString("dir", "WAIT")
                        val conf = obj.optInt("conf", 75)
                        val price = obj.optString("price", "0.0")
                        val change = obj.optString("change", "+0.0%")
                        val changeRaw = obj.optDouble("changeRaw", 0.0)
                        val entry = obj.optString("entry", price)
                        val tp1 = obj.optString("tp1", price)
                        val tp2 = obj.optString("tp2", price)
                        val sl = obj.optString("sl", price)
                        val be = obj.optBoolean("be", false)
                        val tvUrl = obj.optString("tvUrl", QuantSignalEngine.tradingViewUrl(ticker))

                        val dirBadge = when (dir) {
                            SignalDirection.LONG.name -> "▲ $ticker • LONG ($conf%)"
                            SignalDirection.SHORT.name -> "▼ $ticker • SHORT ($conf%)"
                            else -> "• $ticker • WAIT ($conf%)"
                        }
                        val titleColor = when (dir) {
                            SignalDirection.LONG.name -> COLOR_TV_GREEN
                            SignalDirection.SHORT.name -> COLOR_TV_RED
                            else -> COLOR_TV_WAIT
                        }
                        val slPrefix = if (be) "БУ" else "SL"
                        val levelsStr = "Вх $entry • TP1 $tp1 • TP2 $tp2 • $slPrefix $sl"

                        views.setTextViewText(rIds.titleId, dirBadge)
                        views.setTextColor(rIds.titleId, titleColor)
                        views.setTextViewText(rIds.levelsId, levelsStr)
                        views.setTextViewText(rIds.priceId, "$price ₽")
                        views.setTextViewText(rIds.changeId, "$change • TV ↗")
                        views.setTextColor(
                            rIds.changeId,
                            if (changeRaw >= 0.0) COLOR_TV_GREEN else COLOR_TV_RED
                        )

                        val tvIntent = Intent(Intent.ACTION_VIEW, Uri.parse(tvUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val tvPending = PendingIntent.getActivity(
                            context,
                            200 + i,
                            tvIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(rIds.containerId, tvPending)
                    }
                } catch (_: Exception) {
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private data class RowViewIds(
            val containerId: Int,
            val titleId: Int,
            val levelsId: Int,
            val priceId: Int,
            val changeId: Int
        )
    }
}
