package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.call.body

suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
    return try {
        val networkResponse = this.body<NetworkResponse<T>>()
        if (networkResponse.status == "success" && networkResponse.data != null) {
            ApiResponse.Success(networkResponse.data)
        } else if (networkResponse.status == "success" && T::class == Unit::class) {
            ApiResponse.Success(Unit as T)
        }
        else {
            ApiResponse.Error(
                message = networkResponse.message ?: "Unknown Error",
                code = networkResponse.code
            )
        }
    } catch (e: Exception) {
        ApiResponse.Error(
            message = e.message ?: "Unknown Error",
            code = 500
        )
    }
}

suspend inline fun <reified T> HttpResponse.toDirectApiResponse(): ApiResponse<T> {
    return try {
        if (this.status.value in 200..299) {
            val responseData = this.body<T>()
            ApiResponse.Success(responseData)
        } else {
            ApiResponse.Error(
                message = "HTTP Error: ${this.status.value}",
                code = this.status.value
            )
        }
    } catch (e: Exception) {
        ApiResponse.Error(
            message = e.message ?: "Unknown Error",
            code = 500
        )
    }
}
