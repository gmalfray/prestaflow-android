package com.rebuildit.prestaflow.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.navigation.AppDestination
import com.rebuildit.prestaflow.navigation.PrestaFlowNavGraph
import com.rebuildit.prestaflow.ui.auth.AuthRoute
import com.rebuildit.prestaflow.ui.onboarding.ModuleInstallGuideRoute
import com.rebuildit.prestaflow.ui.onboarding.OnboardingRoute
import com.rebuildit.prestaflow.ui.root.RootViewModel
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import com.rebuildit.prestaflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Destinations affichées dans la barre de navigation inférieure / rail. */
private val navBarDestinations =
    listOf(
        AppDestination.Dashboard,
        AppDestination.Orders,
        AppDestination.Products,
        AppDestination.Clients,
        AppDestination.Carts,
    )

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Commande à afficher en profondeur, issue d'une notification push.
    // Partagé entre le cycle de vie Android (onCreate/onNewIntent) et la composition Compose :
    // toute écriture depuis le thread principal déclenche une recomposition.
    private val pendingOrderId = mutableStateOf<Long?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification background (gérée par le système) : le tap ajoute les extras FCM à l'Intent.
        // Notification foreground (notre ContentIntent URI) : le NavHost traite le deep link automatiquement
        // au démarrage à froid, donc on n'en a pas besoin ici (évite une double navigation).
        if (savedInstanceState == null && intent?.data?.scheme != "prestaflow") {
            pendingOrderId.value = intent?.extras?.getString("order_id")?.toLongOrNull()
        }
        setContent {
            val windowSizeClass = calculateWindowSizeClass(activity = this@MainActivity)

            // Request notification permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionState =
                    rememberPermissionState(
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    )
                LaunchedEffect(Unit) {
                    if (!permissionState.status.isGranted) {
                        permissionState.launchPermissionRequest()
                    }
                }
            }

            PrestaFlowApp(
                windowSizeClass = windowSizeClass,
                pendingOrderId = pendingOrderId.value,
                onOrderIdConsumed = { pendingOrderId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Deux chemins selon l'origine du tap :
        //  • URI prestaflow://orders/{id} → notre ContentIntent (notification foreground).
        //    Le NavHost ne retraite pas Activity.intent après le démarrage à chaud ;
        //    on extrait l'orderId manuellement pour déclencher la navigation Compose.
        //  • Extras FCM → notification background gérée par le système (ordre_id en extra String).
        pendingOrderId.value = if (intent.data?.scheme == "prestaflow") {
            intent.data?.lastPathSegment?.toLongOrNull()
        } else {
            intent.extras?.getString("order_id")?.toLongOrNull()
        }
    }
}

@Composable
private fun PrestaFlowApp(
    windowSizeClass: WindowSizeClass,
    pendingOrderId: Long? = null,
    onOrderIdConsumed: () -> Unit = {},
) {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()

    PrestaFlowTheme(settings = themeState.settings) {
        val rootViewModel: RootViewModel = hiltViewModel()
        val authState by rootViewModel.authState.collectAsStateWithLifecycle()

        when (authState) {
            AuthState.Loading -> LoadingScreen()
            is AuthState.Authenticated ->
                AuthenticatedShell(
                    windowSizeClass = windowSizeClass,
                    onLogout = rootViewModel::logout,
                    pendingOrderId = pendingOrderId,
                    onOrderIdConsumed = onOrderIdConsumed,
                )
            AuthState.Unauthenticated -> UnauthenticatedFlow()
        }
    }
}

/**
 * Flux de navigation pour les utilisateurs non authentifiés.
 *
 * Gère l'enchaînement :
 *   OnboardingScreen → [AuthRoute | ModuleInstallGuideScreen]
 *
 * L'état de navigation est conservé via [rememberSaveable] pour survivre aux changements
 * de configuration, mais reste local à ce composable (pas besoin de NavController complet
 * pour 3 destinations).
 */
@Composable
private fun UnauthenticatedFlow() {
    // Destinations locales au flux non-authentifié
    var destination by rememberSaveable { mutableStateOf(UnauthDest.ONBOARDING) }

    when (destination) {
        UnauthDest.ONBOARDING ->
            OnboardingRoute(
                onHasModule = { destination = UnauthDest.CONNECT },
                onNoModule = { destination = UnauthDest.INSTALL_GUIDE },
            )
        UnauthDest.CONNECT ->
            AuthRoute(
                onShowInstallGuide = { destination = UnauthDest.INSTALL_GUIDE },
            )
        UnauthDest.INSTALL_GUIDE ->
            ModuleInstallGuideRoute(
                onBack = { destination = UnauthDest.ONBOARDING },
                onGoToConnect = { destination = UnauthDest.CONNECT },
            )
    }
}

private enum class UnauthDest { ONBOARDING, CONNECT, INSTALL_GUIDE }

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
// Shell responsive : BottomNavigation en compact, NavigationRail en medium/expanded, two-pane commandes en expanded
@Suppress("LongMethod")
@Composable
private fun AuthenticatedShell(
    windowSizeClass: WindowSizeClass,
    onLogout: () -> Unit,
    pendingOrderId: Long? = null,
    onOrderIdConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val haptics = LocalHapticFeedback.current

    var currentTitle by remember { mutableStateOf(R.string.app_name) }

    // Navigation vers le détail commande depuis une notification push.
    // Déclenché à chaque changement de pendingOrderId (non-null uniquement).
    // popUpTo assure un back stack propre : Dashboard → détail commande (retour arrière → Dashboard).
    LaunchedEffect(pendingOrderId) {
        pendingOrderId?.let { orderId ->
            navController.navigate("${AppDestination.Orders.route}/$orderId") {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
            onOrderIdConsumed()
        }
    }

    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route
        // Tronquer à '?' et '/' pour rester robuste aux routes paramétrées (ex. "orders?period=today")
        val baseRoute = route?.substringBefore('?')?.substringBefore('/')
        currentTitle = AppDestination.values().find { it.route == baseRoute }?.labelRes ?: R.string.app_name
    }

    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val currentRoute = navBackStackEntry?.destination?.route
    val isSettings = currentRoute == AppDestination.Settings.route
    val settingsLabel = stringResource(R.string.destination_settings)
    val backLabel = stringResource(R.string.content_description_back)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = currentTitle)) },
                navigationIcon = {
                    // Réglages est ouvert via l'engrenage (hors barre du bas) : sans flèche
                    // retour, l'utilisateur s'y retrouve coincé. On en ajoute une qui dépile.
                    if (isSettings) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backLabel,
                            )
                        }
                    }
                },
                actions = {
                    // Pas d'engrenage quand on est déjà sur Réglages (re-naviguer = no-op).
                    if (!isSettings) {
                        IconButton(
                            onClick = {
                                navController.navigate(AppDestination.Settings.route) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = settingsLabel,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!useNavigationRail) {
                val navigationBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                val navigationBarItemColors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                NavigationBar(containerColor = navigationBarContainerColor) {
                    navBarDestinations.forEach { destination ->
                        val selected = currentRoute?.substringBefore('?')?.substringBefore('/') == destination.route
                        val label = stringResource(id = destination.labelRes)
                        val onItemClick = {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (destination == AppDestination.Dashboard) {
                                    // Dashboard = destination de départ : on dépile vers elle (popBackStack)
                                    // plutôt que de naviguer en avant. navigate(startDest){ launchSingleTop }
                                    // peut être traité comme no-op quand Dashboard est déjà au sommet
                                    // du back stack après popUpTo, rendant le retour au Dashboard impossible.
                                    navController.popBackStack(
                                        route = AppDestination.Dashboard.route,
                                        inclusive = false,
                                        saveState = true,
                                    )
                                } else {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = onItemClick,
                            label = { Text(text = label) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = label,
                                )
                            },
                            colors = navigationBarItemColors,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (useNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(modifier = Modifier.padding(vertical = 12.dp)) {
                        navBarDestinations.forEach { destination ->
                            val selected = currentRoute?.substringBefore('?')?.substringBefore('/') == destination.route
                            val label = stringResource(id = destination.labelRes)
                            val onItemClick = {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (destination == AppDestination.Dashboard) {
                                        // Dashboard = destination de départ : on dépile vers elle (popBackStack)
                                        // plutôt que de naviguer en avant. navigate(startDest){ launchSingleTop }
                                        // peut être traité comme no-op quand Dashboard est déjà au sommet
                                        // du back stack après popUpTo, rendant le retour au Dashboard impossible.
                                        navController.popBackStack(
                                            route = AppDestination.Dashboard.route,
                                            inclusive = false,
                                            saveState = true,
                                        )
                                    } else {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = onItemClick,
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = label,
                                    )
                                },
                                label = { Text(text = label) },
                            )
                        }
                    }
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PrestaFlowNavGraph(
                            navController = navController,
                            onLogout = onLogout,
                            windowSizeClass = windowSizeClass,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                PrestaFlowNavGraph(
                    navController = navController,
                    onLogout = onLogout,
                    windowSizeClass = windowSizeClass,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Suppress("UnusedPrivateMember") // Composable Preview privée : visible dans l'IDE Android Studio
private fun AuthenticatedShellPreview() {
    val windowSize = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
    PrestaFlowTheme {
        AuthenticatedShell(windowSize, onLogout = {})
    }
}
