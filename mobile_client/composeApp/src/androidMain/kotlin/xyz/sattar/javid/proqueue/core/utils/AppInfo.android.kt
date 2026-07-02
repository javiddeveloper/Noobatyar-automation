package xyz.sattar.javid.proqueue.core.utils

import xyz.sattar.javid.proqueue.BuildConfig

actual object AppInfo {
    actual val versionCode: Int = BuildConfig.VERSION_CODE
    actual val versionName: String = BuildConfig.VERSION_NAME
}
