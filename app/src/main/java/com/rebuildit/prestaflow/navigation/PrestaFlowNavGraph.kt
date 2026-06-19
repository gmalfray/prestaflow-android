package com.rebuildit.prestaflow.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.rebuildit.prestaflow.ui.carts.CartDetailRoute
import com.rebuildit.prestaflow.ui.carts.CartsRoute
import com.rebuildit.prestaflow.ui.clients.ClientDetailRoute
import com.rebuildit.prestaflow.ui.clients.ClientsRoute
import com.rebuildit.prestaflow.ui.dashboard.DashboardRoute
import com.rebuildit.prestaflow.ui.orders.OrderDetailRoute
import com.rebuildit.prestaflow.ui.orders.OrdersRoute
import com.rebuildit.prestaflow.ui.products.ProductDetailRoute
import com.rebuildit.prestaflow.ui.products.ProductsRoute
import com.rebuildit.prestaflow.ui.settings.SettingsRoute

@Composable
fun PrestaFlowNavGraph(
    onLogout: () -> Unit,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Dashboard.route,
        modifier = modifier
    ) {
        composable(AppDestination.Dashboard.route) { DashboardRoute() }
        composable(AppDestination.Orders.route) {
            OrdersRoute(
                onOrderClick = { orderId ->
                    navController.navigate("${AppDestination.Orders.route}/$orderId")
                }
            )
        }
        composable(
            route = "${AppDestination.Orders.route}/{orderId}",
            arguments = listOf(
                navArgument("orderId") { type = NavType.LongType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "prestaflow://orders/{orderId}" }
            )
        ) {
            OrderDetailRoute(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppDestination.Products.route) {
            ProductsRoute(
                onProductClick = { productId ->
                    navController.navigate("${AppDestination.Products.route}/$productId")
                }
            )
        }
        composable(
            route = "${AppDestination.Products.route}/{productId}",
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType }
            )
        ) {
            ProductDetailRoute(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppDestination.Clients.route) {
            ClientsRoute(
                onClientClick = { clientId ->
                    navController.navigate("${AppDestination.Clients.route}/$clientId")
                }
            )
        }
        composable(
            route = "${AppDestination.Clients.route}/{clientId}",
            arguments = listOf(
                navArgument("clientId") { type = NavType.LongType }
            )
        ) {
            ClientDetailRoute(
                onBackClick = { navController.popBackStack() },
                onOrderClick = { orderId ->
                    navController.navigate("${AppDestination.Orders.route}/$orderId")
                }
            )
        }
        composable(AppDestination.Carts.route) {
            CartsRoute(
                onCartClick = { cartId ->
                    navController.navigate("${AppDestination.Carts.route}/$cartId")
                }
            )
        }
        composable(
            route = "${AppDestination.Carts.route}/{cartId}",
            arguments = listOf(
                navArgument("cartId") { type = NavType.IntType }
            )
        ) {
            CartDetailRoute(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsRoute(onLogoutClick = onLogout)
        }
    }
}
