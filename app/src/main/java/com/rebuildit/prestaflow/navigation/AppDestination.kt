package com.rebuildit.prestaflow.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.rebuildit.prestaflow.R

enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard("dashboard", R.string.destination_dashboard, Icons.Outlined.Assessment),
    Orders("orders", R.string.destination_orders, Icons.AutoMirrored.Outlined.ListAlt),
    Products("products", R.string.destination_products, Icons.Outlined.Inventory2),
    Clients("clients", R.string.destination_clients, Icons.Outlined.Group),
    Carts("carts", R.string.destination_carts, Icons.Outlined.ShoppingCart),
    Settings("settings", R.string.destination_settings, Icons.Outlined.Settings),
    ;

    companion object {
        /** Route vers l'écran préférences de catégories de notifications (destination secondaire). */
        const val NOTIF_CATEGORIES_ROUTE = "notif-categories"

        /**
         * Route vers l'écran « Ajout / réappro stock » (scan en série, destination secondaire
         * accessible depuis le FAB de [Products] — remplace l'ancien flux scan→fiche stock pour un
         * produit connu, cf. [com.rebuildit.prestaflow.ui.products.StockReplenishScreen]).
         */
        const val STOCK_REPLENISH_ROUTE = "products/replenish"

        /**
         * Route vers le détail d'un fil SAV — destination secondaire accessible depuis la section
         * SAV de l'onglet [Clients] (cf. étude `rebuild-it/docs/app-avis-sav.md` § « Navigation »).
         * Pas une entrée d'[AppDestination] : le SAV n'a pas sa propre place dans la barre du bas.
         */
        const val SAV_THREAD_ROUTE = "sav"
    }
}
