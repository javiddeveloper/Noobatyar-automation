package xyz.sattar.javid.proqueue.core.network

import kotlinx.serialization.Serializable

@Serializable
sealed class ApiResponse<out T> {
    @Serializable
    data class Success<T>(val data: T) : ApiResponse<T>()
    
    @Serializable
    data class Error(val message: String, val code: Int) : ApiResponse<Nothing>()
}

@Serializable
data class NetworkResponse<T>(
    val status: String,
    val code: Int,
    val message: String? = null,
    val data: T? = null
)

/**
 * Thrown by repositories when the backend returns ApiResponse.Error, carrying
 * the HTTP status code so callers can react to specific cases (e.g. 409 quota
 * conflicts) instead of only showing a generic error message.
 */
class ApiException(message: String, val code: Int) : Exception(message)
