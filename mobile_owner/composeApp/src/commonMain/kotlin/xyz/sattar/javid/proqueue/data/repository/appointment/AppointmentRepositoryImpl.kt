package xyz.sattar.javid.proqueue.data.repository.appointment

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentDao
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.toDomain
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.toEntity
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessDao
import xyz.sattar.javid.proqueue.data.localDataSource.business.toDomain
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorDao
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorEntity
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.toDomain
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApiService
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.DashboardStats
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentEntity
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AppointmentRepositoryImpl(
    private val appointmentDao: AppointmentDao,
    private val businessDao: BusinessDao,
    private val visitorDao: VisitorDao,
    private val appointmentApiService: AppointmentApiService
) : AppointmentRepository {
    override suspend fun getDailyCounts(
        businessId: Long,
        days: Int
    ): List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto> {
        return try {
            when (val response = appointmentApiService.getDailyCounts(businessId, days)) {
                is ApiResponse.Success -> response.data
                is ApiResponse.Error -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun createAppointment(appointment: Appointment): Long {
        return try {
            val request = xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.CreateAppointmentRequestDto(
                businessId = appointment.businessId,
                visitorId = appointment.visitorId,
                appointmentDate = appointment.appointmentDate,
                serviceDuration = appointment.serviceDuration,
                description = appointment.description,
                selectedServices = appointment.selectedServices
            )
            val response = appointmentApiService.createAppointment(request)
            if (response is ApiResponse.Success) {
                val dto = response.data
                val entity = xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentEntity(
                    id = dto.id,
                    businessId = appointment.businessId,
                    visitorId = dto.visitor.id,
                    appointmentDate = dto.appointmentDate,
                    serviceDuration = dto.serviceDuration,
                    status = dto.status,
                    description = dto.description,
                    selectedServices = dto.selectedServices,
                    paymentReceipt = dto.paymentReceipt,
                    trackingCode = dto.trackingCode,
                    paymentReference = dto.paymentReference,
                    depositPaymentMethod = dto.depositPaymentMethod,
                    createdAt = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(dto.createdAt),
                    updatedAt = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(dto.updatedAt)
                )
                appointmentDao.upsertAppointment(entity)
                dto.id
            } else if (response is ApiResponse.Error) {
                throw xyz.sattar.javid.proqueue.core.network.ApiException(response.message, response.code)
            } else {
                -1L
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> {
        return try {
            val appointments = appointmentDao.getWaitingQueue(businessId, date)

            appointments.map { appointment ->
                val visitor = visitorDao.getVisitorById(appointment.visitor.id)?.toDomain()
                val business = businessDao.getBusinessById(appointment.business.id)?.toDomain()

                AppointmentWithDetails(
                    appointment = appointment.appointment.toDomain(),
                    visitor = visitor ?: throw Exception("Visitor not found"),
                    business = business ?: throw Exception("Business not found")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean {
        return try {
            val request = xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto(
                status = status
            )
            val response = appointmentApiService.updateAppointment(appointmentId, request)
            if (response is ApiResponse.Success) {
                appointmentDao.updateAppointmentStatus(
                    appointmentId,
                    status,
                    Clock.System.now().toEpochMilliseconds()
                )
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String?): Boolean {
        return try {
            val request = xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto(
                appointmentDate = date,
                serviceDuration = duration,
                description = description,
                selectedServices = selectedServices
            )
            val response = appointmentApiService.updateAppointment(appointmentId, request)
            if (response is ApiResponse.Success) {
                appointmentDao.updateAppointment(
                    appointmentId,
                    date,
                    duration,
                    description,
                    selectedServices,
                    Clock.System.now().toEpochMilliseconds()
                )
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun removeAppointment(appointmentId: Long): Boolean {
        return try {
            val response = appointmentApiService.deleteAppointment(appointmentId)
            if (response is ApiResponse.Success) {
                appointmentDao.removeAppointmentAndReorder(appointmentId)
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getVisitorHistory(visitorId).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getVisitorHistoryForBusiness(visitorId, businessId).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTodayStats(businessId: Long, date: Long): DashboardStats {
        return try {
            DashboardStats(
                totalAppointments = appointmentDao.getTodayAppointmentsCount(businessId, date),
                noShowCount = appointmentDao.getTodayNoShowCount(businessId, date),
                cancelledCount = appointmentDao.getTodayCancelledCount(businessId, date)
            )
        } catch (e: Exception) {
            DashboardStats(0, 0, 0)
        }
    }
    override suspend fun getAppointmentById(appointmentId: Long): Appointment? {
        return try {
            appointmentDao.getAppointmentById(appointmentId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getAllWaitingAppointments(businessId).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getTodayAppointments(businessId).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getAllAppointmentsForBusiness(businessId).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun deleteAppointmentsByVisitorId(visitorId: Long) {
        try {
            appointmentDao.deleteAppointmentsByVisitorId(visitorId)
        } catch (e: Exception) {
            // Log error
        }
    }

    override suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getConflictingAppointments(businessId, startTime, endTime, defaultDuration).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getAppointmentsForDate(businessId, date).map {
                it.toDomain()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun syncAppointments(
        businessId: Long,
        visitorId: Long?,
        status: String?,
        date: Long?,
        dateFrom: Long?,
        dateTo: Long?,
        ordering: AppointmentOrdering?,
        page: Int,
        pageSize: Int
    ): Boolean {
        return try {
            val response = appointmentApiService.queryAppointments(
                businessId = businessId,
                visitorId = visitorId,
                status = status,
                date = date,
                dateFrom = dateFrom,
                dateTo = dateTo,
                ordering = ordering,
                page = page,
                pageSize = pageSize
            )

            if (response is ApiResponse.Success) {
                val appointmentsDto = response.data.results

                // 1. Sync Visitors
                val visitors = appointmentsDto.map { dto ->
                    VisitorEntity(
                        id = dto.visitor.id,
                        fullName = dto.visitor.fullName,
                        phoneNumber = dto.visitor.phoneNumber,
                        createdAt = dto.visitor.createdAt?.let { DateTimeUtils.parseIsoToEpochMillis(it) }
                            ?: Clock.System.now().toEpochMilliseconds()
                    )
                }
                visitorDao.upsertVisitors(visitors)

                // 2. Sync Appointments
                val appointments = appointmentsDto.map { dto ->
                    AppointmentEntity(
                        id = dto.id,
                        businessId = businessId,
                        visitorId = dto.visitor.id,
                        appointmentDate = dto.appointmentDate,
                        serviceDuration = dto.serviceDuration,
                        status = dto.status,
                        description = dto.description,
                        selectedServices = dto.selectedServices,
                        // Without this the inline receipt preview on the queue
                        // card never has anything to show: the API returns it,
                        // but sync dropped it before it reached the DB.
                        paymentReceipt = dto.paymentReceipt,
                    trackingCode = dto.trackingCode,
                    paymentReference = dto.paymentReference,
                    depositPaymentMethod = dto.depositPaymentMethod,
                        createdAt = DateTimeUtils.parseIsoToEpochMillis(dto.createdAt),
                        updatedAt = DateTimeUtils.parseIsoToEpochMillis(dto.updatedAt)
                    )
                }
                appointmentDao.upsertAppointments(appointments)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
