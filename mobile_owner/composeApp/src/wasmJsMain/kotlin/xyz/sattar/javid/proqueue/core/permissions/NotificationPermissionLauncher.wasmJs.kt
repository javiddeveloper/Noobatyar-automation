package xyz.sattar.javid.proqueue.core.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.js.ExperimentalWasmJsInterop
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

/**
 * Read-only check of `Notification.permission` — 'granted' / 'denied' /
 * 'default' (undecided) — with no side effect and no gesture requirement,
 * unlike [jsRequestNotificationPermission] which can pop the browser's
 * prompt. Used by [xyz.sattar.javid.proqueue.core.notifications.
 * WebNotificationScheduler.hasPermission] so the Notifications screen's
 * toggle reflects reality on load instead of always reporting false —
 * loading a settings screen must never itself trigger a permission prompt,
 * only report what's already decided.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    () => {
        try {
            return typeof Notification !== 'undefined' && Notification.permission === 'granted';
        } catch (e) {
            return false;
        }
    }
    """
)
internal external fun jsHasNotificationPermission(): Boolean

/**
 * True only when the browser has explicitly denied notifications
 * (`Notification.permission === 'denied'`) — a state with no programmatic
 * way back; only the user, via their own browser's site settings, can
 * undo it. Used to suppress the app-wide first-launch permission prompt in
 * that case instead of showing a "grant access" dialog whose button would
 * silently do nothing.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    () => {
        try {
            return typeof Notification !== 'undefined' && Notification.permission === 'denied';
        } catch (e) {
            return false;
        }
    }
    """
)
internal external fun jsNotificationPermissionDenied(): Boolean

@Composable
actual fun rememberNotificationPermissionDenied(): Boolean = jsNotificationPermissionDenied()
