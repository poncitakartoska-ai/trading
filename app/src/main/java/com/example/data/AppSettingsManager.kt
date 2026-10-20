package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
enum class MarketAssetClass(
    val code: String,
    val shortTitleRu: String,
    val fullTitleRu: String,
    val currencySymbol: String,
    val benchmarkName: String
) {
    MOEX(
        code = "MOEX",
        shortTitleRu = "Акции РФ",
        fullTitleRu = "Московская биржа (MOEX TQBR • ₽)",
        currencySymbol = "₽",
        benchmarkName = "IMOEX"
    ),
    CRYPTO(
        code = "CRYPTO",
        shortTitleRu = "Крипта 24/7",
        fullTitleRu = "Криптовалютный рынок (Spot & Perp • USDT)",
        currencySymbol = "$",
        benchmarkName = "BTC"
    );

    companion object {
        fun fromCode(code: String): MarketAssetClass =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MOEX
    }
}

@Immutable
enum class AppThemeMode(val code: String, val titleRu: String, val subtitleRu: String) {
    DARK("DARK", "Obsidian Dark", "Институциональная глубокая темная палитра"),
    LIGHT("LIGHT", "Carbon Dark", "Высококонтрастная темная палитра"),
    SYSTEM("SYSTEM", "AMOLED Dark", "Оптимизировано для OLED-экранов");

    companion object {
        fun fromCode(code: String): AppThemeMode =
            entries.firstOrNull { it.code == code } ?: DARK
    }
}

@Immutable
enum class SignalSortOrder(val code: String, val labelRu: String) {
    SIGNAL_STRENGTH("SIGNAL_STRENGTH", "Альфа / Сила"),
    VOLUME_RUB("VOLUME_RUB", "Объем"),
    DAY_CHANGE("DAY_CHANGE", "Импульс (%)"),
    WINRATE_30D("WINRATE_30D", "Winrate 30д");

    companion object {
        fun fromCode(code: String): SignalSortOrder =
            entries.firstOrNull { it.code == code } ?: SIGNAL_STRENGTH
    }
}

@Immutable
data class Strategy(
    val id: String,
    val nameRu: String,
    val subtitleRu: String,
    val assetClass: MarketAssetClass,
    val is24x7VolatilityTuned: Boolean,
    val fastEmaPeriod: Int,
    val slowEmaPeriod: Int,
    val rsiPeriod: Int,
    val rsiOversold: Double,
    val rsiOverbought: Double,
    val rsiBullishFloor: Double,
    val rsiBullishCeiling: Double,
    val rsiBearishFloor: Double,
    val rsiBearishCeiling: Double,
    val atrPeriod: Int,
    val tp1AtrMultiplier: Double,
    val tp2AtrMultiplier: Double,
    val slAtrMultiplier: Double,
    val breakevenLockFraction: Double,
    val vwapWindowBars: Int,
    val adxTrendThreshold: Double,
    val zScoreExtremeThreshold: Double,
    val volumeSpikeMultiplier: Double,
    val expectedWinrateRangeRu: String
) {
    companion object {
        fun forMarket(
            assetClass: MarketAssetClass,
            profile: QuantStrategyProfile = QuantStrategyProfile.SNIPER_HIGH_WINRATE
        ): Strategy {
            val isCrypto = assetClass == MarketAssetClass.CRYPTO
            return when (profile) {
                QuantStrategyProfile.SNIPER_HIGH_WINRATE -> if (isCrypto) {
                    Strategy(
                        id = "CRYPTO_KVANT_SHIELD_24_7",
                        nameRu = "Kvant Shield Crypto 24/7 (Winrate 72–84%)",
                        subtitleRu = "Быстрый фибо-импульс EMA(13/34) + адаптивный RSI(14) [24..76] + быстрый TP1 (0.85 ATR) с мгновенным переводом стопа в Безубыток и защитой от сквизов",
                        assetClass = MarketAssetClass.CRYPTO,
                        is24x7VolatilityTuned = true,
                        fastEmaPeriod = 13,
                        slowEmaPeriod = 34,
                        rsiPeriod = 14,
                        rsiOversold = 24.0,
                        rsiOverbought = 76.0,
                        rsiBullishFloor = 42.0,
                        rsiBullishCeiling = 74.0,
                        rsiBearishFloor = 26.0,
                        rsiBearishCeiling = 58.0,
                        atrPeriod = 14,
                        tp1AtrMultiplier = 0.85,
                        tp2AtrMultiplier = 2.40,
                        slAtrMultiplier = 1.95,
                        breakevenLockFraction = 0.50,
                        vwapWindowBars = 32,
                        adxTrendThreshold = 23.0,
                        zScoreExtremeThreshold = 2.10,
                        volumeSpikeMultiplier = 2.0,
                        expectedWinrateRangeRu = "72% – 84%"
                    )
                } else {
                    Strategy(
                        id = "MOEX_KVANT_SHIELD_TQBR",
                        nameRu = "Kvant Shield MOEX (Winrate 74–85%)",
                        subtitleRu = "Институциональный возврат к VWAP + тренд EMA(20/50) + RSI(14) [30..70] + быстрый TP1 (0.85 ATR) с авто-переводом стопа в Безубыток",
                        assetClass = MarketAssetClass.MOEX,
                        is24x7VolatilityTuned = false,
                        fastEmaPeriod = 20,
                        slowEmaPeriod = 50,
                        rsiPeriod = 14,
                        rsiOversold = 30.0,
                        rsiOverbought = 70.0,
                        rsiBullishFloor = 44.0,
                        rsiBullishCeiling = 68.0,
                        rsiBearishFloor = 32.0,
                        rsiBearishCeiling = 56.0,
                        atrPeriod = 14,
                        tp1AtrMultiplier = 0.85,
                        tp2AtrMultiplier = 2.30,
                        slAtrMultiplier = 1.80,
                        breakevenLockFraction = 0.50,
                        vwapWindowBars = 24,
                        adxTrendThreshold = 20.0,
                        zScoreExtremeThreshold = 1.70,
                        volumeSpikeMultiplier = 1.75,
                        expectedWinrateRangeRu = "74% – 85%"
                    )
                }

                QuantStrategyProfile.CLASSIC_RR_1_2 -> if (isCrypto) {
                    Strategy(
                        id = "CRYPTO_TREND_FLOW_24_7",
                        nameRu = "Crypto Trend & Perp Flow 24/7",
                        subtitleRu = "EMA(14/42) + RSI(14) [26..74] + фильтр фандинга и дельты OI (TP1 1.0 ATR / TP2 2.5 ATR / SL 1.75 ATR)",
                        assetClass = MarketAssetClass.CRYPTO,
                        is24x7VolatilityTuned = true,
                        fastEmaPeriod = 14,
                        slowEmaPeriod = 42,
                        rsiPeriod = 14,
                        rsiOversold = 26.0,
                        rsiOverbought = 74.0,
                        rsiBullishFloor = 45.0,
                        rsiBullishCeiling = 72.0,
                        rsiBearishFloor = 28.0,
                        rsiBearishCeiling = 55.0,
                        atrPeriod = 14,
                        tp1AtrMultiplier = 1.00,
                        tp2AtrMultiplier = 2.50,
                        slAtrMultiplier = 1.75,
                        breakevenLockFraction = 0.50,
                        vwapWindowBars = 30,
                        adxTrendThreshold = 22.0,
                        zScoreExtremeThreshold = 2.00,
                        volumeSpikeMultiplier = 1.9,
                        expectedWinrateRangeRu = "66% – 76%"
                    )
                } else {
                    Strategy(
                        id = "MOEX_SYSTEMATIC_TREND",
                        nameRu = "MOEX Systematic Trend & VWAP",
                        subtitleRu = "EMA(20/50) + RSI(14) [30..70] + поток ордеров TQBR (TP1 1.0 ATR / TP2 2.4 ATR / SL 1.65 ATR)",
                        assetClass = MarketAssetClass.MOEX,
                        is24x7VolatilityTuned = false,
                        fastEmaPeriod = 20,
                        slowEmaPeriod = 50,
                        rsiPeriod = 14,
                        rsiOversold = 30.0,
                        rsiOverbought = 70.0,
                        rsiBullishFloor = 46.0,
                        rsiBullishCeiling = 68.0,
                        rsiBearishFloor = 32.0,
                        rsiBearishCeiling = 54.0,
                        atrPeriod = 14,
                        tp1AtrMultiplier = 1.00,
                        tp2AtrMultiplier = 2.40,
                        slAtrMultiplier = 1.65,
                        breakevenLockFraction = 0.50,
                        vwapWindowBars = 24,
                        adxTrendThreshold = 20.0,
                        zScoreExtremeThreshold = 1.65,
                        volumeSpikeMultiplier = 1.7,
                        expectedWinrateRangeRu = "68% – 78%"
                    )
                }

                QuantStrategyProfile.AGGRESSIVE_IMPULSE -> if (isCrypto) {
                    Strategy(
                        id = "CRYPTO_LIQUIDITY_SWEEP_24_7",
                        nameRu = "Crypto Squeeze & Liquidity Sweep 24/7",
                        subtitleRu = "EMA(10/30) + RSI(12) [22..78] для пробоев и шорт-сквизов (TP1 0.9 ATR / TP2 2.8 ATR / SL 1.85 ATR)",
                        assetClass = MarketAssetClass.CRYPTO,
                        is24x7VolatilityTuned = true,
                        fastEmaPeriod = 10,
                        slowEmaPeriod = 30,
                        rsiPeriod = 12,
                        rsiOversold = 22.0,
                        rsiOverbought = 78.0,
                        rsiBullishFloor = 44.0,
                        rsiBullishCeiling = 76.0,
                        rsiBearishFloor = 24.0,
                        rsiBearishCeiling = 56.0,
                        atrPeriod = 14,
                        tp1AtrMultiplier = 0.90,
                        tp2AtrMultiplier = 2.80,
                        slAtrMultiplier = 1.85,
                        breakevenLockFraction = 0.50,
                        vwapWindowBars = 28,
                        adxTrendThreshold = 24.0,
                        zScoreExtremeThreshold = 2.20,
                        volumeSpikeMultiplier = 2.1,
                        expectedWinrateRangeRu = "65% – 75%"
                    )
                } else {
                    Strategy(
                        id = "MOEX_IMPULSE_BREAKOUT",
                        nameRu = "MOEX Impulse & Smart Money",
                        subtitleRu = "EMA(15/45) + RSI(14) [28..72] + пробой ликвидности (TP1 0.9 ATR / TP2 2.7 ATR / SL 1.70 ATR)",
                        assetClass = MarketAssetClass.MOEX,
                        is24x7VolatilityTuned = false,
                        fastEmaPeriod = 15,
                        slowEmaPeriod = 45,
                        rsiPeriod = 14,
                        rsiOversold = 28.0,
                        rsiOverbought = 72.0,
                        rsiBullishFloor = 45.0,
                        rsiBullishCeiling = 70.0,
                        rsiBearishFloor = 30.0,
                        rsiBearishCeiling = 55.0,
                        atrPeriod = 14,
                        tp1AtrMultiplier = 0.90,
                        tp2AtrMultiplier = 2.70,
                        slAtrMultiplier = 1.70,
                        breakevenLockFraction = 0.50,
                        vwapWindowBars = 20,
                        adxTrendThreshold = 21.0,
                        zScoreExtremeThreshold = 1.80,
                        volumeSpikeMultiplier = 1.8,
                        expectedWinrateRangeRu = "66% – 76%"
                    )
                }
            }
        }
    }
}

@Immutable
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
        titleRu = "Kvant Shield 75%+ (Асимметричный Безубыток)",
        descriptionRu = "Авторская стратегия с Winrate 72–85%: быстрый TP1 (0.85 ATR) фиксирует 50% профита и переводит стоп в Безубыток, защищая депозит от разворотов",
        slAtrMultiplier = 1.80,
        tpAtrMultiplier = 2.35,
        defaultMinConfidence = 75
    ),
    CLASSIC_RR_1_2(
        code = "CLASSIC_RR_1_2",
        titleRu = "Systematic Trend & VWAP (Winrate 68%+)",
        descriptionRu = "Сбалансированная трендовая модель по потоку ордеров, фильтрам EMA/RSI и динамическому VWAP с защитой позиции после TP1",
        slAtrMultiplier = 1.65,
        tpAtrMultiplier = 2.40,
        defaultMinConfidence = 74
    ),
    AGGRESSIVE_IMPULSE(
        code = "AGGRESSIVE_IMPULSE",
        titleRu = "Smart Money Squeeze & Breakout",
        descriptionRu = "Охота за выносами ликвидности (Stop-Hunt), шорт-сквизами и импульсами объема с удержанием второй половины до TP2",
        slAtrMultiplier = 1.75,
        tpAtrMultiplier = 2.75,
        defaultMinConfidence = 78
    );

    val riskRewardRatio: Double
        get() = if (slAtrMultiplier > 0) tpAtrMultiplier / slAtrMultiplier else 2.0

    companion object {
        fun fromCode(code: String): QuantStrategyProfile =
            entries.firstOrNull { it.code == code } ?: SNIPER_HIGH_WINRATE
    }
}

@Immutable
data class AppSettings(
    val marketAssetClass: MarketAssetClass = MarketAssetClass.MOEX,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val strategyProfile: QuantStrategyProfile = QuantStrategyProfile.SNIPER_HIGH_WINRATE,
    val minConfidenceThreshold: Int = 75,
    val candleIntervalMinutes: Int = 10, // 10, 60, or 1440 (1D)
    val useNewsSentimentFilter: Boolean = true,
    val useStrictVwapMacdFilter: Boolean = true,
    val pollIntervalSeconds: Int = 10,
    val autoOpenTradingViewInBrowser: Boolean = true,
    val compactCards: Boolean = false,
    val voiceAssistantEnabled: Boolean = true,
    val defaultRiskRub: Double = 2500.0,
    val ecoBatteryMode: Boolean = false,
    val silentMeetingHapticMode: Boolean = false,
    val privacyHideBalance: Boolean = false,
    val favoriteTickers: Set<String> = setOf("SBER", "LKOH", "YDEX", "T", "BTC", "ETH", "SOL", "TON"),
    val sortOrder: SignalSortOrder = SignalSortOrder.SIGNAL_STRENGTH
) {
    val isCryptoMode: Boolean
        get() = marketAssetClass == MarketAssetClass.CRYPTO

    val currencySymbol: String
        get() = marketAssetClass.currencySymbol

    val benchmarkName: String
        get() = marketAssetClass.benchmarkName

    val activeStrategy: Strategy
        get() = Strategy.forMarket(marketAssetClass, strategyProfile)
}

object AppSettingsManager {
    private const val PREFS_NAME = "moex_pulse_quant_settings_v4"
    private var initialized = false

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val assetClass = MarketAssetClass.fromCode(
                prefs.getString("market_asset_class", MarketAssetClass.MOEX.code)
                    ?: MarketAssetClass.MOEX.code
            )
            val profile = QuantStrategyProfile.fromCode(
                prefs.getString("strategy_profile", QuantStrategyProfile.SNIPER_HIGH_WINRATE.code)
                    ?: QuantStrategyProfile.SNIPER_HIGH_WINRATE.code
            )
            val minConf = prefs.getInt("min_confidence", 75).coerceIn(60, 92)
            val intervalMin = prefs.getInt("candle_interval_min", 10).let {
                when (it) {
                    60 -> 60
                    1440 -> 1440
                    else -> 10
                }
            }
            val useNews = prefs.getBoolean("use_news_sentiment", true)
            val useVwapMacd = prefs.getBoolean("use_strict_vwap_macd", true)
            val pollSec = prefs.getInt("poll_interval_sec", 10).coerceIn(5, 60)
            val compact = prefs.getBoolean("compact_cards", false)
            val voice = prefs.getBoolean("voice_assistant_enabled", true)
            val riskRub = prefs.getFloat("default_risk_rub", 2500f).toDouble().coerceIn(500.0, 100000.0)
            val ecoMode = prefs.getBoolean("eco_battery_mode", false)
            val silentMorse = prefs.getBoolean("silent_meeting_haptic", false)
            val privacyHide = prefs.getBoolean("privacy_hide_balance", false)
            val favCsv = prefs.getString("favorite_tickers_csv", "SBER,LKOH,YDEX,T,BTC,ETH,SOL,TON")
                ?: "SBER,LKOH,YDEX,T,BTC,ETH,SOL,TON"
            val favSet = favCsv.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            val sortOrder = SignalSortOrder.fromCode(
                prefs.getString("sort_order", SignalSortOrder.SIGNAL_STRENGTH.code)
                    ?: SignalSortOrder.SIGNAL_STRENGTH.code
            )

            _settings.value = AppSettings(
                marketAssetClass = assetClass,
                themeMode = AppThemeMode.DARK,
                strategyProfile = profile,
                minConfidenceThreshold = minConf,
                candleIntervalMinutes = intervalMin,
                useNewsSentimentFilter = useNews,
                useStrictVwapMacdFilter = useVwapMacd,
                pollIntervalSeconds = pollSec,
                compactCards = compact,
                voiceAssistantEnabled = voice,
                defaultRiskRub = riskRub,
                ecoBatteryMode = ecoMode,
                silentMeetingHapticMode = silentMorse,
                privacyHideBalance = privacyHide,
                favoriteTickers = favSet,
                sortOrder = sortOrder
            )
            initialized = true
        }
    }

    fun updateMarketAssetClass(context: Context, assetClass: MarketAssetClass) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(marketAssetClass = assetClass)
        save(context)
    }

    fun toggleMarketAssetClass(context: Context): MarketAssetClass {
        ensureInitialized(context)
        val next = if (_settings.value.marketAssetClass == MarketAssetClass.MOEX) {
            MarketAssetClass.CRYPTO
        } else {
            MarketAssetClass.MOEX
        }
        _settings.value = _settings.value.copy(marketAssetClass = next)
        save(context)
        return next
    }

    fun updateThemeMode(context: Context, mode: AppThemeMode) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(themeMode = mode)
        save(context)
    }

    fun toggleLightDarkQuick(context: Context) {
        ensureInitialized(context)
        updateThemeMode(context, AppThemeMode.DARK)
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
        val valid = when (minutes) {
            60 -> 60
            1440 -> 1440
            else -> 10
        }
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

    fun updateVoiceAssistant(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(voiceAssistantEnabled = enabled)
        save(context)
    }

    fun updateDefaultRiskRub(context: Context, riskRub: Double) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(defaultRiskRub = riskRub.coerceIn(500.0, 100000.0))
        save(context)
    }

    fun updateEcoBatteryMode(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(
            ecoBatteryMode = enabled,
            pollIntervalSeconds = if (enabled) 30 else 10
        )
        save(context)
    }

    fun updateSilentMeetingHapticMode(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(silentMeetingHapticMode = enabled)
        save(context)
    }

    fun updatePrivacyHideBalance(context: Context, hide: Boolean) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(privacyHideBalance = hide)
        save(context)
    }

    fun toggleFavoriteTicker(context: Context, ticker: String) {
        ensureInitialized(context)
        val norm = ticker.trim().uppercase()
        val current = _settings.value.favoriteTickers.toMutableSet()
        if (current.contains(norm)) {
            current.remove(norm)
        } else {
            current.add(norm)
        }
        _settings.value = _settings.value.copy(favoriteTickers = current)
        save(context)
    }

    fun updateSortOrder(context: Context, sortOrder: SignalSortOrder) {
        ensureInitialized(context)
        _settings.value = _settings.value.copy(sortOrder = sortOrder)
        save(context)
    }

    private fun save(context: Context) {
        val s = _settings.value
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("market_asset_class", s.marketAssetClass.code)
            .putString("theme_mode", s.themeMode.code)
            .putString("strategy_profile", s.strategyProfile.code)
            .putInt("min_confidence", s.minConfidenceThreshold)
            .putInt("candle_interval_min", s.candleIntervalMinutes)
            .putBoolean("use_news_sentiment", s.useNewsSentimentFilter)
            .putBoolean("use_strict_vwap_macd", s.useStrictVwapMacdFilter)
            .putInt("poll_interval_sec", s.pollIntervalSeconds)
            .putBoolean("compact_cards", s.compactCards)
            .putBoolean("voice_assistant_enabled", s.voiceAssistantEnabled)
            .putFloat("default_risk_rub", s.defaultRiskRub.toFloat())
            .putBoolean("eco_battery_mode", s.ecoBatteryMode)
            .putBoolean("silent_meeting_haptic", s.silentMeetingHapticMode)
            .putBoolean("privacy_hide_balance", s.privacyHideBalance)
            .putString("favorite_tickers_csv", s.favoriteTickers.joinToString(","))
            .putString("sort_order", s.sortOrder.code)
            .apply()
    }
}
