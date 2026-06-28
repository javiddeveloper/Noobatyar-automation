package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Dao
interface AppointmentDao {

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime')
        AND status = 'WAITING'
        ORDER BY ABS(appointmentDate - :date) ASC
    """)
    suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetailsEntity>

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE businessId = :businessId 
        AND status = 'WAITING'
        ORDER BY appointmentDate ASC
    """)
    suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetailsEntity>

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE businessId = :businessId 
        ORDER BY appointmentDate ASC
    """)
    suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetailsEntity>

    @Query("""
        SELECT COUNT(*) FROM Appointment 
        WHERE businessId = :businessId
    """)
    suspend fun getAppointmentCountForBusiness(businessId: Long): Int

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE businessId = :businessId 
        ORDER BY appointmentDate DESC
        LIMIT 100
    """)
    suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetailsEntity>

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE visitorId = :visitorId 
        ORDER BY appointmentDate DESC
    """)
    suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetailsEntity>

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE visitorId = :visitorId AND businessId = :businessId
        ORDER BY appointmentDate DESC
    """)
    suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetailsEntity>


    @Upsert
    suspend fun upsertAppointment(appointment: AppointmentEntity): Long

    @Upsert
    suspend fun upsertAppointments(appointments: List<AppointmentEntity>)

    @Query("""
        SELECT MAX(appointmentDate) FROM Appointment 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')
        AND status = 'WAITING'
    """)
    suspend fun getLastQueuePosition(businessId: Long, date: Long): Int?

    @Query("UPDATE Appointment SET status = :status, updatedAt = :updatedAt WHERE id = :appointmentId")
    suspend fun updateAppointmentStatus(appointmentId: Long, status: String, updatedAt: Long)

    @Query("UPDATE Appointment SET appointmentDate = :date, serviceDuration = :duration, description = :description, updatedAt = :updatedAt WHERE id = :appointmentId")
    suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, updatedAt: Long)

    @Query("""
        UPDATE Appointment 
        SET updatedAt = :updatedAt 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')
        AND status = 'WAITING'
    """)
    suspend fun reorderQueueAfterRemoval(businessId: Long, date: Long, updatedAt: Long)

    @Query("DELETE FROM Appointment WHERE id = :appointmentId")
    suspend fun deleteAppointment(appointmentId: Long)

    @Query("""
        SELECT COUNT(*) FROM Appointment 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')
        AND status != 'CANCELLED'
    """)
    suspend fun getTodayAppointmentsCount(businessId: Long, date: Long): Int

    @Query("""
        SELECT COUNT(*) FROM Appointment 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')
        AND status = 'NO_SHOW'
    """)
    suspend fun getTodayNoShowCount(businessId: Long, date: Long): Int

    @Query("""
        SELECT COUNT(*) FROM Appointment 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')
        AND status = 'CANCELLED'
    """)
    suspend fun getTodayCancelledCount(businessId: Long, date: Long): Int

    @Query("""
        SELECT DISTINCT businessId FROM Appointment 
        WHERE visitorId = :visitorId 
        AND status IN ('COMPLETED', 'NO_SHOW')
    """)
    suspend fun getVisitorBusinessIds(visitorId: Long): List<Long>

    @OptIn(ExperimentalTime::class)
    @Transaction
    suspend fun removeAppointmentAndReorder(appointmentId: Long) {
        val appointment = getAppointmentById(appointmentId) ?: return
        val updatedAt = Clock.System.now().toEpochMilliseconds()

        deleteAppointment(appointmentId)
        reorderQueueAfterRemoval(
            appointment.businessId,
            appointment.appointmentDate,
            updatedAt
        )
    }

    @Query("SELECT * FROM Appointment WHERE id = :appointmentId")
    suspend fun getAppointmentById(appointmentId: Long): AppointmentEntity?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM Appointment 
            WHERE businessId = :businessId 
            AND visitorId = :visitorId 
            AND DATE(appointmentDate/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime')
            AND status = 'WAITING'
        )
    """)
    suspend fun hasActiveAppointment(businessId: Long, visitorId: Long, date: Long): Boolean

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE businessId = :businessId 
        AND status != 'CANCELLED'
        AND appointmentDate < :endTime 
        AND (appointmentDate + COALESCE(serviceDuration, :defaultDuration) * 60 * 1000) > :startTime
    """)
    suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetailsEntity>

    @Transaction
    @Query("""
        SELECT * FROM Appointment 
        WHERE businessId = :businessId 
        AND DATE(appointmentDate/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime')
        AND status != 'CANCELLED'
        ORDER BY appointmentDate ASC
    """)
    suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetailsEntity>

    @Query("DELETE FROM Appointment WHERE visitorId = :visitorId")
    suspend fun deleteAppointmentsByVisitorId(visitorId: Long)
}