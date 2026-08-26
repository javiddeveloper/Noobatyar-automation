package xyz.sattar.javid.proqueue.core

import xyz.sattar.javid.proqueue.BuildKonfig

/**
 * Single point of access for environment-dependent config that today comes
 * from [BuildKonfig] (itself generated from the `buildkonfig` block in
 * composeApp/build.gradle.kts — see docs/ENVIRONMENTS.md).
 *
 * `BuildKonfig.flavor` is an Android/Gradle-only concept: a web target has no
 * product flavor to switch on, and would need to source these values from the
 * hosting page (e.g. `window.__NOOBATYAR_CONFIG__` in index.html) instead of
 * a compile-time constant, so it can change without a rebuild. Routing every
 * call site through this object now means only this file has to change when
 * that happens — nothing else in commonMain needs to know where the value
 * came from.
 */
object AppConfig {
    val BASE_URL: String get() = BuildKonfig.BASE_URL
    val BOOKING_BASE_URL: String get() = BuildKonfig.BOOKING_BASE_URL
}
