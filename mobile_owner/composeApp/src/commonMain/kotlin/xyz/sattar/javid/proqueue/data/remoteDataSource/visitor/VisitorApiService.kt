package xyz.sattar.javid.proqueue.data.remoteDataSource.visitor

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
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.CreateVisitorRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.VisitorDto

class VisitorApiService(private val httpClient: HttpClient) {

    suspend fun getVisitors(
        page: Int, 
        pageSize: Int, 
        query: String? = null
    ): ApiResponse<PaginatedResponseDto<VisitorDto>> {
        return httpClient.get("visitor") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("page_size", pageSize)
            if (!query.isNullOrBlank()) {
                parameter("search", query)
            }
        }.toApiResponse()
    }

    suspend fun getVisitorById(id: Long): ApiResponse<VisitorDto> {
        return httpClient.get("visitor/$id") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun createVisitor(request: CreateVisitorRequestDto): ApiResponse<VisitorDto> {
        return httpClient.post("visitor/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.toApiResponse()
    }

    suspend fun updateVisitor(id: Long, request: CreateVisitorRequestDto): ApiResponse<VisitorDto> {
        return httpClient.put("visitor/$id/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.toApiResponse()
    }

    suspend fun deleteVisitor(id: Long): ApiResponse<Unit> {
        return httpClient.delete("visitor/$id") {
//            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }
}
