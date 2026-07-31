package xyz.sattar.javid.proqueue.feature.settings

sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    /** Pull-to-refresh: re-fetch from the server, not just the cache. */
    data object RefreshSettings : SettingsIntent
    data object OnAboutClick : SettingsIntent
    data object OnChangeBusinessClick : SettingsIntent
    data class OnEditBusinessClick(val businessId: Long) : SettingsIntent
    data object OnDeleteBusinessClick : SettingsIntent
    data object OnNotificationsClick : SettingsIntent
    data object OnMessagesClick : SettingsIntent
}
