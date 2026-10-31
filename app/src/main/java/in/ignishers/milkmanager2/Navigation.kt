package `in`.ignishers.milkmanager2

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.ignishers.milkmanager2.ui.dashboard.DashboardScreen
import `in`.ignishers.milkmanager2.ui.pos.PointOfSaleScreen
import `in`.ignishers.milkmanager2.ui.sales.SalesAnalyticsScreen
import `in`.ignishers.milkmanager2.ui.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {
        
        composable("dashboard") {
            DashboardScreen(
                currentRouteId = null,
                onNavigateToRoute = { routeId ->
                    navController.navigate("route/$routeId")
                },
                onNavigateBack = { },
                onNavigateToPos = { customerId ->
                    navController.navigate("pos/$customerId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToSalesAnalytics = {
                    navController.navigate("sales_analytics")
                }
            )
        }

        composable(
            "route/{routeId}",
            arguments = listOf(navArgument("routeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getLong("routeId")
            DashboardScreen(
                currentRouteId = routeId,
                onNavigateToRoute = { subRouteId ->
                    val popped = navController.popBackStack("route/$subRouteId", inclusive = false)
                    if (!popped) {
                        navController.navigate("route/$subRouteId")
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRoot = {
                    navController.popBackStack("dashboard", inclusive = false)
                },
                onNavigateToPos = { customerId ->
                    navController.navigate("pos/$customerId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToSalesAnalytics = {
                    navController.navigate("sales_analytics")
                }
            )
        }

        composable(
            "pos/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getLong("customerId") ?: 0L
            PointOfSaleScreen(
                customerId = customerId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("sales_analytics") {
            SalesAnalyticsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
