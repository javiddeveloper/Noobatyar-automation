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
 * Two things google-services.json genuinely cannot give us, because they
 * don't exist yet:
 *
 * - [WEB_APP_ID]: only minted once a human registers a **Web app** (the
 *   `</>` icon) under this same Firebase project in the console
 *   (Project settings → General → Your apps). Until then this stays the
 *   placeholder below.
 * - [FCM_WEB_VAPID_KEY]: generated on that Web app's Cloud Messaging tab
 *   ("Web Push certificates" → Generate key pair). It's a public key (safe
 *   to ship in a client bundle) but still project-specific, so it can't be
 *   invented here either — see docs/FCM_SETUP.md for where this fits in the
 *   existing setup flow.
 *
 * [isConfigured] gates every call into the Firebase SDK below: a build that
 * still has the placeholders returns null tokens instead of ever touching
 * `firebase.messaging()`, same fail-soft contract as the Android actual
 * before `google-services.json` exists.
 */
internal object FirebaseWebConfig {
    const val API_KEY = "AIzaSyD11zmX9Gfas8GVXZwpFRo9StrCIGUJT9A"
    const val PROJECT_ID = "nobatyar-79c53"
    const val STORAGE_BUCKET = "nobatyar-79c53.firebasestorage.app"
    const val MESSAGING_SENDER_ID = "56921056578"
    const val AUTH_DOMAIN = "$PROJECT_ID.firebaseapp.com"

    // TODO(human): fill in after registering a Web app for this Firebase
    // project. Looks like "1:56921056578:web:xxxxxxxxxxxxxxxxxxxxxx".
    const val WEB_APP_ID = "REPLACE_WITH_FIREBASE_WEB_APP_ID"

    // TODO(human): fill in from console → Project settings → Cloud Messaging
    // → Web configuration → Web Push certificates → Generate key pair.
    const val FCM_WEB_VAPID_KEY = "REPLACE_WITH_FCM_WEB_VAPID_KEY"

    private const val PLACEHOLDER_PREFIX = "REPLACE_WITH_"

    val isConfigured: Boolean
        get() = !WEB_APP_ID.startsWith(PLACEHOLDER_PREFIX) &&
            !FCM_WEB_VAPID_KEY.startsWith(PLACEHOLDER_PREFIX)
}
