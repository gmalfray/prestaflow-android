package com.rebuildit.prestaflow.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
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
import com.rebuildit.prestaflow.navigation.formatBadgeCount
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
        pendingOrderId.value =
            if (intent.data?.scheme == "prestaflow") {
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
        val unreadSavCount by rootViewModel.unreadSavCount.collectAsStateWithLifecycle()

        when (authState) {
            AuthState.Loading -> LoadingScreen()
            is AuthState.Authenticated ->
                AuthenticatedShell(
                    windowSizeClass = windowSizeClass,
                    onLogout = rootViewModel::logout,
                    pendingOrderId = pendingOrderId,
                    onOrderIdConsumed = onOrderIdConsumed,
                    unreadSavCount = unreadSavCount,
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

/**
 * Libellé d'onglet de navigation (bottom bar + rail). Les 5 libellés sont dimensionnés pour tenir
 * sur une ligne à l'échelle de police par défaut ; on plafonne donc le `fontScale` à 1.0 pour ces
 * seuls libellés afin que « Commandes » ne déborde pas (coupure sur 2 lignes ou ellipsis) quand
 * l'utilisateur agrandit la police système. L'accessibilité (grande police) reste active partout
 * ailleurs dans l'app. Ellipsis conservé comme garde-fou ultime sur écrans exceptionnellement étroits.
 */
@Composable
private fun NavBarLabel(text: String) {
    val density = LocalDensity.current
    val cappedDensity = Density(density.density, density.fontScale.coerceAtMost(1f))
    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        Text(text = text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Icône d'onglet de navigation, avec pastille optionnelle (ex. fils SAV non lus sur l'onglet
 * Clients — cf. [unreadSavCount] dans [AuthenticatedShell]). `null`/vide → pas de pastille, jamais
 * un badge "0" affiché (cf. [formatBadgeCount]). [badgeCount] alimente uniquement la description
 * d'accessibilité de la pastille (ignoré si [badgeLabel] est `null`).
 */
@Composable
private fun NavIconWithBadge(
    icon: ImageVector,
    contentDescription: String,
    badgeLabel: String?,
    badgeCount: Int = 0,
) {
    if (badgeLabel == null) {
        Icon(imageVector = icon, contentDescription = contentDescription)
        return
    }
    val badgeDescription = stringResource(R.string.clients_badge_unread_sav_content_description, badgeCount)
    BadgedBox(
        badge = {
            Badge(
                modifier =
                    Modifier.semantics {
                        this.contentDescription = badgeDescription
                    },
            ) {
                Text(badgeLabel)
            }
        },
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
// Shell responsive : BottomNavigation en compact, NavigationRail en medium/expanded, two-pane commandes en expanded
// + masquage du chrome parent (topBar/bottomBar/rail) sur les destinations plein écran (réappro stock)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun AuthenticatedShell(
    windowSizeClass: WindowSizeClass,
    onLogout: () -> Unit,
    pendingOrderId: Long? = null,
    onOrderIdConsumed: () -> Unit = {},
    // Fils SAV non lus : pastille sur l'onglet Clients, compensation à la descente de niveau du
    // SAV dans la nav (cf. étude rebuild-it/docs/app-avis-sav.md § "Contrepartie assumée").
    unreadSavCount: Int = 0,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val haptics = LocalHapticFeedback.current

    var currentTitle by remember { mutableStateOf(R.string.app_name) }

    // Vrai juste après l'ouverture de Commandes depuis une tuile du dashboard (filtre de période).
    // Compose Navigation restaure une entrée de back-stack SAUVEGARDÉE (saveState/restoreState) en
    // se basant sur la DESTINATION ("orders?period={period}"), pas sur les arguments demandés : un
    // clic ultérieur sur l'onglet "Commandes" (route "orders", sans période) peut donc restaurer
    // cette même entrée filtrée au lieu d'en créer une neuve — le filtre de période dashboard reste
    // "collé" et la puce ✕ ne peut jamais s'en débarrasser durablement (elle réapparaît à chaque
    // retour sur l'onglet). Ce flag permet à `navigateToTab` de forcer, une seule fois, une entrée
    // Commandes fraîche (sans période) au prochain clic sur l'onglet — cf. usage ci-dessous.
    //
    // RÉGRESSION v0.42.5 (fixée ici) : la branche qui consomme ce flag faisait
    // `popUpTo(AppDestination.Orders.route)` au lieu de `popUpTo(graph.startDestinationId)` comme la
    // branche standard ci-dessous et comme le pattern officiel Android pour la navigation multi-onglets
    // (cf. developer.android.com/guide/navigation/navigation-multi-back-stack). Cette frontière
    // `popUpTo` différente casse la cohérence du bookkeeping interne saveState/restoreState de
    // Compose Navigation (qui suppose une frontière commune pour tous les changements d'onglet) :
    // un onglet peut alors restaurer l'entrée sauvegardée d'UN AUTRE onglet. D'où la régression :
    // taper "Commandes" après un passage par Clients(filtré) pouvait atterrir sur Clients.
    var ordersEnteredWithPeriod by rememberSaveable { mutableStateOf(false) }

    // Même correctif que ordersEnteredWithPeriod ci-dessus, pour la tuile KPI "Nouveaux clients" du
    // dashboard qui ouvre Clients avec un filtre de période (route "clients?filter=new&created_from=...").
    // Sans ce flag, un clic ultérieur sur l'onglet "Clients" restaure via `restoreState` cette même
    // entrée filtrée (même pattern de route paramétrée) au lieu d'une entrée fraîche : le filtre
    // "Nouveaux clients" reste collé et la puce ✕/re-tap sur la carte KPI ne peut jamais s'en
    // débarrasser durablement (le `LaunchedEffect(filterArg)` de ClientsRoute le réapplique à chaque
    // ré-entrée sur l'onglet, tant que l'argument de navigation "filter" reste "new").
    var clientsEnteredWithPeriod by rememberSaveable { mutableStateOf(false) }

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
    val isNotifCategories = currentRoute == AppDestination.NOTIF_CATEGORIES_ROUTE
    // Destination plein écran (flux focalisé façon scanner) : pas de chrome parent (TopAppBar
    // "Produits" + engrenage, bottom nav / rail) — l'écran gère lui-même son propre header et son
    // retour. Sans ce garde-fou, deux headers s'empilent (celui de l'onglet Produits + celui de
    // l'écran) et la nav du bas grignote la hauteur utile, poussant Valider/le récap hors écran.
    val isFullScreenRoute = currentRoute == AppDestination.STOCK_REPLENISH_ROUTE
    // Réglages (et ses sous-écrans) est ouvert via l'engrenage, PAS un onglet de la barre du bas :
    // c'est une destination de premier niveau. Sans ce garde-fou, la bottom bar / le rail restent
    // affichés en dessous avec l'onglet d'origine (ex. Commandes) toujours visible, donnant
    // l'impression que Réglages est imbriqué dans cet onglet au lieu d'être un écran indépendant.
    val hideTabChrome = isFullScreenRoute || isSettings || isNotifCategories
    val settingsLabel = stringResource(R.string.destination_settings)
    val backLabel = stringResource(R.string.content_description_back)

    // Navigation partagée par la barre du bas (compact) ET le rail (medium/expanded) : évite de
    // dupliquer deux fois la même logique (et le même correctif) dans les deux blocs `onItemClick`.
    fun navigateToTab(destination: AppDestination) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when {
            destination == AppDestination.Dashboard -> {
                // Dashboard = destination de départ : on dépile vers elle (popBackStack)
                // plutôt que de naviguer en avant. navigate(startDest){ launchSingleTop }
                // peut être traité comme no-op quand Dashboard est déjà au sommet
                // du back stack après popUpTo, rendant le retour au Dashboard impossible.
                navController.popBackStack(
                    route = AppDestination.Dashboard.route,
                    inclusive = false,
                    saveState = true,
                )
            }
            destination == AppDestination.Orders && ordersEnteredWithPeriod -> {
                // Cf. commentaire sur ordersEnteredWithPeriod : on force une entrée Commandes
                // fraîche (sans restoreState) au lieu de risquer de restaurer l'entrée filtrée par
                // période ouverte depuis le dashboard. Une fois consommé, le flag repasse à false :
                // les visites suivantes de l'onglet redeviennent des restaurations normales (tri,
                // recherche, filtre de statut préservés).
                // IMPORTANT : même frontière popUpTo que la branche standard ci-dessous
                // (graph.startDestinationId, pas la route propre de l'onglet) — cf. commentaire sur
                // ordersEnteredWithPeriod : une frontière différente d'un onglet à l'autre corrompt le
                // bookkeeping saveState/restoreState partagé et peut faire restaurer l'onglet d'à côté.
                ordersEnteredWithPeriod = false
                navController.navigate(AppDestination.Orders.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                }
            }
            destination == AppDestination.Clients && clientsEnteredWithPeriod -> {
                // Cf. commentaire sur clientsEnteredWithPeriod : force une entrée Clients fraîche
                // (sans restoreState) au lieu de risquer de restaurer l'entrée filtrée par période
                // ouverte depuis la tuile "Nouveaux clients" du dashboard. Même frontière popUpTo que
                // la branche standard (cf. remarque IMPORTANT ci-dessus, identique pour Clients).
                clientsEnteredWithPeriod = false
                navController.navigate(AppDestination.Clients.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                }
            }
            else -> {
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

    Scaffold(
        topBar = {
            if (!isFullScreenRoute) {
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
            }
        },
        bottomBar = {
            if (!useNavigationRail && !hideTabChrome) {
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
                                navigateToTab(destination)
                            }
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = onItemClick,
                            label = { NavBarLabel(label) },
                            icon = {
                                NavIconWithBadge(
                                    icon = destination.icon,
                                    contentDescription = label,
                                    badgeLabel =
                                        if (destination == AppDestination.Clients) {
                                            formatBadgeCount(unreadSavCount)
                                        } else {
                                            null
                                        },
                                    badgeCount = unreadSavCount,
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
            if (useNavigationRail && !hideTabChrome) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // verticalScroll : en paysage / faible hauteur, les 5 onglets ne tiennent pas dans
                    // le rail — sans défilement le dernier (« Paniers ») est tronqué. Le scroll les rend
                    // tous atteignables quelle que soit la hauteur écran.
                    NavigationRail(
                        modifier =
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp),
                    ) {
                        navBarDestinations.forEach { destination ->
                            val selected = currentRoute?.substringBefore('?')?.substringBefore('/') == destination.route
                            val label = stringResource(id = destination.labelRes)
                            val onItemClick = {
                                if (!selected) {
                                    navigateToTab(destination)
                                }
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = onItemClick,
                                icon = {
                                    NavIconWithBadge(
                                        icon = destination.icon,
                                        contentDescription = label,
                                        badgeLabel =
                                            if (destination == AppDestination.Clients) {
                                                formatBadgeCount(unreadSavCount)
                                            } else {
                                                null
                                            },
                                        badgeCount = unreadSavCount,
                                    )
                                },
                                label = { NavBarLabel(label) },
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
                    onOrdersOpenedWithPeriod = { ordersEnteredWithPeriod = true },
                    onClientsOpenedWithPeriod = { clientsEnteredWithPeriod = true },
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
