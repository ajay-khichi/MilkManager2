package `in`.ignishers.milkmanager2.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "customer",
    foreignKeys = [
        ForeignKey(
            entity = RouteGroup::class,
            parentColumns = ["group_id"],
            childColumns = ["route_id_fk"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["route_id_fk"]),
        Index(value = ["sort_order"])
    ]
)
class Customer {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "customer_id")
    @JvmField
    var id: Long? = null

    @ColumnInfo(name = "customer_name")
    @JvmField
    var name: String? = null

    @ColumnInfo(name = "customer_mobile")
    @JvmField
    var mobile: String? = null

    @ColumnInfo(name = "route_id_fk")
    var routeGroupId: Long? = null

    @ColumnInfo(name = "address_detail")
    @JvmField
    var address: String? = null

    @ColumnInfo(name = "default_qty_morning", defaultValue = "0.0")
    @JvmField
    var defaultQtyMorning: BigDecimal? = BigDecimal.ZERO

    @ColumnInfo(name = "default_qty_evening", defaultValue = "0.0")
    @JvmField
    var defaultQtyEvening: BigDecimal? = BigDecimal.ZERO

    @ColumnInfo(name = "customer_due_balance", defaultValue = "0")
    var currentDue: BigDecimal? = BigDecimal.ZERO

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    var sortOrder: Int? = 0

    @ColumnInfo(name = "auto_entry_enabled", defaultValue = "1")
    var autoEntryEnabled: Boolean = true

    @ColumnInfo(name = "seller_id")
    var syncSellerId: String? = null

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    var syncIsSynced: Int? = 0

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var syncUpdatedAt: Long? = 0

    @ColumnInfo(name = "remote_id")
    var remoteId: Long? = null

    // Ignored fields
    @Ignore
    var routeGroupName: String? = null

    @Ignore
    var morningQtySet: Boolean = false

    @Ignore
    var eveningQtySet: Boolean = false

    @Ignore
    var isVisited: Boolean = false

    constructor()

    @Ignore
    constructor(id: Long, name: String?, mobile: String?, routeGroupId: Long) {
        this.id = id
        this.name = name
        this.mobile = mobile
        this.routeGroupId = routeGroupId
        this.defaultQtyMorning = BigDecimal.ZERO
        this.defaultQtyEvening = BigDecimal.ZERO
        this.currentDue = BigDecimal.ZERO
    }

    @Ignore
    constructor(
        id: Long,
        name: String?,
        mobile: String?,
        currentDue: BigDecimal?
    ) {
        this.id = id
        this.name = name
        this.mobile = mobile
        this.currentDue = currentDue
        this.defaultQtyMorning = BigDecimal.ZERO
        this.defaultQtyEvening = BigDecimal.ZERO
    }

    @Ignore
    constructor(id: Long) {
        this.id = id
        this.defaultQtyMorning = BigDecimal.ZERO
        this.defaultQtyEvening = BigDecimal.ZERO
        this.currentDue = BigDecimal.ZERO
    }
}
