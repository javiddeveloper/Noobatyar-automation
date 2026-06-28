package xyz.sattar.javid.proqueue.core.navigation.navHost

import xyz.sattar.javid.proqueue.feature.calendar.CalendarScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import xyz.sattar.javid.proqueue.core.navigation.NavigationEvent
import xyz.sattar.javid.proqueue.core.navigation.NotificationNavigationManager
import xyz.sattar.javid.proqueue.core.ui.components.BottomNavigationBar
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentScreen
import xyz.sattar.javid.proqueue.feature.createVisitor.CreateVisitorRoute
import xyz.sattar.javid.proqueue.feature.home.HomeScreen
import xyz.sattar.javid.proqueue.feature.lastVisitors.LastVisitorsScreen
import xyz.sattar.javid.proqueue.feature.messages.MessagesScreen
import xyz.sattar.javid.proqueue.feature.notifications.NotificationsScreen
import xyz.sattar.javid.proqueue.feature.profile.PaymentResultScreen
import xyz.sattar.javid.proqueue.feature.settings.SettingsScreen
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsScreen
import xyz.sattar.javid.proqueue.feature.visitorSelection.VisitorSelectionScreen
import xyz.sattar.javid.proqueue.feature.aboutUs.AboutUsScreen
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessRoute

import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
fun MainNavHost(
    onNavigateToCreateBusiness: () -> Unit = {},
    onNavigateToCreateVisitor: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val notificationEvent by NotificationNavigationManager.navigationEvent.collectAsState()

    LaunchedEffect(notificationEvent) {
        notificationEvent?.let { event ->
            if (event is NavigationEvent.ToVisitorDetails) {
                navController.navigate(AppScreens.VisitorDetails(event.visitorId, event.openMessageDialog))
                NotificationNavigationManager.consumeEvent()
            }
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
                    onTabSelected = { tab ->
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
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = AppScreens.Home,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            composable<AppScreens.Home> {
                HomeScreen(
                    onNavigateToCalendar = {
                        navController.navigate(AppScreens.Calendar())
                    },
                    onNavigateToLogin = onNavigateToLogin,
                )
            }

            composable<AppScreens.Visitors> {
                LastVisitorsScreen(
                    onNavigateToCreateAppointment = {
                        navController.navigate(AppScreens.VisitorSelection(returnResult = false))
                    },
                    onNavigateToEditAppointment = { appointmentId ->
                        navController.navigate(AppScreens.CreateAppointment(appointmentId = appointmentId))
                    },
                    onNavigateToVisitorDetails = { visitorId ->
                        navController.navigate(AppScreens.VisitorDetails(visitorId))
                    },
                    onNavigateToLogin = onNavigateToLogin
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
                    onNavigateToNotifications = {
                        navController.navigate(AppScreens.Notifications)
                    },
                    onNavigateToMessages = {
                        navController.navigate(AppScreens.Messages)
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

            composable<AppScreens.Notifications> {
                NotificationsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<AppScreens.Messages> {
                MessagesScreen(
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
                        navController.navigate(AppScreens.Calendar(isPicker = true))
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

            dialog<AppScreens.PaymentResult>(
                dialogProperties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "noobatyar://payment/result?success={success}&ref={ref}&amount={amount}&txn={txn}"
                    }
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
}
