package com.memoria.mobile.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.memoria.mobile.ui.auth.LoginScreen
import com.memoria.mobile.ui.auth.RegisterScreen
import com.memoria.mobile.ui.history.HistoryScreen
import com.memoria.mobile.ui.meds.MedicationEditScreen
import com.memoria.mobile.ui.meds.MedicationsScreen
import com.memoria.mobile.ui.settings.SettingsScreen
import com.memoria.mobile.ui.whatsapp.WhatsAppScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.MEDS, "Remédios", Icons.AutoMirrored.Filled.List),
    Tab(Routes.HISTORY, "Histórico", Icons.Filled.History),
    Tab(Routes.WHATSAPP, "WhatsApp", Icons.Filled.Chat),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings),
)

/**
 * Auth state is hoisted here (not encoded as navigation destinations) so that
 * logout disposes the ENTIRE main graph — its back stack, saved tab states, and
 * every ViewModel scoped to it — leaving no data behind for a next account on
 * the same device. Login likewise disposes the auth graph.
 */
@Composable
fun MemoriaNav(startLoggedIn: Boolean) {
    var loggedIn by remember { mutableStateOf(startLoggedIn) }
    if (loggedIn) {
        MainFlow(onLogout = { loggedIn = false })
    } else {
        AuthFlow(onAuthenticated = { loggedIn = true })
    }
}

@Composable
private fun AuthFlow(onAuthenticated: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = onAuthenticated,
                onRegister = { nav.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = onAuthenticated,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainFlow(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = tabs.any { currentRoute == it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(nav, tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.MEDS) {
            composable(Routes.MEDS) {
                MedicationsScreen(
                    contentPadding = padding,
                    onAdd = { nav.navigate(Routes.medEdit(null)) },
                    onEdit = { id -> nav.navigate(Routes.medEdit(id)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(contentPadding = padding)
            }
            composable(Routes.WHATSAPP) {
                WhatsAppScreen(contentPadding = padding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    contentPadding = padding,
                    onLoggedOut = onLogout,
                )
            }
            composable(
                route = "${Routes.MED_EDIT}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val raw = entry.arguments?.getString("id")
                val id = if (raw == "new") null else raw
                MedicationEditScreen(
                    medicationId = id,
                    onDone = { nav.popBackStack() },
                    onCancel = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun navigateToTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
