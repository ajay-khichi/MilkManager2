package `in`.ignishers.milkmanager2.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import `in`.ignishers.milkmanager2.database.AppDatabase
import `in`.ignishers.milkmanager2.utils.BackupStats
import `in`.ignishers.milkmanager2.utils.DatabaseBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriveBackupItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTimeMillis: Long
)

object GoogleDriveManager {

    private const val FOLDER_NAME = "MilkManager2_Backups"
    private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    private const val MIME_TYPE_JSON = "application/json"

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }

    fun getSignInClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            account
        } else {
            null
        }
    }

    suspend fun signOut(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Tasks.await(getSignInClient(context).signOut())
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("MilkManager2").build()
    }

    private fun getOrCreateFolderId(driveService: Drive): String {
        val query = "mimeType = '$MIME_TYPE_FOLDER' and name = '$FOLDER_NAME' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val existing = result.files?.firstOrNull()
        if (existing != null) {
            return existing.id
        }

        val folderMetadata = DriveFile().apply {
            name = FOLDER_NAME
            mimeType = MIME_TYPE_FOLDER
        }
        val created = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        return created.id
    }

    suspend fun uploadBackup(context: Context, account: GoogleSignInAccount): Result<DriveBackupItem> =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "milkmanager2_upload_temp.json")
            try {
                val db = AppDatabase.getDatabase(context)
                val backupManager = DatabaseBackupManager(context, db)
                val exportResult = backupManager.exportFullDatabaseToFile(tempFile)
                if (exportResult.isFailure) {
                    return@withContext Result.failure(
                        exportResult.exceptionOrNull() ?: Exception("Failed to generate local backup file")
                    )
                }

                val driveService = getDriveService(context, account)
                val folderId = getOrCreateFolderId(driveService)

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupFileName = "MilkManager_Backup_$timeStamp.json"

                val fileMetadata = DriveFile().apply {
                    name = backupFileName
                    parents = listOf(folderId)
                }

                val mediaContent = FileContent(MIME_TYPE_JSON, tempFile)
                val uploaded = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, size, modifiedTime")
                    .execute()

                val item = DriveBackupItem(
                    id = uploaded.id ?: "",
                    name = uploaded.name ?: backupFileName,
                    sizeBytes = uploaded.getSize() ?: tempFile.length(),
                    modifiedTimeMillis = uploaded.modifiedTime?.value ?: System.currentTimeMillis()
                )
                Result.success(item)
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

    suspend fun listBackups(context: Context, account: GoogleSignInAccount): Result<List<DriveBackupItem>> =
        withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(context, account)
                val folderId = getOrCreateFolderId(driveService)

                val query = "'$folderId' in parents and trashed = false"
                val fileList = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setOrderBy("modifiedTime desc")
                    .setFields("files(id, name, size, modifiedTime)")
                    .execute()

                val items = (fileList.files ?: emptyList()).map { file ->
                    DriveBackupItem(
                        id = file.id ?: "",
                        name = file.name ?: "Unknown",
                        sizeBytes = file.getSize() ?: 0L,
                        modifiedTimeMillis = file.modifiedTime?.value ?: 0L
                    )
                }
                Result.success(items)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    suspend fun restoreBackup(
        context: Context,
        account: GoogleSignInAccount,
        fileId: String
    ): Result<BackupStats> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "milkmanager2_download_temp.json")
        try {
            val driveService = getDriveService(context, account)
            tempFile.outputStream().use { outStream ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(outStream)
            }

            val db = AppDatabase.getDatabase(context)
            val backupManager = DatabaseBackupManager(context, db)
            val importResult = backupManager.importFullDatabaseFromFile(tempFile)
            importResult
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
