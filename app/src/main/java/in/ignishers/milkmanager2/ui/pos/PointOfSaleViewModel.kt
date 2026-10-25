package `in`.ignishers.milkmanager2.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.ignishers.milkmanager2.database.CustomerRepository
import `in`.ignishers.milkmanager2.database.DailyTransactionRoomDao
import `in`.ignishers.milkmanager2.database.MilkRateRepository
import `in`.ignishers.milkmanager2.database.PaymentRepository
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.DailyTransaction
import `in`.ignishers.milkmanager2.models.Payment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.ui.BaseViewModel

class PointOfSaleViewModel(
    private val customerRepository: CustomerRepository,
    private val transactionDao: DailyTransactionRoomDao,
    private val milkRateRepository: MilkRateRepository,
    private val paymentRepository: PaymentRepository
) : BaseViewModel() {
    
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer
    
    private val _currentDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    val currentDate: StateFlow<String> = _currentDate
    
    private val _session = MutableStateFlow("Morning")
    val session: StateFlow<String> = _session
    
    private val _quantity = MutableStateFlow(BigDecimal.ZERO)
    val quantity: StateFlow<BigDecimal> = _quantity
    
    private val _amount = MutableStateFlow(BigDecimal.ZERO)
    val amount: StateFlow<BigDecimal> = _amount
    
    private val _milkRate = MutableStateFlow(BigDecimal("60.0"))
    val milkRate: StateFlow<BigDecimal> = _milkRate

    val todayTransactions = MutableStateFlow<List<DailyTransaction>>(emptyList())
    val todayPayments = MutableStateFlow<List<Payment>>(emptyList())

    private val _routeCustomers = MutableStateFlow<List<Customer>>(emptyList())
    val routeCustomers: StateFlow<List<Customer>> = _routeCustomers

    private var currentRouteId: Long? = null
    private var customerFlowJob: Job? = null
    private var routeCustomersJob: Job? = null

    private val _currentMonthBill = MutableStateFlow(BigDecimal.ZERO)
    val currentMonthBill: StateFlow<BigDecimal> = _currentMonthBill

    val lastMonthDue: StateFlow<BigDecimal> = combine(_customer, _currentMonthBill) { cust, bill ->
        val total = cust?.currentDue ?: BigDecimal.ZERO
        total.subtract(bill)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)
    
    fun loadCustomer(customerId: Long) {
        customerFlowJob?.cancel()
        customerFlowJob = viewModelScope.launch {
            val cust = customerRepository.getCustomerById(customerId)
            _customer.value = cust
            val rate = milkRateRepository.getLatestRate()?.price ?: BigDecimal("60.0")
            _milkRate.value = rate
            _quantity.value = if (_session.value == "Morning") cust?.defaultQtyMorning ?: BigDecimal.ZERO else cust?.defaultQtyEvening ?: BigDecimal.ZERO
            calculateAmount()

            // Load route customers if route changed or not yet loaded
            val routeId = cust?.routeGroupId?.takeIf { it > 0L }
            if (routeCustomersJob == null || currentRouteId != routeId) {
                currentRouteId = routeId
                routeCustomersJob?.cancel()
                routeCustomersJob = viewModelScope.launch {
                    val flow = if (routeId != null) {
                        customerRepository.getCustomersByRoute(routeId)
                    } else {
                        customerRepository.getRootCustomers()
                    }
                    flow.collect { list ->
                        _routeCustomers.value = list
                    }
                }
            }

            launch {
                transactionDao.getTransactionsByDateFlow(customerId, _currentDate.value).collect {
                    todayTransactions.value = it
                }
            }

            launch {
                paymentRepository.getPaymentsByDate(customerId, _currentDate.value).collect {
                    todayPayments.value = it
                }
            }

            val cal = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            try {
                sdf.parse(_currentDate.value)?.let { cal.time = it }
            } catch (_: Exception) {}
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val startOfMonth = sdf.format(cal.time)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val endOfMonth = sdf.format(cal.time)

            launch {
                transactionDao.getCurrentMonthMilkTotalFlow(customerId, startOfMonth, endOfMonth).collect { total ->
                    _currentMonthBill.value = total ?: BigDecimal.ZERO
                }
            }
        }
    }

    fun navigateCustomer(isNext: Boolean): Int? {
        val currentList = _routeCustomers.value
        val currentCust = _customer.value ?: return R.string.customer_not_loaded
        val currentId = currentCust.id ?: return R.string.customer_not_loaded

        if (currentList.isEmpty()) {
            return if (isNext) R.string.no_next_customer else R.string.no_prev_customer
        }

        val currentIndex = currentList.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) {
            return if (isNext) R.string.no_next_customer else R.string.no_prev_customer
        }

        if (isNext) {
            if (currentIndex < currentList.size - 1) {
                val nextCustomer = currentList[currentIndex + 1]
                val nextId = nextCustomer.id ?: return R.string.invalid_customer
                loadCustomer(nextId)
                return null
            } else {
                return R.string.no_next_customer
            }
        } else {
            if (currentIndex > 0) {
                val prevCustomer = currentList[currentIndex - 1]
                val prevId = prevCustomer.id ?: return R.string.invalid_customer
                loadCustomer(prevId)
                return null
            } else {
                return R.string.no_prev_customer
            }
        }
    }
    
    fun setSession(newSession: String) {
        _session.value = newSession
        val cust = _customer.value
        if (cust != null) {
            _quantity.value = if (newSession == "Morning") cust.defaultQtyMorning ?: BigDecimal.ZERO else cust.defaultQtyEvening ?: BigDecimal.ZERO
            calculateAmount()
        }
    }
    
    fun setQuantity(newQuantity: BigDecimal) {
        _quantity.value = newQuantity
        calculateAmount()
    }
    
    private fun calculateAmount() {
        _amount.value = _quantity.value.multiply(_milkRate.value).setScale(2, RoundingMode.HALF_UP)
    }
    
    fun toggleCustomerAutoEntry(enabled: Boolean) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            val custId = cust.id ?: return@launchSafe
            cust.autoEntryEnabled = enabled
            customerRepository.updateAutoEntry(custId, enabled)
            _customer.value = customerRepository.getCustomerById(custId)
        }
    }

    fun addQuickEntry(optionKey: String) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            val rate = _milkRate.value
            val (qty, amt) = when (optionKey) {
                "10rs" -> {
                    val a = BigDecimal("10")
                    val q = if (rate > BigDecimal.ZERO) a.divide(rate, 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
                    q to a
                }
                "20rs" -> {
                    val a = BigDecimal("20")
                    val q = if (rate > BigDecimal.ZERO) a.divide(rate, 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
                    q to a
                }
                "30rs" -> {
                    val a = BigDecimal("30")
                    val q = if (rate > BigDecimal.ZERO) a.divide(rate, 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
                    q to a
                }
                "50rs" -> {
                    val a = BigDecimal("50")
                    val q = if (rate > BigDecimal.ZERO) a.divide(rate, 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
                    q to a
                }
                "250ml" -> {
                    val q = BigDecimal("0.25")
                    val a = q.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    q to a
                }
                "500ml" -> {
                    val q = BigDecimal("0.50")
                    val a = q.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    q to a
                }
                "750ml" -> {
                    val q = BigDecimal("0.75")
                    val a = q.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    q to a
                }
                "1l" -> {
                    val q = BigDecimal("1.00")
                    val a = q.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    q to a
                }
                else -> return@launchSafe
            }

            val transaction = DailyTransaction(
                customerId = cust.id ?: 0L,
                date = _currentDate.value,
                session = _session.value,
                quantity = qty,
                amount = amt,
                milkType = "Extra",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            transactionDao.insert(transaction)
            cust.currentDue = (cust.currentDue ?: BigDecimal.ZERO).add(amt)
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    fun addManualEntry(
        date: String,
        session: String,
        entryType: String,
        inputValue: BigDecimal
    ) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            val rate = _milkRate.value
            val (qty, amt) = when (entryType) {
                "Litres" -> {
                    val q = inputValue.setScale(2, RoundingMode.HALF_UP)
                    val a = q.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    q to a
                }
                "ml" -> {
                    val q = inputValue.divide(BigDecimal("1000"), 3, RoundingMode.HALF_UP)
                    val a = q.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    q to a
                }
                "Rupee" -> {
                    val a = inputValue.setScale(2, RoundingMode.HALF_UP)
                    val q = if (rate > BigDecimal.ZERO) a.divide(rate, 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
                    q to a
                }
                else -> return@launchSafe
            }

            val transaction = DailyTransaction(
                customerId = cust.id ?: 0L,
                date = date,
                session = session,
                quantity = qty,
                amount = amt,
                milkType = "Extra",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            transactionDao.insert(transaction)
            cust.currentDue = (cust.currentDue ?: BigDecimal.ZERO).add(amt)
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    fun addPayment(amount: BigDecimal, mode: String, date: String = _currentDate.value) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            val payment = Payment(
                customerId = cust.id ?: 0L,
                date = date,
                amount = amount,
                paymentMode = mode,
                syncUpdatedAt = System.currentTimeMillis()
            )
            paymentRepository.insert(payment)
            cust.currentDue = (cust.currentDue ?: BigDecimal.ZERO).subtract(amount)
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    fun addDefaultEntry() {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            val qty = if (_session.value == "Morning") cust.defaultQtyMorning else cust.defaultQtyEvening
            if (qty == null || qty <= BigDecimal.ZERO) return@launchSafe
            val rate = _milkRate.value
            val amt = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP)

            val transaction = DailyTransaction(
                customerId = cust.id ?: 0L,
                date = _currentDate.value,
                session = _session.value,
                quantity = qty,
                amount = amt,
                milkType = "Regular",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            transactionDao.insert(transaction)
            cust.currentDue = (cust.currentDue ?: BigDecimal.ZERO).add(amt)
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    fun deleteMilkTransaction(transaction: DailyTransaction) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            transactionDao.delete(transaction.transactionId)
            val reversedDue = (cust.currentDue ?: BigDecimal.ZERO).subtract(transaction.amount ?: BigDecimal.ZERO)
            cust.currentDue = reversedDue
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    fun deletePayment(payment: Payment) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            paymentRepository.delete(payment)
            val restoredDue = (cust.currentDue ?: BigDecimal.ZERO).add(payment.amount)
            cust.currentDue = restoredDue
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    fun updateDefaultQuantities(morningQty: BigDecimal, eveningQty: BigDecimal) {
        launchSafe {
            val cust = _customer.value ?: return@launchSafe
            cust.defaultQtyMorning = morningQty
            cust.defaultQtyEvening = eveningQty
            customerRepository.update(cust)
            _customer.value = customerRepository.getCustomerById(cust.id ?: return@launchSafe)
        }
    }

    suspend fun getMonthlyBillReport(yearMonth: YearMonth): BillReportData? {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        val title = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        val isPastMonth = yearMonth.isBefore(YearMonth.now())
        return generateBillReport(start, end, title, isLastMonth = isPastMonth)
    }

    suspend fun generateBillReport(
        startDate: String,
        endDate: String,
        reportTitle: String,
        isLastMonth: Boolean = false
    ): BillReportData? {
        val cust = _customer.value ?: return null
        val custId = cust.id ?: return null

        val transList = if (startDate.isEmpty() && endDate.isEmpty()) {
            transactionDao.getAllTransactionsForCustomer(custId)
        } else {
            transactionDao.getTransactionsByDateRange(custId, startDate, endDate)
        }

        val payList = if (startDate.isEmpty() && endDate.isEmpty()) {
            paymentRepository.getAllPaymentsForCustomer(custId)
        } else {
            paymentRepository.getPaymentsByDateRange(custId, startDate, endDate)
        }

        val postMonthPays = if (isLastMonth && endDate.isNotEmpty()) {
            try {
                val nextStart = LocalDate.parse(endDate).plusDays(1).toString()
                val todayStr = _currentDate.value
                if (nextStart <= todayStr) {
                    paymentRepository.getPaymentsByDateRange(custId, nextStart, todayStr)
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Determine all months in the selected range
        val sortedMonths = if (startDate.length >= 7 && endDate.length >= 7) {
            try {
                val startYm = YearMonth.parse(startDate.substring(0, 7))
                val endYm = YearMonth.parse(endDate.substring(0, 7))
                val list = mutableListOf<String>()
                var curr = startYm
                while (!curr.isAfter(endYm)) {
                    list.add(curr.toString())
                    curr = curr.plusMonths(1)
                }
                if (list.isEmpty()) {
                    list.add(startYm.toString())
                }
                list
            } catch (_: Exception) {
                val monthKeys = mutableSetOf<String>()
                transList.forEach { t -> t.date?.takeIf { it.length >= 7 }?.let { monthKeys.add(it.substring(0, 7)) } }
                payList.forEach { p -> p.date?.takeIf { it.length >= 7 }?.let { monthKeys.add(it.substring(0, 7)) } }
                if (monthKeys.isEmpty()) {
                    monthKeys.add(_currentDate.value.substring(0, 7))
                }
                monthKeys.sorted()
            }
        } else {
            val monthKeys = mutableSetOf<String>()
            transList.forEach { t -> t.date?.takeIf { it.length >= 7 }?.let { monthKeys.add(it.substring(0, 7)) } }
            payList.forEach { p -> p.date?.takeIf { it.length >= 7 }?.let { monthKeys.add(it.substring(0, 7)) } }
            if (monthKeys.isEmpty()) {
                val defaultKey = if (startDate.length >= 7) startDate.substring(0, 7) else _currentDate.value.substring(0, 7)
                monthKeys.add(defaultKey)
            }
            monthKeys.sorted()
        }
        val monthDataList = mutableListOf<MonthStatementData>()

        // Calculate initial opening balance for first month in range
        val firstMonthStart = if (startDate.isNotEmpty()) startDate else "${sortedMonths.first()}-01"
        val totalDueNow = cust.currentDue ?: BigDecimal.ZERO
        val milkSinceStart = transactionDao.getTransactionsByDateRange(custId, firstMonthStart, _currentDate.value)
            .sumOf { it.amount ?: BigDecimal.ZERO }
        val paySinceStart = paymentRepository.getPaymentsByDateRange(custId, firstMonthStart, _currentDate.value)
            .sumOf { it.amount }

        var runningOpening = totalDueNow.subtract(milkSinceStart).add(paySinceStart)
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

        for (mKey in sortedMonths) {
            val monthDate = try { LocalDate.parse("$mKey-01") } catch (_: Exception) { LocalDate.now() }
            val mTitle = monthDate.format(monthFormatter)
            val daysInMonth = monthDate.lengthOfMonth()

            val mTrans = transList.filter { it.date?.startsWith(mKey) == true }
            val mPays = payList.filter { it.date?.startsWith(mKey) == true }

            val days = (1..daysInMonth).map { dayNum ->
                val dayStr = String.format("%02d", dayNum)
                val fullDate = "$mKey-$dayStr"
                val dTrans = mTrans.filter { it.date == fullDate }
                val dPays = mPays.filter { it.date == fullDate }

                val morn = dTrans.filter { it.session.equals("Morning", ignoreCase = true) }
                val eve = dTrans.filter { it.session.equals("Evening", ignoreCase = true) }

                DayStatementItem(
                    day = dayNum,
                    date = fullDate,
                    mornQty = morn.sumOf { it.quantity ?: BigDecimal.ZERO },
                    mornAmt = morn.sumOf { it.amount ?: BigDecimal.ZERO },
                    eveQty = eve.sumOf { it.quantity ?: BigDecimal.ZERO },
                    eveAmt = eve.sumOf { it.amount ?: BigDecimal.ZERO },
                    paymentAmt = dPays.sumOf { it.amount }
                )
            }

            val totalMilk = days.sumOf { it.mornQty + it.eveQty }
            val totalBill = days.sumOf { it.mornAmt + it.eveAmt }
            val paidInMonth = days.sumOf { it.paymentAmt }

            val postPaysForThisMonth = if (isLastMonth && mKey == sortedMonths.last()) postMonthPays else emptyList()
            val postPaidTotal = postPaysForThisMonth.sumOf { it.amount }

            val finalDue = runningOpening.add(totalBill).subtract(paidInMonth).subtract(postPaidTotal)
            val isPaid = finalDue <= BigDecimal.ZERO

            monthDataList.add(
                MonthStatementData(
                    monthTitle = mTitle,
                    monthKey = mKey,
                    days = days,
                    openingBalance = runningOpening,
                    totalMilk = totalMilk,
                    totalBill = totalBill,
                    paidInMonth = paidInMonth,
                    postMonthPayments = postPaysForThisMonth,
                    finalDue = finalDue,
                    isPaid = isPaid
                )
            )

            runningOpening = runningOpening.add(totalBill).subtract(paidInMonth)
        }

        val overallFinalDue = monthDataList.lastOrNull()?.finalDue ?: cust.currentDue ?: BigDecimal.ZERO

        return BillReportData(
            customer = cust,
            title = reportTitle,
            months = monthDataList,
            overallFinalDue = overallFinalDue,
            allTransactions = transList,
            allPayments = payList,
            postMonthPayments = postMonthPays
        )
    }
}

data class DayStatementItem(
    val day: Int,
    val date: String,
    val mornQty: BigDecimal = BigDecimal.ZERO,
    val mornAmt: BigDecimal = BigDecimal.ZERO,
    val eveQty: BigDecimal = BigDecimal.ZERO,
    val eveAmt: BigDecimal = BigDecimal.ZERO,
    val paymentAmt: BigDecimal = BigDecimal.ZERO
)

data class MonthStatementData(
    val monthTitle: String,
    val monthKey: String,
    val days: List<DayStatementItem>,
    val openingBalance: BigDecimal,
    val totalMilk: BigDecimal,
    val totalBill: BigDecimal,
    val paidInMonth: BigDecimal,
    val postMonthPayments: List<Payment>,
    val finalDue: BigDecimal,
    val isPaid: Boolean
)

data class BillReportData(
    val customer: Customer,
    val title: String,
    val months: List<MonthStatementData>,
    val overallFinalDue: BigDecimal,
    val allTransactions: List<DailyTransaction>,
    val allPayments: List<Payment>,
    val postMonthPayments: List<Payment>
)
