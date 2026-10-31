package `in`.ignishers.milkmanager2.ui.sales

import java.math.BigDecimal
import java.time.LocalDate

enum class SalesPeriodFilter(val label: String) {
    LIFETIME("Lifetime"),
    YEAR("Year"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month")
}

enum class AnalyticsSubView(val label: String) {
    MONTHLY_TABLE("Monthly Table"),
    CHARTS("Charts & Trends"),
    ROUTES("Territory Routes"),
    RECOVERY("Priority Recovery")
}

data class SalesOverview(
    val totalMilk: BigDecimal = BigDecimal.ZERO,
    val totalRevenue: BigDecimal = BigDecimal.ZERO,
    val totalCollected: BigDecimal = BigDecimal.ZERO,
    val totalPendingDue: BigDecimal = BigDecimal.ZERO,
    val morningMilk: BigDecimal = BigDecimal.ZERO,
    val eveningMilk: BigDecimal = BigDecimal.ZERO
)

data class MonthlySalesItem(
    val monthKey: String,
    val monthDisplayName: String,
    val milkSold: BigDecimal,
    val revenue: BigDecimal,
    val collected: BigDecimal,
    val gap: BigDecimal
)

data class RouteSalesItem(
    val routeId: Long?,
    val routeName: String,
    val customerCount: Int,
    val milkSold: BigDecimal,
    val revenue: BigDecimal,
    val pendingDue: BigDecimal,
    val percentageOfTotal: Float
)

data class TopCustomerDueItem(
    val customerId: Long,
    val customerName: String,
    val mobile: String?,
    val currentDue: BigDecimal,
    val routeName: String?
)

data class SalesAnalyticsState(
    val isLoading: Boolean = true,
    val selectedFilter: SalesPeriodFilter = SalesPeriodFilter.YEAR,
    val selectedYear: Int = LocalDate.now().year,
    val availableYears: List<Int> = listOf(LocalDate.now().year),
    val activeSubView: AnalyticsSubView = AnalyticsSubView.MONTHLY_TABLE,
    val overview: SalesOverview = SalesOverview(),
    val monthlySales: List<MonthlySalesItem> = emptyList(),
    val routeSales: List<RouteSalesItem> = emptyList(),
    val topDueCustomers: List<TopCustomerDueItem> = emptyList(),
    val isExportingPdf: Boolean = false
)
