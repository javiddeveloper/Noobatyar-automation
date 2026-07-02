package xyz.sattar.javid.proqueue.core.navigation.navHost

import xyz.sattar.javid.proqueue.feature.calendar.CalendarScreen
import xyz.sattar.javid.proqueue.feature.settings.SettingsScreen
import xyz.sattar.javid.proqueue.feature.notifications.NotificationsScreen
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
import xyz.sattar.javid.proqueue.feature.aboutUs.AboutUsScreen

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



    val tabs = listOf(
        MainTab.Appointments,
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
                xyz.sattar.javid.proqueue.feature.businessList.BusinessListScreen(
                    onNavigateToMain = { business -> 
                        navController.navigate(AppScreens.BusinessDetail(business.id))
                    },
                    onNavigateToCreateBusiness = {},
                    onNavigateToEditBusiness = {},
                    onNavigateToLogin = onNavigateToLogin,
                )
            }

            composable<AppScreens.BusinessDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<AppScreens.BusinessDetail>()
                val userViewModel: xyz.sattar.javid.proqueue.feature.profile.UserViewModel = org.koin.compose.viewmodel.koinViewModel()
                val userState by userViewModel.uiState.collectAsState()
                
                val selectedDate = backStackEntry.savedStateHandle.get<Long>("selectedDate")
                val selectedTime = backStackEntry.savedStateHandle.get<String>("selectedTime")

                xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailScreen(
                    businessId = args.businessId,
                    isLoggedIn = userState.userName != null,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreateAppointment = { id ->
                        navController.navigate(AppScreens.CreateAppointment())
                    },
                    selectedDate = selectedDate,
                    selectedTime = selectedTime,
                    onNavigateToCalendar = {
                        navController.navigate(AppScreens.Calendar(isPicker = true))
                    }
                )
            }

            composable<AppScreens.Appointments> {
                xyz.sattar.javid.proqueue.feature.clientAppointments.ClientAppointmentsScreen(
                    onNavigateToLogin = onNavigateToLogin
                )
            }

            composable<AppScreens.Settings> {
                SettingsScreen(
                    onNavigateToAbout = {
                        navController.navigate(AppScreens.AboutUs)
                    },
                    onChangeBusiness = onChangeBusiness,
                    onNavigateToEditBusiness = { _ ->
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



            composable<AppScreens.Notifications> {
                NotificationsScreen(
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
        }
    }
}
