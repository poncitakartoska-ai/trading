package com.example.data

import android.content.Context
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

enum class TradeOutcome(val code: String, val labelRu: String) {
    ACTIVE("ACTIVE", "В СДЕЛКЕ"),
    HIT_TP("HIT_TP", "ТЕЙК-ПРОФИТ"),
    HIT_SL("HIT_SL", "СТОП-ЛОСС");

    companion object {
        fun fromCode(code: String): TradeOutcome =
            entries.firstOrNull { it.code == code } ?: ACTIVE
    }
}

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
    val pnlPercent: Double = 0.0
)

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

@Database(
    entities = [SignalHistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalHistoryDao(): SignalHistoryDao

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

data class PerformanceSummary(
    val totalSignals: Int = 0,
    val activeCount: Int = 0,
    val hitTpCount: Int = 0,
    val hitSlCount: Int = 0,
    val winRatePercent: Double = 0.0,
    val cumulativePnlPercent: Double = 0.0
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
