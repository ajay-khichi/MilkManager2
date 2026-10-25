package `in`.ignishers.milkmanager2.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "milk_transaction",
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
        Index(value = ["transaction_date"]),
        Index(value = ["customer_id_fk", "transaction_date"]),
        Index(value = ["customer_id_fk", "transaction_date", "transaction_session"])
    ]
)
data class DailyTransaction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transaction_id")
    val transactionId: Int = 0,

    @ColumnInfo(name = "customer_id_fk")
    val customerId: Long,

    @ColumnInfo(name = "transaction_date")
    val date: String?,

    @ColumnInfo(name = "transaction_session")
    val session: String? = null,

    @ColumnInfo(name = "transaction_quantity")
    val quantity: BigDecimal?,

    @ColumnInfo(name = "transaction_amount")
    val amount: BigDecimal?,

    @ColumnInfo(name = "transaction_timestamp")
    val timestamp: String? = null,


    @ColumnInfo(name = "transaction_milk_type")
    val milkType: String? = "Regular",

    @ColumnInfo(name = "seller_id")
    val syncSellerId: String? = null,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val syncIsSynced: Int? = 0,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val syncUpdatedAt: Long? = 0,

    @ColumnInfo(name = "remote_id")
    val remoteId: Long? = null
)