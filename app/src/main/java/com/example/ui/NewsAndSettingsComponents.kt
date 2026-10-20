package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.MarketAssetClass
import com.example.data.QuantStrategyProfile
import com.example.data.Strategy
import com.example.quant.MarketNewsItem
import com.example.quant.NewsSentimentType
import com.example.quant.QuantSignalEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.SpaceGroteskFontFamily
import java.io.File
import java.io.FileInputStream
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
    onToggleVoiceAssistant: (Boolean) -> Unit = {},
    onToggleEcoBatteryMode: (Boolean) -> Unit = {},
    onToggleSilentMeetingMode: (Boolean) -> Unit = {},
    onToggleCompactCards: (Boolean) -> Unit = {},
    onTogglePrivacyHideBalance: (Boolean) -> Unit = {},
    onSelectMarketAssetClass: (MarketAssetClass) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val activeStrategy: Strategy = settings.activeStrategy
    var apkExportStatus by remember { mutableStateOf<String?>(null) }

    val saveApkDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        if (uri != null) {
            val resultMsg = exportInstalledApkToUri(context, uri)
            apkExportStatus = resultMsg
            Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Direct APK Download & GitHub Actions Build Status Card
        item(key = "apk_download_and_github_card") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.2.dp, Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apk_download_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📲 СКАЧАТЬ УСТАНОВОЧНЫЙ .APK ФАЙЛ",
                                color = Color.White,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Прямое сохранение готового подписанного APK и статус сборки GitHub Actions",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White
                        ) {
                            Text(
                                text = "JDK 21 • GRADLE 9.3.1",
                                color = Color.Black,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val msg = saveInstalledApkToDownloads(context)
                                apkExportStatus = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("direct_download_apk_btn")
                        ) {
                            Text(
                                text = "📲 СКАЧАТЬ APK В ЗАГРУЗКИ",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                saveApkDocumentLauncher.launch("Kvant_MOEX_Crypto_PRO.apk")
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.2.dp, Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("save_apk_as_btn")
                        ) {
                            Text(
                                text = "💾 СОХРАНИТЬ APK КАК...",
                                fontFamily = SpaceGroteskFontFamily,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    if (apkExportStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.bg,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = apkExportStatus!!,
                                color = Color.White,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bg,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "✅ GitHub Actions (.github/workflows/build_apk.yml) исправлен:",
                                color = Color.White,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "• Обновлены JDK 21 (Temurin), Gradle 9.3.1 и Android SDK 36.1 под AGP 9.1.1.\n• На GitHub: откройте вкладку Actions → Build Kvant MOEX & Crypto PRO → Artifacts → Kvant-MOEX-Crypto-PRO-Android-APK.\n• В AI Studio (браузер): также доступно через меню ⚙️ (Settings в правом верхнем углу) → Generate APK.",
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "РЕЖИМ РЫНКА И МОДЕЛЬ СТРАТЕГИИ",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Авто-калибровка индикаторов EMA, RSI, VWAP и ATR под акции РФ или крипторынок 24/7",
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
                        MarketAssetClass.entries.forEach { assetClass ->
                            val selected = settings.marketAssetClass == assetClass
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectMarketAssetClass(assetClass) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_market_mode_${assetClass.code}"),
                                label = {
                                    Text(
                                        text = if (assetClass == MarketAssetClass.CRYPTO) "₿ Криптовалюта 24/7" else "🏛 Акции РФ (MOEX)",
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White,
                                    selectedLabelColor = Color.Black,
                                    containerColor = palette.bg,
                                    labelColor = palette.textSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bg,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Активная модель Strategy: ${activeStrategy.nameRu}",
                                color = Color.White,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeStrategy.subtitleRu,
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "EMA ${activeStrategy.fastEmaPeriod}/${activeStrategy.slowEmaPeriod} • RSI(${activeStrategy.rsiPeriod}) [${activeStrategy.rsiOversold.toInt()}..${activeStrategy.rsiOverbought.toInt()}] • VWAP(${activeStrategy.vwapWindowBars}) • ATR(${activeStrategy.atrPeriod}) • TP1=${activeStrategy.tp1AtrMultiplier} ATR → Безубыток • SL=${activeStrategy.slAtrMultiplier} ATR",
                                color = Color.White,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Timeframe selector (10m vs 60m vs 1D)
                    Text(
                        text = "Таймфрейм свечей:",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            10 to "10М (Внутри дня)",
                            60 to "1Ч (Свинг)",
                            1440 to "1Д (Дневной)"
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
                                    selectedContainerColor = Color.White,
                                    selectedLabelColor = Color.Black,
                                    containerColor = palette.surfaceElevated,
                                    labelColor = palette.textSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Strategy Profile
                    Text(
                        text = "Режим фильтрации сигналов:",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
                                color = if (selected) Color.White else palette.border
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (selected) Color.White else palette.textMuted,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimum Confidence Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Минимальная уверенность сигнала:",
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${settings.minConfidenceThreshold}%",
                            color = Color.White,
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
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = palette.border
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Compact Cards Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Компактный вид списка",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Показывать больше тикеров на одном экране",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.compactCards,
                            onCheckedChange = onToggleCompactCards,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Privacy Balance Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Скрывать баланс на демо-счете",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Маскировать сумму капитала символами •••• ₽",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.privacyHideBalance,
                            onCheckedChange = onTogglePrivacyHideBalance,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Частота обновления котировок:",
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
                                shape = RoundedCornerShape(50),
                                label = {
                                    Text(
                                        text = "$sec сек",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White,
                                    selectedLabelColor = Color.Black,
                                    containerColor = palette.surfaceElevated,
                                    labelColor = palette.textSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

fun saveInstalledApkToDownloads(context: Context): String {
    return try {
        val sourceFile = File(context.applicationInfo.sourceDir)
        if (!sourceFile.exists()) {
            return "Ошибка: исходный пакет приложения не найден."
        }
        val sizeMb = String.format(Locale.US, "%.2f", sourceFile.length() / (1024.0 * 1024.0))
        val fileName = "Kvant_MOEX_Crypto_PRO_${System.currentTimeMillis() / 1000}.apk"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return "Не удалось создать файл в папке Загрузки."

            resolver.openOutputStream(uri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream, bufferSize = 64 * 1024)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            "✅ Готово! Файл $fileName ($sizeMb МБ) сохранен в папку «Загрузки» (Downloads)."
        } else {
            val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val destFile = File(outDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            "✅ Готово! APK ($sizeMb МБ) сохранен: ${destFile.absolutePath}"
        }
    } catch (e: Exception) {
        "Ошибка сохранения APK: ${e.localizedMessage ?: "неизвестная ошибка"}"
    }
}

fun exportInstalledApkToUri(context: Context, targetUri: Uri): String {
    return try {
        val sourceFile = File(context.applicationInfo.sourceDir)
        val sizeMb = String.format(Locale.US, "%.2f", sourceFile.length() / (1024.0 * 1024.0))
        context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
            FileInputStream(sourceFile).use { inStream ->
                inStream.copyTo(outStream, bufferSize = 64 * 1024)
            }
        }
        "✅ Установочный APK ($sizeMb МБ) успешно сохранен!"
    } catch (e: Exception) {
        "Ошибка записи APK: ${e.localizedMessage ?: "неизвестная ошибка"}"
    }
}

