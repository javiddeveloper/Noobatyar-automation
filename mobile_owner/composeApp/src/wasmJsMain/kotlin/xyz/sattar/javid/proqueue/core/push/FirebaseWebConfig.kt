package xyz.sattar.javid.proqueue.core.push

/**
 * Firebase Web config for FCM push on the owner web panel — see
 * docs/OWNER_WEB_PLAN.md section 10.1 and docs/FCM_SETUP.md.
 *
 * `API_KEY` / `PROJECT_ID` / `STORAGE_BUCKET` / `MESSAGING_SENDER_ID` come
 * straight out of composeApp/google-services.json (the project's `api_key`,
 * `project_id`, `storage_bucket`, and `project_number`) — that file only has
 * the two Android clients registered so far, nothing here is invented.
 * `AUTH_DOMAIN` follows Firebase's fixed `<project-id>.firebaseapp.com`
 * convention, so it doesn't need its own console entry.
 *
 * [WEB_APP_ID] and [FCM_WEB_VAPID_KEY] are not in google-services.json —
 * that file only covers the two Android clients. They come from the Web app
 * registered separately in the same Firebase console project.
 *
 * [isConfigured] gates every call into the Firebase SDK below: a build that
 * still has the placeholders returns null tokens instead of ever touching
 * `firebase.messaging()`, same fail-soft contract as the Android actual
 * before `google-services.json` exists.
 */
internal object FirebaseWebConfig {
    // The *Web* app's key, not the Android one in google-services.json.
    // Firebase mints a separate API key per registered platform app; pairing
    // the Android key with the web appId below fails auth.
    const val API_KEY = "AIzaSyBkj4QwHRnMjruW7BJniWijd4z5uGV1r8o"
    const val PROJECT_ID = "nobatyar-79c53"
    const val STORAGE_BUCKET = "nobatyar-79c53.firebasestorage.app"
    const val MESSAGING_SENDER_ID = "56921056578"
    const val AUTH_DOMAIN = "$PROJECT_ID.firebaseapp.com"

    // Web app registered in the Firebase console for project nobatyar-79c53.
    // Must stay in sync with the same field in firebase-messaging-sw.js — the
    // service worker can't read Kotlin/wasm output, so the two are duplicated
    // by hand. Out of sync means foreground push works and background push
    // silently doesn't, which is a confusing way to fail.
    const val WEB_APP_ID = "1:56921056578:web:29eb94274ea5d99e6a14db"

    // Web Push certificate (public key half) from console → Project settings
    // → Cloud Messaging → Web configuration. Public by design: it ships in
    // the client bundle, same as the API key above.
    const val FCM_WEB_VAPID_KEY =
        "BA0GUU6qC085fE1rJL_i05LSAzfvWa0R654q8TZvyfdP4U9xJdmWY23qB1LI1GWl9ZglAsMQfTEHkgqX03idtsM"

    private const val PLACEHOLDER_PREFIX = "REPLACE_WITH_"

    val isConfigured: Boolean
        get() = !WEB_APP_ID.startsWith(PLACEHOLDER_PREFIX) &&
            !FCM_WEB_VAPID_KEY.startsWith(PLACEHOLDER_PREFIX)
}
