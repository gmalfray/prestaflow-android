package com.rebuildit.prestaflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.rebuildit.prestaflow.ui.root.RootViewModel
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import com.rebuildit.prestaflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Destinations affichées dans la barre de navigation inférieure / rail. */
private val navBarDestinations = listOf(
    AppDestination.Dashboard,
    AppDestination.Orders,
    AppDestination.Products,
    AppDestination.Clients,
    AppDestination.Carts
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(activity = this@MainActivity)

            // Request notification permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionState = rememberPermissionState(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
                LaunchedEffect(Unit) {
                    if (!permissionState.status.isGranted) {
                        permissionState.launchPermissionRequest()
                    }
                }
            }

            PrestaFlowApp(windowSizeClass)
        }
    }
}

@Composable
private fun PrestaFlowApp(windowSizeClass: WindowSizeClass) {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()

    PrestaFlowTheme(settings = themeState.settings) {
        val rootViewModel: RootViewModel = hiltViewModel()
        val authState by rootViewModel.authState.collectAsStateWithLifecycle()

        when (authState) {
            AuthState.Loading -> LoadingScreen()
            is AuthState.Authenticated -> AuthenticatedShell(
                windowSizeClass = windowSizeClass,
                onLogout = rootViewModel::logout
            )
            AuthState.Unauthenticated -> AuthRoute()
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun AuthenticatedShell(
    windowSizeClass: WindowSizeClass,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val haptics = LocalHapticFeedback.current

    var currentTitle by remember { mutableStateOf(R.string.app_name) }

    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route
        currentTitle = AppDestination.values().find { it.route == route }?.labelRes ?: R.string.app_name
    }

    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val currentRoute = navBackStackEntry?.destination?.route
    val settingsLabel = stringResource(R.string.destination_settings)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = currentTitle)) },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(AppDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = settingsLabel
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!useNavigationRail) {
                val navigationBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                val navigationBarItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationBar(containerColor = navigationBarContainerColor) {
                    navBarDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        val label = stringResource(id = destination.labelRes)
                        val onItemClick = {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
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
                                    contentDescription = label
                                )
                            },
                            colors = navigationBarItemColors
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (useNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(modifier = Modifier.padding(vertical = 12.dp)) {
                        navBarDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            val label = stringResource(id = destination.labelRes)
                            val onItemClick = {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = onItemClick,
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(text = label) }
                            )
                        }
                    }
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PrestaFlowNavGraph(
                            navController = navController,
                            onLogout = onLogout,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                PrestaFlowNavGraph(
                    navController = navController,
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize()
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
