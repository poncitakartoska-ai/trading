package com.example.quant

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

enum class SignalDirection(val label: String, val badgeText: String) {
    LONG("LONG", "🟢 LONG"),
    SHORT("SHORT", "🔴 SHORT"),
    WAIT("WAIT", "⏳ WAIT")
}

data class StockMeta(
    val ticker: String,
    val nameRu: String,
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

data class QuantSignal(
    val ticker: String,
    val name: String,
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
    val reason: String,
    val nearestLevelNote: String,
    val updateTime: String,
    val recentCandles: List<MoexCandle>
)

object QuantSignalEngine {

    val TOP_MOEX_STOCKS: List<StockMeta> = listOf(
        StockMeta("SBER", "Сбербанк", 10, 0.01),
        StockMeta("GAZP", "Газпром", 10, 0.01),
        StockMeta("LKOH", "ЛУКОЙЛ", 1, 0.5),
        StockMeta("YDEX", "Яндекс", 1, 0.5),
        StockMeta("T", "Т-Технологии", 1, 0.2),
        StockMeta("ROSN", "Роснефть", 1, 0.05),
        StockMeta("NVTK", "НОВАТЭК", 1, 0.2),
        StockMeta("GMKN", "Норникель", 10, 0.02),
        StockMeta("SNGS", "Сургутнефтегаз", 100, 0.005),
        StockMeta("TATN", "Татнефть", 1, 0.1),
        StockMeta("MTSS", "МТС", 10, 0.05),
        StockMeta("CHMF", "Северсталь", 1, 0.2),
        StockMeta("PLZL", "Полюс", 1, 0.5),
        StockMeta("ALRS", "АЛРОСА", 10, 0.01),
        StockMeta("MOEX", "МосБиржа", 10, 0.01)
    )

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
        candles: List<MoexCandle>
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

        if (effectiveCandles.size < 5) {
            val fallbackAtr = max(currentPrice * 0.012, minStep * 4)
            val sl = roundToStep(currentPrice - 1.5 * fallbackAtr, minStep)
            val tp = roundToStep(currentPrice + 3.0 * fallbackAtr, minStep)
            return QuantSignal(
                ticker = meta.ticker,
                name = meta.nameRu,
                currentPrice = currentPrice,
                changePercent = changePercent,
                valTodayRub = valTodayRub,
                lotSize = lotSize,
                minStep = minStep,
                direction = SignalDirection.WAIT,
                confidence = 45,
                entryPrice = currentPrice,
                takeProfit = tp,
                stopLoss = sl,
                tpPercent = ((tp - currentPrice) / currentPrice) * 100.0,
                slPercent = ((sl - currentPrice) / currentPrice) * 100.0,
                riskRewardRatio = 2.0,
                ema20 = currentPrice,
                ema50 = currentPrice,
                rsi14 = 50.0,
                atr14 = fallbackAtr,
                volumeRatio = 1.0,
                reason = "Накопление свечных данных TQBR",
                nearestLevelNote = "Уровень баланса: ${formatPrice(currentPrice, minStep)} ₽",
                updateTime = updateTime,
                recentCandles = effectiveCandles.takeLast(30)
            )
        }

        val effCloses = effectiveCandles.map { it.close }
        val ema20Series = calculateEmaSeries(effCloses, 20)
        val ema50Series = calculateEmaSeries(effCloses, 50)
        val rsiSeries = calculateRsiSeries(effCloses, 14)
        val atrSeries = calculateAtrSeries(effectiveCandles, 14)

        val lastIdx = effectiveCandles.lastIndex
        val prevIdx = max(0, lastIdx - 1)

        val ema20 = ema20Series[lastIdx]
        val prevEma20 = ema20Series[prevIdx]
        val ema50 = ema50Series[lastIdx]
        val rsi = rsiSeries[lastIdx]
        val atr = max(atrSeries[lastIdx], minStep * 2.0)
        val prevClose = effCloses[prevIdx]
        val prevLow = effectiveCandles[prevIdx].low
        val prevHigh = effectiveCandles[prevIdx].high

        // Use recent completed candle if current 10-min bar just started
        val volRatioCurrent = calculateVolumeRatio(effectiveCandles, lastIdx, 20)
        val volRatioPrev = calculateVolumeRatio(effectiveCandles, prevIdx, 20)
        val volRatio = max(volRatioCurrent, volRatioPrev * 0.92)

        val eval = evaluatePoint(
            price = currentPrice,
            prevClose = prevClose,
            prevLow = prevLow,
            prevHigh = prevHigh,
            ema20 = ema20,
            prevEma20 = prevEma20,
            ema50 = ema50,
            rsi = rsi,
            atr = atr,
            volRatio = volRatio,
            minStep = minStep
        )

        return QuantSignal(
            ticker = meta.ticker,
            name = meta.nameRu,
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
            riskRewardRatio = 2.0,
            ema20 = ema20,
            ema50 = ema50,
            rsi14 = rsi,
            atr14 = atr,
            volumeRatio = volRatio,
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
        val reason: String,
        val levelNote: String
    )

    private fun evaluatePoint(
        price: Double,
        prevClose: Double,
        prevLow: Double,
        prevHigh: Double,
        ema20: Double,
        prevEma20: Double,
        ema50: Double,
        rsi: Double,
        atr: Double,
        volRatio: Double,
        minStep: Double
    ): PointEvaluation {
        // LONG rules:
        // Price > EMA 50, EMA 20 crossover upward or bounce off EMA 20, RSI in 35..62, elevated volume
        val aboveEma50 = price > ema50
        val ema20CrossUp = prevClose <= prevEma20 * 1.002 && price >= ema20
        val ema20BounceUp = price >= ema20 && (prevLow <= ema20 * 1.004 || abs(price - ema20) <= 0.85 * atr)
        val bullishTrendStructure = ema20 >= ema50 * 0.998 && price >= ema20
        val rsiLongOk = rsi in 35.0..62.5
        val volumeLongOk = volRatio >= 0.95

        // SHORT rules:
        // Price < EMA 50, EMA 20 breakdown downward or bounce down off EMA 20, RSI in 38..65, elevated volume
        val belowEma50 = price < ema50
        val ema20CrossDown = prevClose >= prevEma20 * 0.998 && price <= ema20
        val ema20BounceDown = price <= ema20 && (prevHigh >= ema20 * 0.996 || abs(ema20 - price) <= 0.85 * atr)
        val bearishTrendStructure = ema20 <= ema50 * 1.002 && price <= ema20
        val rsiShortOk = rsi in 38.0..65.5
        val volumeShortOk = volRatio >= 0.95

        if (aboveEma50 && (ema20CrossUp || ema20BounceUp || bullishTrendStructure) && rsiLongOk && volumeShortOrLongPulse(volRatio, ema20CrossUp)) {
            val entry = roundToStep(price, minStep)
            val rawSl = entry - (1.5 * atr)
            val rawTp = entry + (3.0 * atr)
            val sl = min(roundToStep(rawSl, minStep), entry - minStep)
            val tp = max(roundToStep(rawTp, minStep), entry + minStep * 2)
            val tpPct = ((tp - entry) / entry) * 100.0
            val slPct = ((sl - entry) / entry) * 100.0

            val reason = when {
                ema20CrossUp && volRatio >= 1.2 -> "Пробой EMA 20 вверх на объеме x${formatOneDec(volRatio)}"
                ema20CrossUp -> "Пересечение EMA 20 вверх + тренд > EMA 50"
                ema20BounceUp -> "Отскок от динамической поддержки EMA 20"
                else -> "Импульс по тренду > EMA 50 • RSI ${formatOneDec(rsi)}"
            }
            val conf = calculateConfidence(
                isCross = ema20CrossUp,
                volRatio = volRatio,
                rsi = rsi,
                targetRsiMid = 50.0,
                emaSpreadPct = abs(ema20 - ema50) / price * 100.0
            )
            return PointEvaluation(
                direction = SignalDirection.LONG,
                confidence = conf,
                entry = entry,
                tp = tp,
                sl = sl,
                tpPct = tpPct,
                slPct = slPct,
                reason = reason,
                levelNote = "Активный LONG • Стоп за 1.5 ATR (${formatPrice(sl, minStep)} ₽)"
            )
        }

        if (belowEma50 && (ema20CrossDown || ema20BounceDown || bearishTrendStructure) && rsiShortOk && volumeShortOrLongPulse(volRatio, ema20CrossDown)) {
            val entry = roundToStep(price, minStep)
            val rawSl = entry + (1.5 * atr)
            val rawTp = entry - (3.0 * atr)
            val sl = max(roundToStep(rawSl, minStep), entry + minStep)
            val tp = min(roundToStep(rawTp, minStep), entry - minStep * 2)
            val tpPct = ((entry - tp) / entry) * 100.0 // positive potential profit % for SHORT
            val slPct = -(((sl - entry) / entry) * 100.0) // negative risk % for SHORT

            val reason = when {
                ema20CrossDown && volRatio >= 1.2 -> "Пробой EMA 20 вниз на объеме x${formatOneDec(volRatio)}"
                ema20CrossDown -> "Пробой EMA 20 вниз + цена < EMA 50"
                ema20BounceDown -> "Ретест сопротивления EMA 20 сверху вниз"
                else -> "Давление продавцов < EMA 50 • RSI ${formatOneDec(rsi)}"
            }
            val conf = calculateConfidence(
                isCross = ema20CrossDown,
                volRatio = volRatio,
                rsi = rsi,
                targetRsiMid = 51.0,
                emaSpreadPct = abs(ema50 - ema20) / price * 100.0
            )
            return PointEvaluation(
                direction = SignalDirection.SHORT,
                confidence = conf,
                entry = entry,
                tp = tp,
                sl = sl,
                tpPct = tpPct,
                slPct = slPct,
                reason = reason,
                levelNote = "Активный SHORT • Стоп за 1.5 ATR (${formatPrice(sl, minStep)} ₽)"
            )
        }

        // Otherwise WAIT status with clear nearest trigger level
        val biasLong = price >= ema50
        val entry = roundToStep(price, minStep)
        val sl = if (biasLong) {
            min(roundToStep(entry - 1.5 * atr, minStep), entry - minStep)
        } else {
            max(roundToStep(entry + 1.5 * atr, minStep), entry + minStep)
        }
        val tp = if (biasLong) {
            max(roundToStep(entry + 3.0 * atr, minStep), entry + minStep * 2)
        } else {
            min(roundToStep(entry - 3.0 * atr, minStep), entry - minStep * 2)
        }
        val tpPct = abs(tp - entry) / entry * 100.0
        val slPct = -abs(sl - entry) / entry * 100.0

        val waitReason = when {
            rsi > 62.5 && biasLong -> "Фильтр RSI (${formatOneDec(rsi)} > 62): перекупленность, ждем откат к EMA 20"
            rsi < 35.0 && biasLong -> "Фильтр RSI (${formatOneDec(rsi)} < 35): ждем возврат выше EMA 20"
            rsi < 38.0 && !biasLong -> "Фильтр RSI (${formatOneDec(rsi)} < 38): перепроданность, ждем ретест EMA 20"
            volRatio < 0.85 -> "Низкий объем (x${formatOneDec(volRatio)} от нормы): ожидание импульса"
            biasLong && price < ema20 -> "Цена между EMA 50 и EMA 20: ждем пробой ${formatPrice(ema20, minStep)} ₽ вверх"
            !biasLong && price > ema20 -> "Коррекция к EMA 50: ждем пробой ${formatPrice(ema20, minStep)} ₽ вниз"
            else -> "Консолидация у ключевых средних • Ожидание триггера"
        }

        val nearestLevel = if (abs(price - ema20) < abs(price - ema50)) {
            "Ближайший уровень EMA 20: ${formatPrice(ema20, minStep)} ₽"
        } else {
            "Ближайший уровень EMA 50: ${formatPrice(ema50, minStep)} ₽"
        }

        val waitConfidence = (42 + (min(volRatio, 1.5) * 12).roundToInt()).coerceIn(38, 64)

        return PointEvaluation(
            direction = SignalDirection.WAIT,
            confidence = waitConfidence,
            entry = entry,
            tp = tp,
            sl = sl,
            tpPct = tpPct,
            slPct = slPct,
            reason = waitReason,
            levelNote = nearestLevel
        )
    }

    private fun volumeShortOrLongPulse(volRatio: Double, isDirectCross: Boolean): Boolean {
        return volRatio >= 0.88 || (isDirectCross && volRatio >= 0.75)
    }

    private fun calculateConfidence(
        isCross: Boolean,
        volRatio: Double,
        rsi: Double,
        targetRsiMid: Double,
        emaSpreadPct: Double
    ): Int {
        var score = 68.0
        if (isCross) score += 8.0
        score += ((volRatio - 0.9) * 14.0).coerceIn(0.0, 14.0)
        val rsiPenalty = (abs(rsi - targetRsiMid) / 15.0) * 5.0
        score -= rsiPenalty
        score += (emaSpreadPct * 3.5).coerceIn(0.0, 8.0)
        return score.roundToInt().coerceIn(66, 97)
    }

    /**
     * Scans closed historical candles for a stock to reconstruct recent historical signals
     * and evaluate whether subsequent candles hit Take Profit or Stop Loss.
     */
    fun scanHistoricalSignalsFromCandles(
        meta: StockMeta,
        candles: List<MoexCandle>,
        minStep: Double
    ): List<SignalHistoryEntity> {
        if (candles.size < 25) return emptyList()
        val closes = candles.map { it.close }
        val ema20Series = calculateEmaSeries(closes, 20)
        val ema50Series = calculateEmaSeries(closes, 50)
        val rsiSeries = calculateRsiSeries(closes, 14)
        val atrSeries = calculateAtrSeries(candles, 14)

        val generated = mutableListOf<SignalHistoryEntity>()
        var nextAllowedIndex = 16

        for (i in 16 until (candles.size - 1)) {
            if (i < nextAllowedIndex) continue
            val c = candles[i]
            val prev = candles[i - 1]
            val volRatio = calculateVolumeRatio(candles, i, 20)
            val eval = evaluatePoint(
                price = c.close,
                prevClose = prev.close,
                prevLow = prev.low,
                prevHigh = prev.high,
                ema20 = ema20Series[i],
                prevEma20 = ema20Series[i - 1],
                ema50 = ema50Series[i],
                rsi = rsiSeries[i],
                atr = max(atrSeries[i], minStep * 2.0),
                volRatio = volRatio,
                minStep = minStep
            )

            if (eval.direction == SignalDirection.LONG || eval.direction == SignalDirection.SHORT) {
                var outcome = TradeOutcome.ACTIVE
                var exitPrice: Double? = null
                var resolvedAt: Long? = null
                var pnlPct = 0.0
                var resolvedIndex = i + 3

                for (j in (i + 1) until candles.size) {
                    val future = candles[j]
                    if (eval.direction == SignalDirection.LONG) {
                        if (future.low <= eval.sl) {
                            outcome = TradeOutcome.HIT_SL
                            exitPrice = eval.sl
                            resolvedAt = future.timestampMillis
                            pnlPct = ((eval.sl - eval.entry) / eval.entry) * 100.0
                            resolvedIndex = j
                            break
                        } else if (future.high >= eval.tp) {
                            outcome = TradeOutcome.HIT_TP
                            exitPrice = eval.tp
                            resolvedAt = future.timestampMillis
                            pnlPct = ((eval.tp - eval.entry) / eval.entry) * 100.0
                            resolvedIndex = j
                            break
                        }
                    } else {
                        if (future.high >= eval.sl) {
                            outcome = TradeOutcome.HIT_SL
                            exitPrice = eval.sl
                            resolvedAt = future.timestampMillis
                            pnlPct = -(((eval.sl - eval.entry) / eval.entry) * 100.0)
                            resolvedIndex = j
                            break
                        } else if (future.low <= eval.tp) {
                            outcome = TradeOutcome.HIT_TP
                            exitPrice = eval.tp
                            resolvedAt = future.timestampMillis
                            pnlPct = ((eval.entry - eval.tp) / eval.entry) * 100.0
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
                        takeProfit = eval.tp,
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

                nextAllowedIndex = max(i + 5, resolvedIndex + 2)
            }
        }
        return generated.takeLast(8)
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

    suspend fun fetchAllStockSignals(): List<QuantSignal> = withContext(Dispatchers.IO) {
        val snapshotMap = fetchBoardSnapshot()
        val nowTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis())

        coroutineScope {
            QuantSignalEngine.TOP_MOEX_STOCKS.map { meta ->
                async {
                    val snap = snapshotMap[meta.ticker]
                    val candles = fetchRecentCandles(meta.ticker)
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
                        candles = candles
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
                .thenByDescending { it.valTodayRub }
        )
    }

    private fun fetchBoardSnapshot(): Map<String, SnapshotQuote> {
        val url = "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities.json?iss.meta=off"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MOEX-Pulse-Android/1.0")
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
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun fetchRecentCandles(ticker: String): List<MoexCandle> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        cal.add(Calendar.DAY_OF_YEAR, -8)
        val fromDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val candles10m = fetchCandlesWithInterval(ticker, fromDate, interval = 10)
        if (candles10m.size >= 30) {
            return candles10m.takeLast(85)
        }
        cal.add(Calendar.DAY_OF_YEAR, -20)
        val fromDateHourly = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val candles60m = fetchCandlesWithInterval(ticker, fromDateHourly, interval = 60)
        return if (candles60m.isNotEmpty()) candles60m.takeLast(85) else candles10m
    }

    private fun fetchCandlesWithInterval(ticker: String, fromDate: String, interval: Int): List<MoexCandle> {
        val url = "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities/$ticker/candles.json?interval=$interval&from=$fromDate&iss.meta=off"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MOEX-Pulse-Android/1.0")
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
        } catch (e: Exception) {
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
