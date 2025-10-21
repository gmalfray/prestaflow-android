package com.rebuildit.prestaflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.navigation.AppDestination
import com.rebuildit.prestaflow.navigation.PrestaFlowNavGraph
import com.rebuildit.prestaflow.ui.auth.AuthRoute
import com.rebuildit.prestaflow.ui.root.RootViewModel
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import com.rebuildit.prestaflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(activity = this@MainActivity)
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
            is AuthState.Authenticated -> AuthenticatedShell(windowSizeClass)
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
private fun AuthenticatedShell(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val destinations = remember { AppDestination.values().toList() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val haptics = LocalHapticFeedback.current

    var currentTitle by remember { mutableStateOf(R.string.app_name) }

    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route
        currentTitle = destinations.find { it.route == route }?.labelRes ?: R.string.app_name
    }

    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(text = stringResource(id = currentTitle)) })
        },
        bottomBar = {
            if (!useNavigationRail) {
                NavigationBar {
                    destinations.forEach { destination ->
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
                            }
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
                        destinations.forEach { destination ->
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                PrestaFlowNavGraph(
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun AuthenticatedShellPreview() {
    val windowSize = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
    PrestaFlowTheme { AuthenticatedShell(windowSize) }
}
