package xyz.sattar.javid.proqueue.core.network

import com.chuckerteam.chucker.api.ChuckerInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import xyz.sattar.javid.proqueue.ProQueueApp

actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(OkHttp) {
        engine {
            addInterceptor(ChuckerInterceptor.Builder(ProQueueApp.appContext).build())
        }

        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        install(AuthInterceptor)
        install(DefaultRequest) {
            url {
                protocol = URLProtocol.HTTP
                host = "10.0.2.2"
                port = 8000
                path("api/")
            }
        }
    }
}
