package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.QuantStrategyProfile
import com.example.quant.MarketNewsItem
import com.example.quant.NewsSentimentType
import com.example.quant.QuantSignalEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsFeedScreen(
    newsItems: List<MarketNewsItem>,
    selectedTickerFilter: String?,
    marketSentimentIndex: Int,
    marketRegimeLabel: String,
    isLoading: Boolean,
    onSelectTickerFilter: (String?) -> Unit,
    onRefreshNews: () -> Unit,
    onAnalyzeNewsWithAi: (MarketNewsItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current

    val filteredNews: List<MarketNewsItem> = if (selectedTickerFilter == null) {
        newsItems
    } else {
        newsItems.filter { item -> selectedTickerFilter in item.relatedTickers }
    }

    val bullishCount = newsItems.count { it.sentiment == NewsSentimentType.BULLISH }
    val bearishCount = newsItems.count { it.sentiment == NewsSentimentType.BEARISH }
    val neutralCount = newsItems.count { it.sentiment == NewsSentimentType.NEUTRAL }

    val allTickers = QuantSignalEngine.TOP_MOEX_STOCKS.map { it.ticker }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        // Market Sentiment Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Newspaper,
                            contentDescription = null,
                            tint = palette.cyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "НОВОСТНОЙ РАДАР И NLP-СЕНТИМЕНТ РФ",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "MOEX ISS • РБК • ПРАЙМ • $marketRegimeLabel",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .clickable(enabled = !isLoading) { onRefreshNews() }
                            .testTag("refresh_news_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = palette.cyan,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Обновить новости",
                                    tint = palette.cyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Обновить",
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val sentimentColor = when {
                    marketSentimentIndex >= 12 -> palette.longColor
                    marketSentimentIndex <= -12 -> palette.shortColor
                    else -> palette.waitColor
                }
                val sentimentText = when {
                    marketSentimentIndex >= 12 -> "БЫЧИЙ ФОН"
                    marketSentimentIndex <= -12 -> "МЕДВЕЖИЙ ФОН"
                    else -> "НЕЙТРАЛЬНЫЙ"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SentimentStatChip(
                        label = "Индекс фона",
                        value = "${String.format(Locale.US, "%+d", marketSentimentIndex)} ($sentimentText)",
                        color = sentimentColor,
                        modifier = Modifier.weight(1.4f)
                    )
                    SentimentStatChip(
                        label = "Позитив",
                        value = "$bullishCount",
                        color = palette.longColor,
                        modifier = Modifier.weight(0.8f)
                    )
                    SentimentStatChip(
                        label = "Негатив",
                        value = "$bearishCount",
                        color = palette.shortColor,
                        modifier = Modifier.weight(0.8f)
                    )
                    SentimentStatChip(
                        label = "Нейтр.",
                        value = "$neutralCount",
                        color = palette.textSecondary,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
        }

        // Ticker Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTickerFilter == null,
                onClick = { onSelectTickerFilter(null) },
                label = {
                    Text(
                        text = "Все новости (${newsItems.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.cyanDim,
                    selectedLabelColor = palette.cyan,
                    containerColor = palette.surface,
                    labelColor = palette.textSecondary
                )
            )
            allTickers.forEach { ticker ->
                val count = newsItems.count { ticker in it.relatedTickers }
                val selected = selectedTickerFilter == ticker
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTickerFilter(if (selected) null else ticker) },
                    label = {
                        Text(
                            text = if (count > 0) "$ticker ($count)" else ticker,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.longDim,
                        selectedLabelColor = palette.longColor,
                        containerColor = palette.surface,
                        labelColor = palette.textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("news_feed_list"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredNews, key = { it.id }) { item ->
                NewsItemCard(
                    item = item,
                    onAnalyzeWithAi = { onAnalyzeNewsWithAi(item) },
                    onOpenUrl = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SentimentStatChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier,
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                color = palette.textMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
            Text(
                text = value,
                color = color,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsItemCard(
    item: MarketNewsItem,
    onAnalyzeWithAi: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val sentColor = when (item.sentiment) {
        NewsSentimentType.BULLISH -> palette.longColor
        NewsSentimentType.BEARISH -> palette.shortColor
        NewsSentimentType.NEUTRAL -> palette.waitColor
    }
    val sentBg = when (item.sentiment) {
        NewsSentimentType.BULLISH -> palette.longDim
        NewsSentimentType.BEARISH -> palette.shortDim
        NewsSentimentType.NEUTRAL -> palette.waitDim
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("news_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, sentColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Text(
                            text = item.sourceName,
                            color = palette.cyan,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.publishedTime,
                        color = palette.textMuted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = sentBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, sentColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "${item.sentiment.badgeEmoji} ${item.sentiment.labelRu} ${String.format(Locale.US, "%+d", item.sentimentScore)}",
                        color = sentColor,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            if (item.summary.isNotBlank() && item.summary != item.title) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.summary,
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }

            if (item.relatedTickers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Затронутые активы (нажмите для открытия графика TradingView):",
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.relatedTickers.forEach { ticker ->
                        val tvUrl = QuantSignalEngine.tradingViewUrl(ticker)
                        Surface(
                            color = palette.cyanDim,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, palette.cyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clickable { onOpenUrl(tvUrl) }
                                .testTag("news_tv_${item.id}_$ticker")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = palette.cyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "MOEX:$ticker • TradingView ↗",
                                    color = palette.cyan,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAnalyzeWithAi,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.longDim,
                        contentColor = palette.longColor
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Разбор влияния",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onOpenUrl(item.url) },
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Источник",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsAndPcScreen(
    settings: AppSettings,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectStrategyProfile: (QuantStrategyProfile) -> Unit,
    onSelectCandleInterval: (Int) -> Unit,
    onChangeMinConfidence: (Int) -> Unit,
    onToggleNewsConfirmation: (Boolean) -> Unit,
    onToggleStrictVwapMacd: (Boolean) -> Unit,
    onSelectPollIntervalSec: (Int) -> Unit,
    onRecalculateBacktest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Theme & Visual Style Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SettingsBrightness,
                            contentDescription = null,
                            tint = palette.cyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ТЕМА ОФОРМЛЕНИЯ И СТИЛЬ",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Адаптивная палитра Alpine Daylight и Soft Obsidian",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            val icon = when (mode) {
                                AppThemeMode.LIGHT -> Icons.Default.LightMode
                                AppThemeMode.DARK -> Icons.Default.DarkMode
                                AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectTheme(mode) }
                                    .testTag("theme_mode_${mode.code.lowercase()}"),
                                color = if (selected) palette.longDim else palette.surfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) palette.longColor else palette.border
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) palette.longColor else palette.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mode.titleRu,
                                        color = if (selected) palette.longColor else palette.textPrimary,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Quant 2.0 Multi-Factor Strategy Profile Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.longColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = palette.longColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ДВИЖОК QUANT 2.0 (7 ФАКТОРОВ + НОВОСТИ)",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "EMA 20/50 • VWAP • MACD • RSI • Bollinger %B • ADX • Новости",
                                color = palette.longColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    QuantStrategyProfile.entries.forEach { profile ->
                        val selected = settings.strategyProfile == profile
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectStrategyProfile(profile) }
                                .testTag("strategy_profile_${profile.code.lowercase()}"),
                            color = if (selected) palette.longDim else palette.surfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) palette.longColor else palette.border
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (selected) palette.longColor else palette.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.titleRu,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = profile.descriptionRu,
                                        color = palette.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Timeframe selector (10m vs 60m)
                    Text(
                        text = "Таймфрейм свечей анализа MOEX ISS:",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            10 to "10 минут (Интрадей)",
                            60 to "1 час (Свинг — выше точность)"
                        ).forEach { (interval, title) ->
                            val selected = settings.candleIntervalMinutes == interval
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectCandleInterval(interval) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.cyanDim,
                                    selectedLabelColor = palette.cyan,
                                    containerColor = palette.surfaceElevated,
                                    labelColor = palette.textSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Minimum Confidence Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Мин. порог уверенности для входа:",
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${settings.minConfidenceThreshold}%",
                            color = palette.longColor,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    Slider(
                        value = settings.minConfidenceThreshold.toFloat(),
                        onValueChange = { onChangeMinConfidence(it.toInt()) },
                        valueRange = 60f..90f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.longColor,
                            activeTrackColor = palette.longColor,
                            inactiveTrackColor = palette.border
                        )
                    )

                    // News Sentiment Guard Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Новостной фильтр (News Sentiment Guard)",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Блокировать LONG при резком негативе в новостях и SHORT при позитиве",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.useNewsSentimentFilter,
                            onCheckedChange = onToggleNewsConfirmation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.bg,
                                checkedTrackColor = palette.longColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Strict VWAP + MACD Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Институциональный фильтр VWAP + MACD",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Требовать подтверждение цены относительно VWAP и импульса MACD",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.useStrictVwapMacdFilter,
                            onCheckedChange = onToggleStrictVwapMacd,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.bg,
                                checkedTrackColor = palette.longColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Частота опроса MOEX ISS API:",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { sec ->
                            val selected = settings.pollIntervalSeconds == sec
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectPollIntervalSec(sec) },
                                label = {
                                    Text(
                                        text = "$sec сек",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.cyanDim,
                                    selectedLabelColor = palette.cyan,
                                    containerColor = palette.surfaceElevated,
                                    labelColor = palette.textSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRecalculateBacktest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("recalculate_quant_history_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.longColor,
                            contentColor = palette.bg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Пересчитать историю по профилю Quant 2.0",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Standalone Windows PC (.exe) Version Info Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.5.dp, palette.cyan.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.cyanDim)
                                .border(1.dp, palette.cyan, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = palette.cyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ОТДЕЛЬНАЯ ВЕРСИЯ ДЛЯ ПК (WINDOWS .EXE)",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Десктопный терминал MOEX_Pulse_AI_Desktop.exe готов в проекте",
                                color = palette.cyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "1. Автосборка в GitHub Actions (.apk + .exe):",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "При отправке проекта в GitHub воркфлоу `.github/workflows/build_apk.yml` автоматически собирает сразу два готовых файла в разделе Actions → Artifacts:\n" +
                                    "• `MOEX-Pulse-AI-Android-APK` (.apk для телефона)\n" +
                                    "• `MOEX-Pulse-AI-Windows-Desktop-EXE` (`MOEX_Pulse_AI_Desktop.exe` для ПК)",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "2. Локальный запуск или сборка .exe на ПК:",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "В папке `desktop_pc_app/` находится полный код десктопной версии (`moex_pulse_pc.py`) и скрипт `build_exe.bat` (собирает автономный `.exe` в один клик через PyInstaller).",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val cmd = "cd desktop_pc_app && build_exe.bat"
                                clipboard.setPrimaryClip(ClipData.newPlainText("PC Build Command", cmd))
                                Toast.makeText(context, "Команда сборки .exe скопирована", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.cyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = palette.cyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Команда .exe",
                                color = palette.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://ru.tradingview.com/chart/?symbol=MOEX:IMOEX")
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.cyan,
                                contentColor = palette.bg
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Индекс IMOEX ↗",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
