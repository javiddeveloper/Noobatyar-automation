package xyz.sattar.javid.proqueue.core

import xyz.sattar.javid.proqueue.BuildKonfig

actual object AppConfig {
    actual val BASE_URL: String get() = BuildKonfig.BASE_URL
    actual val BOOKING_BASE_URL: String get() = BuildKonfig.BOOKING_BASE_URL
}
