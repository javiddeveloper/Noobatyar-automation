package xyz.sattar.javid.proqueue

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.notification_permission_prompt_allow
import proqueue.composeapp.generated.resources.notification_permission_prompt_later
import proqueue.composeapp.generated.resources.notification_permission_prompt_message
import proqueue.composeapp.generated.resources.notification_permission_prompt_title
import xyz.sattar.javid.proqueue.core.network.GlobalError
import xyz.sattar.javid.proqueue.core.network.GlobalErrorManager
import xyz.sattar.javid.proqueue.core.navigation.navHost.AuthNavHost
import xyz.sattar.javid.proqueue.core.navigation.navHost.BusinessNavHost
import xyz.sattar.javid.proqueue.core.navigation.navHost.MainNavHost
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.core.permissions.rememberNotificationPermissionDenied
import xyz.sattar.javid.proqueue.core.permissions.rememberNotificationPermissionLauncher
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.usecase.push.SyncPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.HasTokenUseCase
import xyz.sattar.javid.proqueue.feature.version.VersionHandler
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
@Preview
fun App() {
    val themeMode by ThemeStateHolder.themeMode.collectAsState()
    LaunchedEffect(Unit) {
        PreferencesManager.themeMode.collect { ThemeStateHolder.setThemeMode(it) }
    }
    val hasTokenUseCase: HasTokenUseCase = koinInject()
    var onAuthComplete by remember { mutableStateOf(hasTokenUseCase()) }
    val scope = rememberCoroutineScope()
    val businessRepository: BusinessRepository = koinInject()
    val syncPushToken: SyncPushTokenUseCase = koinInject()

    // Re-registers this device with the backend on every signed-in start, not
    // only right after login: FCM rotates tokens on its own schedule
    // (reinstall, restored backup, cleared app data) and a stale token is
    // silently undeliverable, so re-sending the current one is cheaper than
    // trying to detect the rotation.
    LaunchedEffect(onAuthComplete) {
        if (onAuthComplete) syncPushToken()
    }

    // Mounted for the whole app (outside the auth/business/main nav swap below),
    // so a toast triggered right before a forced navigation — e.g. the session
    // expiring — actually survives to be seen instead of unmounting with the
    // screen that requested it.
    val globalSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        GlobalErrorManager.errorFlow.collect { error ->
            when (error) {
                GlobalError.Unauthorized -> {
                    // Previously this reset auth state with zero explanation —
                    // the user was just silently dropped back on the login screen.
                    scope.launch {
                        globalSnackbarHostState.showToasty(
                            UiMessage.warning("نشست شما منقضی شده است. لطفاً دوباره وارد شوید.")
                        )
                    }
                    onAuthComplete = false
                    BusinessStateHolder.clearBusiness()
                    PreferencesManager.setDefaultBusinessId(null)
                }
                is GlobalError.RateLimit -> {
                    scope.launch {
                        globalSnackbarHostState.showToasty(UiMessage.warning(error.message))
                    }
                }
            }
        }
    }

    AppTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
        // Handle Version Update (no iOS store listing yet, so skip this gate on iOS)
        if (!AppInfo.isIOS) {
            VersionHandler()
        }

        if (!onAuthComplete) {
            AuthNavHost(
                onRegisterComplete = { onAuthComplete = true },
                onNavigateToHome = { onAuthComplete = true },
            )
        } else {

        val selectedBusiness by BusinessStateHolder.selectedBusiness.collectAsState()

        LaunchedEffect(Unit) {
            PreferencesManager.defaultBusinessId.collect { id ->
                if (id != null && BusinessStateHolder.selectedBusiness.value == null) {
                    val business = businessRepository.getBusinessById(id)
                    if (business != null) {
                        BusinessStateHolder.selectBusiness(business)
                    }
                }
            }
        }

        if (selectedBusiness == null) {
            BusinessNavHost(
                onBusinessSelected = { business ->
                    BusinessStateHolder.selectBusiness(business)
                    scope.launch { PreferencesManager.setDefaultBusinessId(business.id) }
                },
                onNavigateToAuth = {
                    onAuthComplete = false
                }
            )
        } else {
            val currentBusiness = selectedBusiness
            MainNavHost(
                onChangeBusiness = {
                    BusinessStateHolder.clearBusiness()
                    scope.launch { PreferencesManager.setDefaultBusinessId(null) }
                },
                onNavigateToLogin = {
                    onAuthComplete = false
                    BusinessStateHolder.clearBusiness()
                    scope.launch { PreferencesManager.setDefaultBusinessId(null) }
                }
            )

            if (currentBusiness != null) {
                NotificationPermissionPrompt(business = currentBusiness, businessRepository = businessRepository)
            }
        }
        }

        ToastyHost(hostState = globalSnackbarHostState)
        }
    }
}

/**
 * App-wide, one-shot-per-session "may we notify you" dialog — the explicit
 * ask was to test permission everywhere and prompt for it right when the
 * app opens, rather than only inside the Notifications settings screen.
 * Gated on a business being selected (i.e. right as [MainNavHost] becomes
 * reachable), so it never competes with the auth/business-selection flow.
 *
 * Suppressed once permission is already granted, and — on platforms that
 * can tell the difference (web) — once it has been explicitly denied,
 * since there [rememberNotificationPermissionLauncher] can't do anything
 * and a "grant access" button that silently fails would be worse than no
 * dialog at all.
 *
 * Granting here also flips the business's own `notificationEnabled` flag
 * on, mirroring exactly what the Notifications screen's own save does
 * ([xyz.sattar.javid.proqueue.feature.notifications.NotificationsViewModel.
 * saveSettings]) — the explicit ask was that granting permission here
 * should also turn that screen's toggle on, and OS permission alone
 * doesn't move the server-side setting.
 */
@Composable
private fun NotificationPermissionPrompt(
    business: xyz.sattar.javid.proqueue.domain.model.business.Business,
    businessRepository: BusinessRepository
) {
    val notificationScheduler: NotificationScheduler = koinInject()
    val scope = rememberCoroutineScope()
    val permissionDenied = rememberNotificationPermissionDenied()
    var showPrompt by remember { mutableStateOf(false) }
    var checkedThisSession by remember { mutableStateOf(false) }

    val launcher = rememberNotificationPermissionLauncher { granted ->
        showPrompt = false
        if (granted) {
            scope.launch {
                val updated = business.copy(notificationEnabled = true)
                if (businessRepository.upsertBusiness(updated)) {
                    BusinessStateHolder.selectBusiness(updated)
                }
            }
        }
    }

    LaunchedEffect(business.id) {
        if (checkedThisSession) return@LaunchedEffect
        checkedThisSession = true
        if (!notificationScheduler.hasPermission() && !permissionDenied) {
            showPrompt = true
        }
    }

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text(stringResource(Res.string.notification_permission_prompt_title)) },
            text = { Text(stringResource(Res.string.notification_permission_prompt_message)) },
            confirmButton = {
                TextButton(
                    onClick = { launcher.launch() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(Res.string.notification_permission_prompt_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) {
                    Text(stringResource(Res.string.notification_permission_prompt_later))
                }
            }
        )
    }
}
