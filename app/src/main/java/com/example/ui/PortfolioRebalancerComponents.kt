package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PaperPortfolioSummary
import com.example.data.PaperTradeEntity
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.ui.theme.LocalAppPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * User-selectable Risk Tolerance Level for the Quantitative Portfolio Rebalancing Engine.
 */
@Immutable
enum class RiskToleranceLevel(
    val code: String,
    val titleRu: String,
    val subtitleRu: String,
    val baseEquityAllocationPct: Double,
    val baseCashReservePct: Double,
    val maxSinglePositionPct: Double,
    val maxPositionsCount: Int
) {
    CONSERVATIVE(
        code = "CONSERVATIVE",
        titleRu = "Консервативный",
        subtitleRu = "Защита капитала • Кэш 45–70% • Голубые фишки",
        baseEquityAllocationPct = 50.0,
        baseCashReservePct = 50.0,
        maxSinglePositionPct = 14.0,
        maxPositionsCount = 4
    ),
    MODERATE(
        code = "MODERATE",
        titleRu = "Сбалансированный",
        subtitleRu = "Risk-Parity по ATR • Кэш 25–45% • 5 секторов",
        baseEquityAllocationPct = 70.0,
        baseCashReservePct = 30.0,
        maxSinglePositionPct = 18.0,
        maxPositionsCount = 5
    ),
    AGGRESSIVE(
        code = "AGGRESSIVE",
        titleRu = "Агрессивный",
        subtitleRu = "Макс. Альфа и Импульс • Кэш 12–25% • Топ-6 идей",
        baseEquityAllocationPct = 86.0,
        baseCashReservePct = 14.0,
        maxSinglePositionPct = 24.0,
        maxPositionsCount = 6
    )
}

@Immutable
enum class RebalanceActionType(val code: String, val badgeRu: String) {
    BUY_NEW("BUY_NEW", "▲ ОТКРЫТЬ"),
    INCREASE("INCREASE", "+ ДОКУПИТЬ"),
    REDUCE("REDUCE", "- СОКРАТИТЬ"),
    CLOSE_EXIT("CLOSE_EXIT", "▼ ЗАКРЫТЬ"),
    HOLD("HOLD", "• ДЕРЖАТЬ")
}

@Immutable
data class RebalanceRecommendation(
    val ticker: String,
    val stockName: String,
    val sectorRu: String,
    val direction: String,
    val actionType: RebalanceActionType,
    val currentWeightPct: Double,
    val targetWeightPct: Double,
    val currentValueRub: Double,
    val targetValueRub: Double,
    val deltaValueRub: Double,
    val currentLots: Int,
    val targetLots: Int,
    val deltaLots: Int,
    val lotSize: Int,
    val currentPrice: Double,
    val minStep: Double,
    val rationaleRu: String,
    val signalRef: QuantSignal?,
    val existingTradeRef: PaperTradeEntity?
)

@Immutable
data class SectorAllocationSlice(
    val sectorNameRu: String,
    val currentWeightPct: Double,
    val targetWeightPct: Double,
    val colorHex: Long
)

@Immutable
data class PortfolioRebalancePlan(
    val riskTolerance: RiskToleranceLevel,
    val marketEnvironmentTitleRu: String,
    val marketEnvironmentDiagnosisRu: String,
    val fearAndGreedScore: Int,
    val totalEquityRub: Double,
    val currentCashWeightPct: Double,
    val targetCashWeightPct: Double,
    val currentCashRub: Double,
    val targetCashRub: Double,
    val alignmentScore: Int,
    val totalDriftPct: Double,
    val estimatedDailyVar95Rub: Double,
    val expectedPortfolioProfitFactor: Double,
    val sectorSlices: List<SectorAllocationSlice>,
    val recommendations: List<RebalanceRecommendation>
) {
    val actionableTradesCount: Int
        get() = recommendations.count { it.actionType != RebalanceActionType.HOLD && abs(it.deltaLots) > 0 }
}

/**
 * Pure quantitative portfolio optimization engine combining:
 * 1. Market Regime & IMOEX Momentum scaling (Dynamic Cash Buffer)
 * 2. Fear & Greed Sentiment adjustment
 * 3. Inverse-Volatility (1 / ATR%) Risk-Parity + Multi-Factor Alpha Score weighting
 * 4. Sector Diversification caps & Lot-exact MOEX execution calculation
 */
object PortfolioRebalanceEngine {

    fun computeOptimalRebalancePlan(
        totalEquityRub: Double,
        availableCashRub: Double,
        openTrades: List<PaperTradeEntity>,
        liveSignals: List<QuantSignal>,
        imoexSnapshot: ImoexIndexSnapshot,
        newsSentimentIndex: Int,
        riskTolerance: RiskToleranceLevel
    ): PortfolioRebalancePlan {
        val safeEquity = totalEquityRub.coerceAtLeast(100_000.0)
        val fgMetrics = computeMarketFearAndGreed(
            signals = liveSignals,
            imoexSnapshot = imoexSnapshot,
            newsSentimentIndex = newsSentimentIndex
        )
        val fgScore = fgMetrics.overallScore

        // 1. Determine Market Regime Modifier for Equity vs Cash (LQDT) allocation
        val (marketRegimeModifier, envTitleRu, envDiagnosisRu) = when {
            imoexSnapshot.isLongBlocked || imoexSnapshot.changePercent <= -1.0 -> Triple(
                -22.0,
                "ЗАЩИТНЫЙ РЕЖИМ (ДАВЛЕНИЕ IMOEX ${QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)})",
                "Рынок под давлением продавцов: алгоритм увеличивает долю защитного кэша/LQDT на +22% и отбирает только хедж-сетапы или бумаги с сильной перепроданностью."
            )
            fgScore <= 32 -> Triple(
                -14.0,
                "ОСТОРОЖНЫЙ РЕЖИМ (СТРАХ НА РЫНКЕ: $fgScore/100)",
                "Повышенная нервозность участников: доля акций снижена на 14% в пользу безрискового резерва ликвидности, вес позиций выровнен обратно пропорционально волатильности ATR."
            )
            imoexSnapshot.changePercent >= 0.8 && fgScore in 55..82 -> Triple(
                +6.0,
                "ИМПУЛЬСНЫЙ БЫЧИЙ РЕЖИМ (IMOEX ${QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)})",
                "Уверенный приток капитала в акции РФ: повышен вес бумаг-лидеров с относительной силой лучше индекса (RS > 0) и высоким Profit Factor."
            )
            fgScore >= 83 -> Triple(
                -8.0,
                "ПЕРЕГРЕТЫЙ РЫНОК (ЭКСТРЕМАЛЬНАЯ ЖАДНОСТЬ: $fgScore/100)",
                "Рынок в зоне эйфории: ребалансировщик рекомендует частичную фиксацию перегретых позиций (+8% в кэш) для защиты накопленной прибыли."
            )
            else -> Triple(
                0.0,
                "СБАЛАНСИРОВАННЫЙ РЫНОК (RISK-PARITY РЕЖИМ)",
                "Нейтральная рыночная среда (IMOEX ${QuantSignalEngine.formatSignedPercent(imoexSnapshot.changePercent)}, F&G $fgScore/100): оптимально распределение по модели Risk-Parity между лидерами 4 секторов."
            )
        }

        val targetEquityBudgetPct = (riskTolerance.baseEquityAllocationPct + marketRegimeModifier)
            .coerceIn(20.0, 92.0)
        val targetCashWeightPct = (100.0 - targetEquityBudgetPct).coerceIn(8.0, 80.0)

        // 2. Rank candidate stocks from liveSignals based on risk tolerance & market environment
        val eligibleCandidates = liveSignals.filter { sig ->
            val notHighRiskNews = !sig.hasHighRiskNewsWarning
            val matchesRiskTier = when (riskTolerance) {
                RiskToleranceLevel.CONSERVATIVE -> sig.isBlueChip && sig.direction != SignalDirection.SHORT
                RiskToleranceLevel.MODERATE -> sig.isBlueChip || sig.confidence >= 76
                RiskToleranceLevel.AGGRESSIVE -> true
            }
            notHighRiskNews && matchesRiskTier
        }.sortedByDescending { sig ->
            val dirBonus = when {
                sig.direction == SignalDirection.LONG && !imoexSnapshot.isLongBlocked -> 28.0
                sig.direction == SignalDirection.SHORT && riskTolerance == RiskToleranceLevel.AGGRESSIVE -> 24.0
                sig.direction == SignalDirection.SHORT && imoexSnapshot.isLongBlocked -> 26.0
                else -> 0.0
            }
            val pfBonus = (sig.backtestResult.profitFactor.coerceIn(0.5, 3.5)) * 12.0
            val rsBonus = sig.relativeStrengthVsImoex.coerceIn(-3.0, 3.0) * 4.5
            val blueChipBonus = if (sig.isBlueChip) 8.0 else 0.0
            sig.confidence + dirBonus + pfBonus + rsBonus + blueChipBonus
        }

        // Select diversified top portfolio constituents (at most 2 per sector so portfolio is diversified)
        val selectedTargets = mutableListOf<QuantSignal>()
        val sectorCounts = mutableMapOf<String, Int>()
        val maxPerSector = if (riskTolerance == RiskToleranceLevel.CONSERVATIVE) 1 else 2

        for (candidate in eligibleCandidates) {
            if (selectedTargets.size >= riskTolerance.maxPositionsCount) break
            val sec = candidate.sectorRu.ifBlank { "Прочие" }
            val currentSecCount = sectorCounts.getOrDefault(sec, 0)
            if (currentSecCount < maxPerSector) {
                selectedTargets.add(candidate)
                sectorCounts[sec] = currentSecCount + 1
            }
        }

        // Compute Inverse-ATR Risk-Parity * Alpha Score weights for selected targets
        val rawScores = selectedTargets.associateWith { sig ->
            val atrPct = if (sig.currentPrice > 0) {
                ((sig.atr14 / sig.currentPrice) * 100.0).coerceIn(0.45, 6.0)
            } else {
                1.5
            }
            val inverseVolWeight = 1.0 / atrPct
            val alphaFactor = (sig.confidence / 100.0) * (sig.backtestResult.profitFactor.coerceIn(0.8, 2.8))
            when (riskTolerance) {
                RiskToleranceLevel.CONSERVATIVE -> inverseVolWeight * 0.75 + alphaFactor * 0.25
                RiskToleranceLevel.MODERATE -> inverseVolWeight * 0.55 + alphaFactor * 0.45
                RiskToleranceLevel.AGGRESSIVE -> inverseVolWeight * 0.30 + alphaFactor * 0.70
            }
        }

        val totalRawScore = rawScores.values.sum().coerceAtLeast(0.0001)
        val targetWeightsByTicker = mutableMapOf<String, Double>()
        selectedTargets.forEach { sig ->
            val uncappedWeight = (rawScores.getOrDefault(sig, 1.0) / totalRawScore) * targetEquityBudgetPct
            targetWeightsByTicker[sig.ticker] = uncappedWeight.coerceAtMost(riskTolerance.maxSinglePositionPct)
        }

        // Recalculate actual cash weight after single-position caps
        val actualTargetEquitySumPct = targetWeightsByTicker.values.sum().coerceIn(0.0, 95.0)
        val finalTargetCashPct = (100.0 - actualTargetEquitySumPct).coerceIn(5.0, 100.0)

        // Map existing open trades by ticker
        val openTradesByTicker = openTrades.groupBy { it.ticker }
        val signalByTicker = liveSignals.associateBy { it.ticker }
        val allInvolvedTickers = (targetWeightsByTicker.keys + openTradesByTicker.keys).distinct()

        val recommendations = mutableListOf<RebalanceRecommendation>()
        var totalAbsDriftPct = 0.0

        for (ticker in allInvolvedTickers) {
            val tickerTrades = openTradesByTicker[ticker].orEmpty()
            val primaryTrade = tickerTrades.firstOrNull()
            val sig = signalByTicker[ticker]

            val livePrice = sig?.currentPrice ?: primaryTrade?.currentPrice ?: 100.0
            val lotSize = sig?.lotSize ?: primaryTrade?.lotSize ?: 1
            val minStep = sig?.minStep ?: primaryTrade?.minStep ?: 0.01
            val stockName = sig?.name ?: primaryTrade?.stockName ?: ticker
            val sectorRu = sig?.sectorRu ?: "Акции РФ"

            val currentLots = tickerTrades.sumOf { it.lots }
            val currentValRub = currentLots * lotSize * livePrice
            val currentWeightPct = ((currentValRub / safeEquity) * 100.0).coerceIn(0.0, 100.0)

            val targetWeightPct = targetWeightsByTicker.getOrDefault(ticker, 0.0)
            val targetValRub = safeEquity * (targetWeightPct / 100.0)
            val oneLotCostRub = (livePrice * lotSize).coerceAtLeast(1.0)
            val targetLots = max(0, floor(targetValRub / oneLotCostRub).toInt())

            val deltaLots = targetLots - currentLots
            val deltaValRub = deltaLots * oneLotCostRub
            val weightDiff = targetWeightPct - currentWeightPct
            totalAbsDriftPct += abs(weightDiff)

            val directionStr = when {
                sig != null && sig.direction == SignalDirection.SHORT &&
                    (riskTolerance == RiskToleranceLevel.AGGRESSIVE || imoexSnapshot.isLongBlocked) -> "SHORT"
                primaryTrade != null && targetLots > 0 -> primaryTrade.direction
                else -> "LONG"
            }

            val actionType = when {
                currentLots == 0 && targetLots > 0 -> RebalanceActionType.BUY_NEW
                targetLots == 0 && currentLots > 0 -> RebalanceActionType.CLOSE_EXIT
                deltaLots > 0 && abs(weightDiff) >= 1.5 -> RebalanceActionType.INCREASE
                deltaLots < 0 && abs(weightDiff) >= 1.5 -> RebalanceActionType.REDUCE
                else -> RebalanceActionType.HOLD
            }

            val rationale = when (actionType) {
                RebalanceActionType.BUY_NEW -> {
                    val pfStr = sig?.let { String.format(Locale.US, "%.2f", it.backtestResult.profitFactor) } ?: "1.80"
                    val conf = sig?.confidence ?: 80
                    "Топ квант-отбора ($sectorRu) • Conf $conf% • PF $pfStr • Целевая доля ${String.format(Locale.US, "%.1f", targetWeightPct)}% по Risk-Parity"
                }
                RebalanceActionType.INCREASE -> {
                    "Недовес в портфеле (${String.format(Locale.US, "%.1f", currentWeightPct)}% → ${String.format(Locale.US, "%.1f", targetWeightPct)}%): усиление позиции по лидеру сектора $sectorRu"
                }
                RebalanceActionType.REDUCE -> {
                    "Снятие перевеса риска (${String.format(Locale.US, "%.1f", currentWeightPct)}% → ${String.format(Locale.US, "%.1f", targetWeightPct)}%): фиксация части лотов для соблюдения лимита волатильности"
                }
                RebalanceActionType.CLOSE_EXIT -> {
                    "Выход из модели ребалансировки: бумага уступает лидерам по квант-рейтингу / освобождение кэша"
                }
                RebalanceActionType.HOLD -> {
                    "Оптимальный вес в рамках целевого коридора (${String.format(Locale.US, "%.1f", currentWeightPct)}% капитала)"
                }
            }

            recommendations.add(
                RebalanceRecommendation(
                    ticker = ticker,
                    stockName = stockName,
                    sectorRu = sectorRu,
                    direction = directionStr,
                    actionType = actionType,
                    currentWeightPct = currentWeightPct,
                    targetWeightPct = targetWeightPct,
                    currentValueRub = currentValRub,
                    targetValueRub = targetLots * oneLotCostRub,
                    deltaValueRub = deltaValRub,
                    currentLots = currentLots,
                    targetLots = targetLots,
                    deltaLots = deltaLots,
                    lotSize = lotSize,
                    currentPrice = livePrice,
                    minStep = minStep,
                    rationaleRu = rationale,
                    signalRef = sig,
                    existingTradeRef = primaryTrade
                )
            )
        }

        val sortedRecommendations = recommendations.sortedWith(
            compareBy<RebalanceRecommendation> {
                when (it.actionType) {
                    RebalanceActionType.CLOSE_EXIT -> 0
                    RebalanceActionType.REDUCE -> 1
                    RebalanceActionType.BUY_NEW -> 2
                    RebalanceActionType.INCREASE -> 3
                    RebalanceActionType.HOLD -> 4
                }
            }.thenByDescending { abs(it.deltaValueRub) }
        )

        val currentCashPct = ((availableCashRub / safeEquity) * 100.0).coerceIn(0.0, 100.0)
        val targetCashRub = safeEquity * (finalTargetCashPct / 100.0)
        totalAbsDriftPct += abs(finalTargetCashPct - currentCashPct)

        val alignmentScore = (100.0 - (totalAbsDriftPct * 0.65)).coerceIn(15.0, 100.0).roundToInt()

        // Sector Allocation Slices (Current vs Target)
        val sectorColors = mapOf(
            "Банки и Финансы" to 0xFF00E5FF,
            "Нефть и Газ" to 0xFF00E676,
            "Металлургия и Добыча" to 0xFFFFC400,
            "IT и Телеком" to 0xFFB388FF,
            "Кэш / LQDT" to 0xFF607D8B
        )
        val sectorNames = listOf("Банки и Финансы", "Нефть и Газ", "Металлургия и Добыча", "IT и Телеком")
        val sectorSlices = mutableListOf<SectorAllocationSlice>()

        for (secName in sectorNames) {
            val curSecPct = sortedRecommendations
                .filter { it.sectorRu.equals(secName, ignoreCase = true) }
                .sumOf { it.currentWeightPct }
            val tarSecPct = sortedRecommendations
                .filter { it.sectorRu.equals(secName, ignoreCase = true) }
                .sumOf { it.targetWeightPct }
            if (curSecPct > 0.1 || tarSecPct > 0.1) {
                sectorSlices.add(
                    SectorAllocationSlice(
                        sectorNameRu = secName,
                        currentWeightPct = curSecPct,
                        targetWeightPct = tarSecPct,
                        colorHex = sectorColors[secName] ?: 0xFF00E5FF
                    )
                )
            }
        }
        sectorSlices.add(
            SectorAllocationSlice(
                sectorNameRu = "💵 Кэш / LQDT",
                currentWeightPct = currentCashPct,
                targetWeightPct = finalTargetCashPct,
                colorHex = 0xFF78909C
            )
        )

        // Estimate Daily 95% VaR (Value at Risk) & Expected Portfolio Profit Factor
        val activeTargets = selectedTargets.ifEmpty { liveSignals.take(4) }
        val avgAtrPct = if (activeTargets.isEmpty()) {
            1.6
        } else {
            activeTargets.map {
                if (it.currentPrice > 0) (it.atr14 / it.currentPrice) * 100.0 else 1.6
            }.average()
        }
        val deployedEquityRub = safeEquity * (actualTargetEquitySumPct / 100.0)
        // Diversified portfolio daily 95% VaR (~1.65 sigma with diversification factor 0.72)
        val estimatedVar95Rub = deployedEquityRub * (avgAtrPct / 100.0) * 1.18

        val expectedPf = if (selectedTargets.isEmpty()) {
            1.65
        } else {
            selectedTargets.map { it.backtestResult.profitFactor.coerceIn(1.0, 3.5) }.average()
        }

        return PortfolioRebalancePlan(
            riskTolerance = riskTolerance,
            marketEnvironmentTitleRu = envTitleRu,
            marketEnvironmentDiagnosisRu = envDiagnosisRu,
            fearAndGreedScore = fgScore,
            totalEquityRub = safeEquity,
            currentCashWeightPct = currentCashPct,
            targetCashWeightPct = finalTargetCashPct,
            currentCashRub = availableCashRub,
            targetCashRub = targetCashRub,
            alignmentScore = alignmentScore,
            totalDriftPct = (totalAbsDriftPct / 2.0).coerceIn(0.0, 100.0),
            estimatedDailyVar95Rub = estimatedVar95Rub,
            expectedPortfolioProfitFactor = expectedPf,
            sectorSlices = sectorSlices,
            recommendations = sortedRecommendations
        )
    }

    private fun RiskToleranceLevel.CONSERVIVE_CHECK(): RiskToleranceLevel = RiskToleranceLevel.CONSERVATIVE
}

/**
 * Interactive Portfolio Rebalancing Module Card for PaperTradingScreen & Sector/Portfolio Views.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioRebalancingCard(
    summary: PaperPortfolioSummary,
    openTrades: List<PaperTradeEntity>,
    liveSignals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot,
    newsSentimentIndex: Int,
    privacyHideBalance: Boolean = false,
    onExecuteSingleRecommendation: (RebalanceRecommendation) -> Unit,
    onExecuteFullRebalancePlan: (PortfolioRebalancePlan) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current

    var selectedRiskTolerance by rememberSaveable { mutableStateOf(RiskToleranceLevel.MODERATE) }
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    val plan = remember(
        summary.totalEquityRub,
        summary.availableCashRub,
        openTrades,
        liveSignals,
        imoexSnapshot,
        newsSentimentIndex,
        selectedRiskTolerance
    ) {
        PortfolioRebalanceEngine.computeOptimalRebalancePlan(
            totalEquityRub = summary.totalEquityRub,
            availableCashRub = summary.availableCashRub,
            openTrades = openTrades,
            liveSignals = liveSignals,
            imoexSnapshot = imoexSnapshot,
            newsSentimentIndex = newsSentimentIndex,
            riskTolerance = selectedRiskTolerance
        )
    }

    fun maskRub(value: Double): String {
        if (privacyHideBalance) return "•••• ₽"
        return String.format(Locale.US, "%,.0f ₽", value).replace(',', ' ')
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.bgCard),
        border = BorderStroke(1.2.dp, palette.cyanAccent.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .testTag("portfolio_rebalancing_module_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header Row with Alignment Score & Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Balance,
                        contentDescription = null,
                        tint = palette.cyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Квант-Ребалансировка Портфеля (AI Allocation)",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Оптимизация долей по режиму IMOEX, волатильности ATR и риск-профилю",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val scoreColor = when {
                        plan.alignmentScore >= 80 -> palette.longGreen
                        plan.alignmentScore >= 55 -> palette.amberWait
                        else -> palette.shortRed
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scoreColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Баланс ${plan.alignmentScore}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = scoreColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Свернуть/развернуть модуль ребалансировки",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Risk Tolerance Selector (Conservative / Balanced / Aggressive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RiskToleranceLevel.entries.forEach { level ->
                    val selected = selectedRiskTolerance == level
                    val levelAccent = when (level) {
                        RiskToleranceLevel.CONSERVATIVE -> palette.cyanAccent
                        RiskToleranceLevel.MODERATE -> palette.longGreen
                        RiskToleranceLevel.AGGRESSIVE -> palette.amberWait
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) levelAccent.copy(alpha = 0.18f) else palette.bgElevated,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 0.8.dp,
                            color = if (selected) levelAccent else palette.borderCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedRiskTolerance = level
                            }
                            .testTag("rebalance_risk_level_${level.code}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = level.titleRu,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) levelAccent else palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Акции ~${level.baseEquityAllocationPct.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. Current Market Environment Diagnosis Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.bgElevated,
                border = BorderStroke(0.8.dp, palette.borderCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = plan.marketEnvironmentTitleRu,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.cyanAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = plan.marketEnvironmentDiagnosisRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RebalanceMetricPill(
                            label = "Целевой Кэш/LQDT",
                            value = "${plan.targetCashWeightPct.roundToInt()}% (${maskRub(plan.targetCashRub)})",
                            color = palette.cyanAccent
                        )
                        RebalanceMetricPill(
                            label = "Ожид. Profit Factor",
                            value = String.format(Locale.US, "%.2f", plan.expectedPortfolioProfitFactor),
                            color = palette.longGreen
                        )
                        RebalanceMetricPill(
                            label = "Дневной VaR (95%)",
                            value = "±${maskRub(plan.estimatedDailyVar95Rub)}",
                            color = palette.amberWait
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 4. Visual Stacked Allocation Bar: Current vs Target Sector Allocation
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "СТРУКТУРА ПО СЕКТОРАМ (ТЕКУЩАЯ VS ЦЕЛЕВАЯ МОДЕЛЬ)",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "Отклонение: ${String.format(Locale.US, "%.1f", plan.totalDriftPct)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.cyanAccent,
                                fontSize = 9.sp
                            )
                        }

                        AllocationComparisonStackedBar(
                            title = "Текущий",
                            slices = plan.sectorSlices,
                            useTarget = false
                        )
                        AllocationComparisonStackedBar(
                            title = "Целевой",
                            slices = plan.sectorSlices,
                            useTarget = true
                        )

                        // Legend
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            plan.sectorSlices.forEach { slice ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(slice.colorHex))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${slice.sectorNameRu}: ${slice.currentWeightPct.roundToInt()}% → ${slice.targetWeightPct.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.borderCard.copy(alpha = 0.6f))

                    // 5. Concrete Asset-Level Rebalancing Recommendations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ТОРГОВЫЙ ПЛАН РЕБАЛАНСИРОВКИ (${plan.recommendations.size} АКТИВОВ)",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )

                        if (plan.actionableTradesCount > 0) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExecuteFullRebalancePlan(plan)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.cyanAccent,
                                    contentColor = Color(0xFF04101C)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("execute_full_rebalance_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "⚡ Применить всё (${plan.actionableTradesCount})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.recommendations.forEach { rec ->
                            RebalanceRecommendationItemRow(
                                rec = rec,
                                privacyHideBalance = privacyHideBalance,
                                onExecute = { onExecuteSingleRecommendation(rec) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RebalanceMetricPill(
    label: String,
    value: String,
    color: Color
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = palette.bgCard,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
                fontSize = 9.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AllocationComparisonStackedBar(
    title: String,
    slices: List<SectorAllocationSlice>,
    useTarget: Boolean
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            fontSize = 9.sp,
            modifier = Modifier.width(48.dp)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            val totalW = size.width
            val h = size.height
            drawRoundRect(
                color = palette.bgElevated,
                cornerRadius = CornerRadius(6f, 6f),
                size = Size(totalW, h)
            )
            var currentX = 0f
            val sumPct = slices.sumOf { if (useTarget) it.targetWeightPct else it.currentWeightPct }
                .coerceAtLeast(1.0)
            slices.forEach { slice ->
                val pct = if (useTarget) slice.targetWeightPct else slice.currentWeightPct
                val segW = ((pct / sumPct) * totalW).toFloat()
                if (segW > 1f) {
                    drawRect(
                        color = Color(slice.colorHex),
                        topLeft = Offset(currentX, 0f),
                        size = Size(segW, h)
                    )
                    currentX += segW
                }
            }
        }
    }
}

@Composable
private fun RebalanceRecommendationItemRow(
    rec: RebalanceRecommendation,
    privacyHideBalance: Boolean,
    onExecute: () -> Unit
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current

    val actionColor = when (rec.actionType) {
        RebalanceActionType.BUY_NEW, RebalanceActionType.INCREASE -> palette.longGreen
        RebalanceActionType.REDUCE -> palette.amberWait
        RebalanceActionType.CLOSE_EXIT -> palette.shortRed
        RebalanceActionType.HOLD -> palette.cyanAccent
    }

    val deltaLotsFormatted = when {
        rec.deltaLots > 0 -> "+${rec.deltaLots} лот."
        rec.deltaLots < 0 -> "${rec.deltaLots} лот."
        else -> "0 лот."
    }

    val deltaRubFormatted = if (privacyHideBalance) {
        "•••• ₽"
    } else {
        val sign = if (rec.deltaValueRub >= 0) "+" else "-"
        "$sign${String.format(Locale.US, "%,.0f", abs(rec.deltaValueRub)).replace(',', ' ')} ₽"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.bgElevated,
        border = BorderStroke(0.8.dp, actionColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rebalance_item_${rec.ticker}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = actionColor.copy(alpha = 0.16f),
                        border = BorderStroke(0.5.dp, actionColor.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = rec.actionType.badgeRu,
                            style = MaterialTheme.typography.labelSmall,
                            color = actionColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#${rec.ticker} (${rec.stockName})",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rec.sectorRu,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }

                if (rec.actionType != RebalanceActionType.HOLD && abs(rec.deltaLots) > 0) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExecute()
                        },
                        border = BorderStroke(1.dp, actionColor),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("execute_rebalance_${rec.ticker}")
                    ) {
                        Text(
                            text = "Исполнить ($deltaLotsFormatted)",
                            style = MaterialTheme.typography.labelSmall,
                            color = actionColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }
                } else {
                    Text(
                        text = "В норме",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.cyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            // Weight & Lot progression bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Вес: ${String.format(Locale.US, "%.1f", rec.currentWeightPct)}% → ${String.format(Locale.US, "%.1f", rec.targetWeightPct)}% (${rec.currentLots} → ${rec.targetLots} лотов)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "$deltaLotsFormatted ($deltaRubFormatted)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = actionColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            LinearProgressIndicator(
                progress = { (rec.targetWeightPct / 25.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = actionColor,
                trackColor = palette.borderCard.copy(alpha = 0.4f)
            )

            Text(
                text = rec.rationaleRu,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
