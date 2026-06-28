package xyz.sattar.javid.proqueue.feature.version

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.ui.components.VersionUpdateBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.NoInternetDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHandler(
    viewModel: VersionViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(VersionIntent.CheckVersion)
    }

    // Network Error Check (Blocking)
    if (state.isNetworkError) {
        NoInternetDialog(
            isLoading = state.isLoading,
            onRetry = {
                viewModel.sendIntent(VersionIntent.CheckVersion)
            }
        )
        return // Block UI if there's no internet
    }

    // Version Update Dialog
    if (state.showUpdateDialog && state.updateInfo != null) {
        VersionUpdateBottomSheet(
            versionInfo = state.updateInfo!!,
            onDismiss = { viewModel.sendIntent(VersionIntent.DismissDialog) },
            onUpdateClick = {
                openUrl("https://cafebazaar.ir/developer/nice_javid")
            }
        )
    }
}
