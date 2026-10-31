package `in`.ignishers.milkmanager2.ui.sales

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.utils.PDFGenerator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

// Premium Harmonious Color Palette
private val PaletteIndigoBg = Color(0xFFEEF2FF)
private val PaletteIndigoBorder = Color(0xFFC7D2FE)
private val PaletteIndigoPrimary = Color(0xFF4F46E5)
private val PaletteIndigoText = Color(0xFF3730A3)

private val PaletteEmeraldBg = Color(0xFFECFDF5)
private val PaletteEmeraldBorder = Color(0xFFA7F3D0)
private val PaletteEmeraldPrimary = Color(0xFF059669)
private val PaletteEmeraldText = Color(0xFF065F46)

private val PaletteAmberBg = Color(0xFFFFFBEB)
private val PaletteAmberBorder = Color(0xFFFDE68A)
private val PaletteAmberPrimary = Color(0xFFD97706)
private val PaletteAmberText = Color(0xFF92400E)

private val PaletteRoseBg = Color(0xFFFEF2F2)
private val PaletteRoseBorder = Color(0xFFFECACA)
private val PaletteRosePrimary = Color(0xFFDC2626)
private val PaletteRoseText = Color(0xFF991B1B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesAnalyticsScreen(
    onBack: () -> Unit,
    viewModel: SalesAnalyticsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 360

    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var showPdfDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.sales_records),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (state.selectedFilter) {
                                SalesPeriodFilter.YEAR -> "Annual Performance • Year ${state.selectedYear}"
                                else -> "Business Cashflow & Territory Insights"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Compact Circular FAB with PDF Icon Only
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val file = viewModel.exportPdfReport(context)
                        if (file != null) {
                            generatedPdfFile = file
                            showPdfDialog = true
                        } else {
                            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                if (state.isExportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Export PDF Report",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        bottomBar = {
            // Bottom Sub-View Window Navigation Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = state.activeSubView == AnalyticsSubView.MONTHLY_TABLE,
                        onClick = { viewModel.setSubView(AnalyticsSubView.MONTHLY_TABLE) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("Monthly", maxLines = 1, softWrap = false, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = state.activeSubView == AnalyticsSubView.CHARTS,
                        onClick = { viewModel.setSubView(AnalyticsSubView.CHARTS) },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        label = { Text("Charts", maxLines = 1, softWrap = false, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = state.activeSubView == AnalyticsSubView.ROUTES,
                        onClick = { viewModel.setSubView(AnalyticsSubView.ROUTES) },
                        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        label = { Text("Routes", maxLines = 1, softWrap = false, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = state.activeSubView == AnalyticsSubView.RECOVERY,
                        onClick = { viewModel.setSubView(AnalyticsSubView.RECOVERY) },
                        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                        label = { Text("Recovery", maxLines = 1, softWrap = false, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Analyzing sales & collections...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 8.dp)
            ) {
                // 1. Period Selector Tabs (Lifetime | Year | This Month | Last Month)
                PeriodSelectorTabs(
                    selectedFilter = state.selectedFilter,
                    onSelect = { viewModel.setFilter(it) }
                )

                // 2. Year Switcher (Visible when 'Year' is selected)
                AnimatedVisibility(visible = state.selectedFilter == SalesPeriodFilter.YEAR) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        YearSwitcherRow(
                            selectedYear = state.selectedYear,
                            availableYears = state.availableYears,
                            onYearChange = { viewModel.setYear(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. 4 Master KPI Summary Cards (2x2 Grid)
                KpiGridSection(
                    overview = state.overview,
                    isCompact = isCompact
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 4. Active Window Sub-View Representation
                when (state.activeSubView) {
                    AnalyticsSubView.MONTHLY_TABLE -> {
                        MonthlyBreakdownTableCard(
                            monthlySales = state.monthlySales,
                            selectedFilter = state.selectedFilter,
                            selectedYear = state.selectedYear
                        )
                    }
                    AnalyticsSubView.CHARTS -> {
                        Column {
                            SessionShareCard(
                                morningMilk = state.overview.morningMilk,
                                eveningMilk = state.overview.eveningMilk
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (state.monthlySales.isNotEmpty()) {
                                SalesChartCard(monthlySales = state.monthlySales)
                            }
                        }
                    }
                    AnalyticsSubView.ROUTES -> {
                        RouteDistributionSection(routes = state.routeSales)
                    }
                    AnalyticsSubView.RECOVERY -> {
                        TopDebtorsSection(
                            topDebtors = state.topDueCustomers,
                            onCall = { mobile ->
                                if (!mobile.isNullOrBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobile"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }

                // Bottom Padding for FAB
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // PDF Ready Dialog
    if (showPdfDialog && generatedPdfFile != null) {
        val scopeName = when (state.selectedFilter) {
            SalesPeriodFilter.YEAR -> "Year ${state.selectedYear}"
            else -> state.selectedFilter.label
        }
        AlertDialog(
            onDismissRequest = { showPdfDialog = false },
            icon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sales Audit PDF Ready", fontWeight = FontWeight.Bold) },
            text = {
                Text("Your official sales & audit report for $scopeName has been generated successfully.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        PDFGenerator.openFile(context, generatedPdfFile!!)
                        showPdfDialog = false
                    }
                ) {
                    Text("View PDF")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        PDFGenerator.shareFile(context, generatedPdfFile!!)
                        showPdfDialog = false
                    }
                ) {
                    Text("Share")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// UI SUBCOMPONENTS
// -------------------------------------------------------------

@Composable
private fun PeriodSelectorTabs(
    selectedFilter: SalesPeriodFilter,
    onSelect: (SalesPeriodFilter) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SalesPeriodFilter.values().forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(filter) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Text(
                        text = filter.label,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun YearSwitcherRow(
    selectedYear: Int,
    availableYears: List<Int>,
    onYearChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = PaletteIndigoBg,
        border = BorderStroke(1.dp, PaletteIndigoBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onYearChange(selectedYear - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Year",
                    tint = PaletteIndigoPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = PaletteIndigoPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Year $selectedYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PaletteIndigoPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            IconButton(
                onClick = { onYearChange(selectedYear + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Year",
                    tint = PaletteIndigoPrimary
                )
            }
        }
    }
}

@Composable
private fun KpiGridSection(
    overview: SalesOverview,
    isCompact: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1: Total Revenue & Total Collected
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMasterCard(
                modifier = Modifier.weight(1f),
                title = "Total Revenue",
                subtitle = "Gross Sales",
                value = "₹${formatCurrency(overview.totalRevenue)}",
                icon = Icons.Filled.Star,
                bgColor = PaletteIndigoBg,
                borderColor = PaletteIndigoBorder,
                accentColor = PaletteIndigoPrimary,
                textColor = PaletteIndigoText,
                isCompact = isCompact
            )
            KpiMasterCard(
                modifier = Modifier.weight(1f),
                title = "Total Collected",
                subtitle = "Cash & UPI Paid",
                value = "₹${formatCurrency(overview.totalCollected)}",
                icon = Icons.Filled.CheckCircle,
                bgColor = PaletteEmeraldBg,
                borderColor = PaletteEmeraldBorder,
                accentColor = PaletteEmeraldPrimary,
                textColor = PaletteEmeraldText,
                isCompact = isCompact
            )
        }

        // Row 2: Total Milk Sold & Market Outstanding Due
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMasterCard(
                modifier = Modifier.weight(1f),
                title = "Total Milk Sold",
                subtitle = "Litres Supplied",
                value = "${formatQuantity(overview.totalMilk)} L",
                icon = Icons.Filled.Favorite,
                bgColor = PaletteAmberBg,
                borderColor = PaletteAmberBorder,
                accentColor = PaletteAmberPrimary,
                textColor = PaletteAmberText,
                isCompact = isCompact
            )
            KpiMasterCard(
                modifier = Modifier.weight(1f),
                title = "Market Dues",
                subtitle = "Pending Recovery",
                value = "₹${formatCurrency(overview.totalPendingDue)}",
                icon = Icons.Filled.Warning,
                bgColor = PaletteRoseBg,
                borderColor = PaletteRoseBorder,
                accentColor = PaletteRosePrimary,
                textColor = PaletteRoseText,
                isCompact = isCompact
            )
        }
    }
}

@Composable
private fun KpiMasterCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    value: String,
    icon: ImageVector,
    bgColor: Color,
    borderColor: Color,
    accentColor: Color,
    textColor: Color,
    isCompact: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(if (isCompact) 12.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(if (isCompact) 28.dp else 34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(if (isCompact) 15.dp else 18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = value,
                fontSize = if (isCompact) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionShareCard(
    morningMilk: BigDecimal,
    eveningMilk: BigDecimal
) {
    val total = morningMilk.add(eveningMilk)
    val morningRatio = if (total > BigDecimal.ZERO) {
        morningMilk.divide(total, 4, RoundingMode.HALF_UP).toFloat()
    } else 0.5f
    val morningPct = (morningRatio * 100).toInt()
    val eveningPct = 100 - morningPct

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session Split (Morning vs Evening)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$morningPct% : $eveningPct%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-tone split bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(morningRatio.coerceIn(0.08f, 0.92f))
                        .fillMaxHeight()
                        .background(PaletteAmberPrimary)
                )
                Box(
                    modifier = Modifier
                        .weight((1f - morningRatio).coerceIn(0.08f, 0.92f))
                        .fillMaxHeight()
                        .background(PaletteIndigoPrimary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PaletteAmberPrimary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Morning ($morningPct%): ${formatQuantity(morningMilk)} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PaletteIndigoPrimary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Evening ($eveningPct%): ${formatQuantity(eveningMilk)} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SalesChartCard(monthlySales: List<MonthlySalesItem>) {
    val chartData = remember(monthlySales) {
        // Show up to the last 6 non-zero or recent months in chronological order
        monthlySales.filter { it.revenue > BigDecimal.ZERO || it.collected > BigDecimal.ZERO }.takeLast(6).ifEmpty { monthlySales.take(6).reversed() }
    }

    val maxVal = remember(chartData) {
        val maxRev = chartData.maxOfOrNull { it.revenue } ?: BigDecimal.ONE
        val maxCol = chartData.maxOfOrNull { it.collected } ?: BigDecimal.ONE
        maxOf(maxRev, maxCol).toFloat().coerceAtLeast(100f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sales vs Collection Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Monthly comparison (Bikri vs Vasooli)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(PaletteIndigoPrimary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sales", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(PaletteEmeraldPrimary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Received", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val width = size.width
                val height = size.height
                val bottomAxisY = height - 24.dp.toPx()
                val chartHeight = bottomAxisY - 10.dp.toPx()

                // Baseline
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.6f),
                    start = Offset(0f, bottomAxisY),
                    end = Offset(width, bottomAxisY),
                    strokeWidth = 1.dp.toPx()
                )

                if (chartData.isNotEmpty()) {
                    val barGroupWidth = width / chartData.size
                    val barWidth = (barGroupWidth * 0.28f).coerceAtMost(18.dp.toPx())

                    chartData.forEachIndexed { index, item ->
                        val groupCenterX = (index * barGroupWidth) + (barGroupWidth / 2f)

                        val revHeight = (item.revenue.toFloat() / maxVal) * chartHeight
                        val colHeight = (item.collected.toFloat() / maxVal) * chartHeight

                        // Revenue Bar (Indigo)
                        val revLeft = groupCenterX - barWidth - 2.dp.toPx()
                        drawRoundRect(
                            color = PaletteIndigoPrimary,
                            topLeft = Offset(revLeft, bottomAxisY - revHeight),
                            size = Size(barWidth, revHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Collected Bar (Emerald)
                        val colLeft = groupCenterX + 2.dp.toPx()
                        drawRoundRect(
                            color = PaletteEmeraldPrimary,
                            topLeft = Offset(colLeft, bottomAxisY - colHeight),
                            size = Size(barWidth, colHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Month label text
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 10.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            val shortMonth = item.monthDisplayName.split(" ").firstOrNull() ?: item.monthDisplayName
                            drawText(
                                shortMonth,
                                groupCenterX,
                                height - 4.dp.toPx(),
                                paint
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteDistributionSection(routes: List<RouteSalesItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Route & Colony Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "Territory sales share and pending collections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            routes.forEachIndexed { index, route ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = route.routeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${route.customerCount} cust",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "₹${formatCurrency(route.revenue)} (${route.percentageOfTotal.toInt()}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PaletteIndigoPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { (route.percentageOfTotal / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PaletteIndigoPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Milk: ${formatQuantity(route.milkSold)} L",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Due: ₹${formatCurrency(route.pendingDue)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (route.pendingDue > BigDecimal.ZERO) PaletteRosePrimary else PaletteEmeraldPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (index < routes.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyBreakdownTableCard(
    monthlySales: List<MonthlySalesItem>,
    selectedFilter: SalesPeriodFilter,
    selectedYear: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val titleText = when (selectedFilter) {
                SalesPeriodFilter.YEAR -> "Annual Performance ($selectedYear) • 12 Months"
                else -> "Monthly Performance Breakdown"
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "Detailed audit table of milk, sales & collections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Table Container with horizontal scroll for smaller screens
            val horizontalScrollState = rememberScrollState()

            Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                // Table Header
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier
                            .width(460.dp)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Month", modifier = Modifier.width(90.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        Text("Milk (L)", modifier = Modifier.width(80.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                        Text("Sales (₹)", modifier = Modifier.width(90.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                        Text("Recv (₹)", modifier = Modifier.width(90.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                        Text("Gap (₹)", modifier = Modifier.width(85.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                var totalMilk = BigDecimal.ZERO
                var totalSales = BigDecimal.ZERO
                var totalRecv = BigDecimal.ZERO

                // Table Rows (All 12 months in Year view, or all records in Lifetime)
                monthlySales.forEachIndexed { index, item ->
                    totalMilk = totalMilk.add(item.milkSold)
                    totalSales = totalSales.add(item.revenue)
                    totalRecv = totalRecv.add(item.collected)

                    val isAlt = index % 2 == 1
                    Surface(
                        modifier = Modifier.width(460.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAlt) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.monthDisplayName,
                                modifier = Modifier.width(90.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = formatQuantity(item.milkSold),
                                modifier = Modifier.width(80.dp),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "₹${formatCurrency(item.revenue)}",
                                modifier = Modifier.width(90.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PaletteIndigoPrimary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "₹${formatCurrency(item.collected)}",
                                modifier = Modifier.width(90.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PaletteEmeraldPrimary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false
                            )

                            // Net Gap Badge
                            val isClean = item.gap <= BigDecimal.ZERO
                            val gapText = if (isClean) "₹0" else "₹${formatCurrency(item.gap)}"
                            Text(
                                text = gapText,
                                modifier = Modifier.width(85.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isClean) PaletteEmeraldPrimary else PaletteRosePrimary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Table Summary Row
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.width(460.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL",
                            modifier = Modifier.width(90.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = formatQuantity(totalMilk),
                            modifier = Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "₹${formatCurrency(totalSales)}",
                            modifier = Modifier.width(90.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PaletteIndigoPrimary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "₹${formatCurrency(totalRecv)}",
                            modifier = Modifier.width(90.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PaletteEmeraldPrimary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )
                        val totalGap = totalSales.subtract(totalRecv)
                        val isClean = totalGap <= BigDecimal.ZERO
                        Text(
                            text = if (isClean) "₹0" else "₹${formatCurrency(totalGap)}",
                            modifier = Modifier.width(85.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isClean) PaletteEmeraldPrimary else PaletteRosePrimary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopDebtorsSection(
    topDebtors: List<TopCustomerDueItem>,
    onCall: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PaletteRoseBg),
        border = BorderStroke(1.2.dp, PaletteRoseBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = PaletteRosePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Priority Collection (Highest Dues)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PaletteRoseText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            topDebtors.forEachIndexed { index, debtor ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, PaletteRoseBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PaletteRosePrimary.copy(alpha = 0.12f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PaletteRosePrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = debtor.customerName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${debtor.routeName ?: "Direct"} • ${debtor.mobile ?: "No phone"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "₹${formatCurrency(debtor.currentDue)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PaletteRosePrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (!debtor.mobile.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onCall(debtor.mobile) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Call,
                                        contentDescription = "Call",
                                        tint = PaletteEmeraldPrimary,
                                        modifier = Modifier.size(18.dp)
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

// -------------------------------------------------------------
// HELPER FORMATTING FUNCTIONS
// -------------------------------------------------------------

private fun formatCurrency(amount: BigDecimal?): String {
    val amt = amount ?: BigDecimal.ZERO
    return if (amt.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
        amt.setScale(0, RoundingMode.HALF_UP).toPlainString()
    } else {
        amt.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}

private fun formatQuantity(quantity: BigDecimal?): String {
    val q = quantity ?: BigDecimal.ZERO
    return if (q.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
        q.setScale(0, RoundingMode.HALF_UP).toPlainString()
    } else {
        q.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
