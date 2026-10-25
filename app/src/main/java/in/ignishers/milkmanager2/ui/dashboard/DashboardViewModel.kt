package `in`.ignishers.milkmanager2.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.ignishers.milkmanager2.database.CustomerRepository
import `in`.ignishers.milkmanager2.database.RouteGroupRepository
import `in`.ignishers.milkmanager2.database.DailyTransactionRoomDao
import `in`.ignishers.milkmanager2.database.MilkRateRepository
import `in`.ignishers.milkmanager2.database.AppSettingsRepository
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.RouteGroup
import `in`.ignishers.milkmanager2.models.MilkRate
import `in`.ignishers.milkmanager2.scheduler.AutoEntryManager
import `in`.ignishers.milkmanager2.scheduler.AutoEntryScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

import `in`.ignishers.milkmanager2.ui.BaseViewModel

class DashboardViewModel(
    private val customerRepository: CustomerRepository,
    private val routeGroupRepository: RouteGroupRepository,
    private val transactionDao: DailyTransactionRoomDao,
    private val milkRateRepository: MilkRateRepository,
    private val appSettingsRepository: AppSettingsRepository
) : BaseViewModel() {
    
    val customers: StateFlow<List<Customer>> = customerRepository.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val routeGroups: StateFlow<List<RouteGroup>> = routeGroupRepository.getAllRouteGroups()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isAutoEntryMasterEnabled: StateFlow<Boolean> = appSettingsRepository.isAutoEntryEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _totalRevenue = MutableStateFlow(BigDecimal.ZERO)
    val totalRevenue: StateFlow<BigDecimal> = _totalRevenue

    private val _totalMilk = MutableStateFlow(BigDecimal.ZERO)
    val totalMilk: StateFlow<BigDecimal> = _totalMilk

    
    private val _currentMilkRate = MutableStateFlow<MilkRate?>(null)
    val currentMilkRate: StateFlow<MilkRate?> = _currentMilkRate

    init {
        loadMetrics()
    }

    private fun loadMetrics() {
        launchSafe {
            _totalRevenue.value = transactionDao.getTotalRevenueLifetime() ?: BigDecimal.ZERO
            _totalMilk.value = transactionDao.getTotalMilkLifetime() ?: BigDecimal.ZERO
            _currentMilkRate.value = milkRateRepository.getLatestRate()
        }
    }

    fun setAutoEntryMasterEnabled(context: Context, enabled: Boolean) {
        launchSafe {
            appSettingsRepository.setAutoEntryEnabled(enabled)
            if (enabled) {
                AutoEntryScheduler.scheduleAll(context)
                val entriesMade = AutoEntryManager.checkAndRunPendingAutoEntries(context)
                if (entriesMade > 0) {
                    loadMetrics()
                }
            } else {
                AutoEntryScheduler.cancelAll(context)
            }
        }
    }

    fun checkPendingAutoEntries(context: Context) {
        launchSafe {
            val entriesMade = AutoEntryManager.checkAndRunPendingAutoEntries(context)
            if (entriesMade > 0) {
                loadMetrics()
            }
        }
    }

    fun addRouteGroup(routeGroup: RouteGroup) {
        launchSafe {
            routeGroupRepository.insert(routeGroup)
        }
    }

    
    fun updateCustomer(customer: Customer) {
        launchSafe {
            customerRepository.update(customer)
        }
    }

    fun updateCustomerSortOrder(customer: Customer, newOrder: Int) {
        customer.sortOrder = newOrder
        updateCustomer(customer)
    }

    fun updateRouteGroup(routeGroup: RouteGroup) {
        launchSafe {
            routeGroupRepository.update(routeGroup)
        }
    }

    fun updateRouteSortOrder(routeGroup: RouteGroup, newOrder: Int) {
        routeGroup.sortOrder = newOrder
        launchSafe { routeGroupRepository.update(routeGroup) }
    }

    fun deleteCustomer(customer: Customer) {
        launchSafe {
            customerRepository.delete(customer.id!!)
        }
    }

    fun deleteRouteGroup(routeGroup: RouteGroup) {
        launchSafe {
            routeGroupRepository.delete(routeGroup.id!!)
        }
    }

    
    private fun loadMilkRate() {
        launchSafe {
            _currentMilkRate.value = milkRateRepository.getLatestRate()
        }
    }

    fun saveMilkRate(rate: MilkRate) {
        launchSafe {
            milkRateRepository.insert(rate)
            loadMilkRate()
        }
    }

    fun addCustomer(customer: Customer) {
        launchSafe {
            customerRepository.insert(customer)
        }
    }
}
