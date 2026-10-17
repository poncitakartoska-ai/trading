package com.example.quant

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AiAssistantMode(
    val code: String,
    val titleRu: String,
    val subtitleRu: String,
    val primaryModelId: String,
    val fallbackModelId: String,
    val useGoogleSearch: Boolean = false,
    val useGoogleMaps: Boolean = false
) {
    PRO_ARGON(
        code = "PRO_ARGON",
        titleRu = "Quant Pro (Argon)",
        subtitleRu = "Глубокий 7-факторный Quant 2.0 и новостной разбор (gemini-3.1-pro)",
        primaryModelId = "gemini-3.1-pro-preview",
        fallbackModelId = "gemini-flash-latest",
        useGoogleSearch = false,
        useGoogleMaps = false
    ),
    FLASH_SEARCH(
        code = "FLASH_SEARCH",
        titleRu = "Google Search Live",
        subtitleRu = "Актуальные новости РФ, дивиденды и отчеты (gemini-3-flash + Search)",
        primaryModelId = "gemini-3-flash-preview",
        fallbackModelId = "gemini-flash-latest",
        useGoogleSearch = true,
        useGoogleMaps = false
    ),
    FLASH_MAPS(
        code = "FLASH_MAPS",
        titleRu = "Google Maps",
        subtitleRu = "Штаб-квартиры эмитентов, биржи и офисы брокеров (gemini-3-flash + Maps)",
        primaryModelId = "gemini-3-flash-preview",
        fallbackModelId = "gemini-flash-latest",
        useGoogleSearch = false,
        useGoogleMaps = true
    ),
    FAST_LITE(
        code = "FAST_LITE",
        titleRu = "Блиц Flash-Lite",
        subtitleRu = "Мгновенный экспресс-ответ по Entry/TP/SL + TradingView (gemini-3.1-flash-lite)",
        primaryModelId = "gemini-3.1-flash-lite-preview",
        fallbackModelId = "gemini-flash-latest",
        useGoogleSearch = false,
        useGoogleMaps = false
    );

    companion object {
        fun fromCode(code: String): AiAssistantMode =
            entries.firstOrNull { it.code == code } ?: PRO_ARGON
    }
}

data class AiTurn(
    val role: String, // "user" or "model"
    val text: String
)

data class GroundingSource(
    val title: String,
    val uri: String,
    val kind: String // "SEARCH", "MAPS", or "TRADINGVIEW"
)

data class AiAssistantResponse(
    val replyText: String,
    val modelUsed: String,
    val sources: List<GroundingSource>
)

object GeminiQuantAssistant {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun buildSystemInstruction(
        mode: AiAssistantMode,
        liveSignals: List<QuantSignal>,
        newsItems: List<MarketNewsItem>,
        depositRub: Double,
        riskPercent: Double
    ): String {
        val activeSignalsSummary = if (liveSignals.isEmpty()) {
            "Котировки MOEX загружаются..."
        } else {
            liveSignals.joinToString("\n") { s ->
                "- ${s.ticker} (${s.name}): ${s.direction.label} (Уверенность ${s.confidence}%, Конфлюэнс ${s.confluencePassedCount}/7), " +
                    "Цена=${QuantSignalEngine.formatPrice(s.currentPrice, s.minStep)} ₽, " +
                    "Entry=${QuantSignalEngine.formatPrice(s.entryPrice, s.minStep)} ₽, " +
                    "TP=${QuantSignalEngine.formatPrice(s.takeProfit, s.minStep)} ₽, " +
                    "SL=${QuantSignalEngine.formatPrice(s.stopLoss, s.minStep)} ₽, " +
                    "VWAP=${QuantSignalEngine.formatPrice(s.vwap, s.minStep)} ₽, " +
                    "EMA20=${QuantSignalEngine.formatPrice(s.ema20, s.minStep)}, " +
                    "EMA50=${QuantSignalEngine.formatPrice(s.ema50, s.minStep)}, " +
                    "RSI=${QuantSignalEngine.formatOneDec(s.rsi14)}, " +
                    "ADX=${QuantSignalEngine.formatOneDec(s.adx14)}, " +
                    "BB%B=${QuantSignalEngine.formatOneDec(s.bollingerPercentB * 100)}%, " +
                    "Новости=${s.newsCatalystSummary} (${s.newsSentimentScore}), " +
                    "TradingView=${s.tradingViewUrl}, " +
                    "Причина: ${s.reason}"
            }
        }

        val newsSummary = if (newsItems.isEmpty()) {
            "Лента новостей загружается..."
        } else {
            newsItems.take(8).joinToString("\n") { item ->
                "• [${item.publishedTime}] (${item.sourceName}, ${item.sentiment.badgeEmoji} ${item.sentiment.labelRu} ${item.sentimentScore}) " +
                    "Тикеры: ${item.relatedTickers.joinToString(",")}: ${item.title}"
            }
        }

        val roleDescription = when (mode) {
            AiAssistantMode.PRO_ARGON ->
                "Ты — главный Quant-стратег и риск-менеджер терминала MOEX Pulse AI (движок Quant 2.0 с 7-факторным конфлюэнсом). " +
                    "Выполняй бескомпромиссный институциональный разбор сделок: проверяй тренд EMA 20/50, положение относительно VWAP, импульс гистограммы MACD, отсутствие перекупленности по Bollinger %B, силу тренда ADX (14) и сентимент свежих новостей."
            AiAssistantMode.FLASH_SEARCH ->
                "Ты — рыночный аналитик MOEX Pulse AI с подключенным Google Search Grounding. " +
                    "Анализируй свежие новости эмитентов Мосбиржи, дивидендные отсечки, отчеты МСФО/РСБУ и риторику ЦБ РФ, сопоставляя их с технической картиной Quant 2.0."
            AiAssistantMode.FLASH_MAPS ->
                "Ты — гео-аналитик инфраструктуры рынка РФ в MOEX Pulse AI с подключенным Google Maps Grounding. " +
                    "Помогай находить штаб-квартиры эмитентов Мосбиржи (Сбербанк, Газпром, ЛУКОЙЛ, Яндекс, Т-Технологии, Мосбиржа), офисы брокеров и финансовые центры с точными адресами."
            AiAssistantMode.FAST_LITE ->
                "Ты — скоростной алгоритмический ассистент MOEX Pulse AI. " +
                    "Давай максимально краткие, четкие и мгновенные ответы по точкам входа Entry, Take Profit, Stop Loss, количеству лотов и прямой ссылке на график TradingView."
        }

        return """
            $roleDescription
            Отвечай на русском языке четко, профессионально и структурированно.
            ВАЖНОЕ ПРАВИЛО: Для КАЖДОГО упомянутого или рекомендованного тикера обязательно указывай прямую ссылку на график TradingView в формате:
            https://ru.tradingview.com/chart/?symbol=MOEX:ТИКЕР
            Параметры риск-менеджмента пользователя: Депозит = ${String.format(Locale.US, "%.0f", depositRub)} ₽, Риск на сделку = ${String.format(Locale.US, "%.1f", riskPercent)}%.
            
            ТЕКУЩИЕ КОТИРОВКИ И 7-ФАКТОРНЫЕ СИГНАЛЫ QUANT 2.0 (MOEX TQBR):
            $activeSignalsSummary
            
            СВЕЖАЯ ЛЕНТА НОВОСТЕЙ И СЕНТИМЕНТ РЫНКА РФ:
            $newsSummary
        """.trimIndent()
    }

    suspend fun sendMultiTurnMessage(
        mode: AiAssistantMode,
        conversationHistory: List<AiTurn>,
        userPrompt: String,
        liveSignals: List<QuantSignal>,
        newsItems: List<MarketNewsItem> = emptyList(),
        depositRub: Double,
        riskPercent: Double
    ): AiAssistantResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val fallbackText = "Для облачного AI-ассистента Gemini укажите ваш ключ `GEMINI_API_KEY` на панели **Secrets** в Google AI Studio.\n\n" +
                "Локальный 7-факторный разбор Quant 2.0 по вашему запросу:\n" +
                buildLocalQuantFallbackSummary(liveSignals, newsItems, depositRub, riskPercent)
            return@withContext AiAssistantResponse(
                replyText = fallbackText,
                modelUsed = "${mode.titleRu} (Локальный Quant 2.0)",
                sources = extractMentionedTickerTradingViewLinks(fallbackText + " " + userPrompt, liveSignals)
            )
        }

        val systemText = buildSystemInstruction(mode, liveSignals, newsItems, depositRub, riskPercent)
        val recentTurns = conversationHistory.takeLast(10) + AiTurn(role = "user", text = userPrompt)

        val primaryAttempt = executeGenerateContentCall(
            apiKey = apiKey,
            modelId = mode.primaryModelId,
            systemText = systemText,
            turns = recentTurns,
            useGoogleSearch = mode.useGoogleSearch,
            useGoogleMaps = mode.useGoogleMaps
        )

        if (primaryAttempt != null) {
            val tvLinks = extractMentionedTickerTradingViewLinks(primaryAttempt.replyText + " " + userPrompt, liveSignals)
            return@withContext primaryAttempt.copy(
                sources = (tvLinks + primaryAttempt.sources).distinctBy { it.uri }.take(8)
            )
        }

        val fallbackAttempt = executeGenerateContentCall(
            apiKey = apiKey,
            modelId = mode.fallbackModelId,
            systemText = systemText,
            turns = recentTurns,
            useGoogleSearch = mode.useGoogleSearch,
            useGoogleMaps = mode.useGoogleMaps
        )

        if (fallbackAttempt != null) {
            val tvLinks = extractMentionedTickerTradingViewLinks(fallbackAttempt.replyText + " " + userPrompt, liveSignals)
            return@withContext fallbackAttempt.copy(
                sources = (tvLinks + fallbackAttempt.sources).distinctBy { it.uri }.take(8)
            )
        }

        val localText = "Не удалось получить ответ от облачного API Gemini. Проверьте `GEMINI_API_KEY` в панели Secrets.\n\n" +
            buildLocalQuantFallbackSummary(liveSignals, newsItems, depositRub, riskPercent)
        AiAssistantResponse(
            replyText = localText,
            modelUsed = mode.titleRu,
            sources = extractMentionedTickerTradingViewLinks(localText + " " + userPrompt, liveSignals)
        )
    }

    private fun extractMentionedTickerTradingViewLinks(
        text: String,
        liveSignals: List<QuantSignal>
    ): List<GroundingSource> {
        val upper = text.uppercase(Locale.getDefault())
        val matched = mutableListOf<GroundingSource>()
        for (meta in QuantSignalEngine.TOP_MOEX_STOCKS) {
            if (upper.contains(meta.ticker) || upper.contains(meta.nameRu.uppercase(Locale.getDefault()))) {
                matched.add(
                    GroundingSource(
                        title = "📈 График TradingView: MOEX:${meta.ticker} (${meta.nameRu})",
                        uri = QuantSignalEngine.tradingViewUrl(meta.ticker),
                        kind = "TRADINGVIEW"
                    )
                )
            }
        }
        if (matched.isEmpty()) {
            liveSignals
                .filter { it.direction != SignalDirection.WAIT }
                .take(3)
                .forEach { s ->
                    matched.add(
                        GroundingSource(
                            title = "📈 График TradingView: MOEX:${s.ticker} (${s.direction.badgeText})",
                            uri = s.tradingViewUrl,
                            kind = "TRADINGVIEW"
                        )
                    )
                }
        }
        return matched.take(5)
    }

    private fun executeGenerateContentCall(
        apiKey: String,
        modelId: String,
        systemText: String,
        turns: List<AiTurn>,
        useGoogleSearch: Boolean,
        useGoogleMaps: Boolean
    ): AiAssistantResponse? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

        val contentsArray = JSONArray()
        for (turn in turns) {
            val role = if (turn.role == "model") "model" else "user"
            val partObj = JSONObject().put("text", turn.text)
            val partsArr = JSONArray().put(partObj)
            val contentObj = JSONObject()
                .put("role", role)
                .put("parts", partsArr)
            contentsArray.put(contentObj)
        }

        val root = JSONObject().apply {
            put("contents", contentsArray)
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemText))
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.30)
                    .put("topP", 0.92)
            )
            if (useGoogleSearch || useGoogleMaps) {
                val toolsArr = JSONArray()
                if (useGoogleSearch) {
                    toolsArr.put(JSONObject().put("googleSearch", JSONObject()))
                }
                if (useGoogleMaps) {
                    toolsArr.put(JSONObject().put("googleMaps", JSONObject()))
                }
                put("tools", toolsArr)
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return null
                if (!response.isSuccessful) {
                    if (useGoogleMaps || useGoogleSearch) {
                        return executeGenerateContentCall(
                            apiKey = apiKey,
                            modelId = modelId,
                            systemText = systemText,
                            turns = turns,
                            useGoogleSearch = false,
                            useGoogleMaps = false
                        )
                    }
                    return null
                }

                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates") ?: return null
                val firstCandidate = candidates.optJSONObject(0) ?: return null
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val partText = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                        if (partText.isNotBlank()) {
                            if (sb.isNotEmpty()) sb.append("\n")
                            sb.append(partText)
                        }
                    }
                }
                val finalText = sb.toString().trim()
                if (finalText.isEmpty()) return null

                val sources = extractGroundingSources(firstCandidate.optJSONObject("groundingMetadata"))
                AiAssistantResponse(
                    replyText = finalText,
                    modelUsed = modelId,
                    sources = sources
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGroundingSources(groundingMetadata: JSONObject?): List<GroundingSource> {
        if (groundingMetadata == null) return emptyList()
        val result = mutableListOf<GroundingSource>()

        val chunks = groundingMetadata.optJSONArray("groundingChunks")
        if (chunks != null) {
            for (i in 0 until chunks.length()) {
                val chunk = chunks.optJSONObject(i) ?: continue
                val web = chunk.optJSONObject("web")
                if (web != null) {
                    val title = web.optString("title", "Веб-источник Google Search")
                    val uri = web.optString("uri", "")
                    if (uri.isNotBlank()) {
                        result.add(GroundingSource(title = title, uri = uri, kind = "SEARCH"))
                    }
                }
                val maps = chunk.optJSONObject("maps")
                if (maps != null) {
                    val title = maps.optString("title", "Локация Google Maps")
                    val uri = maps.optString("uri", "")
                    result.add(GroundingSource(title = title, uri = uri, kind = "MAPS"))
                }
            }
        }

        val queries = groundingMetadata.optJSONArray("webSearchQueries")
        if (queries != null && result.isEmpty()) {
            for (i in 0 until queries.length()) {
                val q = queries.optString(i, "")
                if (q.isNotBlank()) {
                    result.add(
                        GroundingSource(
                            title = "Поисковый запрос: $q",
                            uri = "https://www.google.com/search?q=$q",
                            kind = "SEARCH"
                        )
                    )
                }
            }
        }
        return result.distinctBy { it.title + it.uri }.take(6)
    }

    private fun buildLocalQuantFallbackSummary(
        liveSignals: List<QuantSignal>,
        newsItems: List<MarketNewsItem>,
        depositRub: Double,
        riskPercent: Double
    ): String {
        val active = liveSignals.filter {
            it.direction == SignalDirection.LONG || it.direction == SignalDirection.SHORT
        }
        val riskRub = depositRub * (riskPercent / 100.0)
        return buildString {
            appendLine("• Активных сигналов Quant 2.0 сейчас: ${active.size} из ${liveSignals.size}")
            active.take(4).forEach { s ->
                appendLine(
                    "• ${s.ticker} (${s.direction.badgeText}, ${s.confidence}%, Конфлюэнс ${s.confluencePassedCount}/7): " +
                        "Вход ${QuantSignalEngine.formatPrice(s.entryPrice, s.minStep)} ₽ | " +
                        "TP ${QuantSignalEngine.formatPrice(s.takeProfit, s.minStep)} ₽ | " +
                        "SL ${QuantSignalEngine.formatPrice(s.stopLoss, s.minStep)} ₽ | " +
                        "График: ${s.tradingViewUrl}"
                )
            }
            if (active.isEmpty()) {
                liveSignals.take(3).forEach { s ->
                    appendLine(
                        "• ${s.ticker}: ⏳ WAIT у ${QuantSignalEngine.formatPrice(s.currentPrice, s.minStep)} ₽ " +
                            "(Конфлюэнс ${s.confluencePassedCount}/7, VWAP ${QuantSignalEngine.formatPrice(s.vwap, s.minStep)} ₽, " +
                            "TradingView: ${s.tradingViewUrl})"
                    )
                }
            }
            if (newsItems.isNotEmpty()) {
                val topNews = newsItems.first()
                appendLine("• Главный драйвер ленты: [${topNews.sentiment.badgeEmoji} ${topNews.sentiment.labelRu}] ${topNews.title}")
            }
            append("• Лимит риска на сделку при депозите ${String.format(Locale.US, "%.0f", depositRub)} ₽: ${String.format(Locale.US, "%.0f", riskRub)} ₽.")
        }
    }
}
