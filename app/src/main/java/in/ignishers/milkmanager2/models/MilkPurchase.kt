package `in`.ignishers.milkmanager2.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "milk_purchase",
    indices = [
        Index(value = ["supplier_id_fk"]),
        Index(value = ["purchase_date"]),
        Index(value = ["purchase_date", "purchase_session"]),
        Index(value = ["supplier_id_fk", "purchase_date", "purchase_session"])
    ]
)
data class MilkPurchase(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "purchase_id")
    var id: Long = 0,

    @ColumnInfo(name = "supplier_id_fk")
    var supplierId: Long,

    @ColumnInfo(name = "purchase_date")
    var purchaseDate: String, // Format: YYYY-MM-DD

    @ColumnInfo(name = "purchase_session")
    var session: String, // "Morning" or "Evening"

    @ColumnInfo(name = "quantity")
    var quantity: BigDecimal,

    @ColumnInfo(name = "price_per_litre")
    var pricePerLitre: BigDecimal,

    @ColumnInfo(name = "total_amount")
    var totalAmount: BigDecimal,

    @ColumnInfo(name = "is_paid")
    var isPaid: Int = 0, // 0 = Unpaid, 1 = Paid

    @ColumnInfo(name = "payment_date")
    var paymentDate: String? = null,

    @ColumnInfo(name = "notes")
    var notes: String? = null,

    @ColumnInfo(name = "is_synced")
    var isSynced: Int = 0,

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis()
)
