package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SignalHistoryEntity
import com.example.data.SignalHistoryRepository
import com.example.data.TradeOutcome
import com.example.quant.QuantSignalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Moex Pulse", appName)
    }

    @Test
    fun `quant engine calculates EMA RSI and performance summary accurately`() {
        val prices = (1..30).map { 250.0 + it * 0.8 }
        val ema20 = QuantSignalEngine.calculateEmaSeries(prices, 20)
        val rsi14 = QuantSignalEngine.calculateRsiSeries(prices, 14)

        assertEquals(30, ema20.size)
        assertTrue(ema20.last() > ema20.first())
        assertTrue(rsi14.last() > 60.0)

        val history = listOf(
            SignalHistoryEntity(
                id = 1,
                ticker = "SBER",
                stockName = "Сбербанк",
                signalType = "LONG",
                entryPrice = 280.0,
                takeProfit = 286.0,
                stopLoss = 277.0,
                confidence = 84,
                reason = "Пробой EMA 20 вверх",
                rsi = 52.0,
                atr = 2.0,
                timestamp = 1000L,
                status = TradeOutcome.HIT_TP.code,
                pnlPercent = 2.14
            ),
            SignalHistoryEntity(
                id = 2,
                ticker = "GAZP",
                stockName = "Газпром",
                signalType = "SHORT",
                entryPrice = 140.0,
                takeProfit = 134.0,
                stopLoss = 143.0,
                confidence = 78,
                reason = "Пробой EMA 20 вниз",
                rsi = 48.0,
                atr = 2.0,
                timestamp = 2000L,
                status = TradeOutcome.HIT_SL.code,
                pnlPercent = -2.14
            )
        )
        val summary = SignalHistoryRepository.computeSummary(history)
        assertEquals(2, summary.totalSignals)
        assertEquals(1, summary.hitTpCount)
        assertEquals(1, summary.hitSlCount)
        assertEquals(50.0, summary.winRatePercent, 0.01)
    }
}
