package xyz.sattar.javid.proqueue.core.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Web push (and the browser Notification permission prompt it would need) is
// out of scope for this MVP — docs/OWNER_WEB_PLAN.md section 10.1 is a
// separate phase. This still has to be real, callable code rather than a
// TODO()/crash: some shared code path (SettingsScreen's notification toggle)
// can reach it during ordinary use, and reporting "not granted" is the
// correct, honest answer for a build with no push wiring yet.
@Composable
actual fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    return remember {
        object : PermissionLauncher {
            override fun launch() {
                onResult(false)
            }
        }
    }
}
