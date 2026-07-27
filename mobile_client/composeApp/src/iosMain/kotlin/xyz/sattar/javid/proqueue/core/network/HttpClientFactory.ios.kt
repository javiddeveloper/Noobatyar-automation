package xyz.sattar.javid.proqueue.core.network

import co.touchlab.sqliter.DatabaseConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json

actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(Darwin){
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(AuthInterceptor)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        install(DefaultRequest) {
            url {
                // Production API. No explicit port: HTTPS defaults to 443.
                // For local work point this back at 127.0.0.1:8000 over HTTP —
                // the iOS simulator shares the host's loopback, and ATS still
                // permits local networking.
                protocol = URLProtocol.HTTPS
                host = "api.noobatyar.ir"
                path("api/")
            }
        }
    }
}

