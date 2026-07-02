package xyz.sattar.javid.proqueue.feature.notifications

import androidx.compose.runtime.Immutable

@Immutable
data class NotificationsState(
    val isLoading: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val reminderMinutes: String = "10",
    val error: String? = null,
    val hasPermission: Boolean = false
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class NotificationsEnabledChanged(val enabled: Boolean) : PartialState()
        data class ReminderMinutesChanged(val minutes: String) : PartialState()
        data class Error(val message: String) : PartialState()
        data class PermissionStatusChanged(val hasPermission: Boolean) : PartialState()
    }
}

sealed class NotificationsIntent {
    object LoadSettings : NotificationsIntent()
    data class ToggleNotifications(val enabled: Boolean) : NotificationsIntent()
    data class UpdateReminderMinutes(val minutes: String) : NotificationsIntent()
    object SaveSettings : NotificationsIntent()
    data class PermissionResult(val isGranted: Boolean) : NotificationsIntent()
}

sealed class NotificationsEvent {
    object NavigateBack : NotificationsEvent()
    object ShowSavedConfirmation : NotificationsEvent()
    object RequestPermission : NotificationsEvent()
}
