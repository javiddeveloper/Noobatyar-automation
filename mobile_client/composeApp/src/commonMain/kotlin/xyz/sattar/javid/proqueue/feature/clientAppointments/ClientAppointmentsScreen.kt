package xyz.sattar.javid.proqueue.feature.clientAppointments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.ClientAppointmentDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientAppointmentsScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: ClientAppointmentsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نوبت‌های من") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadAppointments() }) {
                            Text("تلاش مجدد")
                        }
                    }
                }
                state.appointments.isEmpty() -> {
                    Text(
                        "نوبتی برای شما ثبت نشده است.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.appointments) { appointment ->
                            ClientAppointmentItem(appointment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClientAppointmentItem(appointment: ClientAppointmentDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = appointment.business.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            appointment.appointmentDate?.let { date ->
                Text(
                    text = "تاریخ نوبت: ${DateTimeUtils.formatDate(date)} ساعت ${DateTimeUtils.formatTime(date)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "وضعیت: ${translateStatus(appointment.status)}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (appointment.status in listOf("WAITING", "IN_PROGRESS")) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "تعداد افراد در انتظار قبل از شما: ${appointment.queuePosition}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                appointment.estimatedTurnTime?.let { est ->
                    Text(
                        text = "ساعت تقریبی نوبت شما: ${DateTimeUtils.formatTime(est)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun translateStatus(status: String): String {
    return when (status) {
        "PENDING_APPROVAL" -> "در انتظار تایید"
        "WAITING" -> "در صف انتظار"
        "IN_PROGRESS" -> "در حال انجام"
        "COMPLETED" -> "انجام شده"
        "NO_SHOW" -> "عدم حضور"
        "CANCELLED" -> "لغو شده"
        else -> status
    }
}
