package xyz.sattar.javid.proqueue.feature.createAppointment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.appointment_not_found
import proqueue.composeapp.generated.resources.error_loading_appointment
import proqueue.composeapp.generated.resources.error_loading_visitors
import proqueue.composeapp.generated.resources.error_saving_appointment
import proqueue.composeapp.generated.resources.operation_error
import proqueue.composeapp.generated.resources.select_business_error
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.AddServiceCatalogItemUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CheckAppointmentConflictUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CreateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentsForDateUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetServiceCatalogUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetVisitorByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UpdateAppointmentUseCase
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentState.PartialState.*
import kotlin.time.ExperimentalTime

class CreateAppointmentViewModel(
    private val getVisitorByIdUseCase: GetVisitorByIdUseCase,
    private val createAppointmentUseCase: CreateAppointmentUseCase,
    private val getAppointmentByIdUseCase: GetAppointmentByIdUseCase,
    private val updateAppointmentUseCase: UpdateAppointmentUseCase,
    private val checkAppointmentConflictUseCase: CheckAppointmentConflictUseCase,
    private val getAppointmentsForDateUseCase: GetAppointmentsForDateUseCase,
    private val removeAppointmentUseCase: RemoveAppointmentUseCase,
    private val getServiceCatalogUseCase: GetServiceCatalogUseCase,
    private val addServiceCatalogItemUseCase: AddServiceCatalogItemUseCase
) : BaseViewModel<CreateAppointmentState, CreateAppointmentState.PartialState, CreateAppointmentEvent, CreateAppointmentIntent>(
    initialState = CreateAppointmentState()
) {
    override fun handleIntent(intent: CreateAppointmentIntent): Flow<CreateAppointmentState.PartialState> {
        return when (intent) {
            is CreateAppointmentIntent.LoadAppointment -> loadAppointment(intent.appointmentId)
            is CreateAppointmentIntent.SelectVisitor -> flow {
                emit(IsLoading(true))
                try {
                    val visitor = getVisitorByIdUseCase(intent.visitorId)
                    visitor?.let {
                        emit(LoadVisitor(visitor))
                    }
                } catch (e: Exception) {
                    emit(
                        ShowMessage(
                            e.message ?: getString(Res.string.error_loading_visitors)
                        )
                    )
                }
            }
            is CreateAppointmentIntent.LoadDailyAppointments -> loadDailyAppointments(intent.date)
            is CreateAppointmentIntent.UpdateDateTime -> flow {
                emit(UpdateDateTime(intent.date, intent.time))
            }
            is CreateAppointmentIntent.CreateAppointment -> flow {
                emitAll(
                    createAppointment(
                        intent.visitorId,
                        intent.appointmentDate,
                        intent.serviceDuration,
                        intent.description,
                        intent.selectedServices,
                        intent.force
                    )
                )
            }
            CreateAppointmentIntent.BackPress -> sendEvent(CreateAppointmentEvent.NavigateBack)
            CreateAppointmentIntent.AppointmentCreated -> sendEvent(CreateAppointmentEvent.AppointmentCreated)
            CreateAppointmentIntent.DismissConflictDialog -> flow {
                emit(DismissConflictDialog)
            }
            CreateAppointmentIntent.DismissQuotaDialog -> flow {
                emit(CreateAppointmentState.PartialState.DismissQuotaDialog)
            }
            is CreateAppointmentIntent.DeleteAppointment -> deleteAppointment(intent.appointmentId)
            CreateAppointmentIntent.LoadServiceCatalog -> loadServiceCatalog()
            is CreateAppointmentIntent.AddServiceCatalogItem -> addServiceCatalogItem(intent.name)
            is CreateAppointmentIntent.UpdateSelectedServices -> flow {
                emit(CreateAppointmentState.PartialState.UpdateSelectedServices(intent.services))
            }
        }
    }

    override fun reduceState(
        currentState: CreateAppointmentState,
        partialState: CreateAppointmentState.PartialState
    ): CreateAppointmentState {
        return when (partialState) {
            is IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)

            is ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)

            is LoadVisitor ->
                currentState.copy(
                    visitor = partialState.visitor,
                    selectedVisitorId = partialState.visitor.id,
                    serviceDuration = currentState.serviceDuration
                        ?: BusinessStateHolder.selectedBusiness.value?.defaultServiceDuration,
                    isLoading = false
                )

            is LoadAppointmentDetails ->
                currentState.copy(
                    selectedVisitorId = partialState.visitorId,
                    appointmentDate = partialState.appointmentDate,
                    serviceDuration = partialState.serviceDuration
                        ?: BusinessStateHolder.selectedBusiness.value?.defaultServiceDuration,
                    description = partialState.description,
                    selectedServices = partialState.selectedServices,
                    editingAppointmentId = partialState.appointmentId,
                    isLoading = false
                )

            AppointmentCreated ->
                currentState.copy(
                    appointmentCreated = true,
                    isLoading = false
                )
            is ShowConflictDialog ->
                currentState.copy(
                    showConflictDialog = true,
                    conflictingVisitorName = partialState.visitorName,
                    isLoading = false
                )
            DismissConflictDialog ->
                currentState.copy(
                    showConflictDialog = false,
                    conflictingVisitorName = null
                )
            is ShowQuotaDialog ->
                currentState.copy(
                    quotaDialogMessage = partialState.message,
                    isLoading = false
                )
            DismissQuotaDialog ->
                currentState.copy(quotaDialogMessage = null)
            is LoadDailyAppointments ->
                currentState.copy(
                    dailyAppointments = partialState.appointments,
                    dailyAppointmentsCount = partialState.appointments.size
                )
            is UpdateDateTime ->
                currentState.copy(
                    selectedDate = partialState.date ?: currentState.selectedDate,
                    selectedTime = partialState.time ?: currentState.selectedTime
                )
            AppointmentDeleted ->
                currentState.copy(
                    appointmentDeleted = true,
                    isLoading = false
                )
            is LoadServiceCatalog ->
                currentState.copy(
                    serviceCatalog = partialState.items,
                    isServiceCatalogLoading = false
                )
            is ServiceCatalogLoading ->
                currentState.copy(isServiceCatalogLoading = partialState.isLoading)
            is CreateAppointmentState.PartialState.UpdateSelectedServices ->
                currentState.copy(selectedServices = partialState.services)
        }
    }

    override fun createErrorState(message: String): CreateAppointmentState.PartialState =
        CreateAppointmentState.PartialState.ShowMessage(message)

    private fun loadDailyAppointments(date: Long): Flow<CreateAppointmentState.PartialState> = flow {
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            if (business != null) {
                val appointments = getAppointmentsForDateUseCase(business.id, date)
                emit(LoadDailyAppointments(appointments))
            }
        } catch (e: Exception) {
            // Silently fail or log, as this is auxiliary info
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun loadAppointment(appointmentId: Long): Flow<CreateAppointmentState.PartialState> =
        flow {
            emit(CreateAppointmentState.PartialState.IsLoading(true))
            try {
                val appointment = getAppointmentByIdUseCase(appointmentId)
                if (appointment != null) {
                    emit(
                        CreateAppointmentState.PartialState.LoadAppointmentDetails(
                            visitorId = appointment.visitorId,
                            appointmentDate = appointment.appointmentDate,
                            serviceDuration = appointment.serviceDuration,
                            description = appointment.description,
                            selectedServices = appointment.selectedServices
                                ?.split(",")
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                ?: emptyList(),
                            appointmentId = appointment.id
                        )
                    )
                    // Update selected date and time from appointment
                    val instant = Instant.fromEpochMilliseconds(appointment.appointmentDate)
                    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    val hour = localDateTime.hour.toString().padStart(2, '0')
                    val minute = localDateTime.minute.toString().padStart(2, '0')
                    emit(CreateAppointmentState.PartialState.UpdateDateTime(
                        date = appointment.appointmentDate,
                        time = "$hour:$minute"
                    ))

                    // Load visitor details
                    try {
                        val visitor = getVisitorByIdUseCase(appointment.visitorId)
                        if (visitor != null) {
                            emit(CreateAppointmentState.PartialState.LoadVisitor(visitor))
                        }
                    } catch (e: Exception) {
                        // Ignore visitor load error, just show appointment
                    }
                } else {
                    emit(CreateAppointmentState.PartialState.ShowMessage(getString(Res.string.appointment_not_found)))
                }
            } catch (e: Exception) {
                emit(
                    CreateAppointmentState.PartialState.ShowMessage(
                        e.message ?: getString(Res.string.error_loading_appointment)
                    )
                )
            }
        }

    private fun createAppointment(
        visitorId: Long,
        appointmentDate: Long,
        serviceDuration: Int?,
        description: String?,
        selectedServices: List<String>,
        force: Boolean
    ): Flow<CreateAppointmentState.PartialState> = flow {
        val selectedServicesText = selectedServices.joinToString(",").ifEmpty { null }
        emit(CreateAppointmentState.PartialState.IsLoading(true))
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            if (business == null) {
                emit(CreateAppointmentState.PartialState.ShowMessage(getString(Res.string.select_business_error)))
                return@flow
            }

            if (!force) {
                val duration = serviceDuration ?: business.defaultServiceDuration ?: 30
                val defaultDuration = business.defaultServiceDuration ?: 30
                var conflicts = checkAppointmentConflictUseCase(
                    businessId = business.id,
                    startTime = appointmentDate,
                    duration = duration,
                    defaultDuration = defaultDuration
                )

                val editingId = uiState.value.editingAppointmentId
                if (editingId != null) {
                    conflicts = conflicts.filter { it.appointment.id != editingId }
                }

                if (conflicts.isNotEmpty()) {
                    val conflict = conflicts.first()
                    emit(CreateAppointmentState.PartialState.ShowConflictDialog(conflict.visitor.fullName))
                    return@flow
                }
            }

            val editingId = uiState.value.editingAppointmentId
            val success = if (editingId != null) {
                updateAppointmentUseCase(
                    appointmentId = editingId,
                    date = appointmentDate,
                    duration = serviceDuration,
                    description = description,
                    selectedServices = selectedServicesText
                )
            } else {
                createAppointmentUseCase(
                    businessId = business.id,
                    visitorId = visitorId,
                    appointmentDate = appointmentDate,
                    serviceDuration = serviceDuration,
                    description = description,
                    selectedServices = selectedServicesText
                ) > 0
            }
            if (success) {
                emit(CreateAppointmentState.PartialState.AppointmentCreated)
                sendEvent(CreateAppointmentEvent.AppointmentCreated)
            } else {
                emit(CreateAppointmentState.PartialState.ShowMessage(getString(Res.string.error_saving_appointment)))
            }
        } catch (e: xyz.sattar.javid.proqueue.core.network.ApiException) {
            if (e.code == 409) {
                // Monthly appointment quota (or hourly capacity) reached — show a
                // dedicated dialog explaining what happened, not just a snackbar
                // that's easy to miss.
                emit(CreateAppointmentState.PartialState.ShowQuotaDialog(e.message ?: getString(Res.string.operation_error)))
            } else {
                emit(CreateAppointmentState.PartialState.ShowMessage(e.message ?: getString(Res.string.operation_error)))
            }
        } catch (e: Exception) {
            emit(CreateAppointmentState.PartialState.ShowMessage(e.message ?: getString(Res.string.operation_error)))
        }
    }

    private fun deleteAppointment(appointmentId: Long): Flow<CreateAppointmentState.PartialState> = flow {
        emit(IsLoading(true))
        try {
            val success = removeAppointmentUseCase(appointmentId)
            if (success) {
                emit(AppointmentDeleted)
                sendEvent(CreateAppointmentEvent.AppointmentDeleted)
            } else {
                emit(ShowMessage(getString(Res.string.operation_error)))
            }
        } catch (e: Exception) {
            emit(ShowMessage(e.message ?: getString(Res.string.operation_error)))
        }
    }

    /**
     * Chips to offer when recording what a visitor is here for.
     *
     * The business's own menu (defined in the business screen, and the same
     * list the client sees when booking online) comes first, then the rest of
     * the category's shared catalog. Showing the menu alone would have made
     * chips the owner adds inline here disappear from the next visit; showing
     * only the category catalog is what made this list feel unrelated to the
     * business in the first place.
     */
    private fun loadServiceCatalog(): Flow<CreateAppointmentState.PartialState> = flow {
        val business = BusinessStateHolder.selectedBusiness.value ?: return@flow
        emit(CreateAppointmentState.PartialState.ServiceCatalogLoading(true))
        try {
            val items = getServiceCatalogUseCase(business.category)
            emit(
                CreateAppointmentState.PartialState.LoadServiceCatalog(
                    (business.services + items).distinct()
                )
            )
        } catch (e: Exception) {
            // Offline or a failed fetch still leaves the owner their own menu.
            emit(CreateAppointmentState.PartialState.LoadServiceCatalog(business.services))
        }
    }

    /**
     * Adds a chip to the category's shared catalog and immediately selects
     * it, so the owner doesn't have to reopen the sheet to pick the item
     * they just typed.
     */
    private fun addServiceCatalogItem(name: String): Flow<CreateAppointmentState.PartialState> = flow {
        val business = BusinessStateHolder.selectedBusiness.value ?: return@flow
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@flow
        try {
            val addedName = addServiceCatalogItemUseCase(business.category, trimmed)
            val updatedCatalog = (uiState.value.serviceCatalog + addedName).distinct()
            emit(CreateAppointmentState.PartialState.LoadServiceCatalog(updatedCatalog))
            val updatedSelected = (uiState.value.selectedServices + addedName).distinct()
            emit(CreateAppointmentState.PartialState.UpdateSelectedServices(updatedSelected))
        } catch (e: Exception) {
            emit(ShowMessage(e.message ?: getString(Res.string.operation_error)))
        }
    }
}
