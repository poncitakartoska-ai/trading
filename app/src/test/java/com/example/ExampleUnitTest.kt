package com.example

import com.example.data.AppSettings
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.MoexCandle
import com.example.quant.QuantSignalEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun institutionalQuantEngine_computes16FactorMetricsAndMatrixSummary() {
        val meta = QuantSignalEngine.TOP_MOEX_STOCKS.first { it.ticker == "SBER" }
        val candles = (0 until 80).map { idx ->
            val base = 280.0 + idx * 0.45 + kotlin.math.sin(idx * 0.3) * 1.2
            MoexCandle(
                open = base - 0.3,
                close = base + 0.5,
                high = base + 1.1,
                low = base - 0.8,
                volume = 400_000.0 + idx * 5_000.0,
                valueRub = 120_000_000.0 + idx * 2_500_000.0,
                beginTime = "2026-10-18 10:${(idx % 60).toString().padStart(2, '0')}:00",
                timestampMillis = 1760780000000L + idx * 600_000L
            )
        }
        val imoex = ImoexIndexSnapshot(
            currentValue = 3245.5,
            changePercent = 0.85,
            updateTime = "14:30:00"
        )
        val signal = QuantSignalEngine.analyzeStock(
            meta = meta,
            livePrice = candles.last().close,
            changePercent = 1.92,
            valTodayRub = 34_500_000_000.0,
            minStep = 0.01,
            lotSize = 10,
            candles = candles,
            updateTime = "14:30:00",
            settings = AppSettings(),
            imoexSnapshot = imoex
        )

        val iq = signal.institutionalMetrics
        assertTrue("Composite Alpha Score should be in 1..99", iq.compositeAlphaScore in 1..99)
        assertTrue("Hurst exponent should be in 0.20..0.85", iq.hurstExponent in 0.20..0.85)
        assertTrue("Kalman Fair Price should be positive", iq.kalmanFairPrice > 0.0)
        assertTrue("Shannon Entropy should be bounded", iq.shannonEntropy in 0.0..1.0)
        assertTrue("Annualized GKYZ Volatility should be positive", iq.gkyzRealizedVolAnnualPct > 0.0)
        assertTrue("Monte Carlo Upper 95% should exceed Lower 95%", iq.mcUpper95Price > iq.mcLower05Price)
        assertTrue("Multi-horizon alignment should be in 0..3", iq.multiHorizonAlignmentScore in 0..3)

        val summary = QuantSignalEngine.computeMarketQuantMatrixSummary(listOf(signal), imoex)
        assertNotNull(summary)
        assertTrue(summary.marketOrderFlowDiagnosisRu.isNotBlank())
    }
}
