package xyz.sattar.javid.proqueue.feature.visitorDetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.appointments_tab
import proqueue.composeapp.generated.resources.contact_options
import proqueue.composeapp.generated.resources.delete
import proqueue.composeapp.generated.resources.empty_messages_subtitle
import proqueue.composeapp.generated.resources.empty_messages_title
import proqueue.composeapp.generated.resources.message_text
import proqueue.composeapp.generated.resources.messages_tab
import proqueue.composeapp.generated.resources.phone_call
import proqueue.composeapp.generated.resources.send
import proqueue.composeapp.generated.resources.sms
import proqueue.composeapp.generated.resources.telegram
import proqueue.composeapp.generated.resources.visitor_details_title
import proqueue.composeapp.generated.resources.visitor_no_appointments_subtitle
import proqueue.composeapp.generated.resources.visitor_no_appointments_title
import proqueue.composeapp.generated.resources.whatsapp
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.SectionTabs
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.formatPhoneNumberForAction
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openSms
import xyz.sattar.javid.proqueue.core.utils.openTelegram
import xyz.sattar.javid.proqueue.core.utils.openWhatsApp
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun VisitorDetailsScreen(
    visitorId: Long,
    openMessageDialog: Boolean = false,
    viewModel: VisitorDetailsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(visitorId) {
        viewModel.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(visitorId))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    HandleEffects(
        events = viewModel.events,
        onNavigateBack = onNavigateBack
    )

    VisitorDetailsScreenContent(
        uiState = uiState,
        openMessageDialog = openMessageDialog,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent,
        onNavigateBack = onNavigateBack,
        onNavigateToCreateAppointment = { onNavigateToCreateAppointment(visitorId) },
        onGenerateMessage = viewModel::generateReminderMessage,
        onRetry = { viewModel.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(visitorId)) }
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * (collapsing identity header + sticky-tab LazyColumn) untouched; see
 * [VisitorDetailsPhoneBody]. Medium/Expanded get a two-column layout instead
 * — see [VisitorDetailsWebBody]. The message-composer sheet and its deep-link
 * handling (openMessageDialog, from a reminder notification) live here,
 * shared by both layouts, so neither has to duplicate that state.
 */
@Composable
fun VisitorDetailsScreenContent(
    uiState: VisitorDetailsState,
    openMessageDialog: Boolean = false,
    snackbarHostState: SnackbarHostState,
    onIntent: (VisitorDetailsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: () -> Unit,
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String,
    onRetry: () -> Unit = {}
) {
    var showMessageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var messageBody by remember { mutableStateOf("") }
    var currentChannel by remember { mutableStateOf("SMS") }
    var currentAppointmentId by remember { mutableStateOf(0L) }

    /**
     * Opens the compose sheet for [channel].
     *
     * [useTemplate] decides whether the body arrives pre-filled. The
     * reminder template is only meaningful when we already know *which*
     * appointment is being reminded about — that is the case when the
     * screen was opened from a reminder notification, and it is not the
     * case when the owner simply taps an icon on the profile. There,
     * [pickTargetAppointment] falls back to the most recent appointment,
     * which is routinely one that already happened, so the owner ends up
     * about to send a reminder for a past slot. The profile entry point
     * therefore starts from a blank body.
     */
    val prepareMessageSheet: (String, Boolean) -> Unit = { channel, useTemplate ->
        val visitor = uiState.visitor
        if (visitor != null) {
            val business = BusinessStateHolder.selectedBusiness.value
            val targetAppointment =
                if (useTemplate) pickTargetAppointment(uiState.appointments) else null
            currentAppointmentId = targetAppointment?.appointment?.id ?: 0L
            currentChannel = channel
            messageBody = if (targetAppointment == null) {
                ""
            } else {
                val appointmentMillis = targetAppointment.appointment.appointmentDate
                val serviceDurationMinutes =
                    targetAppointment.appointment.serviceDuration
                        ?: targetAppointment.business?.defaultServiceDuration
                        ?: 15
                val waitingText = DateTimeUtils.calculateWaitingOrOverdueText(
                    appointmentMillis,
                    serviceDurationMinutes,
                    targetAppointment.appointment.status
                )
                onGenerateMessage(
                    /* businessId = */ business?.id ?: 0L,
                    /* businessTitle = */ business?.title ?: "--",
                    /* businessAddress = */ business?.address ?: "--",
                    /* visitorName = */ visitor.fullName,
                    /* appointmentMillis = */ appointmentMillis,
                    /* reminderMinutes = */ waitingText,
                    /* serviceDuration = */ targetAppointment.appointment.serviceDuration
                )
            }
            showMessageSheet = true
        }
    }

    LaunchedEffect(openMessageDialog, uiState.visitor) {
        if (openMessageDialog && uiState.visitor != null) {
            // Arrived from a reminder notification: the template is the
            // whole point of the deep link, so keep it.
            prepareMessageSheet("SMS", true)
        }
    }

    if (showMessageSheet && uiState.visitor != null) {
        val visitor = uiState.visitor
        ModalBottomSheet(
            onDismissRequest = { showMessageSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.message_text),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    when (currentChannel) {
                        "SMS" -> openSms(
                            formatPhoneNumberForAction(visitor.phoneNumber),
                            messageBody
                        )

                        "WHATSAPP" -> openWhatsApp(
                            formatPhoneNumberForAction(visitor.phoneNumber),
                            messageBody
                        )

                        "TELEGRAM" -> openTelegram(
                            formatPhoneNumberForAction(visitor.phoneNumber),
                            messageBody
                        )
                    }
                    onIntent(
                        VisitorDetailsIntent.OnSendMessage(
                            appointmentId = currentAppointmentId,
                            type = currentChannel,
                            content = messageBody,
                            businessTitle = BusinessStateHolder.selectedBusiness.value?.title
                                ?: "--"
                        )
                    )
                    showMessageSheet = false
                }) {
                    Text(
                        text = "${stringResource(Res.string.send)} ${
                            channelLabel(
                                currentChannel
                            )
                        }"
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.visitor_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { ToastyHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) {
                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                }
            }
        } else if (uiState.visitor != null) {
            val visitor = uiState.visitor
            if (LocalWindowSize.current == WindowSize.Compact) {
                VisitorDetailsPhoneBody(
                    visitor = visitor,
                    uiState = uiState,
                    paddingValues = paddingValues,
                    onIntent = onIntent,
                    onComposeMessage = { channel -> prepareMessageSheet(channel, false) }
                )
            } else {
                VisitorDetailsWebBody(
                    visitor = visitor,
                    uiState = uiState,
                    paddingValues = paddingValues,
                    onIntent = onIntent,
                    onComposeMessage = { channel -> prepareMessageSheet(channel, false) }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.message ?: "اطلاعات مشتری در دسترس نیست",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRetry) {
                        Text("تلاش مجدد")
                    }
                }
            }
        }
    }
}

/**
 * Phone layout — unchanged. Collapsing identity/contact header (scroll-linked
 * via [NestedScrollConnection]) above a sticky-tab LazyColumn for
 * messages/appointments. See [VisitorDetailsWebBody] for the desktop layout.
 */
@Composable
private fun VisitorDetailsPhoneBody(
    visitor: Visitor,
    uiState: VisitorDetailsState,
    paddingValues: PaddingValues,
    onIntent: (VisitorDetailsIntent) -> Unit,
    onComposeMessage: (channel: String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var selectedTabIndex by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val maxHeight = 250.dp
    val minHeight = 0.dp
    val density = LocalDensity.current
    val collapseRangePx = with(density) { (maxHeight - minHeight).toPx() }
    var headerOffset by remember { mutableStateOf(0f) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val deltaY = available.y
                val isScrollingUp = deltaY < 0
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (isScrollingUp && isAtTop) {
                    val newOffset = (headerOffset - deltaY).coerceIn(0f, collapseRangePx)
                    val consumed = headerOffset - newOffset
                    headerOffset = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                val isScrollingDown = deltaY > 0
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (isScrollingDown && isAtTop) {
                    val newOffset = (headerOffset - deltaY).coerceIn(0f, collapseRangePx)
                    val consumedY = headerOffset - newOffset
                    headerOffset = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }
    val headerHeight = lerp(maxHeight, minHeight, (headerOffset / collapseRangePx))
    val contentAlpha = 1f - (headerOffset / collapseRangePx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                    animationSpec = tween(durationMillis = 500, delayMillis = 300)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .padding(horizontal = 16.dp)
                        .alpha(contentAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VisitorInfoHeader(visitor)
                    Spacer(modifier = Modifier.height(16.dp))
                    CommunicationSection(
                        visitor = visitor,
                        appointments = uiState.appointments,
                        onSendMessage = { appointmentId, type, content, businessTitle ->
                            onIntent(
                                VisitorDetailsIntent.OnSendMessage(
                                    appointmentId = appointmentId,
                                    type = type,
                                    content = content,
                                    businessTitle = businessTitle
                                )
                            )
                        },
                        // Profile entry point: blank body, no template.
                        onComposeMessage = onComposeMessage
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stickyHeader {
                    SectionTabs(
                        labels = listOf(
                            stringResource(Res.string.messages_tab),
                            stringResource(Res.string.appointments_tab)
                        ),
                        selectedIndex = selectedTabIndex,
                        onSelected = { selectedTabIndex = it },
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    )
                }

                if (selectedTabIndex == 0) {
                    if (uiState.messages.isEmpty()) {
                        item {
                            EmptyState(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                icon = Icons.Rounded.Message,
                                title = stringResource(Res.string.empty_messages_title),
                                subtitle = stringResource(Res.string.empty_messages_subtitle)
                            )
                        }
                    } else {
                        items(uiState.messages) { message ->
                            MessageItemCard(
                                message = message,
                                onDeleteClick = {
                                    onIntent(
                                        VisitorDetailsIntent.DeleteMessage(
                                            message.id
                                        )
                                    )
                                }
                            )
                        }
                    }
                } else {
                    if (uiState.appointments.isEmpty()) {
                        item {
                            EmptyState(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                icon = Icons.Rounded.EventNote,
                                title = stringResource(Res.string.visitor_no_appointments_title),
                                subtitle = stringResource(Res.string.visitor_no_appointments_subtitle)
                            )
                        }
                    } else {
                        items(uiState.appointments) { appointment ->
                            xyz.sattar.javid.proqueue.feature.lastVisitors.AppointmentCard(
                                appointmentWithDetails = appointment,
                                onEditClick = {},
                                onDeleteClick = {},
                                onItemClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop layout: two columns instead of the phone's collapsing header. RTL
 * means the first [Row] child lands on the right, so identity/contact/actions
 * — what an owner looks at first for "who is this and how do I reach them" —
 * is the first child (right side); the wider appointment/message history pane
 * is the second child (left side), scrolling independently instead of sharing
 * one scroll container with the header.
 */
@Composable
private fun VisitorDetailsWebBody(
    visitor: Visitor,
    uiState: VisitorDetailsState,
    paddingValues: PaddingValues,
    onIntent: (VisitorDetailsIntent) -> Unit,
    onComposeMessage: (channel: String) -> Unit
) {
    AppScaffold(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        maxWidth = ContentWidth.Wide
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Identity/contact/actions — right side in RTL.
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                VisitorInfoHeader(visitor)
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        CommunicationSection(
                            visitor = visitor,
                            appointments = uiState.appointments,
                            onSendMessage = { appointmentId, type, content, businessTitle ->
                                onIntent(
                                    VisitorDetailsIntent.OnSendMessage(
                                        appointmentId = appointmentId,
                                        type = type,
                                        content = content,
                                        businessTitle = businessTitle
                                    )
                                )
                            },
                            onComposeMessage = onComposeMessage
                        )
                    }
                }
            }

            // Appointment/message history — wider, independently scrolling pane.
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                var selectedTabIndex by remember { mutableStateOf(0) }

                SectionTabs(
                    labels = listOf(
                        stringResource(Res.string.messages_tab),
                        stringResource(Res.string.appointments_tab)
                    ),
                    selectedIndex = selectedTabIndex,
                    onSelected = { selectedTabIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Second grid column only at Expanded — at Medium the pane is
                // narrow enough that two columns would wrap card content.
                val isExpanded = LocalWindowSize.current == WindowSize.Expanded

                if (selectedTabIndex == 0) {
                    if (uiState.messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Rounded.Message,
                                title = stringResource(Res.string.empty_messages_title),
                                subtitle = stringResource(Res.string.empty_messages_subtitle)
                            )
                        }
                    } else if (isExpanded) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            gridItems(uiState.messages) { message ->
                                MessageItemCard(
                                    message = message,
                                    onDeleteClick = { onIntent(VisitorDetailsIntent.DeleteMessage(message.id)) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.messages) { message ->
                                MessageItemCard(
                                    message = message,
                                    onDeleteClick = { onIntent(VisitorDetailsIntent.DeleteMessage(message.id)) }
                                )
                            }
                        }
                    }
                } else {
                    if (uiState.appointments.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Rounded.EventNote,
                                title = stringResource(Res.string.visitor_no_appointments_title),
                                subtitle = stringResource(Res.string.visitor_no_appointments_subtitle)
                            )
                        }
                    } else if (isExpanded) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            gridItems(uiState.appointments) { appointment ->
                                xyz.sattar.javid.proqueue.feature.lastVisitors.AppointmentCard(
                                    appointmentWithDetails = appointment,
                                    onEditClick = {},
                                    onDeleteClick = {},
                                    onItemClick = {}
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.appointments) { appointment ->
                                xyz.sattar.javid.proqueue.feature.lastVisitors.AppointmentCard(
                                    appointmentWithDetails = appointment,
                                    onEditClick = {},
                                    onDeleteClick = {},
                                    onItemClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisitorInfoHeader(visitor: Visitor) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = visitor.fullName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = visitor.phoneNumber,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The contact row at the top of the profile. Each icon only chooses a *channel*
 * — the message itself is composed in the sheet, which opens empty from here (see
 * `prepareMessageSheet`), so nothing in this row builds message text.
 */
@Composable
fun CommunicationSection(
    visitor: Visitor,
    appointments: List<AppointmentWithDetails>,
    onSendMessage: (appointmentId: Long, type: String, content: String, businessTitle: String) -> Unit,
    onComposeMessage: (channel: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.contact_options),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            CommunicationButton(
                icon = Icons.Rounded.Call,
                label = stringResource(Res.string.phone_call),
                onClick = { openPhoneDial(visitor.phoneNumber) }
            )
            CommunicationButton(
                icon = Icons.Rounded.Message,
                label = stringResource(Res.string.sms),
                onClick = { onComposeMessage("SMS") }
            )
            CommunicationButton(
                icon = Res.drawable.whatsapp,
                label = stringResource(Res.string.whatsapp),
                onClick = { onComposeMessage("WHATSAPP") }
            )
            CommunicationButton(
                icon = Icons.Rounded.Send,
                label = stringResource(Res.string.telegram),
                onClick = { onComposeMessage("TELEGRAM") }
            )
        }
    }
}

private fun pickTargetAppointment(appointments: List<AppointmentWithDetails>): AppointmentWithDetails? {
    if (appointments.isEmpty()) return null
    val now = DateTimeUtils.systemCurrentMilliseconds()
    val upcoming = appointments
        .filter { it.appointment.appointmentDate >= now && it.appointment.status == "WAITING" }
        .minByOrNull { it.appointment.appointmentDate }
    if (upcoming != null) return upcoming
    return appointments.maxByOrNull { it.appointment.appointmentDate }
}

/**
 * One circular action in the contact row.
 *
 * Every branch pins the glyph to the same 24.dp box and the whole button to a
 * fixed width: without those, a vector resource fell back to its intrinsic size
 * while the Material icons used the 24.dp default, and the labels (which differ
 * in length) pushed the circles out of line with each other.
 */
@Composable
fun CommunicationButton(
    icon: Any,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is ImageVector -> Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                is org.jetbrains.compose.resources.DrawableResource -> Icon(
                    painterResource(icon),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                else -> Icon(
                    Icons.Rounded.Send,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) // Fallback
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HistorySection(appointments: List<AppointmentWithDetails>, messages: List<Message>) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("ارسال پیام", "نوبت‌ها")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "سوابق",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        SectionTabs(
            labels = tabs,
            selectedIndex = selectedTabIndex,
            onSelected = { selectedTabIndex = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabIndex) {
            0 -> MessagesList(messages)
            1 -> AppointmentsList(appointments)
        }
    }
}

@Composable
fun MessagesList(messages: List<Message>) {
    if (messages.isEmpty()) {
        EmptyState(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            icon = Icons.Rounded.Message,
            title = stringResource(Res.string.empty_messages_title),
            subtitle = stringResource(Res.string.empty_messages_subtitle)
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                MessageItemCard(
                    message = message,
                    onDeleteClick = {}
                )
            }
        }
    }
}

@Composable
fun AppointmentsList(appointments: List<AppointmentWithDetails>) {
    if (appointments.isEmpty()) {
        EmptyState(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            icon = Icons.Rounded.EventNote,
            title = stringResource(Res.string.visitor_no_appointments_title),
            subtitle = stringResource(Res.string.visitor_no_appointments_subtitle)
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(appointments) { appointment ->
                xyz.sattar.javid.proqueue.feature.lastVisitors.AppointmentCard(
                    appointmentWithDetails = appointment,
                    onEditClick = {},
                    onDeleteClick = {},
                    onItemClick = {}
                )
            }
        }
    }
}


@Composable
fun HandleEffects(
    events: Flow<VisitorDetailsEvent>,
    onNavigateBack: () -> Unit
) {
    events.collectWithLifecycleAware { event ->
        when (event) {
            VisitorDetailsEvent.NavigateBack -> onNavigateBack()
        }
    }
}

@Composable
private fun channelLabel(channel: String): String = when (channel) {
    "SMS" -> stringResource(Res.string.sms)
    "WHATSAPP" -> stringResource(Res.string.whatsapp)
    "TELEGRAM" -> stringResource(Res.string.telegram)
    else -> channel
}

@Composable
fun MessageItemCard(
    message: Message,
    onDeleteClick: () -> Unit
) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateTimeUtils.formatDateTime(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.businessTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Server-side SMS logs are read-only history; there is no
                    // local row to delete, so don't offer the action.
                    if (!message.remote) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = {

                                    Text(
                                        stringResource(Res.string.delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Message,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val badgeColor = mediaColor(message.messageType)
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = if (isDark) 0.3f else 0.12f)
                ) {
                    Text(
                        text = channelLabel(message.messageType),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) badgeColor.copy(alpha = 0.9f) else badgeColor
                    )
                }
                if (message.status == "FAILED") {
                    val errorColor = MaterialTheme.colorScheme.error
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = errorColor.copy(alpha = if (isDark) 0.3f else 0.12f)
                    ) {
                        Text(
                            text = "ارسال نشد",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) errorColor.copy(alpha = 0.9f) else errorColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun mediaColor(type: String): Color = when (type) {
    "WHATSAPP" -> Color(0xFF25D366)
    "TELEGRAM" -> Color(0xFF229ED9)
    "SMS" -> Color(0xFF0B5FFF)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
