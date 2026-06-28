package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.QueueItemCard
import xyz.sattar.javid.proqueue.core.ui.components.SectionTabs
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.feature.home.QueueItem
import xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.math.abs

@Composable
fun LastVisitorsScreen(
    viewModel: LastVisitorsViewModel = koinViewModel<LastVisitorsViewModel>(),
    onNavigateToCreateAppointment: () -> Unit = {},
    onNavigateToEditAppointment: (Long) -> Unit = {},
    onNavigateToVisitorDetails: (Long) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(LastVisitorsIntent.LoadAppointments)
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToCreateAppointment = onNavigateToCreateAppointment,
        onNavigateToEditAppointment = onNavigateToEditAppointment,
        onNavigateToVisitorDetails = onNavigateToVisitorDetails
    )

    LastVisitorsScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        onNavigateToLogin = onNavigateToLogin,
        onGenerateMessage = viewModel::generateReminderMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastVisitorsScreenContent(
    modifier: Modifier = Modifier,
    uiState: LastVisitorsState,
    onIntent: (LastVisitorsIntent) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.last_visitors_menu_item),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = { onIntent(LastVisitorsIntent.ShowFilterSheet(true)) }) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                    ProfileAvatar(
                        onNavigateToLogin = onNavigateToLogin
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = 80.dp),
                onClick = {
                    onIntent(LastVisitorsIntent.OnCreateAppointmentClick)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(Res.string.create_appointment)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.appointments.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SectionTabs(
                            labels = listOf(
                                stringResource(Res.string.visitors_tab),
                                stringResource(Res.string.queue_tab),
                            ),
                            selectedIndex = uiState.selectedTab,
                            onSelected = { index -> onIntent(LastVisitorsIntent.OnTabSelected(index)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Rounded.EventNote,
                                title = stringResource(Res.string.empty_appointments_title),
                                subtitle = stringResource(Res.string.empty_appointments_subtitle)
                            )
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SectionTabs(
                            labels = listOf(
                                stringResource(Res.string.visitors_tab),
                                stringResource(Res.string.queue_tab),
                            ),
                            selectedIndex = uiState.selectedTab,
                            onSelected = { index -> onIntent(LastVisitorsIntent.OnTabSelected(index)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.selectedTab == 1) {
                            val now = DateTimeUtils.systemCurrentMilliseconds()
                            val waiting = uiState.appointments
                                .filter { it.appointment.status == "WAITING" }
                                .sortedBy { abs(it.appointment.appointmentDate - now) }

                            TotalCountHeader(
                                title = stringResource(Res.string.people_in_queue_count),
                                count = waiting.size
                            )

                            if (waiting.isEmpty()) {
                                EmptyState(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    icon = Icons.Rounded.EventNote,
                                    title = stringResource(Res.string.empty_appointments_title),
                                    subtitle = stringResource(Res.string.empty_appointments_subtitle)
                                )
                            } else {
                                val queueItems = waiting.map { item ->
                                    val duration = (item.appointment.serviceDuration
                                        ?: item.business.defaultServiceDuration) * 60 * 1000L
                                    QueueItem(
                                        appointment = item.appointment,
                                        visitorName = item.visitor.fullName,
                                        visitorPhone = item.visitor.phoneNumber,
                                        estimatedStartTime = item.appointment.appointmentDate,
                                        estimatedEndTime = item.appointment.appointmentDate + duration
                                    )
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(queueItems) { queueItem ->
                                        QueueItemCard(
                                            item = queueItem,
                                            onRemove = {
                                                onIntent(
                                                    LastVisitorsIntent.OnDeleteAppointment(
                                                        queueItem.appointment.id
                                                    )
                                                )
                                            },
                                            onComplete = {
                                                onIntent(
                                                    LastVisitorsIntent.OnMarkCompleted(
                                                        queueItem.appointment.id
                                                    )
                                                )
                                            },
                                            onNoShow = {
                                                onIntent(LastVisitorsIntent.OnMarkNoShow(queueItem.appointment.id))
                                            },
                                            onSendMessage = { appointmentId, type, content, businessTitle ->
                                                onIntent(
                                                    LastVisitorsIntent.OnSendMessage(
                                                        appointmentId = appointmentId,
                                                        type = type,
                                                        content = content,
                                                        businessTitle = businessTitle
                                                    )
                                                )
                                            },
                                            onItemClick = {
                                                onIntent(
                                                    LastVisitorsIntent.OnEditAppointment(
                                                        queueItem.appointment.id
                                                    )
                                                )
                                            },
                                            onGenerateMessage = onGenerateMessage
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(180.dp)) }
                                }
                            }
                        } else {
                            TotalCountHeader(
                                title = stringResource(Res.string.total_visitors_count),
                                count = uiState.totalCount
                            )
                            AppointmentsList(
                                appointments = uiState.appointments,
                                onEditClick = { appointmentId ->
                                    onIntent(LastVisitorsIntent.OnEditAppointment(appointmentId))
                                },
                                onDeleteClick = { appointmentId ->
                                    onIntent(LastVisitorsIntent.OnDeleteAppointment(appointmentId))
                                },
                                onItemClick = { visitorId ->
                                    onIntent(LastVisitorsIntent.OnAppointmentClick(visitorId))
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.showFilterSheet) {
                FilterBottomSheet(
                    filter = uiState.filter,
                    onDismiss = { onIntent(LastVisitorsIntent.ShowFilterSheet(false)) },
                    onFilterChanged = { onIntent(LastVisitorsIntent.OnFilterChanged(it)) },
                    onClearFilter = { onIntent(LastVisitorsIntent.ClearFilter) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filter: AppointmentFilter,
    onDismiss: () -> Unit,
    onFilterChanged: (AppointmentFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(filter.status) }
    var selectedOrdering by remember { mutableStateOf(filter.ordering) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "فیلتر نوبت‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearFilter) {
                    Text("پاکسازی")
                }
            }

            // Status Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "وضعیت نوبت",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statuses = listOf(
                        null to "همه",
                        "WAITING" to stringResource(Res.string.status_waiting),
                        "COMPLETED" to stringResource(Res.string.status_completed),
                        "NO_SHOW" to stringResource(Res.string.status_no_show),
                        "CANCELLED" to "لغو شده"
                    )
                    statuses.forEach { (status, label) ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Ordering Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "مرتب‌سازی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppointmentOrdering.entries.forEach { ordering ->
                        val label = when (ordering) {
                            AppointmentOrdering.DATE_ASC -> "تاریخ (صعودی)"
                            AppointmentOrdering.DATE_DESC -> "تاریخ (نزولی)"
                            AppointmentOrdering.CREATED_AT_ASC -> "زمان ثبت (صعودی)"
                            AppointmentOrdering.CREATED_AT_DESC -> "زمان ثبت (نزولی)"
                        }
                        FilterChip(
                            selected = selectedOrdering == ordering,
                            onClick = { selectedOrdering = ordering },
                            label = { Text(label) }
                        )
                    }
                }
            }

            AppButton(
                text = "اعمال فیلتر",
                onClick = {
                    onFilterChanged(
                        filter.copy(
                            status = selectedStatus,
                            ordering = selectedOrdering
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TotalCountHeader(
    title: String,
    count: Int
) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.3f else 0.2f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppointmentsList(
    appointments: List<AppointmentWithDetails>,
    onEditClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onItemClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(appointments) { appointment ->
            AppointmentCard(
                appointmentWithDetails = appointment,
                onEditClick = { onEditClick(appointment.appointment.id) },
                onDeleteClick = { onDeleteClick(appointment.appointment.id) },
                onItemClick = { onItemClick(appointment.appointment.visitorId) }
            )
        }
        item { Spacer(modifier = Modifier.height(180.dp)) }
    }
}

@Composable
fun AppointmentCard(
    appointmentWithDetails: AppointmentWithDetails,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val appointment = appointmentWithDetails.appointment
    val visitor = appointmentWithDetails.visitor

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onItemClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val dateText = DateTimeUtils.formatDateTime(appointment.appointmentDate)
            val endTimeMs = appointment.appointmentDate + (appointment.serviceDuration
                ?: appointmentWithDetails.business.defaultServiceDuration) * 60 * 1000L
            val startTimeOnly = DateTimeUtils.formatTime(appointment.appointmentDate)
            val endTimeOnly = DateTimeUtils.formatTime(endTimeMs)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = visitor.fullName.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = visitor.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = visitor.phoneNumber ?: "--",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$endTimeOnly ${stringResource(Res.string.to_label)} $startTimeOnly",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val durationMinutes = appointment.serviceDuration
                ?: appointmentWithDetails.business.defaultServiceDuration
            val endTime = appointment.appointmentDate + durationMinutes * 60 * 1000L
            val overdue =
                DateTimeUtils.systemCurrentMilliseconds() > endTime && appointment.status == "WAITING"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = appointment.status, overdue = overdue)
                
                Text(
                    text = "${durationMinutes} دقیقه",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!appointment.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = appointment.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, overdue: Boolean) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    val (text, bgColor, contentColor) = when {
        status == "WAITING" && overdue -> Triple(
            stringResource(Res.string.overdue_time),
            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
            if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
        )
        status == "WAITING" -> Triple(
            stringResource(Res.string.status_waiting),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        status == "COMPLETED" -> Triple(
            stringResource(Res.string.status_completed),
            if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9),
            if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
        )
        status == "NO_SHOW" -> Triple(
            stringResource(Res.string.status_no_show),
            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
            if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
        )
        else -> Triple(
            status,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}


@Composable
fun HandleEvents(
    events: Flow<LastVisitorsEvent>,
    onNavigateToCreateAppointment: () -> Unit,
    onNavigateToEditAppointment: (Long) -> Unit,
    onNavigateToVisitorDetails: (Long) -> Unit
) {
    events.collectWithLifecycleAware {
        when (it) {
            LastVisitorsEvent.NavigateToCreateAppointment -> {
                onNavigateToCreateAppointment()
            }

            is LastVisitorsEvent.NavigateToEditAppointment -> {
                onNavigateToEditAppointment(it.appointmentId)
            }

            is LastVisitorsEvent.NavigateToVisitorDetails -> {
                onNavigateToVisitorDetails(it.visitorId)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewLastVisitorsScreen() {
    AppTheme {
    }
}
