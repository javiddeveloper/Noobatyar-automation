package xyz.sattar.javid.proqueue.core.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher

interface PermissionLauncher {
    fun launch()
}

// Only the web actual can distinguish "never asked" from "explicitly denied"
// (Notification.permission === 'denied', with no programmatic way back —
// the user must change it in browser site settings). Android/iOS actuals
// return false always: their own OS prompts already no-op safely once
// permanently denied, so there's nothing unsafe about still offering ours.
@Composable
expect fun rememberNotificationPermissionDenied(): Boolean
