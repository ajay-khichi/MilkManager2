package `in`.ignishers.milkmanager2.ui.sales

import android.content.Context
import `in`.ignishers.milkmanager2.database.CustomerRoomDao
import `in`.ignishers.milkmanager2.database.DailyTransactionRoomDao
import `in`.ignishers.milkmanager2.database.PaymentRoomDao
import `in`.ignishers.milkmanager2.database.RouteGroupRoomDao
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.Payment
import `in`.ignishers.milkmanager2.ui.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class SalesAnalyticsViewModel(
    private val transactionDao: DailyTransactionRoomDao,
    private val paymentDao: PaymentRoomDao,
    private val customerDao: CustomerRoomDao,
    private val routeGroupDao: RouteGroupRoomDao
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(SalesAnalyticsState())
    val uiState: StateFlow<SalesAnalyticsState> = _uiState.asStateFlow()

    init {
        loadAnalytics(SalesPeriodFilter.YEAR, LocalDate.now().year)
    }

    fun setFilter(filter: SalesPeriodFilter) {
        if (_uiState.value.selectedFilter == filter && !_uiState.value.isLoading) return
        loadAnalytics(filter, _uiState.value.selectedYear)
    }

    fun setYear(year: Int) {
        if (_uiState.value.selectedYear == year && _uiState.value.selectedFilter == SalesPeriodFilter.YEAR && !_uiState.value.isLoading) return
        loadAnalytics(SalesPeriodFilter.YEAR, year)
    }

    fun setSubView(subView: AnalyticsSubView) {
        _uiState.value = _uiState.value.copy(activeSubView = subView)
    }

    fun refresh() {
        loadAnalytics(_uiState.value.selectedFilter, _uiState.value.selectedYear)
    }

    private fun loadAnalytics(filter: SalesPeriodFilter, targetYear: Int) {
        launchSafe {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedFilter = filter,
                selectedYear = targetYear
            )

            withContext(Dispatchers.IO) {
                val now = LocalDate.now()
                val (startDate, endDate) = when (filter) {
                    SalesPeriodFilter.LIFETIME -> null to null
                    SalesPeriodFilter.YEAR -> {
                        val start = LocalDate.of(targetYear, 1, 1).toString()
                        val end = LocalDate.of(targetYear, 12, 31).toString()
                        start to end
                    }
                    SalesPeriodFilter.THIS_MONTH -> {
                        val ym = YearMonth.from(now)
                        val start = ym.atDay(1).toString()
                        val end = ym.atEndOfMonth().toString()
                        start to end
                    }
                    SalesPeriodFilter.LAST_MONTH -> {
                        val ym = YearMonth.from(now.minusMonths(1))
                        val start = ym.atDay(1).toString()
                        val end = ym.atEndOfMonth().toString()
                        start to end
                    }
                }

                // 1. Fetch transactions & payments for period
                val transactions: List<DailyTransaction> = if (startDate != null && endDate != null) {
                    transactionDao.getTransactionsDateRange(startDate, endDate)
                } else {
                    transactionDao.getAllMilkTransactions()
                }

                val payments: List<Payment> = if (startDate != null && endDate != null) {
                    paymentDao.getPaymentsDateRange(startDate, endDate)
                } else {
                    paymentDao.getAllPaymentsList()
                }

                // Fetch all for lifetime calculations & available years
                val allLifetimeTransactions = if (startDate == null) transactions else transactionDao.getAllMilkTransactions()
                val allLifetimePayments = if (startDate == null) payments else paymentDao.getAllPaymentsList()

                // Calculate available years
                val yearsSet = sortedSetOf<Int>()
                yearsSet.add(now.year)
                for (t in allLifetimeTransactions) {
                    val d = t.date
                    if (d != null && d.length >= 4) {
                        d.substring(0, 4).toIntOrNull()?.let { yearsSet.add(it) }
                    }
                }
                for (p in allLifetimePayments) {
                    val d = p.date
                    if (d.length >= 4) {
                        d.substring(0, 4).toIntOrNull()?.let { yearsSet.add(it) }
                    }
                }
                val availableYears = yearsSet.toList().sortedDescending()

                // 2. Fetch all customers & routes for mappings
                val allCustomers = customerDao.getAllCustomersList()
                val allRoutes = routeGroupDao.getAllRouteGroupsList()
                val routeMap = allRoutes.mapNotNull { r ->
                    val id = r.id ?: return@mapNotNull null
                    id to (r.name ?: "Route #$id")
                }.toMap()

                // 3. Compute Overview
                val totalMilk = transactions.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.quantity ?: BigDecimal.ZERO) }
                val totalRevenue = transactions.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount ?: BigDecimal.ZERO) }
                val totalCollected = payments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
                val totalPendingDue = customerDao.getTotalOutstandingDue() ?: BigDecimal.ZERO

                var morningMilk = BigDecimal.ZERO
                var eveningMilk = BigDecimal.ZERO
                for (t in transactions) {
                    val q = t.quantity ?: BigDecimal.ZERO
                    if (t.session.equals("Morning", ignoreCase = true)) {
                        morningMilk = morningMilk.add(q)
                    } else if (t.session.equals("Evening", ignoreCase = true)) {
                        eveningMilk = eveningMilk.add(q)
                    }
                }

                val overview = SalesOverview(
                    totalMilk = totalMilk,
                    totalRevenue = totalRevenue,
                    totalCollected = totalCollected,
                    totalPendingDue = totalPendingDue,
                    morningMilk = morningMilk,
                    eveningMilk = eveningMilk
                )

                // 4. Monthly Breakdown Table
                val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
                val monthlyList: List<MonthlySalesItem> = when (filter) {
                    SalesPeriodFilter.YEAR -> {
                        // Generate all 12 months for targetYear
                        (1..12).map { monthNum ->
                            val ymKey = String.format(Locale.US, "%04d-%02d", targetYear, monthNum)
                            val mTrans = transactions.filter { it.date?.startsWith(ymKey) == true }
                            val mPays = payments.filter { it.date.startsWith(ymKey) }

                            val mMilk = mTrans.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.quantity ?: BigDecimal.ZERO) }
                            val mAmt = mTrans.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount ?: BigDecimal.ZERO) }
                            val mCol = mPays.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

                            val ym = YearMonth.of(targetYear, monthNum)
                            val displayName = ym.format(monthFormatter)

                            MonthlySalesItem(
                                monthKey = ymKey,
                                monthDisplayName = displayName,
                                milkSold = mMilk,
                                revenue = mAmt,
                                collected = mCol,
                                gap = mAmt.subtract(mCol)
                            )
                        }
                    }
                    SalesPeriodFilter.THIS_MONTH, SalesPeriodFilter.LAST_MONTH -> {
                        // Show the selected month
                        val ym = if (filter == SalesPeriodFilter.THIS_MONTH) YearMonth.from(now) else YearMonth.from(now.minusMonths(1))
                        val ymKey = ym.toString()
                        val mMilk = transactions.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.quantity ?: BigDecimal.ZERO) }
                        val mAmt = transactions.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount ?: BigDecimal.ZERO) }
                        val mCol = payments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

                        listOf(
                            MonthlySalesItem(
                                monthKey = ymKey,
                                monthDisplayName = ym.format(monthFormatter),
                                milkSold = mMilk,
                                revenue = mAmt,
                                collected = mCol,
                                gap = mAmt.subtract(mCol)
                            )
                        )
                    }
                    SalesPeriodFilter.LIFETIME -> {
                        // All distinct months in lifetime
                        val allMonthKeys = sortedSetOf(Comparator.reverseOrder<String>())
                        for (t in allLifetimeTransactions) {
                            val d = t.date
                            if (d != null && d.length >= 7) {
                                allMonthKeys.add(d.substring(0, 7))
                            }
                        }
                        for (p in allLifetimePayments) {
                            val d = p.date
                            if (d.length >= 7) {
                                allMonthKeys.add(d.substring(0, 7))
                            }
                        }
                        allMonthKeys.add(YearMonth.now().toString())

                        allMonthKeys.map { ymKey ->
                            val mTrans = allLifetimeTransactions.filter { it.date?.startsWith(ymKey) == true }
                            val mPays = allLifetimePayments.filter { it.date.startsWith(ymKey) }

                            val mMilk = mTrans.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.quantity ?: BigDecimal.ZERO) }
                            val mAmt = mTrans.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount ?: BigDecimal.ZERO) }
                            val mCol = mPays.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

                            val ym = try { YearMonth.parse(ymKey) } catch (_: Exception) { YearMonth.now() }
                            val displayName = ym.format(monthFormatter)

                            MonthlySalesItem(
                                monthKey = ymKey,
                                monthDisplayName = displayName,
                                milkSold = mMilk,
                                revenue = mAmt,
                                collected = mCol,
                                gap = mAmt.subtract(mCol)
                            )
                        }
                    }
                }

                // 5. Route-wise Breakdown
                val routeSalesList = mutableListOf<RouteSalesItem>()
                val customersByRoute = allCustomers.groupBy { it.routeGroupId?.takeIf { id -> id > 0L } }

                customersByRoute.forEach { (routeId, custList) ->
                    val custIds = custList.mapNotNull { it.id }.toSet()
                    val rTrans = transactions.filter { custIds.contains(it.customerId) }
                    val rMilk = rTrans.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.quantity ?: BigDecimal.ZERO) }
                    val rRev = rTrans.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount ?: BigDecimal.ZERO) }
                    val rDue = custList.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.currentDue ?: BigDecimal.ZERO) }

                    val pct = if (totalRevenue > BigDecimal.ZERO) {
                        rRev.divide(totalRevenue, 4, RoundingMode.HALF_UP).toFloat() * 100f
                    } else 0f

                    val rName = if (routeId != null) routeMap[routeId] ?: "Route #$routeId" else "Direct / Unassigned"

                    routeSalesList.add(
                        RouteSalesItem(
                            routeId = routeId,
                            routeName = rName,
                            customerCount = custList.size,
                            milkSold = rMilk,
                            revenue = rRev,
                            pendingDue = rDue,
                            percentageOfTotal = pct
                        )
                    )
                }
                routeSalesList.sortByDescending { it.revenue }

                // 6. Top 5 Highest Due Customers
                val topDueRaw = customerDao.getTopDueCustomers(5)
                val topDueList = topDueRaw.map { c ->
                    TopCustomerDueItem(
                        customerId = c.id ?: 0L,
                        customerName = c.name ?: "Customer #${c.id}",
                        mobile = c.mobile,
                        currentDue = c.currentDue ?: BigDecimal.ZERO,
                        routeName = c.routeGroupId?.let { routeMap[it] } ?: "Direct"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedYear = targetYear,
                    availableYears = availableYears,
                    overview = overview,
                    monthlySales = monthlyList,
                    routeSales = routeSalesList,
                    topDueCustomers = topDueList
                )
            }
        }
    }

    suspend fun exportPdfReport(context: Context): File? = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(isExportingPdf = true)
        try {
            val scopeTitle = when (_uiState.value.selectedFilter) {
                SalesPeriodFilter.YEAR -> "Year ${_uiState.value.selectedYear}"
                else -> _uiState.value.selectedFilter.label
            }
            val generator = SalesAnalyticsPDFGenerator(
                context = context,
                filterLabel = scopeTitle,
                overview = _uiState.value.overview,
                monthlySales = _uiState.value.monthlySales,
                routeSales = _uiState.value.routeSales,
                topDue = _uiState.value.topDueCustomers
            )
            generator.generatePDF()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            _uiState.value = _uiState.value.copy(isExportingPdf = false)
        }
    }
}
