package `in`.ignishers.milkmanager2.utils

import android.content.Context
import android.net.Uri
import `in`.ignishers.milkmanager2.database.AppDatabase

class CsvImportManager(private val context: Context, private val db: AppDatabase) {
    private val backupManager = DatabaseBackupManager(context, db)

    suspend fun importCustomersFromCsv(uri: Uri): Result<Int> {
        val res = backupManager.importAnyBackup(uri)
        return if (res.isSuccess) {
            Result.success(1)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Import failed"))
        }
    }

    suspend fun importAnyBackup(uri: Uri): Result<String> {
        return backupManager.importAnyBackup(uri)
    }
}
