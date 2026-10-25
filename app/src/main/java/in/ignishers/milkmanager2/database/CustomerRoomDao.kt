package `in`.ignishers.milkmanager2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.ignishers.milkmanager2.models.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerRoomDao {

    @Query("SELECT * FROM customer ORDER BY sort_order ASC, customer_id ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customer ORDER BY sort_order ASC, customer_id ASC")
    suspend fun getAllCustomersList(): List<Customer>

    @Query("SELECT * FROM customer WHERE route_id_fk = :routeId ORDER BY sort_order ASC, customer_id ASC")
    fun getCustomersByRoute(routeId: Long): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE route_id_fk IS NULL OR route_id_fk = 0 ORDER BY sort_order ASC, customer_id ASC")
    fun getRootCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE customer_id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<Customer>): List<Long>

    @Update
    suspend fun update(customer: Customer): Int

    @Query("UPDATE customer SET sort_order = :newOrder, is_synced = 0, updated_at = :updatedAt WHERE customer_id = :id")
    suspend fun updateSortOrder(id: Long, newOrder: Int, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE customer SET auto_entry_enabled = :enabled, is_synced = 0, updated_at = :updatedAt WHERE customer_id = :id")
    suspend fun updateAutoEntry(id: Long, enabled: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM customer WHERE customer_id = :id")
    suspend fun delete(id: Long): Int
}
