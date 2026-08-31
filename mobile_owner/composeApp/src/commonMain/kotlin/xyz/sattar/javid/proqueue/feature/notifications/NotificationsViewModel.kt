package xyz.sattar.javid.proqueue.feature.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiException
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase

class NotificationsViewModel(
    private val notificationScheduler: NotificationScheduler,
    private val businessRepository: BusinessRepository,
    private val getMyEntitlementsUseCase: GetMyEntitlementsUseCase,
    private val getPlansUseCase: GetPlansUseCase,
    private val createPaymentUseCase: CreatePaymentUseCase
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

            is NotificationsIntent.ToggleRemindClient -> flow {
                emit(NotificationsState.PartialState.RemindClientChanged(intent.enabled))
            }

            // PANEL is refused locally when the plan does not cover it, so the
            // owner never reaches a state the server would answer with a 403.
            is NotificationsIntent.SetDelivery -> flow {
                if (intent.delivery == ReminderDelivery.PANEL.value && !uiState.value.canUsePanelDelivery) {
                    emit(NotificationsState.PartialState.Error("ارسال خودکار یادآوری در پلن فعلی شما نیست"))
                    return@flow
                }
                emit(NotificationsState.PartialState.DeliveryChanged(intent.delivery))
            }

            is NotificationsIntent.SaveSettings -> saveSettings()
            is NotificationsIntent.UpgradePlan -> upgradePlan(intent.planId)
            is NotificationsIntent.PermissionResult -> flow {
                emit(NotificationsState.PartialState.PermissionStatusChanged(intent.isGranted))
                if (intent.isGranted) {
                    emit(NotificationsState.PartialState.NotificationsEnabledChanged(true))
                } else {
                    emit(NotificationsState.PartialState.NotificationsEnabledChanged(false))
                    // Denied silently left the toggle looking broken — it just
                    // snapped back off with no explanation. Once denied, no
                    // website/app can re-trigger the OS/browser prompt; the
                    // owner has to flip it in their own browser/system
                    // settings, so at least tell them that instead of nothing.
                    sendEvent(
                        NotificationsEvent.ShowError(
                            "اجازه ارسال اعلان داده نشد. برای فعال‌سازی، دسترسی اعلان این برنامه را از تنظیمات مرورگر یا سیستم خود فعال کنید."
                        )
                    )
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

            is NotificationsState.PartialState.RemindClientChanged ->
                currentState.copy(remindClient = partialState.enabled)

            is NotificationsState.PartialState.DeliveryChanged ->
                currentState.copy(reminderDelivery = partialState.delivery)

            is NotificationsState.PartialState.PanelAllowedChanged ->
                currentState.copy(canUsePanelDelivery = partialState.allowed)

            is NotificationsState.PartialState.EntitlementsLoaded ->
                currentState.copy(entitlements = partialState.entitlements)

            is NotificationsState.PartialState.PlansLoaded ->
                currentState.copy(plans = partialState.plans)
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
                // Businesses created before the lead time had a real default
                // carry 0, which would fire the reminder at the appointment
                // itself. Show the default instead of that.
                val minutes = business.notificationMinutesBefore
                    .takeIf { it > 0 } ?: DEFAULT_REMINDER_MINUTES
                emit(NotificationsState.PartialState.ReminderMinutesChanged(minutes.toString()))
                emit(NotificationsState.PartialState.RemindClientChanged(business.enableReminderSms))
                emit(NotificationsState.PartialState.DeliveryChanged(business.reminderDelivery))
                emit(NotificationsState.PartialState.PanelAllowedChanged(loadPanelEntitlement()))
                emitAll(loadPushFeatureGate())
            } else {
                emit(NotificationsState.PartialState.Error("کسب و کاری انتخاب نشده است"))
            }
        } catch (e: Exception) {
            emit(NotificationsState.PartialState.Error(e.message ?: "خطا در بارگذاری تنظیمات"))
        } finally {
            emit(NotificationsState.PartialState.IsLoading(false))
        }
    }

    /**
     * Whether the plan covers server-side reminders. A failed entitlements call
     * leaves PANEL locked rather than optimistically unlocked — offering it and
     * then eating a 403 on save is the worse of the two failures. Same rule as
     * [xyz.sattar.javid.proqueue.feature.messages.MessagesViewModel].
     */
    private suspend fun loadPanelEntitlement(): Boolean = try {
        when (val response = getMyEntitlementsUseCase()) {
            is ApiResponse.Success -> response.data.hasFeature(EntitlementKeys.AUTO_REMINDER_SMS)
            is ApiResponse.Error -> false
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Raw entitlements + plan list for [xyz.sattar.javid.proqueue.core.ui.components.FeatureGate]
     * to lock the whole "فعال‌سازی اعلان‌ها" card behind push_notifications.
     * Best-effort like loadPanelEntitlement — a failed call here leaves the
     * card in its locked default (FeatureGate treats a null/missing
     * entitlements as not-unlocked), never optimistically open.
     */
    private fun loadPushFeatureGate(): Flow<NotificationsState.PartialState> = flow {
        try {
            when (val response = getMyEntitlementsUseCase()) {
                is ApiResponse.Success -> emit(NotificationsState.PartialState.EntitlementsLoaded(response.data))
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {
        }
        try {
            when (val response = getPlansUseCase()) {
                is ApiResponse.Success -> emit(NotificationsState.PartialState.PlansLoaded(response.data))
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {
        }
    }

    private fun upgradePlan(planId: Int): Flow<NotificationsState.PartialState> = flow {
        try {
            when (val response = createPaymentUseCase(planId)) {
                is ApiResponse.Success -> sendEvent(NotificationsEvent.OpenUrl(response.data.paymentUrl))
                is ApiResponse.Error -> emit(NotificationsState.PartialState.Error(response.message))
            }
        } catch (e: Exception) {
            emit(NotificationsState.PartialState.Error(e.message ?: "خطا در برقراری ارتباط"))
        }
    }

    private fun saveSettings(): Flow<NotificationsState.PartialState> = flow {
        emit(NotificationsState.PartialState.IsLoading(true))
        try {
            val currentState = uiState.value
            val currentBusiness = BusinessStateHolder.selectedBusiness.value

            if (currentBusiness == null) {
                emit(NotificationsState.PartialState.Error("کسب و کاری انتخاب نشده است"))
                return@flow
            }

            val minutes = currentState.reminderMinutes.toIntOrNull()
                ?.takeIf { it > 0 } ?: DEFAULT_REMINDER_MINUTES
            // Falling back to MANUAL when the plan lost the entitlement keeps
            // the saved value and the offered value in agreement.
            val delivery = if (
                currentState.reminderDelivery == ReminderDelivery.PANEL.value &&
                !currentState.canUsePanelDelivery
            ) ReminderDelivery.MANUAL.value else currentState.reminderDelivery

            val updatedBusiness = currentBusiness.copy(
                notificationEnabled = currentState.isNotificationsEnabled,
                notificationMinutesBefore = minutes,
                enableReminderSms = currentState.remindClient,
                reminderDelivery = delivery
            )

            val success = businessRepository.upsertBusiness(updatedBusiness)
            if (success) {
                BusinessStateHolder.selectBusiness(updatedBusiness)
                // The lead time also drives the {minutes} token in the message
                // templates, which reads it back out of preferences.
                PreferencesManager.setNotificationReminderMinutes(minutes)
                emit(NotificationsState.PartialState.ReminderMinutesChanged(minutes.toString()))
                emit(NotificationsState.PartialState.DeliveryChanged(delivery))
                sendEvent(NotificationsEvent.ShowSavedConfirmation)
            } else {
                emit(NotificationsState.PartialState.Error("خطا در ذخیره تنظیمات"))
            }
        } catch (e: ApiException) {
            // 403 = the server disagrees with what we believed about the plan
            // (expired mid-session, say). Drop back to the mode that always
            // works instead of leaving the UI claiming PANEL is on.
            if (e.code == 403) {
                emit(NotificationsState.PartialState.DeliveryChanged(ReminderDelivery.MANUAL.value))
                emit(NotificationsState.PartialState.PanelAllowedChanged(false))
                emit(NotificationsState.PartialState.Error("پلن فعلی شما ارسال خودکار یادآوری را پوشش نمی‌دهد"))
            } else {
                emit(NotificationsState.PartialState.Error(e.message ?: "خطا در ذخیره تنظیمات"))
            }
        } catch (e: Exception) {
            emit(NotificationsState.PartialState.Error(e.message ?: "خطا در ذخیره تنظیمات"))
        } finally {
            emit(NotificationsState.PartialState.IsLoading(false))
        }
    }
}
