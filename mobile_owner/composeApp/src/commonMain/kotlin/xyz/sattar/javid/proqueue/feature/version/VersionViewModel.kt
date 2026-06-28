package xyz.sattar.javid.proqueue.feature.version

import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.model.VersionInfo
import xyz.sattar.javid.proqueue.domain.usecase.user.CheckVersionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.AppInfo

data class VersionState(
    val isLoading: Boolean = false,
    val updateInfo: VersionInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isNetworkError: Boolean = false
)

sealed class VersionIntent {
    data object CheckVersion : VersionIntent()
    data object DismissDialog : VersionIntent()
}

sealed class VersionEvent

sealed class VersionPartialState {
    data class Loading(val isLoading: Boolean) : VersionPartialState()
    data class UpdateAvailable(val info: VersionInfo) : VersionPartialState()
    data object HideDialog : VersionPartialState()
    data class NetworkError(val isError: Boolean) : VersionPartialState()
}

class VersionViewModel(
    private val checkVersionUseCase: CheckVersionUseCase
) : BaseViewModel<VersionState, VersionPartialState, VersionEvent, VersionIntent>(
    initialState = VersionState()
) {
    override fun handleIntent(intent: VersionIntent): Flow<VersionPartialState> = flow {
        when (intent) {
            VersionIntent.CheckVersion -> {
                emit(VersionPartialState.Loading(true))
                emit(VersionPartialState.NetworkError(false))
                try {
                    when (val result = checkVersionUseCase(AppInfo.versionName)) {
                        is ApiResponse.Success -> {
                            val serverVersion = result.data.latestVersion.versionCode
                            val currentVersion = AppInfo.versionCode
                            if (serverVersion > currentVersion) {
                                emit(VersionPartialState.UpdateAvailable(result.data))
                            }
                        }
                        is ApiResponse.Error -> {
                            // If it's a critical error (like no internet), we show the error state
                            // You can check result.code here if needed, e.g., if code is 500 or specific network error
                            emit(VersionPartialState.NetworkError(true))
                        }
                    }
                } catch (e: Exception) {
                    emit(VersionPartialState.NetworkError(true))
                } finally {
                    emit(VersionPartialState.Loading(false))
                }
            }
            VersionIntent.DismissDialog -> emit(VersionPartialState.HideDialog)
        }
    }

    override fun reduceState(currentState: VersionState, partialState: VersionPartialState): VersionState {
        return when (partialState) {
            is VersionPartialState.Loading -> currentState.copy(isLoading = partialState.isLoading)
            is VersionPartialState.UpdateAvailable -> currentState.copy(
                updateInfo = partialState.info,
                showUpdateDialog = true
            )
            VersionPartialState.HideDialog -> currentState.copy(showUpdateDialog = false)
            is VersionPartialState.NetworkError -> currentState.copy(isNetworkError = partialState.isError)
        }
    }

    override fun createErrorState(message: String): VersionPartialState = VersionPartialState.NetworkError(true)
}
