package xyz.sattar.javid.proqueue.core.network

import kotlinx.browser.localStorage

// Mirrors the Android actual's SharedPreferences file ("auth_prefs") one key
// at a time with localStorage — see docs/OWNER_WEB_PLAN.md section 4, row 4.
// Uses Storage's own getItem/setItem/removeItem rather than the get/set
// operator extensions, whose import path has moved between Kotlin releases.
actual object TokenManager {
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    actual fun getAccessToken(): String? = localStorage.getItem(KEY_ACCESS_TOKEN)

    actual fun getRefreshToken(): String? = localStorage.getItem(KEY_REFRESH_TOKEN)

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        localStorage.setItem(KEY_ACCESS_TOKEN, accessToken)
        localStorage.setItem(KEY_REFRESH_TOKEN, refreshToken)
    }

    actual fun clearTokens() {
        localStorage.removeItem(KEY_ACCESS_TOKEN)
        localStorage.removeItem(KEY_REFRESH_TOKEN)
    }
}
