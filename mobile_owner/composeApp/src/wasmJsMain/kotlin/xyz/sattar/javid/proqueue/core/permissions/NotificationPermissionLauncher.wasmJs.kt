package xyz.sattar.javid.proqueue.core.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import xyz.sattar.javid.proqueue.core.push.jsRequestNotificationPermission

// Web push — docs/OWNER_WEB_PLAN.md section 10.1. [launch] is called
// synchronously from NotificationsScreen's toggle click, so this is still
// inside that click's call stack when it reaches
// `Notification.requestPermission()` — required, since browsers reject that
// call outside a real user gesture. jsRequestNotificationPermission (see
// core/push/FirebaseMessagingInterop.kt) already wraps the browser call in
// try/catch and reports `false` for anything that isn't an explicit grant
// (unsupported API, denied, dismissed), so [onResult] always fires exactly
// once and never throws back into Compose.
@Composable
actual fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    return remember {
        object : PermissionLauncher {
            override fun launch() {
                jsRequestNotificationPermission { granted -> onResult(granted) }
            }
        }
    }
}
