package xyz.sattar.javid.proqueue.core.network
import android.content.Context
import android.content.SharedPreferences
import xyz.sattar.javid.proqueue.ProQueueApp

actual object TokenManager {
    private val prefs: SharedPreferences by lazy {
        ProQueueApp.appContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    }

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    actual fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    actual fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            commit()
        }
    }

    actual fun clearTokens() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            commit()
        }
    }
}
