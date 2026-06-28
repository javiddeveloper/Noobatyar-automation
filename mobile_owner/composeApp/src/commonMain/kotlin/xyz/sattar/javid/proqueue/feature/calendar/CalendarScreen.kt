package xyz.sattar.javid.proqueue.feature.calendar

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
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    isPicker: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: (Long, String) -> Unit,
    onNavigateToAppointmentDetails: (Long) -> Unit,
    onSlotSelected: (Long, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(CalendarIntent.LoadData)
    }

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
        onIntent = viewModel::sendIntent,
        onSlotSelected = onSlotSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreenContent(
    uiState: CalendarState,
    isPicker: Boolean,
    onIntent: (CalendarIntent) -> Unit,
    onSlotSelected: (Long, String) -> Unit
) {
    Scaffold(
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
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
                // Time Slots
                TimeSlotsList(
                    appointments = uiState.appointments,
                    selectedDate = uiState.selectedDate,
                    onSlotClick = { time ->
                        if (isPicker) {
                            onSlotSelected(uiState.selectedDate, time)
                        } else {
                            onIntent(CalendarIntent.OnTimeSlotClick(time))
                        }
                    },
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
    val monthName = when(persianDate.month) {
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
                        status == "COMPLETED" -> {
                            val bg = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9)
                            val fg = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
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
