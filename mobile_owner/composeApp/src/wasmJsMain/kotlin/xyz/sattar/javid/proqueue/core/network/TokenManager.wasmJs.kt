package xyz.sattar.javid.proqueue.core.network

import kotlinx.browser.localStorage

// Mirrors the Android actual's SharedPreferences file ("auth_prefs") one key
// at a time with localStorage — see docs/OWNER_WEB_PLAN.md section 4, row 4.
// Uses Storage's own getItem/setItem/removeItem rather than the get/set
// operator extensions, whose import path has moved between Kotlin releases.
actual object TokenManager {
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    actual fun getAccessToken(): String? = safeGet(KEY_ACCESS_TOKEN)

    actual fun getRefreshToken(): String? = safeGet(KEY_REFRESH_TOKEN)

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        safeSet(KEY_ACCESS_TOKEN, accessToken)
        safeSet(KEY_REFRESH_TOKEN, refreshToken)
    }

    actual fun clearTokens() {
        safeRemove(KEY_ACCESS_TOKEN)
        safeRemove(KEY_REFRESH_TOKEN)
    }

    // getAccessToken runs on every outgoing request (AuthInterceptor). In a
    // browser configuration where localStorage throws (Safari private mode,
    // storage disabled, quota exceeded) an unguarded call here would crash
    // every request instead of just failing auth — treat it as "no token"
    // and let the app fall through to its normal signed-out handling.
    private fun safeGet(key: String): String? =
        try {
            localStorage.getItem(key)
        } catch (e: Throwable) {
            null
        }

    private fun safeSet(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (e: Throwable) {
            // Can't persist the session in this browser configuration; the
            // in-memory app state still works for the current tab.
        }
    }

    private fun safeRemove(key: String) {
        try {
            localStorage.removeItem(key)
        } catch (e: Throwable) {
            // No-op: nothing to clear if storage was never writable.
        }
    }
}
