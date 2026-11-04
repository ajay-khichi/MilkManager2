package `in`.ignishers.milkmanager2.ui.purchase

import `in`.ignishers.milkmanager2.models.MilkPurchase
import `in`.ignishers.milkmanager2.models.Supplier
import java.math.BigDecimal

data class SupplierMonthlySummary(
    val supplier: Supplier,
    val totalQuantity: BigDecimal = BigDecimal.ZERO,
    val morningQuantity: BigDecimal = BigDecimal.ZERO,
    val eveningQuantity: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val isPaid: Boolean = false,
    val paymentDate: String? = null,
    val entryCount: Int = 0,
    val dailyPurchases: List<MilkPurchase> = emptyList()
)

data class SupplierDailyInputState(
    val supplier: Supplier,
    val existingPurchaseId: Long = 0,
    val quantityInput: String = "",
    val rateInput: String = "",
    val isSaved: Boolean = false
)

data class PurchaseUiState(
    val selectedDate: String = "",
    val selectedSession: String = "Morning",
    val selectedYearMonth: String = "",
    val suppliers: List<Supplier> = emptyList(),
    val dailyInputs: List<SupplierDailyInputState> = emptyList(),
    val monthlySummaries: List<SupplierMonthlySummary> = emptyList(),
    val todayTotalQuantity: BigDecimal = BigDecimal.ZERO,
    val todayTotalAmount: BigDecimal = BigDecimal.ZERO,
    val monthTotalQuantity: BigDecimal = BigDecimal.ZERO,
    val monthTotalAmount: BigDecimal = BigDecimal.ZERO,
    val activeTab: Int = 0, // 0 = Daily Entry, 1 = Monthly Summary
    val isLoading: Boolean = false
)
