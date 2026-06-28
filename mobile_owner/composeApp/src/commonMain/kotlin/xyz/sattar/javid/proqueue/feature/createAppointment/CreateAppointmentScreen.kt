package xyz.sattar.javid.proqueue.feature.createAppointment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.appointment_create_action
import proqueue.composeapp.generated.resources.appointment_description
import proqueue.composeapp.generated.resources.back
import proqueue.composeapp.generated.resources.cancel
import proqueue.composeapp.generated.resources.confirm
import proqueue.composeapp.generated.resources.conflict_dialog_message_prefix
import proqueue.composeapp.generated.resources.conflict_dialog_message_suffix
import proqueue.composeapp.generated.resources.conflict_dialog_title
import proqueue.composeapp.generated.resources.create_appointment_title
import proqueue.composeapp.generated.resources.delete
import proqueue.composeapp.generated.resources.delete_appointment
import proqueue.composeapp.generated.resources.edit_appointment
import proqueue.composeapp.generated.resources.no
import proqueue.composeapp.generated.resources.select_visitor
import proqueue.composeapp.generated.resources.service_duration_error
import proqueue.composeapp.generated.resources.service_duration_minutes
import proqueue.composeapp.generated.resources.yes_force_create
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.AppointmentsListBottomSheet
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.time.ExperimentalTime

@Composable
fun CreateAppointmentScreen(
    visitorId: Long? = null,
    appointmentId: Long? = null,
    initialDate: Long? = null,
    initialTime: String? = null,
    viewModel: CreateAppointmentViewModel = koinViewModel<CreateAppointmentViewModel>(),
    onNavigateBack: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onAppointmentCreated: () -> Unit = {},
    onNavigateToVisitorSelection: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(appointmentId, visitorId) {
        if (appointmentId != null) {
            viewModel.sendIntent(CreateAppointmentIntent.LoadAppointment(appointmentId))
        } else if (visitorId != null) {
            viewModel.sendIntent(CreateAppointmentIntent.SelectVisitor(visitorId))
        }
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateBack = onNavigateBack,
        onAppointmentCreated = onAppointmentCreated
    )

    CreateAppointmentScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        initialVisitorId = visitorId,
        initialDate = initialDate,
        initialTime = initialTime,
        onNavigateToCalendar = onNavigateToCalendar,
        onNavigateToVisitorSelection = onNavigateToVisitorSelection,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun CreateAppointmentScreenContent(
    modifier: Modifier = Modifier,
    uiState: CreateAppointmentState,
    onIntent: (CreateAppointmentIntent) -> Unit,
    initialVisitorId: Long? = null,
    initialDate: Long? = null,
    initialTime: String? = null,
    onNavigateToCalendar: () -> Unit,
    onNavigateToVisitorSelection: () -> Unit,
) {
    var selectedVisitorId by remember { mutableStateOf(initialVisitorId) }
    var selectedDate by remember { mutableStateOf(initialDate ?: DateTimeUtils.systemCurrentMilliseconds()) }
    var selectedTime by remember { mutableStateOf(initialTime ?: "09:00") }
    var serviceDuration by remember { mutableStateOf(uiState.serviceDuration?.toString() ?: "30") }
    var serviceDurationError by remember { mutableStateOf<String?>(null) }
    var showAppointmentsList by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(uiState.description ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialDate, initialTime) {
        onIntent(CreateAppointmentIntent.UpdateDateTime(initialDate, initialTime))
        if (initialDate != null) selectedDate = initialDate
        if (initialTime != null) selectedTime = initialTime
    }

    LaunchedEffect(uiState.selectedDate, uiState.selectedTime) {
        if (uiState.selectedDate != null) selectedDate = uiState.selectedDate
        if (uiState.selectedTime != null) selectedTime = uiState.selectedTime
    }

    LaunchedEffect(selectedDate) {
        onIntent(CreateAppointmentIntent.LoadDailyAppointments(selectedDate))
    }

    LaunchedEffect(uiState.selectedVisitorId) {
        if (uiState.selectedVisitorId != null) {
            selectedVisitorId = uiState.selectedVisitorId
        }
    }

    LaunchedEffect(uiState.serviceDuration) {
        if (uiState.serviceDuration != null) {
            serviceDuration = uiState.serviceDuration.toString()
        }
    }

    LaunchedEffect(uiState.description) {
        if (uiState.description != null) {
            description = uiState.description ?: ""
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    action = {
                        TextButton(onClick = { data.dismiss() }) {
                            Text(stringResource(Res.string.confirm))
                        }
                    }
                ) {
                    Text(data.visuals.message)
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.editingAppointmentId != null) stringResource(Res.string.edit_appointment) else stringResource(Res.string.create_appointment_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(CreateAppointmentIntent.BackPress) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.editingAppointmentId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(Res.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },

        ) { paddingValues ->

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (uiState.appointmentCreated) {
                onIntent(CreateAppointmentIntent.AppointmentCreated)
            } else if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Visitor Selection
                    Text(
                        text = stringResource(Res.string.select_visitor),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = selectedVisitorId == null) {
                                onNavigateToVisitorSelection()
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selectedVisitorId != null) 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) 
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = if (selectedVisitorId != null) {
                                            uiState.visitor?.fullName
                                                ?: "--"
                                        } else {
                                            "انتخاب مراجع"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedVisitorId != null) 
                                            MaterialTheme.colorScheme.onSurface 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    selectedVisitorId?.let { id ->
                                        uiState.visitor?.phoneNumber?.let { phone ->
                                            Text(
                                                text = phone,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (selectedVisitorId == null) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Date and Time Selection
                    Text(
                        text = "تاریخ و زمان نوبت",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToCalendar() },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "${DateTimeUtils.getJalaliDate(selectedDate)} - $selectedTime",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Show Daily Appointments Count
                    if (uiState.dailyAppointmentsCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "در این روز ${uiState.dailyAppointmentsCount} نوبت ثبت شده است",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(
                                    onClick = { showAppointmentsList = true },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("مشاهده لیست", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Service Duration
                    Text(
                        text = stringResource(Res.string.service_duration_minutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AppTextField(
                        value = serviceDuration,
                        maxLength = 3,
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            serviceDuration = it
                            serviceDurationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = serviceDurationError != null,
                        errorMessage = serviceDurationError,
                        enabled = !uiState.isLoading,
                    )

                    // Description
                    Text(
                        text = stringResource(Res.string.appointment_description),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AppTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLine = 3,
                        maxLength = 200,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        enabled = !uiState.isLoading,
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Create/Update Button
                    val serviceDurationErrorMsg = stringResource(Res.string.service_duration_error)
                    AppButton(
                        text = if (uiState.editingAppointmentId != null) stringResource(Res.string.edit_appointment) else stringResource(Res.string.appointment_create_action),
                        onClick = {
                            selectedVisitorId?.let { visitorId ->
                                val duration = serviceDuration.trim().toIntOrNull()
                                serviceDurationError = if (duration == null) serviceDurationErrorMsg else null
                                onIntent(
                                    CreateAppointmentIntent.CreateAppointment(
                                        visitorId = visitorId,
                                        appointmentDate = DateTimeUtils.combineDateAndTime(
                                            selectedDate,
                                            selectedTime
                                        ),
                                        serviceDuration = duration,
                                        description = description.ifEmpty { null }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedVisitorId != null
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (showAppointmentsList) {
                AppointmentsListBottomSheet(
                    appointments = uiState.dailyAppointments,
                    onDismiss = { showAppointmentsList = false }
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(Res.string.delete_appointment)) },
                    text = { Text("آیا از حذف این نوبت اطمینان دارید؟") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                uiState.editingAppointmentId?.let { id ->
                                    onIntent(CreateAppointmentIntent.DeleteAppointment(id))
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(Res.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(Res.string.cancel))
                        }
                    }
                )
            }

            // Conflict Dialog
            if (uiState.showConflictDialog) {
                val prefix = stringResource(Res.string.conflict_dialog_message_prefix)
                val suffix = stringResource(Res.string.conflict_dialog_message_suffix)
                AlertDialog(
                    onDismissRequest = { onIntent(CreateAppointmentIntent.DismissConflictDialog) },
                    title = { Text(stringResource(Res.string.conflict_dialog_title)) },
                    text = {
                        Text(
                            text = buildAnnotatedString {
                                append(prefix)
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    append(uiState.conflictingVisitorName ?: "")
                                }
                                append(suffix)
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedVisitorId?.let { visitorId ->
                                    val duration = serviceDuration.trim().toIntOrNull()
                                    onIntent(
                                        CreateAppointmentIntent.CreateAppointment(
                                            visitorId = visitorId,
                                            appointmentDate = DateTimeUtils.combineDateAndTime(
                                                selectedDate,
                                                selectedTime
                                            ),
                                            serviceDuration = duration,
                                            description = description.ifEmpty { null },
                                            force = true
                                        )
                                    )
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.yes_force_create))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onIntent(CreateAppointmentIntent.DismissConflictDialog) }) {
                            Text(stringResource(Res.string.no))
                        }
                    }
                )
            }
        }
    }
}



@Composable
fun HandleEvents(
    events: Flow<CreateAppointmentEvent>,
    onNavigateBack: () -> Unit,
    onAppointmentCreated: () -> Unit
) {
    events.collectWithLifecycleAware {
        when (it) {
            CreateAppointmentEvent.NavigateBack -> {
                onNavigateBack()

            }

            CreateAppointmentEvent.AppointmentCreated,
            CreateAppointmentEvent.AppointmentDeleted -> {
                onAppointmentCreated()
            }
            else -> {}
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateAppointmentScreenPreview() {
    AppTheme {
//        CreateAppointmentScreenContent(
//            uiState = CreateAppointmentState(),
//            onIntent = {}
//        )
    }
}
