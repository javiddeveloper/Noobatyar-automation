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

class BusinessApiService(private val httpClient: HttpClient) {

    suspend fun getBusinesses(page: Int, pageSize: Int): ApiResponse<PaginatedResponseDto<BusinessDto>> {
        return httpClient.get("client/business/") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("page_size", pageSize)
        }.toApiResponse()
    }

    suspend fun getBusinessDetail(id: Long): ApiResponse<BusinessDto> {
        return httpClient.get("client/business/$id/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

}
