package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.coroutines.flow.Flow

import xyz.sattar.javid.proqueue.core.state.AppThemeMode

expect object PreferencesManager {
    val themeMode: Flow<AppThemeMode>
    val defaultBusinessId: Flow<Long?>
    val notificationsEnabled: Flow<Boolean>
    val notificationReminderMinutes: Flow<Int>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDefaultBusinessId(id: Long?)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotificationReminderMinutes(minutes: Int)
    fun messageTemplate(businessId: Long): Flow<String?>
    suspend fun setMessageTemplate(businessId: Long, template: String)
    fun getMessageTemplate(businessId: Long): String?
    fun getNotificationReminderMinutes(): Int
}
