package xyz.sattar.javid.proqueue.data.localDataSource.business

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BusinessDao {
    @Upsert
    suspend fun upsertBusiness(business: BusinessEntity)

    @Upsert
    suspend fun upsertBusinesses(businesses: List<BusinessEntity>)

    @Query("SELECT * FROM Business ORDER BY createdAt DESC")
    suspend fun loadAllBusiness(): List<BusinessEntity>

    @Query("SELECT * FROM Business ORDER BY createdAt DESC")
    fun loadAllBusinessFlow(): kotlinx.coroutines.flow.Flow<List<BusinessEntity>>

    @Query("SELECT * FROM Business WHERE id = :businessId")
    suspend fun getBusinessById(businessId: Long): BusinessEntity?

    @Query("DELETE FROM Business WHERE id = :businessId")
    suspend fun deleteBusiness(businessId: Long)

    @Query("DELETE FROM Business")
    suspend fun clearAllBusinesses()
}