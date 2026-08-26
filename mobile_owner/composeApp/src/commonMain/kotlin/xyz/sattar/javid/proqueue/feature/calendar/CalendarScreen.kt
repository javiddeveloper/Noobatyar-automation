package xyz.sattar.javid.proqueue.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.back
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    isPicker: Boolean = false,
    excludeAppointmentId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: (Long, String) -> Unit,
    onNavigateToAppointmentDetails: (Long) -> Unit,
    onSlotSelected: (Long, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    // حذف LaunchedEffect تکراری — ViewModel.init با BusinessStateHolder بار اول را مدیریت می‌کند

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            CalendarEvent.NavigateBack -> onNavigateBack()
            is CalendarEvent.NavigateToCreateAppointment -> onNavigateToCreateAppointment(event.date, event.time)
            is CalendarEvent.NavigateToAppointmentDetails -> onNavigateToAppointmentDetails(event.appointmentId)
        }
    }

    CalendarScreenContent(
        uiState = uiState,
        isPicker = isPicker,
        excludeAppointmentId = excludeAppointmentId,
        onIntent = viewModel::sendIntent,
        onSlotSelected = onSlotSelected
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * (days strip above the time list) untouched; Medium/Expanded get a
 * side-by-side layout — see [CalendarWebContent]. A calendar is the screen
 * that benefits most from extra width, so it gets its own desktop treatment
 * rather than just being width-capped like a list screen.
 */
@Composable
fun CalendarScreenContent(
    uiState: CalendarState,
    isPicker: Boolean,
    excludeAppointmentId: Long? = null,
    onIntent: (CalendarIntent) -> Unit,
    onSlotSelected: (Long, String) -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        CalendarPhoneContent(
            uiState = uiState,
            isPicker = isPicker,
            excludeAppointmentId = excludeAppointmentId,
            onIntent = onIntent,
            onSlotSelected = onSlotSelected
        )
    } else {
        CalendarWebContent(
            uiState = uiState,
            isPicker = isPicker,
            excludeAppointmentId = excludeAppointmentId,
            onIntent = onIntent,
            onSlotSelected = onSlotSelected
        )
    }
}

/**
 * Phone layout — unchanged. Days strip stacked above the time-slot list,
 * which is the right pattern at phone width (limited height per row, no
 * room for a side panel). See [CalendarWebContent] for the desktop layout.
 */
@Composable
private fun CalendarPhoneContent(
    uiState: CalendarState,
    isPicker: Boolean,
    excludeAppointmentId: Long?,
    onIntent: (CalendarIntent) -> Unit,
    onSlotSelected: (Long, String) -> Unit
) {
    Scaffold(
        topBar = {
            CalendarTopBar(uiState = uiState, onIntent = onIntent)
        }
    ) { paddingValues ->
        // AppScaffold is a pass-through Box at Compact/Medium (unchanged
        // layout); only Expanded centers and width-caps this content.
        AppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Days Row
            DaysHeader(
                selectedDate = uiState.selectedDate,
                onDateSelected = { onIntent(CalendarIntent.SelectDate(it)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Time Slots. The appointment being edited (if any) is dropped
                // from the occupancy check, otherwise its own slot reads as
                // "occupied" and the picker refuses to let it be reselected.
                val visibleAppointments = visibleAppointments(uiState, excludeAppointmentId)
                TimeSlotsList(
                    appointments = visibleAppointments,
                    selectedDate = uiState.selectedDate,
                    onSlotClick = { time -> onSlotClick(isPicker, uiState.selectedDate, time, onSlotSelected, onIntent) },
                    onAppointmentClick = {
                        if (!isPicker) {
                            onIntent(CalendarIntent.OnAppointmentClick(it))
                        }
                    }
                )
            }
        }
        }
    }
}

/**
 * Desktop layout (Medium/Expanded). The date navigator — a horizontal strip
 * on phone, where it's competing with the time list for the same vertical
 * space — becomes a vertical panel that sits *beside* the time list instead
 * of above it, so both are visible without the days strip eating into the
 * appointment area.
 *
 * RTL note: the app forces RTL, so the first child of the [Row] below lands
 * on the *right*. The date navigator goes first so it sits on the right —
 * the natural starting point for a Persian reader — with the time list
 * filling the remaining width to its left.
 */
@Composable
private fun CalendarWebContent(
    uiState: CalendarState,
    isPicker: Boolean,
    excludeAppointmentId: Long?,
    onIntent: (CalendarIntent) -> Unit,
    onSlotSelected: (Long, String) -> Unit
) {
    Scaffold(
        topBar = {
            CalendarTopBar(uiState = uiState, onIntent = onIntent)
        }
    ) { paddingValues ->
        AppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            maxWidth = ContentWidth.Wide
        ) {
            // Date navigator stays visible during loading (unlike the phone
            // path's full-screen spinner) so the owner can keep browsing
            // dates while a previous selection's appointments are still
            // being fetched — only the time-list panel shows the spinner.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DateNavigatorPanel(
                    selectedDate = uiState.selectedDate,
                    onDateSelected = { onIntent(CalendarIntent.SelectDate(it)) },
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                )

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val visibleAppointments = visibleAppointments(uiState, excludeAppointmentId)
                        TimeSlotsList(
                            appointments = visibleAppointments,
                            selectedDate = uiState.selectedDate,
                            onSlotClick = { time -> onSlotClick(isPicker, uiState.selectedDate, time, onSlotSelected, onIntent) },
                            onAppointmentClick = {
                                if (!isPicker) {
                                    onIntent(CalendarIntent.OnAppointmentClick(it))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Drops the appointment being edited (if any) from the occupancy check —
 *  otherwise its own slot reads as "occupied" and the picker refuses to let
 *  it be reselected. Shared by both layouts so this rule can't drift. */
private fun visibleAppointments(
    uiState: CalendarState,
    excludeAppointmentId: Long?
): List<AppointmentWithDetails> = if (excludeAppointmentId != null) {
    uiState.appointments.filter { it.appointment.id != excludeAppointmentId }
} else {
    uiState.appointments
}

/** Shared slot-click routing: picker mode reports the slot back to the
 *  caller, normal mode opens create-appointment for it. */
private fun onSlotClick(
    isPicker: Boolean,
    selectedDate: Long,
    time: String,
    onSlotSelected: (Long, String) -> Unit,
    onIntent: (CalendarIntent) -> Unit
) {
    if (isPicker) {
        onSlotSelected(selectedDate, time)
    } else {
        onIntent(CalendarIntent.OnTimeSlotClick(time))
    }
}

@Composable
private fun CalendarTopBar(
    uiState: CalendarState,
    onIntent: (CalendarIntent) -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "تقویم نوبت‌دهی",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = { onIntent(CalendarIntent.BackPress) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.back))
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.isLoading)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                    .clickable(enabled = !uiState.isLoading) { onIntent(CalendarIntent.LoadData) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "بروزرسانی",
                    tint = if (uiState.isLoading)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/** Persian month name for the Jalali month number (۱..۱۲). Shared by the
 *  phone days strip and the desktop date navigator so the two can't drift. */
private fun persianMonthName(month: Int): String = when (month) {
    1 -> "فروردین"
    2 -> "اردیبهشت"
    3 -> "خرداد"
    4 -> "تیر"
    5 -> "مرداد"
    6 -> "شهریور"
    7 -> "مهر"
    8 -> "آبان"
    9 -> "آذر"
    10 -> "دی"
    11 -> "بهمن"
    12 -> "اسفند"
    else -> ""
}

@Composable
fun DaysHeader(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit
) {
    val days = remember {
        DateTimeUtils.getNextDays(30)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val selectedDateFormatted = DateTimeUtils.formatDate(selectedDate)
        val index = days.indexOfFirst { DateTimeUtils.formatDate(it) == selectedDateFormatted }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    val firstDay = days.firstOrNull() ?: DateTimeUtils.systemCurrentMilliseconds()
    val persianDate = DateTimeUtils.getJalaliDateParts(firstDay)
    val monthName = persianMonthName(persianDate.month)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$monthName ${persianDate.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(days) { dateMillis ->
                val isSelected = DateTimeUtils.formatDate(dateMillis) == DateTimeUtils.formatDate(selectedDate)

                val pDate = DateTimeUtils.getJalaliDateParts(dateMillis)
                val dayOfWeek = DateTimeUtils.getDayOfWeekName(dateMillis)

                DayItem(
                    day = pDate.dayOfMonth.toString(),
                    dayOfWeek = dayOfWeek,
                    isSelected = isSelected,
                    onClick = { onDateSelected(dateMillis) }
                )
            }
        }
    }
}

@Composable
fun DayItem(
    day: String,
    dayOfWeek: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .width(64.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayOfWeek,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Desktop date navigator: the same 30-day window as [DaysHeader], but laid
 * out as a vertical panel (month title on top, a scrollable column of day
 * rows below) so it can sit beside the time list instead of above it.
 */
@Composable
private fun DateNavigatorPanel(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember {
        DateTimeUtils.getNextDays(30)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val selectedDateFormatted = DateTimeUtils.formatDate(selectedDate)
        val index = days.indexOfFirst { DateTimeUtils.formatDate(it) == selectedDateFormatted }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    val firstDay = days.firstOrNull() ?: DateTimeUtils.systemCurrentMilliseconds()
    val persianDate = DateTimeUtils.getJalaliDateParts(firstDay)
    val monthName = persianMonthName(persianDate.month)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$monthName ${persianDate.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(days) { dateMillis ->
                    val isSelected = DateTimeUtils.formatDate(dateMillis) == DateTimeUtils.formatDate(selectedDate)

                    val pDate = DateTimeUtils.getJalaliDateParts(dateMillis)
                    val dayOfWeek = DateTimeUtils.getDayOfWeekName(dateMillis)

                    DateNavigatorRow(
                        day = pDate.dayOfMonth.toString(),
                        dayOfWeek = dayOfWeek,
                        isSelected = isSelected,
                        onClick = { onDateSelected(dateMillis) }
                    )
                }
            }
        }
    }
}

/** One row of [DateNavigatorPanel] — day number leading (right, in RTL),
 *  weekday name filling the rest. */
@Composable
private fun DateNavigatorRow(
    day: String,
    dayOfWeek: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) contentColor.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = dayOfWeek,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
fun TimeSlotsList(
    appointments: List<AppointmentWithDetails>,
    selectedDate: Long,
    onSlotClick: (String) -> Unit,
    onAppointmentClick: (Long) -> Unit
) {
    val slots = remember {
        val list = mutableListOf<String>()
        for (h in 0..23) {
            for (m in 0..59 step 10) {
                list.add("${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}")
            }
        }
        list
    }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val now = DateTimeUtils.formatTimeNow()
        val parts = now.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val index = (h * 6) + (m / 10)
        if (index in slots.indices) {
            listState.scrollToItem(index)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(slots) { time ->
            val slotTimeParts = time.split(":")
            val slotHour = slotTimeParts[0].toInt()
            val slotMinute = slotTimeParts[1].toInt()

            val startingAppointments = appointments.filter {
                val appTime = DateTimeUtils.formatTime(it.appointment.appointmentDate)
                val parts = appTime.split(":")
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                h == slotHour && m >= slotMinute && m < slotMinute + 10
            }

            val isCovered = appointments.any { appointment ->
                val appStart = appointment.appointment.appointmentDate
                val duration = appointment.appointment.serviceDuration ?: 30
                val slotInMins = slotHour * 60 + slotMinute
                val appStartParts = DateTimeUtils.formatTime(appStart).split(":")
                val appStartInMins = appStartParts[0].toInt() * 60 + appStartParts[1].toInt()
                val appEndInMins = appStartInMins + duration
                slotInMins > appStartInMins && slotInMins < appEndInMins
            }

            if (!isCovered) {
                TimeSlotRow(
                    time = time,
                    appointments = startingAppointments,
                    onSlotClick = { onSlotClick(time) },
                    onAppointmentClick = onAppointmentClick
                )
            }
        }
    }
}

@Composable
fun TimeSlotRow(
    time: String,
    appointments: List<AppointmentWithDetails>,
    onSlotClick: () -> Unit,
    onAppointmentClick: (Long) -> Unit
) {
    val height = if (appointments.isNotEmpty()) {
        val maxDuration = appointments.maxOf { it.appointment.serviceDuration ?: 10 }
        (maxDuration * 6.0).dp.coerceAtLeast(60.dp)
    } else {
        60.dp
    }

    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time Label
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSlotClick),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // Timeline Dot and Line
        Column(
            modifier = Modifier.fillMaxHeight().width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Appointments Area
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = appointments.isEmpty(), onClick = onSlotClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (appointments.isNotEmpty()) {
                appointments.forEach { appointment ->
                    val appHeight = ((appointment.appointment.serviceDuration ?: 10) * 6.0).dp

                    val isOverdue = remember(appointment) {
                        val endTime = appointment.appointment.appointmentDate + (appointment.appointment.serviceDuration ?: 30) * 60 * 1000L
                        DateTimeUtils.systemCurrentMilliseconds() > endTime && appointment.appointment.status == "WAITING"
                    }

                    // Adaptive Colors
                    val status = appointment.appointment.status
                    val (containerColor, contentColor) = when {
                        // Green = service finished. CONFIRMED only means the owner
                        // approved the booking, so it gets its own blue.
                        status == "COMPLETED" -> {
                            val bg = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9)
                            val fg = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                            bg to fg
                        }
                        status == "CONFIRMED" -> {
                            val bg = if (isDark) Color(0xFF0D47A1).copy(alpha = 0.4f) else Color(0xFFE3F2FD)
                            val fg = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0)
                            bg to fg
                        }
                        status == "NO_SHOW" || status == "CANCELLED" || isOverdue -> {
                            val bg = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE)
                            val fg = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
                            bg to fg
                        }
                        else -> {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.5f else 0.8f) to MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(appHeight)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAppointmentClick(appointment.appointment.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = appointment.visitor.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (appointment.appointment.description != null) {
                                Text(
                                    text = appointment.appointment.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${appointment.appointment.serviceDuration ?: 30} دقیقه",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty slot visual hint
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                )
            }
        }
    }
}
