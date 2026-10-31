package `in`.ignishers.milkmanager2.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ignishers.milkmanager2.BuildConfig
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.database.AppDatabase
import `in`.ignishers.milkmanager2.database.AppSettingsRepository
import `in`.ignishers.milkmanager2.drive.DriveBackupItem
import `in`.ignishers.milkmanager2.drive.GoogleDriveManager
import `in`.ignishers.milkmanager2.scheduler.AutoEntryManager
import `in`.ignishers.milkmanager2.utils.DatabaseBackupManager
import `in`.ignishers.milkmanager2.utils.LocaleHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val backupManager = remember { DatabaseBackupManager(context, db) }
    val appSettingsRepo = remember { AppSettingsRepository(db.appSettingsDao()) }

    var selectedLangCode by remember { mutableStateOf(LocaleHelper.getSelectedLanguageCode()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    var googleAccount by remember { mutableStateOf(GoogleDriveManager.getSignedInAccount(context)) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var driveLoadingMessage by remember { mutableStateOf("") }
    var driveBackupsList by remember { mutableStateOf<List<DriveBackupItem>?>(null) }
    var showDriveRestoreDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            googleAccount = account
            Toast.makeText(context, context.getString(R.string.connected_as, account.email ?: ""), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in error: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun getTimeStamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    // 1. Export All Tables ZIP (Contains 6 individual CSVs)
    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = backupManager.exportAllTablesToZip(it)
                if (result.isSuccess) {
                    val stats = result.getOrNull()
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_success_stats, stats?.summary() ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 2. Export Full Database JSON
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = backupManager.exportFullDatabaseToJson(it)
                if (result.isSuccess) {
                    val stats = result.getOrNull()
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_success_stats, stats?.summary() ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 3. Export Customers Only CSV
    val exportCustomersLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = backupManager.exportCustomersToCsv(it)
                if (result.isSuccess) {
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 4. Export Transactions Only CSV
    val exportTransactionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = backupManager.exportTransactionsToCsv(it)
                if (result.isSuccess) {
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 5. Universal Smart Import (ZIP with CSVs, JSON Backup, or single CSV)
    val importUniversalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = backupManager.importAnyBackup(it)
                if (result.isSuccess) {
                    Toast.makeText(context, result.getOrNull() ?: "", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: context.getString(R.string.import_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val currentLanguageItem = LocaleHelper.getLanguageByCode(selectedLangCode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val isCompact = configuration.screenWidthDp < 360

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_language),
                            contentDescription = stringResource(R.string.language),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.choose_language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Interactive language selector button/surface
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = currentLanguageItem.nativeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentLanguageItem.englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { showLanguageDialog = true }
                            ) {
                                Text(stringResource(R.string.select_language))
                            }
                        }
                    }
                }
            }

            // Data Migration / Backup & Restore Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.backup_restore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = stringResource(R.string.import_backup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.export_data))
                        }

                        OutlinedButton(
                            onClick = {
                                importUniversalLauncher.launch(
                                    arrayOf("application/zip", "application/json", "text/csv", "text/comma-separated-values", "*/*")
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.import_data))
                        }
                    }

                    // Test entry notification button
                    OutlinedButton(
                        onClick = {
                            AutoEntryManager.sendAutoEntryNotification(
                                context = context,
                                session = "Morning",
                                processedCount = 3,
                                totalMilk = BigDecimal("6.5"),
                                totalAmount = BigDecimal("390.0")
                            )
                            Toast.makeText(context, "Notification sent! Check system tray.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.test_notification),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Google Drive Cloud Backup Card (Free Tier)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_drive),
                            contentDescription = "Google Drive",
                            modifier = Modifier.size(28.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.google_drive_backup),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.google_drive_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (googleAccount == null) {
                        Button(
                            onClick = {
                                val client = GoogleDriveManager.getSignInClient(context)
                                googleSignInLauncher.launch(client.signInIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_drive),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_in_google))
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Google Account",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = googleAccount?.email ?: "Connected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            GoogleDriveManager.signOut(context)
                                            googleAccount = null
                                            Toast.makeText(context, "Disconnected from Google Drive", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.disconnect_account),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (isDriveLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(
                                    text = driveLoadingMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        googleAccount?.let { account ->
                                            isDriveLoading = true
                                            driveLoadingMessage = context.getString(R.string.drive_backup_in_progress)
                                            coroutineScope.launch {
                                                val res = GoogleDriveManager.uploadBackup(context, account)
                                                isDriveLoading = false
                                                if (res.isSuccess) {
                                                    val item = res.getOrNull()
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.drive_backup_success, item?.name ?: ""),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Backup failed: ${res.exceptionOrNull()?.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.backup_to_drive_now), maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        googleAccount?.let { account ->
                                            isDriveLoading = true
                                            driveLoadingMessage = "Loading backups from Google Drive..."
                                            coroutineScope.launch {
                                                val res = GoogleDriveManager.listBackups(context, account)
                                                isDriveLoading = false
                                                if (res.isSuccess) {
                                                    val list = res.getOrNull() ?: emptyList()
                                                    if (list.isEmpty()) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.no_backups_found_on_drive),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        driveBackupsList = list
                                                        showDriveRestoreDialog = true
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Failed: ${res.exceptionOrNull()?.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.restore_from_drive), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // About Ignishers Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "IG",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_ignishers),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.ignishers_community_tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.ignishers_community_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.open_source), style = MaterialTheme.typography.labelSmall) }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.cybersecurity), style = MaterialTheme.typography.labelSmall) }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.offline_first), style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ignishers.in"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_language),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.visit_website), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MilkManager v${BuildConfig.VERSION_NAME} • Kotlin & Jetpack Compose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Language Selection Dialog
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_language),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.select_language),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(LocaleHelper.SUPPORTED_LANGUAGES) { lang ->
                            val isSelected = lang.code.equals(selectedLangCode, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLangCode = lang.code
                                        LocaleHelper.setLocale(lang.code)
                                        coroutineScope.launch {
                                            appSettingsRepo.setLanguage(lang.code)
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.language_changed_toast,
                                                "${lang.nativeName} (${lang.englishName})"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showLanguageDialog = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedLangCode = lang.code
                                            LocaleHelper.setLocale(lang.code)
                                            coroutineScope.launch {
                                                appSettingsRepo.setLanguage(lang.code)
                                            }
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.language_changed_toast,
                                                    "${lang.nativeName} (${lang.englishName})"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showLanguageDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lang.nativeName,
                                            fontSize = 17.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = lang.englishName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Export Format Selection Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.choose_export_format),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. All Tables CSV ZIP
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportDialog = false
                                    exportZipLauncher.launch("MilkManager_AllTables_${getTimeStamp()}.zip")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.export_all_tables_zip),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.export_all_tables_zip_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 2. Full Database JSON
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportDialog = false
                                    exportJsonLauncher.launch("MilkManager_Backup_${getTimeStamp()}.json")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.export_full_json),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.export_full_json_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 3. Customers Only CSV
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportDialog = false
                                    exportCustomersLauncher.launch("customers_export.csv")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.export_customers_csv),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. Transactions Only CSV
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportDialog = false
                                    exportTransactionsLauncher.launch("transactions_export.csv")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.export_transactions_csv),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Google Drive Restore Dialog
        if (showDriveRestoreDialog && driveBackupsList != null) {
            AlertDialog(
                onDismissRequest = { showDriveRestoreDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.select_backup_to_restore),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(driveBackupsList!!) { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        googleAccount?.let { account ->
                                            showDriveRestoreDialog = false
                                            isDriveLoading = true
                                            driveLoadingMessage = context.getString(R.string.drive_restore_in_progress)
                                            coroutineScope.launch {
                                                val res = GoogleDriveManager.restoreBackup(context, account, item.id)
                                                isDriveLoading = false
                                                if (res.isSuccess) {
                                                    val stats = res.getOrNull()
                                                    Toast.makeText(
                                                        context,
                                                        "Restore complete: ${stats?.summary() ?: ""}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Restore failed: ${res.exceptionOrNull()?.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dateStr = if (item.modifiedTimeMillis > 0) {
                                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                                .format(Date(item.modifiedTimeMillis))
                                        } else {
                                            "Unknown date"
                                        }
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${(item.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDriveRestoreDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
