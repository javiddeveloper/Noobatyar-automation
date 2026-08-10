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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarDefaults
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.MainTopAppBar
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.LastVisitorsListShimmer
import xyz.sattar.javid.proqueue.core.ui.components.QueueItemCard
import xyz.sattar.javid.proqueue.core.ui.components.SectionTabs
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.feature.home.QueueItem
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.math.abs

@Composable
fun LastVisitorsScreen(
    viewModel: LastVisitorsViewModel = koinViewModel<LastVisitorsViewModel>(),
    initialStatus: String? = null,
    initialTab: Int? = null,
    initialDateFrom: Long? = null,
    initialDateTo: Long? = null,
    onNavigateToCreateAppointment: () -> Unit = {},
    onNavigateToEditAppointment: (Long) -> Unit = {},
    onNavigateToVisitorDetails: (Long) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initial load happens once in LastVisitorsViewModel.init (it observes the
    // selected business). Not re-triggered here, so switching back to this tab
    // does not re-request the server.

    // Applies the filter/tab a Home stat card, the queue row, or the 7-day
    // chart navigated in with. Reuses the existing OnFilterChanged/OnTabSelected
    // intents rather than a parallel filtering mechanism. Keyed on the actual
    // arg values (not Unit) so re-navigating here with different args — e.g.
    // tapping a different stat card without leaving the tab stack — re-applies.
    LaunchedEffect(initialStatus, initialTab, initialDateFrom, initialDateTo) {
        if (initialTab != null) {
            viewModel.sendIntent(LastVisitorsIntent.OnTabSelected(initialTab))
        }
        if (initialStatus != null || initialDateFrom != null || initialDateTo != null) {
            viewModel.sendIntent(
                LastVisitorsIntent.OnFilterChanged(
                    AppointmentFilter(
                        status = initialStatus,
                        dateFrom = initialDateFrom,
                        dateTo = initialDateTo
                    )
                )
            )
        }
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
        onChangeBusiness = onChangeBusiness,
        onGenerateMessage = viewModel::generateReminderMessage
    )
}

@Composable
fun LastVisitorsScreenContent(
    modifier: Modifier = Modifier,
    uiState: LastVisitorsState,
    onIntent: (LastVisitorsIntent) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
    // This screen previously had no toast host at all, so every error and
    // (once added) success confirmation from LastVisitorsState.message — delete,
    // complete, no-show, approve/reject, send-message — vanished silently.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            onIntent(LastVisitorsIntent.ClearMessage)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            MainTopAppBar(
                onNavigateToLogin = onNavigateToLogin,
                onChangeBusiness = onChangeBusiness,
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !uiState.isLoading) {
                                onIntent(LastVisitorsIntent.LoadAppointments)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(Res.string.refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onIntent(LastVisitorsIntent.ShowFilterSheet(true)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "فیلتر",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onIntent(LastVisitorsIntent.OnCreateAppointmentClick)
                },
                modifier = Modifier.padding(bottom = BottomBarDefaults.FabClearance),
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        LastVisitorsListShimmer()
                        BottomBarSpacer()
                    }
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
                                .filter { it.appointment.status == "WAITING" || it.appointment.status == "PENDING_APPROVAL" || it.appointment.status == "PENDING_VERIFICATION" }
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
                                                when (queueItem.appointment.status) {
                                                    "PENDING_APPROVAL", "PENDING_VERIFICATION" -> {
                                                        // Verify/Approve receipt → move to WAITING
                                                        onIntent(LastVisitorsIntent.OnUpdateStatus(queueItem.appointment.id, "WAITING"))
                                                    }
                                                    else -> onIntent(LastVisitorsIntent.OnMarkCompleted(queueItem.appointment.id))
                                                }
                                            },
                                            onNoShow = {
                                                when (queueItem.appointment.status) {
                                                    "PENDING_APPROVAL", "PENDING_VERIFICATION" -> {
                                                        // Reject receipt → CANCELLED
                                                        onIntent(LastVisitorsIntent.OnUpdateStatus(queueItem.appointment.id, "CANCELLED"))
                                                    }
                                                    else -> onIntent(LastVisitorsIntent.OnMarkNoShow(queueItem.appointment.id))
                                                }
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
                                    item { BottomBarSpacer() }
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

@OptIn(ExperimentalLayoutApi::class)
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
                    // Same set as Appointment.STATUS_CHOICES on the backend.
                    val statuses = listOf(
                        null to "همه",
                        "LOCKED" to "در حال پرداخت",
                        "PENDING_VERIFICATION" to "💳 در انتظار تأیید فیش",
                        "PENDING_APPROVAL" to "در انتظار تایید",
                        "CONFIRMED" to "تأیید شده",
                        "WAITING" to stringResource(Res.string.status_waiting),
                        "IN_PROGRESS" to "در حال سرویس",
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
                            AppointmentOrdering.DATE_ASC -> stringResource(Res.string.sort_date_nearest_first)
                            AppointmentOrdering.DATE_DESC -> stringResource(Res.string.sort_date_farthest_first)
                            AppointmentOrdering.CREATED_AT_ASC -> stringResource(Res.string.sort_created_oldest_first)
                            AppointmentOrdering.CREATED_AT_DESC -> stringResource(Res.string.sort_created_newest_first)
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
        item { BottomBarSpacer() }
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
    val uriHandler = LocalUriHandler.current

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
                .padding(14.dp)
        ) {
            val durationMinutes = appointment.serviceDuration
                ?: appointmentWithDetails.business.defaultServiceDuration
            val endTimeMs = appointment.appointmentDate + durationMinutes * 60 * 1000L
            val dateText = DateTimeUtils.formatDate(appointment.appointmentDate)
            val startTimeOnly = DateTimeUtils.formatTime(appointment.appointmentDate)
            val endTimeOnly = DateTimeUtils.formatTime(endTimeMs)
            val overdue =
                DateTimeUtils.systemCurrentMilliseconds() > endTimeMs && appointment.status == "WAITING"

            // Header: identity + status, on a single line.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = visitor.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = visitor.phoneNumber ?: "--",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = appointment.status, overdue = overdue)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact info strip: time range · date · duration grouped in one bar,
            // so the width is used instead of leaving a big empty gap.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaItem(
                        icon = Icons.Rounded.Schedule,
                        text = "$endTimeOnly ${stringResource(Res.string.to_label)} $startTimeOnly",
                        emphasized = true
                    )
                    MetaItem(
                        icon = Icons.Rounded.CalendarToday,
                        text = dateText
                    )
                    MetaItem(
                        icon = Icons.Rounded.Timer,
                        text = "$durationMinutes دقیقه"
                    )
                }
            }

            if (!appointment.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Deposit summary — a gateway payment has a tracking reference but no
            // receipt image, so this has to stand on its own rather than being
            // folded into the receipt block below. Purpose: an owner meeting the
            // client in person to collect the remainder needs to see at a glance
            // that a deposit was already taken, and through which channel.
            if (appointment.depositPaymentMethod == "CARD" || appointment.depositPaymentMethod == "GATEWAY") {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (appointment.depositPaymentMethod == "GATEWAY")
                                    Icons.Rounded.CreditCard else Icons.Rounded.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(Res.string.deposit_paid_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "— " + stringResource(
                                    if (appointment.depositPaymentMethod == "GATEWAY")
                                        Res.string.deposit_method_gateway
                                    else Res.string.deposit_method_card
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        appointment.paymentReference?.takeIf { it.isNotBlank() }?.let { reference ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${stringResource(Res.string.tracking_number_label)}: $reference",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Payment receipt preview — same pattern as QueueItemCard, missing
            // here meant a client's uploaded slip was invisible from this tab.
            appointment.paymentReceipt?.let { receipt ->
                val receiptUrl = if (receipt.startsWith("http")) receipt
                else "${xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL}$receipt"
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.payment_receipt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = receiptUrl,
                    contentDescription = stringResource(Res.string.payment_receipt),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { uriHandler.openUri(receiptUrl) },
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = stringResource(Res.string.click_to_view_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    emphasized: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun StatusBadge(status: String, overdue: Boolean) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    // Mirrors Appointment.STATUS_CHOICES in backend/appointment/models.py. Note
    // CONFIRMED (owner approved the booking) and COMPLETED (service finished)
    // are distinct: CONFIRMED used to be mislabelled "تکمیل شده", and COMPLETED,
    // LOCKED and IN_PROGRESS fell through to the raw English status text.
    val (text, bgColor, contentColor) = when {
        status == "WAITING" && overdue -> Triple(
            stringResource(Res.string.overdue_time),
            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
            if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
        )
        status == "LOCKED" -> Triple(
            "در حال پرداخت",
            if (isDark) Color(0xFF37474F).copy(alpha = 0.5f) else Color(0xFFECEFF1),
            if (isDark) Color(0xFFB0BEC5) else Color(0xFF455A64)
        )
        status == "PENDING_VERIFICATION" -> Triple(
            "💳 در انتظار تأیید فیش",
            if (isDark) Color(0xFF4A148C).copy(alpha = 0.4f) else Color(0xFFEDE7F6),
            if (isDark) Color(0xFFCE93D8) else Color(0xFF6A1B9A)
        )
        status == "PENDING_APPROVAL" -> Triple(
            "در انتظار تایید",
            if (isDark) Color(0xFFE65100).copy(alpha = 0.4f) else Color(0xFFFFF3E0),
            if (isDark) Color(0xFFFFCC80) else Color(0xFFE65100)
        )
        status == "CONFIRMED" -> Triple(
            "تأیید شده",
            if (isDark) Color(0xFF0D47A1).copy(alpha = 0.4f) else Color(0xFFE3F2FD),
            if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0)
        )
        status == "WAITING" -> Triple(
            stringResource(Res.string.status_waiting),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        status == "IN_PROGRESS" -> Triple(
            "در حال سرویس",
            if (isDark) Color(0xFF006064).copy(alpha = 0.45f) else Color(0xFFE0F7FA),
            if (isDark) Color(0xFF80DEEA) else Color(0xFF00838F)
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
        status == "CANCELLED" -> Triple(
            "لغو شده",
            if (isDark) Color(0xFF424242).copy(alpha = 0.5f) else Color(0xFFF5F5F5),
            if (isDark) Color(0xFFBDBDBD) else Color(0xFF616161)
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
