package xyz.sattar.javid.proqueue.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.DeleteBusinessUseCase

class SettingsViewModel(
    private val deleteBusinessUseCase: DeleteBusinessUseCase,
    private val businessRepository: xyz.sattar.javid.proqueue.domain.BusinessRepository
) : BaseViewModel<SettingsState, SettingsState.PartialState, SettingsEvent, SettingsIntent>(
    initialState = SettingsState()
) {
    init {
        viewModelScope.launch {
            BusinessStateHolder.selectedBusiness.collectLatest {
                sendIntent(SettingsIntent.LoadSettings)
            }
        }
    }

    override fun handleIntent(intent: SettingsIntent): Flow<SettingsState.PartialState> {
        return when (intent) {
            SettingsIntent.LoadSettings -> loadSettings()
            SettingsIntent.RefreshSettings -> refreshSettings()
            SettingsIntent.OnAboutClick -> sendEvent(SettingsEvent.NavigateToAbout)
            SettingsIntent.OnChangeBusinessClick -> sendEvent(SettingsEvent.NavigateToBusinessSelection)
            is SettingsIntent.OnEditBusinessClick -> sendEvent(SettingsEvent.NavigateToEditBusiness(intent.businessId))
            SettingsIntent.OnDeleteBusinessClick -> deleteBusiness()
            SettingsIntent.OnNotificationsClick -> sendEvent(SettingsEvent.NavigateToNotifications)
            SettingsIntent.OnMessagesClick -> sendEvent(SettingsEvent.NavigateToMessages)
        }
    }

    private fun deleteBusiness(): Flow<SettingsState.PartialState> = flow {
        val currentBusiness = BusinessStateHolder.selectedBusiness.value
        if (currentBusiness != null) {
            emit(SettingsState.PartialState.IsLoading(true))
            try {
                deleteBusinessUseCase(currentBusiness.id)
                sendEvent(SettingsEvent.BusinessDeleted)
            } catch (e: Exception) {
                emit(SettingsState.PartialState.ShowMessage(e.message ?: "Error deleting business"))
            } finally {
                emit(SettingsState.PartialState.IsLoading(false))
            }
        }
    }

    override fun reduceState(
        currentState: SettingsState,
        partialState: SettingsState.PartialState
    ): SettingsState {
        return when (partialState) {
            is SettingsState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            is SettingsState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)
            is SettingsState.PartialState.LoadSettings ->
                currentState.copy(
                    businessName = partialState.business?.title,
                    currentBusiness = partialState.business,
                    isLoading = false
                )
        }
    }

    override fun createErrorState(message: String): SettingsState.PartialState =
        SettingsState.PartialState.ShowMessage(message)

    private fun loadSettings(): Flow<SettingsState.PartialState> = flow {
        emit(SettingsState.PartialState.IsLoading(true))
        val currentBusiness = BusinessStateHolder.selectedBusiness.value
        emit(SettingsState.PartialState.LoadSettings(currentBusiness))
    }

    /**
     * Pull-to-refresh on the profile screen. Unlike [loadSettings], which only
     * re-reads the cached selection, this re-fetches the businesses from the
     * server so edits made elsewhere (or by the backend) actually show up.
     */
    private fun refreshSettings(): Flow<SettingsState.PartialState> = flow {
        emit(SettingsState.PartialState.IsLoading(true))
        try {
            businessRepository.fetchAndCacheBusinesses(page = 1, pageSize = 20)
            val selectedId = BusinessStateHolder.selectedBusiness.value?.id
            val refreshed = selectedId?.let { businessRepository.getBusinessById(it) }
            if (refreshed != null) {
                // Keep the rest of the app (top bar, home) on the fresh copy too.
                BusinessStateHolder.selectBusiness(refreshed)
            }
            emit(
                SettingsState.PartialState.LoadSettings(
                    refreshed ?: BusinessStateHolder.selectedBusiness.value
                )
            )
        } catch (e: Exception) {
            emit(SettingsState.PartialState.LoadSettings(BusinessStateHolder.selectedBusiness.value))
        }
    }
}
