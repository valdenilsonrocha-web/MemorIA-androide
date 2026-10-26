package com.memoria.mobile.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.memoria.mobile.ui.auth.OnboardingPrefs
import com.memoria.mobile.ui.auth.OnboardingScreen
import com.memoria.mobile.ui.auth.RegisterScreen
import com.memoria.mobile.ui.calendar.CalendarScreen
import com.memoria.mobile.reminders.MemoriaNotifications
import com.memoria.mobile.ui.common.LocalShowBackArrow
import com.memoria.mobile.ui.common.MemoriaHeader
import com.memoria.mobile.ui.common.TrialExpiryBanner
import com.memoria.mobile.ui.common.SystemBarIcons
import com.memoria.mobile.ui.common.reminderScheduler
import com.memoria.mobile.ui.common.repoViewModel
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
import com.memoria.mobile.ui.more.MoreMenuSheet
import com.memoria.mobile.ui.more.MoreScreen
import com.memoria.mobile.ui.optimization.OptimizationGuideScreen
import com.memoria.mobile.ui.plans.PlansScreen
import com.memoria.mobile.ui.prescriptions.PrescriptionsScreen
import com.memoria.mobile.ui.profile.ProfileScreen
import com.memoria.mobile.ui.replenishment.ReplenishmentScreen
import com.memoria.mobile.ui.reports.ReportsScreen
import com.memoria.mobile.ui.settings.SettingsScreen
import com.memoria.mobile.ui.whatsapp.WhatsAppScreen
import java.io.File
import com.memoria.mobile.ui.common.ReloadOnDoseChanges

/**
 * Auth state is hoisted here (not encoded as navigation destinations) so that
 * logout disposes the ENTIRE main graph — its back stack, saved tab states, and
 * every ViewModel scoped to it — leaving no data behind for a next account on
 * the same device. Login likewise disposes the auth graph.
 */
@Composable
fun MemoriaNav(startLoggedIn: Boolean) {
    val repo = LocalContext.current.repository()
    var loggedIn by remember { mutableStateOf(startLoggedIn) }
    var sessionExpired by remember { mutableStateOf(false) }

    // When the server rejects the token, the repository clears it and this emits
    // null — which is what brings the user back to the login screen.
    //
    // Without it an expired token was a dead end: every screen showed "Token
    // inválido ou expirado." with a "Tentar novamente" button that re-sent the
    // same dead token and got the same 401 forever, and the only way out was
    // Configurações › Sair. Tokens last 7 days, so this happened weekly.
    LaunchedEffect(Unit) {
        repo.tokenFlow.collect { token ->
            if (token.isNullOrBlank() && loggedIn) {
                sessionExpired = true
                loggedIn = false
            }
        }
    }

    if (loggedIn) {
        MainFlow(onLogout = { loggedIn = false })
    } else {
        AuthFlow(
            onAuthenticated = { sessionExpired = false; loggedIn = true },
            // Explains the bounce. Landing on a login screen with no reason given
            // reads as the app having lost the account.
            expiredNotice = sessionExpired,
        )
    }
}

@Composable
private fun AuthFlow(onAuthenticated: () -> Unit, expiredNotice: Boolean = false) {
    val nav = rememberNavController()
    // The sign-in screens are white: dark status-bar icons.
    SystemBarIcons(lightIcons = false)
    NavHost(navController = nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = onAuthenticated,
                onRegister = { nav.navigate(Routes.REGISTER) },
                onForgotPassword = { nav.navigate(Routes.FORGOT_PASSWORD) },
                expiredNotice = expiredNotice,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainFlow(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    val repo = context.repository()

    val shell = repoViewModel { ShellViewModel(it) }
    val pendingCount by shell.pendingCount.collectAsStateWithLifecycle()
    val trialHours by shell.trialHoursLeft.collectAsStateWithLifecycle()
    // Recounted on every page change — the web recounts on each render too, and a
    // dose taken on one screen must clear the badge by the time the next shows.
    LaunchedEffect(currentRoute) { shell.refresh() }
    ReloadOnDoseChanges(shell::refresh)

    // `syncProfileAvatar()`: the header avatar shows the profile photo. Re-read on
    // every page change so a photo taken in "Meu Perfil" is there on the way out.
    var photoPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        photoPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { repo.local.uiSetting(PROFILE_PHOTO_KEY) }.getOrNull()
                ?.takeIf { it.isNotBlank() && File(it).exists() }
        }
    }

    // "Mais" is a sheet over the current page on the web, not a page of its own.
    var showMore by rememberSaveable { mutableStateOf(false) }

    // First-run setup (the web's `#onboardingOverlay`), once per device.
    var showOnboarding by remember { mutableStateOf(!OnboardingPrefs.isCompleted(context)) }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denied is survivable: the app still works, it just cannot remind. */ }

    // Asked here rather than at launch because this composable exists only once
    // the user is in — a permission dialog over the login screen reads as noise
    // and gets dismissed. Arming the alarms rides along, so both a returning
    // session and a fresh login end up with reminders scheduled.
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.SETTINGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MemoriaNotifications.canPost(context)
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { context.reminderScheduler().reschedule() }
    }

    if (showOnboarding) {
        OnboardingScreen(onFinished = { showOnboarding = false })
        return
    }

    // Medication reminders must always open big; ask for what that needs.
    var voiceOpenedForSession by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!voiceOpenedForSession) {
            voiceOpenedForSession = true
            nav.navigate(Routes.VOICE) { launchSingleTop = true }
        }
    }
    if (currentRoute != Routes.VOICE) com.memoria.mobile.ui.common.BigAlertPermissionDialog()

    val goHome = { navigateToTab(nav, Routes.HOME) }
    val goMeds = { navigateToTab(nav, Routes.MEDS) }

    Scaffold(
        // The web's blue `<header>` sits over every page.
        topBar = {
            MemoriaHeader(
                pendingCount = pendingCount,
                onProfile = {
                    if (currentRoute != Routes.PROFILE) nav.navigate(Routes.PROFILE) { launchSingleTop = true }
                },
                onPendingAlerts = { navigateToTab(nav, Routes.HISTORY) },
                shareUrl = runCatching { repo.currentBaseUrl() }.getOrNull(),
                photoPath = photoPath,
            )
        },
        // On the web the bottom bar is part of the page shell (`.container`), so it
        // stays under EVERY page, not only the five it links to.
        bottomBar = {
            MemoriaBottomBar(
                selectedRoute = if (showMore) Routes.MORE else tabFor(currentRoute),
                pendingCount = pendingCount,
                onSelect = { tab ->
                    if (tab.route == Routes.MORE) {
                        showMore = true
                    } else {
                        navigateToTab(nav, tab.route)
                    }
                },
            )
        },
    ) { padding ->
        val back = { nav.popBackStack(); Unit }
        // The NavHost takes both bars' space and consumes those insets, so every
        // screen — tab or not — lays out between the header and the bottom bar
        // without adding the system bars a second time.
        val none = PaddingValues()

        if (showMore) {
            MoreMenuSheet(
                onDismiss = { showMore = false },
                onNavigate = { route ->
                    if (BOTTOM_TABS.any { it.route == route }) navigateToTab(nav, route) else nav.navigate(route)
                },
            )
        }

        // The web has no back arrows: the bottom bar is always there.
        CompositionLocalProvider(LocalShowBackArrow provides false) {
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // `#trialExpiryBanner`, above whatever page is open.
            trialHours?.let { hours -> TrialExpiryBanner(hours, onManage = { nav.navigate(Routes.PLANS) }) }
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.weight(1f),
            ) {
                // ---- Bottom bar ----
                composable(Routes.HOME) {
                    DashboardScreen(
                        contentPadding = none,
                        // "Adicionar" on the web opens `addMedicationPage` — the tab.
                        onAdd = goMeds,
                        onOpenVoice = { nav.navigate(Routes.VOICE) { launchSingleTop = true } },
                        onOpenMedication = { id -> nav.navigate(Routes.medDetails(id)) },
                        onOpenPlans = { nav.navigate(Routes.PLANS) },
                        onEdit = { id -> nav.navigate(Routes.medEdit(id)) },
                    )
                }
                // The web's "Medicamentos" tab is `addMedicationPage`: the form
                // itself, titled "Adicionar Medicamento". Saving or "Cancelar"
                // returns to the dashboard (`navigateToPage('dashboardPage')`).
                composable(Routes.MEDS) {
                    MedicationsScreen(
                        contentPadding = none,
                        onAdd = {},
                        onEdit = { id -> nav.navigate(Routes.medEdit(id)) },
                        onOpenDetails = { id -> nav.navigate(Routes.medDetails(id)) },
                        onGoHome = goHome,
                    )
                }
                composable(Routes.HEALTH) {
                    HealthScreen(contentPadding = none)
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        contentPadding = none,
                        onOpenPlans = { nav.navigate(Routes.PLANS) },
                        onOpenReplenishment = { nav.navigate(Routes.REPLENISHMENT) },
                        onOpenPrescriptions = { nav.navigate(Routes.PRESCRIPTIONS) },
                        onOpenDoctors = { nav.navigate(Routes.DOCTORS) },
                        onOpenProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    )
                }
                composable(Routes.MORE) {
                    MoreScreen(contentPadding = none, onNavigate = { route ->
                        if (BOTTOM_TABS.any { it.route == route }) navigateToTab(nav, route) else nav.navigate(route)
                    })
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
                composable(Routes.VOICE) { com.memoria.mobile.ui.voice.VoiceScreen(onBack = back, onOpenPage = { route -> nav.navigate(route) { launchSingleTop = true } }) }
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
                // Not linked from anywhere (the web has no WhatsApp page); kept so a
                // restored back stack that still holds it cannot crash the NavHost.
                composable(Routes.WHATSAPP) {
                    WhatsAppScreen(contentPadding = none, onBack = back)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        contentPadding = none,
                        onLoggedOut = onLogout,
                        onOpenPlans = { nav.navigate(Routes.PLANS) },
                        onOpenProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                        onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                        onBack = back,
                    )
                }
            }
            }
        }
    }
}

/** `profile_photo_path` — where ProfileViewModel keeps the photo's file path. */
private const val PROFILE_PHOTO_KEY = "profile_photo_path"

/**
 * Which bottom-bar item the web lights for a page (`nav-item[data-page=…]`):
 * the five tabs themselves, and the medication form in edit mode, which is the
 * same `addMedicationPage`. Every other page lights none.
 */
private fun tabFor(route: String?): String? = when {
    route == null -> null
    BOTTOM_TABS.any { it.route == route } -> route
    route.startsWith(Routes.MED_EDIT) -> Routes.MEDS
    else -> null
}

private fun navigateToTab(nav: NavHostController, route: String) {
    // Close the secondary pages (Editar/Detalhes do Medicamento, Calendário…)
    // sitting on top of the current tab first. Otherwise `saveState` saved them
    // with the tab and `restoreState` brought them back: tapping "Medicamentos"
    // from the edit page reopened the edit page instead of the tab. The web has
    // no back stack — a bottom-bar item always shows that page.
    while (nav.currentDestination?.route.let { r -> r != null && BOTTOM_TABS.none { it.route == r } }) {
        if (!nav.popBackStack()) break
    }
    if (nav.currentDestination?.route == route) return
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
