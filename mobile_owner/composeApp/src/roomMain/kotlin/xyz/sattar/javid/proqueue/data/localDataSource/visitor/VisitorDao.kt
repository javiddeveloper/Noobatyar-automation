package xyz.sattar.javid.proqueue.data.localDataSource.visitor

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VisitorDao {
    @Upsert
    suspend fun upsertVisitor(visitor: VisitorEntity): Long

    @Upsert
    suspend fun upsertVisitors(visitors: List<VisitorEntity>)

    @Query("SELECT * FROM Visitor WHERE id = :visitorId")
    suspend fun getVisitorById(visitorId: Long): VisitorEntity?

    @Query("""
        SELECT DISTINCT * FROM Visitor
        WHERE (:query IS NULL OR fullName LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%')
        ORDER BY fullName ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getVisitors(limit: Int, offset: Int, query: String?): List<VisitorEntity>

    @Query("DELETE FROM Visitor")
    suspend fun clearAllVisitors()

    @Query("DELETE FROM Visitor WHERE id = :visitorId")
    suspend fun deleteVisitor(visitorId: Long)
}
