package `in`.ignishers.milkmanager2.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_group",
    indices = [
        Index(value = ["parent_group_id"]),
        Index(value = ["sort_order"])
    ]
)
class RouteGroup {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "group_id")
    var id: Long? = null

    @ColumnInfo(name = "parent_group_id")
    var parentId: Long? = null

    @ColumnInfo(name = "group_name")
    var name: String? = null

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    var sortOrder: Int? = 0

    @ColumnInfo(name = "seller_id")
    var syncSellerId: String? = null

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    var syncIsSynced: Int? = 0

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var syncUpdatedAt: Long? = 0

    @ColumnInfo(name = "remote_id")
    var remoteId: Long? = null

    constructor()

    @Ignore
    constructor(id: Long, parentId: Long?, name: String?) {
        this.id = id
        this.parentId = parentId
        this.name = name
    }
}