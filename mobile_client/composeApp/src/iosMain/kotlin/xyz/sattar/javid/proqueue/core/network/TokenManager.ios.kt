package xyz.sattar.javid.proqueue.core.network

import platform.Foundation.NSUserDefaults

actual object TokenManager {
    private val defaults = NSUserDefaults.standardUserDefaults

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    actual fun getAccessToken(): String? = defaults.stringForKey(KEY_ACCESS_TOKEN)

    actual fun getRefreshToken(): String? = defaults.stringForKey(KEY_REFRESH_TOKEN)

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        defaults.setObject(accessToken, KEY_ACCESS_TOKEN)
        defaults.setObject(refreshToken, KEY_REFRESH_TOKEN)
    }

    actual fun clearTokens() {
        defaults.removeObjectForKey(KEY_ACCESS_TOKEN)
        defaults.removeObjectForKey(KEY_REFRESH_TOKEN)
    }
}
