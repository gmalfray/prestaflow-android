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
import com.rebuildit.prestaflow.ui.products.ProductsRoute
import com.rebuildit.prestaflow.ui.settings.SettingsRoute

@Suppress("LongMethod") // NavGraph centralise toutes les routes de l'app, longueur inhérente
@Composable
fun PrestaFlowNavGraph(
    onLogout: () -> Unit,
    windowSizeClass: WindowSizeClass,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
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
                    navController.navigate("${AppDestination.Orders.route}?period=${period.queryValue}")
                },
                onClientsClick = {
                    // Le KPI "Nouveaux clients" navigue toujours vers une entrée Clients fraîche
                    // avec filter=new (mode NEW_THIS_MONTH). On purge d'abord toute entrée Clients
                    // présente dans le back-stack (active ou sauvegardée via saveState) pour
                    // garantir qu'un nouveau ViewModel est créé — le filtre est ainsi toujours
                    // appliqué même si l'utilisateur avait déjà visité l'onglet Clients.
                    navController.navigate("${AppDestination.Clients.route}?filter=new") {
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
            )
        }
        composable(
            route = "${AppDestination.Products.route}/{productId}",
            arguments =
                listOf(
                    navArgument("productId") { type = NavType.LongType },
                ),
        ) {
            ProductDetailRoute(
                onBackClick = { navController.popBackStack() },
            )
        }
        // Route clients avec filtre optionnel (arg "filter").
        // "new" est transmis depuis la carte KPI "Nouveaux clients" du dashboard.
        // Accès direct via la barre de navigation → filter=null → mode TOP_CLIENTS par défaut.
        composable(
            route = "${AppDestination.Clients.route}?filter={filter}",
            arguments =
                listOf(
                    navArgument("filter") {
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
