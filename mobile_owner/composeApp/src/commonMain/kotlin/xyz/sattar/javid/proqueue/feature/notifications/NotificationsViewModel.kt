package xyz.sattar.javid.proqueue.feature.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.BusinessRepository

class NotificationsViewModel(
    private val notificationScheduler: NotificationScheduler,
    private val businessRepository: BusinessRepository
) :
    BaseViewModel<NotificationsState, NotificationsState.PartialState, NotificationsEvent, NotificationsIntent>(
        initialState = NotificationsState()
    ) {

    init {
        sendIntent(NotificationsIntent.LoadSettings)
    }

    override fun handleIntent(intent: NotificationsIntent): Flow<NotificationsState.PartialState> {
        return when (intent) {
            is NotificationsIntent.LoadSettings -> loadSettings()
            is NotificationsIntent.ToggleNotifications -> flow {
                if (intent.enabled) {
                    val hasPermission = notificationScheduler.hasPermission()
                    if (hasPermission) {
                        emit(NotificationsState.PartialState.NotificationsEnabledChanged(true))
                    } else {
                        sendEvent(NotificationsEvent.RequestPermission)
                    }
                } else {
                    emit(NotificationsState.PartialState.NotificationsEnabledChanged(false))
                }
            }

            is NotificationsIntent.UpdateReminderMinutes -> flow {
                if (intent.minutes.all { it.isDigit() }) {
                    emit(NotificationsState.PartialState.ReminderMinutesChanged(intent.minutes))
                }
            }

            is NotificationsIntent.SaveSettings -> saveSettings()
            is NotificationsIntent.PermissionResult -> flow {
                emit(NotificationsState.PartialState.PermissionStatusChanged(intent.isGranted))
                if (intent.isGranted) {
                    emit(NotificationsState.PartialState.NotificationsEnabledChanged(true))
                } else {
                    emit(NotificationsState.PartialState.NotificationsEnabledChanged(false))
                }
            }
        }
    }

    override fun reduceState(
        currentState: NotificationsState,
        partialState: NotificationsState.PartialState
    ): NotificationsState {
        return when (partialState) {
            is NotificationsState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)

            is NotificationsState.PartialState.NotificationsEnabledChanged ->
                currentState.copy(isNotificationsEnabled = partialState.enabled)

            is NotificationsState.PartialState.ReminderMinutesChanged ->
                currentState.copy(reminderMinutes = partialState.minutes)

            is NotificationsState.PartialState.Error ->
                currentState.copy(error = partialState.message, isLoading = false)

            is NotificationsState.PartialState.PermissionStatusChanged ->
                currentState.copy(hasPermission = partialState.hasPermission)
        }
    }

    override fun createErrorState(message: String): NotificationsState.PartialState =
        NotificationsState.PartialState.Error(message)

    private fun loadSettings(): Flow<NotificationsState.PartialState> = flow {
        emit(NotificationsState.PartialState.IsLoading(true))
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            val hasPermission = notificationScheduler.hasPermission()
            
            emit(NotificationsState.PartialState.PermissionStatusChanged(hasPermission))
            
            if (business != null) {
                emit(NotificationsState.PartialState.NotificationsEnabledChanged(business.notificationEnabled))
                emit(NotificationsState.PartialState.ReminderMinutesChanged(business.notificationMinutesBefore.toString()))
            } else {
                emit(NotificationsState.PartialState.Error("کسب و کاری انتخاب نشده است"))
            }
        } catch (e: Exception) {
            emit(NotificationsState.PartialState.Error(e.message ?: "خطا در بارگذاری تنظیمات"))
        } finally {
            emit(NotificationsState.PartialState.IsLoading(false))
        }
    }

    private fun saveSettings(): Flow<NotificationsState.PartialState> = flow {
        emit(NotificationsState.PartialState.IsLoading(true))
        try {
            val currentState = uiState.value
            val currentBusiness = BusinessStateHolder.selectedBusiness.value
            
            if (currentBusiness != null) {
                val minutes = currentState.reminderMinutes.toIntOrNull() ?: 30
                val updatedBusiness = currentBusiness.copy(
                    notificationEnabled = currentState.isNotificationsEnabled,
                    notificationMinutesBefore = minutes
                )
                
                val success = businessRepository.upsertBusiness(updatedBusiness)
                if (success) {
                    BusinessStateHolder.selectBusiness(updatedBusiness)
                    sendEvent(NotificationsEvent.ShowSavedConfirmation)
                } else {
                    emit(NotificationsState.PartialState.Error("خطا در ذخیره تنظیمات"))
                }
            } else {
                emit(NotificationsState.PartialState.Error("کسب و کاری انتخاب نشده است"))
            }
        } catch (e: Exception) {
            emit(NotificationsState.PartialState.Error(e.message ?: "خطا در ذخیره تنظیمات"))
        } finally {
            emit(NotificationsState.PartialState.IsLoading(false))
        }
    }
}
