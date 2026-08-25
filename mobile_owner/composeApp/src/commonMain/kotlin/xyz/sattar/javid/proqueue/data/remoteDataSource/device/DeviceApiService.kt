package xyz.sattar.javid.proqueue.data.remoteDataSource.device

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.toApiResponse

@Serializable
data class RegisterDeviceRequestDto(
    @SerialName("token") val token: String,
    @SerialName("platform") val platform: String,
    @SerialName("app_version") val appVersion: String = ""
)

@Serializable
data class UnregisterDeviceRequestDto(
    @SerialName("token") val token: String
)

/**
 * Registration of this installation's push token with the backend, which is what
 * lets the server address a notification to *this* owner's phone.
 */
class DeviceApiService(private val httpClient: HttpClient) {

    suspend fun register(body: RegisterDeviceRequestDto): ApiResponse<Unit> {
        return httpClient.post("devices/register/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun unregister(body: UnregisterDeviceRequestDto): ApiResponse<Unit> {
        return httpClient.post("devices/unregister/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
}
