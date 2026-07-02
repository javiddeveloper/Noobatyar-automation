package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.feature.login.LoginBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientBusinessDetailScreen(
    businessId: Long,
    viewModel: ClientBusinessDetailViewModel = koinViewModel(),
    isLoggedIn: Boolean,
    selectedDate: Long? = null,
    selectedTime: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: (businessId: Long) -> Unit,
    onNavigateToCalendar: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLoginBottomSheet by remember { mutableStateOf(false) }
    var showCreateAppointmentBottomSheet by remember { mutableStateOf(selectedDate != null) }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(businessId) {
        viewModel.loadBusiness(businessId)
    }

    if (showLoginBottomSheet) {
        LoginBottomSheet(
            onDismissRequest = { showLoginBottomSheet = false },
            onLoginSuccess = {
                showLoginBottomSheet = false
                showCreateAppointmentBottomSheet = true
            }
        )
    }

    if (showCreateAppointmentBottomSheet) {
        ClientCreateAppointmentBottomSheet(
            businessId = businessId,
            initialDate = selectedDate,
            initialTime = selectedTime,
            onDismissRequest = { showCreateAppointmentBottomSheet = false },
            onSubmit = { date, time ->
                viewModel.createAppointment(businessId, date, time) { success, message ->
                    showCreateAppointmentBottomSheet = false
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                        if (success) {
                            onNavigateBack() // Or navigate to appointments list
                        }
                    }
                }
            },
            onNavigateToCalendar = onNavigateToCalendar
        )
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("جزئیات کسب و کار") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "برگشت")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.business != null -> {
                    val business = uiState.business!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Factory,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = business.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = business.address ?: "آدرسی ثبت نشده است",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Spacer(modifier = Modifier.weight(1f))

                        AppButton(
                            text = "رزرو نوبت",
                            onClick = {
                                if (isLoggedIn) {
                                    showCreateAppointmentBottomSheet = true
                                } else {
                                    showLoginBottomSheet = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
