package `in`.ignishers.milkmanager2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import `in`.ignishers.milkmanager2.models.DailyTransaction
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow

data class ReportSummary(
    var totalMilk: BigDecimal = BigDecimal.ZERO,
    var totalRevenue: BigDecimal = BigDecimal.ZERO,
    var totalCollected: BigDecimal = BigDecimal.ZERO,
    var totalDue: BigDecimal = BigDecimal.ZERO,
    var totalLegacyDue: BigDecimal = BigDecimal.ZERO
)

data class MonthlyReportItem(
    val month: Int,
    val milk: BigDecimal,
    val amount: BigDecimal,
    val collected: BigDecimal
)

data class CustomerSalesItem(
    val customerName: String,
    val totalMilk: BigDecimal,
    val totalSpent: BigDecimal
)

data class YearlyReportItem(
    val year: Int,
    val totalMilk: BigDecimal,
    val totalRevenue: BigDecimal
)

@Dao
interface DailyTransactionRoomDao {
    @Query("SELECT * FROM milk_transaction")
    fun getAllTransactions(): kotlinx.coroutines.flow.Flow<List<DailyTransaction>>

    @Query("SELECT * FROM milk_transaction ORDER BY transaction_date ASC, transaction_id ASC")
    suspend fun getAllTransactionsList(): List<DailyTransaction>

    @Insert
    suspend fun insert(transaction: DailyTransaction): Long

    @Insert
    suspend fun insertAll(transactions: List<DailyTransaction>): List<Long>

    @Query("SELECT * FROM milk_transaction WHERE customer_id_fk = :customerId AND strftime('%m', transaction_date) = :month AND strftime('%Y', transaction_date) = :year")
    suspend fun getTransactionsByMonth(customerId: String, month: String, year: String): List<DailyTransaction>

    @Query("SELECT * FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date = :date ORDER BY transaction_id DESC")
    suspend fun getTransactionsByDate(customerId: Long, date: String): List<DailyTransaction>

    @Query("SELECT * FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date = :date ORDER BY transaction_id DESC")
    fun getTransactionsByDateFlow(customerId: Long, date: String): Flow<List<DailyTransaction>>

    @Query("DELETE FROM milk_transaction WHERE transaction_id = :transactionId")
    suspend fun delete(transactionId: Int): Int

    @Query("SELECT COUNT(*) > 0 FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date = :date AND transaction_session = :session")
    suspend fun hasTodayEntry(customerId: Long, date: String, session: String): Boolean

    @Query("SELECT * FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date = :date AND transaction_session = :session ORDER BY transaction_id DESC LIMIT 1")
    suspend fun getTodaySession(customerId: Long, date: String, session: String): DailyTransaction?

    @Query("DELETE FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date = :date AND transaction_session = :session")
    suspend fun deleteTodaySession(customerId: Long, date: String, session: String): Int

    @Query("DELETE FROM milk_transaction WHERE customer_id_fk = :customerId")
    suspend fun deleteAllForCustomer(customerId: Long): Int

    @Query("SELECT * FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date BETWEEN :startDate AND :endDate ORDER BY transaction_date ASC, transaction_id ASC")
    suspend fun getTransactionsByDateRange(customerId: Long, startDate: String, endDate: String): List<DailyTransaction>

    @Query("SELECT * FROM milk_transaction WHERE customer_id_fk = :customerId ORDER BY transaction_date ASC, transaction_id ASC")
    suspend fun getAllTransactionsForCustomer(customerId: Long): List<DailyTransaction>

    @Query("SELECT SUM(transaction_amount) FROM milk_transaction WHERE customer_id_fk = :customerId AND transaction_date >= :startDate AND transaction_date <= :endDate AND (transaction_session IS NULL OR transaction_session NOT LIKE 'Payment%')")
    fun getCurrentMonthMilkTotalFlow(customerId: Long, startDate: String, endDate: String): Flow<BigDecimal?>
    
    // Aggregations
    @Query("SELECT SUM(transaction_quantity) FROM milk_transaction WHERE transaction_session NOT LIKE 'Payment%'")
    suspend fun getTotalMilkLifetime(): BigDecimal?
    
    @Query("SELECT SUM(transaction_amount) FROM milk_transaction WHERE transaction_session NOT LIKE 'Payment%'")
    suspend fun getTotalRevenueLifetime(): BigDecimal?
    
    @Query("SELECT SUM(transaction_amount) FROM milk_transaction WHERE transaction_session LIKE 'Payment%'")
    suspend fun getTotalCollectedLifetime(): BigDecimal?
    
    @Query("SELECT customer_name as customerName, SUM(transaction_quantity) as totalMilk, SUM(transaction_amount) as totalSpent FROM milk_transaction t JOIN customer c ON t.customer_id_fk = c.customer_id WHERE t.transaction_session NOT LIKE 'Payment%' GROUP BY t.customer_id_fk ORDER BY SUM(transaction_amount) DESC LIMIT :limit")
    suspend fun getCustomerSalesRanking(limit: Int): List<CustomerSalesItem>
    
    @Query("SELECT CAST(strftime('%m', transaction_date) as INTEGER) as month, SUM(transaction_quantity) as milk, SUM(transaction_amount) as amount, 0 as collected FROM milk_transaction WHERE strftime('%Y', transaction_date) = :year AND transaction_session NOT LIKE 'Payment%' GROUP BY month")
    suspend fun getMonthlySales(year: String): List<MonthlyReportItem>
    
    @Query("SELECT CAST(strftime('%m', transaction_date) as INTEGER) as month, 0 as milk, 0 as amount, SUM(transaction_amount) as collected FROM milk_transaction WHERE strftime('%Y', transaction_date) = :year AND transaction_session LIKE 'Payment%' GROUP BY month")
    suspend fun getMonthlyCollected(year: String): List<MonthlyReportItem>

    @Query("SELECT CAST(strftime('%Y', transaction_date) as INTEGER) as year, SUM(transaction_quantity) as totalMilk, SUM(transaction_amount) as totalRevenue FROM milk_transaction WHERE transaction_session NOT LIKE 'Payment%' AND strftime('%Y', transaction_date) >= :startYear GROUP BY year ORDER BY year")
    suspend fun getYearlyRevenueTrend(startYear: String): List<YearlyReportItem>
}
