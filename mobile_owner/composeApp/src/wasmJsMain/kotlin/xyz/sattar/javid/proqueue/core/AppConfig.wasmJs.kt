package xyz.sattar.javid.proqueue.core

/**
 * Hardcoded to the local Django dev server for now (docs/ENVIRONMENTS.md:
 * `127.0.0.1` is what a browser on this machine reaches it at — `10.0.2.2`,
 * which the Android "local" flavor uses, resolves to nothing in a real
 * browser). This is the one platform that cannot share Android/iOS's
 * BuildKonfig-driven value, precisely because `-Pbuildkonfig.flavor=local`
 * is a single value shared across every non-Android-flavored target — see
 * the class doc on the `expect object` in commonMain/core/AppConfig.kt.
 *
 * Not yet wired to `window.__NOOBATYAR_CONFIG__` for a real deployed panel
 * build (docs/OWNER_WEB_PLAN.md ۴, row ۸) — that is follow-up work once
 * there's an actual panel domain to point at; local dev only needs this.
 */
actual object AppConfig {
    actual val BASE_URL: String = "http://127.0.0.1:8000"
    actual val BOOKING_BASE_URL: String = "http://127.0.0.1:3000"
}
