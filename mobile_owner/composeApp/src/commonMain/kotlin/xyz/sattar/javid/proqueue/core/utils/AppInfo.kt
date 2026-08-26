package xyz.sattar.javid.proqueue.core.utils

expect object AppInfo {
    val versionCode: Int
    val versionName: String
    val isIOS: Boolean

    /**
     * True only for the wasmJs build. Added alongside the web target so
     * [xyz.sattar.javid.proqueue.App] can skip [xyz.sattar.javid.proqueue.feature.version.VersionHandler]
     * on web the same way it already skips it on iOS — there is no app store
     * version gate on the web, and per docs/OWNER_WEB_PLAN.md section 4 (row
     * 10) that check must be fully disabled there, not just left pointed at
     * an Android-shaped endpoint.
     */
    val isWeb: Boolean
}
