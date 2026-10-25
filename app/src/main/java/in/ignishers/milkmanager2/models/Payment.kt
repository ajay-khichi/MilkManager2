package `in`.ignishers.milkmanager2.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "payment",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["customer_id"],
            childColumns = ["customer_id_fk"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customer_id_fk"]),
        Index(value = ["payment_date"]),
        Index(value = ["customer_id_fk", "payment_date"])
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "payment_id")
    val paymentId: Long = 0,

    @ColumnInfo(name = "customer_id_fk")
    val customerId: Long,

    @ColumnInfo(name = "payment_date")
    val date: String,

    @ColumnInfo(name = "payment_amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "payment_mode")
    val paymentMode: String? = "Cash",

    @ColumnInfo(name = "payment_reference")
    val reference: String? = null,

    @ColumnInfo(name = "seller_id")
    val syncSellerId: String? = null,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val syncIsSynced: Int? = 0,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val syncUpdatedAt: Long? = 0,

    @ColumnInfo(name = "remote_id")
    val remoteId: Long? = null
)
