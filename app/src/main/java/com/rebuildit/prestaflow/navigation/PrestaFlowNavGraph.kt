package com.rebuildit.prestaflow.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.rebuildit.prestaflow.ui.dashboard.DashboardRoute
import com.rebuildit.prestaflow.ui.orders.OrdersRoute
import com.rebuildit.prestaflow.ui.orders.OrderDetailRoute
import com.rebuildit.prestaflow.ui.products.ProductsRoute
import com.rebuildit.prestaflow.ui.clients.ClientsRoute

@Composable
fun PrestaFlowNavGraph(
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
            com.rebuildit.prestaflow.ui.products.ProductDetailRoute(
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
            com.rebuildit.prestaflow.ui.clients.ClientDetailRoute(
                onBackClick = { navController.popBackStack() },
                onOrderClick = { orderId ->
                    navController.navigate("${AppDestination.Orders.route}/$orderId")
                }
            )
        }
        composable(AppDestination.Carts.route) { PlaceholderScreen(AppDestination.Carts) }
    }
}

@Composable
private fun PlaceholderScreen(destination: AppDestination) {
    Scaffold { paddingValues ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = stringResource(id = destination.labelRes),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(all = 24.dp)
            )
        }
    }
}
