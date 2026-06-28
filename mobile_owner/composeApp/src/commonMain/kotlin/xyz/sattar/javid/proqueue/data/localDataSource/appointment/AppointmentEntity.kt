package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessEntity
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorEntity

@Entity(
    tableName = "Appointment",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VisitorEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("businessId"),
        Index("visitorId"),
        Index("appointmentDate"),
        Index("status")
    ]
)
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val businessId: Long,
    val visitorId: Long,
    val appointmentDate: Long, // timestamp کامل (روز + ساعت)
    val serviceDuration: Int? = null, // null = استفاده از defaultServiceDuration
    val status: String = "WAITING", // WAITING, IN_PROGRESS, COMPLETED, NO_SHOW, CANCELLED
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
