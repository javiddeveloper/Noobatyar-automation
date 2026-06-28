package xyz.sattar.javid.proqueue.feature.settings

sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    data object OnAboutClick : SettingsIntent
    data object OnChangeBusinessClick : SettingsIntent
    data object OnDeleteBusinessClick : SettingsIntent
    data object OnNotificationsClick : SettingsIntent
    data object OnMessagesClick : SettingsIntent
}
