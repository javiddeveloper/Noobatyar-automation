package xyz.sattar.javid.proqueue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.navigation.navHost.AuthNavHost
import xyz.sattar.javid.proqueue.core.navigation.navHost.BusinessNavHost
import xyz.sattar.javid.proqueue.core.navigation.navHost.MainNavHost
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.domain.BusinessRepository
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

    AppTheme(themeMode = themeMode) {
        // Handle Version Update
        VersionHandler()

        if (!onAuthComplete) {
            AuthNavHost(
                onRegisterComplete = { onAuthComplete = true },
                onNavigateToHome = { onAuthComplete = true },
            )
            return@AppTheme
        }

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
            MainNavHost(
                onNavigateToCreateBusiness = {
                    BusinessStateHolder.clearBusiness()
                    scope.launch { PreferencesManager.setDefaultBusinessId(null) }
                },
                onNavigateToCreateVisitor = {
                },
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
}
