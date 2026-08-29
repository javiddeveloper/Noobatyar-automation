package xyz.sattar.javid.proqueue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.network.GlobalError
import xyz.sattar.javid.proqueue.core.network.GlobalErrorManager
import xyz.sattar.javid.proqueue.core.navigation.navHost.AuthNavHost
import xyz.sattar.javid.proqueue.core.navigation.navHost.BusinessNavHost
import xyz.sattar.javid.proqueue.core.navigation.navHost.MainNavHost
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
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
        // Root width probe for the adaptive layout (docs/OWNER_WEB_PLAN.md
        // section ۸): everything below reads its bucket from LocalWindowSize
        // instead of measuring the window itself. maxWidth here is in dp
        // already (BoxWithConstraints), matching the plan's breakpoint table.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowSize = WindowSize.of(maxWidth)
        CompositionLocalProvider(LocalWindowSize provides windowSize) {
        // Painted at the root so no region can ever fall through to the
        // web canvas's white clear colour — on wasmJs anything Compose
        // does not draw over shows up as a white gap, not as the page
        // background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // Handle Version Update (no iOS store listing yet, so skip this gate on
        // iOS; no store/version concept on the web either, see AppInfo.isWeb)
        if (!AppInfo.isIOS && !AppInfo.isWeb) {
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
                    var business = businessRepository.getBusinessById(id)
                    // On wasmJs the local cache is in-memory only (see
                    // docs/OWNER_WEB_PLAN.md section 5) and starts empty on
                    // every fresh page load — unlike Android/iOS, where Room
                    // persists it to disk. defaultBusinessId itself survives
                    // a refresh (it's in localStorage), so without this
                    // fallback the owner's remembered business could never be
                    // found again after a browser reload, and every refresh
                    // silently dropped back to the business list. Refetching
                    // once and re-checking the cache fixes that on web and is
                    // a harmless no-op on Android/iOS, where the first lookup
                    // above already succeeds and this branch never runs.
                    if (business == null) {
                        businessRepository.fetchAndCacheBusinesses(page = 1, pageSize = 20)
                        business = businessRepository.getBusinessById(id)
                    }
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
        }
        }

        ToastyHost(hostState = globalSnackbarHostState)
        }
        }
        }
    }
}
