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
            // HEADERS (not ALL): LogLevel.ALL reads the request body to log it,
            // which consumes the one-shot MultiPartFormDataContent channel and
            // makes the business PUT arrive at the server with an empty body.
            level = LogLevel.HEADERS
        }
        install(AuthInterceptor)
        install(DefaultRequest) {
            url("${xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL}/api/")
        }
    }
}
