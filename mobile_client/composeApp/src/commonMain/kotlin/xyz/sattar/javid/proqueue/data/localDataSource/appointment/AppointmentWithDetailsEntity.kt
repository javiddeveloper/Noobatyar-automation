package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import androidx.room.Embedded
import androidx.room.Relation
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessEntity


data class AppointmentWithDetailsEntity(
    @Embedded val appointment: AppointmentEntity,

    @Relation(
        parentColumn = "businessId",
        entityColumn = "id"
    )
    val business: BusinessEntity
)