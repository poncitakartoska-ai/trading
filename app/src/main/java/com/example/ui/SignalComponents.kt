package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PerformanceSummary
import com.example.data.SignalHistoryEntity
import com.example.data.SignalHistoryRepository
import com.example.data.TradeOutcome
import com.example.quant.MoexCandle
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.ui.theme.SignalLong
import com.example.ui.theme.SignalLongDim
import com.example.ui.theme.SignalShort
import com.example.ui.theme.SignalShortDim
import com.example.ui.theme.SignalWait
import com.example.ui.theme.SignalWaitDim
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalTextMuted
import com.example.ui.theme.TerminalTextPrimary
import com.example.ui.theme.TerminalTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun SignalCard(
    signal: QuantSignal,
    stockHistory: List<SignalHistoryEntity>,
    depositRub: Double,
    riskPercent: Double,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenFullStockHistory: () -> Unit,
    onNotifySignal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (signal.direction) {
        SignalDirection.LONG -> SignalLong
        SignalDirection.SHORT -> SignalShort
        SignalDirection.WAIT -> SignalWait
    }
    val badgeBg = when (signal.direction) {
        SignalDirection.LONG -> SignalLongDim
        SignalDirection.SHORT -> SignalShortDim
        SignalDirection.WAIT -> SignalWaitDim
    }

    val riskRub = max(0.0, depositRub * (riskPercent / 100.0))
    val stopDistanceRub = max(abs(signal.entryPrice - signal.stopLoss), signal.minStep)
    val rawShares = if (stopDistanceRub > 0.0) floor(riskRub / stopDistanceRub).toLong() else 0L
    val recommendedLots = if (signal.lotSize > 0) (rawShares / signal.lotSize) else rawShares
    val recommendedShares = recommendedLots * max(1, signal.lotSize)
    val positionValueRub = recommendedShares * signal.entryPrice

    val minBound = min(signal.stopLoss, signal.takeProfit)
    val maxBound = max(signal.stopLoss, signal.takeProfit)
    val span = max(maxBound - minBound, signal.minStep)
    val rawProgress = ((signal.currentPrice - minBound) / span).toFloat().coerceIn(0.04f, 0.96f)
    val progressTowardTp = if (signal.direction == SignalDirection.SHORT) {
        (1f - rawProgress).coerceIn(0.04f, 0.96f)
    } else {
        rawProgress
    }

    val stockSummary = SignalHistoryRepository.computeSummary(stockHistory)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("signal_card_${signal.ticker}")
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(
            width = if (signal.direction != SignalDirection.WAIT) 1.4.dp else 1.dp,
            color = if (signal.direction != SignalDirection.WAIT) accentColor.copy(alpha = 0.55f) else TerminalBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Ticker + Name + Live Price + Signal Badge
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeBg)
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = signal.ticker.take(4),
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = signal.ticker,
                                color = TerminalTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val changeSign = if (signal.changePercent >= 0) "+" else ""
                            val changeColor = if (signal.changePercent >= 0) SignalLong else SignalShort
                            Text(
                                text = "$changeSign${String.format(Locale.US, "%.2f", signal.changePercent)}%",
                                color = changeColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "${signal.name} • Объем ${(signal.valTodayRub / 1_000_000_000.0).let { String.format(Locale.US, "%.2f", it) }} млрд ₽",
                            color = TerminalTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${signal.direction.badgeText} • ${signal.confidence}%",
                                color = accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (signal.direction != SignalDirection.WAIT) {
                        IconButton(
                            onClick = onNotifySignal,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("notify_btn_${signal.ticker}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Отправить push-уведомление по сигналу ${signal.ticker}",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Three Large Monospace Blocks: ENTRY, TAKE PROFIT, STOP LOSS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceMetricBlock(
                    label = "ВХОД (ENTRY)",
                    priceText = "${QuantSignalEngine.formatPrice(signal.entryPrice, signal.minStep)} ₽",
                    subText = "Тек: ${QuantSignalEngine.formatPrice(signal.currentPrice, signal.minStep)} ₽",
                    valueColor = TerminalTextPrimary,
                    subColor = TerminalCyan,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricBlock(
                    label = "ТЕЙК-ПРОФИТ (TP)",
                    priceText = "${QuantSignalEngine.formatPrice(signal.takeProfit, signal.minStep)} ₽",
                    subText = "+${String.format(Locale.US, "%.2f", abs(signal.tpPercent))}%",
                    valueColor = SignalLong,
                    subColor = SignalLong,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricBlock(
                    label = "СТОП-ЛОСС (SL)",
                    priceText = "${QuantSignalEngine.formatPrice(signal.stopLoss, signal.minStep)} ₽",
                    subText = "-${String.format(Locale.US, "%.2f", abs(signal.slPercent))}%",
                    valueColor = SignalShort,
                    subColor = SignalShort,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual LinearProgressIndicator between Stop Loss and Take Profit
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SL: ${QuantSignalEngine.formatPrice(signal.stopLoss, signal.minStep)} ₽",
                        color = SignalShort,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Text(
                        text = signal.nearestLevelNote,
                        color = TerminalTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "TP: ${QuantSignalEngine.formatPrice(signal.takeProfit, signal.minStep)} ₽",
                        color = SignalLong,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressTowardTp },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = TerminalSurfaceElevated,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Analytics Row: RSI | ATR | R/R | Reason
            Surface(
                color = TerminalBg.copy(alpha = 0.75f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = "RSI: ${QuantSignalEngine.formatOneDec(signal.rsi14)} | ATR: ${QuantSignalEngine.formatPrice(signal.atr14, signal.minStep)} ₽ | R/R: 1:${QuantSignalEngine.formatOneDec(signal.riskRewardRatio)} | Vol: x${QuantSignalEngine.formatOneDec(signal.volumeRatio)}",
                        color = TerminalCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "EMA20: ${QuantSignalEngine.formatPrice(signal.ema20, signal.minStep)} ₽ • EMA50: ${QuantSignalEngine.formatPrice(signal.ema50, signal.minStep)} ₽ • ${signal.reason}",
                        color = TerminalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Automatic Risk & Lot Size Recommendation + Expandable History Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Рекомендуемый объем: $recommendedShares акций ($recommendedLots лотов)",
                        color = TerminalTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Риск на сделку: ${String.format(Locale.US, "%.0f", riskRub)} ₽ (${String.format(Locale.US, "%.1f", riskPercent)}%) • Сумма: ${String.format(Locale.US, "%.0f", positionValueRub)} ₽",
                        color = TerminalTextMuted,
                        fontSize = 10.sp
                    )
                }

                Surface(
                    color = TerminalSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.clickable(onClick = onToggleExpand)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "История сигналов ${signal.ticker}",
                            tint = TerminalCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "История (${stockHistory.size})",
                            color = TerminalTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            tint = TerminalTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable Signal History & Mini Chart for this Stock
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Mini Candlestick + EMA + TP/SL Chart
                    if (signal.recentCandles.size >= 8) {
                        Text(
                            text = "График свечей (10м) • Уровни Entry / TP / SL и EMA 20/50",
                            color = TerminalTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        MiniQuantCandleChart(
                            candles = signal.recentCandles,
                            entryPrice = signal.entryPrice,
                            takeProfit = signal.takeProfit,
                            stopLoss = signal.stopLoss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TerminalBg)
                                .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Per-Stock Signal History Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ЖУРНАЛ СИГНАЛОВ ${signal.ticker} (Win Rate: ${String.format(Locale.US, "%.0f", stockSummary.winRatePercent)}%)",
                            color = TerminalTextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "TP: ${stockSummary.hitTpCount} • SL: ${stockSummary.hitSlCount} • Актив: ${stockSummary.activeCount}",
                            color = TerminalCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (stockHistory.isEmpty()) {
                        Text(
                            text = "По бумаге ${signal.ticker} пока нет зафиксированных сигналов LONG/SHORT.",
                            color = TerminalTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        stockHistory.take(5).forEach { item ->
                            CompactHistoryRow(item = item, minStep = signal.minStep)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        OutlinedButton(
                            onClick = onOpenFullStockHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_history_${signal.ticker}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TerminalCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalCyan),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Открыть всю историю сигналов по ${signal.ticker} (${stockHistory.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceMetricBlock(
    label: String,
    priceText: String,
    subText: String,
    valueColor: Color,
    subColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = TerminalTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = priceText,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            maxLines = 1
        )
        Text(
            text = subText,
            color = subColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactHistoryRow(
    item: SignalHistoryEntity,
    minStep: Double
) {
    val isLong = item.signalType == SignalDirection.LONG.name
    val dirColor = if (isLong) SignalLong else SignalShort
    val outcome = TradeOutcome.fromCode(item.status)
    val (outcomeColor, outcomeBg, outcomeIcon) = when (outcome) {
        TradeOutcome.HIT_TP -> Triple(SignalLong, SignalLongDim, Icons.Default.CheckCircle)
        TradeOutcome.HIT_SL -> Triple(SignalShort, SignalShortDim, Icons.Default.Close)
        TradeOutcome.ACTIVE -> Triple(SignalWait, SignalWaitDim, Icons.Default.Schedule)
    }
    val timeFmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(item.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalBg.copy(alpha = 0.85f))
            .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLong) "🟢 LONG" else "🔴 SHORT",
                    color = dirColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeFmt,
                    color = TerminalTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Вход: ${QuantSignalEngine.formatPrice(item.entryPrice, minStep)} ₽ → TP: ${QuantSignalEngine.formatPrice(item.takeProfit, minStep)} ₽ | SL: ${QuantSignalEngine.formatPrice(item.stopLoss, minStep)} ₽",
                color = TerminalTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Surface(
            color = outcomeBg,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, outcomeColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = outcomeIcon,
                    contentDescription = null,
                    tint = outcomeColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val pnlSuffix = if (outcome != TradeOutcome.ACTIVE) {
                    " (${QuantSignalEngine.formatSignedPercent(item.pnlPercent)})"
                } else ""
                Text(
                    text = "${outcome.labelRu}$pnlSuffix",
                    color = outcomeColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun MiniQuantCandleChart(
    candles: List<MoexCandle>,
    entryPrice: Double,
    takeProfit: Double,
    stopLoss: Double,
    modifier: Modifier = Modifier
) {
    val ema20 = QuantSignalEngine.calculateEmaSeries(candles.map { it.close }, 20)
    Canvas(modifier = modifier) {
        if (candles.isEmpty()) return@Canvas
        val allPrices = candles.flatMap { listOf(it.high, it.low) } + listOf(entryPrice, takeProfit, stopLoss)
        val minP = allPrices.minOrNull() ?: 0.0
        val maxP = allPrices.maxOrNull() ?: 1.0
        val range = max(maxP - minP, 1e-4)

        fun yFor(price: Double): Float {
            val norm = ((price - minP) / range).toFloat().coerceIn(0f, 1f)
            return size.height * (1f - norm)
        }

        // Draw TP, Entry, SL horizontal reference lines
        val tpY = yFor(takeProfit)
        val slY = yFor(stopLoss)
        val entryY = yFor(entryPrice)

        drawLine(
            color = SignalLong.copy(alpha = 0.45f),
            start = Offset(0f, tpY),
            end = Offset(size.width, tpY),
            strokeWidth = 1.5f
        )
        drawLine(
            color = TerminalCyan.copy(alpha = 0.45f),
            start = Offset(0f, entryY),
            end = Offset(size.width, entryY),
            strokeWidth = 1.5f
        )
        drawLine(
            color = SignalShort.copy(alpha = 0.45f),
            start = Offset(0f, slY),
            end = Offset(size.width, slY),
            strokeWidth = 1.5f
        )

        val stepX = size.width / max(candles.size, 1).toFloat()
        val bodyWidth = max(stepX * 0.58f, 3f)

        candles.forEachIndexed { idx, c ->
            val cx = idx * stepX + stepX / 2f
            val highY = yFor(c.high)
            val lowY = yFor(c.low)
            val openY = yFor(c.open)
            val closeY = yFor(c.close)
            val isBull = c.close >= c.open
            val color = if (isBull) SignalLong else SignalShort

            drawLine(
                color = color,
                start = Offset(cx, highY),
                end = Offset(cx, lowY),
                strokeWidth = 1.5f
            )
            val topY = min(openY, closeY)
            val boxH = max(abs(closeY - openY), 2.5f)
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, topY),
                size = Size(bodyWidth, boxH)
            )
        }

        // Draw EMA 20 curve
        if (ema20.size == candles.size) {
            val path = Path()
            ema20.forEachIndexed { idx, emaVal ->
                val cx = idx * stepX + stepX / 2f
                val cy = yFor(emaVal)
                if (idx == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            }
            drawPath(
                path = path,
                color = TerminalCyan,
                style = Stroke(width = 2.2f)
            )
        }
    }
}

@Composable
fun SignalHistoryScreen(
    allHistory: List<SignalHistoryEntity>,
    summary: PerformanceSummary,
    outcomeFilter: HistoryOutcomeFilter,
    selectedTicker: String?,
    onSelectOutcomeFilter: (HistoryOutcomeFilter) -> Unit,
    onSelectTicker: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredList = allHistory.filter { item ->
        val matchesTicker = selectedTicker == null || item.ticker == selectedTicker
        val matchesOutcome = when (outcomeFilter) {
            HistoryOutcomeFilter.ALL -> true
            HistoryOutcomeFilter.HIT_TP -> item.status == TradeOutcome.HIT_TP.code
            HistoryOutcomeFilter.HIT_SL -> item.status == TradeOutcome.HIT_SL.code
            HistoryOutcomeFilter.ACTIVE -> item.status == TradeOutcome.ACTIVE.code
        }
        matchesTicker && matchesOutcome
    }

    val displayedSummary = if (selectedTicker != null) {
        SignalHistoryRepository.computeSummary(allHistory.filter { it.ticker == selectedTicker })
    } else {
        summary
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("signal_history_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Performance Summary Dashboard Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (selectedTicker != null) {
                                    "СТАТИСТИКА СИГНАЛОВ • $selectedTicker"
                                } else {
                                    "СТАТИСТИКА ТОРГОВЫХ СИГНАЛОВ MOEX"
                                },
                                color = TerminalTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Автоматический трекинг достижения Take Profit и Stop Loss",
                                color = TerminalTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            color = SignalLongDim,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SignalLong.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Win Rate: ${String.format(Locale.US, "%.1f", displayedSummary.winRatePercent)}%",
                                color = SignalLong,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatBox(
                            label = "ВСЕГО",
                            value = "${displayedSummary.totalSignals}",
                            color = TerminalTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatBox(
                            label = "ТЕЙК (TP)",
                            value = "${displayedSummary.hitTpCount}",
                            color = SignalLong,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatBox(
                            label = "СТОП (SL)",
                            value = "${displayedSummary.hitSlCount}",
                            color = SignalShort,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatBox(
                            label = "В СДЕЛКЕ",
                            value = "${displayedSummary.activeCount}",
                            color = SignalWait,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Outcome Filter Chips + Ticker Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryOutcomeFilter.entries.forEach { filter ->
                        val selected = outcomeFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectOutcomeFilter(filter) },
                            label = {
                                Text(
                                    text = filter.labelRu,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TerminalCyan.copy(alpha = 0.22f),
                                selectedLabelColor = TerminalCyan,
                                containerColor = TerminalSurface,
                                labelColor = TerminalTextSecondary
                            )
                        )
                    }
                }

                // Stock Ticker Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTicker == null,
                        onClick = { onSelectTicker(null) },
                        label = { Text("Все тикеры (15)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalLongDim,
                            selectedLabelColor = SignalLong,
                            containerColor = TerminalSurface,
                            labelColor = TerminalTextSecondary
                        )
                    )
                    QuantSignalEngine.TOP_MOEX_STOCKS.forEach { stock ->
                        val isSelected = selectedTicker == stock.ticker
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onSelectTicker(if (isSelected) null else stock.ticker)
                            },
                            label = {
                                Text(
                                    text = stock.ticker,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SignalLongDim,
                                selectedLabelColor = SignalLong,
                                containerColor = TerminalSurface,
                                labelColor = TerminalTextSecondary
                            )
                        )
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TerminalTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Нет сигналов по выбранному фильтру",
                            color = TerminalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "При генерации новых сигналов LONG/SHORT они автоматически сохраняются в журнал истории.",
                            color = TerminalTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { historyItem ->
                SignalHistoryDetailCard(item = historyItem)
            }
        }
    }
}

@Composable
private fun SummaryStatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TerminalTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun SignalHistoryDetailCard(item: SignalHistoryEntity) {
    val isLong = item.signalType == SignalDirection.LONG.name
    val dirColor = if (isLong) SignalLong else SignalShort
    val dirBg = if (isLong) SignalLongDim else SignalShortDim
    val outcome = TradeOutcome.fromCode(item.status)
    val (outcomeColor, outcomeBg) = when (outcome) {
        TradeOutcome.HIT_TP -> SignalLong to SignalLongDim
        TradeOutcome.HIT_SL -> SignalShort to SignalShortDim
        TradeOutcome.ACTIVE -> SignalWait to SignalWaitDim
    }
    val step = QuantSignalEngine.TOP_MOEX_STOCKS.firstOrNull { it.ticker == item.ticker }?.defaultStep ?: 0.01
    val createdTimeStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    val resolvedTimeStr = item.resolvedAt?.let {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, outcomeColor.copy(alpha = 0.4f)),
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
                    Surface(
                        color = dirBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, dirColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isLong) "🟢 LONG" else "🔴 SHORT",
                            color = dirColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.ticker} • ${item.stockName}",
                        color = TerminalTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Surface(
                    color = outcomeBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, outcomeColor.copy(alpha = 0.6f))
                ) {
                    val outcomeLabel = when (outcome) {
                        TradeOutcome.HIT_TP -> "✅ TP ДОСТИГНУТ (${QuantSignalEngine.formatSignedPercent(item.pnlPercent)})"
                        TradeOutcome.HIT_SL -> "❌ SL СРАБОТАЛ (${QuantSignalEngine.formatSignedPercent(item.pnlPercent)})"
                        TradeOutcome.ACTIVE -> "⏳ В СДЕЛКЕ"
                    }
                    Text(
                        text = outcomeLabel,
                        color = outcomeColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceMetricBlock(
                    label = "ВХОД (ENTRY)",
                    priceText = "${QuantSignalEngine.formatPrice(item.entryPrice, step)} ₽",
                    subText = "Сила: ${item.confidence}%",
                    valueColor = TerminalTextPrimary,
                    subColor = TerminalCyan,
                    modifier = Modifier.weight(1f)
                )
                val tpPct = abs(item.takeProfit - item.entryPrice) / item.entryPrice * 100.0
                PriceMetricBlock(
                    label = "ТЕЙК-ПРОФИТ (TP)",
                    priceText = "${QuantSignalEngine.formatPrice(item.takeProfit, step)} ₽",
                    subText = "+${String.format(Locale.US, "%.2f", tpPct)}%",
                    valueColor = SignalLong,
                    subColor = SignalLong,
                    modifier = Modifier.weight(1f)
                )
                val slPct = abs(item.stopLoss - item.entryPrice) / item.entryPrice * 100.0
                PriceMetricBlock(
                    label = "СТОП-ЛОСС (SL)",
                    priceText = "${QuantSignalEngine.formatPrice(item.stopLoss, step)} ₽",
                    subText = "-${String.format(Locale.US, "%.2f", slPct)}%",
                    valueColor = SignalShort,
                    subColor = SignalShort,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сигнал: $createdTimeStr" + (resolvedTimeStr?.let { " • Закрыт: $it" } ?: ""),
                    color = TerminalTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = "RSI: ${QuantSignalEngine.formatOneDec(item.rsi)} • ATR: ${QuantSignalEngine.formatPrice(item.atr, step)} ₽",
                    color = TerminalCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.reason,
                color = TerminalTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
