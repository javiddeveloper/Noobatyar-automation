package xyz.sattar.javid.proqueue.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.NOTICE_MESSAGE_MAX_LENGTH
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.DeleteBusinessUseCase

class SettingsViewModel(
    private val deleteBusinessUseCase: DeleteBusinessUseCase,
    private val businessRepository: xyz.sattar.javid.proqueue.domain.BusinessRepository,
    private val businessUpsertUseCase: BusinessUpsertUseCase
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
            SettingsIntent.OnSmsReportClick -> sendEvent(SettingsEvent.NavigateToSmsReport)
            SettingsIntent.OnEmergencyNoticeClick -> sendEvent(SettingsEvent.NavigateToEmergencyNotice)
            is SettingsIntent.UpdateNoticeEnabled ->
                flow { emit(SettingsState.PartialState.SetNoticeEnabled(intent.enabled)) }

            is SettingsIntent.UpdateNoticeMessage -> flow {
                emit(
                    SettingsState.PartialState.SetNoticeMessage(
                        intent.message.take(NOTICE_MESSAGE_MAX_LENGTH)
                    )
                )
            }

            SettingsIntent.SaveNotice -> saveNotice()
        }
    }

    /**
     * Pushes the notice draft to the server. The whole business is sent (the API
     * is a full update), so the copy is taken from the currently selected
     * business rather than from anything this screen holds.
     */
    private fun saveNotice(): Flow<SettingsState.PartialState> = flow {
        val business = BusinessStateHolder.selectedBusiness.value
        if (business == null) {
            emit(SettingsState.PartialState.ShowMessage("کسب‌وکار انتخاب نشده"))
            return@flow
        }
        emit(SettingsState.PartialState.IsSavingNotice(true))
        try {
            val updated = businessUpsertUseCase(
                business.copy(
                    noticeEnabled = uiState.value.noticeEnabled,
                    noticeMessage = uiState.value.noticeMessage.trim()
                )
            )
            if (updated != null) {
                BusinessStateHolder.selectBusiness(updated)
                emit(SettingsState.PartialState.LoadSettings(updated))
                emit(SettingsState.PartialState.ShowMessage("پیام اضطراری ذخیره شد"))
            } else {
                emit(SettingsState.PartialState.ShowMessage("خطا در ذخیره پیام اضطراری"))
            }
        } catch (e: Exception) {
            emit(SettingsState.PartialState.ShowMessage(e.message ?: "خطا در ذخیره پیام اضطراری"))
        } finally {
            emit(SettingsState.PartialState.IsSavingNotice(false))
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
                    isLoading = false,
                    // The draft always starts from what the server has; anything
                    // typed and not saved is discarded when the business reloads.
                    noticeEnabled = partialState.business?.noticeEnabled ?: false,
                    noticeMessage = partialState.business?.noticeMessage ?: ""
                )

            is SettingsState.PartialState.SetNoticeEnabled ->
                currentState.copy(noticeEnabled = partialState.enabled)

            is SettingsState.PartialState.SetNoticeMessage ->
                currentState.copy(noticeMessage = partialState.message)

            is SettingsState.PartialState.IsSavingNotice ->
                currentState.copy(isSavingNotice = partialState.saving)
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
