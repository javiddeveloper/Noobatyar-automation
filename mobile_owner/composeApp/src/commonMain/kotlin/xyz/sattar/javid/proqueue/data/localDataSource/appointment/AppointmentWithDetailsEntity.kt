package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import androidx.room.Embedded
import androidx.room.Relation
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessEntity
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorEntity

data class AppointmentWithDetailsEntity(
    @Embedded val appointment: AppointmentEntity,
    @Relation(
        parentColumn = "visitorId",
        entityColumn = "id"
    )
    val visitor: VisitorEntity,
    @Relation(
        parentColumn = "businessId",
        entityColumn = "id"
    )
    val business: BusinessEntity
)