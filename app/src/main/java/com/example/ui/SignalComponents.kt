package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketAssetClass
import com.example.data.PaperTradingRepository
import com.example.data.PerformanceSummary
import com.example.data.SignalHistoryEntity
import com.example.data.SignalSortOrder
import com.example.data.TradeOutcome
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.MarketRegimeType
import com.example.quant.MarketSessionStatus
import com.example.quant.MoexCandle
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.service.TraderVoiceAndShareHelper
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.widget.MoexPulseWidgetProvider
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Helper for corporate event / dividend calendar per ticker (Clean Institutional Format)
 */
fun getCorporateEventNoteRu(ticker: String): String = when (ticker.uppercase(Locale.US)) {
    "SBER" -> "Дивидендный профиль Сбер: ожид. ~35–38 ₽ (июль) • Отчетность МСФО ежемесячно"
    "LKOH" -> "Дивидендный профиль ЛУКОЙЛ: финальные + промежуточные выплаты (~1000+ ₽/год)"
    "GAZP" -> "Фундаментальный фокус: отчет МСФО, FCF и инвестпрограмма Газпрома"
    "YDEX" -> "Фундаментальный фокус Яндекс: див. политика + темпы роста выручки ИИ и Финтеха"
    "T" -> "Фундаментальный фокус Т-Технологии: ежеквартальные дивиденды + ROE > 30%"
    "ROSN" -> "Дивидендный профиль Роснефть: полугодовые выплаты (50% чистой прибыли МСФО)"
    "NVTK" -> "Дивидендный профиль НОВАТЭК: полугодовые выплаты + динамика отгрузок СПГ"
    "SNGS" -> "Фундаментальный фокус Сургутнефтегаз: валютная ликвидность и годовая отсечка"
    "TATN" -> "Дивидендный профиль Татнефть: выплаты 3 раза в год • высокая норма распределения"
    "GMKN" -> "Фундаментальный фокус Норникель: корзина металлов (Pd, Ni, Cu) и свободный поток FCF"
    "CHMF" -> "Дивидендный профиль Северсталь: квартальные выплаты при Net Debt/EBITDA < 0.5x"
    "PLZL" -> "Фундаментальный фокус Полюс: корреляция с золотом (XAU) и высокая маржинальность"
    "NLMK" -> "Фундаментальный фокус НЛМК: чистая денежная позиция и потенциал дивидендов"
    "ALRS" -> "Фундаментальный фокус АЛРОСА: полугодовая база распределения FCF"
    "MOEX" -> "Фундаментальный фокус МосБиржа: бенефициар высокой ключевой ставки ЦБ РФ"
    "MTSS" -> "Дивидендный профиль МТС: фиксированная летняя отсечка (~35 ₽ на акцию)"
    "VTBR" -> "Фундаментальный фокус ВТБ: динамика чистой прибыли МСФО и достаточность капитала"
    "CBOM" -> "Фундаментальный фокус МКБ: корпоративный кредитный портфель и маржа NIM"
    "POSI" -> "Фундаментальный фокус Позитив: сезонность отгрузок кибербезопасности в IV квартале"
    "VKCO" -> "Фундаментальный фокус ВК: рентабельность EBITDA и динамика долговой нагрузки"
    else -> "Режим торгов Т+1 (TQBR Московская Биржа)"
}

/**
 * Samsung S25 Edge-to-Edge Safe Institutional Header:
 * Respects `WindowInsets.statusBars` so the camera punch-hole never overlaps UI elements,
 * and uses adaptive horizontal scrolling / FlowRow so 360dp screens never squish or clip text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalHeader(
    lastPollTime: String,
    isMarketLive: Boolean,
    imoexSnapshot: ImoexIndexSnapshot,
    sessionStatus: MarketSessionStatus = QuantSignalEngine.getMoscowSessionStatus(10),
    marketAssetClass: MarketAssetClass = MarketAssetClass.MOEX,
    onSelectMarketAssetClass: (MarketAssetClass) -> Unit = {},
    activeCount: Int,
    longCount: Int,
    shortCount: Int,
    waitCount: Int,
    winRatePercent: Double,
    totalHistoricalSignals: Int,
    isBackgroundMonitoring: Boolean,
    voiceEnabled: Boolean,
    compactCards: Boolean = false,
    ecoBatteryMode: Boolean = false,
    silentMeetingMode: Boolean = false,
    onToggleBackgroundMonitoring: (Boolean) -> Unit,
    onToggleThemeQuick: () -> Unit,
    onToggleVoiceQuick: () -> Unit,
    onToggleCompactQuick: () -> Unit = {},
    onToggleEcoModeQuick: () -> Unit = {},
    onToggleSilentMeetingQuick: () -> Unit = {},
    onRefreshClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    val isCrypto = marketAssetClass == MarketAssetClass.CRYPTO

    Surface(
        color = palette.bgElevated,
        tonalElevation = 0.dp,
        border = BorderStroke(0.8.dp, palette.borderCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMarketLive) Color.White else palette.textMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCrypto) "KVANT CRYPTO" else "KVANT",
                                fontFamily = SpaceGroteskFontFamily,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCrypto) {
                                    "BTC $${String.format(Locale.US, "%.0f", imoexSnapshot.currentValue)} (${QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)}) • Dom ${String.format(Locale.US, "%.1f", imoexSnapshot.btcDominancePct)}%"
                                } else {
                                    "IMOEX ${String.format(Locale.US, "%.0f", imoexSnapshot.currentValue)} (${QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)})"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                color = if (imoexSnapshot.changePercent >= 0) Color.White else palette.textSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (isCrypto) {
                                "${sessionStatus.sessionBadgeRu} • F&G ${imoexSnapshot.fearGreedIndex}/100 • $lastPollTime"
                            } else {
                                "${sessionStatus.sessionBadgeRu} • Обновлено $lastPollTime"
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            color = palette.textSecondary,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeaderBadge(
                        text = "▲ $longCount",
                        textColor = Color.White,
                        bgColor = palette.bgCard
                    )
                    HeaderBadge(
                        text = "▼ $shortCount",
                        textColor = palette.textSecondary,
                        bgColor = palette.bgCard
                    )
                    RoundedHeaderIconBtn(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Обновить котировки",
                        tint = Color.White,
                        testTag = "refresh_signals_button",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRefreshClick()
                        }
                    )
                }
            }

            // Prominent 1-Tap Market Mode Switcher: Акции MOEX <-> Криптовалюта 24/7
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("market_mode_switcher_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MarketAssetClass.entries.forEach { mode ->
                        val selected = marketAssetClass == mode
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) Color.White else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectMarketAssetClass(mode)
                                }
                                .testTag("market_mode_${mode.code.lowercase(Locale.US)}")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (mode == MarketAssetClass.MOEX) {
                                        "Фондовый рынок РФ (MOEX)"
                                    } else {
                                        "Криптовалюта 24/7 (USDT)"
                                    },
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = if (selected) Color.Black else palette.textSecondary,
                                    fontWeight = FontWeight.ExtraBold,
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
}

@Composable
private fun RoundedHeaderIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = CircleShape,
        color = palette.bgCard,
        border = BorderStroke(0.8.dp, palette.borderCard),
        modifier = Modifier
            .size(30.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun HeaderBadge(
    text: String,
    textColor: Color,
    bgColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(0.7.dp, textColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            fontFamily = SpaceGroteskFontFamily,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Smart Instant Search, Watchlist Filter, 1-Tap Multi-Sort & Timeframe Bar (Samsung S25 Optimized)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UltimateSearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: SignalDirection?,
    onSelectFilter: (SignalDirection?) -> Unit,
    onlyWatchlist: Boolean,
    watchlistCount: Int,
    onToggleWatchlistOnly: () -> Unit,
    sortOrder: SignalSortOrder,
    onSelectSortOrder: (SignalSortOrder) -> Unit,
    candleIntervalMinutes: Int,
    onSelectCandleInterval: (Int) -> Unit,
    totalCount: Int,
    longCount: Int,
    shortCount: Int,
    waitCount: Int,
    isCryptoMode: Boolean = false
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Instant Search Pill + Rounded Timeframe Switcher (10M / 1H / 1D)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = if (isCryptoMode) "Поиск: BTC, ETH, SOL, TON, PEPE..." else "Поиск: SBER, Сбер, Яндекс...",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted,
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск тикера",
                        tint = palette.cyanAccent,
                        modifier = Modifier.size(17.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить поиск",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.cyanAccent,
                    unfocusedBorderColor = palette.borderCard,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    focusedContainerColor = palette.bgCard,
                    unfocusedContainerColor = palette.bgCard
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("ticker_search_input")
            )

            // Quick Timeframe Pill Switcher (10M / 1H / 1D)
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderCard)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf(10 to "10M", 60 to "1H", 1440 to "1D").forEach { (mins, label) ->
                        val active = candleIntervalMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) palette.cyanAccent else Color.Transparent,
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectCandleInterval(mins)
                                }
                                .testTag("tf_switch_$label")
                        ) {
                            Text(
                                text = label,
                                fontFamily = SpaceGroteskFontFamily,
                                color = if (active) Color(0xFF04121C) else palette.textSecondary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Row 2: Rounded Direction Filter Pills + Watchlist Pill
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RoundedDirectionChip(
                label = "Все ($totalCount)",
                selected = selectedFilter == null && !onlyWatchlist,
                accentColor = palette.cyanAccent,
                bgSelected = palette.cyanDim,
                testTag = "filter_all_chip",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onlyWatchlist) onToggleWatchlistOnly()
                    onSelectFilter(null)
                }
            )
            RoundedDirectionChip(
                label = "▲ LONG ($longCount)",
                selected = selectedFilter == SignalDirection.LONG,
                accentColor = palette.longGreen,
                bgSelected = palette.longGreenDim,
                testTag = "filter_long_chip",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectFilter(if (selectedFilter == SignalDirection.LONG) null else SignalDirection.LONG)
                }
            )
            RoundedDirectionChip(
                label = "▼ SHORT ($shortCount)",
                selected = selectedFilter == SignalDirection.SHORT,
                accentColor = palette.shortRed,
                bgSelected = palette.shortRedDim,
                testTag = "filter_short_chip",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectFilter(if (selectedFilter == SignalDirection.SHORT) null else SignalDirection.SHORT)
                }
            )
            RoundedDirectionChip(
                label = "★ Избранное ($watchlistCount)",
                selected = onlyWatchlist,
                accentColor = palette.amberWait,
                bgSelected = palette.amberWaitDim,
                testTag = "filter_watchlist_chip",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleWatchlistOnly()
                }
            )
        }
    }
}

@Composable
private fun RoundedDirectionChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    bgSelected: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) bgSelected else palette.bgCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor else palette.borderCard
        ),
        modifier = Modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            color = if (selected) accentColor else palette.textSecondary,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun FilterBar(
    selectedFilter: SignalDirection?,
    totalCount: Int,
    longCount: Int,
    shortCount: Int,
    waitCount: Int,
    onSelectFilter: (SignalDirection?) -> Unit
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundedDirectionChip(
            label = "Все ($totalCount)",
            selected = selectedFilter == null,
            accentColor = palette.cyanAccent,
            bgSelected = palette.cyanDim,
            testTag = "filter_all_chip",
            onClick = { onSelectFilter(null) }
        )
        RoundedDirectionChip(
            label = "▲ LONG ($longCount)",
            selected = selectedFilter == SignalDirection.LONG,
            accentColor = palette.longGreen,
            bgSelected = palette.longGreenDim,
            testTag = "filter_long_chip",
            onClick = { onSelectFilter(SignalDirection.LONG) }
        )
        RoundedDirectionChip(
            label = "▼ SHORT ($shortCount)",
            selected = selectedFilter == SignalDirection.SHORT,
            accentColor = palette.shortRed,
            bgSelected = palette.shortRedDim,
            testTag = "filter_short_chip",
            onClick = { onSelectFilter(SignalDirection.SHORT) }
        )
        RoundedDirectionChip(
            label = "• WAIT ($waitCount)",
            selected = selectedFilter == SignalDirection.WAIT,
            accentColor = palette.amberWait,
            bgSelected = palette.amberWaitDim,
            testTag = "filter_wait_chip",
            onClick = { onSelectFilter(SignalDirection.WAIT) }
        )
    }
}

/**
 * Multi-factor market sentiment model for the Fear & Greed Index component.
 */
@Immutable
data class FearAndGreedMetrics(
    val overallScore: Int,
    val labelRu: String,
    val labelEn: String,
    val descriptionRu: String,
    val imoexMomentumScore: Int,
    val marketBreadthScore: Int,
    val volumeDeltaScore: Int,
    val rsiMomentumScore: Int,
    val newsSentimentScore: Int,
    val advancingCount: Int,
    val decliningCount: Int,
    val averageBuyFlowPercent: Int
)

fun computeMarketFearAndGreed(
    signals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot,
    newsSentimentIndex: Int
): FearAndGreedMetrics {
    val imoexScore = ((imoexSnapshot.changePercent + 2.5) / 5.0 * 100.0)
        .coerceIn(0.0, 100.0)
        .roundToInt()

    val advancing = signals.count { it.changePercent > 0.05 }
    val declining = signals.count { it.changePercent < -0.05 }
    val longCount = signals.count { it.direction == SignalDirection.LONG }
    val shortCount = signals.count { it.direction == SignalDirection.SHORT }
    val breadthScore = if (signals.isEmpty()) {
        50
    } else {
        val advRatio = advancing.toDouble() / signals.size.toDouble() * 100.0
        val dirTotal = (longCount + shortCount).coerceAtLeast(1)
        val dirRatio = if (longCount + shortCount == 0) 50.0 else (longCount.toDouble() / dirTotal.toDouble()) * 100.0
        (advRatio * 0.55 + dirRatio * 0.45).coerceIn(0.0, 100.0).roundToInt()
    }

    val avgBuyFlow = if (signals.isEmpty()) {
        50
    } else {
        signals.map { (it.buySellVolumeRatio * 100.0).coerceIn(0.0, 100.0) }.average().coerceIn(0.0, 100.0).roundToInt()
    }

    val rsiScore = if (signals.isEmpty()) {
        50
    } else {
        signals.map { it.rsi14 }.average().coerceIn(15.0, 85.0).let { avgRsi ->
            ((avgRsi - 20.0) / 60.0 * 100.0).coerceIn(0.0, 100.0).roundToInt()
        }
    }

    val newsScore = ((newsSentimentIndex + 100) / 2.0)
        .coerceIn(0.0, 100.0)
        .roundToInt()

    val composite = (
        imoexScore * 0.25 +
            breadthScore * 0.25 +
            avgBuyFlow * 0.20 +
            rsiScore * 0.15 +
            newsScore * 0.15
        ).coerceIn(0.0, 100.0).roundToInt()

    val (labelRu, labelEn, descRu) = when {
        composite <= 20 -> Triple(
            "ЭКСТРЕМАЛЬНЫЙ СТРАХ",
            "Extreme Fear",
            "Высокая перепроданность на рынке РФ: ищите статистические отклонения (Z-Score < -2.0, Калман) или работайте от SHORT."
        )
        composite <= 40 -> Triple(
            "СТРАХ",
            "Fear",
            "Предложение доминирует в стакане MOEX: приоритет защитным активам и строгая фильтрация сигналов LONG."
        )
        composite <= 59 -> Triple(
            "НЕЙТРАЛЬНО",
            "Neutral",
            "Баланс спроса и предложения: торгуйте избирательные сетапы с высоким Alpha Score и Profit Factor."
        )
        composite <= 79 -> Triple(
            "ЖАДНОСТЬ",
            "Greed",
            "Уверенный приток капитала в акции РФ: приоритет импульсным трендам (Hurst H > 0.55) по лидерам рынка."
        )
        else -> Triple(
            "ЭКСТРЕМАЛЬНАЯ ЖАДНОСТЬ",
            "Extreme Greed",
            "Перегрев спроса на Мосбирже: подтягивайте ATR Trailing Stop ближе к текущей цене."
        )
    }

    return FearAndGreedMetrics(
        overallScore = composite,
        labelRu = labelRu,
        labelEn = labelEn,
        descriptionRu = descRu,
        imoexMomentumScore = imoexScore,
        marketBreadthScore = breadthScore,
        volumeDeltaScore = avgBuyFlow,
        rsiMomentumScore = rsiScore,
        newsSentimentScore = newsScore,
        advancingCount = advancing,
        decliningCount = declining,
        averageBuyFlowPercent = avgBuyFlow
    )
}

/**
 * Fear & Greed Index component on the main dashboard visualizing overall market sentiment
 * using a multi-zone circular progress gauge. Fixed for Samsung S25 360dp width using FlowRow!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FearAndGreedIndexCard(
    signals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot,
    newsSentimentIndex: Int,
    onOpenNewsTab: () -> Unit = {},
    onOpen3DSceneTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val metrics = remember(signals, imoexSnapshot, newsSentimentIndex) {
        computeMarketFearAndGreed(
            signals = signals,
            imoexSnapshot = imoexSnapshot,
            newsSentimentIndex = newsSentimentIndex
        )
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (metrics.overallScore / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "fear_greed_progress"
    )

    val sentimentColor = when {
        metrics.overallScore <= 20 -> Color(0xFF707070)
        metrics.overallScore <= 40 -> Color(0xFF969696)
        metrics.overallScore <= 59 -> Color(0xFFB8B8B8)
        metrics.overallScore <= 79 -> Color(0xFFE0E0E0)
        else -> Color(0xFFFFFFFF)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(1.dp, sentimentColor.copy(alpha = 0.48f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                expanded = !expanded
            }
            .testTag("fear_and_greed_index_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular Progress Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(84.dp)
                        .testTag("fear_and_greed_circular_gauge")
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(80.dp),
                        color = sentimentColor.copy(alpha = 0.35f),
                        trackColor = palette.borderCard.copy(alpha = 0.25f),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round
                    )

                    Canvas(modifier = Modifier.size(72.dp)) {
                        val strokeWidthPx = 8.dp.toPx()
                        val startAngle = 135f
                        val totalSweep = 270f
                        val arcDiameter = min(size.width, size.height) - strokeWidthPx
                        val topLeftOffset = Offset(
                            (size.width - arcDiameter) / 2f,
                            (size.height - arcDiameter) / 2f
                        )
                        val arcSize = Size(arcDiameter, arcDiameter)

                        drawArc(
                            color = palette.borderCard.copy(alpha = 0.45f),
                            startAngle = startAngle,
                            sweepAngle = totalSweep,
                            useCenter = false,
                            topLeft = topLeftOffset,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )

                        val zoneColors = listOf(
                            Color(0xFF555555).copy(alpha = 0.30f),
                            Color(0xFF777777).copy(alpha = 0.30f),
                            Color(0xFF999999).copy(alpha = 0.30f),
                            Color(0xFFCCCCCC).copy(alpha = 0.30f),
                            Color(0xFFFFFFFF).copy(alpha = 0.30f)
                        )
                        val segSweep = totalSweep / zoneColors.size
                        zoneColors.forEachIndexed { idx, zColor ->
                            drawArc(
                                color = zColor,
                                startAngle = startAngle + idx * segSweep,
                                sweepAngle = segSweep - 2f,
                                useCenter = false,
                                topLeft = topLeftOffset,
                                size = arcSize,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                            )
                        }

                        val activeSweep = (totalSweep * animatedProgress).coerceIn(2f, totalSweep)
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF666666),
                                    Color(0xFF999999),
                                    Color(0xFFCCCCCC),
                                    Color(0xFFFFFFFF),
                                    sentimentColor
                                )
                            ),
                            startAngle = startAngle,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = topLeftOffset,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )

                        val pointerAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                        val radius = arcDiameter / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dotCenter = Offset(
                            x = center.x + (radius * cos(pointerAngleRad)).toFloat(),
                            y = center.y + (radius * sin(pointerAngleRad)).toFloat()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = strokeWidthPx * 0.62f,
                            center = dotCenter
                        )
                        drawCircle(
                            color = sentimentColor,
                            radius = strokeWidthPx * 0.42f,
                            center = dotCenter
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = metrics.overallScore,
                            transitionSpec = {
                                (slideInVertically { h -> h / 2 } + fadeIn(tween(200)))
                                    .togetherWith(slideOutVertically { h -> -h / 2 } + fadeOut(tween(150)))
                            },
                            label = "fear_greed_score_text"
                        ) { targetScore ->
                            Text(
                                text = "$targetScore",
                                fontFamily = SpaceGroteskFontFamily,
                                color = sentimentColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "/100",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = palette.textMuted,
                            fontSize = 8.sp
                        )
                    }
                }

                // Right Column: Responsive FlowRow header & telemetry pills so Samsung S25 never overflows
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "FEAR & GREED INDEX",
                                fontFamily = SpaceGroteskFontFamily,
                                color = palette.cyanAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = sentimentColor.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, sentimentColor.copy(alpha = 0.65f))
                            ) {
                                Text(
                                    text = metrics.labelRu,
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = sentimentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Детали индекса страха и жадности",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = metrics.descriptionRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = if (expanded) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Responsive FlowRow of rounded telemetry pills (Fixes Samsung S25 360dp width wrap bug)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        HeaderBadge(
                            text = "Ширина: ${metrics.advancingCount}/${metrics.decliningCount}",
                            textColor = if (metrics.advancingCount >= metrics.decliningCount) palette.longGreen else palette.shortRed,
                            bgColor = if (metrics.advancingCount >= metrics.decliningCount) palette.longGreenDim else palette.shortRedDim
                        )
                        HeaderBadge(
                            text = "Спрос: ${metrics.averageBuyFlowPercent}%",
                            textColor = if (metrics.averageBuyFlowPercent >= 50) palette.longGreen else palette.shortRed,
                            bgColor = palette.bgElevated
                        )
                        HeaderBadge(
                            text = "Фон: ${if (newsSentimentIndex >= 0) "+" else ""}$newsSentimentIndex",
                            textColor = palette.cyanAccent,
                            bgColor = palette.cyanDim
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.cyanDim,
                            border = BorderStroke(0.8.dp, palette.cyanAccent),
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpen3DSceneTab()
                                }
                                .testTag("open_threejs_3d_from_dashboard_chip")
                        ) {
                            Text(
                                text = "3D Терминал",
                                fontFamily = SpaceGroteskFontFamily,
                                color = palette.cyanAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Expandable 5-Factor Breakdown
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = palette.borderCard.copy(alpha = 0.6f))
                    Text(
                        text = "ДЕКОМПОЗИЦИЯ ФАКТОРОВ СЕНТИМЕНТА MOEX (0 = СТРАХ, 100 = ЖАДНОСТЬ)",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )

                    FearGreedFactorRow(
                        label = "1. Импульс индекса IMOEX (${QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)})",
                        score = metrics.imoexMomentumScore
                    )
                    FearGreedFactorRow(
                        label = "2. Ширина рынка (Растущие ${metrics.advancingCount} vs Падающие ${metrics.decliningCount})",
                        score = metrics.marketBreadthScore
                    )
                    FearGreedFactorRow(
                        label = "3. Дельта потока ордеров (Доля покупок ${metrics.averageBuyFlowPercent}%)",
                        score = metrics.volumeDeltaScore
                    )
                    FearGreedFactorRow(
                        label = "4. Средний индекс RSI-14 по 20 голубым фишкам",
                        score = metrics.rsiMomentumScore
                    )
                    FearGreedFactorRow(
                        label = "5. Сентимент новостного потока РФ",
                        score = metrics.newsSentimentScore
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpen3DSceneTab()
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, palette.cyanAccent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "3D Сцена Рынка (Three.js)",
                                fontSize = 10.sp,
                                color = palette.cyanAccent,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenNewsTab()
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, palette.cyanAccent.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Лента новостей РФ",
                                fontSize = 10.sp,
                                color = palette.cyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FearGreedFactorRow(
    label: String,
    score: Int
) {
    val palette = LocalAppPalette.current
    val barColor = when {
        score <= 35 -> palette.shortRed
        score <= 60 -> palette.amberWait
        else -> palette.longGreen
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$score / 100",
                fontFamily = JetBrainsMonoFontFamily,
                color = barColor,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50)),
            color = barColor,
            trackColor = palette.borderCard.copy(alpha = 0.4f)
        )
    }
}

/**
 * Top Quant Pick of the Day spotlight card (Clean Institutional Design, Rounded Pill Button)
 */
@Composable
fun TopPickOfTheDayBanner(
    signal: QuantSignal,
    onSelectForRisk: () -> Unit,
    onOpenPaperTrade: () -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    val isLong = signal.direction == SignalDirection.LONG
    val accent = if (isLong) palette.longGreen else if (signal.direction == SignalDirection.SHORT) palette.shortRed else palette.cyanAccent
    val iq = signal.institutionalMetrics

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgElevated),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.68f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectForRisk()
            }
            .testTag("top_pick_of_the_day_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "TOP ALPHA PICK • ${iq.institutionalRating}",
                            fontFamily = SpaceGroteskFontFamily,
                            color = accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Alpha ${iq.compositeAlphaScore} • PF ${String.format(Locale.US, "%.2f", signal.backtestResult.profitFactor)} • WR ${signal.backtestResult.winratePercent}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = palette.cyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${signal.direction.badgeText} #${signal.ticker} (${signal.name}) • Вход ${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)} ₽ → TP2 ${QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)} ₽",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenPaperTrade()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF04121C)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "В портфель (${signal.confidence}%)",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Kvant Monochrome Institutional Boot & Telemetry Shimmer Loader
 */
@Composable
fun BloombergShimmerLoadingScreen(modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    val transition = rememberInfiniteTransition(label = "shimmer_loading")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val pulseProgress by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boot_progress"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            palette.bgCard,
            palette.bgElevated,
            Color.White.copy(alpha = 0.12f),
            palette.bgCard
        ),
        start = Offset(shimmerProgress - 350f, 0f),
        end = Offset(shimmerProgress, 260f)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("kvant_loading_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.bgCard),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "K",
                                        fontFamily = SpaceGroteskFontFamily,
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "KVANT QUANT ENGINE • ЗАГРУЗКА ТЕЛЕМЕТРИИ",
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Синхронизация MOEX ISS TQBR • 24-Факторное Ядро • Recharts Heatmap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = "${(pulseProgress * 100).toInt()}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }

                    LinearProgressIndicator(
                        progress = { pulseProgress },
                        color = Color.White,
                        trackColor = palette.bgElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Шлюз MOEX: Активен",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = palette.textSecondary,
                            fontSize = 9.5.sp
                        )
                        Text(
                            text = "Калибровка Hurst / Kalman / VPIN / Recharts",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        items(5) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.bgCard),
                border = BorderStroke(1.dp, palette.borderCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(palette.borderCard.copy(alpha = 0.55f))
                        )
                        Box(
                            modifier = Modifier
                                .width(85.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(palette.borderCard.copy(alpha = 0.55f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.borderCard.copy(alpha = 0.38f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(palette.borderCard.copy(alpha = 0.45f))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Institutional Stock Signal Card with 16-Factor Quant Analytics, Kalman Ribbon, Monte Carlo Cone,
 * and rounded pill buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalCard(
    signal: QuantSignal,
    isSelectedForRisk: Boolean = false,
    tickerHistory: List<SignalHistoryEntity> = emptyList(),
    imoexSnapshot: ImoexIndexSnapshot = ImoexIndexSnapshot(),
    timeframeMinutes: Int = 10,
    defaultRiskRub: Double = 2500.0,
    compactMode: Boolean = false,
    ecoBatteryMode: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onChangeTimeframe: (Int) -> Unit = {},
    onSelectForRisk: () -> Unit = {},
    onOpenFullHistoryForTicker: (String) -> Unit = {},
    onOpenNewsForTicker: (String) -> Unit = {},
    onOpenPaperTrade: (QuantSignal) -> Unit = {}
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showInlineHistory by rememberSaveable(signal.ticker) { mutableStateOf(false) }
    var showFullDetailsInCompact by rememberSaveable(signal.ticker) { mutableStateOf(false) }

    // Live Price Tick Flash (600ms emerald/crimson pulse on price change)
    var flashState by remember(signal.ticker) { mutableIntStateOf(0) }
    LaunchedEffect(signal.currentPrice) {
        if (signal.priceTickDirection != 0) {
            flashState = signal.priceTickDirection
            delay(600L)
            flashState = 0
        }
    }

    val priceFlashBgColor by animateColorAsState(
        targetValue = when {
            flashState > 0 -> palette.longGreenDim.copy(alpha = 0.85f)
            flashState < 0 -> palette.shortRedDim.copy(alpha = 0.85f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 450),
        label = "price_tick_flash"
    )

    val accentColor = when (signal.direction) {
        SignalDirection.LONG -> palette.longGreen
        SignalDirection.SHORT -> palette.shortRed
        SignalDirection.WAIT -> palette.amberWait
    }

    val badgeBgColor = when (signal.direction) {
        SignalDirection.LONG -> palette.longGreenDim
        SignalDirection.SHORT -> palette.shortRedDim
        SignalDirection.WAIT -> palette.amberWaitDim
    }

    val isHighConfidenceActive = signal.direction != SignalDirection.WAIT && signal.confidence >= 85 && !ecoBatteryMode
    val infiniteGlow = rememberInfiniteTransition(label = "neon_radar_glow")
    val glowAlpha by infiniteGlow.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val cardBorderColor = when {
        signal.hasHighRiskNewsWarning -> palette.shortRed
        signal.isBreakevenActive -> palette.longGreen
        isSelectedForRisk -> palette.cyanAccent
        isHighConfidenceActive -> accentColor.copy(alpha = glowAlpha)
        signal.direction == SignalDirection.LONG -> palette.longGreen.copy(alpha = 0.45f)
        signal.direction == SignalDirection.SHORT -> palette.shortRed.copy(alpha = 0.45f)
        else -> palette.borderCard
    }

    var cumulativeDrag by remember { mutableFloatStateOf(0f) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(
            width = if (isSelectedForRisk || signal.isBreakevenActive || signal.hasHighRiskNewsWarning || isHighConfidenceActive) 1.5.dp else 1.dp,
            color = cardBorderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .pointerInput(signal.ticker) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (cumulativeDrag > 160f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenPaperTrade(signal)
                        } else if (cumulativeDrag < -160f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            TraderVoiceAndShareHelper.shareSetupToTelegram(
                                context = context,
                                signal = signal,
                                timeframeMinutes = timeframeMinutes,
                                riskRub = defaultRiskRub,
                                imoexSnapshot = imoexSnapshot
                            )
                        }
                        cumulativeDrag = 0f
                    },
                    onDragCancel = { cumulativeDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        cumulativeDrag += dragAmount
                    }
                )
            }
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (compactMode) {
                    showFullDetailsInCompact = !showFullDetailsInCompact
                }
                onSelectForRisk()
            }
            .testTag("signal_card_${signal.ticker}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactMode && !showFullDetailsInCompact) 11.dp else 14.dp)
        ) {
            // Top Row: Watchlist Star + Ticker + Tier Pill + Live Price + Direction Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleFavorite()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("fav_star_${signal.ticker}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "В избранное Watchlist",
                            tint = if (isFavorite) palette.amberWait else palette.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        TraderVoiceAndShareHelper.copyToClipboardWithToast(
                                            context = context,
                                            labelRu = "Тикер",
                                            valueText = signal.ticker
                                        )
                                    }
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = signal.ticker,
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Скопировать тикер",
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (signal.isBlueChip) palette.cyanDim else palette.amberWaitDim,
                                border = BorderStroke(
                                    0.6.dp,
                                    if (signal.isBlueChip) palette.cyanAccent.copy(alpha = 0.5f) else palette.amberWait.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = if (signal.isCrypto) {
                                        "USDT • ${signal.recommendedLeverage}x"
                                    } else if (signal.isBlueChip) {
                                        "TIER-1"
                                    } else {
                                        "TIER-2"
                                    },
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = if (signal.isBlueChip) palette.cyanAccent else palette.amberWait,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${signal.name} • ${signal.sectorRu}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }
                }

                val curSym = signal.currencySymbol
                val formattedLivePrice = if (signal.isCrypto) {
                    "$${QuantSignalEngine.formatPrice(signal.currentPrice, signal.minStep)}"
                } else {
                    "${QuantSignalEngine.formatPrice(signal.currentPrice, signal.minStep)} ₽"
                }

                // Live Price with 600ms Tick Flash + Day % Change
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = priceFlashBgColor
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formattedLivePrice,
                            fontFamily = SpaceGroteskFontFamily,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        )
                        val changeSign = if (signal.changePercent >= 0) "+" else ""
                        Text(
                            text = "$changeSign${QuantSignalEngine.formatOneDec(signal.changePercent)}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = if (signal.changePercent >= 0) palette.longGreen else palette.shortRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeBgColor,
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = signal.direction.badgeText,
                            fontFamily = SpaceGroteskFontFamily,
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${signal.confidence}% • ${signal.confluencePassedCount}/7",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = accentColor.copy(alpha = 0.9f),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Compact Pro View 1-line summary
            if (compactMode && !showFullDetailsInCompact) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Вход ${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)} • TP1 ${QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)} • TP2 ${QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)} • SL ${QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Alpha ${signal.institutionalMetrics.compositeAlphaScore} • WR ${signal.backtestResult.winratePercent}%",
                        fontFamily = SpaceGroteskFontFamily,
                        color = palette.cyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Mini-Chart with Entry / TP1 / TP2 / SL Levels
            InteractiveCandlestickChartWithLevels(
                candles = signal.recentCandles,
                direction = signal.direction,
                entryPrice = signal.entryPrice,
                takeProfit1 = signal.takeProfit1,
                takeProfit2 = signal.takeProfit2,
                stopLoss = signal.dynamicStopLoss,
                minStep = signal.minStep,
                timeframeMinutes = timeframeMinutes,
                onSelectTimeframe = onChangeTimeframe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Clear Price Levels (Tap to Copy)
            val priceUnitSuffix = if (signal.isCrypto) " $" else " ₽"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.bgElevated)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LevelMetricBox(
                    label = "ВХОД",
                    value = "${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)}$priceUnitSuffix",
                    subValue = "Точка входа",
                    valueColor = palette.textPrimary,
                    onClickCopy = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        TraderVoiceAndShareHelper.copyToClipboardWithToast(
                            context,
                            "Вход #${signal.ticker}",
                            QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                LevelMetricBox(
                    label = "ЦЕЛЬ 1 (TP1)",
                    value = "${QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)}$priceUnitSuffix",
                    subValue = if (signal.direction == SignalDirection.WAIT) "1.5 ATR"
                    else "+${QuantSignalEngine.formatOneDec(signal.tp1Percent)}%",
                    valueColor = palette.longGreen,
                    onClickCopy = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        TraderVoiceAndShareHelper.copyToClipboardWithToast(
                            context,
                            "TP1 #${signal.ticker}",
                            QuantSignalEngine.formatPrice(signal.takeProfit1, signal.minStep)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                LevelMetricBox(
                    label = "ЦЕЛЬ 2 (TP2)",
                    value = "${QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)}$priceUnitSuffix",
                    subValue = if (signal.direction == SignalDirection.WAIT) "3.0 ATR"
                    else "+${QuantSignalEngine.formatOneDec(signal.tp2Percent)}%",
                    valueColor = palette.longGreen,
                    onClickCopy = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        TraderVoiceAndShareHelper.copyToClipboardWithToast(
                            context,
                            "TP2 #${signal.ticker}",
                            QuantSignalEngine.formatPrice(signal.takeProfit2, signal.minStep)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                LevelMetricBox(
                    label = "СТОП (SL)",
                    value = "${QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)}$priceUnitSuffix",
                    subValue = "-${QuantSignalEngine.formatOneDec(signal.slPercent)}%",
                    valueColor = palette.shortRed,
                    onClickCopy = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        TraderVoiceAndShareHelper.copyToClipboardWithToast(
                            context,
                            "Стоп #${signal.ticker}",
                            QuantSignalEngine.formatPrice(signal.dynamicStopLoss, signal.minStep)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (signal.isCrypto) {
                // Dedicated Crypto Derivatives & Smart Proposal Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.bgElevated,
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val fundingSign = if (signal.fundingRate8hPct >= 0) "+" else ""
                            HeaderBadge(
                                text = "Фандинг 8ч: $fundingSign${String.format(Locale.US, "%.3f", signal.fundingRate8hPct)}%",
                                textColor = if (signal.fundingRate8hPct < 0.0) Color.White else palette.textSecondary,
                                bgColor = palette.bgCard
                            )
                            val oiSign = if (signal.openInterestChangePct >= 0) "+" else ""
                            HeaderBadge(
                                text = "OI 24ч: $oiSign${String.format(Locale.US, "%.1f", signal.openInterestChangePct)}%",
                                textColor = Color.White,
                                bgColor = palette.bgCard
                            )
                            HeaderBadge(
                                text = "Изолир. ${signal.recommendedLeverage}x (Ликв. ~${String.format(Locale.US, "%.0f", signal.liquidationBufferPct)}%)",
                                textColor = Color.White,
                                bgColor = palette.bgCard
                            )
                            HeaderBadge(
                                text = signal.relativeStrengthBadgeRu,
                                textColor = if (signal.relativeStrengthVsImoex >= 0) Color.White else palette.textSecondary,
                                bgColor = palette.bgCard
                            )
                        }
                        Text(
                            text = signal.cryptoActionProposalRu.ifBlank { signal.reason },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textPrimary,
                            lineHeight = 15.sp,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                // Concise summary line for a regular MOEX trader
                Text(
                    text = "Winrate 30д: ${signal.backtestResult.winratePercent}% • RSI: ${QuantSignalEngine.formatOneDec(signal.rsi14)} • Лот: ${signal.lotSize} шт.",
                    fontFamily = JetBrainsMonoFontFamily,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = signal.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Single Clean Row of Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenPaperTrade(signal)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(38.dp)
                        .testTag("paper_trade_button_${signal.ticker}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "В портфель",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectForRisk()
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("calc_risk_button_${signal.ticker}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Риск / Лоты",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(signal.tradingViewUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, palette.borderCard),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("tradingview_button_${signal.ticker}")
                ) {
                    Text(
                        text = "TV ↗",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 11.sp,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            AnimatedVisibility(visible = showInlineHistory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.bgElevated)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Журнал сделок ${signal.ticker}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Все (${tickerHistory.size}) →",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.cyanAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onOpenFullHistoryForTicker(signal.ticker) }
                                .padding(4.dp)
                                .testTag("open_full_ticker_history_${signal.ticker}")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (tickerHistory.isEmpty()) {
                        Text(
                            text = "По бумаге ${signal.ticker} еще нет записанных сигналов LONG/SHORT.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted
                        )
                    } else {
                        tickerHistory.take(4).forEach { item ->
                            CompactHistoryRow(item = item, minStep = signal.minStep)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntradayRangeProgressBar(
    dayLow: Double,
    dayHigh: Double,
    currentPrice: Double,
    rangePosition: Float,
    minStep: Double
) {
    val palette = LocalAppPalette.current
    val clampedPos = rangePosition.coerceIn(0.02f, 0.98f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bgElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Low: ${QuantSignalEngine.formatPrice(dayLow, minStep)} ₽",
                fontFamily = JetBrainsMonoFontFamily,
                color = palette.shortRed,
                fontSize = 9.sp
            )
            Text(
                text = "Диапазон дня (${(clampedPos * 100).roundToInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Text(
                text = "High: ${QuantSignalEngine.formatPrice(dayHigh, minStep)} ₽",
                fontFamily = JetBrainsMonoFontFamily,
                color = palette.longGreen,
                fontSize = 9.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.borderCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedPos)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(palette.shortRed, palette.amberWait, palette.longGreen)
                        )
                    )
            )
        }
    }
}

@Composable
private fun CompactHistoryRow(
    item: SignalHistoryEntity,
    minStep: Double
) {
    val palette = LocalAppPalette.current
    val outcome = TradeOutcome.fromCode(item.status)
    val isLong = item.signalType == "LONG"
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    val statusColor = when (outcome) {
        TradeOutcome.HIT_TP -> palette.longGreen
        TradeOutcome.HIT_SL -> palette.shortRed
        TradeOutcome.ACTIVE -> palette.cyanAccent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.bgCard)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isLong) "▲ LONG" else "▼ SHORT",
                fontFamily = SpaceGroteskFontFamily,
                color = if (isLong) palette.longGreen else palette.shortRed,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Вход ${QuantSignalEngine.formatPrice(item.entryPrice, minStep)} → TP2 ${QuantSignalEngine.formatPrice(item.takeProfit2, minStep)}",
                fontFamily = JetBrainsMonoFontFamily,
                color = palette.textSecondary,
                fontSize = 9.5.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
                fontSize = 9.5.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (outcome) {
                    TradeOutcome.HIT_TP -> "ТЕЙК (+${QuantSignalEngine.formatOneDec(abs(item.pnlPercent))}%)"
                    TradeOutcome.HIT_SL -> "СТОП (-${QuantSignalEngine.formatOneDec(abs(item.pnlPercent))}%)"
                    TradeOutcome.ACTIVE -> "АКТИВЕН"
                },
                fontFamily = SpaceGroteskFontFamily,
                color = statusColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.5.sp
            )
        }
    }
}

@Composable
private fun LevelMetricBox(
    label: String,
    value: String,
    subValue: String,
    valueColor: Color,
    onClickCopy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClickCopy() }
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            fontSize = 8.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = SpaceGroteskFontFamily,
            color = valueColor,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            fontSize = 11.sp
        )
        Text(
            text = subValue,
            fontFamily = JetBrainsMonoFontFamily,
            color = valueColor.copy(alpha = 0.8f),
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun IndicatorPill(
    text: String,
    highlightColor: Color? = null
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(50),
        color = palette.bgElevated,
        border = BorderStroke(
            width = 0.7.dp,
            color = highlightColor?.copy(alpha = 0.6f) ?: palette.borderCard
        )
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            color = highlightColor ?: palette.textSecondary,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Interactive Mini-Chart with Touch Crosshair, Candlesticks <-> Area Sparkline toggle,
 * Dotted horizontal Entry/TP1/SL level lines, and Horizontal Volume Profile (POC).
 */
@Composable
fun InteractiveCandlestickChartWithLevels(
    candles: List<MoexCandle>,
    direction: SignalDirection,
    entryPrice: Double,
    takeProfit1: Double,
    takeProfit2: Double,
    stopLoss: Double,
    minStep: Double,
    timeframeMinutes: Int,
    onSelectTimeframe: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    var showCandlesticks by rememberSaveable { mutableStateOf(true) }
    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }

    val displayCandles = remember(candles) { candles.takeLast(32) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070C14))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val inspectedCandle = selectedCandleIndex?.let { idx ->
                displayCandles.getOrNull(idx.coerceIn(0, (displayCandles.size - 1).coerceAtLeast(0)))
            }
            Text(
                text = if (inspectedCandle != null) {
                    "Прицел: ${QuantSignalEngine.formatPrice(inspectedCandle.close, minStep)} ₽ (${inspectedCandle.beginTime.takeLast(8).take(5)})"
                } else {
                    "График + POC Профиль объема (тап для прицела)"
                },
                fontFamily = JetBrainsMonoFontFamily,
                color = if (inspectedCandle != null) palette.cyanAccent else palette.textMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.bgElevated,
                    border = BorderStroke(0.6.dp, palette.cyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { showCandlesticks = !showCandlesticks }
                ) {
                    Text(
                        text = if (showCandlesticks) "Свечи" else "Линия",
                        fontFamily = SpaceGroteskFontFamily,
                        color = palette.cyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                listOf(10 to "10M", 60 to "1H", 1440 to "1D").forEach { (tfMin, tfText) ->
                    val isTfSelected = timeframeMinutes == tfMin
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isTfSelected) palette.cyanAccent else palette.bgElevated,
                        modifier = Modifier.clickable { onSelectTimeframe(tfMin) }
                    ) {
                        Text(
                            text = tfText,
                            fontFamily = SpaceGroteskFontFamily,
                            color = if (isTfSelected) Color(0xFF04121C) else palette.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val strokeColor = when (direction) {
            SignalDirection.LONG -> palette.longGreen
            SignalDirection.SHORT -> palette.shortRed
            SignalDirection.WAIT -> palette.cyanAccent
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayCandles.size) {
                    detectTapGestures { offset ->
                        if (displayCandles.isNotEmpty() && size.width > 0) {
                            val idx = ((offset.x / size.width.toFloat()) * displayCandles.size)
                                .toInt()
                                .coerceIn(0, displayCandles.size - 1)
                            selectedCandleIndex = if (selectedCandleIndex == idx) null else idx
                        }
                    }
                }
        ) {
            if (displayCandles.size < 2) return@Canvas

            val allHighs = displayCandles.map { it.high } + listOf(entryPrice, takeProfit1, stopLoss)
            val allLows = displayCandles.map { it.low } + listOf(entryPrice, takeProfit1, stopLoss)
            val minPrice = allLows.minOrNull() ?: 0.0
            val maxPrice = allHighs.maxOrNull() ?: 1.0
            val range = max(1e-6, maxPrice - minPrice)

            fun priceToY(p: Double): Float {
                val norm = ((p - minPrice) / range).toFloat().coerceIn(0f, 1f)
                return size.height - (norm * (size.height * 0.84f) + size.height * 0.08f)
            }

            // 1. Horizontal Volume Profile (POC) bars on the right side
            val bins = 6
            val binVolumes = DoubleArray(bins)
            for (c in displayCandles) {
                val mid = (c.high + c.low) / 2.0
                val binIdx = (((mid - minPrice) / range) * (bins - 1)).toInt().coerceIn(0, bins - 1)
                binVolumes[binIdx] += c.valueRub
            }
            val maxBinVol = (binVolumes.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            val pocBinIndex = binVolumes.indices.maxByOrNull { binVolumes[it] } ?: 0
            val binHeight = size.height / bins.toFloat()

            for (b in 0 until bins) {
                val ratio = (binVolumes[b] / maxBinVol).toFloat().coerceIn(0.05f, 1f)
                val barW = size.width * 0.22f * ratio
                val topY = size.height - (b + 1) * binHeight
                drawRect(
                    color = if (b == pocBinIndex) palette.amberWait.copy(alpha = 0.28f)
                    else palette.cyanAccent.copy(alpha = 0.14f),
                    topLeft = Offset(size.width - barW, topY + 1f),
                    size = Size(barW, (binHeight - 2f).coerceAtLeast(2f))
                )
            }

            // 2. Dotted horizontal level lines for Entry, TP1, SL
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            val yEntry = priceToY(entryPrice)
            val yTp1 = priceToY(takeProfit1)
            val ySl = priceToY(stopLoss)

            drawLine(
                color = palette.cyanAccent.copy(alpha = 0.55f),
                start = Offset(0f, yEntry),
                end = Offset(size.width, yEntry),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = palette.longGreen.copy(alpha = 0.55f),
                start = Offset(0f, yTp1),
                end = Offset(size.width, yTp1),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = palette.shortRed.copy(alpha = 0.55f),
                start = Offset(0f, ySl),
                end = Offset(size.width, ySl),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            // 3. Candlesticks or Area Sparkline
            if (showCandlesticks) {
                val slotW = size.width / displayCandles.size.toFloat()
                val bodyW = (slotW * 0.62f).coerceAtLeast(2f)
                displayCandles.forEachIndexed { idx, c ->
                    val centerX = idx * slotW + slotW / 2f
                    val isBull = c.close >= c.open
                    val cColor = if (isBull) palette.longGreen else palette.shortRed
                    val yHigh = priceToY(c.high)
                    val yLow = priceToY(c.low)
                    val yOpen = priceToY(c.open)
                    val yClose = priceToY(c.close)

                    drawLine(
                        color = cColor.copy(alpha = 0.85f),
                        start = Offset(centerX, yHigh),
                        end = Offset(centerX, yLow),
                        strokeWidth = 1.1.dp.toPx()
                    )
                    val topBody = min(yOpen, yClose)
                    val bottomBody = max(yOpen, yClose)
                    val bh = (bottomBody - topBody).coerceAtLeast(2f)
                    drawRect(
                        color = cColor,
                        topLeft = Offset(centerX - bodyW / 2f, topBody),
                        size = Size(bodyW, bh)
                    )
                }
            } else {
                val stepX = size.width / (displayCandles.size - 1).toFloat()
                val linePath = Path()
                val fillPath = Path()
                displayCandles.forEachIndexed { index, c ->
                    val x = index * stepX
                    val y = priceToY(c.close)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                drawPath(
                    path = linePath,
                    color = strokeColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 4. Interactive Crosshair
            selectedCandleIndex?.let { selIdx ->
                if (selIdx in displayCandles.indices) {
                    val slotW = size.width / displayCandles.size.toFloat()
                    val cx = selIdx * slotW + slotW / 2f
                    val cy = priceToY(displayCandles[selIdx].close)
                    drawLine(
                        color = palette.cyanAccent,
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = palette.cyanAccent,
                        start = Offset(0f, cy),
                        end = Offset(size.width, cy),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawCircle(
                        color = palette.cyanAccent,
                        radius = 4.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

/**
 * Interactive Position Size, Net Broker Fee (0.05%), Kelly Fraction, Custom Stop-Loss & DCA Calculator
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiskCalculatorPanel(
    selectedSignal: QuantSignal?,
    availableCashRub: Double = 1_000_000.0,
    onClose: () -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    if (selectedSignal == null) return

    val isCrypto = selectedSignal.isCrypto
    val curSym = selectedSignal.currencySymbol
    var depositInput by rememberSaveable(isCrypto) { mutableStateOf(if (isCrypto) "10000" else "100000") }
    var riskRubPreset by rememberSaveable(isCrypto) { mutableStateOf(if (isCrypto) "100" else "2500") }
    var customStopLossInput by rememberSaveable(selectedSignal.ticker) {
        mutableStateOf(QuantSignalEngine.formatPrice(selectedSignal.dynamicStopLoss, selectedSignal.minStep))
    }
    var showDcaCalculator by rememberSaveable { mutableStateOf(false) }
    var dcaExistingLotsInput by rememberSaveable { mutableStateOf("10") }
    var dcaExistingPriceInput by rememberSaveable(selectedSignal.ticker) {
        mutableStateOf(QuantSignalEngine.formatPrice(selectedSignal.entryPrice * 1.02, selectedSignal.minStep))
    }
    var dcaNewLotsInput by rememberSaveable { mutableStateOf("10") }

    val depositRub = depositInput.replace(",", ".").toDoubleOrNull() ?: if (isCrypto) 10000.0 else 100000.0
    val maxRiskRub = (riskRubPreset.replace(",", ".").toDoubleOrNull() ?: if (isCrypto) 100.0 else 2500.0).coerceAtLeast(1.0)
    val effectiveStopPrice = customStopLossInput.replace(",", ".").toDoubleOrNull() ?: selectedSignal.dynamicStopLoss

    val riskPerShare = abs(selectedSignal.entryPrice - effectiveStopPrice)
    val riskPerLot = riskPerShare * selectedSignal.lotSize

    val recommendedLots = if (riskPerLot > 1e-9 && maxRiskRub > 0) {
        max(1, floor(maxRiskRub / riskPerLot).toInt())
    } else {
        1
    }
    val totalShares = recommendedLots * selectedSignal.lotSize
    val totalPositionRub = totalShares * selectedSignal.entryPrice
    val actualRiskRub = recommendedLots * riskPerLot

    val roundTripBrokerFeeRub = totalPositionRub * PaperTradingRepository.BROKER_FEE_RATE_PER_SIDE * 2.0

    val grossProfitTp1Rub = abs(selectedSignal.takeProfit1 - selectedSignal.entryPrice) * (totalShares * 0.5)
    val grossProfitTp2Rub = abs(selectedSignal.takeProfit2 - selectedSignal.entryPrice) * (totalShares * 0.5)
    val netTotalExpectedProfitRub = (grossProfitTp1Rub + grossProfitTp2Rub) - roundTripBrokerFeeRub

    val isMarginWarning = totalPositionRub > depositRub || (!isCrypto && totalPositionRub > availableCashRub)
    val isolatedMarginUsdt = if (isCrypto) totalPositionRub / selectedSignal.recommendedLeverage.coerceAtLeast(1) else totalPositionRub

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgElevated),
        border = BorderStroke(1.2.dp, palette.cyanAccent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize()
            .testTag("risk_calculator_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = palette.cyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCrypto) {
                            "Крипто-Риск & Плечо ${selectedSignal.recommendedLeverage}x • ${selectedSignal.ticker}/USDT"
                        } else {
                            "Риск-Модель + Келли (${String.format(Locale.US, "%.1f%%", selectedSignal.institutionalMetrics.optimalKellyFractionPct)}) • ${selectedSignal.ticker}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("close_risk_calculator_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть калькулятор",
                        tint = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Быстрый выбор риска на сделку ($curSym):",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val presets = if (isCrypto) listOf(25, 50, 100, 250, 500) else listOf(500, 1000, 2500, 5000, 10000)
                presets.forEach { rubPreset ->
                    val isCurrent = riskRubPreset == rubPreset.toString()
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isCurrent) palette.cyanAccent else palette.bgCard,
                        border = BorderStroke(1.dp, if (isCurrent) palette.cyanAccent else palette.borderCard),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            riskRubPreset = rubPreset.toString()
                        }
                    ) {
                        Text(
                            text = if (isCrypto) "$$rubPreset" else "$rubPreset ₽",
                            fontFamily = SpaceGroteskFontFamily,
                            color = if (isCurrent) Color(0xFF04121C) else palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                listOf(0.5 to "0.5% деп.", 1.0 to "1% деп.", 2.0 to "2% деп.").forEach { (pct, label) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = palette.bgCard,
                        border = BorderStroke(1.dp, palette.cyanAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            riskRubPreset = ((depositRub * (pct / 100.0)).roundToInt()).toString()
                        }
                    ) {
                        Text(
                            text = label,
                            fontFamily = SpaceGroteskFontFamily,
                            color = palette.cyanAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = depositInput,
                    onValueChange = { depositInput = it },
                    label = { Text("Депозит ($curSym)", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.cyanAccent,
                        unfocusedBorderColor = palette.borderCard,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("deposit_input_field")
                )

                OutlinedTextField(
                    value = riskRubPreset,
                    onValueChange = { riskRubPreset = it },
                    label = { Text("Риск ($curSym)", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.cyanAccent,
                        unfocusedBorderColor = palette.borderCard,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    ),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("risk_percent_input_field")
                )

                OutlinedTextField(
                    value = customStopLossInput,
                    onValueChange = { customStopLossInput = it },
                    label = { Text("Свой SL ($curSym)", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.shortRed,
                        unfocusedBorderColor = palette.borderCard,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    ),
                    modifier = Modifier
                        .weight(0.95f)
                        .testTag("custom_sl_input_field")
                )
            }

            if (isCrypto) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.bgCard,
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Изолир. маржа (${selectedSignal.recommendedLeverage}x): $${String.format(Locale.US, "%.1f", isolatedMarginUsdt)} USDT • Буфер до ликвидации: ~${String.format(Locale.US, "%.1f", selectedSignal.liquidationBufferPct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else if (isMarginWarning) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.amberWaitDim,
                    border = BorderStroke(1.dp, palette.amberWait),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Маржинальное плечо: Объем позиции (${QuantSignalEngine.formatRublesCompact(totalPositionRub)}) превышает депозит (${QuantSignalEngine.formatRublesCompact(depositRub)}).",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.amberWait,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.bgCard)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Объем к покупке", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                    Text(
                        text = if (isCrypto) "$totalShares ${selectedSignal.ticker}" else "$recommendedLots лотов ($totalShares шт.)",
                        fontFamily = SpaceGroteskFontFamily,
                        color = palette.cyanAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Сумма / Комиссия 0%", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                    Text(
                        text = "${QuantSignalEngine.formatRublesCompact(totalPositionRub, isCrypto)} (0 $curSym)",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Чистый Net Профит / Риск", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                    Text(
                        text = "+${netTotalExpectedProfitRub.toInt()} $curSym / -${actualRiskRub.toInt()} $curSym",
                        fontFamily = SpaceGroteskFontFamily,
                        color = palette.longGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDcaCalculator = !showDcaCalculator }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Калькулятор усреднения позиции (DCA)",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.cyanAccent,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showDcaCalculator) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = palette.cyanAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = showDcaCalculator) {
                val oldLots = (dcaExistingLotsInput.toIntOrNull() ?: 10).coerceAtLeast(1)
                val oldPrice = dcaExistingPriceInput.replace(",", ".").toDoubleOrNull() ?: selectedSignal.entryPrice
                val addLots = (dcaNewLotsInput.toIntOrNull() ?: 10).coerceAtLeast(1)
                val totalDcaLots = oldLots + addLots
                val avgPrice = ((oldLots * oldPrice) + (addLots * selectedSignal.currentPrice)) / totalDcaLots.toDouble()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.bgCard)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dcaExistingLotsInput,
                            onValueChange = { dcaExistingLotsInput = it },
                            label = { Text("Уже лотов", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dcaExistingPriceInput,
                            onValueChange = { dcaExistingPriceInput = it },
                            label = { Text("Средняя ($curSym)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dcaNewLotsInput,
                            onValueChange = { dcaNewLotsInput = it },
                            label = { Text("Добор лотов", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Новая средняя цена ($totalDcaLots лот.): ${QuantSignalEngine.formatPrice(avgPrice, selectedSignal.minStep)} $curSym (Текущая: ${QuantSignalEngine.formatPrice(selectedSignal.currentPrice, selectedSignal.minStep)} $curSym)",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = palette.longGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalHistoryScreen(
    allHistory: List<SignalHistoryEntity>,
    summary: PerformanceSummary,
    selectedTickerFilter: String?,
    onSelectTickerFilter: (String?) -> Unit,
    onClearHistory: () -> Unit,
    onExportCsv: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val filteredHistory = remember(allHistory, selectedTickerFilter) {
        if (selectedTickerFilter == null) {
            allHistory
        } else {
            allHistory.filter { it.ticker == selectedTickerFilter }
        }
    }

    val activeSummary = remember(filteredHistory) {
        com.example.data.SignalHistoryRepository.computeSummary(filteredHistory)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("signal_history_list"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.bgCard),
                border = BorderStroke(1.dp, palette.borderCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = palette.cyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTickerFilter == null) {
                                    "Статистика и История сигналов"
                                } else {
                                    "История сигналов • $selectedTickerFilter"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = onExportCsv,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("export_csv_history_button")
                            ) {
                                Text(
                                    text = "Экспорт CSV",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.5.sp,
                                    color = palette.cyanAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (allHistory.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearHistory,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("clear_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Очистить историю",
                                        tint = palette.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.bgElevated)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatItem(
                            label = "ВСЕГО",
                            value = "${activeSummary.totalSignals}",
                            color = palette.textPrimary
                        )
                        SummaryStatItem(
                            label = "ТЕЙК (TP)",
                            value = "${activeSummary.hitTpCount}",
                            color = palette.longGreen
                        )
                        SummaryStatItem(
                            label = "СТОП (SL)",
                            value = "${activeSummary.hitSlCount}",
                            color = palette.shortRed
                        )
                        SummaryStatItem(
                            label = "WIN RATE",
                            value = "${QuantSignalEngine.formatOneDec(activeSummary.winRatePercent)}%",
                            color = palette.cyanAccent
                        )
                        val pnlSign = if (activeSummary.cumulativePnlPercent >= 0) "+" else ""
                        SummaryStatItem(
                            label = "ИТОГ P&L",
                            value = "$pnlSign${QuantSignalEngine.formatOneDec(activeSummary.cumulativePnlPercent)}%",
                            color = if (activeSummary.cumulativePnlPercent >= 0) palette.longGreen else palette.shortRed
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoundedDirectionChip(
                            label = "Все тикеры (${summary.totalSignals})",
                            selected = selectedTickerFilter == null,
                            accentColor = palette.cyanAccent,
                            bgSelected = palette.cyanDim,
                            testTag = "history_filter_all",
                            onClick = { onSelectTickerFilter(null) }
                        )
                        QuantSignalEngine.TOP_MOEX_STOCKS.forEach { stock ->
                            val countForTicker = allHistory.count { it.ticker == stock.ticker }
                            if (countForTicker > 0 || selectedTickerFilter == stock.ticker) {
                                RoundedDirectionChip(
                                    label = "${stock.ticker} ($countForTicker)",
                                    selected = selectedTickerFilter == stock.ticker,
                                    accentColor = palette.cyanAccent,
                                    bgSelected = palette.cyanDim,
                                    testTag = "history_filter_${stock.ticker}",
                                    onClick = {
                                        onSelectTickerFilter(
                                            if (selectedTickerFilter == stock.ticker) null else stock.ticker
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredHistory.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.bgCard),
                    border = BorderStroke(1.dp, palette.borderCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = palette.cyanAccent,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "История сигналов пока пуста",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            items(filteredHistory, key = { it.id }, contentType = { "history_row" }) { item ->
                HistoryDetailCard(item = item)
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    color: Color
) {
    val palette = LocalAppPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            fontSize = 9.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = SpaceGroteskFontFamily,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun HistoryDetailCard(item: SignalHistoryEntity) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val outcome = TradeOutcome.fromCode(item.status)
    val isLong = item.signalType == "LONG"
    val stockMeta = QuantSignalEngine.TOP_MOEX_STOCKS.firstOrNull { it.ticker == item.ticker }
    val minStep = stockMeta?.defaultStep ?: 0.01

    val createdDate = remember(item.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    }

    val outcomeColor = when (outcome) {
        TradeOutcome.HIT_TP -> palette.longGreen
        TradeOutcome.HIT_SL -> palette.shortRed
        TradeOutcome.ACTIVE -> palette.cyanAccent
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(1.dp, outcomeColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.ticker,
                        fontFamily = SpaceGroteskFontFamily,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isLong) palette.longGreenDim else palette.shortRedDim
                    ) {
                        Text(
                            text = if (isLong) "▲ LONG" else "▼ SHORT",
                            fontFamily = SpaceGroteskFontFamily,
                            color = if (isLong) palette.longGreen else palette.shortRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = outcomeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, outcomeColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (outcome) {
                                TradeOutcome.HIT_TP -> Icons.Default.CheckCircle
                                TradeOutcome.HIT_SL -> Icons.Default.Close
                                TradeOutcome.ACTIVE -> Icons.Default.HourglassEmpty
                            },
                            contentDescription = null,
                            tint = outcomeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val pnlSign = if (item.pnlPercent >= 0) "+" else ""
                        Text(
                            text = when (outcome) {
                                TradeOutcome.HIT_TP -> "ТЕЙК ($pnlSign${QuantSignalEngine.formatOneDec(item.pnlPercent)}%)"
                                TradeOutcome.HIT_SL -> "СТОП ($pnlSign${QuantSignalEngine.formatOneDec(item.pnlPercent)}%)"
                                TradeOutcome.ACTIVE -> "В СДЕЛКЕ"
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            color = outcomeColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.bgElevated)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LevelMetricBox(
                    label = "ВХОД",
                    value = "${QuantSignalEngine.formatPrice(item.entryPrice, minStep)} ₽",
                    subValue = "Увер. ${item.confidence}%",
                    valueColor = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                LevelMetricBox(
                    label = "TP1 / TP2",
                    value = "${QuantSignalEngine.formatPrice(item.takeProfit2, minStep)} ₽",
                    subValue = "TP1: ${QuantSignalEngine.formatPrice(item.takeProfit1, minStep)}",
                    valueColor = palette.longGreen,
                    modifier = Modifier.weight(1f)
                )
                LevelMetricBox(
                    label = "СТОП (SL)",
                    value = "${QuantSignalEngine.formatPrice(item.stopLoss, minStep)} ₽",
                    subValue = if (item.exitPrice != null) "Выход: ${QuantSignalEngine.formatPrice(item.exitPrice, minStep)}" else "Активен",
                    valueColor = palette.shortRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = palette.borderCard)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Создан: $createdDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "TradingView ↗",
                    fontFamily = SpaceGroteskFontFamily,
                    color = palette.cyanAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(QuantSignalEngine.tradingViewUrl(item.ticker)))
                            )
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }
}
