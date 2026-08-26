package xyz.sattar.javid.proqueue.data.localDataSource.message

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentEntity

@Entity(
    tableName = "Message",
    foreignKeys = [
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("appointmentId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appointmentId: Long,
    val messageType: String, // SMS, WHATSAPP, TELEGRAM
    val content: String,
    val sentAt: Long,
    val businessTitle: String
) {
}
