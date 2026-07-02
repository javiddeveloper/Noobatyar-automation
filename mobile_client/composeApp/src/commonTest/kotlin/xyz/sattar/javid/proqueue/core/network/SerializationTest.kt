package xyz.sattar.javid.proqueue.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import kotlin.test.Test

class SerializationTest {
    @Test
    fun testJsonParsing() {
        val jsonString = """
            {
              "status":"success",
              "code":200,
              "message":"لیست کسب و کارها با موفقیت دریافت شد",
              "data":{
                "count":2,
                "total_pages":1,
                "current_page":1,
                "next":null,
                "previous":null,
                "results":[
                  {
                    "id":2,
                    "title":"salon1",
                    "category":"BEAUTY_SALON",
                    "unique_code":"Z99QUDKN",
                    "phone":null,
                    "address":null,
                    "logo":null,
                    "default_service_duration":22,
                    "work_start_hour":9,
                    "work_end_hour":21,
                    "allow_anonymous_view":false
                  }
                ]
              }
            }
        """.trimIndent()
        
        val format = Json { ignoreUnknownKeys = true }
        
        try {
            val response = format.decodeFromString<NetworkResponse<PaginatedResponseDto<BusinessDto>>>(jsonString)
            println("SUCCESS: ${response.data?.results?.size}")
        } catch (e: Exception) {
            println("FAILED TO PARSE: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
