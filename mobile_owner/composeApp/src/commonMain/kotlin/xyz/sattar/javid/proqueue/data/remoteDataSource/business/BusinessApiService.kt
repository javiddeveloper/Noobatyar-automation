package xyz.sattar.javid.proqueue.data.remoteDataSource.business

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.core.network.toDirectApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.CreateBusinessRequestDto
import xyz.sattar.javid.proqueue.domain.model.business.Business

class BusinessApiService(private val httpClient: HttpClient) {

    suspend fun getBusinesses(page: Int, pageSize: Int): ApiResponse<PaginatedResponseDto<BusinessDto>> {
        return httpClient.get("business/") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("page_size", pageSize)
        }.toApiResponse()
    }

    suspend fun createBusiness(business: Business): ApiResponse<BusinessDto> {
        return httpClient.post("business/") {
            setBody(buildMultipart(business))
        }.toApiResponse()
    }

    suspend fun updateBusiness(id: Long, business: Business): ApiResponse<BusinessDto> {
        return httpClient.put("business/$id/") {
            setBody(buildMultipart(business))
        }.toApiResponse()
    }

    private fun buildMultipart(business: Business): io.ktor.client.request.forms.MultiPartFormDataContent {
        return io.ktor.client.request.forms.MultiPartFormDataContent(
            io.ktor.client.request.forms.formData {
                append("title", business.title)
                append("category", business.category.value)
                append("phone", business.phone)
                append("address", business.address)
                append("default_service_duration", business.defaultServiceDuration.toString())
                append("work_start_hour", business.workStartHour.toString())
                append("work_end_hour", business.workEndHour.toString())
                append("notification_enabled", business.notificationEnabled.toString())
                append("notification_types", business.notificationTypes)
                append("notification_minutes_before", business.notificationMinutesBefore.toString())
                append("allow_anonymous_view", business.allowAnonymousView.toString())
                if (business.paymentMethod != null) append("payment_method", business.paymentMethod)
                if (business.acceptedPaymentMethods != null) {
                    val jsonArrayStr = if (business.acceptedPaymentMethods.isEmpty()) "[]" 
                        else business.acceptedPaymentMethods.joinToString(separator = "\",\"", prefix = "[\"", postfix = "\"]")
                    append("accepted_payment_methods", jsonArrayStr)
                }
                if (business.maxAppointmentsPerHour != null) append("max_appointments_per_hour", business.maxAppointmentsPerHour.toString())
                if (business.depositMode != null) append("deposit_mode", business.depositMode)
                if (business.depositAmount != null) append("deposit_amount", business.depositAmount.toString())
                append("merchant_id", business.merchantId)
                append("payment_link", business.paymentLink)
                append("card_number", business.cardNumber)
                append("card_owner_name", business.cardOwnerName)
                append("bio", business.bio)
                
                if (business.logoBytes != null) {
                    append("logo", business.logoBytes, io.ktor.http.Headers.build {
                        append(io.ktor.http.HttpHeaders.ContentType, "image/jpeg")
                        append(io.ktor.http.HttpHeaders.ContentDisposition, "filename=\"logo.jpg\"")
                    })
                }
            }
        )
    }

    suspend fun deleteBusiness(id: Long): ApiResponse<Unit> {
        return httpClient.delete("business/$id/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }
}
