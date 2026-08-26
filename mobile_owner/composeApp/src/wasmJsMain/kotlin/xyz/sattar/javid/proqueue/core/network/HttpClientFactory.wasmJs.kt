package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json

// No Chucker (Android-only debug tool) and no OkHttp/Darwin engine — the
// browser's own network stack is the engine here, via ktor-client-js (the
// same client artifact ktor publishes for both js and wasmJs targets).
actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(Js) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            // HEADERS (not ALL): see the Android actual for why — reading the
            // body to log it consumes the one-shot multipart channel.
            level = LogLevel.HEADERS
        }
        install(AuthInterceptor)
        install(DefaultRequest) {
            url("${xyz.sattar.javid.proqueue.core.AppConfig.BASE_URL}/api/")
        }
    }
}
