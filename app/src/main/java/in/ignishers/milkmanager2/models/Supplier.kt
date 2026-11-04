package `in`.ignishers.milkmanager2.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "supplier",
    indices = [
        Index(value = ["sort_order"]),
        Index(value = ["is_active"])
    ]
)
data class Supplier(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "supplier_id")
    var id: Long = 0,

    @ColumnInfo(name = "supplier_name")
    var name: String,

    @ColumnInfo(name = "supplier_mobile")
    var mobile: String? = null,

    @ColumnInfo(name = "supplier_address")
    var address: String? = null,

    @ColumnInfo(name = "default_rate")
    var defaultRate: BigDecimal = BigDecimal("50.0"),

    @ColumnInfo(name = "is_active")
    var isActive: Int = 1,

    @ColumnInfo(name = "sort_order")
    var sortOrder: Int = 0,

    @ColumnInfo(name = "is_synced")
    var isSynced: Int = 0,

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis()
)
