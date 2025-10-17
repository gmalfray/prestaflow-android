package com.rebuildit.prestaflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import dagger.hilt.android.AndroidEntryPoint
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.navigation.AppDestination
import com.rebuildit.prestaflow.navigation.PrestaFlowNavGraph
import com.rebuildit.prestaflow.navigation.icon
import com.rebuildit.prestaflow.navigation.labelRes
import androidx.navigation.compose.rememberNavController

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrestaFlowApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrestaFlowApp() {
    PrestaFlowTheme {
        val navController = rememberNavController()
        val destinations = remember { AppDestination.values().toList() }
        val navBackStackEntry by navController.currentBackStackEntryAsState()

        var currentDestinationLabel by remember { mutableStateOf(R.string.app_name) }

        LaunchedEffect(navBackStackEntry) {
            val route = navBackStackEntry?.destination?.route
            currentDestinationLabel = when (route) {
                AppDestination.Dashboard.route -> R.string.destination_dashboard
                AppDestination.Orders.route -> R.string.destination_orders
                AppDestination.Products.route -> R.string.destination_products
                AppDestination.Clients.route -> R.string.destination_clients
                AppDestination.Carts.route -> R.string.destination_carts
                else -> R.string.app_name
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(text = stringResource(id = currentDestinationLabel)) }
                )
            },
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = navBackStackEntry?.destination?.route == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            label = { Text(text = stringResource(id = destination.labelRes())) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(id = destination.labelRes())
                                )
                            }
                        )
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
private fun PrestaFlowPreview() {
    PrestaFlowApp()
}
