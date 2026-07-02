package xyz.sattar.javid.proqueue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
    var showAuthFlow by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val businessRepository: BusinessRepository = koinInject()

    AppTheme(themeMode = themeMode) {
        // Handle Version Update
        VersionHandler()

        Box(modifier = Modifier.fillMaxSize()) {
            MainNavHost(
                onNavigateToCreateBusiness = {},
                onNavigateToCreateVisitor = {},
                onChangeBusiness = {},
                onNavigateToLogin = {
                    showAuthFlow = true
                }
            )

            if (showAuthFlow) {
                AuthNavHost(
                    onRegisterComplete = { showAuthFlow = false },
                    onNavigateToHome = { showAuthFlow = false },
                )
            }
        }
    }
}
