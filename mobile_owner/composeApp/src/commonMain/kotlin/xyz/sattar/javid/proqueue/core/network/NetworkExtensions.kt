package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
    return try {
        if (this.status == HttpStatusCode.TooManyRequests) {
             val networkResponse = try {
                 this.body<NetworkResponse<T>>()
             } catch (e: Exception) {
                 null
             }
             return ApiResponse.Error(
                 message = networkResponse?.message ?: "تعداد درخواست‌های شما بیش از حد مجاز است.",
                 code = 429
             )
        }
        
        val networkResponse = this.body<NetworkResponse<T>>()
        if (networkResponse.status == "success" && networkResponse.data != null) {
            ApiResponse.Success(networkResponse.data)
        } else if (networkResponse.status == "success" && T::class == Unit::class) {
            ApiResponse.Success(Unit as T)
        } else {
            ApiResponse.Error(
                    message = networkResponse.message ?: "Unknown Error",
                    code = networkResponse.code
            )
        }
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.TooManyRequests) {
             val networkResponse = try {
                 e.response.body<NetworkResponse<T>>()
             } catch (ex: Exception) {
                 null
             }
             ApiResponse.Error(
                 message = networkResponse?.message ?: "تعداد درخواست‌های شما بیش از حد مجاز است.",
                 code = 429
             )
        } else {
            ApiResponse.Error(message = e.message ?: "Unknown Error", code = e.response.status.value)
        }
    } catch (e: Exception) {
        ApiResponse.Error(message = e.message ?: "Unknown Error", code = 500)
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
        ApiResponse.Error(message = e.message ?: "Unknown Error", code = 500)
    }
}
