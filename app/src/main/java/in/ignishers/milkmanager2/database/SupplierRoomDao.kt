package `in`.ignishers.milkmanager2.database

import androidx.room.*
import `in`.ignishers.milkmanager2.models.Supplier
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierRoomDao {

    @Query("SELECT * FROM supplier WHERE is_active = 1 ORDER BY sort_order ASC, supplier_name ASC")
    fun getAllActiveSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM supplier WHERE is_active = 1 ORDER BY sort_order ASC, supplier_name ASC")
    suspend fun getAllActiveSuppliersList(): List<Supplier>

    @Query("SELECT * FROM supplier ORDER BY supplier_id ASC")
    suspend fun getAllSuppliersList(): List<Supplier>

    @Query("SELECT * FROM supplier WHERE supplier_id = :id")
    suspend fun getSupplierById(id: Long): Supplier?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supplier: Supplier): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(suppliers: List<Supplier>): List<Long>

    @Update
    suspend fun update(supplier: Supplier): Int

    @Query("UPDATE supplier SET is_active = 0, updated_at = :updatedAt, is_synced = 0 WHERE supplier_id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM supplier WHERE supplier_id = :id")
    suspend fun delete(id: Long): Int
}
