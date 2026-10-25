package `in`.ignishers.milkmanager2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.ignishers.milkmanager2.models.AppSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsRoomDao {
    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettingsList(): List<AppSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<AppSetting>): List<Long>

    @Query("SELECT setting_value FROM app_settings WHERE setting_key = :key")
    suspend fun getSettingValue(key: String): String?

    @Query("SELECT setting_value FROM app_settings WHERE setting_key = :key")
    fun getSettingValueFlow(key: String): Flow<String?>
}
