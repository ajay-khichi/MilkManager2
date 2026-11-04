package `in`.ignishers.milkmanager2.ui.purchase

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.models.Supplier
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    onBack: () -> Unit,
    viewModel: PurchaseViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<Supplier?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.milk_purchase),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSupplierDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_supplier)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (uiState.activeTab == 0 && uiState.suppliers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSupplierDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_supplier)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Switch: Daily Entry vs Monthly Summary
            TabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = { Text(stringResource(R.string.daily_entry), fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = { Text(stringResource(R.string.monthly_summary), fontWeight = FontWeight.SemiBold) }
                )
            }

            if (uiState.activeTab == 0) {
                DailyEntryTab(
                    uiState = uiState,
                    onDateChange = { viewModel.setDate(it) },
                    onSessionChange = { viewModel.setSession(it) },
                    onQuantityChange = { id, text -> viewModel.updateQuantityInput(id, text) },
                    onRateChange = { id, text -> viewModel.updateRateInput(id, text) },
                    onSaveEntry = { id -> viewModel.savePurchaseEntry(id) },
                    onDeleteEntry = { id -> viewModel.deletePurchaseEntry(id) },
                    onEditSupplier = { s -> editingSupplier = s },
                    onAddSupplierClick = { showAddSupplierDialog = true }
                )
            } else {
                MonthlySummaryTab(
                    uiState = uiState,
                    onYearMonthChange = { viewModel.setYearMonth(it) },
                    onTogglePaid = { id, markPaid -> viewModel.toggleMonthPaidStatus(id, markPaid) },
                    onEditSupplier = { s -> editingSupplier = s }
                )
            }
        }
    }

    // Add / Edit Supplier Dialog
    if (showAddSupplierDialog || editingSupplier != null) {
        SupplierDialog(
            supplier = editingSupplier,
            onDismiss = {
                showAddSupplierDialog = false
                editingSupplier = null
            },
            onSave = { name, mobile, rate ->
                if (editingSupplier != null) {
                    val updated = editingSupplier!!.copy(
                        name = name,
                        mobile = mobile,
                        defaultRate = rate
                    )
                    viewModel.updateSupplier(updated)
                    Toast.makeText(context, "Supplier updated", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addSupplier(name, mobile, rate)
                    Toast.makeText(context, "Supplier added", Toast.LENGTH_SHORT).show()
                }
                showAddSupplierDialog = false
                editingSupplier = null
            },
            onDelete = if (editingSupplier != null) {
                {
                    viewModel.deleteSupplier(editingSupplier!!.id)
                    Toast.makeText(context, "Supplier removed", Toast.LENGTH_SHORT).show()
                    editingSupplier = null
                }
            } else null
        )
    }
}

@Composable
private fun DailyEntryTab(
    uiState: PurchaseUiState,
    onDateChange: (String) -> Unit,
    onSessionChange: (String) -> Unit,
    onQuantityChange: (Long, String) -> Unit,
    onRateChange: (Long, String) -> Unit,
    onSaveEntry: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onEditSupplier: (Supplier) -> Unit,
    onAddSupplierClick: () -> Unit
) {
    val context = LocalContext.current

    // Date & Session Bar
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        try {
                            cal.time = sdf.parse(uiState.selectedDate) ?: Date()
                        } catch (e: Exception) {
                            cal.time = Date()
                        }
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                        onDateChange(sdf.format(cal.time))
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        val cal = Calendar.getInstance()
                        val parts = uiState.selectedDate.split("-")
                        if (parts.size == 3) {
                            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val formatted = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                onDateChange(formatted)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = uiState.selectedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        try {
                            cal.time = sdf.parse(uiState.selectedDate) ?: Date()
                        } catch (e: Exception) {
                            cal.time = Date()
                        }
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        onDateChange(sdf.format(cal.time))
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                }
            }

            // Session Selector: Morning vs Evening
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isMorning = uiState.selectedSession.equals("Morning", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isMorning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isMorning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSessionChange("Morning") }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "☀️ Morning",
                            fontWeight = FontWeight.Bold,
                            color = if (isMorning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (!isMorning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (!isMorning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSessionChange("Evening") }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌙 Evening",
                            fontWeight = FontWeight.Bold,
                            color = if (!isMorning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Daily Summary Banner
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.today_procurement),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${uiState.todayTotalQuantity.stripTrailingZeros().toPlainString()} L",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (uiState.todayTotalAmount > BigDecimal.ZERO) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Estimated Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "₹${uiState.todayTotalAmount.stripTrailingZeros().toPlainString()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Suppliers Input List
    if (uiState.suppliers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.no_suppliers),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.add_supplier_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAddSupplierClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_supplier))
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.dailyInputs, key = { it.supplier.id }) { input ->
                SupplierDailyCard(
                    input = input,
                    onQuantityChange = { text -> onQuantityChange(input.supplier.id, text) },
                    onRateChange = { text -> onRateChange(input.supplier.id, text) },
                    onSave = { onSaveEntry(input.supplier.id) },
                    onDelete = { onDeleteEntry(input.supplier.id) },
                    onEditSupplier = { onEditSupplier(input.supplier) }
                )
            }
            item {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun SupplierDailyCard(
    input: SupplierDailyInputState,
    onQuantityChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onEditSupplier: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Supplier Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = input.supplier.name.take(1).uppercase(),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = input.supplier.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val subText = if (!input.supplier.mobile.isNullOrBlank()) {
                            "${input.supplier.mobile} • Default ₹${input.supplier.defaultRate}/L"
                        } else {
                            "Default ₹${input.supplier.defaultRate}/L"
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (input.isSaved) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.saved),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onEditSupplier,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Input Fields Row: Quantity & Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input.quantityInput,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.weight(1.3f),
                    label = { Text(stringResource(R.string.quantity_litres)) },
                    placeholder = { Text("0.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("L", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = input.rateInput,
                    onValueChange = onRateChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Rate") },
                    placeholder = { Text("50.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = onSave,
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (input.isSaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save"
                    )
                }
            }

            // Quick Litre Increment Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("10", "20", "30", "50").forEach { quickL ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable {
                            val cur = input.quantityInput.toDoubleOrNull() ?: 0.0
                            val next = cur + quickL.toDouble()
                            val s = if (next % 1.0 == 0.0) next.toInt().toString() else String.format(Locale.US, "%.1f", next)
                            onQuantityChange(s)
                        }
                    ) {
                        Text(
                            text = "+$quickL L",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                if (input.existingPurchaseId > 0) {
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryTab(
    uiState: PurchaseUiState,
    onYearMonthChange: (String) -> Unit,
    onTogglePaid: (Long, Boolean) -> Unit,
    onEditSupplier: (Supplier) -> Unit
) {
    val context = LocalContext.current

    // Month Selector Bar
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                    val cal = Calendar.getInstance()
                    try {
                        cal.time = sdf.parse(uiState.selectedYearMonth) ?: Date()
                    } catch (e: Exception) {
                        cal.time = Date()
                    }
                    cal.add(Calendar.MONTH, -1)
                    onYearMonthChange(sdf.format(cal.time))
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
            }

            val displayMonth = try {
                val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(uiState.selectedYearMonth)
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date ?: Date())
            } catch (e: Exception) {
                uiState.selectedYearMonth
            }

            Text(
                text = displayMonth,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                    val cal = Calendar.getInstance()
                    try {
                        cal.time = sdf.parse(uiState.selectedYearMonth) ?: Date()
                    } catch (e: Exception) {
                        cal.time = Date()
                    }
                    cal.add(Calendar.MONTH, 1)
                    onYearMonthChange(sdf.format(cal.time))
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
            }
        }
    }

    // Monthly Big Cards
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.total_purchased),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${uiState.monthTotalQuantity.stripTrailingZeros().toPlainString()} L",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Payable Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "₹${uiState.monthTotalAmount.stripTrailingZeros().toPlainString()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Supplier Summaries List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.monthlySummaries, key = { it.supplier.id }) { summary ->
                SupplierMonthlyCard(
                    summary = summary,
                    onTogglePaid = { markPaid -> onTogglePaid(summary.supplier.id, markPaid) },
                    onEditSupplier = { onEditSupplier(summary.supplier) }
                )
            }
        }
    }
}

@Composable
private fun SupplierMonthlyCard(
    summary: SupplierMonthlySummary,
    onTogglePaid: (Boolean) -> Unit,
    onEditSupplier: () -> Unit
) {
    var expandedDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.supplier.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!summary.supplier.mobile.isNullOrBlank()) {
                        Text(
                            text = summary.supplier.mobile!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Paid / Unpaid Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (summary.isPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = if (summary.isPaid) "Paid 🟢" else "Unpaid 🔴",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.isPaid) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Metrics row: Total Quantity + Split + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Milk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.totalQuantity.stripTrailingZeros().toPlainString()} L",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "☀️ ${summary.morningQuantity.stripTrailingZeros().toPlainString()}L | 🌙 ${summary.eveningQuantity.stripTrailingZeros().toPlainString()}L",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Amount (₹)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${summary.totalAmount.stripTrailingZeros().toPlainString()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${summary.entryCount} entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action row: Mark as Paid / Unpaid & Toggle Daily Log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { expandedDetails = !expandedDetails },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (expandedDetails) "Hide Daily Log ▲" else "View Daily Log ▼",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (summary.totalQuantity > BigDecimal.ZERO) {
                    if (summary.isPaid) {
                        OutlinedButton(
                            onClick = { onTogglePaid(false) },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.mark_unpaid), style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = { onTogglePaid(true) },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.mark_as_paid), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Expandable Daily Log
            if (expandedDetails && summary.dailyPurchases.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Daily Entries Log:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        summary.dailyPurchases.forEach { dp ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${dp.purchaseDate} (${dp.session.take(1)})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${dp.quantity.stripTrailingZeros().toPlainString()} L @ ₹${dp.pricePerLitre}/L",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "₹${dp.totalAmount.stripTrailingZeros().toPlainString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierDialog(
    supplier: Supplier?,
    onDismiss: () -> Unit,
    onSave: (String, String?, BigDecimal) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(supplier?.name ?: "") }
    var mobile by remember { mutableStateOf(supplier?.mobile ?: "") }
    var rateText by remember { mutableStateOf(supplier?.defaultRate?.stripTrailingZeros()?.toPlainString() ?: "50.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (supplier != null) stringResource(R.string.edit_supplier) else stringResource(R.string.add_supplier),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.supplier_name)) },
                    placeholder = { Text("e.g. Ramesh Patel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text(stringResource(R.string.default_rate_per_litre)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val rate = rateText.trim().toBigDecimalOrNull() ?: BigDecimal("50.0")
                        onSave(name, mobile.takeIf { it.isNotBlank() }, rate)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
