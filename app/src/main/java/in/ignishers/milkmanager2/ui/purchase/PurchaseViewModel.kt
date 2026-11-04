package `in`.ignishers.milkmanager2.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.ignishers.milkmanager2.database.MilkPurchaseRoomDao
import `in`.ignishers.milkmanager2.database.SupplierRoomDao
import `in`.ignishers.milkmanager2.models.MilkPurchase
import `in`.ignishers.milkmanager2.models.Supplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PurchaseViewModel(
    private val supplierDao: SupplierRoomDao,
    private val purchaseDao: MilkPurchaseRoomDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentYearMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val defaultSession = if (currentHour < 16) "Morning" else "Evening"

        _uiState.value = _uiState.value.copy(
            selectedDate = today,
            selectedSession = defaultSession,
            selectedYearMonth = currentYearMonth
        )
        loadData()
    }

    fun setDate(date: String) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadData()
    }

    fun setSession(session: String) {
        _uiState.value = _uiState.value.copy(selectedSession = session)
        loadData()
    }

    fun setYearMonth(yearMonth: String) {
        _uiState.value = _uiState.value.copy(selectedYearMonth = yearMonth)
        loadData()
    }

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = _uiState.value
            val suppliers = supplierDao.getAllActiveSuppliersList()

            // 1. Daily Inputs for selectedDate and selectedSession
            val dailyPurchases = purchaseDao.getPurchasesForDateAndSessionList(state.selectedDate, state.selectedSession)
            val purchaseBySupplier = dailyPurchases.associateBy { it.supplierId }

            var todayQty = BigDecimal.ZERO
            var todayAmt = BigDecimal.ZERO

            val inputs = suppliers.map { s ->
                val existing = purchaseBySupplier[s.id]
                if (existing != null) {
                    todayQty = todayQty.add(existing.quantity)
                    todayAmt = todayAmt.add(existing.totalAmount)
                    SupplierDailyInputState(
                        supplier = s,
                        existingPurchaseId = existing.id,
                        quantityInput = existing.quantity.stripTrailingZeros().toPlainString(),
                        rateInput = existing.pricePerLitre.stripTrailingZeros().toPlainString(),
                        isSaved = true
                    )
                } else {
                    SupplierDailyInputState(
                        supplier = s,
                        existingPurchaseId = 0,
                        quantityInput = "",
                        rateInput = s.defaultRate.stripTrailingZeros().toPlainString(),
                        isSaved = false
                    )
                }
            }

            // 2. Monthly Summaries for selectedYearMonth
            val monthPurchases = purchaseDao.getPurchasesForMonthList(state.selectedYearMonth)
            val monthBySupplier = monthPurchases.groupBy { it.supplierId }

            var monthTotalQty = BigDecimal.ZERO
            var monthTotalAmt = BigDecimal.ZERO

            val summaries = suppliers.map { s ->
                val sPurchases = monthBySupplier[s.id] ?: emptyList()
                var sTotalQty = BigDecimal.ZERO
                var sMorningQty = BigDecimal.ZERO
                var sEveningQty = BigDecimal.ZERO
                var sTotalAmt = BigDecimal.ZERO
                var anyPaid = false
                var latestPaymentDate: String? = null

                sPurchases.forEach { p ->
                    sTotalQty = sTotalQty.add(p.quantity)
                    sTotalAmt = sTotalAmt.add(p.totalAmount)
                    if (p.session.equals("Morning", ignoreCase = true)) {
                        sMorningQty = sMorningQty.add(p.quantity)
                    } else {
                        sEveningQty = sEveningQty.add(p.quantity)
                    }
                    if (p.isPaid == 1) {
                        anyPaid = true
                        if (p.paymentDate != null) latestPaymentDate = p.paymentDate
                    }
                }

                monthTotalQty = monthTotalQty.add(sTotalQty)
                monthTotalAmt = monthTotalAmt.add(sTotalAmt)

                val allPaid = sPurchases.isNotEmpty() && sPurchases.all { it.isPaid == 1 }

                SupplierMonthlySummary(
                    supplier = s,
                    totalQuantity = sTotalQty,
                    morningQuantity = sMorningQty,
                    eveningQuantity = sEveningQty,
                    totalAmount = sTotalAmt,
                    isPaid = allPaid,
                    paymentDate = latestPaymentDate,
                    entryCount = sPurchases.size,
                    dailyPurchases = sPurchases
                )
            }

            _uiState.value = _uiState.value.copy(
                suppliers = suppliers,
                dailyInputs = inputs,
                monthlySummaries = summaries,
                todayTotalQuantity = todayQty,
                todayTotalAmount = todayAmt,
                monthTotalQuantity = monthTotalQty,
                monthTotalAmount = monthTotalAmt,
                isLoading = false
            )
        }
    }

    fun updateQuantityInput(supplierId: Long, text: String) {
        val updated = _uiState.value.dailyInputs.map { item ->
            if (item.supplier.id == supplierId) {
                item.copy(quantityInput = text, isSaved = false)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(dailyInputs = updated)
    }

    fun updateRateInput(supplierId: Long, text: String) {
        val updated = _uiState.value.dailyInputs.map { item ->
            if (item.supplier.id == supplierId) {
                item.copy(rateInput = text, isSaved = false)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(dailyInputs = updated)
    }

    fun savePurchaseEntry(supplierId: Long) {
        viewModelScope.launch {
            val input = _uiState.value.dailyInputs.find { it.supplier.id == supplierId } ?: return@launch
            val qty = input.quantityInput.trim().toBigDecimalOrNull()
            if (qty == null || qty <= BigDecimal.ZERO) {
                // If quantity is cleared/0, remove the entry if existed
                if (input.existingPurchaseId > 0) {
                    purchaseDao.delete(input.existingPurchaseId)
                }
                loadData()
                return@launch
            }

            val rate = input.rateInput.trim().toBigDecimalOrNull() ?: input.supplier.defaultRate
            val amount = qty.multiply(rate)

            val purchase = MilkPurchase(
                id = input.existingPurchaseId,
                supplierId = supplierId,
                purchaseDate = _uiState.value.selectedDate,
                session = _uiState.value.selectedSession,
                quantity = qty,
                pricePerLitre = rate,
                totalAmount = amount,
                isPaid = 0,
                updatedAt = System.currentTimeMillis()
            )
            purchaseDao.insert(purchase)
            loadData()
        }
    }

    fun deletePurchaseEntry(supplierId: Long) {
        viewModelScope.launch {
            purchaseDao.deleteForSupplierDateSession(
                supplierId = supplierId,
                date = _uiState.value.selectedDate,
                session = _uiState.value.selectedSession
            )
            loadData()
        }
    }

    fun addSupplier(name: String, mobile: String?, defaultRate: BigDecimal) {
        viewModelScope.launch {
            val count = supplierDao.getAllActiveSuppliersList().size
            val supplier = Supplier(
                name = name.trim(),
                mobile = mobile?.trim()?.takeIf { it.isNotBlank() },
                defaultRate = defaultRate,
                sortOrder = count + 1
            )
            supplierDao.insert(supplier)
            loadData()
        }
    }

    fun updateSupplier(supplier: Supplier) {
        viewModelScope.launch {
            supplier.updatedAt = System.currentTimeMillis()
            supplierDao.update(supplier)
            loadData()
        }
    }

    fun deleteSupplier(supplierId: Long) {
        viewModelScope.launch {
            supplierDao.softDelete(supplierId)
            loadData()
        }
    }

    fun toggleMonthPaidStatus(supplierId: Long, markPaid: Boolean) {
        viewModelScope.launch {
            val yearMonth = _uiState.value.selectedYearMonth
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val status = if (markPaid) 1 else 0
            val paymentDate = if (markPaid) today else null
            purchaseDao.updateMonthPaidStatus(
                supplierId = supplierId,
                yearMonth = yearMonth,
                isPaid = status,
                paymentDate = paymentDate
            )
            loadData()
        }
    }
}
