package com.rebuildit.prestaflow.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import com.rebuildit.prestaflow.ui.notifications.NotificationCategoriesRoute
import com.rebuildit.prestaflow.ui.orders.OrderDetailRoute
import com.rebuildit.prestaflow.ui.orders.OrdersRoute
import com.rebuildit.prestaflow.ui.orders.OrdersTwoPaneRoute
import com.rebuildit.prestaflow.ui.products.ProductDetailRoute
import com.rebuildit.prestaflow.ui.products.ProductEditRoute
import com.rebuildit.prestaflow.ui.products.ProductsRoute
import com.rebuildit.prestaflow.ui.products.StockReplenishRoute
import com.rebuildit.prestaflow.ui.settings.SettingsRoute

// NavGraph centralise toutes les routes de l'app (longueur inhérente) + ses deux callbacks
// "OpenedWithPeriod" (Commandes et Clients, cf. commentaires ci-dessous) qui poussent le nombre de
// paramètres au-delà du seuil detekt par défaut.
@Suppress("LongMethod", "LongParameterList")
@Composable
fun PrestaFlowNavGraph(
    onLogout: () -> Unit,
    windowSizeClass: WindowSizeClass,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    // Notifie le shell (AuthenticatedShell) qu'une entrée Commandes filtrée par période vient d'être
    // poussée, afin qu'un futur clic sur l'onglet "Commandes" de la barre du bas force une entrée
    // fraîche (sans période) au lieu de restaurer celle-ci via `restoreState` — cf. commentaire détaillé
    // sur le flag ordersEnteredWithPeriod dans MainActivity.kt.
    onOrdersOpenedWithPeriod: () -> Unit = {},
    // Même rôle que onOrdersOpenedWithPeriod, pour la tuile KPI "Nouveaux clients" du dashboard qui
    // ouvre Clients avec un filtre de période — cf. commentaire détaillé sur le flag
    // clientsEnteredWithPeriod dans MainActivity.kt.
    onClientsOpenedWithPeriod: () -> Unit = {},
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    NavHost(
        navController = navController,
        startDestination = AppDestination.Dashboard.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Dashboard.route) {
            DashboardRoute(
                onAddShop = {
                    navController.navigate(AppDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onOrdersClick = { period ->
                    onOrdersOpenedWithPeriod()
                    // Purge d'abord toute entrée Commandes existante (active ou sauvegardée via
                    // saveState) avant de pousser l'entrée filtrée : même pattern que le KPI
                    // "Nouveaux clients" ci-dessous, pour garantir un ViewModel frais avec la bonne
                    // période même si l'utilisateur avait déjà visité l'onglet Commandes ou une autre
                    // tuile du dashboard juste avant.
                    navController.navigate("${AppDestination.Orders.route}?period=${period.queryValue}") {
                        popUpTo(AppDestination.Orders.route) { inclusive = true }
                    }
                },
                onClientsClick = { createdFrom ->
                    onClientsOpenedWithPeriod()
                    // Le KPI "Nouveaux clients" navigue toujours vers une entrée Clients fraîche
                    // avec filter=new. On purge d'abord toute entrée Clients présente dans le
                    // back-stack (active ou sauvegardée via saveState) pour garantir qu'un nouveau
                    // ViewModel est créé — le filtre est ainsi toujours appliqué même si
                    // l'utilisateur avait déjà visité l'onglet Clients.
                    // created_from = début de la période du dashboard → la liste correspond au KPI.
                    val createdFromArg = createdFrom?.let { "&created_from=$it" } ?: ""
                    navController.navigate("${AppDestination.Clients.route}?filter=new$createdFromArg") {
                        popUpTo(AppDestination.Clients.route) { inclusive = true }
                    }
                },
            )
        }
        // Route commandes avec filtre de période optionnel (transmis depuis le dashboard).
        // L'argument "period" est null lors d'un accès direct via la barre de navigation.
        composable(
            route = "${AppDestination.Orders.route}?period={period}",
            arguments =
                listOf(
                    navArgument("period") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) {
            if (isExpanded) {
                // Tablette : layout deux colonnes liste + détail
                OrdersTwoPaneRoute()
            } else {
                // Téléphone / medium : navigation classique single-pane
                OrdersRoute(
                    onOrderClick = { orderId ->
                        navController.navigate("${AppDestination.Orders.route}/$orderId")
                    },
                    onAddShop = {
                        navController.navigate(AppDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        composable(
            route = "${AppDestination.Orders.route}/{orderId}",
            arguments =
                listOf(
                    navArgument("orderId") { type = NavType.LongType },
                ),
            deepLinks =
                listOf(
                    navDeepLink { uriPattern = "prestaflow://orders/{orderId}" },
                ),
        ) {
            OrderDetailRoute(
                onBackClick = { navController.popBackStack() },
                onProductClick = { productId ->
                    navController.navigate("${AppDestination.Products.route}/$productId")
                },
                onClientClick = { clientId ->
                    navController.navigate("${AppDestination.Clients.route}/$clientId")
                },
            )
        }
        composable(AppDestination.Products.route) {
            ProductsRoute(
                onProductClick = { productId ->
                    navController.navigate("${AppDestination.Products.route}/$productId")
                },
                onAddShop = {
                    navController.navigate(AppDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onScanProduct = {
                    navController.navigate(AppDestination.STOCK_REPLENISH_ROUTE)
                },
            )
        }
        composable(AppDestination.STOCK_REPLENISH_ROUTE) {
            StockReplenishRoute(
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = "${AppDestination.Products.route}/{productId}",
            arguments =
                listOf(
                    navArgument("productId") { type = NavType.LongType },
                ),
            deepLinks =
                listOf(
                    navDeepLink { uriPattern = "prestaflow://products/{productId}" },
                ),
        ) {
            ProductDetailRoute(
                onBackClick = { navController.popBackStack() },
                onEditClick = { productId ->
                    navController.navigate("${AppDestination.Products.route}/$productId/edit")
                },
            )
        }
        composable(
            route = "${AppDestination.Products.route}/{productId}/edit",
            arguments =
                listOf(
                    navArgument("productId") { type = NavType.LongType },
                ),
        ) {
            ProductEditRoute(
                onBackClick = { navController.popBackStack() },
            )
        }
        // Route clients avec filtre optionnel (arg "filter").
        // "new" est transmis depuis la carte KPI "Nouveaux clients" du dashboard.
        // Accès direct via la barre de navigation → filter=null → mode TOP_CLIENTS par défaut.
        composable(
            route = "${AppDestination.Clients.route}?filter={filter}&created_from={created_from}",
            arguments =
                listOf(
                    navArgument("filter") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("created_from") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) {
            ClientsRoute(
                onClientClick = { clientId ->
                    navController.navigate("${AppDestination.Clients.route}/$clientId")
                },
                onAddShop = {
                    navController.navigate(AppDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = "${AppDestination.Clients.route}/{clientId}",
            arguments =
                listOf(
                    navArgument("clientId") { type = NavType.LongType },
                ),
        ) {
            ClientDetailRoute(
                onBackClick = { navController.popBackStack() },
                onOrderClick = { orderId ->
                    navController.navigate("${AppDestination.Orders.route}/$orderId")
                },
            )
        }
        composable(AppDestination.Carts.route) {
            CartsRoute(
                onCartClick = { cartId ->
                    navController.navigate("${AppDestination.Carts.route}/$cartId")
                },
                onAddShop = {
                    navController.navigate(AppDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = "${AppDestination.Carts.route}/{cartId}",
            arguments =
                listOf(
                    navArgument("cartId") { type = NavType.IntType },
                ),
        ) {
            CartDetailRoute(
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsRoute(
                onLogoutClick = onLogout,
                onNotifCategoriesClick = {
                    navController.navigate(AppDestination.NOTIF_CATEGORIES_ROUTE)
                },
            )
        }
        composable(AppDestination.NOTIF_CATEGORIES_ROUTE) {
            NotificationCategoriesRoute(
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}
