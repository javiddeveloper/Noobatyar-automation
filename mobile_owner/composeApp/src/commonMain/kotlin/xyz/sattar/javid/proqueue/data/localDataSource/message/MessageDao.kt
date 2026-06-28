package xyz.sattar.javid.proqueue.data.localDataSource.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM Message WHERE appointmentId = :appointmentId ORDER BY sentAt DESC")
    suspend fun getAppointmentMessages(appointmentId: Long): List<MessageEntity>

    @Query("""
        SELECT Message.* FROM Message
        INNER JOIN Appointment ON Message.appointmentId = Appointment.id
        WHERE Appointment.visitorId = :visitorId AND Appointment.businessId = :businessId
        ORDER BY Message.sentAt DESC
    """)
    suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<MessageEntity>

    @Query("DELETE FROM Message WHERE id = :id")
    suspend fun deleteMessage(id: Long): Int

    @Query("""
        DELETE FROM Message 
        WHERE appointmentId IN (
            SELECT id FROM Appointment WHERE visitorId = :visitorId
        )
    """)
    suspend fun deleteMessagesByVisitorId(visitorId: Long): Int
}
