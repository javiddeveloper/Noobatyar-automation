package xyz.sattar.javid.proqueue.feature.notifications

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery

@Immutable
data class NotificationsState(
    val isLoading: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val reminderMinutes: String = DEFAULT_REMINDER_MINUTES.toString(),
    val error: String? = null,
    val hasPermission: Boolean = false,
    /** Whether the *client* is reminded, on top of the owner's own reminder. */
    val remindClient: Boolean = false,
    /**
     * [ReminderDelivery] value. PANEL is what makes the server text the client
     * automatically; MANUAL leaves the sending to the owner's own SIM.
     */
    val reminderDelivery: String = ReminderDelivery.MANUAL.value,
    /** False when the plan doesn't include `auto_reminder_sms`. */
    val canUsePanelDelivery: Boolean = false
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class NotificationsEnabledChanged(val enabled: Boolean) : PartialState()
        data class ReminderMinutesChanged(val minutes: String) : PartialState()
        data class Error(val message: String) : PartialState()
        data class PermissionStatusChanged(val hasPermission: Boolean) : PartialState()
        data class RemindClientChanged(val enabled: Boolean) : PartialState()
        data class DeliveryChanged(val delivery: String) : PartialState()
        data class PanelAllowedChanged(val allowed: Boolean) : PartialState()
    }
}

sealed class NotificationsIntent {
    object LoadSettings : NotificationsIntent()
    data class ToggleNotifications(val enabled: Boolean) : NotificationsIntent()
    data class UpdateReminderMinutes(val minutes: String) : NotificationsIntent()
    object SaveSettings : NotificationsIntent()
    data class PermissionResult(val isGranted: Boolean) : NotificationsIntent()
    data class ToggleRemindClient(val enabled: Boolean) : NotificationsIntent()
    data class SetDelivery(val delivery: String) : NotificationsIntent()
}

sealed class NotificationsEvent {
    object NavigateBack : NotificationsEvent()
    object ShowSavedConfirmation : NotificationsEvent()
    object RequestPermission : NotificationsEvent()
    data class ShowError(val message: String) : NotificationsEvent()
}
