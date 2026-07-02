package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.*

import xyz.sattar.javid.proqueue.core.state.AppThemeMode

actual object PreferencesManager {
    private val defaults = NSUserDefaults.standardUserDefaults

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEFAULT_BUSINESS_ID = "default_business_id"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_NOTIFICATION_REMINDER_MINUTES = "notification_reminder_minutes"
    private const val KEY_MESSAGE_TEMPLATE_PREFIX = "message_template_" // suffix: businessId

    private val _themeMode = MutableStateFlow(
        AppThemeMode.values()[defaults.integerForKey(KEY_THEME_MODE).toInt().coerceIn(0, 2)]
    )
    private fun readDefaultBusinessId(): Long? = if (defaults.objectForKey(KEY_DEFAULT_BUSINESS_ID) != null) {
        defaults.integerForKey(KEY_DEFAULT_BUSINESS_ID)
    } else null
    private val _defaultBusinessId = MutableStateFlow(readDefaultBusinessId())
    
    private val _notificationsEnabled = MutableStateFlow(
        defaults.boolForKey(KEY_NOTIFICATIONS_ENABLED)
    )
    
    private val _notificationReminderMinutes = MutableStateFlow(
        if (defaults.objectForKey(KEY_NOTIFICATION_REMINDER_MINUTES) != null) 
            defaults.integerForKey(KEY_NOTIFICATION_REMINDER_MINUTES).toInt() 
        else 10
    )

    actual val themeMode: Flow<AppThemeMode> = _themeMode
    actual val defaultBusinessId: Flow<Long?> = _defaultBusinessId
    actual val notificationsEnabled: Flow<Boolean> = _notificationsEnabled
    actual val notificationReminderMinutes: Flow<Int> = _notificationReminderMinutes
    private val templateFlows = mutableMapOf<Long, MutableStateFlow<String?>>()

    actual suspend fun setThemeMode(mode: AppThemeMode) {
        defaults.setInteger(mode.ordinal.toLong(), KEY_THEME_MODE)
        _themeMode.value = mode
    }

    actual suspend fun setDefaultBusinessId(id: Long?) {
        if (id == null) {
            defaults.removeObjectForKey(KEY_DEFAULT_BUSINESS_ID)
            _defaultBusinessId.value = null
        } else {
            defaults.setInteger(id, KEY_DEFAULT_BUSINESS_ID)
            _defaultBusinessId.value = id
        }
    }

    actual suspend fun setNotificationsEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_NOTIFICATIONS_ENABLED)
        _notificationsEnabled.value = enabled
    }

    actual suspend fun setNotificationReminderMinutes(minutes: Int) {
        defaults.setInteger(minutes.toLong(), KEY_NOTIFICATION_REMINDER_MINUTES)
        _notificationReminderMinutes.value = minutes
    }

    actual fun messageTemplate(businessId: Long): Flow<String?> {
        return templateFlows.getOrPut(businessId) {
            val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
            val value = defaults.stringForKey(key)
            MutableStateFlow(if (value != null) value else null)
        }
    }

    actual suspend fun setMessageTemplate(businessId: Long, template: String) {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        defaults.setObject(template, key)
        val flow = templateFlows.getOrPut(businessId) { MutableStateFlow(null) }
        flow.value = template
    }

    actual fun getMessageTemplate(businessId: Long): String? {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        return defaults.stringForKey(key)
    }

    actual fun getNotificationReminderMinutes(): Int {
        val value = defaults.integerForKey(KEY_NOTIFICATION_REMINDER_MINUTES)
        return if (value == 0L) 20 else value.toInt()
    }
}
