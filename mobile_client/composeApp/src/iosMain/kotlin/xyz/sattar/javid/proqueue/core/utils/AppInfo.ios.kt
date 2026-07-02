package xyz.sattar.javid.proqueue.core.utils

import platform.Foundation.NSBundle

actual object AppInfo {
    actual val versionCode: Int = (NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 100
    actual val versionName: String = (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "1.0.0"
}
