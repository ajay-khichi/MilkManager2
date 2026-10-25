package `in`.ignishers.milkmanager2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.ignishers.milkmanager2.models.RouteGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteGroupRoomDao {
    @Query("SELECT * FROM route_group ORDER BY sort_order ASC, group_id ASC")
    fun getAllRouteGroups(): Flow<List<RouteGroup>>

    @Query("SELECT * FROM route_group ORDER BY sort_order ASC, group_id ASC")
    suspend fun getAllRouteGroupsList(): List<RouteGroup>

    @Query("SELECT * FROM route_group WHERE (parent_group_id IS NULL OR parent_group_id = 0) AND group_id != 0 ORDER BY sort_order ASC, group_id ASC")
    fun getRootGroups(): Flow<List<RouteGroup>>

    @Query("SELECT * FROM route_group WHERE parent_group_id = :parentId AND group_id != 0 ORDER BY sort_order ASC, group_id ASC")
    fun getChildGroups(parentId: Long): Flow<List<RouteGroup>>

    @Query("SELECT * FROM route_group WHERE group_id = :id")
    suspend fun getGroupById(id: Long): RouteGroup?

    @Query("UPDATE route_group SET sort_order = :newOrder, is_synced = 0, updated_at = :updatedAt WHERE group_id = :id")
    suspend fun updateSortOrder(id: Long, newOrder: Int, updatedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routeGroup: RouteGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routeGroups: List<RouteGroup>): List<Long>

    @Update
    suspend fun update(routeGroup: RouteGroup): Int

    @Query("DELETE FROM route_group WHERE group_id = :id")
    suspend fun delete(id: Long): Int
}
