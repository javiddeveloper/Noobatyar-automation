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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.SectionTabs
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.buildReminderMessage
import xyz.sattar.javid.proqueue.core.utils.formatPhoneNumberForAction
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openSms
import xyz.sattar.javid.proqueue.core.utils.openTelegram
import xyz.sattar.javid.proqueue.core.utils.openWhatsApp
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

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
        onGenerateMessage = viewModel::generateReminderMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorDetailsScreenContent(
    uiState: VisitorDetailsState,
    openMessageDialog: Boolean = false,
    snackbarHostState: SnackbarHostState,
    onIntent: (VisitorDetailsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: () -> Unit,
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.visitor != null) {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            var selectedTabIndex by remember { mutableStateOf(0) }

            var showMessageSheet by remember { mutableStateOf(false) }
            val sheetState = rememberModalBottomSheetState()
            var messageBody by remember { mutableStateOf("") }
            var currentChannel by remember { mutableStateOf("SMS") }
            var currentAppointmentId by remember { mutableStateOf(0L) }

            val prepareMessageSheet: (String) -> Unit = { channel ->
                val business = BusinessStateHolder.selectedBusiness.value
                val businessTitle = business?.title ?: "--"
                val businessAddress = business?.address ?: "--"
                val targetAppointment =
                    pickTargetAppointment(uiState.appointments)
                currentAppointmentId = targetAppointment?.appointment?.id ?: 0L
                currentChannel = channel
                val appointmentMillis =
                    targetAppointment?.appointment?.appointmentDate
                        ?: DateTimeUtils.systemCurrentMilliseconds()
                val serviceDurationMinutes =
                    targetAppointment?.appointment?.serviceDuration
                        ?: targetAppointment?.business?.defaultServiceDuration
                        ?: 15
                val status = targetAppointment?.appointment?.status ?: "WAITING"
                val waitingText = DateTimeUtils.calculateWaitingOrOverdueText(
                    appointmentMillis,
                    serviceDurationMinutes,
                    status
                )
                messageBody = onGenerateMessage(
                    /* businessId = */ business?.id ?: 0L,
                    /* businessTitle = */ businessTitle,
                    /* businessAddress = */ businessAddress,
                    /* visitorName = */ uiState.visitor.fullName,
                    /* appointmentMillis = */ appointmentMillis,
                    /* reminderMinutes = */ waitingText,
                    /* serviceDuration = */ targetAppointment?.appointment?.serviceDuration
                )
                showMessageSheet = true
            }

            LaunchedEffect(openMessageDialog, uiState.visitor) {
                if (openMessageDialog && uiState.visitor != null) {
                    prepareMessageSheet("SMS")
                }
            }

            if (showMessageSheet) {
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
                                    formatPhoneNumberForAction(uiState.visitor.phoneNumber),
                                    messageBody
                                )

                                "WHATSAPP" -> openWhatsApp(
                                    formatPhoneNumberForAction(uiState.visitor.phoneNumber),
                                    messageBody
                                )

                                "TELEGRAM" -> openTelegram(
                                    formatPhoneNumberForAction(uiState.visitor.phoneNumber),
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
                            VisitorInfoHeader(uiState.visitor)
                            Spacer(modifier = Modifier.height(16.dp))
                            CommunicationSection(
                                visitor = uiState.visitor,
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
                                onComposeMessage = prepareMessageSheet
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
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CommunicationButton(
                icon = Icons.Rounded.Call,
                label = stringResource(Res.string.phone_call),
                onClick = { openPhoneDial(visitor.phoneNumber) }
            )
            CommunicationButton(
                icon = Icons.Rounded.Message,
                label = stringResource(Res.string.sms),
                onClick = {
                    val business = BusinessStateHolder.selectedBusiness.value
                    val businessTitle = business?.title ?: "--"
                    val businessAddress = business?.address ?: "--"
                    val targetAppointment = pickTargetAppointment(appointments)
                    val appointmentId = targetAppointment?.appointment?.id ?: 0L
                    val appointmentMillis = targetAppointment?.appointment?.appointmentDate
                        ?: DateTimeUtils.systemCurrentMilliseconds()
                    val serviceDurationMinutes = targetAppointment?.appointment?.serviceDuration
                        ?: targetAppointment?.business?.defaultServiceDuration
                        ?: 15
                    val status = targetAppointment?.appointment?.status ?: "WAITING"
                    val waitingText = DateTimeUtils.calculateWaitingOrOverdueText(
                        appointmentMillis,
                        serviceDurationMinutes,
                        status
                    )
                    val content = buildReminderMessage(
                        businessId = business?.id ?: 0L,
                        businessTitle = businessTitle,
                        businessAddress = businessAddress,
                        visitorName = visitor.fullName,
                        appointmentMillis = appointmentMillis,
                        reminderMinutes = waitingText,
                        serviceDuration = targetAppointment?.appointment?.serviceDuration,
                    )
                    onComposeMessage("SMS")
                }
            )
            CommunicationButton(
                icon = Res.drawable.whatsapp,
                label = stringResource(Res.string.whatsapp),
                onClick = {
                    val business = BusinessStateHolder.selectedBusiness.value
                    val businessTitle = business?.title ?: "--"
                    val businessAddress = business?.address ?: "--"
                    val targetAppointment = pickTargetAppointment(appointments)
                    val appointmentId = targetAppointment?.appointment?.id ?: 0L
                    val appointmentMillis = targetAppointment?.appointment?.appointmentDate
                        ?: DateTimeUtils.systemCurrentMilliseconds()
                    val serviceDurationMinutes = targetAppointment?.appointment?.serviceDuration
                        ?: targetAppointment?.business?.defaultServiceDuration
                        ?: 15
                    val status = targetAppointment?.appointment?.status ?: "WAITING"
                    val waitingText = DateTimeUtils.calculateWaitingOrOverdueText(
                        appointmentMillis,
                        serviceDurationMinutes,
                        status
                    )
                    val content = buildReminderMessage(
                        businessId = business?.id ?: 0L,
                        businessTitle = businessTitle,
                        businessAddress = businessAddress,
                        visitorName = visitor.fullName,
                        appointmentMillis = appointmentMillis,
                        reminderMinutes = waitingText,
                        serviceDuration = targetAppointment?.appointment?.serviceDuration,
                    )
                    onComposeMessage("WHATSAPP")
                }
            )
            CommunicationButton(
                icon = Icons.Rounded.Send,
                label = stringResource(Res.string.telegram),
                onClick = {
                    val business = BusinessStateHolder.selectedBusiness.value
                    val businessTitle = business?.title ?: "--"
                    val businessAddress = business?.address ?: "--"
                    val targetAppointment = pickTargetAppointment(appointments)
                    val appointmentId = targetAppointment?.appointment?.id ?: 0L
                    val appointmentMillis = targetAppointment?.appointment?.appointmentDate
                        ?: DateTimeUtils.systemCurrentMilliseconds()
                    val serviceDurationMinutes = targetAppointment?.appointment?.serviceDuration
                        ?: targetAppointment?.business?.defaultServiceDuration
                        ?: 15
                    val status = targetAppointment?.appointment?.status ?: "WAITING"
                    val waitingText = DateTimeUtils.calculateWaitingOrOverdueText(
                        appointmentMillis,
                        serviceDurationMinutes,
                        status
                    )
                    val content = buildReminderMessage(
                        businessId = business?.id ?: 0L,
                        businessTitle = businessTitle,
                        businessAddress = businessAddress,
                        visitorName = visitor.fullName,
                        appointmentMillis = appointmentMillis,
                        reminderMinutes = waitingText,
                        serviceDuration = targetAppointment?.appointment?.serviceDuration,
                    )
                    onComposeMessage("TELEGRAM")
                }
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

@Composable
fun CommunicationButton(
    icon: Any,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
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
                    tint = MaterialTheme.colorScheme.primary
                )

                is org.jetbrains.compose.resources.DrawableResource -> Icon(
                    painterResource(icon),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                else -> Icon(Icons.Rounded.Send, contentDescription = label) // Fallback
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
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
