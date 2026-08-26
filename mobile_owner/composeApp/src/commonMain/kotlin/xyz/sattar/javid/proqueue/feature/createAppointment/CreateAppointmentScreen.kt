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
import proqueue.composeapp.generated.resources.service_catalog_title
import proqueue.composeapp.generated.resources.service_duration_error
import proqueue.composeapp.generated.resources.service_duration_minutes
import proqueue.composeapp.generated.resources.yes_force_create
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.AppointmentsListBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.SelectedServiceChipsRow
import xyz.sattar.javid.proqueue.core.ui.components.ServiceCatalogBottomSheet
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.time.ExperimentalTime
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


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

/**
 * Holds all shared state/effects (unchanged from before the web layout split)
 * and dispatches to [CreateAppointmentPhoneContent] or [CreateAppointmentWebContent]
 * by [LocalWindowSize]. Compact keeps the exact original screen; Medium/Expanded
 * get a two-panel desktop layout — see [CreateAppointmentWebContent].
 */
@OptIn(ExperimentalTime::class)
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
    var showServiceCatalogSheet by remember { mutableStateOf(false) }

    // Loaded once per screen visit, scoped to the current business's
    // category — see ServiceCatalogBottomSheet's doc for why this list is
    // shared across every business in that category, not just this one.
    LaunchedEffect(Unit) {
        onIntent(CreateAppointmentIntent.LoadServiceCatalog)
    }

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

    // Shared submit logic for both the primary action and the conflict
    // dialog's "force create" confirm — kept in one place so the two
    // call sites (bottomBar button on phone, inline action card on web,
    // and the conflict dialog) can never drift apart on validation or on
    // which fields get sent.
    val serviceDurationErrorMsg = stringResource(Res.string.service_duration_error)
    val onSubmit: (Boolean) -> Unit = submit@{ force ->
        val visitorId = selectedVisitorId ?: return@submit
        val duration = serviceDuration.trim().toIntOrNull()
        if (!force) {
            serviceDurationError = if (duration == null) serviceDurationErrorMsg else null
        }
        onIntent(
            CreateAppointmentIntent.CreateAppointment(
                visitorId = visitorId,
                appointmentDate = DateTimeUtils.combineDateAndTime(selectedDate, selectedTime),
                serviceDuration = duration,
                description = description.ifEmpty { null },
                selectedServices = uiState.selectedServices,
                force = force
            )
        )
    }

    if (LocalWindowSize.current == WindowSize.Compact) {
        CreateAppointmentPhoneContent(
            modifier = modifier,
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            selectedVisitorId = selectedVisitorId,
            selectedDate = selectedDate,
            selectedTime = selectedTime,
            serviceDuration = serviceDuration,
            serviceDurationError = serviceDurationError,
            description = description,
            onIntent = onIntent,
            onServiceDurationChange = { serviceDuration = it; serviceDurationError = null },
            onDescriptionChange = { description = it },
            onSubmit = { onSubmit(false) },
            onShowAppointmentsList = { showAppointmentsList = true },
            onShowServiceCatalogSheet = { showServiceCatalogSheet = true },
            onShowDeleteDialog = { showDeleteDialog = true },
            onNavigateToCalendar = onNavigateToCalendar,
            onNavigateToVisitorSelection = onNavigateToVisitorSelection,
        )
    } else {
        CreateAppointmentWebContent(
            modifier = modifier,
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            selectedVisitorId = selectedVisitorId,
            selectedDate = selectedDate,
            selectedTime = selectedTime,
            serviceDuration = serviceDuration,
            serviceDurationError = serviceDurationError,
            description = description,
            onIntent = onIntent,
            onServiceDurationChange = { serviceDuration = it; serviceDurationError = null },
            onDescriptionChange = { description = it },
            onSubmit = { onSubmit(false) },
            onShowAppointmentsList = { showAppointmentsList = true },
            onShowServiceCatalogSheet = { showServiceCatalogSheet = true },
            onShowDeleteDialog = { showDeleteDialog = true },
            onNavigateToCalendar = onNavigateToCalendar,
            onNavigateToVisitorSelection = onNavigateToVisitorSelection,
        )
    }

    // Overlays: sheets and dialogs render through Dialog/ModalBottomSheet,
    // which position themselves relative to the window, not to whichever
    // content tree they're declared under — safe to keep as one shared block
    // for both layouts instead of duplicating per variant.
    CreateAppointmentOverlays(
        uiState = uiState,
        showAppointmentsList = showAppointmentsList,
        onDismissAppointmentsList = { showAppointmentsList = false },
        showServiceCatalogSheet = showServiceCatalogSheet,
        onDismissServiceCatalogSheet = { showServiceCatalogSheet = false },
        showDeleteDialog = showDeleteDialog,
        onDismissDeleteDialog = { showDeleteDialog = false },
        onConfirmDelete = {
            showDeleteDialog = false
            uiState.editingAppointmentId?.let { id ->
                onIntent(CreateAppointmentIntent.DeleteAppointment(id))
            }
        },
        onIntent = onIntent,
        onForceCreate = { onSubmit(true) }
    )
}

/**
 * Phone layout — unchanged. Scaffold with a bottomBar pinning the submit
 * button to the bottom of the viewport, which is the right pattern at phone
 * width (thumb reach). See [CreateAppointmentWebContent] for the desktop
 * layout, where a pinned bar would just float at the bottom of an otherwise
 * empty page.
 */
@Composable
private fun CreateAppointmentPhoneContent(
    modifier: Modifier = Modifier,
    uiState: CreateAppointmentState,
    snackbarHostState: SnackbarHostState,
    selectedVisitorId: Long?,
    selectedDate: Long,
    selectedTime: String,
    serviceDuration: String,
    serviceDurationError: String?,
    description: String,
    onIntent: (CreateAppointmentIntent) -> Unit,
    onServiceDurationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowAppointmentsList: () -> Unit,
    onShowServiceCatalogSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToVisitorSelection: () -> Unit,
) {
    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
        },
        topBar = {
            CreateAppointmentTopBar(
                uiState = uiState,
                onBackPress = { onIntent(CreateAppointmentIntent.BackPress) },
                onDeleteClick = onShowDeleteDialog
            )
        },
        bottomBar = {
            if (!uiState.isLoading && !uiState.appointmentCreated) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                    AppButton(
                        text = if (uiState.editingAppointmentId != null) stringResource(Res.string.edit_appointment) else stringResource(Res.string.appointment_create_action),
                        onClick = onSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        isLoading = uiState.isLoading
                    )
                }
            }
        }
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
                    VisitorSelectionSection(
                        selectedVisitorId = selectedVisitorId,
                        visitorName = uiState.visitor?.fullName,
                        visitorPhone = uiState.visitor?.phoneNumber,
                        onClick = onNavigateToVisitorSelection
                    )

                    DateTimeSection(
                        selectedDate = selectedDate,
                        selectedTime = selectedTime,
                        onClick = onNavigateToCalendar
                    )

                    if (uiState.dailyAppointmentsCount > 0) {
                        DailyCountBanner(
                            count = uiState.dailyAppointmentsCount,
                            onShowList = onShowAppointmentsList
                        )
                    }

                    ServiceDurationSection(
                        value = serviceDuration,
                        onValueChange = onServiceDurationChange,
                        isError = serviceDurationError != null,
                        errorMessage = serviceDurationError,
                        enabled = !uiState.isLoading
                    )

                    DescriptionSection(
                        value = description,
                        onValueChange = onDescriptionChange,
                        enabled = !uiState.isLoading
                    )

                    ServiceCatalogSection(
                        selectedServices = uiState.selectedServices,
                        onOpenCatalog = onShowServiceCatalogSheet,
                        onRemove = { name ->
                            onIntent(
                                CreateAppointmentIntent.UpdateSelectedServices(
                                    uiState.selectedServices - name
                                )
                            )
                        }
                    )
                    // Button moved to bottomBar
                }
            }
        }
    }
}

/**
 * Desktop layout (Medium/Expanded). A single scrolling column of stacked
 * cards is a phone pattern that just reads as a narrow ribbon on a wide
 * monitor, so the fields are grouped into two panels side by side — the
 * "who and when" decisions (visitor, date/time, today's count) on one side,
 * "what and how" (duration, description, service catalog) on the other —
 * with the submit action as its own full-width card below both, still part
 * of the normal scroll rather than pinned to the viewport bottom the way the
 * phone bottomBar is.
 *
 * RTL note: the app forces RTL, so the first child of the panels [Row]
 * lands on the *right*. The visitor/date panel goes first so it reads as
 * the natural starting point; the service/notes panel follows to its left.
 */
@Composable
private fun CreateAppointmentWebContent(
    modifier: Modifier = Modifier,
    uiState: CreateAppointmentState,
    snackbarHostState: SnackbarHostState,
    selectedVisitorId: Long?,
    selectedDate: Long,
    selectedTime: String,
    serviceDuration: String,
    serviceDurationError: String?,
    description: String,
    onIntent: (CreateAppointmentIntent) -> Unit,
    onServiceDurationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowAppointmentsList: () -> Unit,
    onShowServiceCatalogSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToVisitorSelection: () -> Unit,
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
        },
        topBar = {
            CreateAppointmentTopBar(
                uiState = uiState,
                onBackPress = { onIntent(CreateAppointmentIntent.BackPress) },
                onDeleteClick = onShowDeleteDialog
            )
        }
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
                AppScaffold(
                    modifier = Modifier.fillMaxSize(),
                    maxWidth = if (isExpanded) ContentWidth.Wide else ContentWidth.List
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (isExpanded) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    VisitorSelectionSection(
                                        selectedVisitorId = selectedVisitorId,
                                        visitorName = uiState.visitor?.fullName,
                                        visitorPhone = uiState.visitor?.phoneNumber,
                                        onClick = onNavigateToVisitorSelection
                                    )
                                    DateTimeSection(
                                        selectedDate = selectedDate,
                                        selectedTime = selectedTime,
                                        onClick = onNavigateToCalendar
                                    )
                                    if (uiState.dailyAppointmentsCount > 0) {
                                        DailyCountBanner(
                                            count = uiState.dailyAppointmentsCount,
                                            onShowList = onShowAppointmentsList
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ServiceDurationSection(
                                        value = serviceDuration,
                                        onValueChange = onServiceDurationChange,
                                        isError = serviceDurationError != null,
                                        errorMessage = serviceDurationError,
                                        enabled = !uiState.isLoading
                                    )
                                    DescriptionSection(
                                        value = description,
                                        onValueChange = onDescriptionChange,
                                        enabled = !uiState.isLoading
                                    )
                                    ServiceCatalogSection(
                                        selectedServices = uiState.selectedServices,
                                        onOpenCatalog = onShowServiceCatalogSheet,
                                        onRemove = { name ->
                                            onIntent(
                                                CreateAppointmentIntent.UpdateSelectedServices(
                                                    uiState.selectedServices - name
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        } else {
                            // Medium: still panel cards (not phone rows), one
                            // column — two columns at this width would squeeze
                            // the panels too narrow for the daily-count banner text.
                            VisitorSelectionSection(
                                selectedVisitorId = selectedVisitorId,
                                visitorName = uiState.visitor?.fullName,
                                visitorPhone = uiState.visitor?.phoneNumber,
                                onClick = onNavigateToVisitorSelection
                            )
                            DateTimeSection(
                                selectedDate = selectedDate,
                                selectedTime = selectedTime,
                                onClick = onNavigateToCalendar
                            )
                            if (uiState.dailyAppointmentsCount > 0) {
                                DailyCountBanner(
                                    count = uiState.dailyAppointmentsCount,
                                    onShowList = onShowAppointmentsList
                                )
                            }
                            ServiceDurationSection(
                                value = serviceDuration,
                                onValueChange = onServiceDurationChange,
                                isError = serviceDurationError != null,
                                errorMessage = serviceDurationError,
                                enabled = !uiState.isLoading
                            )
                            DescriptionSection(
                                value = description,
                                onValueChange = onDescriptionChange,
                                enabled = !uiState.isLoading
                            )
                            ServiceCatalogSection(
                                selectedServices = uiState.selectedServices,
                                onOpenCatalog = onShowServiceCatalogSheet,
                                onRemove = { name ->
                                    onIntent(
                                        CreateAppointmentIntent.UpdateSelectedServices(
                                            uiState.selectedServices - name
                                        )
                                    )
                                }
                            )
                        }

                        // Submit action — its own full-width card at the end of
                        // the scrolling content. Deliberately not a Scaffold
                        // bottomBar: pinning it to the viewport bottom is a
                        // phone (thumb-reach) pattern that on a tall desktop
                        // window would leave it floating below an empty page.
                        if (!uiState.isLoading && !uiState.appointmentCreated) {
                            SubmitActionCard(
                                text = if (uiState.editingAppointmentId != null) stringResource(Res.string.edit_appointment) else stringResource(Res.string.appointment_create_action),
                                isLoading = uiState.isLoading,
                                onClick = onSubmit
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shared top bar: back button, title (create vs edit), delete action while editing. */
@Composable
private fun CreateAppointmentTopBar(
    uiState: CreateAppointmentState,
    onBackPress: () -> Unit,
    onDeleteClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (uiState.editingAppointmentId != null) stringResource(Res.string.edit_appointment) else stringResource(Res.string.create_appointment_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPress) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.back)
                )
            }
        },
        actions = {
            if (uiState.editingAppointmentId != null) {
                IconButton(onClick = onDeleteClick) {
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
}

/** Visitor picker card — shared by phone and desktop layouts. */
@Composable
private fun VisitorSelectionSection(
    selectedVisitorId: Long?,
    visitorName: String?,
    visitorPhone: String?,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    onClick()
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
                                visitorName ?: "--"
                            } else {
                                "انتخاب مراجع"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedVisitorId != null)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        selectedVisitorId?.let {
                            visitorPhone?.let { phone ->
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
    }
}

/** Date/time picker card — shared by phone and desktop layouts. */
@Composable
private fun DateTimeSection(
    selectedDate: Long,
    selectedTime: String,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "تاریخ و زمان نوبت",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
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
    }
}

/** "N appointments today" banner with a link to [AppointmentsListBottomSheet]. */
@Composable
private fun DailyCountBanner(
    count: Int,
    onShowList: () -> Unit
) {
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
                text = "در این روز $count نوبت ثبت شده است",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TextButton(
                onClick = onShowList,
                modifier = Modifier.height(32.dp)
            ) {
                Text("مشاهده لیست", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Service duration field — shared by phone and desktop layouts. */
@Composable
private fun ServiceDurationSection(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.service_duration_minutes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        AppTextField(
            value = value,
            maxLength = 3,
            keyboardType = KeyboardType.Number,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            isError = isError,
            errorMessage = errorMessage,
            enabled = enabled,
        )
    }
}

/** Free-text description field — shared by phone and desktop layouts. */
@Composable
private fun DescriptionSection(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.appointment_description),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        AppTextField(
            value = value,
            onValueChange = onValueChange,
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
            enabled = enabled,
        )
    }
}

/**
 * Service catalog chips — additive to the free-text description above, not a
 * replacement for it. The list offered here is scoped to the current
 * business's category and shared with every other business in that category
 * (see ServiceCatalogBottomSheet's doc).
 */
@Composable
private fun ServiceCatalogSection(
    selectedServices: List<String>,
    onOpenCatalog: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.service_catalog_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenCatalog() },
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
                Text(
                    text = if (selectedServices.isEmpty())
                        "انتخاب خدمات از فهرست"
                    else
                        "${selectedServices.size} خدمت انتخاب شده",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedServices.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        SelectedServiceChipsRow(
            selected = selectedServices,
            onRemove = onRemove
        )
    }
}

/**
 * Desktop-only submit action, part of the normal scroll instead of a pinned
 * bottomBar (see [CreateAppointmentWebContent]).
 */
@Composable
private fun SubmitActionCard(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        AppButton(
            text = text,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            isLoading = isLoading
        )
    }
}

/**
 * Sheets and dialogs shared by both layouts. [AppointmentsListBottomSheet]
 * and [ServiceCatalogBottomSheet] already route through AdaptiveSheet
 * (bottom sheet on Compact, centered dialog on Medium/Expanded) and neither
 * calls `sheetState.hide()` from here — the "done" action just calls
 * [onDismiss] directly, so there's no anchor-less hide() to guard in this
 * file. AlertDialog is unaffected by window size.
 */
@Composable
private fun CreateAppointmentOverlays(
    uiState: CreateAppointmentState,
    showAppointmentsList: Boolean,
    onDismissAppointmentsList: () -> Unit,
    showServiceCatalogSheet: Boolean,
    onDismissServiceCatalogSheet: () -> Unit,
    showDeleteDialog: Boolean,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onIntent: (CreateAppointmentIntent) -> Unit,
    onForceCreate: () -> Unit
) {
    if (showAppointmentsList) {
        AppointmentsListBottomSheet(
            appointments = uiState.dailyAppointments,
            onDismiss = onDismissAppointmentsList
        )
    }

    if (showServiceCatalogSheet) {
        ServiceCatalogBottomSheet(
            catalog = uiState.serviceCatalog,
            selected = uiState.selectedServices,
            isLoading = uiState.isServiceCatalogLoading,
            onToggle = { name ->
                val updated = if (uiState.selectedServices.contains(name)) {
                    uiState.selectedServices - name
                } else {
                    uiState.selectedServices + name
                }
                onIntent(CreateAppointmentIntent.UpdateSelectedServices(updated))
            },
            onAddNew = { name ->
                onIntent(CreateAppointmentIntent.AddServiceCatalogItem(name))
            },
            onDismiss = onDismissServiceCatalogSheet
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = { Text(stringResource(Res.string.delete_appointment)) },
            text = { Text("آیا از حذف این نوبت اطمینان دارید؟") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) {
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
                TextButton(onClick = onForceCreate) {
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

    // Quota / capacity reached dialog — explains what happened and what to do.
    if (uiState.quotaDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { onIntent(CreateAppointmentIntent.DismissQuotaDialog) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("امکان ثبت نوبت نیست") },
            text = {
                Column {
                    Text(uiState.quotaDialogMessage ?: "")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "برای رزرو نوبت بیشتر، از صفحه‌ی خانه پلن خود را ارتقا دهید یا منتظر شروع ماه بعد بمانید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(CreateAppointmentIntent.DismissQuotaDialog) }) {
                    Text("متوجه شدم")
                }
            }
        )
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
