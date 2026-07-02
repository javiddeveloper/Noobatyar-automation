package xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.back
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP.SendOTPIntent

@Composable
fun ResetPasswordScreen(
    viewModel: ResetPasswordViewModel = koinViewModel(),
    phone: String,
    resetToken: String,
    onNavigateToLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.initialize(phone, resetToken)
    }

    HandleResetPasswordEvents(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateToLogin = onNavigateToLogin
    )

    ResetPasswordScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreenContent(
    modifier: Modifier = Modifier,
    uiState: ResetPasswordState,
    snackbarHostState: SnackbarHostState,
    onIntent: (ResetPasswordIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تغییر رمز عبور",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { onIntent(ResetPasswordIntent.NavigateToLogin) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
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
            ) {Spacer(modifier = Modifier.height(24.dp))

                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "رمز عبور جدید را وارد کنید",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = uiState.newPassword,
                    onValueChange = { onIntent(ResetPasswordIntent.NewPasswordChanged(it)) },
                    label = "رمز عبور جدید",
                    isError = uiState.newPasswordError != null,
                    errorMessage = uiState.newPasswordError,
                    enabled = !uiState.isLoading,
                    keyboardType = KeyboardType.Password,
                    maxLength = 100,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                AppTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { onIntent(ResetPasswordIntent.ConfirmPasswordChanged(it)) },
                    label = "تکرار رمز عبور",
                    isError = uiState.confirmPasswordError != null,
                    errorMessage = uiState.confirmPasswordError,
                    enabled = !uiState.isLoading,
                    keyboardType = KeyboardType.Password,
                    maxLength = 100,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    AppButton(
                        text = "تغییر رمز عبور",
                        onClick = { onIntent(ResetPasswordIntent.Submit) }
                    )
                }
            }
        }
    }
}

@Composable
fun HandleResetPasswordEvents(
    events: Flow<ResetPasswordEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateToLogin: () -> Unit
) {
    events.collectWithLifecycleAware { event ->
        when (event) {
            ResetPasswordEvent.NavigateToLogin -> onNavigateToLogin()
            is ResetPasswordEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)}
    }
}
