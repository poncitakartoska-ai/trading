package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.min

@Immutable
enum class TradeOutcome(val code: String, val labelRu: String) {
    ACTIVE("ACTIVE", "В СДЕЛКЕ"),
    HIT_TP("HIT_TP", "ТЕЙК-ПРОФИТ"),
    HIT_SL("HIT_SL", "СТОП-ЛОСС");

    companion object {
        fun fromCode(code: String): TradeOutcome =
            entries.firstOrNull { it.code == code } ?: ACTIVE
    }
}

@Immutable
@Entity(tableName = "signal_history")
data class SignalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val stockName: String,
    val signalType: String, // "LONG" or "SHORT"
    val entryPrice: Double,
    val takeProfit: Double,
    val stopLoss: Double,
    val confidence: Int,
    val reason: String,
    val rsi: Double,
    val atr: Double,
    val timestamp: Long,
    val status: String = TradeOutcome.ACTIVE.code, // "ACTIVE", "HIT_TP", "HIT_SL"
    val exitPrice: Double? = null,
    val resolvedAt: Long? = null,
    val pnlPercent: Double = 0.0,
    val takeProfit1: Double = takeProfit,
    val takeProfit2: Double = takeProfit,
    val isBreakeven: Boolean = false
)

@Immutable
@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val stockName: String,
    val direction: String, // "LONG" or "SHORT"
    val entryPrice: Double,
    val currentPrice: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val initialStopLoss: Double,
    val dynamicStopLoss: Double,
    val lots: Int,
    val lotSize: Int,
    val minStep: Double = 0.01,
    val atr: Double = 1.0,
    val positionValueRub: Double,
    val status: String = "OPEN", // "OPEN", "CLOSED_TP", "CLOSED_SL", "CLOSED_MANUAL"
    val tp1Hit: Boolean = false,
    val lockedTp1ProfitRub: Double = 0.0,
    val unrealizedPnlRub: Double = 0.0,
    val realizedPnlRub: Double = 0.0,
    val totalPnlRub: Double = 0.0,
    val pnlPercent: Double = 0.0,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val exitPrice: Double? = null,
    val closeNoteRu: String = "Позиция активна"
)

typealias DemoTrade = PaperTradeEntity

@Dao
interface SignalHistoryDao {
    @Query("SELECT * FROM signal_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<SignalHistoryEntity>>

    @Query("SELECT * FROM signal_history WHERE ticker = :ticker ORDER BY timestamp DESC")
    fun getHistoryForTickerFlow(ticker: String): Flow<List<SignalHistoryEntity>>

    @Query("SELECT * FROM signal_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryOnce(): List<SignalHistoryEntity>

    @Query("SELECT * FROM signal_history WHERE ticker = :ticker ORDER BY timestamp DESC")
    suspend fun getHistoryForTickerOnce(ticker: String): List<SignalHistoryEntity>

    @Query("SELECT * FROM signal_history WHERE ticker = :ticker AND status = 'ACTIVE' ORDER BY timestamp DESC")
    suspend fun getActiveSignalsForTicker(ticker: String): List<SignalHistoryEntity>

    @Query("SELECT * FROM signal_history WHERE ticker = :ticker ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSignalForTicker(ticker: String): SignalHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<SignalHistoryEntity>)

    @Update
    suspend fun updateSignal(signal: SignalHistoryEntity)

    @Query("DELETE FROM signal_history")
    suspend fun clearAllHistory()
}

@Dao
interface PaperTradeDao {
    @Query("SELECT * FROM paper_trades ORDER BY openedAt DESC")
    fun getAllPaperTradesFlow(): Flow<List<PaperTradeEntity>>

    @Query("SELECT * FROM paper_trades WHERE status = 'OPEN' ORDER BY openedAt DESC")
    suspend fun getOpenPaperTradesOnce(): List<PaperTradeEntity>

    @Query("SELECT * FROM paper_trades ORDER BY openedAt DESC")
    suspend fun getAllPaperTradesOnce(): List<PaperTradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaperTrade(trade: PaperTradeEntity): Long

    @Update
    suspend fun updatePaperTrade(trade: PaperTradeEntity)

    @Query("DELETE FROM paper_trades")
    suspend fun clearAllPaperTrades()
}

@Immutable
@Entity(tableName = "trading_strategies")
data class TradingStrategy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val strategyCode: String,
    val name: String,
    val description: String,
    val marketType: String, // "MOEX" or "CRYPTO"
    val fastEmaPeriod: Int,
    val slowEmaPeriod: Int,
    val signalEmaPeriod: Int = 7, // Smoothing EMA on RSI for dual-confirmed RSI/EMA crossover
    val rsiPeriod: Int,
    val rsiOversold: Double,
    val rsiOverbought: Double,
    val rsiBullCrossLevel: Double = 47.0,
    val rsiBearCrossLevel: Double = 53.0,
    val tp1AtrMultiplier: Double = 0.80,
    val tp2AtrMultiplier: Double = 2.25,
    val slAtrMultiplier: Double = 1.85,
    val breakevenTriggerAtr: Double = 0.48,
    val historicalWinRate: Double = 78.5,
    val totalSignalsEvaluated: Int = 120,
    val isActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface TradingStrategyDao {
    @Query("SELECT * FROM trading_strategies ORDER BY isActive DESC, historicalWinRate DESC")
    fun getAllStrategiesFlow(): Flow<List<TradingStrategy>>

    @Query("SELECT * FROM trading_strategies WHERE marketType = :marketType ORDER BY isActive DESC, historicalWinRate DESC")
    fun getStrategiesByMarketFlow(marketType: String): Flow<List<TradingStrategy>>

    @Query("SELECT * FROM trading_strategies WHERE marketType = :marketType AND isActive = 1 LIMIT 1")
    fun getActiveStrategyForMarketFlow(marketType: String): Flow<TradingStrategy?>

    @Query("SELECT * FROM trading_strategies ORDER BY isActive DESC, historicalWinRate DESC")
    suspend fun getAllStrategiesOnce(): List<TradingStrategy>

    @Query("SELECT * FROM trading_strategies WHERE marketType = :marketType AND isActive = 1 LIMIT 1")
    suspend fun getActiveStrategyForMarketOnce(marketType: String): TradingStrategy?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: TradingStrategy): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategies(strategies: List<TradingStrategy>)

    @Update
    suspend fun updateStrategy(strategy: TradingStrategy)

    @Query("UPDATE trading_strategies SET isActive = 0 WHERE marketType = :marketType")
    suspend fun deactivateAllForMarket(marketType: String)

    @Query("UPDATE trading_strategies SET isActive = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun activateStrategyById(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM trading_strategies WHERE id = :id")
    suspend fun deleteStrategyById(id: Long)
}

@Database(
    entities = [SignalHistoryEntity::class, PaperTradeEntity::class, TradingStrategy::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalHistoryDao(): SignalHistoryDao
    abstract fun paperTradeDao(): PaperTradeDao
    abstract fun tradingStrategyDao(): TradingStrategyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moex_pulse_quant.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Immutable
data class PerformanceSummary(
    val totalSignals: Int = 0,
    val activeCount: Int = 0,
    val hitTpCount: Int = 0,
    val hitSlCount: Int = 0,
    val winRatePercent: Double = 0.0,
    val cumulativePnlPercent: Double = 0.0
)

@Immutable
data class PaperPortfolioSummary(
    val initialDepositRub: Double = 1_000_000.0,
    val totalEquityRub: Double = 1_000_000.0,
    val availableCashRub: Double = 1_000_000.0,
    val allocatedMarginRub: Double = 0.0,
    val unrealizedPnlRub: Double = 0.0,
    val realizedPnlRub: Double = 0.0,
    val totalPnlRub: Double = 0.0,
    val brokerCommissionPaidRub: Double = 0.0,
    val netPnlRub: Double = 0.0,
    val totalPnlPercent: Double = 0.0,
    val dailyDrawdownPercent: Double = 0.0,
    val isDailyLossLimitReached: Boolean = false,
    val openCount: Int = 0,
    val closedCount: Int = 0,
    val winRatePercent: Double = 0.0,
    val equityCurvePoints: List<Double> = listOf(1_000_000.0)
)

class SignalHistoryRepository(private val dao: SignalHistoryDao) {
    val allHistory: Flow<List<SignalHistoryEntity>> = dao.getAllHistoryFlow()

    fun getHistoryForTicker(ticker: String): Flow<List<SignalHistoryEntity>> =
        dao.getHistoryForTickerFlow(ticker)

    suspend fun getAllOnce(): List<SignalHistoryEntity> = dao.getAllHistoryOnce()

    suspend fun getHistoryForTickerOnce(ticker: String): List<SignalHistoryEntity> =
        dao.getHistoryForTickerOnce(ticker)

    suspend fun getActiveSignalsForTicker(ticker: String): List<SignalHistoryEntity> =
        dao.getActiveSignalsForTicker(ticker)

    suspend fun getLatestSignalForTicker(ticker: String): SignalHistoryEntity? =
        dao.getLatestSignalForTicker(ticker)

    suspend fun insertSignal(signal: SignalHistoryEntity): Long =
        dao.insertSignal(signal)

    suspend fun insertSignals(signals: List<SignalHistoryEntity>) =
        dao.insertSignals(signals)

    suspend fun updateSignal(signal: SignalHistoryEntity) =
        dao.updateSignal(signal)

    suspend fun clearAll() = dao.clearAllHistory()

    companion object {
        fun computeSummary(list: List<SignalHistoryEntity>): PerformanceSummary {
            val active = list.count { it.status == TradeOutcome.ACTIVE.code }
            val tp = list.count { it.status == TradeOutcome.HIT_TP.code }
            val sl = list.count { it.status == TradeOutcome.HIT_SL.code }
            val closed = tp + sl
            val winRate = if (closed > 0) (tp.toDouble() / closed.toDouble()) * 100.0 else 0.0
            val totalPnl = list
                .filter { it.status != TradeOutcome.ACTIVE.code }
                .sumOf { it.pnlPercent }
            return PerformanceSummary(
                totalSignals = list.size,
                activeCount = active,
                hitTpCount = tp,
                hitSlCount = sl,
                winRatePercent = winRate,
                cumulativePnlPercent = totalPnl
            )
        }
    }
}

class PaperTradingRepository(private val dao: PaperTradeDao) {
    val allTradesFlow: Flow<List<PaperTradeEntity>> = dao.getAllPaperTradesFlow()

    suspend fun getOpenTradesOnce(): List<PaperTradeEntity> = dao.getOpenPaperTradesOnce()
    suspend fun getAllTradesOnce(): List<PaperTradeEntity> = dao.getAllPaperTradesOnce()

    suspend fun insertTrade(trade: PaperTradeEntity): Long = dao.insertPaperTrade(trade)
    suspend fun updateTrade(trade: PaperTradeEntity) = dao.updatePaperTrade(trade)
    suspend fun clearAll() = dao.clearAllPaperTrades()

    companion object {
        const val DEFAULT_STARTING_BALANCE = 1_000_000.0
        const val BROKER_FEE_RATE_PER_SIDE = 0.0 // 0% commission in Demo Account

        fun computePortfolioSummary(
            trades: List<PaperTradeEntity>,
            startingBalance: Double = DEFAULT_STARTING_BALANCE
        ): PaperPortfolioSummary {
            val openTrades = trades.filter { it.status == "OPEN" }
            val closedTrades = trades.filter { it.status != "OPEN" }.sortedBy { it.closedAt ?: it.openedAt }

            val grossRealized = closedTrades.sumOf { it.realizedPnlRub } + openTrades.sumOf { it.lockedTp1ProfitRub }
            val unrealized = openTrades.sumOf { it.unrealizedPnlRub }

            // Zero commission in Demo Account (0%)
            val totalCommission = 0.0

            val grossTotalPnl = grossRealized + unrealized
            val netTotalPnl = grossTotalPnl
            val allocated = openTrades.sumOf {
                val activeShareFactor = if (it.tp1Hit) 0.5 else 1.0
                it.positionValueRub * activeShareFactor
            }
            val equity = startingBalance + netTotalPnl
            val available = max(0.0, equity - allocated)
            val wins = closedTrades.count { it.realizedPnlRub > 0.0 }
            val winRate = if (closedTrades.isNotEmpty()) {
                (wins.toDouble() / closedTrades.size.toDouble()) * 100.0
            } else 0.0

            // Build Equity Curve points across closed trades + current mark-to-market equity
            val curve = ArrayList<Double>()
            var runningEq = startingBalance
            curve.add(runningEq)
            for (ct in closedTrades) {
                runningEq += ct.realizedPnlRub
                curve.add(max(0.0, runningEq))
            }
            if (openTrades.isNotEmpty()) {
                curve.add(max(0.0, equity))
            }

            // Daily Loss Circuit Breaker check (-3.0% drawdown limit)
            val oneDayAgo = System.currentTimeMillis() - 24L * 3600_000L
            val todayNetLossRub = trades
                .filter { (it.closedAt ?: it.openedAt) >= oneDayAgo }
                .sumOf { it.totalPnlRub }
            val dailyDrawdownPct = if (startingBalance > 0) (todayNetLossRub / startingBalance) * 100.0 else 0.0
            val circuitBreakerTriggered = dailyDrawdownPct <= -3.0

            return PaperPortfolioSummary(
                initialDepositRub = startingBalance,
                totalEquityRub = max(0.0, equity),
                availableCashRub = available,
                allocatedMarginRub = allocated,
                unrealizedPnlRub = unrealized,
                realizedPnlRub = grossRealized,
                totalPnlRub = grossTotalPnl,
                brokerCommissionPaidRub = totalCommission,
                netPnlRub = netTotalPnl,
                totalPnlPercent = if (startingBalance > 0) (netTotalPnl / startingBalance) * 100.0 else 0.0,
                dailyDrawdownPercent = dailyDrawdownPct,
                isDailyLossLimitReached = circuitBreakerTriggered,
                openCount = openTrades.size,
                closedCount = closedTrades.size,
                winRatePercent = winRate,
                equityCurvePoints = curve
            )
        }
    }
}

class TradingStrategyRepository(private val dao: TradingStrategyDao) {
    val allStrategiesFlow: Flow<List<TradingStrategy>> = dao.getAllStrategiesFlow()

    fun getStrategiesByMarketFlow(marketType: String): Flow<List<TradingStrategy>> =
        dao.getStrategiesByMarketFlow(marketType)

    fun getActiveStrategyForMarketFlow(marketType: String): Flow<TradingStrategy?> =
        dao.getActiveStrategyForMarketFlow(marketType)

    suspend fun getAllOnce(): List<TradingStrategy> = dao.getAllStrategiesOnce()

    suspend fun getActiveForMarketOnce(marketType: String): TradingStrategy? =
        dao.getActiveStrategyForMarketOnce(marketType)

    suspend fun insert(strategy: TradingStrategy): Long = dao.insertStrategy(strategy)

    suspend fun update(strategy: TradingStrategy) = dao.updateStrategy(strategy)

    suspend fun activateStrategy(strategy: TradingStrategy) {
        dao.deactivateAllForMarket(strategy.marketType)
        dao.activateStrategyById(strategy.id)
    }

    suspend fun deleteById(id: Long) = dao.deleteStrategyById(id)

    suspend fun ensureDefaultStrategiesSeeded() {
        val existing = dao.getAllStrategiesOnce()
        if (existing.isNotEmpty()) return
        val now = System.currentTimeMillis()
        val defaults = listOf(
            // MOEX Equities Presets
            TradingStrategy(
                strategyCode = "MOEX_KVANT_SHIELD_CROSS",
                name = "Квант-Щит 75%+ (MOEX EMA 20/50 + RSI 14 Cross)",
                description = "Асимметричный Безубыток + пересечение EMA(20/50) и сглаженного RSI(14)/EMA(7) для акций РФ (TQBR 10:00–23:50)",
                marketType = "MOEX",
                fastEmaPeriod = 20,
                slowEmaPeriod = 50,
                signalEmaPeriod = 7,
                rsiPeriod = 14,
                rsiOversold = 28.0,
                rsiOverbought = 72.0,
                rsiBullCrossLevel = 46.0,
                rsiBearCrossLevel = 54.0,
                tp1AtrMultiplier = 0.80,
                tp2AtrMultiplier = 2.25,
                slAtrMultiplier = 1.85,
                breakevenTriggerAtr = 0.48,
                historicalWinRate = 79.4,
                totalSignalsEvaluated = 164,
                isActive = true,
                updatedAt = now
            ),
            TradingStrategy(
                strategyCode = "MOEX_SNIPER_EMA9_21",
                name = "MOEX Снайпер-Кроссовер (EMA 9/21 + RSI 12)",
                description = "Ранний вход на пересечении быстрых EMA(9/21) при пробое сигнальной линии RSI(12) с фильтром объема",
                marketType = "MOEX",
                fastEmaPeriod = 9,
                slowEmaPeriod = 21,
                signalEmaPeriod = 5,
                rsiPeriod = 12,
                rsiOversold = 30.0,
                rsiOverbought = 70.0,
                rsiBullCrossLevel = 48.0,
                rsiBearCrossLevel = 52.0,
                tp1AtrMultiplier = 0.75,
                tp2AtrMultiplier = 2.10,
                slAtrMultiplier = 1.75,
                breakevenTriggerAtr = 0.45,
                historicalWinRate = 76.8,
                totalSignalsEvaluated = 138,
                isActive = false,
                updatedAt = now
            ),
            TradingStrategy(
                strategyCode = "MOEX_INSTITUTIONAL_SWING",
                name = "MOEX Институциональный Тренд (EMA 24/60 + RSI 14)",
                description = "Консервативный свинг-алгоритм по голубым фишкам РФ (SBER, LKOH, GAZP, PLZL) с широким стопом",
                marketType = "MOEX",
                fastEmaPeriod = 24,
                slowEmaPeriod = 60,
                signalEmaPeriod = 9,
                rsiPeriod = 14,
                rsiOversold = 32.0,
                rsiOverbought = 68.0,
                rsiBullCrossLevel = 49.0,
                rsiBearCrossLevel = 51.0,
                tp1AtrMultiplier = 0.95,
                tp2AtrMultiplier = 2.50,
                slAtrMultiplier = 1.90,
                breakevenTriggerAtr = 0.52,
                historicalWinRate = 74.2,
                totalSignalsEvaluated = 112,
                isActive = false,
                updatedAt = now
            ),
            // Crypto 24/7 Volatility-Tuned Presets
            TradingStrategy(
                strategyCode = "CRYPTO_KVANT_SHIELD_24_7",
                name = "Квант-Щит 75%+ Crypto 24/7 (EMA 12/34 + RSI 11 Cross)",
                description = "Калибровка под 24/7 волатильность крипторынка: быстрые EMA(12/34), динамический RSI(11)/EMA(6) [22..78] и защита от сквизов",
                marketType = "CRYPTO",
                fastEmaPeriod = 12,
                slowEmaPeriod = 34,
                signalEmaPeriod = 6,
                rsiPeriod = 11,
                rsiOversold = 22.0,
                rsiOverbought = 78.0,
                rsiBullCrossLevel = 44.0,
                rsiBearCrossLevel = 56.0,
                tp1AtrMultiplier = 0.82,
                tp2AtrMultiplier = 2.40,
                slAtrMultiplier = 1.95,
                breakevenTriggerAtr = 0.48,
                historicalWinRate = 78.6,
                totalSignalsEvaluated = 195,
                isActive = true,
                updatedAt = now
            ),
            TradingStrategy(
                strategyCode = "CRYPTO_PERP_MOMENTUM_8_21",
                name = "Crypto Perp Impulse 24/7 (EMA 8/21 + RSI 9)",
                description = "Импульсный кроссовер EMA(8/21) + RSI(9) для ловли пробоев и шорт-сквизов на BTC, ETH, SOL, TON",
                marketType = "CRYPTO",
                fastEmaPeriod = 8,
                slowEmaPeriod = 21,
                signalEmaPeriod = 5,
                rsiPeriod = 9,
                rsiOversold = 20.0,
                rsiOverbought = 80.0,
                rsiBullCrossLevel = 45.0,
                rsiBearCrossLevel = 55.0,
                tp1AtrMultiplier = 0.78,
                tp2AtrMultiplier = 2.65,
                slAtrMultiplier = 1.90,
                breakevenTriggerAtr = 0.46,
                historicalWinRate = 75.1,
                totalSignalsEvaluated = 172,
                isActive = false,
                updatedAt = now
            ),
            TradingStrategy(
                strategyCode = "CRYPTO_MACRO_RIBBON_18_48",
                name = "Crypto Macro Trend 24/7 (EMA 18/48 + RSI 14)",
                description = "Сглаженный трендовый фильтр для фильтрации ночного шума 24/7 и удержания крупных движений",
                marketType = "CRYPTO",
                fastEmaPeriod = 18,
                slowEmaPeriod = 48,
                signalEmaPeriod = 8,
                rsiPeriod = 14,
                rsiOversold = 25.0,
                rsiOverbought = 75.0,
                rsiBullCrossLevel = 47.0,
                rsiBearCrossLevel = 53.0,
                tp1AtrMultiplier = 0.90,
                tp2AtrMultiplier = 2.55,
                slAtrMultiplier = 2.00,
                breakevenTriggerAtr = 0.50,
                historicalWinRate = 76.4,
                totalSignalsEvaluated = 144,
                isActive = false,
                updatedAt = now
            )
        )
        dao.insertStrategies(defaults)
    }
}
