package xyz.sattar.javid.proqueue.core.navigation.navHost

import xyz.sattar.javid.proqueue.feature.calendar.CalendarScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import xyz.sattar.javid.proqueue.core.ui.LocalHazeState
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import xyz.sattar.javid.proqueue.core.navigation.NavigationEvent
import xyz.sattar.javid.proqueue.core.navigation.NotificationNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.PaymentNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.PendingVisitorsFilter
import xyz.sattar.javid.proqueue.core.ui.components.AppNavigationRail
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.BottomNavigationBar
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentScreen
import xyz.sattar.javid.proqueue.feature.createVisitor.CreateVisitorRoute
import xyz.sattar.javid.proqueue.feature.home.HomeScreen
import xyz.sattar.javid.proqueue.feature.home.VisitorsNavArgs
import xyz.sattar.javid.proqueue.feature.lastVisitors.LastVisitorsScreen
import xyz.sattar.javid.proqueue.feature.messages.MessagesScreen
import xyz.sattar.javid.proqueue.feature.notifications.NotificationsScreen
import xyz.sattar.javid.proqueue.feature.profile.PaymentResultScreen
import xyz.sattar.javid.proqueue.feature.settings.EmergencyNoticeScreen
import xyz.sattar.javid.proqueue.feature.settings.SettingsScreen
import xyz.sattar.javid.proqueue.feature.smsReport.SmsReportScreen
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsScreen
import xyz.sattar.javid.proqueue.feature.visitorSelection.VisitorSelectionScreen
import xyz.sattar.javid.proqueue.feature.aboutUs.AboutUsScreen
import xyz.sattar.javid.proqueue.feature.addons.AddonsScreen
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessRoute
import xyz.sattar.javid.proqueue.feature.createBusiness.AdvancedSettingsRoute


@Composable
fun MainNavHost(
    onChangeBusiness: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val notificationEvent by NotificationNavigationManager.navigationEvent.collectAsState()
    val paymentEvent by PaymentNavigationManager.paymentEvent.collectAsState()

    // Shared blur source/target state for the glass top & bottom bars.
    val hazeState = remember { HazeState() }

    // GlobalError (Unauthorized / RateLimit) is now shown from a single
    // always-mounted host in App.kt, which survives the navigation swap that
    // Unauthorized triggers — this screen-local host used to only catch
    // RateLimit, and would unmount before Unauthorized's toast could show.

    LaunchedEffect(notificationEvent) {
        notificationEvent?.let { event ->
            if (event is NavigationEvent.ToVisitorDetails) {
                navController.navigate(AppScreens.VisitorDetails(event.visitorId, event.openMessageDialog))
                NotificationNavigationManager.consumeEvent()
            }
        }
    }

    LaunchedEffect(paymentEvent) {
        paymentEvent?.let { event ->
            navController.navigate(
                AppScreens.PaymentResult(
                    success = if (event.success) 1 else 0,
                    ref = event.ref,
                    amount = event.amount,
                    txn = event.txn
                )
            )
            PaymentNavigationManager.consumeEvent()
        }
    }

    val tabs = listOf(
        MainTab.LastVisitors,
        MainTab.Home,
        MainTab.Settings
    )

    // Determine if the bottom bar should be shown
    val shouldShowBottomBar = tabs.any { tab ->
        currentDestination?.hierarchy?.any {
            it.route == tab.route::class.qualifiedName
        } == true
    }

    val selectedTab = tabs.find { tab ->
        currentDestination?.hierarchy?.any {
            it.route == tab.route::class.qualifiedName
        } == true
    } ?: MainTab.Home

    // Same tab-switch logic as before extraction — only the container that
    // triggers it (bottom bar vs rail) differs by window size below.
    val onTabSelected: (MainTab) -> Unit = { tab ->
        if (selectedTab != tab) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val windowSize = LocalWindowSize.current

    CompositionLocalProvider(LocalHazeState provides hazeState) {
    if (windowSize == WindowSize.Compact) {
    // Compact: unchanged from before this file gained an adaptive layout —
    // same Scaffold, same floating BottomNavigationBar.
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = shouldShowBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                )
            ) {
                BottomNavigationBar(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected
                )
            }
        }
    ) { paddingValues ->
        // Note: we intentionally do NOT pad the content by the bottom bar height.
        // The bottom bar floats (with a gradient scrim), so screens scroll under
        // it with no solid gap. Tab screens add BottomBarSpacer / FabClearance
        // so the last items and FABs clear the bar.
        MainNavGraph(
            navController = navController,
            hazeState = hazeState,
            onChangeBusiness = onChangeBusiness,
            onNavigateToLogin = onNavigateToLogin,
            modifier = Modifier.fillMaxSize()
        )
    }
    } else {
    // Medium/Expanded: a standard NavigationRail instead of the floating
    // bottom bar. BottomNavigationBar is a heavily custom shape (cutout
    // notch for the floating home button) that only makes sense as a
    // phone-width bottom bar, so it is not reused here — same tabs/icons,
    // a plain rail container.
    //
    // RTL: the whole app is forced LayoutDirection.Rtl (Theme.kt), so a Row's
    // "start" edge is the right-hand edge of the screen. Placing the rail as
    // the Row's first child therefore lands it on the right without any
    // manual mirroring — Compose resolves Row order against the ambient
    // LayoutDirection automatically.
    Row(modifier = Modifier.fillMaxSize()) {
        // fillMaxHeight here is what the old bare AnimatedVisibility{NavigationRail}
        // was missing — without it the rail only took the height its content
        // needed, which under Row's default top alignment left it pinned to
        // y=0 with no room for a header, reading as jammed into the corner.
        AnimatedVisibility(
            visible = shouldShowBottomBar,
            modifier = Modifier.fillMaxHeight()
        ) {
            AppNavigationRail(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )
        }
        // Capped here rather than per-screen so every destination in the
        // graph is covered, not just the handful that were wrapped
        // individually — a screen added later inherits this for free.
        AppScaffold(
            modifier = Modifier.weight(1f),
            maxWidth = ContentWidth.Wide
        ) {
            MainNavGraph(
                navController = navController,
                hazeState = hazeState,
                onChangeBusiness = onChangeBusiness,
                onNavigateToLogin = onNavigateToLogin,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    }
    }
}

/**
 * The tab graph itself, factored out of [MainNavHost] so the Compact
 * (bottom bar) and Medium/Expanded (rail) branches can share it verbatim
 * instead of duplicating ~300 lines of `composable<>` destinations.
 */
@Composable
private fun MainNavGraph(
    navController: NavHostController,
    hazeState: HazeState,
    onChangeBusiness: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
        NavHost(
            navController = navController,
            startDestination = AppScreens.Home,
            modifier = modifier
                .hazeSource(hazeState)
        ) {
            composable<AppScreens.Home> {
                HomeScreen(
                    onNavigateToCalendar = {
                        navController.navigate(AppScreens.Calendar())
                    },
                    onNavigateToLogin = onNavigateToLogin,
                    onChangeBusiness = onChangeBusiness,
                    onNavigateToAddons = {
                        navController.navigate(AppScreens.AddOns)
                    },
                    onNavigateToVisitors = { args: VisitorsNavArgs ->
                        // Same call the bottom bar's onTabSelected uses — this
                        // must land on the one shared MainTab.LastVisitors
                        // entry, not push a second stacked copy (that copy
                        // wouldn't match any tab in `tabs`, so shouldShowBottomBar
                        // would stay false and the bottom bar would vanish).
                        PendingVisitorsFilter.set(args)
                        navController.navigate(MainTab.LastVisitors.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable<AppScreens.Visitors> {
                val pending = remember { PendingVisitorsFilter.consume() }
                LastVisitorsScreen(
                    initialStatus = pending?.status,
                    initialTab = pending?.tab,
                    initialDateFrom = pending?.dateFrom,
                    initialDateTo = pending?.dateTo,
                    onNavigateToCreateAppointment = {
                        navController.navigate(AppScreens.VisitorSelection(returnResult = false))
                    },
                    onNavigateToEditAppointment = { appointmentId ->
                        navController.navigate(AppScreens.CreateAppointment(appointmentId = appointmentId))
                    },
                    onNavigateToVisitorDetails = { visitorId ->
                        navController.navigate(AppScreens.VisitorDetails(visitorId))
                    },
                    onNavigateToLogin = onNavigateToLogin,
                    onChangeBusiness = onChangeBusiness
                )
            }

            composable<AppScreens.VisitorSelection> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.VisitorSelection>()
                val createdVisitorId = backStackEntry.savedStateHandle.get<Long>("createdVisitorId")
                LaunchedEffect(createdVisitorId) {
                    if (createdVisitorId != null) {
                         if (args.returnResult) {
                            navController.previousBackStackEntry?.savedStateHandle?.set("selectedVisitorId", createdVisitorId)
                            navController.popBackStack()
                        } else {
                            navController.navigate(AppScreens.CreateAppointment(visitorId = createdVisitorId))
                        }
                        backStackEntry.savedStateHandle.remove<Long>("createdVisitorId")
                    }
                }

                VisitorSelectionScreen(
                    onNavigateToCreateAppointment = { visitorId ->
                        if (args.returnResult) {
                            navController.previousBackStackEntry?.savedStateHandle?.set("selectedVisitorId", visitorId)
                            navController.popBackStack()
                        } else {
                            navController.navigate(AppScreens.CreateAppointment(visitorId = visitorId))
                        }
                    },
                    onNavigateToEditVisitor = { visitorId ->
                        navController.navigate(AppScreens.EditVisitor(visitorId))
                    },
                    onNavigateToCreateVisitor = {
                        navController.navigate(AppScreens.CreateVisitor)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.Settings> {
                SettingsScreen(
                    onNavigateToAbout = {
                        navController.navigate(AppScreens.AboutUs)
                    },
                    onChangeBusiness = onChangeBusiness,
                    onNavigateToEditBusiness = { businessId ->
                        navController.navigate(AppScreens.CreateBusiness(businessId = businessId))
                    },
                    onNavigateToAdvancedSettings = { businessId ->
                        navController.navigate(AppScreens.AdvancedSettings(businessId = businessId))
                    },
                    onNavigateToNotifications = {
                        navController.navigate(AppScreens.Notifications)
                    },
                    onNavigateToMessages = {
                        navController.navigate(AppScreens.Messages)
                    },
                    onNavigateToSmsReport = {
                        navController.navigate(AppScreens.SmsReport)
                    },
                    onNavigateToEmergencyNotice = {
                        navController.navigate(AppScreens.EmergencyNotice)
                    },
                    onNavigateToLogin = onNavigateToLogin
                )
            }

            composable<AppScreens.CreateBusiness> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.CreateBusiness>()
                CreateBusinessRoute(
                    businessId = args.businessId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onContinue = {
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.AdvancedSettings> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.AdvancedSettings>()
                AdvancedSettingsRoute(
                    businessId = args.businessId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<AppScreens.Notifications> {
                NotificationsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.Messages> {
                MessagesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddons = { navController.navigate(AppScreens.AddOns) }
                )
            }

            composable<AppScreens.SmsReport> {
                SmsReportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<AppScreens.EmergencyNotice> {
                EmergencyNoticeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<AppScreens.VisitorDetails> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.VisitorDetails>()
                VisitorDetailsScreen(
                    visitorId = args.visitorId,
                    openMessageDialog = args.openMessageDialog,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToCreateAppointment = { visitorId ->
                        navController.navigate(AppScreens.CreateAppointment(visitorId))
                    }
                )
            }

            composable<AppScreens.CreateVisitor> {
                CreateVisitorRoute(
                    onContinue = { visitorId ->
                         navController.previousBackStackEntry?.savedStateHandle?.set("createdVisitorId", visitorId)
                         navController.popBackStack()
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.EditVisitor> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.EditVisitor>()
                CreateVisitorRoute(
                    visitorId = args.visitorId,
                    onContinue = { visitorId ->
                        navController.popBackStack()
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.Calendar> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.Calendar>()
                CalendarScreen(
                    isPicker = args.isPicker,
                    excludeAppointmentId = args.excludeAppointmentId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreateAppointment = { date, time ->
                        navController.navigate(AppScreens.CreateAppointment(date = date, time = time))
                    },
                    onNavigateToAppointmentDetails = { appointmentId ->
                        navController.navigate(AppScreens.CreateAppointment(appointmentId = appointmentId))
                    },
                    onSlotSelected = { date, time ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("selectedDate", date)
                        navController.previousBackStackEntry?.savedStateHandle?.set("selectedTime", time)
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.CreateAppointment> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.CreateAppointment>()
                val selectedDate = backStackEntry.savedStateHandle.get<Long>("selectedDate")
                val selectedTime = backStackEntry.savedStateHandle.get<String>("selectedTime")
                val selectedVisitorId = backStackEntry.savedStateHandle.get<Long>("selectedVisitorId")

                CreateAppointmentScreen(
                    visitorId = selectedVisitorId ?: args.visitorId,
                    appointmentId = args.appointmentId,
                    initialDate = selectedDate ?: args.date,
                    initialTime = selectedTime ?: args.time,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToCalendar = {
                        navController.navigate(
                            AppScreens.Calendar(isPicker = true, excludeAppointmentId = args.appointmentId)
                        )
                    },
                    onNavigateToVisitorSelection = {
                        navController.navigate(AppScreens.VisitorSelection(returnResult = true))
                    },
                    onAppointmentCreated = {
                        navController.navigate(AppScreens.Home) {
                            popUpTo(AppScreens.Home) {
                                inclusive = true
                            }
                        }
                    },
                )
            }

            composable<AppScreens.AboutUs> {
                AboutUsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<AppScreens.AddOns> {
                AddonsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // No `deepLinks` here on purpose — the noobatyar://payment/result
            // deep link is parsed once in MainActivity and routed through
            // PaymentNavigationManager (see the LaunchedEffect above). Letting
            // Navigation-Compose auto-handle the Activity intent itself would
            // re-trigger this dialog every time a fresh NavHostController sets
            // its graph, e.g. after a business switch recreates MainNavHost.
            dialog<AppScreens.PaymentResult>(
                dialogProperties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.PaymentResult>()
                PaymentResultScreen(
                    success = args.success == 1,
                    ref = args.ref,
                    amount = args.amount,
                    onDone = {
                        navController.navigate(AppScreens.Home) {
                            popUpTo(AppScreens.Home) { inclusive = true }
                        }
                    }
                )
            }
        }
}
