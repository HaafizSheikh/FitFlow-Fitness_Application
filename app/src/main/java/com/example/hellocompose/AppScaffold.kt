package com.example.hellocompose

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

data class DrawerItem(
    val label: String,
    val route: NavRoutes
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    navController: NavController,
    currentRoute: NavRoutes,
    snackbarHost: @Composable (() -> Unit)? = null,   // ✅ Added
    onAccountClick: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {

    val drawerItems = listOf(
        DrawerItem("Dashboard", NavRoutes.DASHBOARD),
        DrawerItem("Workouts",  NavRoutes.WORKOUTS),
        DrawerItem("Meals",     NavRoutes.MEALS),
        DrawerItem("Progress",  NavRoutes.PROGRESS),
        DrawerItem("Community", NavRoutes.COMMUNITY),
        DrawerItem("Settings / Account", NavRoutes.ACCOUNT),
    )

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "FitFlow",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route.name) {
                                popUpTo(NavRoutes.DASHBOARD.name)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = {
                snackbarHost?.invoke() ?: SnackbarHost(remember { SnackbarHostState() })
            },
            topBar = {
                TopAppBar(
                    title = { Text("FitFlow") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Menu") // simple icon as nav button
                        }
                    },
                    actions = {
                        IconButton(onClick = onAccountClick) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                        }
                    }
                )
            }
        ) { innerPadding ->
            content(Modifier.padding(innerPadding))
        }
    }
}
