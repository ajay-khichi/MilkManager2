package `in`.ignishers.milkmanager2.database

import androidx.room.*
import `in`.ignishers.milkmanager2.models.MilkPurchase
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface MilkPurchaseRoomDao {

    @Query("SELECT * FROM milk_purchase WHERE purchase_date = :date AND purchase_session = :session ORDER BY purchase_id ASC")
    fun getPurchasesForDateAndSession(date: String, session: String): Flow<List<MilkPurchase>>

    @Query("SELECT * FROM milk_purchase WHERE purchase_date = :date AND purchase_session = :session")
    suspend fun getPurchasesForDateAndSessionList(date: String, session: String): List<MilkPurchase>

    @Query("SELECT * FROM milk_purchase WHERE supplier_id_fk = :supplierId AND purchase_date = :date AND purchase_session = :session LIMIT 1")
    suspend fun getPurchaseForSupplierDateSession(supplierId: Long, date: String, session: String): MilkPurchase?

    @Query("SELECT * FROM milk_purchase WHERE purchase_date LIKE :yearMonth || '%' ORDER BY purchase_date ASC, purchase_session ASC")
    fun getPurchasesForMonth(yearMonth: String): Flow<List<MilkPurchase>>

    @Query("SELECT * FROM milk_purchase WHERE purchase_date LIKE :yearMonth || '%' ORDER BY purchase_date ASC, purchase_session ASC")
    suspend fun getPurchasesForMonthList(yearMonth: String): List<MilkPurchase>

    @Query("SELECT * FROM milk_purchase WHERE supplier_id_fk = :supplierId AND purchase_date LIKE :yearMonth || '%' ORDER BY purchase_date ASC")
    suspend fun getPurchasesForSupplierMonth(supplierId: Long, yearMonth: String): List<MilkPurchase>

    @Query("SELECT SUM(quantity) FROM milk_purchase WHERE purchase_date LIKE :yearMonth || '%'")
    suspend fun getTotalQuantityForMonth(yearMonth: String): BigDecimal?

    @Query("SELECT SUM(total_amount) FROM milk_purchase WHERE purchase_date LIKE :yearMonth || '%'")
    suspend fun getTotalAmountForMonth(yearMonth: String): BigDecimal?

    @Query("SELECT SUM(quantity) FROM milk_purchase WHERE supplier_id_fk = :supplierId AND purchase_date LIKE :yearMonth || '%'")
    suspend fun getSupplierTotalQuantityForMonth(supplierId: Long, yearMonth: String): BigDecimal?

    @Query("SELECT SUM(total_amount) FROM milk_purchase WHERE supplier_id_fk = :supplierId AND purchase_date LIKE :yearMonth || '%'")
    suspend fun getSupplierTotalAmountForMonth(supplierId: Long, yearMonth: String): BigDecimal?

    @Query("UPDATE milk_purchase SET is_paid = :isPaid, payment_date = :paymentDate, is_synced = 0, updated_at = :updatedAt WHERE supplier_id_fk = :supplierId AND purchase_date LIKE :yearMonth || '%'")
    suspend fun updateMonthPaidStatus(supplierId: Long, yearMonth: String, isPaid: Int, paymentDate: String?, updatedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(purchase: MilkPurchase): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(purchases: List<MilkPurchase>): List<Long>

    @Update
    suspend fun update(purchase: MilkPurchase): Int

    @Query("DELETE FROM milk_purchase WHERE purchase_id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM milk_purchase WHERE supplier_id_fk = :supplierId AND purchase_date = :date AND purchase_session = :session")
    suspend fun deleteForSupplierDateSession(supplierId: Long, date: String, session: String): Int
}
