package `in`.ignishers.milkmanager2.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import `in`.ignishers.milkmanager2.database.AppDatabase
import `in`.ignishers.milkmanager2.models.AppSetting
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.MilkRate
import `in`.ignishers.milkmanager2.models.Payment
import `in`.ignishers.milkmanager2.models.RouteGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupStats(
    var customersCount: Int = 0,
    var routesCount: Int = 0,
    var transactionsCount: Int = 0,
    var paymentsCount: Int = 0,
    var ratesCount: Int = 0,
    var settingsCount: Int = 0
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (customersCount > 0) parts.add("$customersCount customers")
        if (routesCount > 0) parts.add("$routesCount routes")
        if (transactionsCount > 0) parts.add("$transactionsCount transactions")
        if (paymentsCount > 0) parts.add("$paymentsCount payments")
        if (ratesCount > 0) parts.add("$ratesCount rates")
        if (settingsCount > 0) parts.add("$settingsCount settings")
        return if (parts.isEmpty()) "0 records" else parts.joinToString(", ")
    }
}

data class DatabaseBackupJson(
    val version: Int = 1,
    val app: String = "MilkManager2",
    val exportTimestamp: Long = System.currentTimeMillis(),
    val routes: List<RouteGroup> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val transactions: List<DailyTransaction> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val rates: List<MilkRate> = emptyList(),
    val settings: List<AppSetting> = emptyList()
)

class DatabaseBackupManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // -------------------------------------------------------------------------
    // 1. ALL TABLES CSV EXPORT (ZIP Container with 6 CSV files)
    // -------------------------------------------------------------------------
    suspend fun exportAllTablesToZip(uri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val stats = BackupStats()
            context.contentResolver.openOutputStream(uri)?.use { rawOut ->
                ZipOutputStream(rawOut.buffered(32768)).use { zos ->
                    val writer = OutputStreamWriter(zos, StandardCharsets.UTF_8)

                    // 1. RouteGroup
                    val routes = db.routeGroupDao().getAllRouteGroupsList()
                    stats.routesCount = routes.size
                    zos.putNextEntry(ZipEntry("route_group.csv"))
                    writer.write("group_id,parent_group_id,group_name,sort_order,seller_id,is_synced,updated_at,remote_id\n")
                    for (r in routes) {
                        writer.write(
                            "${r.id ?: ""}," +
                            "${r.parentId ?: ""}," +
                            "${escapeCsv(r.name)}," +
                            "${r.sortOrder ?: 0}," +
                            "${escapeCsv(r.syncSellerId)}," +
                            "${r.syncIsSynced ?: 0}," +
                            "${r.syncUpdatedAt ?: 0}," +
                            "${r.remoteId ?: ""}\n"
                        )
                    }
                    writer.flush()
                    zos.closeEntry()

                    // 2. Customer
                    val customers = db.customerDao().getAllCustomersList()
                    stats.customersCount = customers.size
                    zos.putNextEntry(ZipEntry("customer.csv"))
                    writer.write("customer_id,customer_name,customer_mobile,route_id_fk,address_detail,default_qty_morning,default_qty_evening,customer_due_balance,sort_order,auto_entry_enabled,seller_id,is_synced,updated_at,remote_id\n")
                    for (c in customers) {
                        writer.write(
                            "${c.id ?: ""}," +
                            "${escapeCsv(c.name)}," +
                            "${escapeCsv(c.mobile)}," +
                            "${c.routeGroupId ?: ""}," +
                            "${escapeCsv(c.address)}," +
                            "${c.defaultQtyMorning ?: BigDecimal.ZERO}," +
                            "${c.defaultQtyEvening ?: BigDecimal.ZERO}," +
                            "${c.currentDue ?: BigDecimal.ZERO}," +
                            "${c.sortOrder ?: 0}," +
                            "${if (c.autoEntryEnabled) 1 else 0}," +
                            "${escapeCsv(c.syncSellerId)}," +
                            "${c.syncIsSynced ?: 0}," +
                            "${c.syncUpdatedAt ?: 0}," +
                            "${c.remoteId ?: ""}\n"
                        )
                    }
                    writer.flush()
                    zos.closeEntry()

                    // 3. DailyTransaction
                    val transactions = db.dailyTransactionDao().getAllTransactionsList()
                    stats.transactionsCount = transactions.size
                    zos.putNextEntry(ZipEntry("milk_transaction.csv"))
                    writer.write("transaction_id,customer_id_fk,transaction_date,transaction_session,transaction_quantity,transaction_amount,transaction_timestamp,transaction_milk_type,seller_id,is_synced,updated_at,remote_id\n")
                    for (t in transactions) {
                        writer.write(
                            "${t.transactionId}," +
                            "${t.customerId}," +
                            "${escapeCsv(t.date)}," +
                            "${escapeCsv(t.session)}," +
                            "${t.quantity ?: BigDecimal.ZERO}," +
                            "${t.amount ?: BigDecimal.ZERO}," +
                            "${escapeCsv(t.timestamp)}," +
                            "${escapeCsv(t.milkType)}," +
                            "${escapeCsv(t.syncSellerId)}," +
                            "${t.syncIsSynced ?: 0}," +
                            "${t.syncUpdatedAt ?: 0}," +
                            "${t.remoteId ?: ""}\n"
                        )
                    }
                    writer.flush()
                    zos.closeEntry()

                    // 4. Payment
                    val payments = db.paymentDao().getAllPaymentsList()
                    stats.paymentsCount = payments.size
                    zos.putNextEntry(ZipEntry("payment.csv"))
                    writer.write("payment_id,customer_id_fk,payment_date,payment_amount,payment_mode,payment_reference,seller_id,is_synced,updated_at,remote_id\n")
                    for (p in payments) {
                        writer.write(
                            "${p.paymentId}," +
                            "${p.customerId}," +
                            "${escapeCsv(p.date)}," +
                            "${p.amount}," +
                            "${escapeCsv(p.paymentMode)}," +
                            "${escapeCsv(p.reference)}," +
                            "${escapeCsv(p.syncSellerId)}," +
                            "${p.syncIsSynced ?: 0}," +
                            "${p.syncUpdatedAt ?: 0}," +
                            "${p.remoteId ?: ""}\n"
                        )
                    }
                    writer.flush()
                    zos.closeEntry()

                    // 5. Global Milk Price
                    val rates = db.milkRateDao().getAllRatesList()
                    stats.ratesCount = rates.size
                    zos.putNextEntry(ZipEntry("global_milk_price.csv"))
                    writer.write("price_id,price_per_litre,effective_date,seller_id,is_synced,updated_at,remote_id\n")
                    for (r in rates) {
                        writer.write(
                            "${r.id}," +
                            "${r.price}," +
                            "${escapeCsv(r.effectiveDate)}," +
                            "${escapeCsv(r.syncSellerId)}," +
                            "${r.syncIsSynced ?: 0}," +
                            "${r.syncUpdatedAt ?: 0}," +
                            "${r.remoteId ?: ""}\n"
                        )
                    }
                    writer.flush()
                    zos.closeEntry()

                    // 6. App Settings
                    val settings = db.appSettingsDao().getAllSettingsList()
                    stats.settingsCount = settings.size
                    zos.putNextEntry(ZipEntry("app_settings.csv"))
                    writer.write("setting_key,setting_value\n")
                    for (s in settings) {
                        writer.write(
                            "${escapeCsv(s.key)}," +
                            "${escapeCsv(s.value)}\n"
                        )
                    }
                    writer.flush()
                    zos.closeEntry()
                }
            }
            Result.success(stats)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 2. ALL TABLES CSV IMPORT (From ZIP containing CSV files)
    // -------------------------------------------------------------------------
    suspend fun importAllTablesFromZip(uri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val stats = BackupStats()
            val rawIn = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            // Temporary memory holding lists for safe ordered insert
            val routeList = mutableListOf<RouteGroup>()
            val customerList = mutableListOf<Customer>()
            val transactionList = mutableListOf<DailyTransaction>()
            val paymentList = mutableListOf<Payment>()
            val rateList = mutableListOf<MilkRate>()
            val settingList = mutableListOf<AppSetting>()

            ZipInputStream(rawIn.buffered(32768)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.substringAfterLast("/").lowercase()
                    val reader = BufferedReader(InputStreamReader(zis, StandardCharsets.UTF_8))

                    when {
                        entryName == "route_group.csv" -> {
                            parseRouteCsv(reader, routeList)
                        }
                        entryName == "customer.csv" -> {
                            parseCustomerCsv(reader, customerList)
                        }
                        entryName == "milk_transaction.csv" -> {
                            parseTransactionCsv(reader, transactionList)
                        }
                        entryName == "payment.csv" -> {
                            parsePaymentCsv(reader, paymentList)
                        }
                        entryName == "global_milk_price.csv" -> {
                            parseMilkRateCsv(reader, rateList)
                        }
                        entryName == "app_settings.csv" -> {
                            parseAppSettingsCsv(reader, settingList)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Execute batch inserts inside database transaction in foreign-key order
            db.runInTransaction {
                if (routeList.isNotEmpty()) {
                    routeList.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.routeGroupDao().insertAll(chunk) }
                    }
                    stats.routesCount = routeList.size
                }
                if (customerList.isNotEmpty()) {
                    customerList.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.customerDao().insertAll(chunk) }
                    }
                    stats.customersCount = customerList.size
                }
                if (transactionList.isNotEmpty()) {
                    transactionList.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.dailyTransactionDao().insertAll(chunk) }
                    }
                    stats.transactionsCount = transactionList.size
                }
                if (paymentList.isNotEmpty()) {
                    paymentList.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.paymentDao().insertAll(chunk) }
                    }
                    stats.paymentsCount = paymentList.size
                }
                if (rateList.isNotEmpty()) {
                    rateList.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.milkRateDao().insertAll(chunk) }
                    }
                    stats.ratesCount = rateList.size
                }
                if (settingList.isNotEmpty()) {
                    settingList.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.appSettingsDao().insertAll(chunk) }
                    }
                    stats.settingsCount = settingList.size
                }
            }

            Result.success(stats)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 3. FULL DATABASE JSON BACKUP & RESTORE
    // -------------------------------------------------------------------------
    suspend fun exportFullDatabaseToJson(uri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val stats = BackupStats()
            val routes = db.routeGroupDao().getAllRouteGroupsList()
            val customers = db.customerDao().getAllCustomersList()
            val transactions = db.dailyTransactionDao().getAllTransactionsList()
            val payments = db.paymentDao().getAllPaymentsList()
            val rates = db.milkRateDao().getAllRatesList()
            val settings = db.appSettingsDao().getAllSettingsList()

            stats.routesCount = routes.size
            stats.customersCount = customers.size
            stats.transactionsCount = transactions.size
            stats.paymentsCount = payments.size
            stats.ratesCount = rates.size
            stats.settingsCount = settings.size

            val backup = DatabaseBackupJson(
                version = 1,
                app = "MilkManager2",
                exportTimestamp = System.currentTimeMillis(),
                routes = routes,
                customers = customers,
                transactions = transactions,
                payments = payments,
                rates = rates,
                settings = settings
            )

            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                BufferedWriter(OutputStreamWriter(outStream, StandardCharsets.UTF_8), 32768).use { writer ->
                    gson.toJson(backup, writer)
                }
            }
            Result.success(stats)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun importFullDatabaseFromJson(uri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val stats = BackupStats()
            val rawIn = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            val backup: DatabaseBackupJson = BufferedReader(
                InputStreamReader(rawIn, StandardCharsets.UTF_8), 32768
            ).use { reader ->
                gson.fromJson(reader, object : TypeToken<DatabaseBackupJson>() {}.type)
            }

            db.runInTransaction {
                if (backup.routes.isNotEmpty()) {
                    backup.routes.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.routeGroupDao().insertAll(chunk) }
                    }
                    stats.routesCount = backup.routes.size
                }
                if (backup.customers.isNotEmpty()) {
                    backup.customers.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.customerDao().insertAll(chunk) }
                    }
                    stats.customersCount = backup.customers.size
                }
                if (backup.transactions.isNotEmpty()) {
                    backup.transactions.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.dailyTransactionDao().insertAll(chunk) }
                    }
                    stats.transactionsCount = backup.transactions.size
                }
                if (backup.payments.isNotEmpty()) {
                    backup.payments.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.paymentDao().insertAll(chunk) }
                    }
                    stats.paymentsCount = backup.payments.size
                }
                if (backup.rates.isNotEmpty()) {
                    backup.rates.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.milkRateDao().insertAll(chunk) }
                    }
                    stats.ratesCount = backup.rates.size
                }
                if (backup.settings.isNotEmpty()) {
                    backup.settings.chunked(500).forEach { chunk ->
                        kotlinx.coroutines.runBlocking { db.appSettingsDao().insertAll(chunk) }
                    }
                    stats.settingsCount = backup.settings.size
                }
            }
            Result.success(stats)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 4. SINGLE CSV EXPORTS (Customers & Transactions)
    // -------------------------------------------------------------------------
    suspend fun exportCustomersToCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val customers = db.customerDao().getAllCustomersList()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 16384).use { writer ->
                    writer.write("customer_id,customer_name,customer_mobile,route_id_fk,address_detail,default_qty_morning,default_qty_evening,customer_due_balance,sort_order,auto_entry_enabled,seller_id,is_synced,updated_at,remote_id\n")
                    for (c in customers) {
                        writer.write(
                            "${c.id ?: ""}," +
                            "${escapeCsv(c.name)}," +
                            "${escapeCsv(c.mobile)}," +
                            "${c.routeGroupId ?: ""}," +
                            "${escapeCsv(c.address)}," +
                            "${c.defaultQtyMorning ?: BigDecimal.ZERO}," +
                            "${c.defaultQtyEvening ?: BigDecimal.ZERO}," +
                            "${c.currentDue ?: BigDecimal.ZERO}," +
                            "${c.sortOrder ?: 0}," +
                            "${if (c.autoEntryEnabled) 1 else 0}," +
                            "${escapeCsv(c.syncSellerId)}," +
                            "${c.syncIsSynced ?: 0}," +
                            "${c.syncUpdatedAt ?: 0}," +
                            "${c.remoteId ?: ""}\n"
                        )
                    }
                }
            }
            Result.success(customers.size)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun exportTransactionsToCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val transactions = db.dailyTransactionDao().getAllTransactionsList()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 16384).use { writer ->
                    writer.write("transaction_id,customer_id_fk,transaction_date,transaction_session,transaction_quantity,transaction_amount,transaction_timestamp,transaction_milk_type,seller_id,is_synced,updated_at,remote_id\n")
                    for (t in transactions) {
                        writer.write(
                            "${t.transactionId}," +
                            "${t.customerId}," +
                            "${escapeCsv(t.date)}," +
                            "${escapeCsv(t.session)}," +
                            "${t.quantity ?: BigDecimal.ZERO}," +
                            "${t.amount ?: BigDecimal.ZERO}," +
                            "${escapeCsv(t.timestamp)}," +
                            "${escapeCsv(t.milkType)}," +
                            "${escapeCsv(t.syncSellerId)}," +
                            "${t.syncIsSynced ?: 0}," +
                            "${t.syncUpdatedAt ?: 0}," +
                            "${t.remoteId ?: ""}\n"
                        )
                    }
                }
            }
            Result.success(transactions.size)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 5. SMART UNIVERSAL IMPORT
    // Detects ZIP, JSON, or CSV file format automatically and imports safely
    // -------------------------------------------------------------------------
    suspend fun importAnyBackup(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            // First inspect initial bytes to detect format
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open selected file"))

            val headerBytes = ByteArray(4)
            val bytesRead = stream.read(headerBytes)
            stream.close()

            val isZip = bytesRead >= 4 &&
                    headerBytes[0] == 0x50.toByte() &&
                    headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() &&
                    headerBytes[3] == 0x04.toByte()

            if (isZip) {
                val res = importAllTablesFromZip(uri)
                if (res.isSuccess) {
                    return@withContext Result.success("ZIP Archive restored: ${res.getOrNull()?.summary()}")
                } else {
                    return@withContext Result.failure(res.exceptionOrNull() ?: Exception("ZIP import failed"))
                }
            }

            // Check if JSON
            val firstLine = context.contentResolver.openInputStream(uri)?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readLine()?.trim()
            } ?: ""

            if (firstLine.startsWith("{")) {
                val res = importFullDatabaseFromJson(uri)
                if (res.isSuccess) {
                    return@withContext Result.success("JSON Backup restored: ${res.getOrNull()?.summary()}")
                } else {
                    return@withContext Result.failure(res.exceptionOrNull() ?: Exception("JSON restore failed"))
                }
            }

            // Otherwise treat as CSV - determine table type by header line
            val lowerHeader = firstLine.lowercase()
            when {
                lowerHeader.contains("group_name") -> {
                    val list = mutableListOf<RouteGroup>()
                    context.contentResolver.openInputStream(uri)?.use {
                        parseRouteCsv(BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)), list)
                    }
                    if (list.isNotEmpty()) {
                        list.chunked(500).forEach { db.routeGroupDao().insertAll(it) }
                    }
                    Result.success("Imported ${list.size} routes from CSV")
                }
                lowerHeader.contains("customer_name") || lowerHeader.contains("customer") -> {
                    val list = mutableListOf<Customer>()
                    context.contentResolver.openInputStream(uri)?.use {
                        parseCustomerCsv(BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)), list)
                    }
                    if (list.isNotEmpty()) {
                        list.chunked(500).forEach { db.customerDao().insertAll(it) }
                    }
                    Result.success("Imported ${list.size} customers from CSV")
                }
                lowerHeader.contains("transaction_quantity") || lowerHeader.contains("transaction") -> {
                    val list = mutableListOf<DailyTransaction>()
                    context.contentResolver.openInputStream(uri)?.use {
                        parseTransactionCsv(BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)), list)
                    }
                    if (list.isNotEmpty()) {
                        list.chunked(500).forEach { db.dailyTransactionDao().insertAll(it) }
                    }
                    Result.success("Imported ${list.size} transactions from CSV")
                }
                lowerHeader.contains("payment_amount") || lowerHeader.contains("payment") -> {
                    val list = mutableListOf<Payment>()
                    context.contentResolver.openInputStream(uri)?.use {
                        parsePaymentCsv(BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)), list)
                    }
                    if (list.isNotEmpty()) {
                        list.chunked(500).forEach { db.paymentDao().insertAll(it) }
                    }
                    Result.success("Imported ${list.size} payments from CSV")
                }
                lowerHeader.contains("price_per_litre") -> {
                    val list = mutableListOf<MilkRate>()
                    context.contentResolver.openInputStream(uri)?.use {
                        parseMilkRateCsv(BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)), list)
                    }
                    if (list.isNotEmpty()) {
                        list.chunked(500).forEach { db.milkRateDao().insertAll(it) }
                    }
                    Result.success("Imported ${list.size} milk price records from CSV")
                }
                else -> {
                    Result.failure(Exception("Unrecognized CSV format or unsupported file type"))
                }
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // CSV Parsing Helpers
    // -------------------------------------------------------------------------
    private fun escapeCsv(value: Any?): String {
        if (value == null) return ""
        val str = value.toString()
        return if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            "\"${str.replace("\"", "\"\"")}\""
        } else {
            str
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cur.append(c)
                }
            } else {
                if (c == '"') {
                    inQuotes = true
                } else if (c == ',') {
                    result.add(cur.toString().trim())
                    cur = StringBuilder()
                } else {
                    cur.append(c)
                }
            }
            i++
        }
        result.add(cur.toString().trim())
        return result
    }

    private fun parseRouteCsv(reader: BufferedReader, out: MutableList<RouteGroup>) {
        val header = reader.readLine() ?: return
        var line: String? = reader.readLine()
        while (line != null) {
            val p = parseCsvLine(line)
            if (p.isNotEmpty() && p.any { it.isNotEmpty() }) {
                val route = RouteGroup().apply {
                    id = p.getOrNull(0)?.toLongOrNull()
                    parentId = p.getOrNull(1)?.toLongOrNull()
                    name = p.getOrNull(2)
                    sortOrder = p.getOrNull(3)?.toIntOrNull() ?: 0
                    syncSellerId = p.getOrNull(4)
                    syncIsSynced = p.getOrNull(5)?.toIntOrNull() ?: 0
                    syncUpdatedAt = p.getOrNull(6)?.toLongOrNull() ?: 0L
                    remoteId = p.getOrNull(7)?.toLongOrNull()
                }
                out.add(route)
            }
            line = reader.readLine()
        }
    }

    private fun parseCustomerCsv(reader: BufferedReader, out: MutableList<Customer>) {
        val header = reader.readLine() ?: return
        var line: String? = reader.readLine()
        while (line != null) {
            val p = parseCsvLine(line)
            if (p.isNotEmpty() && p.any { it.isNotEmpty() }) {
                val customer = Customer().apply {
                    id = p.getOrNull(0)?.toLongOrNull()
                    name = p.getOrNull(1)
                    mobile = p.getOrNull(2)
                    routeGroupId = p.getOrNull(3)?.toLongOrNull()
                    address = p.getOrNull(4)
                    defaultQtyMorning = p.getOrNull(5)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    defaultQtyEvening = p.getOrNull(6)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    currentDue = p.getOrNull(7)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    sortOrder = p.getOrNull(8)?.toIntOrNull() ?: 0
                    autoEntryEnabled = (p.getOrNull(9) == "1" || p.getOrNull(9).equals("true", ignoreCase = true))
                    syncSellerId = p.getOrNull(10)
                    syncIsSynced = p.getOrNull(11)?.toIntOrNull() ?: 0
                    syncUpdatedAt = p.getOrNull(12)?.toLongOrNull() ?: 0L
                    remoteId = p.getOrNull(13)?.toLongOrNull()
                }
                out.add(customer)
            }
            line = reader.readLine()
        }
    }

    private fun parseTransactionCsv(reader: BufferedReader, out: MutableList<DailyTransaction>) {
        val header = reader.readLine() ?: return
        var line: String? = reader.readLine()
        while (line != null) {
            val p = parseCsvLine(line)
            if (p.isNotEmpty() && p.any { it.isNotEmpty() }) {
                val txn = DailyTransaction(
                    transactionId = p.getOrNull(0)?.toIntOrNull() ?: 0,
                    customerId = p.getOrNull(1)?.toLongOrNull() ?: 0L,
                    date = p.getOrNull(2),
                    session = p.getOrNull(3),
                    quantity = p.getOrNull(4)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    amount = p.getOrNull(5)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    timestamp = p.getOrNull(6),
                    milkType = p.getOrNull(7) ?: "Regular",
                    syncSellerId = p.getOrNull(8),
                    syncIsSynced = p.getOrNull(9)?.toIntOrNull() ?: 0,
                    syncUpdatedAt = p.getOrNull(10)?.toLongOrNull() ?: 0L,
                    remoteId = p.getOrNull(11)?.toLongOrNull()
                )
                out.add(txn)
            }
            line = reader.readLine()
        }
    }

    private fun parsePaymentCsv(reader: BufferedReader, out: MutableList<Payment>) {
        val header = reader.readLine() ?: return
        var line: String? = reader.readLine()
        while (line != null) {
            val p = parseCsvLine(line)
            if (p.isNotEmpty() && p.any { it.isNotEmpty() }) {
                val payment = Payment(
                    paymentId = p.getOrNull(0)?.toLongOrNull() ?: 0L,
                    customerId = p.getOrNull(1)?.toLongOrNull() ?: 0L,
                    date = p.getOrNull(2) ?: "",
                    amount = p.getOrNull(3)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    paymentMode = p.getOrNull(4) ?: "Cash",
                    reference = p.getOrNull(5),
                    syncSellerId = p.getOrNull(6),
                    syncIsSynced = p.getOrNull(7)?.toIntOrNull() ?: 0,
                    syncUpdatedAt = p.getOrNull(8)?.toLongOrNull() ?: 0L,
                    remoteId = p.getOrNull(9)?.toLongOrNull()
                )
                out.add(payment)
            }
            line = reader.readLine()
        }
    }

    private fun parseMilkRateCsv(reader: BufferedReader, out: MutableList<MilkRate>) {
        val header = reader.readLine() ?: return
        var line: String? = reader.readLine()
        while (line != null) {
            val p = parseCsvLine(line)
            if (p.isNotEmpty() && p.any { it.isNotEmpty() }) {
                val rate = MilkRate(
                    id = p.getOrNull(0)?.toIntOrNull() ?: 0,
                    price = p.getOrNull(1)?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    effectiveDate = p.getOrNull(2) ?: "",
                    syncSellerId = p.getOrNull(3),
                    syncIsSynced = p.getOrNull(4)?.toIntOrNull() ?: 0,
                    syncUpdatedAt = p.getOrNull(5)?.toLongOrNull() ?: 0L,
                    remoteId = p.getOrNull(6)?.toLongOrNull()
                )
                out.add(rate)
            }
            line = reader.readLine()
        }
    }

    private fun parseAppSettingsCsv(reader: BufferedReader, out: MutableList<AppSetting>) {
        val header = reader.readLine() ?: return
        var line: String? = reader.readLine()
        while (line != null) {
            val p = parseCsvLine(line)
            if (p.size >= 2) {
                out.add(AppSetting(key = p[0], value = p[1]))
            }
            line = reader.readLine()
        }
    }
}
