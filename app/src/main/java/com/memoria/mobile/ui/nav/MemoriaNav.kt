package com.memoria.mobile.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memoria.mobile.ui.admin.AdminScreen
import com.memoria.mobile.ui.auth.ForgotPasswordScreen
import com.memoria.mobile.ui.auth.LoginScreen
import com.memoria.mobile.ui.auth.RegisterScreen
import com.memoria.mobile.ui.calendar.CalendarScreen
import com.memoria.mobile.reminders.MemoriaNotifications
import com.memoria.mobile.ui.common.reminderScheduler
import com.memoria.mobile.ui.common.repository
import com.memoria.mobile.ui.doctors.MyDoctorsScreen
import com.memoria.mobile.ui.health.HealthScreen
import com.memoria.mobile.ui.help.HelpScreen
import com.memoria.mobile.ui.history.HistoryScreen
import com.memoria.mobile.ui.legal.PrivacyPolicyScreen
import com.memoria.mobile.ui.legal.PrivacyScreen
import com.memoria.mobile.ui.home.DashboardScreen
import com.memoria.mobile.ui.meds.MedicationDetailsScreen
import com.memoria.mobile.ui.meds.MedicationEditScreen
import com.memoria.mobile.ui.meds.MedicationsScreen
import com.memoria.mobile.ui.more.MoreScreen
import com.memoria.mobile.ui.optimization.OptimizationGuideScreen
import com.memoria.mobile.ui.plans.PlansScreen
import com.memoria.mobile.ui.prescriptions.PrescriptionsScreen
import com.memoria.mobile.ui.profile.ProfileScreen
import com.memoria.mobile.ui.replenishment.ReplenishmentScreen
import com.memoria.mobile.ui.reports.ReportsScreen
import com.memoria.mobile.ui.settings.SettingsScreen
import com.memoria.mobile.ui.whatsapp.WhatsAppScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** The web app's bottom bar: Início, Medicamentos, Saúde, Histórico, Mais. */
private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Filled.Home),
    Tab(Routes.MEDS, "Remédios", Icons.AutoMirrored.Filled.List),
    Tab(Routes.HEALTH, "Saúde", Icons.Filled.Favorite),
    Tab(Routes.HISTORY, "Histórico", Icons.Filled.History),
    Tab(Routes.MORE, "Mais", Icons.Filled.MoreHoriz),
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
                onForgotPassword = { nav.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onBack = { nav.popBackStack() },
                onFinished = { nav.popBackStack() },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = onAuthenticated,
                onBack = { nav.popBackStack() },
                onOpenPolicy = { nav.navigate(Routes.PRIVACY_POLICY) },
            )
        }
        // The policy lives in the auth graph as well: a consent box the user
        // cannot read before ticking is not informed consent.
        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun MainFlow(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = tabs.any { currentRoute == it.route }
    val context = LocalContext.current

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denied is survivable: the app still works, it just cannot remind. */ }

    // Asked here rather than at launch because this composable exists only once
    // the user is in — a permission dialog over the login screen reads as noise
    // and gets dismissed. Arming the alarms rides along, so both a returning
    // session and a fresh login end up with reminders scheduled.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MemoriaNotifications.canPost(context)
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { context.reminderScheduler().reschedule() }
    }

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
        val back = { nav.popBackStack(); Unit }

        NavHost(navController = nav, startDestination = Routes.HOME) {
            // ---- Bottom bar ----
            composable(Routes.HOME) {
                DashboardScreen(
                    contentPadding = padding,
                    onAdd = { nav.navigate(Routes.medEdit(null)) },
                    onOpenMedication = { id -> nav.navigate(Routes.medDetails(id)) },
                    onOpenPlans = { nav.navigate(Routes.PLANS) },
                )
            }
            composable(Routes.MEDS) {
                MedicationsScreen(
                    contentPadding = padding,
                    onAdd = { nav.navigate(Routes.medEdit(null)) },
                    onEdit = { id -> nav.navigate(Routes.medEdit(id)) },
                    onOpenDetails = { id -> nav.navigate(Routes.medDetails(id)) },
                )
            }
            composable(Routes.HEALTH) {
                HealthScreen(contentPadding = padding)
            }
            composable(Routes.HISTORY) {
                HistoryScreen(contentPadding = padding)
            }
            composable(Routes.MORE) {
                MoreScreen(contentPadding = padding, onNavigate = { nav.navigate(it) })
            }

            // ---- Secondary ----
            composable(
                route = "${Routes.MED_EDIT}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val raw = entry.arguments?.getString("id")
                val id = if (raw == "new") null else raw
                MedicationEditScreen(
                    medicationId = id,
                    onDone = back,
                    onCancel = back,
                )
            }
            composable(
                route = "${Routes.MED_DETAILS}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                MedicationDetailsScreen(
                    medicationId = id,
                    onBack = back,
                    onEdit = { medId -> nav.navigate(Routes.medEdit(medId)) },
                )
            }
            composable(Routes.CALENDAR) { CalendarScreen(onBack = back) }
            composable(Routes.REPORTS) {
                ReportsScreen(onBack = back, onOpenPlans = { nav.navigate(Routes.PLANS) })
            }
            composable(Routes.REPLENISHMENT) { ReplenishmentScreen(onBack = back) }
            composable(Routes.DOCTORS) { MyDoctorsScreen(onBack = back) }
            composable(Routes.PRESCRIPTIONS) {
                PrescriptionsScreen(onBack = back, onOpenPlans = { nav.navigate(Routes.PLANS) })
            }
            composable(Routes.PROFILE) { ProfileScreen(onBack = back) }
            composable(Routes.PLANS) { PlansScreen(onBack = back) }
            composable(Routes.HELP) { HelpScreen(onBack = back) }
            composable(Routes.PRIVACY) {
                PrivacyScreen(
                    onBack = back,
                    onOpenPolicy = { nav.navigate(Routes.PRIVACY_POLICY) },
                    // Erasing the account ends the session, so the whole main
                    // graph is disposed exactly as it is on logout.
                    onAccountDeleted = onLogout,
                )
            }
            composable(Routes.PRIVACY_POLICY) { PrivacyPolicyScreen(onBack = back) }
            composable(Routes.OPTIMIZATION) { OptimizationGuideScreen(onBack = back) }
            composable(Routes.ADMIN) { AdminScreen(onBack = back) }
            composable(Routes.WHATSAPP) {
                WhatsAppScreen(contentPadding = padding, onBack = back)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    contentPadding = padding,
                    onLoggedOut = onLogout,
                    onBack = back,
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
