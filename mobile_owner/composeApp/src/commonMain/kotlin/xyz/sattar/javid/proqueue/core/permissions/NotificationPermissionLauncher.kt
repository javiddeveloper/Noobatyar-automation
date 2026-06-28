package xyz.sattar.javid.proqueue.core.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher

interface PermissionLauncher {
    fun launch()
}
