package `in`.ignishers.milkmanager2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.RouteGroup
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.MilkRate
import `in`.ignishers.milkmanager2.models.AppSetting
import `in`.ignishers.milkmanager2.models.Payment

@Database(entities = [Customer::class, RouteGroup::class, DailyTransaction::class, MilkRate::class, AppSetting::class, Payment::class], version = 2, exportSchema = false)
@TypeConverters(BigDecimalConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerRoomDao
    abstract fun routeGroupDao(): RouteGroupRoomDao
    abstract fun dailyTransactionDao(): DailyTransactionRoomDao
    abstract fun milkRateDao(): MilkRateRoomDao
    abstract fun appSettingsDao(): AppSettingsRoomDao
    abstract fun paymentDao(): PaymentRoomDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_route_id_fk` ON `customer` (`route_id_fk`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_sort_order` ON `customer` (`sort_order`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_transaction_customer_id_fk` ON `milk_transaction` (`customer_id_fk`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_transaction_transaction_date` ON `milk_transaction` (`transaction_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_transaction_customer_id_fk_transaction_date` ON `milk_transaction` (`customer_id_fk`, `transaction_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_transaction_customer_id_fk_transaction_date_transaction_session` ON `milk_transaction` (`customer_id_fk`, `transaction_date`, `transaction_session`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_customer_id_fk` ON `payment` (`customer_id_fk`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_payment_date` ON `payment` (`payment_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_customer_id_fk_payment_date` ON `payment` (`customer_id_fk`, `payment_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_group_parent_group_id` ON `route_group` (`parent_group_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_group_sort_order` ON `route_group` (`sort_order`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "milky_mist_db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
