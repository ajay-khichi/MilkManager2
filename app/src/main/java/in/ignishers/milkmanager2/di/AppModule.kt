package `in`.ignishers.milkmanager2.di

import `in`.ignishers.milkmanager2.database.AppDatabase
import `in`.ignishers.milkmanager2.database.CustomerRepository
import `in`.ignishers.milkmanager2.database.RouteGroupRepository
import `in`.ignishers.milkmanager2.database.PaymentRepository
import `in`.ignishers.milkmanager2.database.AppSettingsRepository
import `in`.ignishers.milkmanager2.ui.dashboard.DashboardViewModel
import `in`.ignishers.milkmanager2.ui.pos.PointOfSaleViewModel
import `in`.ignishers.milkmanager2.ui.sales.SalesAnalyticsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getDatabase(androidContext()) }
    
    // DAOs
    single { get<AppDatabase>().customerDao() }
    single { get<AppDatabase>().routeGroupDao() }
    single { get<AppDatabase>().dailyTransactionDao() }
    single { get<AppDatabase>().paymentDao() }
    single { get<AppDatabase>().milkRateDao() }
    single { get<AppDatabase>().appSettingsDao() }
    
    // Repositories
    single { CustomerRepository(get()) }
    single { RouteGroupRepository(get()) }
    single { PaymentRepository(get()) }
    single { `in`.ignishers.milkmanager2.database.MilkRateRepository(get()) }
    single { AppSettingsRepository(get()) }
    
    // ViewModels
    viewModel { DashboardViewModel(get(), get(), get(), get(), get()) }
    viewModel { PointOfSaleViewModel(get(), get(), get(), get()) }
    viewModel { SalesAnalyticsViewModel(get(), get(), get(), get()) }
}
