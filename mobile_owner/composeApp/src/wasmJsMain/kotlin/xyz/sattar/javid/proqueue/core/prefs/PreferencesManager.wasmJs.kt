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

    // These property initializers run at first access to this object, which
    // for _themeMode is during app startup (App() reads it to theme the very
    // first composition). In a browser configuration where localStorage
    // throws (Safari private mode, storage disabled) an unguarded getItem
    // here would throw before ComposeViewport ever renders anything —
    // safeGet swallows that and falls back to the same defaults as "nothing
    // stored yet".
    private val _themeMode = MutableStateFlow(
        safeGet(KEY_THEME_MODE)?.toIntOrNull()
            ?.let { AppThemeMode.values().getOrNull(it) }
            ?: AppThemeMode.DARK
    )
    private val _defaultBusinessId = MutableStateFlow(
        safeGet(KEY_DEFAULT_BUSINESS_ID)?.toLongOrNull()
    )
    private val _notificationsEnabled = MutableStateFlow(
        safeGet(KEY_NOTIFICATIONS_ENABLED)?.toBoolean() ?: false
    )
    private val _notificationReminderMinutes = MutableStateFlow(
        safeGet(KEY_NOTIFICATION_REMINDER_MINUTES)?.toIntOrNull() ?: DEFAULT_REMINDER_MINUTES
    )

    actual val themeMode: Flow<AppThemeMode> = _themeMode
    actual val defaultBusinessId: Flow<Long?> = _defaultBusinessId
    actual val notificationsEnabled: Flow<Boolean> = _notificationsEnabled
    actual val notificationReminderMinutes: Flow<Int> = _notificationReminderMinutes
    private val templateFlows = mutableMapOf<Long, MutableStateFlow<String?>>()

    actual suspend fun setThemeMode(mode: AppThemeMode) {
        safeSet(KEY_THEME_MODE, mode.ordinal.toString())
        _themeMode.value = mode
    }

    actual suspend fun setDefaultBusinessId(id: Long?) {
        if (id == null) safeRemove(KEY_DEFAULT_BUSINESS_ID)
        else safeSet(KEY_DEFAULT_BUSINESS_ID, id.toString())
        _defaultBusinessId.value = id
    }

    actual suspend fun setNotificationsEnabled(enabled: Boolean) {
        safeSet(KEY_NOTIFICATIONS_ENABLED, enabled.toString())
        _notificationsEnabled.value = enabled
    }

    actual suspend fun setNotificationReminderMinutes(minutes: Int) {
        safeSet(KEY_NOTIFICATION_REMINDER_MINUTES, minutes.toString())
        _notificationReminderMinutes.value = minutes
    }

    actual fun messageTemplate(businessId: Long): Flow<String?> {
        return templateFlows.getOrPut(businessId) {
            val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
            MutableStateFlow(safeGet(key))
        }
    }

    actual suspend fun setMessageTemplate(businessId: Long, template: String) {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        safeSet(key, template)
        val flow = templateFlows.getOrPut(businessId) { MutableStateFlow(null) }
        flow.value = template
    }

    actual fun getMessageTemplate(businessId: Long): String? {
        return safeGet(KEY_MESSAGE_TEMPLATE_PREFIX + businessId)
    }

    actual fun getNotificationReminderMinutes(): Int {
        return safeGet(KEY_NOTIFICATION_REMINDER_MINUTES)?.toIntOrNull() ?: DEFAULT_REMINDER_MINUTES
    }

    private fun safeGet(key: String): String? =
        try {
            localStorage.getItem(key)
        } catch (e: Throwable) {
            null
        }

    private fun safeSet(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (e: Throwable) {
            // Can't persist in this browser configuration; the in-memory
            // MutableStateFlow above still keeps the setting for this tab.
        }
    }

    private fun safeRemove(key: String) {
        try {
            localStorage.removeItem(key)
        } catch (e: Throwable) {
            // No-op: nothing to clear if storage was never writable.
        }
    }
}
