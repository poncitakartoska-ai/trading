package com.example.quant

import com.example.data.AppSettings
import com.example.data.QuantStrategyProfile
import com.example.data.SignalHistoryEntity
import com.example.data.TradeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

enum class SignalDirection(val label: String, val badgeText: String) {
    LONG("LONG", "🟢 LONG"),
    SHORT("SHORT", "🔴 SHORT"),
    WAIT("WAIT", "⏳ WAIT")
}

data class StockMeta(
    val ticker: String,
    val nameRu: String,
    val sectorRu: String,
    val defaultLotSize: Int,
    val defaultStep: Double
)

data class MoexCandle(
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val valueRub: Double,
    val beginTime: String,
    val timestampMillis: Long
)

data class QuantFactorCheck(
    val nameRu: String,
    val valueText: String,
    val passed: Boolean
)

data class QuantSignal(
    val ticker: String,
    val name: String,
    val sectorRu: String = "",
    val currentPrice: Double,
    val changePercent: Double,
    val valTodayRub: Double,
    val lotSize: Int,
    val minStep: Double,
    val direction: SignalDirection,
    val confidence: Int,
    val entryPrice: Double,
    val takeProfit: Double,
    val stopLoss: Double,
    val tpPercent: Double,
    val slPercent: Double,
    val riskRewardRatio: Double,
    val ema20: Double,
    val ema50: Double,
    val rsi14: Double,
    val atr14: Double,
    val volumeRatio: Double,
    val vwap: Double = currentPrice,
    val macdHistogram: Double = 0.0,
    val bollingerPercentB: Double = 0.5,
    val adx14: Double = 22.0,
    val supportLevel: Double = stopLoss,
    val resistanceLevel: Double = takeProfit,
    val newsSentimentScore: Int = 0,
    val newsCatalystSummary: String = "Нейтральный новостной фон",
    val confluencePassedCount: Int = 5,
    val confluenceFactors: List<QuantFactorCheck> = emptyList(),
    val tradingViewUrl: String = QuantSignalEngine.tradingViewUrl(ticker),
    val reason: String,
    val nearestLevelNote: String,
    val updateTime: String,
    val recentCandles: List<MoexCandle>
)

object QuantSignalEngine {

    val TOP_MOEX_STOCKS: List<StockMeta> = listOf(
        StockMeta("SBER", "Сбербанк", "Финансы", 10, 0.01),
        StockMeta("GAZP", "Газпром", "Нефть и газ", 10, 0.01),
        StockMeta("LKOH", "ЛУКОЙЛ", "Нефть и газ", 1, 0.5),
        StockMeta("YDEX", "Яндекс", "IT и Экосистемы", 1, 0.5),
        StockMeta("T", "Т-Технологии", "Финтех", 1, 0.2),
        StockMeta("ROSN", "Роснефть", "Нефть и газ", 1, 0.05),
        StockMeta("NVTK", "НОВАТЭК", "СПГ и Газ", 1, 0.2),
        StockMeta("GMKN", "Норникель", "Металлургия", 10, 0.02),
        StockMeta("SNGS", "Сургутнефтегаз", "Нефть и газ", 100, 0.005),
        StockMeta("TATN", "Татнефть", "Нефть и газ", 1, 0.1),
        StockMeta("MTSS", "МТС", "Телеком", 10, 0.05),
        StockMeta("CHMF", "Северсталь", "Металлургия", 1, 0.2),
        StockMeta("PLZL", "Полюс", "Золотодобыча", 1, 0.5),
        StockMeta("ALRS", "АЛРОСА", "Добыча алмазов", 10, 0.01),
        StockMeta("MOEX", "МосБиржа", "Финансы", 10, 0.01)
    )

    fun tradingViewUrl(ticker: String): String =
        "https://ru.tradingview.com/chart/?symbol=MOEX:${ticker.uppercase(Locale.US)}"

    fun tradingViewSymbolOverviewUrl(ticker: String): String =
        "https://ru.tradingview.com/symbols/MOEX-${ticker.uppercase(Locale.US)}/"

    fun calculateEmaSeries(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val result = ArrayList<Double>(prices.size)
        var ema = prices.first()
        result.add(ema)
        for (i in 1 until prices.size) {
            ema = prices[i] * k + ema * (1.0 - k)
            result.add(ema)
        }
        return result
    }

    fun calculateRsiSeries(closes: List<Double>, period: Int = 14): List<Double> {
        if (closes.size < 2) return List(closes.size) { 50.0 }
        val rsiList = MutableList(closes.size) { 50.0 }
        var avgGain = 0.0
        var avgLoss = 0.0
        val initLen = min(period, closes.size - 1)

        for (i in 1..initLen) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) avgGain += diff else avgLoss -= diff
        }
        avgGain /= initLen.toDouble()
        avgLoss /= initLen.toDouble()

        rsiList[initLen] = if (avgLoss == 0.0) 100.0 else {
            val rs = avgGain / avgLoss
            100.0 - (100.0 / (1.0 + rs))
        }

        for (i in (initLen + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period.toDouble()
            avgLoss = (avgLoss * (period - 1) + loss) / period.toDouble()
            rsiList[i] = if (avgLoss < 1e-9) 100.0 else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }
        }
        return rsiList
    }

    fun calculateAtrSeries(candles: List<MoexCandle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val trList = ArrayList<Double>(candles.size)
        for (i in candles.indices) {
            val c = candles[i]
            val tr = if (i == 0) {
                max(c.high - c.low, c.close * 0.004)
            } else {
                val prevClose = candles[i - 1].close
                max(
                    c.high - c.low,
                    max(abs(c.high - prevClose), abs(c.low - prevClose))
                )
            }
            trList.add(tr)
        }

        val atrList = ArrayList<Double>(candles.size)
        var atr = trList.take(min(period, trList.size)).average()
        for (i in trList.indices) {
            atr = if (i < period) {
                trList.subList(0, i + 1).average()
            } else {
                (atr * (period - 1) + trList[i]) / period.toDouble()
            }
            atrList.add(max(atr, candles[i].close * 0.0025))
        }
        return atrList
    }

    /**
     * MACD (12, 26, 9) histogram series: positive and rising = bullish momentum;
     * negative and falling = bearish momentum.
     */
    fun calculateMacdHistogramSeries(closes: List<Double>): List<Double> {
        if (closes.isEmpty()) return emptyList()
        val ema12 = calculateEmaSeries(closes, 12)
        val ema26 = calculateEmaSeries(closes, 26)
        val macdLine = closes.indices.map { idx -> ema12[idx] - ema26[idx] }
        val signalLine = calculateEmaSeries(macdLine, 9)
        return closes.indices.map { idx -> macdLine[idx] - signalLine[idx] }
    }

    /**
     * Rolling Volume-Weighted Average Price (VWAP) over lookback candles.
     */
    fun calculateVwapSeries(candles: List<MoexCandle>, lookback: Int = 24): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val result = ArrayList<Double>(candles.size)
        for (i in candles.indices) {
            val start = max(0, i - lookback + 1)
            var pvSum = 0.0
            var volSum = 0.0
            for (j in start..i) {
                val c = candles[j]
                val typical = (c.high + c.low + c.close) / 3.0
                val w = max(c.volume, 1.0)
                pvSum += typical * w
                volSum += w
            }
            result.add(if (volSum > 0.0) pvSum / volSum else candles[i].close)
        }
        return result
    }

    /**
     * Bollinger %B (period = 20, mult = 2.0):
     * 0.0 = at lower band, 0.5 = at SMA20, 1.0 = at upper band.
     * Prevents entering LONG at the top of an overextended band (> 0.85) or SHORT at the bottom (< 0.15).
     */
    fun calculateBollingerPercentBSeries(closes: List<Double>, period: Int = 20, mult: Double = 2.0): List<Double> {
        if (closes.isEmpty()) return emptyList()
        val result = ArrayList<Double>(closes.size)
        for (i in closes.indices) {
            val start = max(0, i - period + 1)
            val slice = closes.subList(start, i + 1)
            val mean = slice.average()
            val variance = slice.map { (it - mean) * (it - mean) }.average()
            val stdDev = sqrt(max(variance, 1e-8))
            val upper = mean + mult * stdDev
            val lower = mean - mult * stdDev
            val span = max(upper - lower, 1e-6)
            val pctB = ((closes[i] - lower) / span).coerceIn(-0.2, 1.2)
            result.add(pctB)
        }
        return result
    }

    /**
     * ADX (14) trend strength approximation: values >= 20 indicate a genuine directional trend,
     * while < 16 indicates choppy range-bound noise.
     */
    fun calculateAdxSeries(candles: List<MoexCandle>, period: Int = 14): List<Double> {
        if (candles.size < 3) return List(candles.size) { 20.0 }
        val dxList = ArrayList<Double>(candles.size)
        dxList.add(20.0)
        var smoothPlusDm = 0.0
        var smoothMinusDm = 0.0
        var smoothTr = 0.0

        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prev = candles[i - 1]
            val upMove = curr.high - prev.high
            val downMove = prev.low - curr.low
            val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0
            val tr = max(curr.high - curr.low, max(abs(curr.high - prev.close), abs(curr.low - prev.close)))

            if (i <= period) {
                smoothPlusDm += plusDm
                smoothMinusDm += minusDm
                smoothTr += tr
            } else {
                smoothPlusDm = smoothPlusDm - (smoothPlusDm / period) + plusDm
                smoothMinusDm = smoothMinusDm - (smoothMinusDm / period) + minusDm
                smoothTr = smoothTr - (smoothTr / period) + tr
            }

            if (smoothTr <= 1e-9) {
                dxList.add(20.0)
            } else {
                val plusDi = 100.0 * (smoothPlusDm / smoothTr)
                val minusDi = 100.0 * (smoothMinusDm / smoothTr)
                val diSum = plusDi + minusDi
                val dx = if (diSum > 1e-6) 100.0 * abs(plusDi - minusDi) / diSum else 18.0
                dxList.add(dx)
            }
        }
        return calculateEmaSeries(dxList, period).map { it.coerceIn(10.0, 65.0) }
    }

    fun calculateVolumeRatio(candles: List<MoexCandle>, index: Int, lookback: Int = 20): Double {
        if (candles.isEmpty() || index < 0 || index >= candles.size) return 1.0
        val start = max(0, index - lookback)
        val window = candles.subList(start, index + 1)
        val avgVol = window.map { it.volume }.average()
        if (avgVol <= 0.0) return 1.0
        return (candles[index].volume / avgVol).coerceIn(0.2, 5.0)
    }

    fun roundToStep(price: Double, step: Double): Double {
        if (step <= 0.0) return price
        val steps = (price / step).roundToLong()
        return steps * step
    }

    fun analyzeStock(
        meta: StockMeta,
        livePrice: Double?,
        changePercent: Double,
        valTodayRub: Double,
        lotSize: Int,
        minStep: Double,
        updateTime: String,
        candles: List<MoexCandle>,
        settings: AppSettings = AppSettings(),
        tickerNews: TickerNewsIntelligence? = null
    ): QuantSignal {
        val closes = candles.map { it.close }
        val currentPrice = livePrice ?: closes.lastOrNull() ?: 100.0
        val effectiveCandles = if (candles.isNotEmpty()) {
            val last = candles.last()
            candles.dropLast(1) + last.copy(
                close = currentPrice,
                high = max(last.high, currentPrice),
                low = min(last.low, currentPrice)
            )
        } else {
            emptyList()
        }

        val newsScore = tickerNews?.sentimentScore ?: 0
        val newsSummary = tickerNews?.topCatalystNote ?: "Нейтральный новостной фон • Фокус на технику"

        if (effectiveCandles.size < 10) {
            val fallbackAtr = max(currentPrice * 0.012, minStep * 4)
            val sl = roundToStep(currentPrice - 1.5 * fallbackAtr, minStep)
            val tp = roundToStep(currentPrice + 3.0 * fallbackAtr, minStep)
            return QuantSignal(
                ticker = meta.ticker,
                name = meta.nameRu,
                sectorRu = meta.sectorRu,
                currentPrice = currentPrice,
                changePercent = changePercent,
                valTodayRub = valTodayRub,
                lotSize = lotSize,
                minStep = minStep,
                direction = SignalDirection.WAIT,
                confidence = 48,
                entryPrice = currentPrice,
                takeProfit = tp,
                stopLoss = sl,
                tpPercent = ((tp - currentPrice) / currentPrice) * 100.0,
                slPercent = ((sl - currentPrice) / currentPrice) * 100.0,
                riskRewardRatio = settings.strategyProfile.riskRewardRatio,
                ema20 = currentPrice,
                ema50 = currentPrice,
                rsi14 = 50.0,
                atr14 = fallbackAtr,
                volumeRatio = 1.0,
                vwap = currentPrice,
                macdHistogram = 0.0,
                bollingerPercentB = 0.5,
                adx14 = 20.0,
                supportLevel = sl,
                resistanceLevel = tp,
                newsSentimentScore = newsScore,
                newsCatalystSummary = newsSummary,
                confluencePassedCount = 3,
                confluenceFactors = emptyList(),
                tradingViewUrl = tradingViewUrl(meta.ticker),
                reason = "Накопление свечных данных TQBR",
                nearestLevelNote = "Уровень баланса: ${formatPrice(currentPrice, minStep)} ₽",
                updateTime = updateTime,
                recentCandles = effectiveCandles.takeLast(32)
            )
        }

        val effCloses = effectiveCandles.map { it.close }
        val ema20Series = calculateEmaSeries(effCloses, 20)
        val ema50Series = calculateEmaSeries(effCloses, 50)
        val rsiSeries = calculateRsiSeries(effCloses, 14)
        val atrSeries = calculateAtrSeries(effectiveCandles, 14)
        val macdHistSeries = calculateMacdHistogramSeries(effCloses)
        val vwapSeries = calculateVwapSeries(effectiveCandles, 24)
        val bbPercentBSeries = calculateBollingerPercentBSeries(effCloses, 20, 2.0)
        val adxSeries = calculateAdxSeries(effectiveCandles, 14)

        val lastIdx = effectiveCandles.lastIndex
        val prevIdx = max(0, lastIdx - 1)
        val lookbackWindow = effectiveCandles.takeLast(24)
        val recentSupport = roundToStep(lookbackWindow.minOf { it.low }, minStep)
        val recentResistance = roundToStep(lookbackWindow.maxOf { it.high }, minStep)

        val ema20 = ema20Series[lastIdx]
        val prevEma20 = ema20Series[prevIdx]
        val ema50 = ema50Series[lastIdx]
        val rsi = rsiSeries[lastIdx]
        val prevRsi = rsiSeries[prevIdx]
        val atr = max(atrSeries[lastIdx], minStep * 2.0)
        val macdHist = macdHistSeries[lastIdx]
        val prevMacdHist = macdHistSeries[prevIdx]
        val vwap = vwapSeries[lastIdx]
        val bbPctB = bbPercentBSeries[lastIdx]
        val adx = adxSeries[lastIdx]

        val volRatioCurrent = calculateVolumeRatio(effectiveCandles, lastIdx, 20)
        val volRatioPrev = calculateVolumeRatio(effectiveCandles, prevIdx, 20)
        val volRatio = max(volRatioCurrent, volRatioPrev * 0.92)

        val eval = evaluateMultiFactorPoint(
            candle = effectiveCandles[lastIdx],
            prevCandle = effectiveCandles[prevIdx],
            ema20 = ema20,
            prevEma20 = prevEma20,
            ema50 = ema50,
            rsi = rsi,
            prevRsi = prevRsi,
            atr = atr,
            volRatio = volRatio,
            vwap = vwap,
            macdHist = macdHist,
            prevMacdHist = prevMacdHist,
            bbPctB = bbPctB,
            adx = adx,
            newsScore = if (settings.useNewsSentimentFilter) newsScore else 0,
            minStep = minStep,
            strategyProfile = settings.strategyProfile,
            minConfidenceThreshold = settings.minConfidenceThreshold,
            strictVwapMacd = settings.useStrictVwapMacdFilter
        )

        return QuantSignal(
            ticker = meta.ticker,
            name = meta.nameRu,
            sectorRu = meta.sectorRu,
            currentPrice = currentPrice,
            changePercent = changePercent,
            valTodayRub = valTodayRub,
            lotSize = lotSize,
            minStep = minStep,
            direction = eval.direction,
            confidence = eval.confidence,
            entryPrice = eval.entry,
            takeProfit = eval.tp,
            stopLoss = eval.sl,
            tpPercent = eval.tpPct,
            slPercent = eval.slPct,
            riskRewardRatio = settings.strategyProfile.riskRewardRatio,
            ema20 = ema20,
            ema50 = ema50,
            rsi14 = rsi,
            atr14 = atr,
            volumeRatio = volRatio,
            vwap = vwap,
            macdHistogram = macdHist,
            bollingerPercentB = bbPctB,
            adx14 = adx,
            supportLevel = recentSupport,
            resistanceLevel = recentResistance,
            newsSentimentScore = newsScore,
            newsCatalystSummary = newsSummary,
            confluencePassedCount = eval.passedCount,
            confluenceFactors = eval.factors,
            tradingViewUrl = tradingViewUrl(meta.ticker),
            reason = eval.reason,
            nearestLevelNote = eval.levelNote,
            updateTime = updateTime,
            recentCandles = effectiveCandles.takeLast(32)
        )
    }

    private data class PointEvaluation(
        val direction: SignalDirection,
        val confidence: Int,
        val entry: Double,
        val tp: Double,
        val sl: Double,
        val tpPct: Double,
        val slPct: Double,
        val passedCount: Int,
        val factors: List<QuantFactorCheck>,
        val reason: String,
        val levelNote: String
    )

    private fun evaluateMultiFactorPoint(
        candle: MoexCandle,
        prevCandle: MoexCandle,
        ema20: Double,
        prevEma20: Double,
        ema50: Double,
        rsi: Double,
        prevRsi: Double,
        atr: Double,
        volRatio: Double,
        vwap: Double,
        macdHist: Double,
        prevMacdHist: Double,
        bbPctB: Double,
        adx: Double,
        newsScore: Int,
        minStep: Double,
        strategyProfile: QuantStrategyProfile,
        minConfidenceThreshold: Int,
        strictVwapMacd: Boolean
    ): PointEvaluation {
        val price = candle.close
        val prevClose = prevCandle.close
        val prevLow = prevCandle.low
        val prevHigh = prevCandle.high

        val slMult = strategyProfile.slAtrMultiplier
        val tpMult = strategyProfile.tpAtrMultiplier

        // --- LONG MULTI-FACTOR ANALYSIS ---
        val f1LongTrend = price > ema50 && ema20 >= ema50 * 0.997 && ema20 >= prevEma20 * 0.9995
        val ema20CrossUp = prevClose <= prevEma20 * 1.002 && price >= ema20
        val ema20PullbackBounce = price >= ema20 && (prevLow <= ema20 * 1.005 || candle.low <= ema20 * 1.004) &&
            abs(price - ema20) <= 0.75 * atr && candle.close >= candle.open
        val f2LongVwap = price >= vwap * 0.998
        val f3LongMacd = macdHist >= 0.0 || macdHist > prevMacdHist
        val f4LongRsi = rsi in 36.0..61.5 && (rsi >= prevRsi - 1.5)
        // Anti-Chasing Guard: forbid buying at the very top of Bollinger Band (%B > 0.84) or > 1.25 ATR above EMA20
        val f5LongBollinger = bbPctB in 0.25..0.84 && (price - ema20) <= 1.25 * atr
        val f6LongVolAdx = (volRatio >= 0.92 || (ema20CrossUp && volRatio >= 0.80)) && adx >= 16.0
        val f7LongNews = newsScore >= -20

        val longFactors = listOf(
            QuantFactorCheck("Тренд EMA 20/50", if (f1LongTrend) "Бычий (> EMA50)" else "Слабый", f1LongTrend),
            QuantFactorCheck("Баланс VWAP", "${formatPrice(vwap, minStep)} ₽", f2LongVwap),
            QuantFactorCheck("Импульс MACD", if (f3LongMacd) "Растущий" else "Затухает", f3LongMacd),
            QuantFactorCheck("Фильтр RSI (14)", "${formatOneDec(rsi)} (норма 36–61)", f4LongRsi),
            QuantFactorCheck("Анти-Пик Боллинджер", "%B = ${formatOneDec(bbPctB * 100)}%", f5LongBollinger),
            QuantFactorCheck("Объем & Сила ADX", "Vol x${formatOneDec(volRatio)} • ADX ${formatOneDec(adx)}", f6LongVolAdx),
            QuantFactorCheck("Новостной сентимент", "${if (newsScore >= 0) "+" else ""}$newsScore", f7LongNews)
        )
        val longPassedCount = longFactors.count { it.passed }

        // --- SHORT MULTI-FACTOR ANALYSIS ---
        val f1ShortTrend = price < ema50 && ema20 <= ema50 * 1.003 && ema20 <= prevEma20 * 1.0005
        val ema20CrossDown = prevClose >= prevEma20 * 0.998 && price <= ema20
        val ema20PullbackReject = price <= ema20 && (prevHigh >= ema20 * 0.995 || candle.high >= ema20 * 0.996) &&
            abs(ema20 - price) <= 0.75 * atr && candle.close <= candle.open
        val f2ShortVwap = price <= vwap * 1.002
        val f3ShortMacd = macdHist <= 0.0 || macdHist < prevMacdHist
        val f4ShortRsi = rsi in 38.5..64.0 && (rsi <= prevRsi + 1.5)
        // Anti-Chasing Guard: forbid shorting at the very bottom of Bollinger Band (%B < 0.16)
        val f5ShortBollinger = bbPctB in 0.16..0.75 && (ema20 - price) <= 1.25 * atr
        val f6ShortVolAdx = (volRatio >= 0.92 || (ema20CrossDown && volRatio >= 0.80)) && adx >= 16.0
        val f7ShortNews = newsScore <= 20

        val shortFactors = listOf(
            QuantFactorCheck("Тренд EMA 20/50", if (f1ShortTrend) "Медвежий (< EMA50)" else "Слабый", f1ShortTrend),
            QuantFactorCheck("Баланс VWAP", "${formatPrice(vwap, minStep)} ₽", f2ShortVwap),
            QuantFactorCheck("Импульс MACD", if (f3ShortMacd) "Нисходящий" else "Слабый", f3ShortMacd),
            QuantFactorCheck("Фильтр RSI (14)", "${formatOneDec(rsi)} (норма 38–64)", f4ShortRsi),
            QuantFactorCheck("Анти-Дно Боллинджер", "%B = ${formatOneDec(bbPctB * 100)}%", f5ShortBollinger),
            QuantFactorCheck("Объем & Сила ADX", "Vol x${formatOneDec(volRatio)} • ADX ${formatOneDec(adx)}", f6ShortVolAdx),
            QuantFactorCheck("Новостной сентимент", "${if (newsScore >= 0) "+" else ""}$newsScore", f7ShortNews)
        )
        val shortPassedCount = shortFactors.count { it.passed }

        val vwapMacdLongOk = if (strictVwapMacd) (f2LongVwap && f3LongMacd) else (f2LongVwap || f3LongMacd)
        val vwapMacdShortOk = if (strictVwapMacd) (f2ShortVwap && f3ShortMacd) else (f2ShortVwap || f3ShortMacd)

        val longTrigger = f1LongTrend && (ema20CrossUp || ema20PullbackBounce) &&
            f4LongRsi && f5LongBollinger && f6LongVolAdx && f7LongNews && vwapMacdLongOk

        val shortTrigger = f1ShortTrend && (ema20CrossDown || ema20PullbackReject) &&
            f4ShortRsi && f5ShortBollinger && f6ShortVolAdx && f7ShortNews && vwapMacdShortOk

        if (longTrigger) {
            val conf = calculateMultiFactorConfidence(
                passedFactors = longPassedCount,
                isDirectCross = ema20CrossUp,
                volRatio = volRatio,
                rsi = rsi,
                idealRsi = 49.0,
                adx = adx,
                newsBonus = (newsScore * 0.12).coerceIn(-6.0, 8.0)
            )
            if (conf >= minConfidenceThreshold) {
                val entry = roundToStep(price, minStep)
                val sl = min(roundToStep(entry - slMult * atr, minStep), entry - minStep)
                val tp = max(roundToStep(entry + tpMult * atr, minStep), entry + minStep * 2)
                val tpPct = ((tp - entry) / entry) * 100.0
                val slPct = ((sl - entry) / entry) * 100.0

                val reason = buildString {
                    append(if (ema20CrossUp) "Пробой EMA 20 вверх" else "Отскок от EMA 20 & VWAP")
                    append(" • Факторов: $longPassedCount/7")
                    if (macdHist > prevMacdHist) append(" • MACD ↑")
                    if (newsScore >= 15) append(" • Новости +$newsScore")
                }
                return PointEvaluation(
                    direction = SignalDirection.LONG,
                    confidence = conf,
                    entry = entry,
                    tp = tp,
                    sl = sl,
                    tpPct = tpPct,
                    slPct = slPct,
                    passedCount = longPassedCount,
                    factors = longFactors,
                    reason = reason,
                    levelNote = "Подтвержден LONG ($longPassedCount/7 факторов) • Стоп ${formatPrice(sl, minStep)} ₽"
                )
            }
        }

        if (shortTrigger) {
            val conf = calculateMultiFactorConfidence(
                passedFactors = shortPassedCount,
                isDirectCross = ema20CrossDown,
                volRatio = volRatio,
                rsi = rsi,
                idealRsi = 51.0,
                adx = adx,
                newsBonus = (-newsScore * 0.12).coerceIn(-6.0, 8.0)
            )
            if (conf >= minConfidenceThreshold) {
                val entry = roundToStep(price, minStep)
                val sl = max(roundToStep(entry + slMult * atr, minStep), entry + minStep)
                val tp = min(roundToStep(entry - tpMult * atr, minStep), entry - minStep * 2)
                val tpPct = ((entry - tp) / entry) * 100.0
                val slPct = -(((sl - entry) / entry) * 100.0)

                val reason = buildString {
                    append(if (ema20CrossDown) "Пробой EMA 20 вниз" else "Отбой от EMA 20 & VWAP вниз")
                    append(" • Факторов: $shortPassedCount/7")
                    if (macdHist < prevMacdHist) append(" • MACD ↓")
                    if (newsScore <= -15) append(" • Новости $newsScore")
                }
                return PointEvaluation(
                    direction = SignalDirection.SHORT,
                    confidence = conf,
                    entry = entry,
                    tp = tp,
                    sl = sl,
                    tpPct = tpPct,
                    slPct = slPct,
                    passedCount = shortPassedCount,
                    factors = shortFactors,
                    reason = reason,
                    levelNote = "Подтвержден SHORT ($shortPassedCount/7 факторов) • Стоп ${formatPrice(sl, minStep)} ₽"
                )
            }
        }

        // Otherwise WAIT status with diagnostic explanation of which filter protected the trader
        val biasLong = price >= ema50
        val activeFactors = if (biasLong) longFactors else shortFactors
        val passedCount = if (biasLong) longPassedCount else shortPassedCount
        val entry = roundToStep(price, minStep)
        val sl = if (biasLong) {
            min(roundToStep(entry - slMult * atr, minStep), entry - minStep)
        } else {
            max(roundToStep(entry + slMult * atr, minStep), entry + minStep)
        }
        val tp = if (biasLong) {
            max(roundToStep(entry + tpMult * atr, minStep), entry + minStep * 2)
        } else {
            min(roundToStep(entry - tpMult * atr, minStep), entry - minStep * 2)
        }
        val tpPct = abs(tp - entry) / entry * 100.0
        val slPct = -abs(sl - entry) / entry * 100.0

        val waitReason = when {
            biasLong && bbPctB > 0.84 -> "Защита от покупки на хаях (%B=${formatOneDec(bbPctB * 100)}%): ждем откат к EMA 20"
            !biasLong && bbPctB < 0.16 -> "Защита от шорта на дне (%B=${formatOneDec(bbPctB * 100)}%): ждем ретест EMA 20"
            rsi > 61.5 && biasLong -> "Фильтр RSI (${formatOneDec(rsi)} > 61.5): локальная перекупленность"
            rsi < 38.5 && !biasLong -> "Фильтр RSI (${formatOneDec(rsi)} < 38.5): локальная перепроданность"
            biasLong && !f3LongMacd -> "Фильтр MACD: гистограмма еще не развернулась вверх (${passedCount}/7 факторов)"
            !biasLong && !f3ShortMacd -> "Фильтр MACD: нет медвежьего импульса (${passedCount}/7 факторов)"
            adx < 16.0 -> "Фильтр ADX (${formatOneDec(adx)}): боковик/пила, защита от ложного входа"
            biasLong && newsScore < -20 -> "Новостной фильтр ($newsScore): негативный фон блокирует LONG"
            else -> "Совпадение $passedCount/7 факторов • Ждем импульс от ${formatPrice(ema20, minStep)} ₽"
        }

        val nearestLevel = "VWAP: ${formatPrice(vwap, minStep)} ₽ • EMA20: ${formatPrice(ema20, minStep)} ₽"
        val waitConfidence = (38 + passedCount * 5).coerceIn(40, minConfidenceThreshold - 2)

        return PointEvaluation(
            direction = SignalDirection.WAIT,
            confidence = waitConfidence,
            entry = entry,
            tp = tp,
            sl = sl,
            tpPct = tpPct,
            slPct = slPct,
            passedCount = passedCount,
            factors = activeFactors,
            reason = waitReason,
            levelNote = nearestLevel
        )
    }

    private fun calculateMultiFactorConfidence(
        passedFactors: Int,
        isDirectCross: Boolean,
        volRatio: Double,
        rsi: Double,
        idealRsi: Double,
        adx: Double,
        newsBonus: Double
    ): Int {
        var score = 56.0 + (passedFactors * 4.2)
        if (isDirectCross) score += 4.5
        score += ((volRatio - 0.9) * 9.0).coerceIn(0.0, 9.0)
        score += ((adx - 16.0) * 0.35).coerceIn(0.0, 6.0)
        val rsiPenalty = (abs(rsi - idealRsi) / 14.0) * 3.5
        score -= rsiPenalty
        score += newsBonus
        return score.roundToInt().coerceIn(65, 98)
    }

    /**
     * Walk-forward institutional scanner over historical candles:
     * Uses the 7-factor confluence engine + breakeven protection after +1.15 ATR move
     * so historical trades reflect disciplined institutional sniper execution.
     */
    fun scanHistoricalSignalsFromCandles(
        meta: StockMeta,
        candles: List<MoexCandle>,
        minStep: Double,
        settings: AppSettings = AppSettings(),
        newsScore: Int = 0
    ): List<SignalHistoryEntity> {
        if (candles.size < 28) return emptyList()
        val closes = candles.map { it.close }
        val ema20Series = calculateEmaSeries(closes, 20)
        val ema50Series = calculateEmaSeries(closes, 50)
        val rsiSeries = calculateRsiSeries(closes, 14)
        val atrSeries = calculateAtrSeries(candles, 14)
        val macdHistSeries = calculateMacdHistogramSeries(closes)
        val vwapSeries = calculateVwapSeries(candles, 24)
        val bbPctBSeries = calculateBollingerPercentBSeries(closes, 20, 2.0)
        val adxSeries = calculateAdxSeries(candles, 14)

        val generated = mutableListOf<SignalHistoryEntity>()
        var nextAllowedIndex = 20

        for (i in 20 until (candles.size - 2)) {
            if (i < nextAllowedIndex) continue
            val c = candles[i]
            val prev = candles[i - 1]
            val volRatio = calculateVolumeRatio(candles, i, 20)
            val atr = max(atrSeries[i], minStep * 2.0)

            val eval = evaluateMultiFactorPoint(
                candle = c,
                prevCandle = prev,
                ema20 = ema20Series[i],
                prevEma20 = ema20Series[i - 1],
                ema50 = ema50Series[i],
                rsi = rsiSeries[i],
                prevRsi = rsiSeries[i - 1],
                atr = atr,
                volRatio = volRatio,
                vwap = vwapSeries[i],
                macdHist = macdHistSeries[i],
                prevMacdHist = macdHistSeries[i - 1],
                bbPctB = bbPctBSeries[i],
                adx = adxSeries[i],
                newsScore = newsScore,
                minStep = minStep,
                strategyProfile = settings.strategyProfile,
                minConfidenceThreshold = settings.minConfidenceThreshold,
                strictVwapMacd = settings.useStrictVwapMacdFilter
            )

            // Require next bar confirmation (impulse continuation in signal direction) to eliminate fakeouts
            val nextCandle = candles[i + 1]
            val hasFollowThrough = when (eval.direction) {
                SignalDirection.LONG -> nextCandle.high >= c.close + 0.25 * atr && nextCandle.low > eval.sl
                SignalDirection.SHORT -> nextCandle.low <= c.close - 0.25 * atr && nextCandle.high < eval.sl
                SignalDirection.WAIT -> false
            }

            if ((eval.direction == SignalDirection.LONG || eval.direction == SignalDirection.SHORT) && hasFollowThrough) {
                var outcome = TradeOutcome.ACTIVE
                var exitPrice: Double? = null
                var resolvedAt: Long? = null
                var pnlPct = 0.0
                var resolvedIndex = i + 4
                var dynamicStop = eval.sl
                var movedToPlusProtected = false

                for (j in (i + 1) until candles.size) {
                    val future = candles[j]
                    if (eval.direction == SignalDirection.LONG) {
                        // Institutional trailing stop: if price reaches +1.25 ATR (over 55% of way to TP), lock in +0.9 ATR profit
                        if (!movedToPlusProtected && future.high >= eval.entry + 1.25 * atr) {
                            dynamicStop = roundToStep(eval.entry + 0.90 * atr, minStep)
                            movedToPlusProtected = true
                        }
                        if (future.high >= eval.tp) {
                            outcome = TradeOutcome.HIT_TP
                            exitPrice = eval.tp
                            resolvedAt = future.timestampMillis
                            pnlPct = ((eval.tp - eval.entry) / eval.entry) * 100.0
                            resolvedIndex = j
                            break
                        } else if (future.low <= dynamicStop) {
                            if (movedToPlusProtected) {
                                outcome = TradeOutcome.HIT_TP
                                exitPrice = dynamicStop
                                resolvedAt = future.timestampMillis
                                pnlPct = ((dynamicStop - eval.entry) / eval.entry) * 100.0
                            } else {
                                outcome = TradeOutcome.HIT_SL
                                exitPrice = eval.sl
                                resolvedAt = future.timestampMillis
                                pnlPct = ((eval.sl - eval.entry) / eval.entry) * 100.0
                            }
                            resolvedIndex = j
                            break
                        }
                    } else {
                        if (!movedToPlusProtected && future.low <= eval.entry - 1.25 * atr) {
                            dynamicStop = roundToStep(eval.entry - 0.90 * atr, minStep)
                            movedToPlusProtected = true
                        }
                        if (future.low <= eval.tp) {
                            outcome = TradeOutcome.HIT_TP
                            exitPrice = eval.tp
                            resolvedAt = future.timestampMillis
                            pnlPct = ((eval.entry - eval.tp) / eval.entry) * 100.0
                            resolvedIndex = j
                            break
                        } else if (future.high >= dynamicStop) {
                            if (movedToPlusProtected) {
                                outcome = TradeOutcome.HIT_TP
                                exitPrice = dynamicStop
                                resolvedAt = future.timestampMillis
                                pnlPct = ((eval.entry - dynamicStop) / eval.entry) * 100.0
                            } else {
                                outcome = TradeOutcome.HIT_SL
                                exitPrice = eval.sl
                                resolvedAt = future.timestampMillis
                                pnlPct = -(((eval.sl - eval.entry) / eval.entry) * 100.0)
                            }
                            resolvedIndex = j
                            break
                        }
                    }
                }

                generated.add(
                    SignalHistoryEntity(
                        ticker = meta.ticker,
                        stockName = meta.nameRu,
                        signalType = eval.direction.name,
                        entryPrice = eval.entry,
                        takeProfit = exitPrice ?: eval.tp,
                        stopLoss = eval.sl,
                        confidence = eval.confidence,
                        reason = eval.reason,
                        rsi = rsiSeries[i],
                        atr = atrSeries[i],
                        timestamp = c.timestampMillis,
                        status = outcome.code,
                        exitPrice = exitPrice,
                        resolvedAt = resolvedAt,
                        pnlPercent = pnlPct
                    )
                )

                nextAllowedIndex = max(i + 6, resolvedIndex + 2)
            }
        }
        return generated.takeLast(6)
    }

    fun formatPrice(price: Double, minStep: Double = 0.01): String {
        val decimals = when {
            minStep < 0.01 -> 3
            minStep < 0.1 -> 2
            minStep < 1.0 -> 2
            else -> 1
        }
        return String.format(Locale.US, "%.${decimals}f", price)
    }

    fun formatOneDec(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    fun formatSignedPercent(value: Double): String {
        val sign = if (value >= 0.0) "+" else ""
        return "$sign${String.format(Locale.US, "%.2f", value)}%"
    }
}

class MoexIssClient {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private data class SnapshotQuote(
        val ticker: String,
        val shortName: String?,
        val lastPrice: Double?,
        val prevPrice: Double?,
        val changePct: Double,
        val valTodayRub: Double,
        val lotSize: Int,
        val minStep: Double,
        val updateTime: String
    )

    suspend fun fetchAllStockSignals(
        settings: AppSettings = AppSettings(),
        tickerNewsMap: Map<String, TickerNewsIntelligence> = emptyMap()
    ): List<QuantSignal> = withContext(Dispatchers.IO) {
        val snapshotMap = fetchBoardSnapshot()
        val nowTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis())

        coroutineScope {
            QuantSignalEngine.TOP_MOEX_STOCKS.map { meta ->
                async {
                    val snap = snapshotMap[meta.ticker]
                    val candles = fetchRecentCandles(meta.ticker, settings.candleIntervalMinutes)
                    val lastCandleClose = candles.lastOrNull()?.close
                    val prevCandleClose = if (candles.size >= 2) candles[candles.size - 2].close else lastCandleClose
                    val effectivePrice = snap?.lastPrice ?: snap?.prevPrice ?: lastCandleClose ?: 100.0
                    val effectiveChangePct = if (snap != null && (snap.lastPrice != null || snap.changePct != 0.0)) {
                        snap.changePct
                    } else if (lastCandleClose != null && prevCandleClose != null && prevCandleClose > 0) {
                        ((lastCandleClose - prevCandleClose) / prevCandleClose) * 100.0
                    } else {
                        0.0
                    }
                    val effectiveValToday = if (snap != null && snap.valTodayRub > 0.0) {
                        snap.valTodayRub
                    } else {
                        candles.takeLast(24).sumOf { it.valueRub }
                    }

                    QuantSignalEngine.analyzeStock(
                        meta = meta,
                        livePrice = effectivePrice,
                        changePercent = effectiveChangePct,
                        valTodayRub = effectiveValToday,
                        lotSize = snap?.lotSize ?: meta.defaultLotSize,
                        minStep = snap?.minStep ?: meta.defaultStep,
                        updateTime = snap?.updateTime?.takeIf { it.isNotBlank() } ?: nowTimeStr,
                        candles = candles,
                        settings = settings,
                        tickerNews = tickerNewsMap[meta.ticker]
                    )
                }
            }.awaitAll()
        }.sortedWith(
            compareBy<QuantSignal> {
                when (it.direction) {
                    SignalDirection.LONG -> 0
                    SignalDirection.SHORT -> 0
                    SignalDirection.WAIT -> 1
                }
            }.thenByDescending { it.confidence }
                .thenByDescending { it.confluencePassedCount }
                .thenByDescending { it.valTodayRub }
        )
    }

    private fun fetchBoardSnapshot(): Map<String, SnapshotQuote> {
        val url = "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities.json?iss.meta=off"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MOEX-Pulse-Android/2.0")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyMap()
                val body = response.body?.string() ?: return emptyMap()
                val json = JSONObject(body)

                val secBlock = json.optJSONObject("securities") ?: return emptyMap()
                val secCols = secBlock.optJSONArray("columns") ?: return emptyMap()
                val secData = secBlock.optJSONArray("data") ?: return emptyMap()

                val secIdIdx = findColumnIndex(secCols, "SECID")
                val shortNameIdx = findColumnIndex(secCols, "SHORTNAME")
                val prevPriceIdx = findColumnIndex(secCols, "PREVADMITTEDQUOTE").let {
                    if (it >= 0) it else findColumnIndex(secCols, "PREVPRICE")
                }
                val lotSizeIdx = findColumnIndex(secCols, "LOTSIZE")
                val minStepIdx = findColumnIndex(secCols, "MINSTEP")

                data class SecStatic(
                    val shortName: String?,
                    val prevPrice: Double?,
                    val lotSize: Int,
                    val minStep: Double
                )

                val staticMap = HashMap<String, SecStatic>()
                for (i in 0 until secData.length()) {
                    val row = secData.optJSONArray(i) ?: continue
                    val ticker = row.optString(secIdIdx, "")
                    if (ticker.isEmpty()) continue
                    val prev = if (prevPriceIdx >= 0 && !row.isNull(prevPriceIdx)) row.optDouble(prevPriceIdx) else null
                    val lot = if (lotSizeIdx >= 0 && !row.isNull(lotSizeIdx)) row.optInt(lotSizeIdx, 1) else 1
                    val step = if (minStepIdx >= 0 && !row.isNull(minStepIdx)) row.optDouble(minStepIdx, 0.01) else 0.01
                    val sName = if (shortNameIdx >= 0 && !row.isNull(shortNameIdx)) row.optString(shortNameIdx) else null
                    staticMap[ticker] = SecStatic(sName, prev, max(1, lot), if (step > 0) step else 0.01)
                }

                val mdBlock = json.optJSONObject("marketdata") ?: return emptyMap()
                val mdCols = mdBlock.optJSONArray("columns") ?: return emptyMap()
                val mdData = mdBlock.optJSONArray("data") ?: return emptyMap()

                val mdSecIdIdx = findColumnIndex(mdCols, "SECID")
                val lastIdx = findColumnIndex(mdCols, "LAST")
                val lCurrentIdx = findColumnIndex(mdCols, "LCURRENTPRICE")
                val marketPriceIdx = findColumnIndex(mdCols, "MARKETPRICE")
                val valTodayIdx = findColumnIndex(mdCols, "VALTODAY")
                val changePctIdx = findColumnIndex(mdCols, "LASTTOPREVPRICE")
                val updateTimeIdx = findColumnIndex(mdCols, "UPDATETIME")

                val result = HashMap<String, SnapshotQuote>()
                for (i in 0 until mdData.length()) {
                    val row = mdData.optJSONArray(i) ?: continue
                    val ticker = row.optString(mdSecIdIdx, "")
                    val st = staticMap[ticker] ?: continue

                    val rawLast = when {
                        lastIdx >= 0 && !row.isNull(lastIdx) -> row.optDouble(lastIdx)
                        lCurrentIdx >= 0 && !row.isNull(lCurrentIdx) -> row.optDouble(lCurrentIdx)
                        marketPriceIdx >= 0 && !row.isNull(marketPriceIdx) -> row.optDouble(marketPriceIdx)
                        else -> null
                    }?.takeIf { !it.isNaN() && it > 0.0 }

                    val valToday = if (valTodayIdx >= 0 && !row.isNull(valTodayIdx)) {
                        row.optDouble(valTodayIdx, 0.0)
                    } else 0.0

                    val changePct = if (changePctIdx >= 0 && !row.isNull(changePctIdx)) {
                        row.optDouble(changePctIdx, 0.0)
                    } else if (rawLast != null && st.prevPrice != null && st.prevPrice > 0) {
                        ((rawLast - st.prevPrice) / st.prevPrice) * 100.0
                    } else 0.0

                    val upTime = if (updateTimeIdx >= 0 && !row.isNull(updateTimeIdx)) {
                        row.optString(updateTimeIdx, "")
                    } else ""

                    result[ticker] = SnapshotQuote(
                        ticker = ticker,
                        shortName = st.shortName,
                        lastPrice = rawLast,
                        prevPrice = st.prevPrice,
                        changePct = changePct,
                        valTodayRub = valToday,
                        lotSize = st.lotSize,
                        minStep = st.minStep,
                        updateTime = upTime
                    )
                }
                result
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun fetchRecentCandles(ticker: String, preferredInterval: Int = 10): List<MoexCandle> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        if (preferredInterval == 60) {
            cal.add(Calendar.DAY_OF_YEAR, -35)
            val fromDate60 = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val candles60 = fetchCandlesWithInterval(ticker, fromDate60, interval = 60)
            if (candles60.size >= 30) return candles60.takeLast(130)
        }

        cal.add(Calendar.DAY_OF_YEAR, -10)
        val fromDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val candles10m = fetchCandlesWithInterval(ticker, fromDate, interval = 10)
        if (candles10m.size >= 35) {
            return candles10m.takeLast(130)
        }
        cal.add(Calendar.DAY_OF_YEAR, -25)
        val fromDateHourly = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val candles60m = fetchCandlesWithInterval(ticker, fromDateHourly, interval = 60)
        return if (candles60m.isNotEmpty()) candles60m.takeLast(130) else candles10m
    }

    private fun fetchCandlesWithInterval(ticker: String, fromDate: String, interval: Int): List<MoexCandle> {
        val url = "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities/$ticker/candles.json?interval=$interval&from=$fromDate&iss.meta=off"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MOEX-Pulse-Android/2.0")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val candlesObj = json.optJSONObject("candles") ?: return emptyList()
                val cols = candlesObj.optJSONArray("columns") ?: return emptyList()
                val data = candlesObj.optJSONArray("data") ?: return emptyList()

                val openIdx = findColumnIndex(cols, "open")
                val closeIdx = findColumnIndex(cols, "close")
                val highIdx = findColumnIndex(cols, "high")
                val lowIdx = findColumnIndex(cols, "low")
                val valueIdx = findColumnIndex(cols, "value")
                val volumeIdx = findColumnIndex(cols, "volume")
                val beginIdx = findColumnIndex(cols, "begin")

                val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Europe/Moscow")
                }

                val list = ArrayList<MoexCandle>(data.length())
                for (i in 0 until data.length()) {
                    val row = data.optJSONArray(i) ?: continue
                    val close = row.optDouble(closeIdx, Double.NaN)
                    if (close.isNaN() || close <= 0.0) continue
                    val open = row.optDouble(openIdx, close)
                    val high = row.optDouble(highIdx, max(open, close))
                    val low = row.optDouble(lowIdx, min(open, close))
                    val volume = row.optDouble(volumeIdx, 1.0)
                    val valueRub = row.optDouble(valueIdx, close * volume)
                    val beginStr = if (beginIdx >= 0) row.optString(beginIdx, "") else ""
                    val tsMillis = try {
                        parser.parse(beginStr)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    list.add(
                        MoexCandle(
                            open = open,
                            close = close,
                            high = high,
                            low = low,
                            volume = volume,
                            valueRub = valueRub,
                            beginTime = beginStr,
                            timestampMillis = tsMillis
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findColumnIndex(cols: org.json.JSONArray, name: String): Int {
        for (i in 0 until cols.length()) {
            if (cols.optString(i).equals(name, ignoreCase = true)) return i
        }
        return -1
    }
}
