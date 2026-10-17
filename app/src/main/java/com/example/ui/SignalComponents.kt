package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SignalHistoryEntity
import com.example.data.TradeOutcome
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

@Composable
fun TerminalHeaderBar(
    updatedTime: String,
    nextRefreshInSec: Int,
    isRefreshing: Boolean,
    isLiveConnection: Boolean,
    longCount: Int,
    shortCount: Int,
    waitCount: Int,
    historyWinRatePct: Int,
    resolvedTradesCount: Int,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleThemeClick: () -> Unit,
    userEmail: String? = null,
    cloudSignalsCount: Int = 0,
    onSignOutClick: () -> Unit = {}
) {
    val palette = LocalAppPalette.current

    Surface(
        color = palette.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isLiveConnection) palette.longColor else palette.waitColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "MOEX PULSE AI • QUANT 2.0",
                            color = palette.textPrimary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "7 факторов + Новости • Обн: $updatedTime (${nextRefreshInSec}с)",
                            color = palette.textSecondary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onToggleThemeClick,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceElevated)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .testTag("quick_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (palette.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Переключить светлую/темную тему",
                            tint = if (palette.isDark) palette.waitColor else palette.cyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    if (userEmail != null) {
                        UserCloudAccountChip(
                            userEmail = userEmail,
                            cloudSignalsCount = cloudSignalsCount,
                            onSignOutClick = onSignOutClick
                        )
                    } else {
                        Surface(
                            color = if (notificationsGranted) palette.longDim else palette.waitDim,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (notificationsGranted) palette.longColor.copy(alpha = 0.5f) else palette.waitColor.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .clickable { onRequestNotificationPermission() }
                                .testTag("notification_status_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Статус push-уведомлений",
                                    tint = if (notificationsGranted) palette.longColor else palette.waitColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (notificationsGranted) "PUSH" else "PUSH!",
                                    color = if (notificationsGranted) palette.longColor else palette.waitColor,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceElevated)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .testTag("refresh_market_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.longColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить котировки MOEX",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SummaryBadge(
                    label = "LONG",
                    countText = longCount.toString(),
                    accent = palette.longColor,
                    bg = palette.longDim,
                    modifier = Modifier.weight(1f)
                )
                SummaryBadge(
                    label = "SHORT",
                    countText = shortCount.toString(),
                    accent = palette.shortColor,
                    bg = palette.shortDim,
                    modifier = Modifier.weight(1f)
                )
                SummaryBadge(
                    label = "WAIT",
                    countText = waitCount.toString(),
                    accent = palette.waitColor,
                    bg = palette.waitDim,
                    modifier = Modifier.weight(1f)
                )
                SummaryBadge(
                    label = "WIN RATE",
                    countText = if (resolvedTradesCount > 0) "$historyWinRatePct%" else "--",
                    accent = palette.cyan,
                    bg = palette.cyanDim,
                    modifier = Modifier.weight(1.15f)
                )
            }
        }
    }
}

@Composable
private fun SummaryBadge(
    label: String,
    countText: String,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = accent,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                text = countText,
                color = accent,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun TerminalTabSwitcher(
    selectedTab: TerminalTab,
    totalHistoryCount: Int,
    openHistoryCount: Int,
    newsCount: Int,
    onSelectTab: (TerminalTab) -> Unit
) {
    val palette = LocalAppPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TerminalTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val badgeText = when (tab) {
                TerminalTab.LIVE_SIGNALS -> "Сигналы"
                TerminalTab.NEWS_FEED -> if (newsCount > 0) "Новости ($newsCount)" else "Новости"
                TerminalTab.HISTORY -> "История ($totalHistoryCount)"
                TerminalTab.AI_CHAT -> "AI Чат"
                TerminalTab.SETTINGS_PC -> "Настройки • ПК"
            }
            val icon = when (tab) {
                TerminalTab.LIVE_SIGNALS -> Icons.AutoMirrored.Filled.ShowChart
                TerminalTab.NEWS_FEED -> Icons.Default.Newspaper
                TerminalTab.HISTORY -> Icons.Default.History
                TerminalTab.AI_CHAT -> Icons.Default.AutoAwesome
                TerminalTab.SETTINGS_PC -> Icons.Default.Settings
            }

            Surface(
                modifier = Modifier
                    .clickable { onSelectTab(tab) }
                    .testTag("tab_${tab.name.lowercase()}"),
                color = if (selected) palette.longDim else palette.surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) palette.longColor else palette.border
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) palette.longColor else palette.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = badgeText,
                        color = if (selected) palette.longColor else palette.textSecondary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FilterBar(
    selectedFilter: SignalFilter,
    onSelectFilter: (SignalFilter) -> Unit
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SignalFilter.entries.forEach { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onSelectFilter(filter) },
                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                label = {
                    Text(
                        text = filter.title,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.longDim,
                    selectedLabelColor = palette.longColor,
                    containerColor = palette.surface,
                    labelColor = palette.textSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = palette.border,
                    selectedBorderColor = palette.longColor
                )
            )
        }
    }
}

@Composable
fun SignalCard(
    signal: QuantSignal,
    tickerHistory: List<SignalHistoryEntity>,
    onClick: () -> Unit,
    onOpenTickerHistory: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    var expandedHistory by remember { mutableStateOf(false) }
    var expandedFactors by remember { mutableStateOf(false) }

    val accentColor = when (signal.direction) {
        SignalDirection.LONG -> palette.longColor
        SignalDirection.SHORT -> palette.shortColor
        SignalDirection.WAIT -> palette.waitColor
    }
    val badgeBg = when (signal.direction) {
        SignalDirection.LONG -> palette.longDim
        SignalDirection.SHORT -> palette.shortDim
        SignalDirection.WAIT -> palette.waitDim
    }

    val changeColor = when {
        signal.changePercent > 0.0 -> palette.longColor
        signal.changePercent < 0.0 -> palette.shortColor
        else -> palette.textSecondary
    }

    val resolvedForTicker = tickerHistory.filter {
        it.status == TradeOutcome.HIT_TP.code || it.status == TradeOutcome.HIT_SL.code
    }
    val tpHits = resolvedForTicker.count { it.status == TradeOutcome.HIT_TP.code }
    val turnoverBillionsStr = String.format(Locale.US, "%.1f млрд ₽", signal.valTodayRub / 1_000_000_000.0)
    val sparklinePrices = remember(signal.recentCandles, signal.currentPrice) {
        val list = signal.recentCandles.takeLast(32).map { it.close }
        if (list.size >= 2) list else listOf(signal.currentPrice * 0.99, signal.currentPrice)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("signal_card_${signal.ticker}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(
            width = if (signal.direction == SignalDirection.WAIT) 1.dp else 1.5.dp,
            color = if (signal.direction == SignalDirection.WAIT) palette.border else accentColor.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Ticker + Name + Price + Signal Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeBg)
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = signal.ticker.take(4),
                            color = accentColor,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = signal.ticker,
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = palette.surfaceElevated,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${signal.confluencePassedCount}/7 факторов",
                                    color = if (signal.confluencePassedCount >= 5) palette.longColor else palette.cyan,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${signal.name} • Объем $turnoverBillionsStr",
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${QuantSignalEngine.formatPrice(signal.currentPrice, signal.minStep)} ₽",
                        color = palette.textPrimary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = QuantSignalEngine.formatSignedPercent(signal.changePercent),
                        color = changeColor,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (signal.direction) {
                                SignalDirection.LONG -> Icons.AutoMirrored.Filled.TrendingUp
                                SignalDirection.SHORT -> Icons.AutoMirrored.Filled.TrendingDown
                                SignalDirection.WAIT -> Icons.AutoMirrored.Filled.TrendingFlat
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = signal.direction.label,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = signal.direction.label,
                                color = accentColor,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "Conf ${signal.confidence}%",
                            color = accentColor.copy(alpha = 0.9f),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Entry / Take Profit / Stop Loss Box
            if (signal.direction == SignalDirection.LONG || signal.direction == SignalDirection.SHORT) {
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LevelMetricColumn(
                            title = "ТОЧКА ВХОДА (ENTRY)",
                            price = "${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)} ₽",
                            sub = "Текущая котировка",
                            valueColor = palette.textPrimary
                        )
                        LevelMetricColumn(
                            title = "ТЕЙК-ПРОФИТ (TP)",
                            price = "${QuantSignalEngine.formatPrice(signal.takeProfit, signal.minStep)} ₽",
                            sub = QuantSignalEngine.formatSignedPercent(signal.tpPercent),
                            valueColor = palette.longColor
                        )
                        LevelMetricColumn(
                            title = "СТОП-ЛОСС (SL)",
                            price = "${QuantSignalEngine.formatPrice(signal.stopLoss, signal.minStep)} ₽",
                            sub = "-${QuantSignalEngine.formatOneDec(abs(signal.slPercent))}%",
                            valueColor = palette.shortColor
                        )
                    }
                }
            } else {
                Surface(
                    color = palette.waitDim.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.waitColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏳ ВНЕ РЫНКА (ЗАЩИТА КАПИТАЛА QUANT 2.0)",
                                color = palette.waitColor,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${signal.nearestLevelNote} • Поддержка ${QuantSignalEngine.formatPrice(signal.supportLevel, signal.minStep)} ₽ / Сопр. ${QuantSignalEngine.formatPrice(signal.resistanceLevel, signal.minStep)} ₽",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "Ждем импульс",
                            color = palette.waitColor,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sparkline + Quant 2.0 Technical Indicators Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SparklineCanvas(
                    prices = sparklinePrices,
                    ema20 = signal.ema20,
                    ema50 = signal.ema50,
                    lineColor = accentColor,
                    modifier = Modifier
                        .width(105.dp)
                        .height(36.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    TechChip(
                        label = "VWAP",
                        value = QuantSignalEngine.formatPrice(signal.vwap, signal.minStep)
                    )
                    TechChip(
                        label = "RSI",
                        value = QuantSignalEngine.formatOneDec(signal.rsi14),
                        highlight = when {
                            signal.rsi14 > 70 -> palette.shortColor
                            signal.rsi14 < 30 -> palette.longColor
                            else -> palette.textPrimary
                        }
                    )
                    TechChip(
                        label = "ADX",
                        value = QuantSignalEngine.formatOneDec(signal.adx14),
                        highlight = if (signal.adx14 >= 20) palette.longColor else palette.textSecondary
                    )
                    TechChip(
                        label = "BB%B",
                        value = "${(signal.bollingerPercentB * 100).toInt()}%"
                    )
                    TechChip(
                        label = "Новости",
                        value = String.format(Locale.US, "%+d", signal.newsSentimentScore),
                        highlight = when {
                            signal.newsSentimentScore >= 14 -> palette.longColor
                            signal.newsSentimentScore <= -14 -> palette.shortColor
                            else -> palette.textSecondary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = signal.reason,
                color = palette.textSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar: Immediate TradingView Button + 7-Factor Checklist + History + Risk Calc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // DIRECT TRADINGVIEW LINK BUTTON ON EVERY STOCK CARD
                    Surface(
                        color = palette.cyanDim,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, palette.cyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(signal.tradingViewUrl))
                                    )
                                } catch (_: Exception) {
                                }
                            }
                            .testTag("tradingview_btn_${signal.ticker}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Открыть график в TradingView",
                                tint = palette.cyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TradingView ↗",
                                color = palette.cyan,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Expand 7-factor institutional checklist
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .clickable { expandedFactors = !expandedFactors }
                            .testTag("toggle_factors_${signal.ticker}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7 факторов (${signal.confluencePassedCount}/7)",
                                color = palette.textPrimary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Icon(
                                imageVector = if (expandedFactors) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Expand History
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .clickable { expandedHistory = !expandedHistory }
                            .testTag("expand_history_${signal.ticker}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (resolvedForTicker.isNotEmpty()) {
                                    "Ист (${tpHits}/${resolvedForTicker.size})"
                                } else {
                                    "Ист (${tickerHistory.size})"
                                },
                                color = palette.textSecondary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp
                            )
                            Icon(
                                imageVector = if (expandedHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onClick)
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = palette.longColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Риск",
                        color = palette.longColor,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Expandable 7-Factor Confluence Checklist
            AnimatedVisibility(visible = expandedFactors) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ЧЕК-ЛИСТ ИНСТИТУЦИОНАЛЬНОГО КОНФЛЮЭНСА QUANT 2.0:",
                        color = palette.textPrimary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    signal.confluenceFactors.forEach { factor ->
                        val fColor = if (factor.passed) palette.longColor else palette.textMuted
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (factor.passed) "✔" else "•",
                                    color = fColor,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = factor.nameRu,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = factor.valueText,
                                color = fColor,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Expandable per-stock signal history log
            AnimatedVisibility(visible = expandedHistory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Последние сигналы по ${signal.ticker}",
                            color = palette.textPrimary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Весь журнал →",
                            color = palette.cyan,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onOpenTickerHistory(signal.ticker) }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (tickerHistory.isEmpty()) {
                        Text(
                            text = "По бумаге ${signal.ticker} еще не было зафиксировано сигналов.",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    } else {
                        tickerHistory.take(4).forEach { record ->
                            CompactTickerHistoryRow(record = record, minStep = signal.minStep)
                            Spacer(modifier = Modifier.height(5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTickerHistoryRow(record: SignalHistoryEntity, minStep: Double = 0.01) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val outcome = TradeOutcome.fromCode(record.status)
    val outcomeColor = when (outcome) {
        TradeOutcome.HIT_TP -> palette.longColor
        TradeOutcome.HIT_SL -> palette.shortColor
        TradeOutcome.ACTIVE -> palette.waitColor
    }
    val dirColor = if (record.signalType == "LONG") palette.longColor else palette.shortColor
    val timeFormatted = remember(record.timestamp) {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val tvUrl = QuantSignalEngine.tradingViewUrl(record.ticker)

    Surface(
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.signalType,
                    color = dirColor,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeFormatted,
                    color = palette.textSecondary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Вход ${QuantSignalEngine.formatPrice(record.entryPrice, minStep)}",
                    color = palette.textPrimary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TV ↗",
                    color = palette.cyan,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tvUrl)))
                            } catch (_: Exception) {
                            }
                        }
                        .padding(end = 6.dp)
                )
                Text(
                    text = when (outcome) {
                        TradeOutcome.HIT_TP -> "✓ TP (${QuantSignalEngine.formatSignedPercent(record.pnlPercent)})"
                        TradeOutcome.HIT_SL -> "✗ SL (${QuantSignalEngine.formatSignedPercent(-abs(record.pnlPercent))})"
                        TradeOutcome.ACTIVE -> "⏳ В работе"
                    },
                    color = outcomeColor,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun SignalHistoryScreen(
    historyList: List<SignalHistoryEntity>,
    filterTicker: String?,
    onClearFilterTicker: () -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val filtered = if (filterTicker != null) {
        historyList.filter { it.ticker == filterTicker }
    } else {
        historyList
    }

    val resolved = filtered.filter {
        it.status == TradeOutcome.HIT_TP.code || it.status == TradeOutcome.HIT_SL.code
    }
    val tpCount = resolved.count { it.status == TradeOutcome.HIT_TP.code }
    val slCount = resolved.count { it.status == TradeOutcome.HIT_SL.code }
    val openCount = filtered.count { it.status == TradeOutcome.ACTIVE.code }
    val winRate = if (resolved.isNotEmpty()) ((tpCount.toDouble() / resolved.size) * 100).toInt() else 0
    val netPct = resolved.sumOf { record ->
        if (record.status == TradeOutcome.HIT_TP.code) abs(record.pnlPercent) else -abs(record.pnlPercent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
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
                    Column {
                        Text(
                            text = if (filterTicker != null) {
                                "ИСТОРИЯ СИГНАЛОВ • $filterTicker"
                            } else {
                                "ЖУРНАЛ И СТАТИСТИКА QUANT 2.0"
                            },
                            color = palette.textPrimary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Автоматическая проверка достижения Take Profit и Stop Loss",
                            color = palette.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (filterTicker != null) {
                            Surface(
                                color = palette.cyanDim,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, palette.cyan),
                                modifier = Modifier.clickable { onClearFilterTicker() }
                            ) {
                                Text(
                                    text = "Все акции ✕",
                                    color = palette.cyan,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (historyList.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAllHistory,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("clear_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Очистить историю",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryStatBox(
                        label = "WIN RATE",
                        value = if (resolved.isNotEmpty()) "$winRate%" else "--",
                        sub = "${tpCount}W / ${slCount}L",
                        color = palette.cyan,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryStatBox(
                        label = "УСПЕШНО (TP)",
                        value = tpCount.toString(),
                        sub = "Тейк-профит",
                        color = palette.longColor,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryStatBox(
                        label = "СТОП (SL)",
                        value = slCount.toString(),
                        sub = "Стоп-лосс",
                        color = palette.shortColor,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryStatBox(
                        label = "ИТОГ %",
                        value = QuantSignalEngine.formatSignedPercent(netPct),
                        sub = "В работе: $openCount",
                        color = if (netPct >= 0.0) palette.longColor else palette.shortColor,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "История сигналов пока пуста. Новые сигналы LONG / SHORT записываются автоматически.",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_list"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    HistoryItemCard(record = item)
                }
            }
        }
    }
}

@Composable
private fun HistoryStatBox(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier,
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label,
                color = palette.textMuted,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp
            )
            Text(
                text = value,
                color = color,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
            Text(
                text = sub,
                color = palette.textSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HistoryItemCard(record: SignalHistoryEntity) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val outcome = TradeOutcome.fromCode(record.status)
    val isLong = record.signalType == "LONG"
    val dirColor = if (isLong) palette.longColor else palette.shortColor
    val dirBg = if (isLong) palette.longDim else palette.shortDim

    val outcomeColor = when (outcome) {
        TradeOutcome.HIT_TP -> palette.longColor
        TradeOutcome.HIT_SL -> palette.shortColor
        TradeOutcome.ACTIVE -> palette.waitColor
    }
    val outcomeBg = when (outcome) {
        TradeOutcome.HIT_TP -> palette.longDim
        TradeOutcome.HIT_SL -> palette.shortDim
        TradeOutcome.ACTIVE -> palette.waitDim
    }
    val outcomeIcon = when (outcome) {
        TradeOutcome.HIT_TP -> Icons.Default.CheckCircle
        TradeOutcome.HIT_SL -> Icons.Default.Close
        TradeOutcome.ACTIVE -> Icons.Default.Schedule
    }

    val createdDateStr = remember(record.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }
    val resolvedDateStr = remember(record.resolvedAt) {
        record.resolvedAt?.let {
            SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it))
        }
    }
    val tvUrl = QuantSignalEngine.tradingViewUrl(record.ticker)
    val minStep = QuantSignalEngine.TOP_MOEX_STOCKS.find { it.ticker == record.ticker }?.defaultStep ?: 0.01

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${record.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, outcomeColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = dirBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, dirColor)
                    ) {
                        Text(
                            text = if (isLong) "🟢 LONG" else "🔴 SHORT",
                            color = dirColor,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${record.ticker} • ${record.stockName}",
                            color = palette.textPrimary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Сигнал: $createdDateStr",
                            color = palette.textSecondary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    color = outcomeBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, outcomeColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = outcomeIcon,
                            contentDescription = outcome.labelRu,
                            tint = outcomeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (outcome) {
                                TradeOutcome.HIT_TP -> "УСПЕХ (TP) ${QuantSignalEngine.formatSignedPercent(record.pnlPercent)}"
                                TradeOutcome.HIT_SL -> "СТОП (SL) ${QuantSignalEngine.formatSignedPercent(-abs(record.pnlPercent))}"
                                TradeOutcome.ACTIVE -> "В РАБОТЕ"
                            },
                            color = outcomeColor,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = palette.surfaceElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LevelMetricColumn(
                        title = "ВХОД (ENTRY)",
                        price = "${QuantSignalEngine.formatPrice(record.entryPrice, minStep)} ₽",
                        sub = "Conf ${record.confidence}%",
                        valueColor = palette.textPrimary
                    )
                    LevelMetricColumn(
                        title = "ТЕЙК (TP)",
                        price = "${QuantSignalEngine.formatPrice(record.takeProfit, minStep)} ₽",
                        sub = if (outcome == TradeOutcome.HIT_TP && resolvedDateStr != null) "Взят $resolvedDateStr" else "Цель Quant 2.0",
                        valueColor = palette.longColor
                    )
                    LevelMetricColumn(
                        title = "СТОП (SL)",
                        price = "${QuantSignalEngine.formatPrice(record.stopLoss, minStep)} ₽",
                        sub = if (outcome == TradeOutcome.HIT_SL && resolvedDateStr != null) "Выбит $resolvedDateStr" else "Защита ATR",
                        valueColor = palette.shortColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.reason,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = palette.cyanDim,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.cyan.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tvUrl)))
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Text(
                        text = "TradingView ↗",
                        color = palette.cyan,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelMetricColumn(
    title: String,
    price: String,
    sub: String,
    valueColor: Color
) {
    val palette = LocalAppPalette.current
    Column {
        Text(
            text = title,
            color = palette.textMuted,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = price,
            color = valueColor,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
        Text(
            text = sub,
            color = valueColor.copy(alpha = 0.85f),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TechChip(
    label: String,
    value: String,
    highlight: Color? = null
) {
    val palette = LocalAppPalette.current
    val vColor = highlight ?: palette.textPrimary
    Surface(
        color = palette.bg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label:",
                color = palette.textMuted,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = value,
                color = vColor,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SparklineCanvas(
    prices: List<Double>,
    ema20: Double,
    ema50: Double,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val minVal = prices.minOrNull() ?: 0.0
        val maxVal = prices.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).takeIf { it > 1e-8 } ?: 1.0

        val path = Path()
        val fillPath = Path()

        prices.forEachIndexed { index, price ->
            val x = (index.toFloat() / (prices.size - 1).toFloat()) * size.width
            val y = size.height - (((price - minVal) / range).toFloat() * (size.height * 0.82f) + size.height * 0.09f)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == prices.lastIndex) {
                fillPath.lineTo(x, size.height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.26f), Color.Transparent)
            )
        )

        if (ema50 in minVal..maxVal) {
            val yEma50 = size.height - (((ema50 - minVal) / range).toFloat() * (size.height * 0.82f) + size.height * 0.09f)
            drawLine(
                color = palette.cyan.copy(alpha = 0.45f),
                start = Offset(0f, yEma50),
                end = Offset(size.width, yEma50),
                strokeWidth = 1.dp.toPx()
            )
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionCalculatorSheet(
    signal: QuantSignal,
    depositText: String,
    riskPercentText: String,
    onDepositChange: (String) -> Unit,
    onRiskPercentChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val depositRub = depositText.toDoubleOrNull() ?: 150000.0
    val riskPct = riskPercentText.toDoubleOrNull() ?: 1.0
    val maxRiskRub = depositRub * (riskPct / 100.0)

    val stopDistancePerShare = abs(signal.entryPrice - signal.stopLoss)
    val riskPerLotRub = stopDistancePerShare * signal.lotSize
    val recommendedLots = if (riskPerLotRub > 0.0001) {
        max(0, floor(maxRiskRub / riskPerLotRub).toInt())
    } else {
        0
    }
    val totalShares = recommendedLots * signal.lotSize
    val totalPositionValueRub = totalShares * signal.entryPrice
    val actualRiskRub = recommendedLots * riskPerLotRub
    val expectedProfitRub = recommendedLots * abs(signal.takeProfit - signal.entryPrice) * signal.lotSize

    val accentColor = when (signal.direction) {
        SignalDirection.LONG -> palette.longColor
        SignalDirection.SHORT -> palette.shortColor
        SignalDirection.WAIT -> palette.waitColor
    }
    val overviewUrl = QuantSignalEngine.tradingViewSymbolOverviewUrl(signal.ticker)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .testTag("risk_calculator_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${signal.ticker} • ${signal.name}",
                        color = palette.textPrimary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Лот: ${signal.lotSize} шт. • Шаг цены: ${signal.minStep} ₽ • ${signal.direction.badgeText}",
                        color = accentColor,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть калькулятор",
                        tint = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct TradingView Buttons inside Calculator Sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signal.tradingViewUrl)))
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("sheet_tradingview_chart_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.cyan,
                        contentColor = palette.bg
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "График TradingView ↗",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(overviewUrl)))
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("sheet_tradingview_overview_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.cyan)
                ) {
                    Text(
                        text = "Обзор TV ↗",
                        color = palette.cyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
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
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LevelMetricColumn(
                            title = "ВХОД (ENTRY)",
                            price = "${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)} ₽",
                            sub = "1 лот = ${QuantSignalEngine.formatPrice(signal.entryPrice * signal.lotSize, 0.01)} ₽",
                            valueColor = palette.textPrimary
                        )
                        LevelMetricColumn(
                            title = "ТЕЙК (TP)",
                            price = "${QuantSignalEngine.formatPrice(signal.takeProfit, signal.minStep)} ₽",
                            sub = QuantSignalEngine.formatSignedPercent(signal.tpPercent),
                            valueColor = palette.longColor
                        )
                        LevelMetricColumn(
                            title = "СТОП (SL)",
                            price = "${QuantSignalEngine.formatPrice(signal.stopLoss, signal.minStep)} ₽",
                            sub = "-${QuantSignalEngine.formatOneDec(abs(signal.slPercent))}%",
                            valueColor = palette.shortColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "КАЛЬКУЛЯТОР РИСК-МЕНЕДЖМЕНТА",
                color = palette.cyan,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = depositText,
                    onValueChange = onDepositChange,
                    label = { Text("Депозит (₽)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("deposit_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.longColor,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                OutlinedTextField(
                    value = riskPercentText,
                    onValueChange = onRiskPercentChange,
                    label = { Text("Риск (%)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("risk_percent_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.longColor,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("0.5", "1.0", "1.5", "2.0").forEach { preset ->
                    FilterChip(
                        selected = riskPercentText == preset,
                        onClick = { onRiskPercentChange(preset) },
                        label = {
                            Text(
                                text = "Риск $preset%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.longDim,
                            selectedLabelColor = palette.longColor,
                            containerColor = palette.surfaceElevated,
                            labelColor = palette.textSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = palette.border)
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = palette.bg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcRow(
                        label = "Рекомендуемый объем:",
                        value = "$recommendedLots лотов ($totalShares акций)",
                        valueColor = palette.textPrimary,
                        bold = true
                    )
                    CalcRow(
                        label = "Сумма входа в позицию:",
                        value = "${String.format(Locale.US, "%,.0f", totalPositionValueRub)} ₽",
                        valueColor = palette.textSecondary
                    )
                    CalcRow(
                        label = "Риск при срабатывании SL:",
                        value = "-${String.format(Locale.US, "%,.0f", actualRiskRub)} ₽ (лимит ${String.format(Locale.US, "%,.0f", maxRiskRub)} ₽)",
                        valueColor = palette.shortColor
                    )
                    CalcRow(
                        label = "Ожидаемый профит на TP:",
                        value = "+${String.format(Locale.US, "%,.0f", expectedProfitRub)} ₽",
                        valueColor = palette.longColor,
                        bold = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun CalcRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
