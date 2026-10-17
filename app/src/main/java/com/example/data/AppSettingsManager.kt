package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val code: String, val titleRu: String, val subtitleRu: String) {
    DARK("DARK", "🌙 Темная (Soft Obsidian)", "Мягкая глубокая палитра для комфортной торговли"),
    LIGHT("LIGHT", "☀️ Светлая (Alpine Daylight)", "Чистая светлая тема с высокой контрастностью"),
    SYSTEM("SYSTEM", "📱 Системная (Авто)", "Автоматическое переключение по настройкам Android");

    companion object {
        fun fromCode(code: String): AppThemeMode =
            entries.firstOrNull { it.code == code } ?: DARK
    }
}

enum class QuantStrategyProfile(
    val code: String,
    val titleRu: String,
    val descriptionRu: String,
    val slAtrMultiplier: Double,
    val tpAtrMultiplier: Double,
    val defaultMinConfidence: Int
) {
    SNIPER_HIGH_WINRATE(
        code = "SNIPER_HIGH_WINRATE",
        titleRu = "🎯 Снайпер Max Win-Rate (Quant 2.0)",
        descriptionRu = "7-факторный фильтр (EMA+VWAP+MACD+RSI+BB+ADX+Новости), защита от входа на пиках и реалистичный тейк (SL 1.5 ATR / TP 2.2 ATR)",
        slAtrMultiplier = 1.5,
        tpAtrMultiplier = 2.2,
        defaultMinConfidence = 75
    ),
    CLASSIC_RR_1_2(
        code = "CLASSIC_RR_1_2",
        titleRu = "⚖️ Классический Quant (R/R 1:2.0)",
        descriptionRu = "Классическая формула SL = 1.5 ATR, TP = 3.0 ATR с жестким 7-факторным подтверждением тренда и объема",
        slAtrMultiplier = 1.5,
        tpAtrMultiplier = 3.0,
        defaultMinConfidence = 74
    ),
    AGGRESSIVE_IMPULSE(
        code = "AGGRESSIVE_IMPULSE",
        titleRu = "🚀 Импульс Пробоя (R/R 1:2.5)",
        descriptionRu = "Удержание сильных трендовых движений (SL 1.4 ATR / TP 3.5 ATR) при высоком ADX и новостном драйвере",
        slAtrMultiplier = 1.4,
        tpAtrMultiplier = 3.5,
        defaultMinConfidence = 78
    );

    val riskRewardRatio: Double
        get() = if (slAtrMultiplier > 0) tpAtrMultiplier / slAtrMultiplier else 2.0

    companion object {
        fun fromCode(code: String): QuantStrategyProfile =
            entries.firstOrNull { it.code == code } ?: SNIPER_HIGH_WINRATE
    }
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val strategyProfile: QuantStrategyProfile = QuantStrategyProfile.SNIPER_HIGH_WINRATE,
    val minConfidenceThreshold: Int = 75,
    val candleIntervalMinutes: Int = 10, // 10 or 60
    val useNewsSentimentFilter: Boolean = true,
    val useStrictVwapMacdFilter: Boolean = true,
    val pollIntervalSeconds: Int = 10,
    val autoOpenTradingViewInBrowser: Boolean = true,
    val compactCards: Boolean = false
)

object AppSettingsManager {
    private const val PREFS_NAME = "moex_pulse_quant_settings_v2"
    private var initialized = false

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val theme = AppThemeMode.fromCode(prefs.getString("theme_mode", AppThemeMode.DARK.code) ?: AppThemeMode.DARK.code)
            val profile = QuantStrategyProfile.fromCode(
                prefs.getString("strategy_profile", QuantStrategyProfile.SNIPER_HIGH_WINRATE.code)
                    ?: QuantStrategyProfile.SNIPER_HIGH_WINRATE.code
            )
            val minConf = prefs.getInt("min_confidence", 75).coerceIn(60, 92)
            val intervalMin = prefs.getInt("candle_interval_min", 10).let { if (it == 60) 60 else 10 }
            val useNews = prefs.getBoolean("use_news_sentiment", true)
            val useVwapMacd = prefs.getBoolean("use_strict_vwap_macd", true)
            val pollSec = prefs.getInt("poll_interval_sec", 10).coerceIn(5, 60)
            val compact = prefs.getBoolean("compact_cards", false)

            _settings.value = AppSettings(
                themeMode = theme,
                strategyProfile = profile,
                minConfidenceThreshold = minConf,
                candleIntervalMinutes = intervalMin,
                useNewsSentimentFilter = useNews,
                useStrictVwapMacdFilter = useVwapMacd,
                pollIntervalSeconds = pollSec,
                compactCards = compact
            )
            initialized = true
        }
    }

    fun updateThemeMode(context: Context, mode: AppThemeMode) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(themeMode = mode)
        save(context)
    }

    fun toggleLightDarkQuick(context: Context) {
        ensureInitialized(context)
        val next = if (_settings.value.themeMode == AppThemeMode.LIGHT) {
            AppThemeMode.DARK
        } else {
            AppThemeMode.LIGHT
        }
        updateThemeMode(context, next)
    }

    fun updateStrategyProfile(context: Context, profile: QuantStrategyProfile) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(
            strategyProfile = profile,
            minConfidenceThreshold = profile.defaultMinConfidence
        )
        save(context)
    }

    fun updateMinConfidence(context: Context, threshold: Int) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(minConfidenceThreshold = threshold.coerceIn(60, 92))
        save(context)
    }

    fun updateCandleInterval(context: Context, minutes: Int) {
        ensureInitialized(context)
        val valid = if (minutes == 60) 60 else 10
        _settings.value = _settings.value.copy(candleIntervalMinutes = valid)
        save(context)
    }

    fun updateUseNewsSentiment(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(useNewsSentimentFilter = enabled)
        save(context)
    }

    fun updateUseStrictVwapMacd(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(useStrictVwapMacdFilter = enabled)
        save(context)
    }

    fun updatePollIntervalSeconds(context: Context, seconds: Int) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(pollIntervalSeconds = seconds.coerceIn(5, 60))
        save(context)
    }

    fun updateCompactCards(context: Context, compact: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(compactCards = compact)
        save(context)
    }

    private fun save(context: Context) {
        val s = _settings.value
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", s.themeMode.code)
            .putString("strategy_profile", s.strategyProfile.code)
            .putInt("min_confidence", s.minConfidenceThreshold)
            .putInt("candle_interval_min", s.candleIntervalMinutes)
            .putBoolean("use_news_sentiment", s.useNewsSentimentFilter)
            .putBoolean("use_strict_vwap_macd", s.useStrictVwapMacdFilter)
            .putInt("poll_interval_sec", s.pollIntervalSeconds)
            .putBoolean("compact_cards", s.compactCards)
            .apply()
    }
}
