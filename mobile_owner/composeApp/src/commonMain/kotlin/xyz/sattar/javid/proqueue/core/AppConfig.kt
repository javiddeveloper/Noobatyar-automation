package xyz.sattar.javid.proqueue.core

/**
 * Single point of access for environment-dependent config.
 *
 * Android/iOS source this from [xyz.sattar.javid.proqueue.BuildKonfig] (the
 * `buildkonfig` block in composeApp/build.gradle.kts — see
 * docs/ENVIRONMENTS.md). `BuildKonfig.flavor` is an Android/Gradle-only
 * concept driven by a *build-time* flag, and that flag is shared by every
 * non-Android-flavored target: docs/ENVIRONMENTS.md already documents the
 * trap of `-Pbuildkonfig.flavor=local` pointing at `10.0.2.2` (the Android
 * emulator's loopback alias) for *all* targets at once, iOS included. wasmJs
 * runs in a real browser, where `10.0.2.2` resolves to nothing — so it can't
 * share that same compile-time constant the way iOS does, or switching it to
 * something a browser can reach would silently break the documented Android
 * emulator flow again. Making this expect/actual lets each platform answer
 * independently without any call site (there are ~8) needing to know why.
 */
expect object AppConfig {
    val BASE_URL: String
    val BOOKING_BASE_URL: String
}
