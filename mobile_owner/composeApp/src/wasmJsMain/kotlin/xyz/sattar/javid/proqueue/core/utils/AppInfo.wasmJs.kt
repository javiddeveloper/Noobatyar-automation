package xyz.sattar.javid.proqueue.core.utils

// There is no app-store version concept on the web — the panel is always
// whatever was last deployed, which is also why App.kt already skips
// VersionHandler when isIOS is true and will need the same skip for web once
// this ships (docs/OWNER_WEB_PLAN.md section 4, row 10). versionCode/Name are
// fixed placeholders rather than wired to anything real; nothing reads them
// today outside that gate.
actual object AppInfo {
    actual val versionCode: Int = 1
    actual val versionName: String = "web"
    actual val isIOS: Boolean = false
    actual val isWeb: Boolean = true
}
