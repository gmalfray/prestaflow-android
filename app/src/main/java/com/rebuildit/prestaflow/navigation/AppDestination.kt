package com.rebuildit.prestaflow.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.rebuildit.prestaflow.R

enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Dashboard("dashboard", R.string.destination_dashboard, Icons.Outlined.Assessment),
    Orders("orders", R.string.destination_orders, Icons.Outlined.ListAlt),
    Products("products", R.string.destination_products, Icons.Outlined.Inventory2),
    Clients("clients", R.string.destination_clients, Icons.Outlined.Group),
    Carts("carts", R.string.destination_carts, Icons.Outlined.ShoppingCart)
}
