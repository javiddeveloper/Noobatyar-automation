package xyz.sattar.javid.proqueue.feature.login

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToForgetPassword: (phone:String) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleLoginEvents(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateToHome = onNavigateToHome,
        onNavigateToForgetPassword = onNavigateToForgetPassword,
        onNavigateToRegister = onNavigateToRegister,
    )

    LoginScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreenContent(
    modifier: Modifier = Modifier,
    uiState: LoginState,
    snackbarHostState: SnackbarHostState,
    onIntent: (LoginIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "نوبت یار ورود",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "ورود به حساب کاربری",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = uiState.phone,
                    onValueChange = { onIntent(LoginIntent.PhoneChanged(it)) },
                    label = "شماره موبایل",
                    isError = uiState.phoneError != null,
                    errorMessage = uiState.phoneError,
                    enabled = !uiState.isLoading,
                    keyboardType = KeyboardType.Phone,
                    maxLength = 11,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PhoneIphone,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                AppTextField(
                    value = uiState.password,
                    onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
                    label = "رمز عبور",
                    isError = uiState.passwordError != null,
                    errorMessage = uiState.passwordError,
                    enabled = !uiState.isLoading,
                    keyboardType = KeyboardType.Password,
                    maxLength = 100,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )


                Text(
                    modifier = Modifier.clickable {
                        onIntent(LoginIntent.ForgetPassword(uiState.phone))
                    },
                    text = "فراموشی رمز عبور",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.loginError != null) {
                    Text(
                        text = uiState.loginError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
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
                        text = "ورود",
                        onClick = { onIntent(LoginIntent.Submit) },
                        enabled = !uiState.isLoading
                    )
                    AppButton(
                        text = "ثبت نام",
                        onClick = { onIntent(LoginIntent.Register) },
                        isOutlined = true,
                        enabled = !uiState.isLoading
                    )
                }
            }
        }
    }
}

@Composable
fun HandleLoginEvents(
    events: Flow<LoginEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateToHome: () -> Unit,
    onNavigateToForgetPassword: (phone:String) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    events.collectWithLifecycleAware { event ->
        when (event) {
            LoginEvent.NavigateToHome -> onNavigateToHome()
            is LoginEvent.NavigateToForgetPassword -> onNavigateToForgetPassword(event.phone)
            LoginEvent.NavigateToRegister -> onNavigateToRegister()
            is LoginEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
        }
    }
}

@Preview
@Composable
fun PreviewLoginScreen() {
    AppTheme {
        LoginScreenContent(
            uiState = LoginState(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {}
        )
    }
}
