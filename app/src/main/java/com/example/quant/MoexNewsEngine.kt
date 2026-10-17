package com.example.quant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NewsSentimentType(val labelRu: String, val badgeEmoji: String) {
    BULLISH("ПОЗИТИВ (БЫЧИЙ)", "🟢"),
    BEARISH("НЕГАТИВ (МЕДВЕЖИЙ)", "🔴"),
    NEUTRAL("НЕЙТРАЛЬНО", "⚪")
}

enum class NewsImpactLevel(val labelRu: String) {
    HIGH("🔥 Высокое влияние"),
    MEDIUM("⚡ Среднее влияние"),
    INFO("ℹ️ Рыночный фон")
}

data class MarketNewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val publishedTime: String,
    val url: String,
    val relatedTickers: List<String>,
    val sentiment: NewsSentimentType,
    val sentimentScore: Int, // -100..+100
    val impactLevel: NewsImpactLevel,
    val tradingViewSymbolUrl: String?
)

data class TickerNewsIntelligence(
    val ticker: String,
    val sentimentScore: Int, // -100..+100
    val sentimentType: NewsSentimentType,
    val headlineCount: Int,
    val topCatalystNote: String
)

data class MarketNewsOverview(
    val marketSentimentIndex: Int, // -100..+100
    val marketRegimeLabel: String,
    val bullishCount: Int,
    val bearishCount: Int,
    val neutralCount: Int,
    val items: List<MarketNewsItem>,
    val tickerIntelligence: Map<String, TickerNewsIntelligence>
)

object MoexNewsEngine {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val tickerKeywords: Map<String, List<String>> = mapOf(
        "SBER" to listOf("sber", "сбер", "сбербанк", "греф", "банковск"),
        "GAZP" to listOf("gazp", "газпром", "газов", "спг", "трубопровод"),
        "LKOH" to listOf("lkoh", "лукойл", "нефт", "brent", "опек"),
        "YDEX" to listOf("ydex", "яндекс", "yandex", "ит-сектор", "технологич", "ии"),
        "T" to listOf("t-bank", "т-технологи", "т-банк", "тинькофф", "tcsg", "финтех"),
        "ROSN" to listOf("rosn", "роснефт", "восток ойл", "сечин", "нефт"),
        "NVTK" to listOf("nvtk", "новатэк", "арктик спг", "ямал спг"),
        "GMKN" to listOf("gmkn", "норникел", "никел", "паллади", "медь"),
        "SNGS" to listOf("sngs", "сургутнефтегаз", "сургут", "кубышк", "валютн"),
        "TATN" to listOf("tatn", "татнефт", "татарстан", "нефтепереработ"),
        "MTSS" to listOf("mtss", "мтс", "телеком", "связь", "экосистем"),
        "CHMF" to listOf("chmf", "северстал", "сталь", "металлург", "мордашов"),
        "PLZL" to listOf("plzl", "полюс", "золот", "gold", "сухой лог"),
        "ALRS" to listOf("alrs", "алрос", "алмаз", "бриллиант", "якут"),
        "MOEX" to listOf("moex", "мосбирж", "московская биржа", "торги", "индекс мосбиржи", "ключевая ставка", "цб рф")
    )

    private val positiveTerms: List<Pair<String, Int>> = listOf(
        "дивиденд" to 28,
        "рекомендовал дивиденды" to 36,
        "выплат" to 20,
        "рост прибыли" to 32,
        "чистая прибыль выросла" to 35,
        "рекордн" to 28,
        "выше ожиданий" to 30,
        "байбэк" to 32,
        "выкуп акций" to 30,
        "рост выручки" to 24,
        "повысил" to 18,
        "увеличил" to 18,
        "пробой" to 20,
        "приток" to 22,
        "лидер роста" to 26,
        "снижение ставки" to 30,
        "смягчени" to 24,
        "отчет мсфо" to 16,
        "покупать" to 22,
        "апсайд" to 24,
        "восстановлен" to 18,
        "максимум" to 20,
        "партнерств" to 16,
        "запуск" to 14
    )

    private val negativeTerms: List<Pair<String, Int>> = listOf(
        "убыток" to -34,
        "падение прибыли" to -30,
        "снижение прибыли" to -26,
        "отказался от дивидендов" to -38,
        "не выплачивать дивиденды" to -38,
        "санкц" to -32,
        "ограничен" to -22,
        "повышение ставк" to -26,
        "жестк" to -20,
        "инфляц" to -18,
        "падени" to -22,
        "снизился" to -18,
        "сократил" to -20,
        "налог" to -20,
        "ндпи" to -24,
        "допэмисс" to -32,
        "размыти" to -28,
        "авари" to -26,
        "штраф" to -20,
        "давлени" to -18,
        "распродаж" to -26,
        "коррекц" to -16
    )

    @Volatile
    private var cachedOverview: MarketNewsOverview? = null
    @Volatile
    private var lastFetchTimeMs: Long = 0L

    suspend fun fetchNewsAndSentiment(
        liveSignals: List<QuantSignal>,
        forceRefresh: Boolean = false
    ): MarketNewsOverview = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = cachedOverview
        if (!forceRefresh && existing != null && (now - lastFetchTimeMs) < 45_000L && existing.items.isNotEmpty()) {
            return@withContext enrichWithLiveOrderFlow(existing, liveSignals)
        }

        val combinedItems = coroutineScope {
            val moexSiteDeferred = async { fetchMoexSiteNews() }
            val rbcRssDeferred = async {
                fetchRssFeed(
                    url = "https://rssexport.rbc.ru/rbcnews/news/20/full.rss",
                    sourceLabel = "РБК Инвестиции / Финансы"
                )
            }
            val primeRssDeferred = async {
                fetchRssFeed(
                    url = "https://prime.ru/export/rss2/index.xml",
                    sourceLabel = "ПРАЙМ Финансовая лента"
                )
            }

            val rawList = mutableListOf<MarketNewsItem>()
            rawList.addAll(moexSiteDeferred.await())
            rawList.addAll(rbcRssDeferred.await())
            rawList.addAll(primeRssDeferred.await())
            rawList
        }

        val orderFlowNews = buildOrderFlowCatalystNews(liveSignals)
        val allMerged = (orderFlowNews + combinedItems)
            .distinctBy { it.title.lowercase(Locale.getDefault()).take(55) }
            .take(36)

        val overview = buildOverviewFromItems(allMerged, liveSignals)
        cachedOverview = overview
        lastFetchTimeMs = now
        overview
    }

    fun injectAiSearchNewsItems(aiItems: List<MarketNewsItem>, liveSignals: List<QuantSignal>): MarketNewsOverview {
        val currentItems = cachedOverview?.items.orEmpty()
        val merged = (aiItems + currentItems)
            .distinctBy { it.title.lowercase(Locale.getDefault()).take(55) }
            .take(40)
        val updated = buildOverviewFromItems(merged, liveSignals)
        cachedOverview = updated
        lastFetchTimeMs = System.currentTimeMillis()
        return updated
    }

    private fun enrichWithLiveOrderFlow(
        base: MarketNewsOverview,
        liveSignals: List<QuantSignal>
    ): MarketNewsOverview {
        if (liveSignals.isEmpty()) return base
        val flowItems = buildOrderFlowCatalystNews(liveSignals)
        val nonFlowItems = base.items.filterNot { it.id.startsWith("quant_flow_") }
        val merged = (flowItems + nonFlowItems).take(36)
        return buildOverviewFromItems(merged, liveSignals)
    }

    private fun buildOverviewFromItems(
        items: List<MarketNewsItem>,
        liveSignals: List<QuantSignal>
    ): MarketNewsOverview {
        val tickerMap = HashMap<String, TickerNewsIntelligence>()
        val signalByTicker = liveSignals.associateBy { it.ticker }

        for (meta in QuantSignalEngine.TOP_MOEX_STOCKS) {
            val ticker = meta.ticker
            val matching = items.filter { ticker in it.relatedTickers }
            val sig = signalByTicker[ticker]

            // Combine news sentiment with live intraday order-flow momentum
            val newsScoreAvg = if (matching.isNotEmpty()) {
                matching.map { it.sentimentScore }.average()
            } else {
                0.0
            }
            val flowBonus = if (sig != null) {
                val changeFactor = (sig.changePercent * 8.5).coerceIn(-35.0, 35.0)
                val volBonus = if (sig.volumeRatio >= 1.25) {
                    if (sig.changePercent >= 0) 14.0 else -14.0
                } else 0.0
                changeFactor + volBonus
            } else 0.0

            val combinedScore = if (matching.isNotEmpty()) {
                (newsScoreAvg * 0.68 + flowBonus * 0.32).roundToInt().coerceIn(-95, 95)
            } else {
                flowBonus.roundToInt().coerceIn(-80, 80)
            }

            val sentimentType = when {
                combinedScore >= 15 -> NewsSentimentType.BULLISH
                combinedScore <= -15 -> NewsSentimentType.BEARISH
                else -> NewsSentimentType.NEUTRAL
            }

            val catalystNote = matching.firstOrNull()?.title ?: when {
                combinedScore >= 20 -> "Приток институционального объема и позитивный фон по сектору"
                combinedScore <= -20 -> "Давление продавцов и осторожный новостной фон"
                else -> "Нейтральный новостной фон • Торговля от уровней VWAP и EMA"
            }

            tickerMap[ticker] = TickerNewsIntelligence(
                ticker = ticker,
                sentimentScore = combinedScore,
                sentimentType = sentimentType,
                headlineCount = matching.size,
                topCatalystNote = catalystNote
            )
        }

        val bullish = items.count { it.sentiment == NewsSentimentType.BULLISH }
        val bearish = items.count { it.sentiment == NewsSentimentType.BEARISH }
        val neutral = items.count { it.sentiment == NewsSentimentType.NEUTRAL }

        val overallScore = if (tickerMap.isNotEmpty()) {
            tickerMap.values.map { it.sentimentScore }.average().roundToInt().coerceIn(-100, 100)
        } else 0

        val regimeLabel = when {
            overallScore >= 25 -> "🟢 Уверенный Позитив (Risk-On на Мосбирже)"
            overallScore >= 8 -> "🟢 Умеренно-бычий фон (Покупка сильных эмитентов)"
            overallScore <= -25 -> "🔴 Осторожный / Медвежий фон (Защита капитала)"
            overallScore <= -8 -> "🔴 Умеренное давление (Избирательный Short/Wait)"
            else -> "⚖️ Сбалансированный рынок (Фокус на точечных сигналах Quant)"
        }

        return MarketNewsOverview(
            marketSentimentIndex = overallScore,
            marketRegimeLabel = regimeLabel,
            bullishCount = bullish,
            bearishCount = bearish,
            neutralCount = neutral,
            items = items,
            tickerIntelligence = tickerMap
        )
    }

    private fun buildOrderFlowCatalystNews(liveSignals: List<QuantSignal>): List<MarketNewsItem> {
        if (liveSignals.isEmpty()) return emptyList()
        val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
        val items = mutableListOf<MarketNewsItem>()

        // 1. Top Volume & Active Signal Leader
        val topActive = liveSignals
            .filter { it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT }
            .maxByOrNull { it.confidence }

        if (topActive != null) {
            val isLong = topActive.direction == SignalDirection.LONG
            val score = if (isLong) topActive.confidence / 2 + 25 else -(topActive.confidence / 2 + 25)
            items.add(
                MarketNewsItem(
                    id = "quant_flow_top_${topActive.ticker}",
                    title = "Квант-радар MOEX: ${topActive.ticker} (${topActive.name}) формирует ${topActive.direction.label} (${topActive.confidence}%)",
                    summary = "Алгоритмический детектор зафиксировал сильное совпадение факторов по ${topActive.ticker}: ${topActive.reason}. " +
                        "Вход: ${QuantSignalEngine.formatPrice(topActive.entryPrice, topActive.minStep)} ₽, " +
                        "TP: ${QuantSignalEngine.formatPrice(topActive.takeProfit, topActive.minStep)} ₽, " +
                        "SL: ${QuantSignalEngine.formatPrice(topActive.stopLoss, topActive.minStep)} ₽.",
                    sourceName = "MOEX Quant Алго-Радар",
                    publishedTime = "LIVE • $nowStr",
                    url = QuantSignalEngine.tradingViewUrl(topActive.ticker),
                    relatedTickers = listOf(topActive.ticker),
                    sentiment = if (isLong) NewsSentimentType.BULLISH else NewsSentimentType.BEARISH,
                    sentimentScore = score.coerceIn(-95, 95),
                    impactLevel = NewsImpactLevel.HIGH,
                    tradingViewSymbolUrl = QuantSignalEngine.tradingViewUrl(topActive.ticker)
                )
            )
        }

        // 2. Abnormal Volume Spike Detection
        val volLeader = liveSignals.maxByOrNull { it.volumeRatio }
        if (volLeader != null && volLeader.volumeRatio >= 1.15) {
            val isBull = volLeader.changePercent >= 0.0
            val volPct = String.format(Locale.US, "%.1f", volLeader.volumeRatio)
            val valBillions = String.format(Locale.US, "%.2f", volLeader.valTodayRub / 1_000_000_000.0)
            items.add(
                MarketNewsItem(
                    id = "quant_flow_vol_${volLeader.ticker}",
                    title = "Всплеск ликвидности в ${volLeader.ticker} (${volLeader.name}): объем x$volPct к норме ($valBillions млрд ₽)",
                    summary = "Крупные участники (Smart Money) проявляют повышенную активность в ${volLeader.ticker}. " +
                        "Внутридневное изменение: ${QuantSignalEngine.formatSignedPercent(volLeader.changePercent)}, " +
                        "цена относительно VWAP: ${QuantSignalEngine.formatPrice(volLeader.vwap, volLeader.minStep)} ₽.",
                    sourceName = "Поток Ордеров TQBR",
                    publishedTime = "LIVE • $nowStr",
                    url = QuantSignalEngine.tradingViewUrl(volLeader.ticker),
                    relatedTickers = listOf(volLeader.ticker),
                    sentiment = if (isBull) NewsSentimentType.BULLISH else NewsSentimentType.BEARISH,
                    sentimentScore = if (isBull) 48 else -48,
                    impactLevel = NewsImpactLevel.HIGH,
                    tradingViewSymbolUrl = QuantSignalEngine.tradingViewUrl(volLeader.ticker)
                )
            )
        }

        // 3. Sector Rotation Summary (Oil & Gas vs Financials vs Tech/Metals)
        val financials = liveSignals.filter { it.ticker in listOf("SBER", "T", "MOEX") }
        val oilGas = liveSignals.filter { it.ticker in listOf("LKOH", "ROSN", "NVTK", "TATN", "GAZP", "SNGS") }
        if (financials.isNotEmpty() && oilGas.isNotEmpty()) {
            val finAvg = financials.map { it.changePercent }.average()
            val oilAvg = oilGas.map { it.changePercent }.average()
            val leaderSectorName = if (finAvg >= oilAvg) "Финансовый сектор (SBER, T, MOEX)" else "Нефтегазовый сектор (LKOH, ROSN, TATN)"
            val leaderTickers = if (finAvg >= oilAvg) listOf("SBER", "T", "MOEX") else listOf("LKOH", "ROSN", "TATN")
            val bestStock = liveSignals.filter { it.ticker in leaderTickers }.maxByOrNull { it.changePercent } ?: liveSignals.first()
            val avgMove = maxOf(finAvg, oilAvg)

            items.add(
                MarketNewsItem(
                    id = "quant_flow_sector_rotation",
                    title = "Секторальная ротация Мосбиржи: лидирует $leaderSectorName (${QuantSignalEngine.formatSignedPercent(avgMove)})",
                    summary = "Институциональный капитал фокусируется в бумагах ${leaderTickers.joinToString(", ")}. " +
                        "Фаворит сектора по динамике — ${bestStock.ticker} (${bestStock.name}) с ценой ${QuantSignalEngine.formatPrice(bestStock.currentPrice, bestStock.minStep)} ₽.",
                    sourceName = "Макро-Аналитика MOEX",
                    publishedTime = "Сегодня • $nowStr",
                    url = QuantSignalEngine.tradingViewUrl(bestStock.ticker),
                    relatedTickers = leaderTickers,
                    sentiment = if (avgMove >= -0.15) NewsSentimentType.BULLISH else NewsSentimentType.BEARISH,
                    sentimentScore = (avgMove * 22.0).roundToInt().coerceIn(-65, 65),
                    impactLevel = NewsImpactLevel.MEDIUM,
                    tradingViewSymbolUrl = QuantSignalEngine.tradingViewUrl(bestStock.ticker)
                )
            )
        }

        return items
    }

    private fun fetchMoexSiteNews(): List<MarketNewsItem> {
        val url = "https://iss.moex.com/iss/sitenews.json?iss.meta=off"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MOEX-Pulse-Android/2.0")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val newsBlock = json.optJSONObject("sitenews") ?: return emptyList()
                val cols = newsBlock.optJSONArray("columns") ?: return emptyList()
                val data = newsBlock.optJSONArray("data") ?: return emptyList()

                var idIdx = -1
                var titleIdx = -1
                var pubIdx = -1
                for (i in 0 until cols.length()) {
                    when (cols.optString(i).lowercase(Locale.US)) {
                        "id" -> idIdx = i
                        "title" -> titleIdx = i
                        "published_at" -> pubIdx = i
                    }
                }
                if (titleIdx < 0) return emptyList()

                val list = mutableListOf<MarketNewsItem>()
                val limit = minOf(data.length(), 18)
                for (i in 0 until limit) {
                    val row = data.optJSONArray(i) ?: continue
                    val id = if (idIdx >= 0) row.optString(idIdx, "$i") else "$i"
                    val rawTitle = row.optString(titleIdx, "").let(::cleanHtmlEntities).trim()
                    if (rawTitle.length < 8) continue
                    val pubTime = if (pubIdx >= 0) row.optString(pubIdx, "").take(16) else "Сегодня"

                    val analysis = analyzeNewsText(rawTitle, "Официальное сообщение Московской Биржи (MOEX ISS)")
                    val primaryTicker = analysis.tickers.firstOrNull() ?: "MOEX"

                    list.add(
                        MarketNewsItem(
                            id = "moex_iss_$id",
                            title = rawTitle,
                            summary = "Официальный релиз Московской Биржи • Влияние на ${analysis.tickers.ifEmpty { listOf("MOEX") }.joinToString(", ")}",
                            sourceName = "Московская Биржа (ISS)",
                            publishedTime = pubTime,
                            url = "https://www.moex.com/n$id",
                            relatedTickers = analysis.tickers.ifEmpty { listOf("MOEX") },
                            sentiment = analysis.sentiment,
                            sentimentScore = analysis.score,
                            impactLevel = analysis.impact,
                            tradingViewSymbolUrl = QuantSignalEngine.tradingViewUrl(primaryTicker)
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchRssFeed(url: String, sourceLabel: String): List<MarketNewsItem> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; MOEX-Pulse-Quant/2.0)")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val xml = response.body?.string() ?: return emptyList()
                parseRssItems(xml, sourceLabel)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseRssItems(xml: String, sourceLabel: String): List<MarketNewsItem> {
        val itemRegex = Regex("<item[\\s>](.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val titleRegex = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val descRegex = Regex("<description>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</description>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val linkRegex = Regex("<link>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</link>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val dateRegex = Regex("<pubDate>(.*?)</pubDate>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        val results = mutableListOf<MarketNewsItem>()
        val matches = itemRegex.findAll(xml).take(25)
        var idx = 0

        for (match in matches) {
            val block = match.groupValues[1]
            val title = titleRegex.find(block)?.groupValues?.getOrNull(1)?.let(::cleanHtmlEntities)?.trim().orEmpty()
            if (title.length < 10) continue
            val desc = descRegex.find(block)?.groupValues?.getOrNull(1)?.let(::cleanHtmlEntities)?.trim().orEmpty()
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .take(260)
            val link = linkRegex.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val pubDate = dateRegex.find(block)?.groupValues?.getOrNull(1)?.trim()?.take(22) ?: "Сегодня"

            val analysis = analyzeNewsText(title, desc)
            // Keep items that either mention a MOEX stock/keyword or have financial keywords
            val isFinancialRelevant = analysis.tickers.isNotEmpty() ||
                abs(analysis.score) >= 14 ||
                title.lowercase(Locale.getDefault()).let { t ->
                    t.contains("акци") || t.contains("рубл") || t.contains("цб") ||
                        t.contains("ставк") || t.contains("бирж") || t.contains("нефт") ||
                        t.contains("индекс") || t.contains("дивиденд") || t.contains("отчет") ||
                        t.contains("прибыл") || t.contains("инвест") || t.contains("рынок") ||
                        t.contains("экономик") || t.contains("банк")
                }

            if (!isFinancialRelevant) continue
            idx++
            val primaryTicker = analysis.tickers.firstOrNull()
            results.add(
                MarketNewsItem(
                    id = "rss_${sourceLabel.hashCode()}_$idx",
                    title = title,
                    summary = if (desc.isNotBlank() && desc != title) desc else "Финансовая сводка • $sourceLabel",
                    sourceName = sourceLabel,
                    publishedTime = pubDate,
                    url = link.ifBlank {
                        if (primaryTicker != null) QuantSignalEngine.tradingViewUrl(primaryTicker)
                        else "https://ru.tradingview.com/markets/stocks-russia/"
                    },
                    relatedTickers = analysis.tickers,
                    sentiment = analysis.sentiment,
                    sentimentScore = analysis.score,
                    impactLevel = analysis.impact,
                    tradingViewSymbolUrl = primaryTicker?.let { QuantSignalEngine.tradingViewUrl(it) }
                )
            )
            if (results.size >= 14) break
        }
        return results
    }

    data class TextSentimentAnalysis(
        val tickers: List<String>,
        val score: Int,
        val sentiment: NewsSentimentType,
        val impact: NewsImpactLevel
    )

    fun analyzeNewsText(title: String, body: String): TextSentimentAnalysis {
        val combined = "$title $body".lowercase(Locale.getDefault())
        val detectedTickers = mutableListOf<String>()

        for ((ticker, keywords) in tickerKeywords) {
            if (keywords.any { kw -> combined.contains(kw) }) {
                detectedTickers.add(ticker)
            }
        }

        var rawScore = 0
        for ((term, weight) in positiveTerms) {
            if (combined.contains(term)) {
                rawScore += weight
            }
        }
        for ((term, weight) in negativeTerms) {
            if (combined.contains(term)) {
                rawScore += weight
            }
        }

        // Title matches carry extra weight
        val lowerTitle = title.lowercase(Locale.getDefault())
        if (lowerTitle.contains("дивиденд") || lowerTitle.contains("рекорд") || lowerTitle.contains("вырос")) {
            rawScore += 18
        }
        if (lowerTitle.contains("убыток") || lowerTitle.contains("санкц") || lowerTitle.contains("упал") || lowerTitle.contains("снизил")) {
            rawScore -= 18
        }

        val finalScore = rawScore.coerceIn(-95, 95)
        val sentiment = when {
            finalScore >= 14 -> NewsSentimentType.BULLISH
            finalScore <= -14 -> NewsSentimentType.BEARISH
            else -> NewsSentimentType.NEUTRAL
        }

        val impact = when {
            abs(finalScore) >= 35 || (detectedTickers.isNotEmpty() && abs(finalScore) >= 20) -> NewsImpactLevel.HIGH
            detectedTickers.isNotEmpty() || abs(finalScore) >= 15 -> NewsImpactLevel.MEDIUM
            else -> NewsImpactLevel.INFO
        }

        return TextSentimentAnalysis(
            tickers = detectedTickers.distinct().take(4),
            score = finalScore,
            sentiment = sentiment,
            impact = impact
        )
    }

    private fun cleanHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
