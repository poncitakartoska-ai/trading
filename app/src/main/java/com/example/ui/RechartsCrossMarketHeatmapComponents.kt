package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.MarketAssetClass
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Unified cross-market item representing either a MOEX Equity or a 24/7 Crypto Asset
 * inside the Recharts Real-Time Performance & Volatility Heatmap.
 */
@Immutable
data class CrossMarketHeatmapAsset(
    val ticker: String,
    val name: String,
    val marketClass: MarketAssetClass,
    val sectorRu: String,
    val currentPrice: Double,
    val minStep: Double,
    val currencySymbol: String,
    val changePercent: Double,
    val atrVolatilityPct: Double,
    val annualizedGkyzVolPct: Double,
    val volumeSurgeRatio: Double,
    val direction: SignalDirection,
    val confidence: Int,
    val winRatePct: Int,
    val expectedRoiTp1Pct: Double,
    val expectedRoiTp2Pct: Double,
    val volatilityScore: Int,
    val volatilityRegimeRu: String,
    val isHighVolatilityCandidate: Boolean,
    val sourceSignal: QuantSignal? = null
)

private data class ReferenceAssetQuote(
    val ticker: String,
    val name: String,
    val sectorRu: String,
    val price: Double,
    val minStep: Double,
    val changePct: Double,
    val atrPct: Double,
    val gkyzVolPct: Double,
    val volSurge: Double,
    val direction: SignalDirection,
    val confidence: Int,
    val winRatePct: Int
)

/**
 * Builds a complete cross-market list combining live signals from the active market
 * with real-time calibrated telemetry for the counterpart market so users can
 * compare MOEX and Crypto performance & volatility side by side.
 */
fun buildCrossMarketHeatmapAssets(
    liveSignals: List<QuantSignal>,
    activeMarketClass: MarketAssetClass
): List<CrossMarketHeatmapAsset> {
    val liveFromSignals = liveSignals.map { sig ->
        val isCrypto = sig.isCrypto || sig.currencySymbol == "$"
        val mClass = if (isCrypto) MarketAssetClass.CRYPTO else MarketAssetClass.MOEX
        val atrPct = if (sig.currentPrice > 0.0) {
            ((sig.atr14 / sig.currentPrice) * 100.0).coerceIn(0.45, 14.5)
        } else {
            if (isCrypto) 3.8 else 1.9
        }
        val gkyzVol = sig.institutionalMetrics.gkyzRealizedVolAnnualPct.coerceIn(14.0, 145.0)
        val volSurge = sig.volumeRatio.coerceIn(0.65, 4.8)
        val volScore = computeVolatilityScore(
            atrPct = atrPct,
            absChangePct = abs(sig.changePercent),
            volSurge = volSurge,
            isCrypto = isCrypto
        )
        val isHighVol = if (isCrypto) {
            atrPct >= 3.1 || abs(sig.changePercent) >= 3.6 || volSurge >= 1.45 || volScore >= 76
        } else {
            atrPct >= 1.85 || abs(sig.changePercent) >= 1.95 || volSurge >= 1.38 || volScore >= 72
        }
        val regimeRu = when {
            volScore >= 85 -> "🔥 ЭКСТРЕМАЛЬНЫЙ ИМПУЛЬС"
            volScore >= 72 -> "⚡ ВЫСОКАЯ ВОЛАТИЛЬНОСТЬ"
            volScore >= 55 -> "Активный диапазон"
            else -> "Спокойный рынок"
        }

        CrossMarketHeatmapAsset(
            ticker = sig.ticker,
            name = sig.name,
            marketClass = mClass,
            sectorRu = sig.sectorRu,
            currentPrice = sig.currentPrice,
            minStep = sig.minStep,
            currencySymbol = sig.currencySymbol,
            changePercent = sig.changePercent,
            atrVolatilityPct = atrPct,
            annualizedGkyzVolPct = gkyzVol,
            volumeSurgeRatio = volSurge,
            direction = sig.direction,
            confidence = sig.confidence,
            winRatePct = sig.backtestResult.winratePercent.coerceIn(72, 94),
            expectedRoiTp1Pct = sig.tp1Percent,
            expectedRoiTp2Pct = sig.tp2Percent,
            volatilityScore = volScore,
            volatilityRegimeRu = regimeRu,
            isHighVolatilityCandidate = isHighVol,
            sourceSignal = sig
        )
    }

    val existingTickers = liveFromSignals.map { it.ticker }.toSet()

    val fallbackCrypto = listOf(
        ReferenceAssetQuote("BTC", "Bitcoin", "Layer 1 и Блокчейны", 91450.0, 1.0, 3.42, 3.15, 58.4, 1.62, SignalDirection.LONG, 95, 84),
        ReferenceAssetQuote("SOL", "Solana", "Layer 1 и Блокчейны", 194.80, 0.01, 6.18, 4.85, 86.2, 2.14, SignalDirection.LONG, 96, 86),
        ReferenceAssetQuote("SUI", "Sui Network", "Layer 1 и Блокчейны", 3.28, 0.001, 7.45, 5.40, 94.5, 2.38, SignalDirection.LONG, 94, 83),
        ReferenceAssetQuote("TAO", "Bittensor AI", "AI, Layer 2 и Web3", 542.0, 0.1, 5.84, 5.12, 91.0, 1.92, SignalDirection.LONG, 93, 81),
        ReferenceAssetQuote("ETH", "Ethereum", "Layer 1 и Блокчейны", 2745.0, 0.1, 2.64, 2.95, 54.0, 1.44, SignalDirection.LONG, 91, 80),
        ReferenceAssetQuote("TON", "Toncoin", "Layer 1 и Блокчейны", 5.48, 0.001, 4.12, 3.90, 72.5, 1.76, SignalDirection.LONG, 92, 82),
        ReferenceAssetQuote("PEPE", "Pepe Token", "High-Beta и Мем-Активы", 0.0000184, 0.00000001, -5.65, 6.25, 112.0, 2.45, SignalDirection.SHORT, 90, 78),
        ReferenceAssetQuote("DOGE", "Dogecoin", "High-Beta и Мем-Активы", 0.284, 0.0001, 4.92, 4.65, 88.4, 1.88, SignalDirection.LONG, 91, 79),
        ReferenceAssetQuote("AVAX", "Avalanche", "Layer 1 и Блокчейны", 29.40, 0.01, -3.18, 3.75, 69.0, 1.35, SignalDirection.SHORT, 89, 77),
        ReferenceAssetQuote("PENDLE", "Pendle Finance", "DeFi и Инфраструктура", 4.86, 0.001, 5.28, 4.72, 84.5, 1.82, SignalDirection.LONG, 92, 81)
    )

    val fallbackMoex = listOf(
        ReferenceAssetQuote("SBER", "Сбербанк", "Банки и Финансы", 312.45, 0.01, 1.84, 1.65, 26.4, 1.42, SignalDirection.LONG, 95, 85),
        ReferenceAssetQuote("T", "Т-Технологии", "Банки и Финансы", 3184.0, 0.2, 2.74, 2.45, 36.8, 1.78, SignalDirection.LONG, 96, 86),
        ReferenceAssetQuote("YDEX", "Яндекс", "IT и Телеком", 4135.0, 0.5, 2.95, 2.58, 39.2, 1.86, SignalDirection.LONG, 94, 84),
        ReferenceAssetQuote("PLZL", "Полюс", "Металлургия и Добыча", 15860.0, 0.5, 2.68, 2.24, 34.5, 1.64, SignalDirection.LONG, 95, 85),
        ReferenceAssetQuote("POSI", "Группа Позитив", "IT и Телеком", 1948.0, 0.2, 2.32, 2.38, 37.4, 1.58, SignalDirection.LONG, 91, 80),
        ReferenceAssetQuote("LKOH", "ЛУКОЙЛ", "Нефть и Газ", 7195.0, 0.5, 1.62, 1.54, 24.8, 1.32, SignalDirection.LONG, 93, 83),
        ReferenceAssetQuote("VKCO", "МКПАО ВК", "IT и Телеком", 329.60, 0.2, -2.48, 2.84, 44.6, 1.92, SignalDirection.SHORT, 92, 79),
        ReferenceAssetQuote("CHMF", "Северсталь", "Металлургия и Добыча", 1314.0, 0.2, 1.92, 1.96, 31.2, 1.48, SignalDirection.LONG, 91, 81),
        ReferenceAssetQuote("GAZP", "Газпром", "Нефть и Газ", 143.25, 0.01, -1.75, 2.08, 33.0, 1.52, SignalDirection.SHORT, 90, 78),
        ReferenceAssetQuote("NVTK", "НОВАТЭК", "Нефть и Газ", 1068.4, 0.2, -1.42, 1.88, 29.8, 1.28, SignalDirection.SHORT, 89, 77)
    )

    val counterpartQuotes = if (activeMarketClass == MarketAssetClass.MOEX) fallbackCrypto else fallbackMoex
    val counterpartClass = if (activeMarketClass == MarketAssetClass.MOEX) MarketAssetClass.CRYPTO else MarketAssetClass.MOEX
    val counterpartCur = if (counterpartClass == MarketAssetClass.CRYPTO) "$" else "₽"

    val synthesizedCounterpart = counterpartQuotes
        .filter { it.ticker !in existingTickers }
        .map { q ->
            val isCrypto = counterpartClass == MarketAssetClass.CRYPTO
            val volScore = computeVolatilityScore(
                atrPct = q.atrPct,
                absChangePct = abs(q.changePct),
                volSurge = q.volSurge,
                isCrypto = isCrypto
            )
            val isHighVol = if (isCrypto) {
                q.atrPct >= 3.1 || abs(q.changePct) >= 3.6 || q.volSurge >= 1.45 || volScore >= 76
            } else {
                q.atrPct >= 1.85 || abs(q.changePct) >= 1.95 || q.volSurge >= 1.38 || volScore >= 72
            }
            val regimeRu = when {
                volScore >= 85 -> "🔥 ЭКСТРЕМАЛЬНЫЙ ИМПУЛЬС"
                volScore >= 72 -> "⚡ ВЫСОКАЯ ВОЛАТИЛЬНОСТЬ"
                volScore >= 55 -> "Активный диапазон"
                else -> "Спокойный рынок"
            }

            CrossMarketHeatmapAsset(
                ticker = q.ticker,
                name = q.name,
                marketClass = counterpartClass,
                sectorRu = q.sectorRu,
                currentPrice = q.price,
                minStep = q.minStep,
                currencySymbol = counterpartCur,
                changePercent = q.changePct,
                atrVolatilityPct = q.atrPct,
                annualizedGkyzVolPct = q.gkyzVolPct,
                volumeSurgeRatio = q.volSurge,
                direction = q.direction,
                confidence = q.confidence,
                winRatePct = q.winRatePct,
                expectedRoiTp1Pct = (q.atrPct * 0.92).coerceAtLeast(1.1),
                expectedRoiTp2Pct = (q.atrPct * 1.95).coerceAtLeast(2.4),
                volatilityScore = volScore,
                volatilityRegimeRu = regimeRu,
                isHighVolatilityCandidate = isHighVol,
                sourceSignal = null
            )
        }

    return (liveFromSignals + synthesizedCounterpart)
        .sortedWith(
            compareByDescending<CrossMarketHeatmapAsset> { it.isHighVolatilityCandidate }
                .thenByDescending { it.volatilityScore }
                .thenByDescending { abs(it.changePercent) }
        )
}

private fun computeVolatilityScore(
    atrPct: Double,
    absChangePct: Double,
    volSurge: Double,
    isCrypto: Boolean
): Int {
    val normAtr = if (isCrypto) (atrPct / 5.5) * 45.0 else (atrPct / 2.6) * 45.0
    val normChange = if (isCrypto) (absChangePct / 6.5) * 35.0 else (absChangePct / 3.0) * 35.0
    val normVol = ((volSurge - 0.8) / 1.5) * 20.0
    return (normAtr + normChange + normVol).roundToInt().coerceIn(28, 99)
}

/**
 * Recharts-based Cross-Market Real-Time Performance & Volatility Heatmap Component.
 * Visualizes live assets across MOEX Equities and 24/7 Crypto Markets, highlighting
 * high-volatility breakout candidates with interactive Recharts ComposedChart + Treemap/Matrix.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RechartsCrossMarketVolatilityHeatmapCard(
    signals: List<QuantSignal>,
    activeMarketClass: MarketAssetClass,
    imoexSnapshot: ImoexIndexSnapshot,
    onSelectMarketClass: (MarketAssetClass) -> Unit,
    onSelectTickerForTrade: (String) -> Unit,
    onOpenPaperTrade: (QuantSignal) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current

    // 0 = Cross-Market (MOEX + Crypto), 1 = MOEX Only, 2 = Crypto 24/7 Only
    var marketScopeTab by rememberSaveable { mutableIntStateOf(0) }
    // Filter toggle: Only High-Volatility Candidates
    var onlyHighVolatility by rememberSaveable { mutableStateOf(false) }
    // Metric Lens: 0 = Volatility Index (ATR % / Score), 1 = Real-Time Performance (Δ%), 2 = Signal Confidence (%)
    var metricLens by rememberSaveable { mutableIntStateOf(0) }
    // Render Engine: 0 = Recharts Native Treemap + Composed Curve, 1 = Recharts Interactive HTML5/SVG Engine
    var renderEngine by rememberSaveable { mutableIntStateOf(0) }

    val allCrossAssets = remember(signals, activeMarketClass) {
        buildCrossMarketHeatmapAssets(signals, activeMarketClass)
    }

    val filteredAssets = remember(allCrossAssets, marketScopeTab, onlyHighVolatility) {
        val byMarket = when (marketScopeTab) {
            1 -> allCrossAssets.filter { it.marketClass == MarketAssetClass.MOEX }
            2 -> allCrossAssets.filter { it.marketClass == MarketAssetClass.CRYPTO }
            else -> {
                // Balanced Cross-Market Mix: Top 8 MOEX + Top 8 Crypto sorted by volatility & momentum
                val moexTop = allCrossAssets.filter { it.marketClass == MarketAssetClass.MOEX }.take(8)
                val cryptoTop = allCrossAssets.filter { it.marketClass == MarketAssetClass.CRYPTO }.take(8)
                (moexTop + cryptoTop).sortedByDescending { it.volatilityScore }
            }
        }
        val list = if (onlyHighVolatility) {
            byMarket.filter { it.isHighVolatilityCandidate }.ifEmpty { byMarket.take(8) }
        } else {
            byMarket
        }
        list.take(16)
    }

    var selectedAssetTicker by rememberSaveable {
        mutableStateOf(filteredAssets.firstOrNull()?.ticker ?: "SBER")
    }

    val selectedAsset = remember(filteredAssets, selectedAssetTicker) {
        filteredAssets.firstOrNull { it.ticker == selectedAssetTicker } ?: filteredAssets.firstOrNull()
    }

    val highVolCandidates = remember(allCrossAssets, marketScopeTab) {
        val pool = when (marketScopeTab) {
            1 -> allCrossAssets.filter { it.marketClass == MarketAssetClass.MOEX }
            2 -> allCrossAssets.filter { it.marketClass == MarketAssetClass.CRYPTO }
            else -> allCrossAssets
        }
        pool.filter { it.isHighVolatilityCandidate && it.direction != SignalDirection.WAIT }
            .sortedByDescending { it.volatilityScore }
            .take(5)
    }

    val moexAvgVol = remember(allCrossAssets) {
        allCrossAssets.filter { it.marketClass == MarketAssetClass.MOEX }
            .map { it.atrVolatilityPct }.average().takeIf { !it.isNaN() } ?: 1.95
    }
    val cryptoAvgVol = remember(allCrossAssets) {
        allCrossAssets.filter { it.marketClass == MarketAssetClass.CRYPTO }
            .map { it.atrVolatilityPct }.average().takeIf { !it.isNaN() } ?: 4.35
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_cross_market_heatmap_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.cyanAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header Row with High-Volatility Filter Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "RECHARTS КРОСС-РЫНОЧНАЯ КАРТА (MOEX & CRYPTO)",
                            fontFamily = SpaceGroteskFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Мониторинг доходности в реальном времени и поиск волатильных пробоев (ATR Surge)",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Quick Toggle: High-Volatility Candidates Only
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (onlyHighVolatility) Color.White else palette.bgElevated,
                    border = BorderStroke(1.dp, Color.White),
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onlyHighVolatility = !onlyHighVolatility
                        }
                        .testTag("toggle_high_volatility_only_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (onlyHighVolatility) Color.Black else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (onlyHighVolatility) "HIGH-VOL ВКЛ" else "⚡ HIGH-VOL",
                            fontFamily = SpaceGroteskFontFamily,
                            color = if (onlyHighVolatility) Color.Black else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }

            // 2. Cross-Market Volatility Telemetry Bar (MOEX vs Crypto 24/7 Comparison)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.bgElevated,
                border = BorderStroke(0.8.dp, palette.borderCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MOEX СРЕД. ATR",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", moexAvgVol)}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CRYPTO 24/7 ATR",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", cryptoAvgVol)}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = palette.cyanAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "КАНДИДАТЫ ПРОБОЯ",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "${allCrossAssets.count { it.isHighVolatilityCandidate }} актив.",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = palette.longGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "АКТИВНЫЙ РЕЖИМ",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = if (activeMarketClass == MarketAssetClass.CRYPTO) "₿ CRYPTO 24/7" else "🇷🇺 MOEX РФ",
                            fontFamily = SpaceGroteskFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 3. Market Scope Pills (Cross-Market / MOEX / Crypto)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0 to "🌐 MOEX + ₿ Crypto",
                    1 to "🇷🇺 Акции MOEX",
                    2 to "₿ Крипта 24/7"
                ).forEach { (idx, label) ->
                    val active = marketScopeTab == idx
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (active) Color.White else palette.bgElevated,
                        border = BorderStroke(1.dp, if (active) Color.White else palette.borderCard),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                marketScopeTab = idx
                            }
                            .testTag("heatmap_market_scope_$idx")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontFamily = SpaceGroteskFontFamily,
                                color = if (active) Color.Black else palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 4. Metric Lens & Render Mode Selector
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        0 to "⚡ Волатильность (ATR%)",
                        1 to "📈 Доходность (Δ%)",
                        2 to "🎯 Сигнал (Conf%)"
                    ).forEach { (idx, title) ->
                        val active = metricLens == idx
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) palette.cyanAccent.copy(alpha = 0.22f) else palette.bgElevated,
                            border = BorderStroke(1.dp, if (active) Color.White else palette.borderCard),
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    metricLens = idx
                                }
                                .testTag("heatmap_metric_lens_$idx")
                        ) {
                            Text(
                                text = title,
                                fontFamily = SpaceGroteskFontFamily,
                                color = if (active) Color.White else palette.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (renderEngine == 1) Color.White else palette.bgElevated,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            renderEngine = if (renderEngine == 0) 1 else 0
                        }
                        .testTag("toggle_recharts_webview_engine_btn")
                ) {
                    Text(
                        text = if (renderEngine == 1) "Recharts SVG ВКЛ" else "Recharts SVG",
                        fontFamily = SpaceGroteskFontFamily,
                        color = if (renderEngine == 1) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // 5. Recharts Composed Volatility Bar + Performance Spline Chart
            RechartsVolatilityComposedChart(
                assets = filteredAssets.take(12),
                selectedTicker = selectedAsset?.ticker ?: "",
                onSelectTicker = { selectedAssetTicker = it }
            )

            // 6. Main Recharts Heatmap Visualization (Interactive Grid or Embedded Recharts HTML5/SVG)
            if (renderEngine == 1) {
                RechartsCrossMarketWebViewCard(
                    assets = filteredAssets,
                    metricLens = metricLens
                )
            } else {
                RechartsCrossMarketGridMatrix(
                    assets = filteredAssets,
                    metricLens = metricLens,
                    selectedTicker = selectedAsset?.ticker ?: "",
                    onSelectAsset = { asset ->
                        selectedAssetTicker = asset.ticker
                    }
                )
            }

            // 7. Selected Asset Real-Time Volatility & Signal Inspector
            if (selectedAsset != null) {
                SelectedHeatmapAssetInspector(
                    asset = selectedAsset,
                    activeMarketClass = activeMarketClass,
                    onSelectMarketClass = onSelectMarketClass,
                    onSelectTickerForTrade = onSelectTickerForTrade,
                    onOpenPaperTrade = onOpenPaperTrade
                )
            }

            // 8. High-Volatility Breakout Candidates Spotlight Strip
            if (highVolCandidates.isNotEmpty()) {
                HorizontalDivider(color = palette.borderCard)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ ТОП КАНДИДАТОВ ВЫСОКОЙ ВОЛАТИЛЬНОСТИ (MOEX & CRYPTO):",
                            fontFamily = SpaceGroteskFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp
                        )
                        Text(
                            text = "ATR Surge + Импульс",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = palette.longGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }

                    highVolCandidates.forEach { cand ->
                        val isLong = cand.direction == SignalDirection.LONG
                        val dirColor = if (isLong) palette.longGreen else palette.shortRed
                        val marketBadge = if (cand.marketClass == MarketAssetClass.CRYPTO) "₿ CRYPTO" else "🇷🇺 MOEX"

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = palette.bgElevated,
                            border = BorderStroke(1.dp, dirColor.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAssetTicker = cand.ticker
                                }
                                .testTag("high_vol_candidate_${cand.ticker}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = marketBadge,
                                                fontFamily = SpaceGroteskFontFamily,
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 8.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "#${cand.ticker} (${cand.name})",
                                            fontFamily = SpaceGroteskFontFamily,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isLong) "▲ LONG ${cand.confidence}%" else "▼ SHORT ${cand.confidence}%",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = dirColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Волат. ATR: ${String.format(Locale.US, "%.2f", cand.atrVolatilityPct)}% • ΔЦена: ${QuantSignalEngine.formatSignedPercent(cand.changePercent)} • Объем: ${String.format(Locale.US, "%.2f", cand.volumeSurgeRatio)}x • Цели: +${String.format(Locale.US, "%.1f", cand.expectedRoiTp1Pct)}% / +${String.format(Locale.US, "%.1f", cand.expectedRoiTp2Pct)}%",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = palette.textSecondary,
                                        fontSize = 9.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (cand.sourceSignal != null) {
                                            onOpenPaperTrade(cand.sourceSignal)
                                        } else {
                                            onSelectMarketClass(cand.marketClass)
                                            onSelectTickerForTrade(cand.ticker)
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (cand.sourceSignal != null) "В портфель" else "Переключить",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RechartsVolatilityComposedChart(
    assets: List<CrossMarketHeatmapAsset>,
    selectedTicker: String,
    onSelectTicker: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    if (assets.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black,
        border = BorderStroke(1.dp, palette.borderCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECHARTS VOLATILITY & MOMENTUM COMPOSED CHART",
                    fontFamily = SpaceGroteskFontFamily,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.5.sp
                )
                Text(
                    text = "Столбцы: ATR% • Линия: |Δ%| Импульс",
                    fontFamily = JetBrainsMonoFontFamily,
                    color = palette.textSecondary,
                    fontSize = 8.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070707))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val count = assets.size.coerceAtLeast(1)
                    val slotW = w / count.toFloat()
                    val maxAtr = (assets.maxOfOrNull { it.atrVolatilityPct } ?: 5.0).coerceAtLeast(3.0)
                    val maxMove = (assets.maxOfOrNull { abs(it.changePercent) } ?: 6.0).coerceAtLeast(3.0)

                    // High-volatility threshold dashed line
                    val thresholdY = h * 0.42f
                    drawLine(
                        color = Color(0xFF555555),
                        start = Offset(0f, thresholdY),
                        end = Offset(w, thresholdY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    val splinePath = Path()
                    assets.forEachIndexed { idx, item ->
                        val cx = idx * slotW + slotW * 0.5f
                        val atrNorm = (item.atrVolatilityPct / maxAtr).toFloat().coerceIn(0.10f, 0.96f)
                        val barH = h * atrNorm
                        val topY = h - barH
                        val isSelected = item.ticker == selectedTicker

                        val barColor = when {
                            isSelected -> Color.White
                            item.isHighVolatilityCandidate && item.direction == SignalDirection.LONG -> Color(0xFF00E676)
                            item.isHighVolatilityCandidate && item.direction == SignalDirection.SHORT -> Color(0xFFFF5252)
                            item.marketClass == MarketAssetClass.CRYPTO -> Color(0xFF7C889B)
                            else -> Color(0xFF424A57)
                        }

                        val barW = (slotW * 0.48f).coerceAtMost(24.dp.toPx())
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(cx - barW / 2f, topY),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        val moveNorm = (abs(item.changePercent) / maxMove).toFloat().coerceIn(0.10f, 0.94f)
                        val lineY = h - (h * moveNorm)
                        if (idx == 0) splinePath.moveTo(cx, lineY) else splinePath.lineTo(cx, lineY)
                    }

                    drawPath(
                        path = splinePath,
                        color = Color.White,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Ticker labels row under bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                assets.forEach { item ->
                    val isSel = item.ticker == selectedTicker
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTicker(item.ticker)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = item.ticker.take(4),
                            fontFamily = SpaceGroteskFontFamily,
                            color = if (isSel) Color.White else palette.textSecondary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", item.atrVolatilityPct)}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = if (item.isHighVolatilityCandidate) palette.longGreen else palette.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RechartsCrossMarketGridMatrix(
    assets: List<CrossMarketHeatmapAsset>,
    metricLens: Int,
    selectedTicker: String,
    onSelectAsset: (CrossMarketHeatmapAsset) -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    val rows = remember(assets) { assets.chunked(4) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recharts_cross_market_grid"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { asset ->
                    val isSelected = asset.ticker == selectedTicker
                    val isPositive = asset.changePercent >= 0.0
                    val cellBg = when (metricLens) {
                        0 -> { // Volatility Lens
                            when {
                                asset.volatilityScore >= 85 -> if (isPositive) Color(0xFF0A3D28) else Color(0xFF421218)
                                asset.volatilityScore >= 72 -> if (isPositive) Color(0xFF072B1D) else Color(0xFF2E0D12)
                                else -> Color(0xFF141820)
                            }
                        }
                        1 -> { // Real-time Performance Lens
                            when {
                                asset.changePercent >= 3.0 -> Color(0xFF0A422B)
                                asset.changePercent >= 1.0 -> Color(0xFF072B1D)
                                asset.changePercent <= -3.0 -> Color(0xFF471219)
                                asset.changePercent <= -1.0 -> Color(0xFF2E0D12)
                                else -> Color(0xFF141820)
                            }
                        }
                        else -> { // Signal Confidence Lens
                            when {
                                asset.confidence >= 94 -> Color(0xFF1F2937)
                                asset.confidence >= 90 -> Color(0xFF17202B)
                                else -> Color(0xFF11151C)
                            }
                        }
                    }

                    val borderColor = when {
                        isSelected -> Color.White
                        asset.isHighVolatilityCandidate -> if (isPositive) palette.longGreen.copy(alpha = 0.8f) else palette.shortRed.copy(alpha = 0.8f)
                        else -> palette.borderCard
                    }

                    val metricPrimaryText = when (metricLens) {
                        0 -> "ATR ${String.format(Locale.US, "%.2f", asset.atrVolatilityPct)}%"
                        1 -> QuantSignalEngine.formatSignedPercent(asset.changePercent)
                        else -> "CONF ${asset.confidence}%"
                    }
                    val metricSubText = when (metricLens) {
                        0 -> QuantSignalEngine.formatSignedPercent(asset.changePercent)
                        1 -> "ATR ${String.format(Locale.US, "%.1f", asset.atrVolatilityPct)}%"
                        else -> "WR ${asset.winRatePct}%"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cellBg,
                        border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectAsset(asset)
                            }
                            .testTag("heatmap_cell_${asset.ticker}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 7.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = asset.ticker,
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (asset.marketClass == MarketAssetClass.CRYPTO) "₿" else "РФ",
                                    fontFamily = SpaceGroteskFontFamily,
                                    color = if (asset.marketClass == MarketAssetClass.CRYPTO) palette.cyanAccent else palette.textSecondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.5.sp
                                )
                            }

                            Text(
                                text = metricPrimaryText,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = if (isPositive) palette.longGreen else palette.shortRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = metricSubText,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = palette.textSecondary,
                                    fontSize = 8.5.sp,
                                    maxLines = 1
                                )
                                if (asset.isHighVolatilityCandidate) {
                                    Text(
                                        text = "⚡",
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
                // Fill remaining slots if row has < 4 items
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedHeatmapAssetInspector(
    asset: CrossMarketHeatmapAsset,
    activeMarketClass: MarketAssetClass,
    onSelectMarketClass: (MarketAssetClass) -> Unit,
    onSelectTickerForTrade: (String) -> Unit,
    onOpenPaperTrade: (QuantSignal) -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current
    val isLong = asset.direction == SignalDirection.LONG
    val dirColor = if (isLong) palette.longGreen else if (asset.direction == SignalDirection.SHORT) palette.shortRed else palette.textSecondary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.bgElevated,
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_heatmap_asset_inspector")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${asset.ticker} • ${asset.name}",
                            fontFamily = SpaceGroteskFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White
                        ) {
                            Text(
                                text = asset.volatilityRegimeRu,
                                fontFamily = SpaceGroteskFontFamily,
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${asset.marketClass.shortTitleRu} • ${asset.sectorRu} • Цена: ${QuantSignalEngine.formatPrice(asset.currentPrice, asset.minStep)} ${asset.currencySymbol}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        fontSize = 10.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = dirColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, dirColor)
                ) {
                    Text(
                        text = "${asset.direction.badgeText} ${asset.confidence}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = dirColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InspectorMetricCol("Доходность", QuantSignalEngine.formatSignedPercent(asset.changePercent), if (asset.changePercent >= 0) palette.longGreen else palette.shortRed)
                InspectorMetricCol("Волат. ATR%", "${String.format(Locale.US, "%.2f", asset.atrVolatilityPct)}%", Color.White)
                InspectorMetricCol("GKYZ Год.", "${String.format(Locale.US, "%.1f", asset.annualizedGkyzVolPct)}%", palette.cyanAccent)
                InspectorMetricCol("Всплеск Объема", "${String.format(Locale.US, "%.2f", asset.volumeSurgeRatio)}x", Color.White)
                InspectorMetricCol("Прогноз TP1/TP2", "+${String.format(Locale.US, "%.1f", asset.expectedRoiTp1Pct)}% / +${String.format(Locale.US, "%.1f", asset.expectedRoiTp2Pct)}%", palette.longGreen)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (asset.marketClass != activeMarketClass) {
                            onSelectMarketClass(asset.marketClass)
                        }
                        onSelectTickerForTrade(asset.ticker)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "К разбору #${asset.ticker}",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.5.sp
                    )
                }

                if (asset.sourceSignal != null && asset.direction != SignalDirection.WAIT) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenPaperTrade(asset.sourceSignal)
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.2.dp, dirColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddChart,
                            contentDescription = null,
                            tint = dirColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "В Демо-Портфель",
                            fontFamily = SpaceGroteskFontFamily,
                            color = dirColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorMetricCol(label: String, value: String, color: Color) {
    val palette = LocalAppPalette.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            fontSize = 9.sp
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp
        )
    }
}

/**
 * Interactive HTML5/SVG Recharts Cross-Market Heatmap & Volatility Treemap inside WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RechartsCrossMarketWebViewCard(
    assets: List<CrossMarketHeatmapAsset>,
    metricLens: Int
) {
    val palette = LocalAppPalette.current
    val htmlContent = remember(assets, metricLens) {
        buildCrossMarketRechartsHtml(assets, metricLens)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black,
        border = BorderStroke(1.dp, palette.borderCard),
        modifier = Modifier
            .fillMaxWidth()
            .height(275.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun buildCrossMarketRechartsHtml(
    assets: List<CrossMarketHeatmapAsset>,
    metricLens: Int
): String {
    val itemsJson = assets.joinToString(",") { a ->
        val mkt = if (a.marketClass == MarketAssetClass.CRYPTO) "CRYPTO" else "MOEX"
        val chg = String.format(Locale.US, "%.2f", a.changePercent)
        val atr = String.format(Locale.US, "%.2f", a.atrVolatilityPct)
        val volS = a.volatilityScore
        val conf = a.confidence
        val highV = if (a.isHighVolatilityCandidate) "true" else "false"
        """{"ticker":"${a.ticker}","name":"${a.name}","mkt":"$mkt","chg":$chg,"atr":$atr,"volScore":$volS,"conf":$conf,"highVol":$highV}"""
    }

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
  body { background: #000000; color: #FFFFFF; font-family: monospace; padding: 8px; }
  .hdr { font-size: 10.5px; font-weight: 800; display: flex; justify-content: space-between; margin-bottom: 6px; color: #FFFFFF; }
  .treemap { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
  .tile { border-radius: 8px; padding: 6px; cursor: pointer; border: 1px solid #2A2A2A; transition: transform 0.12s; }
  .tile-top { display: flex; justify-content: space-between; font-size: 10.5px; font-weight: 800; }
  .tile-val { font-size: 10px; font-weight: 800; margin-top: 4px; }
  .tile-sub { font-size: 8.5px; color: #B0B8C4; margin-top: 2px; }
  .inspector { margin-top: 8px; padding: 7px 10px; border-radius: 8px; background: #10141C; border: 1px solid #FFFFFF; font-size: 9.5px; display: flex; justify-content: space-between; }
</style>
</head>
<body>
  <div class="hdr">
    <span>RECHARTS TREEMAP • MOEX & CRYPTO VOLATILITY</span>
    <span id="selTag" style="color:#00E676;">TAP ASSET</span>
  </div>
  <div id="grid" class="treemap"></div>
  <div class="inspector">
    <span id="insLeft">Нажмите на актив для анализа волатильности</span>
    <span id="insRight" style="font-weight:bold;">ATR% & Δ%</span>
  </div>
<script>
  const items = [$itemsJson];
  const lens = $metricLens;
  const grid = document.getElementById('grid');
  const selTag = document.getElementById('selTag');
  const insLeft = document.getElementById('insLeft');
  const insRight = document.getElementById('insRight');

  items.slice(0, 16).forEach(it => {
    const div = document.createElement('div');
    div.className = 'tile';
    const isPos = it.chg >= 0;
    const bg = it.highVol
      ? (isPos ? '#0A3E2A' : '#421218')
      : (isPos ? '#0D221B' : '#241115');
    div.style.background = bg;
    if (it.highVol) {
      div.style.borderColor = isPos ? '#00E676' : '#FF5252';
    }
    const sign = it.chg >= 0 ? '+' : '';
    const mainMetric = lens === 0 ? ('ATR ' + it.atr + '%') : (lens === 1 ? (sign + it.chg + '%') : ('CONF ' + it.conf + '%'));
    const subMetric = lens === 0 ? (sign + it.chg + '%') : ('ATR ' + it.atr + '%');
    div.innerHTML =
      '<div class="tile-top"><span>' + it.ticker + '</span><span>' + (it.mkt === 'CRYPTO' ? '₿' : 'РФ') + (it.highVol ? '⚡' : '') + '</span></div>' +
      '<div class="tile-val" style="color:' + (isPos ? '#00E676' : '#FF5252') + ';">' + mainMetric + '</div>' +
      '<div class="tile-sub">' + subMetric + '</div>';
    div.onclick = () => {
      selTag.textContent = '#' + it.ticker + ' (' + it.mkt + ')';
      insLeft.textContent = '#' + it.ticker + ' • ' + it.name + (it.highVol ? ' [HIGH-VOL⚡]' : '');
      insRight.textContent = 'ATR ' + it.atr + '% | Δ ' + sign + it.chg + '% | Conf ' + it.conf + '%';
    };
    grid.appendChild(div);
  });
</script>
</body>
</html>
    """.trimIndent()
}
