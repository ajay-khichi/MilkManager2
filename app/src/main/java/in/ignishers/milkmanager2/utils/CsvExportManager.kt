package `in`.ignishers.milkmanager2.utils

import android.content.Context
import android.net.Uri
import `in`.ignishers.milkmanager2.database.AppDatabase

class CsvExportManager(private val context: Context, private val db: AppDatabase) {
    private val backupManager = DatabaseBackupManager(context, db)

    suspend fun exportCustomersToCsv(uri: Uri): Result<Int> {
        return backupManager.exportCustomersToCsv(uri)
    }

    suspend fun exportTransactionsToCsv(uri: Uri): Result<Int> {
        return backupManager.exportTransactionsToCsv(uri)
    }

    suspend fun exportAllTablesToZip(uri: Uri): Result<BackupStats> {
        return backupManager.exportAllTablesToZip(uri)
    }

    suspend fun exportSuppliersToCsv(uri: Uri): Result<Int> {
        return backupManager.exportSuppliersToCsv(uri)
    }

    suspend fun exportPurchasesToCsv(uri: Uri): Result<Int> {
        return backupManager.exportPurchasesToCsv(uri)
    }
}
