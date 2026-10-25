package `in`.ignishers.milkmanager2.ui.pos

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.ui.dashboard.UnitSegmentedButtons
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.Payment
import `in`.ignishers.milkmanager2.models.Customer
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import `in`.ignishers.milkmanager2.utils.PDFGenerator
import kotlinx.coroutines.launch

sealed class TodayHistoryItem {
    data class Milk(val transaction: DailyTransaction) : TodayHistoryItem()
    data class Pay(val payment: Payment) : TodayHistoryItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PointOfSaleScreen(
    customerId: Long,
    onBack: () -> Unit,
    viewModel: PointOfSaleViewModel = koinViewModel()
) {
    val context = LocalContext.current

    var initialLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(customerId) {
        if (!initialLoaded) {
            viewModel.loadCustomer(customerId)
            initialLoaded = true
        }
    }

    val customer by viewModel.customer.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val session by viewModel.session.collectAsState()
    val milkRate by viewModel.milkRate.collectAsState()
    val todayTransactions by viewModel.todayTransactions.collectAsState()
    val todayPayments by viewModel.todayPayments.collectAsState()
    val currentMonthBill by viewModel.currentMonthBill.collectAsState()
    val lastMonthDue by viewModel.lastMonthDue.collectAsState()

    var showQuickMenu by remember { mutableStateOf(false) }
    var showManualEntrySheet by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<TodayHistoryItem?>(null) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDefaultQtyDialog by remember { mutableStateOf(false) }
    var showBillDialog by remember { mutableStateOf(false) }
    var statementPreviewData by remember { mutableStateOf<BillReportData?>(null) }
    var currentViewingMonth by remember { mutableStateOf<YearMonth?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val openBillForMonth: (YearMonth) -> Unit = { ym ->
        currentViewingMonth = ym
        coroutineScope.launch {
            val report = viewModel.getMonthlyBillReport(ym)
            if (report != null) {
                statementPreviewData = report
            } else {
                Toast.makeText(context, context.getString(R.string.could_not_load_bill, ym.format(DateTimeFormatter.ofPattern("MMMM yyyy"))), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(vertical = 4.dp),
                title = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = customer?.name ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (customer?.currentDue != null && customer?.currentDue!! > BigDecimal.ZERO) {
                            Text(
                                text = "Due: ₹${customer?.currentDue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Auto",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = customer?.autoEntryEnabled == true,
                            onCheckedChange = { enabled ->
                                viewModel.toggleCustomerAutoEntry(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.onPrimary,
                                checkedBorderColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                uncheckedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    // Three dots overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.options),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.default_quantity), fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showOverflowMenu = false
                                    showDefaultQtyDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bill_current_month), fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showOverflowMenu = false
                                    openBillForMonth(YearMonth.now())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.custom_statement_reports), fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showOverflowMenu = false
                                    showBillDialog = true
                                }
                            )
                        }
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
        val density = LocalDensity.current
        val swipeThreshold = remember(density) { with(density) { 60.dp.toPx() } }
        var totalDragX by remember { mutableFloatStateOf(0f) }
        val scrollState = rememberScrollState()

        LaunchedEffect(customer?.id) {
            scrollState.scrollTo(0)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                        },
                        onDragEnd = {
                            if (totalDragX < -swipeThreshold) {
                                // Left swipe -> Next Customer in route
                                val msgResId = viewModel.navigateCustomer(isNext = true)
                                if (msgResId != null) {
                                    Toast.makeText(context, context.getString(msgResId), Toast.LENGTH_SHORT).show()
                                }
                            } else if (totalDragX > swipeThreshold) {
                                // Right swipe -> Previous Customer in route
                                val msgResId = viewModel.navigateCustomer(isNext = false)
                                if (msgResId != null) {
                                    Toast.makeText(context, context.getString(msgResId), Toast.LENGTH_SHORT).show()
                                }
                            }
                            totalDragX = 0f
                        },
                        onDragCancel = {
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Customer Due & Balance Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Mobile No & Total Due Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.mobile_no), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = customer?.mobile?.takeIf { it.isNotBlank() } ?: "-",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.total_due), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "₹${customer?.currentDue ?: BigDecimal.ZERO}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if ((customer?.currentDue ?: BigDecimal.ZERO) > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Last Month Due, Bill Button & Pay Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.last_month_due), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (lastMonthDue > BigDecimal.ZERO) "₹$lastMonthDue" else "₹0",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (lastMonthDue > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { openBillForMonth(YearMonth.now()) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = stringResource(R.string.bill),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.bill), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { showPaymentDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.pay), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Session Segmented Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionButton(
                    text = "Morning",
                    isSelected = session == "Morning",
                    onClick = { viewModel.setSession("Morning") },
                    modifier = Modifier.weight(1f)
                )
                SessionButton(
                    text = "Evening",
                    isSelected = session == "Evening",
                    onClick = { viewModel.setSession("Evening") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Entry & Manual Entry Card (Replaces old Quantity Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Milk Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Date & Rate Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Date: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentDate,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Rate: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${milkRate}/L",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row with Quick Extras Dropdown and Manual Entry Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown Menu Button for Quick Quantities
                        var buttonWidthPx by remember { mutableIntStateOf(0) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onSizeChanged { buttonWidthPx = it.width }
                        ) {
                            OutlinedButton(
                                onClick = { showQuickMenu = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(R.string.quick_qty), fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (showQuickMenu) {
                                val density = LocalDensity.current
                                Popup(
                                    onDismissRequest = { showQuickMenu = false },
                                    popupPositionProvider = remember(density) {
                                        object : PopupPositionProvider {
                                            override fun calculatePosition(
                                                anchorBounds: IntRect,
                                                windowSize: IntSize,
                                                layoutDirection: LayoutDirection,
                                                popupContentSize: IntSize
                                            ): IntOffset {
                                                // Always open strictly below anchor (never flip upwards)
                                                val x = anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
                                                val y = anchorBounds.bottom + with(density) { 4.dp.roundToPx() }
                                                return IntOffset(x, y)
                                            }
                                        }
                                    },
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    val menuWidthDp = with(density) { buttonWidthPx.toDp() }
                                    Surface(
                                        modifier = Modifier
                                            .width(if (menuWidthDp > 100.dp) menuWidthDp else 180.dp)
                                            .heightIn(max = 260.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 6.dp,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(vertical = 4.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            val quickOptions = listOf(
                                                "10rs" to "₹10",
                                                "20rs" to "₹20",
                                                "30rs" to "₹30",
                                                "50rs" to "₹50",
                                                "250ml" to "250 ml",
                                                "500ml" to "500 ml",
                                                "750ml" to "750 ml",
                                                "1l" to "1 Litre"
                                            )
                                            quickOptions.forEach { (key, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label, fontWeight = FontWeight.Medium) },
                                                    onClick = {
                                                        showQuickMenu = false
                                                        viewModel.addQuickEntry(key)
                                                        Toast.makeText(context, context.getString(R.string.added_entry, label), Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Manual Entry Button
                        Button(
                            onClick = { showManualEntrySheet = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.manual_entry), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Today's Entry History Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Text(
                            text = "Current Month Bill:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "₹$currentMonthBill",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }


                    if (lastMonthDue < BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Extra Paid / Advance:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "- ₹${lastMonthDue.abs()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        val remainingDue = customer?.currentDue ?: BigDecimal.ZERO
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Remaining Due:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (remainingDue < BigDecimal.ZERO) "- ₹${remainingDue.abs()}" else "₹$remainingDue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingDue > BigDecimal.ZERO) MaterialTheme.colorScheme.error
                                        else if (remainingDue < BigDecimal.ZERO) Color(0xFF10B981)
                                        else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (todayTransactions.isEmpty() && todayPayments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No entries recorded for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Milk transactions
                            todayTransactions.forEach { trans ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                itemToDelete = TodayHistoryItem.Milk(trans)
                                            }
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (trans.session == "Morning") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = trans.session ?: "Milk",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (trans.session == "Morning") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val milkTypeTag = trans.milkType?.take(1)?.uppercase() ?: "R"
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (milkTypeTag == "R") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = milkTypeTag,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (milkTypeTag == "R") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${trans.quantity ?: 0} Litres",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (!trans.timestamp.isNullOrBlank()) {
                                                Text(
                                                    text = trans.timestamp,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            text = "+ ₹${trans.amount ?: 0}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            // Payments
                            todayPayments.forEach { pay ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                itemToDelete = TodayHistoryItem.Pay(pay)
                                            }
                                        ),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Payment (${pay.paymentMode ?: "Cash"})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = "Received",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "- ₹${pay.amount}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Manual Entry Bottom Sheet (Slided-up Animated Dialog)
    if (showManualEntrySheet) {
        ManualEntryBottomSheet(
            initialDate = currentDate,
            initialSession = session,
            milkRate = milkRate,
            onDismiss = { showManualEntrySheet = false },
            onSave = { date, entrySession, entryType, value ->
                viewModel.addManualEntry(date, entrySession, entryType, value)
                Toast.makeText(context, context.getString(R.string.manual_entry_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Record Payment Dialog
    if (showPaymentDialog) {
        val paymentPrefill = if (lastMonthDue > BigDecimal.ZERO) {
            lastMonthDue
        } else {
            customer?.currentDue?.takeIf { it > BigDecimal.ZERO }
        }
        RecordPaymentDialog(
            initialDate = currentDate,
            initialAmount = paymentPrefill,
            onDismiss = { showPaymentDialog = false },
            onSave = { amount, mode, date ->
                viewModel.addPayment(amount, mode, date)
                Toast.makeText(context, context.getString(R.string.payment_recorded, amount.toPlainString()), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_entry_title), fontWeight = FontWeight.Bold) },
            text = {
                val message = when (val item = itemToDelete) {
                    is TodayHistoryItem.Milk -> "Are you sure you want to delete this milk transaction of ${item.transaction.quantity}L (₹${item.transaction.amount})? Customer due balance will be reduced by ₹${item.transaction.amount}."
                    is TodayHistoryItem.Pay -> "Are you sure you want to delete this payment of ₹${item.payment.amount}? Customer due balance will be restored by ₹${item.payment.amount}."
                    null -> ""
                }
                Text(message)
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val item = itemToDelete) {
                            is TodayHistoryItem.Milk -> {
                                viewModel.deleteMilkTransaction(item.transaction)
                                Toast.makeText(context, context.getString(R.string.milk_entry_deleted), Toast.LENGTH_SHORT).show()
                            }
                            is TodayHistoryItem.Pay -> {
                                viewModel.deletePayment(item.payment)
                                Toast.makeText(context, context.getString(R.string.payment_entry_deleted), Toast.LENGTH_SHORT).show()
                            }
                            null -> {}
                        }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Default Quantity Dialog
    if (showDefaultQtyDialog && customer != null) {
        DefaultQuantityDialog(
            customer = customer!!,
            milkRate = milkRate,
            onDismiss = { showDefaultQtyDialog = false },
            onUpdate = { morningQty, eveningQty ->
                viewModel.updateDefaultQuantities(morningQty, eveningQty)
                showDefaultQtyDialog = false
                Toast.makeText(context, context.getString(R.string.default_quantities_updated), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Bill Generation Dialog
    if (showBillDialog && customer != null) {
        BillDialog(
            customer = customer!!,
            onDismiss = { showBillDialog = false },
            onGenerateReport = { startDate, endDate, reportTitle, isLastMonth, action ->
                coroutineScope.launch {
                    val reportData = viewModel.generateBillReport(startDate, endDate, reportTitle, isLastMonth)
                    if (reportData != null) {
                        when (action) {
                            BillAction.VIEW -> {
                                currentViewingMonth = null
                                statementPreviewData = reportData
                            }
                            BillAction.SHARE -> {
                                val file = PDFGenerator.generateCustomerMonthlyPDF(context, reportData)
                                PDFGenerator.shareFile(context, file)
                            }
                            BillAction.SAVE -> {
                                val file = PDFGenerator.generateCustomerMonthlyPDF(context, reportData)
                                Toast.makeText(context, context.getString(R.string.saved_to_file, file.name), Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.no_records_found), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Statement In-App Preview Dialog
    if (statementPreviewData != null) {
        StatementPreviewDialog(
            reportData = statementPreviewData!!,
            onDismiss = {
                statementPreviewData = null
                currentViewingMonth = null
            },
            onShare = {
                val file = PDFGenerator.generateCustomerMonthlyPDF(context, statementPreviewData!!)
                PDFGenerator.shareFile(context, file)
            },
            onSave = {
                val file = PDFGenerator.generateCustomerMonthlyPDF(context, statementPreviewData!!)
                Toast.makeText(context, context.getString(R.string.saved_to_file, file.name), Toast.LENGTH_LONG).show()
            },
            onOpenPdf = {
                val file = PDFGenerator.generateCustomerMonthlyPDF(context, statementPreviewData!!)
                PDFGenerator.openFile(context, file)
            },
            onNavigateMonth = if (currentViewingMonth != null) { isNext ->
                val targetMonth = currentViewingMonth!!.let {
                    if (isNext) it.plusMonths(1) else it.minusMonths(1)
                }
                openBillForMonth(targetMonth)
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryBottomSheet(
    initialDate: String,
    initialSession: String,
    milkRate: BigDecimal,
    onDismiss: () -> Unit,
    onSave: (date: String, session: String, entryType: String, value: BigDecimal) -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedSession by remember { mutableStateOf(initialSession) }
    var selectedEntryType by remember { mutableStateOf("Litres") } // "Litres", "ml", "Rupee"
    var inputValue by remember { mutableStateOf("") }

    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Manual Milk Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Date Change
            OutlinedCard(
                onClick = { datePickerDialog.show() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.date_label, selectedDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.change_date), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // Session Selection
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionButton(
                    text = stringResource(R.string.morning),
                    isSelected = selectedSession == "Morning",
                    onClick = { selectedSession = "Morning" },
                    modifier = Modifier.weight(1f)
                )
                SessionButton(
                    text = stringResource(R.string.evening),
                    isSelected = selectedSession == "Evening",
                    onClick = { selectedSession = "Evening" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Entry Type Selection (Only one can be selected)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.entry_type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedEntryType == "Litres",
                        onClick = { selectedEntryType = "Litres" },
                        label = { Text(stringResource(R.string.litres)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedEntryType == "ml",
                        onClick = { selectedEntryType = "ml" },
                        label = { Text(stringResource(R.string.ml)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedEntryType == "Rupee",
                        onClick = { selectedEntryType = "Rupee" },
                        label = { Text(stringResource(R.string.rupee_symbol)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quantity Input Box
            val labelText = when (selectedEntryType) {
                "Litres" -> "Quantity (in Litres)"
                "ml" -> "Quantity (in Millilitres)"
                else -> "Amount (in Rupees)"
            }
            val suffixText = when (selectedEntryType) {
                "Litres" -> "L"
                "ml" -> "ml"
                else -> "₹"
            }

            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text(labelText) },
                suffix = { Text(suffixText, fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Live Calculation Preview
            val numValue = inputValue.toDoubleOrNull()
            if (numValue != null && numValue > 0) {
                val previewText = when (selectedEntryType) {
                    "Litres" -> {
                        val amt = numValue * milkRate.toDouble()
                        "Qty: $numValue L • Rate: ₹$milkRate/L • Total: ₹%.2f".format(amt)
                    }
                    "ml" -> {
                        val ltr = numValue / 1000.0
                        val amt = ltr * milkRate.toDouble()
                        "Qty: %.3f L ($numValue ml) • Total: ₹%.2f".format(ltr, amt)
                    }
                    else -> {
                        val ltr = if (milkRate.toDouble() > 0) numValue / milkRate.toDouble() else 0.0
                        "Amount: ₹$numValue • Qty: %.2f L (at ₹$milkRate/L)".format(ltr)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewText,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Save Entry Button
            Button(
                onClick = {
                    val dec = inputValue.toBigDecimalOrNull()
                    if (dec != null && dec > BigDecimal.ZERO) {
                        onSave(selectedDate, selectedSession, selectedEntryType, dec)
                        onDismiss()
                    }
                },
                enabled = numValue != null && numValue > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save_entry), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RecordPaymentDialog(
    initialDate: String,
    initialAmount: BigDecimal? = null,
    onDismiss: () -> Unit,
    onSave: (amount: BigDecimal, mode: String, date: String) -> Unit
) {
    var amountText by remember {
        mutableStateOf(initialAmount?.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var paymentMode by remember { mutableStateOf("Cash") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_payment), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.payment_amount)) },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMode == "Cash",
                        onClick = { paymentMode = "Cash" },
                        label = { Text(stringResource(R.string.cash)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paymentMode == "UPI",
                        onClick = { paymentMode = "UPI" },
                        label = { Text(stringResource(R.string.upi)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toBigDecimalOrNull()
                    if (amt != null && amt > BigDecimal.ZERO) {
                        onSave(amt, paymentMode, initialDate)
                        onDismiss()
                    }
                },
                enabled = amountText.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
            ) {
                Text(stringResource(R.string.save_payment))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SessionButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

enum class BillAction {
    VIEW, SHARE, SAVE
}

@Composable
fun DefaultQuantityDialog(
    customer: Customer,
    milkRate: BigDecimal,
    onDismiss: () -> Unit,
    onUpdate: (morningQty: BigDecimal, eveningQty: BigDecimal) -> Unit
) {
    var selectedUnit by remember { mutableStateOf("Litres") } // "Litres", "ml", "Rupees"
    var morningText by remember {
        mutableStateOf(
            customer.defaultQtyMorning?.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString() ?: "0"
        )
    }
    var eveningText by remember {
        mutableStateOf(
            customer.defaultQtyEvening?.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString() ?: "0"
        )
    }

    fun switchUnit(newUnit: String) {
        if (newUnit.equals(selectedUnit, ignoreCase = true)) return
        val mVal = morningText.toBigDecimalOrNull()
        val eVal = eveningText.toBigDecimalOrNull()

        if (mVal != null && mVal > BigDecimal.ZERO) {
            val baseLiters = when (selectedUnit) {
                "Litres" -> mVal
                "ml" -> mVal.divide(BigDecimal("1000.0"), 4, RoundingMode.HALF_UP)
                "Rupees" -> if (milkRate > BigDecimal.ZERO) mVal.divide(milkRate, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
                else -> mVal
            }
            morningText = when (newUnit) {
                "Litres" -> baseLiters.stripTrailingZeros().toPlainString()
                "ml" -> baseLiters.multiply(BigDecimal("1000.0")).stripTrailingZeros().toPlainString()
                "Rupees" -> baseLiters.multiply(milkRate).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                else -> baseLiters.toPlainString()
            }
        }

        if (eVal != null && eVal > BigDecimal.ZERO) {
            val baseLiters = when (selectedUnit) {
                "Litres" -> eVal
                "ml" -> eVal.divide(BigDecimal("1000.0"), 4, RoundingMode.HALF_UP)
                "Rupees" -> if (milkRate > BigDecimal.ZERO) eVal.divide(milkRate, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
                else -> eVal
            }
            eveningText = when (newUnit) {
                "Litres" -> baseLiters.stripTrailingZeros().toPlainString()
                "ml" -> baseLiters.multiply(BigDecimal("1000.0")).stripTrailingZeros().toPlainString()
                "Rupees" -> baseLiters.multiply(milkRate).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                else -> baseLiters.toPlainString()
            }
        }

        selectedUnit = newUnit
    }

    val mLabel = when (selectedUnit) {
        "Litres" -> stringResource(R.string.morning_liters)
        "ml" -> stringResource(R.string.morning_ml)
        else -> stringResource(R.string.morning_rupees)
    }
    val eLabel = when (selectedUnit) {
        "Litres" -> stringResource(R.string.evening_liters)
        "ml" -> stringResource(R.string.evening_ml)
        else -> stringResource(R.string.evening_rupees)
    }
    val prefix = when (selectedUnit) {
        "Litres" -> "L "
        "ml" -> "ml "
        else -> "₹ "
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.default_milk_quantity), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Customer: ${customer.name ?: "N/A"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                UnitSegmentedButtons(
                    selectedUnit = selectedUnit,
                    onUnitSelected = { switchUnit(it) }
                )

                Text(
                    text = "Current Rate: ₹$milkRate / Litre",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                // Morning Field
                OutlinedTextField(
                    value = morningText,
                    onValueChange = { morningText = it },
                    label = { Text(mLabel) },
                    prefix = { Text(prefix) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Evening Field
                OutlinedTextField(
                    value = eveningText,
                    onValueChange = { eveningText = it },
                    label = { Text(eLabel) },
                    prefix = { Text(prefix) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val morningVal = morningText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val eveningVal = eveningText.toBigDecimalOrNull() ?: BigDecimal.ZERO

                    val mFinal = when (selectedUnit) {
                        "Rupees" -> if (milkRate > BigDecimal.ZERO) morningVal.divide(milkRate, 3, RoundingMode.HALF_UP) else BigDecimal.ZERO
                        "ml" -> morningVal.divide(BigDecimal("1000.0"), 3, RoundingMode.HALF_UP)
                        else -> morningVal
                    }
                    val eFinal = when (selectedUnit) {
                        "Rupees" -> if (milkRate > BigDecimal.ZERO) eveningVal.divide(milkRate, 3, RoundingMode.HALF_UP) else BigDecimal.ZERO
                        "ml" -> eveningVal.divide(BigDecimal("1000.0"), 3, RoundingMode.HALF_UP)
                        else -> eveningVal
                    }
                    onUpdate(mFinal, eFinal)
                }
            ) {
                Text(stringResource(R.string.update), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onGenerateReport: (startDate: String, endDate: String, reportTitle: String, isLastMonth: Boolean, action: BillAction) -> Unit
) {
    val context = LocalContext.current
    var isLastMonthSelected by remember { mutableStateOf(true) }

    // Custom options state
    var selectedReportType by remember { mutableStateOf("By Month") }
    var reportTypeExpanded by remember { mutableStateOf(false) }

    val currentYear = remember { LocalDate.now().year }
    val years = remember { (currentYear - 4..currentYear + 1).toList().reversed() }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var yearExpanded by remember { mutableStateOf(false) }

    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val currentMonthIdx = remember { LocalDate.now().monthValue - 1 }
    var selectedMonthIdx by remember { mutableIntStateOf(currentMonthIdx) }
    var monthExpanded by remember { mutableStateOf(false) }

    val quarters = listOf("Q1 (Jan - Mar)", "Q2 (Apr - Jun)", "Q3 (Jul - Sep)", "Q4 (Oct - Dec)")
    var selectedQuarterIdx by remember { mutableIntStateOf(0) }
    var quarterExpanded by remember { mutableStateOf(false) }

    var customStartDate by remember {
        mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())
    }
    var customEndDate by remember {
        mutableStateOf(LocalDate.now().toString())
    }

    val lastMonthDate = remember { LocalDate.now().minusMonths(1) }
    val lastMonthTitle = remember { lastMonthDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")) }

    fun pickCustomDate(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                if (isStart) customStartDate = dateStr else customEndDate = dateStr
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun submitReport(action: BillAction) {
        if (isLastMonthSelected) {
            val start = lastMonthDate.withDayOfMonth(1).toString()
            val end = lastMonthDate.withDayOfMonth(lastMonthDate.lengthOfMonth()).toString()
            val title = "Last Month ($lastMonthTitle)"
            onGenerateReport(start, end, title, true, action)
        } else {
            when (selectedReportType) {
                "By Month" -> {
                    val mNum = selectedMonthIdx + 1
                    val targetMonth = LocalDate.of(selectedYear, mNum, 1)
                    val start = targetMonth.withDayOfMonth(1).toString()
                    val end = targetMonth.withDayOfMonth(targetMonth.lengthOfMonth()).toString()
                    val title = "${months[selectedMonthIdx]} $selectedYear"
                    onGenerateReport(start, end, title, false, action)
                }
                "By Quarter" -> {
                    val (startMonth, endMonth, endDay) = when (selectedQuarterIdx) {
                        0 -> Triple(1, 3, 31)
                        1 -> Triple(4, 6, 30)
                        2 -> Triple(7, 9, 30)
                        else -> Triple(10, 12, 31)
                    }
                    val start = String.format(Locale.US, "%04d-%02d-01", selectedYear, startMonth)
                    val end = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, endMonth, endDay)
                    val title = "${quarters[selectedQuarterIdx].split(" ").first()} $selectedYear"
                    onGenerateReport(start, end, title, false, action)
                }
                "By Year" -> {
                    val start = "$selectedYear-01-01"
                    val end = "$selectedYear-12-31"
                    val title = "Year $selectedYear"
                    onGenerateReport(start, end, title, false, action)
                }
                "Lifetime" -> {
                    onGenerateReport("", "", "Lifetime Statement", false, action)
                }
                "Custom Date Range" -> {
                    val title = "$customStartDate to $customEndDate"
                    onGenerateReport(customStartDate, customEndDate, title, false, action)
                }
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.generate_bill_statement), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = customer.name ?: stringResource(R.string.customer),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Due: ₹${customer.currentDue ?: BigDecimal.ZERO}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if ((customer.currentDue ?: BigDecimal.ZERO) > BigDecimal.ZERO) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                            )
                        }
                        if (!customer.mobile.isNullOrBlank()) {
                            Text(
                                text = "Mobile: ${customer.mobile}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Radio buttons: Last Month vs Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isLastMonthSelected = true }
                    ) {
                        RadioButton(
                            selected = isLastMonthSelected,
                            onClick = { isLastMonthSelected = true }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.last_month), fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isLastMonthSelected = false }
                    ) {
                        RadioButton(
                            selected = !isLastMonthSelected,
                            onClick = { isLastMonthSelected = false }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.custom), fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isLastMonthSelected) {
                    // Last Month Info Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Month: $lastMonthTitle",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Includes all milk deliveries in $lastMonthTitle and accounts for settlements paid in the following month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Custom Configuration Box
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Report Type Dropdown
                        Text(stringResource(R.string.report_type), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { reportTypeExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedReportType)
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = reportTypeExpanded,
                                onDismissRequest = { reportTypeExpanded = false }
                            ) {
                                val reportTypes = listOf("By Month", "By Quarter", "By Year", "Lifetime", "Custom Date Range")
                                reportTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            selectedReportType = type
                                            reportTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        when (selectedReportType) {
                            "By Month" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Year Selector
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { yearExpanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("$selectedYear")
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = yearExpanded,
                                            onDismissRequest = { yearExpanded = false }
                                        ) {
                                            years.forEach { y ->
                                                DropdownMenuItem(
                                                    text = { Text("$y") },
                                                    onClick = {
                                                        selectedYear = y
                                                        yearExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Month Selector
                                    Box(modifier = Modifier.weight(1.4f)) {
                                        OutlinedButton(
                                            onClick = { monthExpanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(months[selectedMonthIdx])
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = monthExpanded,
                                            onDismissRequest = { monthExpanded = false }
                                        ) {
                                            months.forEachIndexed { index, mName ->
                                                DropdownMenuItem(
                                                    text = { Text(mName) },
                                                    onClick = {
                                                        selectedMonthIdx = index
                                                        monthExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            "By Quarter" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Year Selector
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { yearExpanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("$selectedYear")
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = yearExpanded,
                                            onDismissRequest = { yearExpanded = false }
                                        ) {
                                            years.forEach { y ->
                                                DropdownMenuItem(
                                                    text = { Text("$y") },
                                                    onClick = {
                                                        selectedYear = y
                                                        yearExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Quarter Selector
                                    Box(modifier = Modifier.weight(1.5f)) {
                                        OutlinedButton(
                                            onClick = { quarterExpanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(quarters[selectedQuarterIdx])
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = quarterExpanded,
                                            onDismissRequest = { quarterExpanded = false }
                                        ) {
                                            quarters.forEachIndexed { index, qName ->
                                                DropdownMenuItem(
                                                    text = { Text(qName) },
                                                    onClick = {
                                                        selectedQuarterIdx = index
                                                        quarterExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            "By Year" -> {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { yearExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Year: $selectedYear")
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = yearExpanded,
                                        onDismissRequest = { yearExpanded = false }
                                    ) {
                                        years.forEach { y ->
                                            DropdownMenuItem(
                                                text = { Text("$y") },
                                                onClick = {
                                                    selectedYear = y
                                                    yearExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            "Lifetime" -> {
                                Text(
                                    text = "Generates complete statement of all records since customer registration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            "Custom Date Range" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { pickCustomDate(true) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.from_date), style = MaterialTheme.typography.labelSmall)
                                            Text(customStartDate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { pickCustomDate(false) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.to_date), style = MaterialTheme.typography.labelSmall)
                                            Text(customEndDate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Three Action Buttons (View, Share, Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { submitReport(BillAction.VIEW) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.view), fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { submitReport(BillAction.SHARE) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.share), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { submitReport(BillAction.SAVE) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun StatementPreviewDialog(
    reportData: BillReportData,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpenPdf: () -> Unit,
    onNavigateMonth: ((Boolean) -> Unit)? = null
) {
    var selectedMonthIndex by remember(reportData) { mutableIntStateOf(0) }
    val currentMonthData = reportData.months.getOrNull(selectedMonthIndex) ?: reportData.months.firstOrNull()

    val density = LocalDensity.current
    val swipeThreshold = remember(density) { with(density) { 50.dp.toPx() } }
    var dialogDragX by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.96f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dialogDragX = 0f },
                        onDragEnd = {
                            if (dialogDragX < -swipeThreshold) {
                                // Left swipe -> Next Month
                                if (onNavigateMonth != null) {
                                    onNavigateMonth(true)
                                } else if (selectedMonthIndex < reportData.months.size - 1) {
                                    selectedMonthIndex++
                                }
                            } else if (dialogDragX > swipeThreshold) {
                                // Right swipe -> Prev Month
                                if (onNavigateMonth != null) {
                                    onNavigateMonth(false)
                                } else if (selectedMonthIndex > 0) {
                                    selectedMonthIndex--
                                }
                            }
                            dialogDragX = 0f
                        },
                        onDragCancel = { dialogDragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dialogDragX += dragAmount
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 480.dp
                val horizontalPad = if (isCompact) 8.dp else 12.dp
                val verticalPad = if (isCompact) 8.dp else 12.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPad, vertical = verticalPad)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reportData.customer.name ?: "Customer",
                                style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (reportData.months.size > 1) reportData.title else (currentMonthData?.monthTitle ?: reportData.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Month Switcher (Left/Right Month Navigation)
                    val canGoPrev = onNavigateMonth != null || selectedMonthIndex > 0
                    val canGoNext = onNavigateMonth != null || selectedMonthIndex < reportData.months.size - 1

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (onNavigateMonth != null) {
                                    onNavigateMonth(false)
                                } else if (selectedMonthIndex > 0) {
                                    selectedMonthIndex--
                                }
                            },
                            enabled = canGoPrev
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentMonthData?.monthTitle ?: reportData.title,
                                fontSize = if (isCompact) 18.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (reportData.months.size > 1) {
                                Text(
                                    text = "${selectedMonthIndex + 1} of ${reportData.months.size} (${reportData.title})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (onNavigateMonth != null) {
                                    onNavigateMonth(true)
                                } else if (selectedMonthIndex < reportData.months.size - 1) {
                                    selectedMonthIndex++
                                }
                            },
                            enabled = canGoNext
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                        }
                    }

                    // Month Summary Box (Adaptive 2-Column Grid)
                    if (currentMonthData != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Row 1: Opening Due (Left) & Milk Bill (Right)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.opening_due_format, "").replace(":", "").replace("₹", "").trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "₹${currentMonthData.openingBalance.toPlainString()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentMonthData.openingBalance > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.milk_bill_format, "", "").substringBefore(":").trim().takeIf { it.isNotBlank() } ?: "Milk Bill",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${currentMonthData.totalMilk.toPlainString()}L (₹${currentMonthData.totalBill.toPlainString()})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                                // Row 2: Paid in Month (Left) & Final Due + Status Badge (Right)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.month_paid_format, "").replace(":", "").replace("₹", "").trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "₹${currentMonthData.paidInMonth.toPlainString()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.final_due).replace(":", "").trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = "₹${currentMonthData.finalDue}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentMonthData.finalDue > BigDecimal.ZERO) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (currentMonthData.isPaid) Color(0xFF10B981).copy(alpha = 0.18f) else MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = if (currentMonthData.isPaid) "PAID" else "PENDING",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (currentMonthData.isPaid) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                if (currentMonthData.postMonthPayments.isNotEmpty()) {
                                    val postPaidTotal = currentMonthData.postMonthPayments.sumOf { it.amount }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Settlement: -₹$postPaidTotal paid in subsequent month",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color(0xFF047857),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 16 * 16 Table Header (Date, Morning, Evening, Payment on Left & Right)
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DayHalfHeader(isCompact = isCompact, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                            )
                            DayHalfHeader(isCompact = isCompact, modifier = Modifier.weight(1f))
                        }
                    }

                    // 16 * 16 Table Rows
                    val tableScrollState = rememberScrollState()
                    val daysMap = remember(currentMonthData) {
                        currentMonthData?.days?.associateBy { it.day } ?: emptyMap()
                    }
                    val daysInMonth = remember(currentMonthData) {
                        currentMonthData?.days?.size ?: 31
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(tableScrollState)
                    ) {
                        for (i in 1..16) {
                            val leftDay = i
                            val rightDay = i + 16

                            val leftItem = daysMap[leftDay]
                            val rightItem = if (rightDay <= daysInMonth) daysMap[rightDay] else null

                            val rowBg = if (i % 2 == 0) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            }

                            Surface(
                                color = rowBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DayHalfRow(
                                        dayNum = leftDay,
                                        dayItem = leftItem,
                                        isCompact = isCompact,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    )
                                    DayHalfRow(
                                        dayNum = if (rightDay <= daysInMonth) rightDay else null,
                                        dayItem = rightItem,
                                        isCompact = isCompact,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Bottom Action Buttons: PDF View, Share, Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenPdf,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_view),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = onShare,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.share),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.save),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHalfHeader(isCompact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isCompact) "Dt" else stringResource(R.string.date),
            modifier = Modifier.weight(0.75f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isCompact) 9.sp else 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = if (isCompact) "Morn" else stringResource(R.string.morning),
            modifier = Modifier.weight(1.15f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isCompact) 9.sp else 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = if (isCompact) "Eve" else stringResource(R.string.evening),
            modifier = Modifier.weight(1.15f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isCompact) 9.sp else 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = if (isCompact) "Pay" else stringResource(R.string.payment),
            modifier = Modifier.weight(1.15f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isCompact) 9.sp else 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DayHalfRow(
    dayNum: Int?,
    dayItem: DayStatementItem?,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp, horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayNum?.let { String.format(Locale.US, "%02d", it) } ?: "-",
            modifier = Modifier.weight(0.75f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = if (isCompact) 9.5.sp else 10.sp),
            fontWeight = FontWeight.Bold,
            color = if (dayNum != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = if (dayItem != null && (dayItem.mornQty > BigDecimal.ZERO || dayItem.mornAmt > BigDecimal.ZERO)) {
                val q = dayItem.mornQty.stripTrailingZeros().toPlainString()
                val a = dayItem.mornAmt.setScale(0, RoundingMode.HALF_UP).toPlainString()
                "${q}L\n₹$a"
            } else "-",
            modifier = Modifier.weight(1.15f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                lineHeight = if (isCompact) 10.5.sp else 12.sp
            ),
            color = if (dayItem != null && (dayItem.mornQty > BigDecimal.ZERO || dayItem.mornAmt > BigDecimal.ZERO)) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = false
        )
        Text(
            text = if (dayItem != null && (dayItem.eveQty > BigDecimal.ZERO || dayItem.eveAmt > BigDecimal.ZERO)) {
                val q = dayItem.eveQty.stripTrailingZeros().toPlainString()
                val a = dayItem.eveAmt.setScale(0, RoundingMode.HALF_UP).toPlainString()
                "${q}L\n₹$a"
            } else "-",
            modifier = Modifier.weight(1.15f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                lineHeight = if (isCompact) 10.5.sp else 12.sp
            ),
            color = if (dayItem != null && (dayItem.eveQty > BigDecimal.ZERO || dayItem.eveAmt > BigDecimal.ZERO)) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = false
        )
        Text(
            text = if (dayItem != null && dayItem.paymentAmt > BigDecimal.ZERO) {
                val p = dayItem.paymentAmt.setScale(0, RoundingMode.HALF_UP).toPlainString()
                "₹$p"
            } else "-",
            modifier = Modifier.weight(1.15f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = if (isCompact) 8.5.sp else 9.5.sp),
            fontWeight = if (dayItem != null && dayItem.paymentAmt > BigDecimal.ZERO) FontWeight.Bold else FontWeight.Normal,
            color = if (dayItem != null && dayItem.paymentAmt > BigDecimal.ZERO) {
                Color(0xFF10B981)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}
