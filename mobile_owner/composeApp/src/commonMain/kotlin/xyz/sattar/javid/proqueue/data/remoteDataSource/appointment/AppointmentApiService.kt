package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentDto
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering

class AppointmentApiService(private val httpClient: HttpClient) {

    suspend fun queryAppointments(
        businessId: Long,
        visitorId: Long? = null,
        status: String? = null,
        date: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        ordering: AppointmentOrdering? = AppointmentOrdering.DATE_DESC,
        page: Int = 1,
        pageSize: Int = 20
    ): ApiResponse<PaginatedResponseDto<AppointmentDto>> {
        return httpClient.get("appointment/query") {
            contentType(ContentType.Application.Json)
            parameter("business_id", businessId)
            visitorId?.let { parameter("visitor_id", it) }
            status?.let { parameter("status", it) }
            date?.let { parameter("date", it) }
            dateFrom?.let { parameter("date_from", it) }
            dateTo?.let { parameter("date_to", it) }
            parameter("ordering", ordering?.value)
            parameter("page", page)
            parameter("page_size", pageSize)
        }.toApiResponse()
    }
}
