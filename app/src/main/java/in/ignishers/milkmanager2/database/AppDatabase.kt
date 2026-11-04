package `in`.ignishers.milkmanager2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.ignishers.milkmanager2.models.AppSetting
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.MilkPurchase
import `in`.ignishers.milkmanager2.models.MilkRate
import `in`.ignishers.milkmanager2.models.Payment
import `in`.ignishers.milkmanager2.models.RouteGroup
import `in`.ignishers.milkmanager2.models.Supplier

@Database(
    entities = [
        Customer::class,
        RouteGroup::class,
        DailyTransaction::class,
        MilkRate::class,
        AppSetting::class,
        Payment::class,
        Supplier::class,
        MilkPurchase::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(BigDecimalConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerRoomDao
    abstract fun routeGroupDao(): RouteGroupRoomDao
    abstract fun dailyTransactionDao(): DailyTransactionRoomDao
    abstract fun milkRateDao(): MilkRateRoomDao
    abstract fun appSettingsDao(): AppSettingsRoomDao
    abstract fun paymentDao(): PaymentRoomDao
    abstract fun supplierDao(): SupplierRoomDao
    abstract fun milkPurchaseDao(): MilkPurchaseRoomDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `supplier` (
                        `supplier_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `supplier_name` TEXT NOT NULL,
                        `supplier_mobile` TEXT,
                        `supplier_address` TEXT,
                        `default_rate` TEXT NOT NULL,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_sort_order` ON `supplier` (`sort_order`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_is_active` ON `supplier` (`is_active`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `milk_purchase` (
                        `purchase_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `supplier_id_fk` INTEGER NOT NULL,
                        `purchase_date` TEXT NOT NULL,
                        `purchase_session` TEXT NOT NULL,
                        `quantity` TEXT NOT NULL,
                        `price_per_litre` TEXT NOT NULL,
                        `total_amount` TEXT NOT NULL,
                        `is_paid` INTEGER NOT NULL DEFAULT 0,
                        `payment_date` TEXT,
                        `notes` TEXT,
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_purchase_supplier_id_fk` ON `milk_purchase` (`supplier_id_fk`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_purchase_purchase_date` ON `milk_purchase` (`purchase_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_purchase_purchase_date_purchase_session` ON `milk_purchase` (`purchase_date`, `purchase_session`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milk_purchase_supplier_id_fk_purchase_date_purchase_session` ON `milk_purchase` (`supplier_id_fk`, `purchase_date`, `purchase_session`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "milky_mist_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
