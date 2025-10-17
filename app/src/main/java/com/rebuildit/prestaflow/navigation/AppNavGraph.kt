package com.rebuildit.prestaflow.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
        composable(AppDestination.Dashboard.route) {
            PlaceholderScreen(destination = AppDestination.Dashboard)
        }
        composable(AppDestination.Orders.route) {
            PlaceholderScreen(destination = AppDestination.Orders)
        }
        composable(AppDestination.Products.route) {
            PlaceholderScreen(destination = AppDestination.Products)
        }
        composable(AppDestination.Clients.route) {
            PlaceholderScreen(destination = AppDestination.Clients)
        }
        composable(AppDestination.Carts.route) {
            PlaceholderScreen(destination = AppDestination.Carts)
        }
    }
}

enum class AppDestination(val route: String) {
    Dashboard("dashboard"),
    Orders("orders"),
    Products("products"),
    Clients("clients"),
    Carts("carts")
}

@StringRes
fun AppDestination.labelRes(): Int = when (this) {
    AppDestination.Dashboard -> com.rebuildit.prestaflow.R.string.destination_dashboard
    AppDestination.Orders -> com.rebuildit.prestaflow.R.string.destination_orders
    AppDestination.Products -> com.rebuildit.prestaflow.R.string.destination_products
    AppDestination.Clients -> com.rebuildit.prestaflow.R.string.destination_clients
    AppDestination.Carts -> com.rebuildit.prestaflow.R.string.destination_carts
}

val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.Dashboard -> Icons.Outlined.Assessment
        AppDestination.Orders -> Icons.Outlined.ListAlt
        AppDestination.Products -> Icons.Outlined.Inventory2
        AppDestination.Clients -> Icons.Outlined.Group
        AppDestination.Carts -> Icons.Outlined.ShoppingCart
    }

@Composable
private fun PlaceholderScreen(destination: AppDestination) {
    Scaffold { paddingValues ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = stringResource(id = destination.labelRes()),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(all = 24.dp)
            )
        }
    }
}
