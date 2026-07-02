package xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.back
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.OTPTextField
import xyz.sattar.javid.proqueue.core.ui.toTimerFormat
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentIntent

@Composable
fun SendOTPScreen(
    viewModel: SendOTPViewModel = koinViewModel(),
    phone: String,
    onNavigateToResetPassword: (phone: String, resetToken: String) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(SendOTPIntent.PhoneChanged(phone))
        viewModel.sendIntent(SendOTPIntent.StartTimer(120))
        viewModel.sendIntent(SendOTPIntent.SendOTPAgain(phone))
    }

    HandleSendOTPEvents(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateToResetPassword = onNavigateToResetPassword,
        onNavigateToLogin = onNavigateToLogin
    )

    SendOTPScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendOTPScreenContent(
    modifier: Modifier = Modifier,
    uiState: SendOTPState,
    snackbarHostState: SnackbarHostState,
    onIntent: (SendOTPIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "کد راستی‌آزمایی",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { onIntent(SendOTPIntent.BackPress) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
            )

        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "کد راستی‌آزمایی",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "کد ارسال شده به ${uiState.phone} را وارد کنید",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OTPTextField(
                    otp = uiState.otp,
                    onOTPChange = { onIntent(SendOTPIntent.OTPChanged(it, uiState.phone)) },
                    isError = uiState.otpError != null, errorMessage = uiState.otpError
                )

                Spacer(modifier = Modifier.height(8.dp))

                TimerAndResendButton(
                    remainingTime = uiState.remainingTime,
                    canResend = uiState.canResend,
                    isLoading = uiState.isLoading,
                    onResend = { onIntent(SendOTPIntent.SendOTPAgain(uiState.phone)) }
                )

                if (uiState.isLoading && uiState.otp.length == 6) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}


@Composable
fun TimerAndResendButton(
    remainingTime: Int,
    canResend: Boolean,
    isLoading: Boolean,
    onResend: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (remainingTime > 0) {
            Text(
                text = "ارسال مجدد کد در ${remainingTime.toTimerFormat()} دیگر ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = canResend && !isLoading) {
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onResend)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "ارسال مجدد کد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun HandleSendOTPEvents(
    events: Flow<SendOTPEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateToResetPassword: (phone: String, resetToken: String) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    events.collectWithLifecycleAware { event ->
        when (event) {
            is SendOTPEvent.NavigateToResetPassword ->
                onNavigateToResetPassword(event.phone, event.resetToken)

            is SendOTPEvent.ShowToast ->
                snackbarHostState.showSnackbar(event.message)

            SendOTPEvent.NavigateToLogin -> onNavigateToLogin()
        }
    }
}