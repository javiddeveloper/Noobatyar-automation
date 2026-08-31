package xyz.sattar.javid.proqueue.feature.notifications

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
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
    val canUsePanelDelivery: Boolean = false,
    /** Raw entitlements/plans, passed straight into [xyz.sattar.javid.proqueue.core.ui.components.FeatureGate]
     * to lock the whole card when the plan doesn't include `push_notifications`. */
    val entitlements: EntitlementsResponseDto? = null,
    val plans: List<PlanDto> = emptyList()
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
        data class EntitlementsLoaded(val entitlements: EntitlementsResponseDto) : PartialState()
        data class PlansLoaded(val plans: List<PlanDto>) : PartialState()
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
    /** "ارتقا به X" tapped on the locked push-notifications card. */
    data class UpgradePlan(val planId: Int) : NotificationsIntent()
}

sealed class NotificationsEvent {
    object NavigateBack : NotificationsEvent()
    object ShowSavedConfirmation : NotificationsEvent()
    object RequestPermission : NotificationsEvent()
    data class ShowError(val message: String) : NotificationsEvent()
    data class OpenUrl(val url: String) : NotificationsEvent()
}
