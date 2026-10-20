package com.example.quant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
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
    val tradingViewSymbolUrl: String?,
    val isRecent2Hours: Boolean = true,
    val hasHighRiskStopWords: Boolean = false,
    val stopWordsFound: List<String> = emptyList()
)

data class TickerNewsIntelligence(
    val ticker: String,
    val sentimentScore: Int, // -100..+100
    val sentimentType: NewsSentimentType,
    val headlineCount: Int,
    val topCatalystNote: String,
    val recentBannerHeadline: String? = null,
    val hasHighRiskWarning: Boolean = false,
    val highRiskReason: String? = null
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

    // Supports all 19 sector tickers + general market triggers
    private val tickerKeywords: Map<String, List<String>> = mapOf(
        "SBER" to listOf("sber", "сбер", "сбербанк", "греф", "банковск"),
        "T" to listOf("t-bank", "т-технологи", "т-банк", "тинькофф", "tcsg", "финтех"),
        "VTBR" to listOf("vtbr", "втб", "костин", "банк втб"),
        "MOEX" to listOf("moex", "мосбирж", "московская биржа", "торги", "индекс мосбиржи", "ключевая ставка", "цб рф", "набиуллин"),
        "LKOH" to listOf("lkoh", "лукойл", "нефт", "brent", "опек"),
        "ROSN" to listOf("rosn", "роснефт", "восток ойл", "сечин", "нефт"),
        "NVTK" to listOf("nvtk", "новатэк", "арктик спг", "ямал спг", "михельсон"),
        "GAZP" to listOf("gazp", "газпром", "газов", "спг", "трубопровод", "миллер"),
        "SNGS" to listOf("sngs", "сургутнефтегаз", "сургут", "кубышк", "валютн"),
        "TATN" to listOf("tatn", "татнефт", "татарстан", "нефтепереработ"),
        "GMKN" to listOf("gmkn", "норникел", "никел", "паллади", "медь", "потанин"),
        "CHMF" to listOf("chmf", "северстал", "сталь", "металлург", "мордашов"),
        "PLZL" to listOf("plzl", "полюс", "золот", "gold", "сухой лог"),
        "NLMK" to listOf("nlmk", "нлмк", "липецк", "лисин", "сталь"),
        "ALRS" to listOf("alrs", "алрос", "алмаз", "бриллиант", "якут"),
        "YDEX" to listOf("ydex", "яндекс", "yandex", "ит-сектор", "технологич", "ии"),
        "MTSS" to listOf("mtss", "мтс", "телеком", "связь", "экосистем"),
        "POSI" to listOf("posi", "positive", "позитив", "кибербезопасн", "группа позитив"),
        "VKCO" to listOf("vkco", "вк ", "vk ", "вконтакте", "соцсет")
    )

    // Module 7: Explicit stop-words that trigger High-Risk Warning on the stock card
    private val highRiskStopWords: List<Pair<String, String>> = listOf(
        "санкц" to "санкции",
        "убыток" to "убыток",
        "отмена дивиденд" to "отмена дивидендов",
        "отказался от дивиденд" to "отмена дивидендов",
        "не выплачивать дивиденд" to "отказ от дивидендов",
        "допэмисс" to "допэмиссия",
        "штраф" to "штраф",
        "арест" to "арест активов",
        "дефолт" to "риск дефолта"
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
        "отмена дивиденд" to -38,
        "санкц" to -34,
        "ограничен" to -22,
        "повышение ставк" to -26,
        "жестк" to -20,
        "инфляц" to -18,
        "падени" to -22,
        "снизился" to -18,
        "сократил" to -20,
        "налог" to -20,
        "ндпи" to -24,
        "допэмисс" to -34,
        "размыти" to -28,
        "авари" to -26,
        "штраф" to -22,
        "давлени" to -18,
        "распродаж" to -26,
        "коррекц" to -16
    )

    @Volatile
    private var cachedOverview: MarketNewsOverview? = null
    @Volatile
    private var lastFetchTimeMs: Long = 0L

    suspend fun fetchLiveMarketNews(
        liveSignals: List<QuantSignal> = emptyList(),
        forceRefresh: Boolean = false
    ): MarketNewsOverview = fetchNewsAndSentiment(liveSignals, forceRefresh)

    suspend fun fetchNewsAndSentiment(
        liveSignals: List<QuantSignal> = emptyList(),
        forceRefresh: Boolean = false
    ): MarketNewsOverview = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = cachedOverview
        if (!forceRefresh && existing != null && (now - lastFetchTimeMs) < 40_000L && existing.items.isNotEmpty()) {
            return@withContext enrichWithLiveOrderFlow(existing, liveSignals)
        }

        val combinedItems = coroutineScope {
            val smartLabDeferred = async {
                fetchRssFeedWithXmlPullParser(
                    url = "https://smart-lab.ru/rss/news/",
                    sourceLabel = "Smart-Lab RSS"
                )
            }
            val rbcRssDeferred = async {
                fetchRssFeedWithXmlPullParser(
                    url = "https://rssexport.rbc.ru/rbcnews/news/20/full.rss",
                    sourceLabel = "РБК Инвестиции"
                )
            }
            val primeRssDeferred = async {
                fetchRssFeedWithXmlPullParser(
                    url = "https://prime.ru/export/rss2/index.xml",
                    sourceLabel = "ПРАЙМ / Интерфакс"
                )
            }
            val moexSiteDeferred = async { fetchMoexSiteNews() }

            val rawList = mutableListOf<MarketNewsItem>()
            rawList.addAll(smartLabDeferred.await())
            rawList.addAll(moexSiteDeferred.await())
            rawList.addAll(rbcRssDeferred.await())
            rawList.addAll(primeRssDeferred.await())
            rawList
        }

        val orderFlowNews = buildOrderFlowCatalystNews(liveSignals)
        val allMerged = (orderFlowNews + combinedItems)
            .distinctBy { it.title.lowercase(Locale.getDefault()).take(55) }
            .take(40)

        val overview = buildOverviewFromItems(allMerged, liveSignals)
        cachedOverview = overview
        lastFetchTimeMs = now
        overview
    }

    private fun enrichWithLiveOrderFlow(
        base: MarketNewsOverview,
        liveSignals: List<QuantSignal>
    ): MarketNewsOverview {
        if (liveSignals.isEmpty()) return base
        val flowItems = buildOrderFlowCatalystNews(liveSignals)
        val nonFlowItems = base.items.filterNot { it.id.startsWith("quant_flow_") }
        val merged = (flowItems + nonFlowItems).take(40)
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
                (newsScoreAvg * 0.70 + flowBonus * 0.30).roundToInt().coerceIn(-95, 95)
            } else {
                flowBonus.roundToInt().coerceIn(-80, 80)
            }

            val sentimentType = when {
                combinedScore >= 15 -> NewsSentimentType.BULLISH
                combinedScore <= -15 -> NewsSentimentType.BEARISH
                else -> NewsSentimentType.NEUTRAL
            }

            val recentExternalNews = matching.firstOrNull { !it.id.startsWith("quant_flow_") && it.isRecent2Hours }
                ?: matching.firstOrNull { it.isRecent2Hours }

            val stopWordItem = matching.firstOrNull { it.hasHighRiskStopWords }
            val hasHighRisk = stopWordItem != null
            val highRiskNote = if (stopWordItem != null) {
                "[РИСК-ФАКТОР: ${stopWordItem.stopWordsFound.joinToString(", ")}] ${stopWordItem.title.take(70)}"
            } else null

            val catalystNote = recentExternalNews?.title ?: matching.firstOrNull()?.title ?: when {
                combinedScore >= 20 -> "Приток институционального объема и позитивный фон по сектору"
                combinedScore <= -20 -> "Давление продавцов и осторожный новостной фон"
                else -> "Нейтральный новостной фон • Торговля от уровней VWAP и EMA"
            }

            tickerMap[ticker] = TickerNewsIntelligence(
                ticker = ticker,
                sentimentScore = combinedScore,
                sentimentType = sentimentType,
                headlineCount = matching.size,
                topCatalystNote = catalystNote,
                recentBannerHeadline = recentExternalNews?.title,
                hasHighRiskWarning = hasHighRisk,
                highRiskReason = highRiskNote
            )
        }

        val bullCount = items.count { it.sentiment == NewsSentimentType.BULLISH }
        val bearCount = items.count { it.sentiment == NewsSentimentType.BEARISH }
        val neutCount = items.count { it.sentiment == NewsSentimentType.NEUTRAL }

        val overallIndex = if (items.isNotEmpty()) {
            items.map { it.sentimentScore }.average().roundToInt().coerceIn(-100, 100)
        } else 0

        val regimeLabel = when {
            overallIndex >= 18 -> "Уверенный позитив на рынке РФ (Risk-On)"
            overallIndex >= 6 -> "Умеренно-бычий новостной фон"
            overallIndex <= -18 -> "Давление негативных новостей (Risk-Off)"
            overallIndex <= -6 -> "Осторожный / смешанный фон"
            else -> "Нейтральный баланс новостей"
        }

        return MarketNewsOverview(
            marketSentimentIndex = overallIndex,
            marketRegimeLabel = regimeLabel,
            bullishCount = bullCount,
            bearishCount = bearCount,
            neutralCount = neutCount,
            items = items,
            tickerIntelligence = tickerMap
        )
    }

    private fun buildOrderFlowCatalystNews(liveSignals: List<QuantSignal>): List<MarketNewsItem> {
        if (liveSignals.isEmpty()) return emptyList()
        val list = mutableListOf<MarketNewsItem>()
        val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis())

        val volumeLeaders = liveSignals.filter { it.volumeRatio >= 1.25 || abs(it.changePercent) >= 1.2 }
            .sortedByDescending { abs(it.changePercent) * it.volumeRatio }
            .take(5)

        for (sig in volumeLeaders) {
            val isBull = sig.changePercent >= 0.0
            val sentType = if (isBull) NewsSentimentType.BULLISH else NewsSentimentType.BEARISH
            val score = (sig.changePercent * 14.0).roundToInt().coerceIn(-85, 85)
            val actionWord = if (isBull) "всплеск спроса и рост" else "давление продавцов и снижение"
            val title = "${sig.name} (${sig.ticker}): $actionWord ${QuantSignalEngine.formatSignedPercent(sig.changePercent)} на объеме x${QuantSignalEngine.formatOneDec(sig.volumeRatio)}"
            val summary = "Алго-радар Quant 2.0 зафиксировал активность в бумаге ${sig.ticker}. " +
                "Текущая цена ${QuantSignalEngine.formatPrice(sig.currentPrice, sig.minStep)} ₽, " +
                "VWAP ${QuantSignalEngine.formatPrice(sig.vwap, sig.minStep)} ₽, RSI ${QuantSignalEngine.formatOneDec(sig.rsi14)}."

            list.add(
                MarketNewsItem(
                    id = "quant_flow_${sig.ticker}",
                    title = title,
                    summary = summary,
                    sourceName = "MOEX Quant 2.0 Radar",
                    publishedTime = "Сегодня $nowStr",
                    url = sig.tradingViewUrl,
                    relatedTickers = listOf(sig.ticker),
                    sentiment = sentType,
                    sentimentScore = if (score == 0) (if (isBull) 22 else -22) else score,
                    impactLevel = if (abs(sig.changePercent) >= 2.0 || sig.volumeRatio >= 1.6) NewsImpactLevel.HIGH else NewsImpactLevel.MEDIUM,
                    tradingViewSymbolUrl = sig.tradingViewUrl,
                    isRecent2Hours = true,
                    hasHighRiskStopWords = false,
                    stopWordsFound = emptyList()
                )
            )
        }
        return list
    }

    private fun fetchMoexSiteNews(): List<MarketNewsItem> {
        val url = "https://iss.moex.com/iss/sitenews.json?iss.meta=off"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MOEX-Pulse-Android/3.0")
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
                var publishedIdx = -1
                for (i in 0 until cols.length()) {
                    when (cols.optString(i).lowercase(Locale.US)) {
                        "id" -> idIdx = i
                        "title" -> titleIdx = i
                        "published_at", "modified_at" -> if (publishedIdx == -1) publishedIdx = i
                    }
                }
                if (titleIdx == -1) return emptyList()

                val items = mutableListOf<MarketNewsItem>()
                val limit = minOf(data.length(), 20)
                for (i in 0 until limit) {
                    val row = data.optJSONArray(i) ?: continue
                    val newsId = if (idIdx >= 0) row.optString(idIdx, i.toString()) else i.toString()
                    val rawTitle = cleanHtmlEntities(row.optString(titleIdx, "")).trim()
                    if (rawTitle.length < 8) continue
                    val pubTime = if (publishedIdx >= 0) {
                        row.optString(publishedIdx, "").take(16)
                    } else "Сегодня"

                    val analysis = analyzeNewsText(rawTitle, "")
                    val primaryTicker = analysis.tickers.firstOrNull() ?: "MOEX"
                    val tickersList = if (analysis.tickers.isEmpty()) listOf("MOEX") else analysis.tickers

                    items.add(
                        MarketNewsItem(
                            id = "moex_iss_$newsId",
                            title = rawTitle,
                            summary = "Официальное сообщение Московской Биржи (MOEX ISS) • Влияние на индекс IMOEX и режим торгов TQBR.",
                            sourceName = "Мосбиржа (ISS)",
                            publishedTime = pubTime,
                            url = "https://www.moex.com/n$newsId",
                            relatedTickers = tickersList,
                            sentiment = analysis.sentiment,
                            sentimentScore = analysis.score,
                            impactLevel = analysis.impact,
                            tradingViewSymbolUrl = QuantSignalEngine.tradingViewUrl(primaryTicker),
                            isRecent2Hours = true,
                            hasHighRiskStopWords = analysis.stopWords.isNotEmpty(),
                            stopWordsFound = analysis.stopWords
                        )
                    )
                }
                items
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Module 7: Lightweight native Android XmlPullParser RSS reader (without heavy dependencies).
     */
    private fun fetchRssFeedWithXmlPullParser(url: String, sourceLabel: String): List<MarketNewsItem> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; MOEX-Pulse-AI-Pro/3.0)")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val xmlString = response.body?.string() ?: return emptyList()
                parseRssWithXmlPullParser(xmlString, sourceLabel)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseRssWithXmlPullParser(xml: String, sourceLabel: String): List<MarketNewsItem> {
        val results = mutableListOf<MarketNewsItem>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var insideItem = false
            var currentTag = ""
            var title = ""
            var description = ""
            var link = ""
            var pubDate = ""
            var idx = 0

            while (eventType != XmlPullParser.END_DOCUMENT && results.size < 16) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name?.lowercase(Locale.US).orEmpty()
                        if (tagName == "item") {
                            insideItem = true
                            title = ""
                            description = ""
                            link = ""
                            pubDate = ""
                        } else if (insideItem) {
                            currentTag = tagName
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideItem && currentTag.isNotEmpty()) {
                            val textChunk = parser.text.orEmpty()
                            when (currentTag) {
                                "title" -> title += textChunk
                                "description" -> description += textChunk
                                "link" -> link += textChunk
                                "pubdate", "dc:date" -> pubDate += textChunk
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name?.lowercase(Locale.US).orEmpty()
                        if (tagName == "item" && insideItem) {
                            insideItem = false
                            val cleanTitle = cleanHtmlEntities(title).trim()
                            val cleanDesc = cleanHtmlEntities(description)
                                .replace(Regex("<[^>]+>"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(250)

                            if (cleanTitle.length >= 10) {
                                val analysis = analyzeNewsText(cleanTitle, cleanDesc)
                                val isFinancialRelevant = analysis.tickers.isNotEmpty() ||
                                    abs(analysis.score) >= 14 ||
                                    analysis.stopWords.isNotEmpty() ||
                                    cleanTitle.lowercase(Locale.getDefault()).let { t ->
                                        t.contains("акци") || t.contains("рубл") || t.contains("цб") ||
                                            t.contains("ставк") || t.contains("бирж") || t.contains("нефт") ||
                                            t.contains("индекс") || t.contains("дивиденд") || t.contains("отчет") ||
                                            t.contains("прибыл") || t.contains("инвест") || t.contains("рынок") ||
                                            t.contains("сбер") || t.contains("газпром") || t.contains("лукойл") ||
                                            t.contains("яндекс")
                                    }

                                if (isFinancialRelevant) {
                                    idx++
                                    val primaryTicker = analysis.tickers.firstOrNull()
                                    results.add(
                                        MarketNewsItem(
                                            id = "xml_rss_${sourceLabel.hashCode()}_$idx",
                                            title = cleanTitle,
                                            summary = if (cleanDesc.isNotBlank() && cleanDesc != cleanTitle) cleanDesc else "Срочная лента • $sourceLabel",
                                            sourceName = sourceLabel,
                                            publishedTime = pubDate.trim().take(22).ifBlank { "Сегодня" },
                                            url = link.trim().ifBlank {
                                                if (primaryTicker != null) QuantSignalEngine.tradingViewUrl(primaryTicker)
                                                else "https://ru.tradingview.com/markets/stocks-russia/"
                                            },
                                            relatedTickers = analysis.tickers,
                                            sentiment = analysis.sentiment,
                                            sentimentScore = analysis.score,
                                            impactLevel = analysis.impact,
                                            tradingViewSymbolUrl = primaryTicker?.let { QuantSignalEngine.tradingViewUrl(it) },
                                            isRecent2Hours = true,
                                            hasHighRiskStopWords = analysis.stopWords.isNotEmpty(),
                                            stopWordsFound = analysis.stopWords
                                        )
                                    )
                                }
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return results
    }

    data class TextSentimentAnalysis(
        val tickers: List<String>,
        val score: Int,
        val sentiment: NewsSentimentType,
        val impact: NewsImpactLevel,
        val stopWords: List<String>
    )

    fun analyzeNewsText(title: String, body: String): TextSentimentAnalysis {
        val combined = "$title $body".lowercase(Locale.getDefault())
        val detectedTickers = mutableListOf<String>()

        for ((ticker, keywords) in tickerKeywords) {
            if (keywords.any { kw -> combined.contains(kw) }) {
                detectedTickers.add(ticker)
            }
        }

        val matchedStopWords = mutableListOf<String>()
        for ((stem, label) in highRiskStopWords) {
            if (combined.contains(stem)) {
                matchedStopWords.add(label)
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
                rawScore -= weight
            }
        }

        if (matchedStopWords.isNotEmpty()) {
            rawScore -= 25 * matchedStopWords.size
        }

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
            matchedStopWords.isNotEmpty() || abs(finalScore) >= 35 || (detectedTickers.isNotEmpty() && abs(finalScore) >= 20) -> NewsImpactLevel.HIGH
            detectedTickers.isNotEmpty() || abs(finalScore) >= 15 -> NewsImpactLevel.MEDIUM
            else -> NewsImpactLevel.INFO
        }

        return TextSentimentAnalysis(
            tickers = detectedTickers.distinct().take(4),
            score = finalScore,
            sentiment = sentiment,
            impact = impact,
            stopWords = matchedStopWords.distinct()
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
