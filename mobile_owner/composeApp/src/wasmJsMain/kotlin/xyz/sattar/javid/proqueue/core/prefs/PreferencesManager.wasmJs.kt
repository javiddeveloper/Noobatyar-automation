package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES

/**
 * Backed by `localStorage`, one key per SharedPreferences key the Android
 * actual uses — same names, same defaults, so a value set on one platform
 * reads the same way conceptually on the other (there is of course no
 * syncing between the two; see docs/OWNER_WEB_PLAN.md section 5 on why the
 * web build treats all local state as disposable).
 *
 * One default is deliberately different: [themeMode] falls back to
 * [AppThemeMode.DARK] instead of [AppThemeMode.SYSTEM] when nothing is
 * stored yet — docs/OWNER_WEB_PLAN.md section 7.2, "dark as the web
 * default". The user can still change it from SettingsScreen like normal;
 * only the unset-default differs.
 */
actual object PreferencesManager {
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEFAULT_BUSINESS_ID = "default_business_id"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_NOTIFICATION_REMINDER_MINUTES = "notification_reminder_minutes"
    private const val KEY_MESSAGE_TEMPLATE_PREFIX = "message_template_" // suffix: businessId

    private val _themeMode = MutableStateFlow(
        localStorage.getItem(KEY_THEME_MODE)?.toIntOrNull()
            ?.let { AppThemeMode.values().getOrNull(it) }
            ?: AppThemeMode.DARK
    )
    private val _defaultBusinessId = MutableStateFlow(
        localStorage.getItem(KEY_DEFAULT_BUSINESS_ID)?.toLongOrNull()
    )
    private val _notificationsEnabled = MutableStateFlow(
        localStorage.getItem(KEY_NOTIFICATIONS_ENABLED)?.toBoolean() ?: false
    )
    private val _notificationReminderMinutes = MutableStateFlow(
        localStorage.getItem(KEY_NOTIFICATION_REMINDER_MINUTES)?.toIntOrNull() ?: DEFAULT_REMINDER_MINUTES
    )

    actual val themeMode: Flow<AppThemeMode> = _themeMode
    actual val defaultBusinessId: Flow<Long?> = _defaultBusinessId
    actual val notificationsEnabled: Flow<Boolean> = _notificationsEnabled
    actual val notificationReminderMinutes: Flow<Int> = _notificationReminderMinutes
    private val templateFlows = mutableMapOf<Long, MutableStateFlow<String?>>()

    actual suspend fun setThemeMode(mode: AppThemeMode) {
        localStorage.setItem(KEY_THEME_MODE, mode.ordinal.toString())
        _themeMode.value = mode
    }

    actual suspend fun setDefaultBusinessId(id: Long?) {
        if (id == null) localStorage.removeItem(KEY_DEFAULT_BUSINESS_ID)
        else localStorage.setItem(KEY_DEFAULT_BUSINESS_ID, id.toString())
        _defaultBusinessId.value = id
    }

    actual suspend fun setNotificationsEnabled(enabled: Boolean) {
        localStorage.setItem(KEY_NOTIFICATIONS_ENABLED, enabled.toString())
        _notificationsEnabled.value = enabled
    }

    actual suspend fun setNotificationReminderMinutes(minutes: Int) {
        localStorage.setItem(KEY_NOTIFICATION_REMINDER_MINUTES, minutes.toString())
        _notificationReminderMinutes.value = minutes
    }

    actual fun messageTemplate(businessId: Long): Flow<String?> {
        return templateFlows.getOrPut(businessId) {
            val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
            MutableStateFlow(localStorage.getItem(key))
        }
    }

    actual suspend fun setMessageTemplate(businessId: Long, template: String) {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        localStorage.setItem(key, template)
        val flow = templateFlows.getOrPut(businessId) { MutableStateFlow(null) }
        flow.value = template
    }

    actual fun getMessageTemplate(businessId: Long): String? {
        return localStorage.getItem(KEY_MESSAGE_TEMPLATE_PREFIX + businessId)
    }

    actual fun getNotificationReminderMinutes(): Int {
        return localStorage.getItem(KEY_NOTIFICATION_REMINDER_MINUTES)?.toIntOrNull() ?: DEFAULT_REMINDER_MINUTES
    }
}
