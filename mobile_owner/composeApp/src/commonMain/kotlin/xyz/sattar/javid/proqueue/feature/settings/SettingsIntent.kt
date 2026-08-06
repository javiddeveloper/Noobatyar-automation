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
    /** Dispatched right after a toast is shown so it can't be re-shown/deduped. */
    data object ClearMessage : SettingsIntent
    data object OnSmsReportClick : SettingsIntent
    data object OnEmergencyNoticeClick : SettingsIntent

    // --- Emergency notice (public banner on the booking page) ---
    data class UpdateNoticeEnabled(val enabled: Boolean) : SettingsIntent
    data class UpdateNoticeMessage(val message: String) : SettingsIntent
    data object SaveNotice : SettingsIntent
}
