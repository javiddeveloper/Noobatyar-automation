package xyz.sattar.javid.proqueue.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginBottomSheet(
    viewModel: LoginViewModel = koinViewModel(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            LoginEvent.NavigateToHome -> {
                onLoginSuccess()
                onDismissRequest()
            }
            is LoginEvent.ShowToast -> {
                // Toast
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Login,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "ورود یا ثبت‌نام",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (uiState.step) {
                LoginStep.PHONE -> {
                    AppTextField(
                        value = uiState.phone,
                        onValueChange = { viewModel.sendIntent(LoginIntent.PhoneChanged(it)) },
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
                }
                LoginStep.OTP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ارسال شده به: ${uiState.phone}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "ویرایش شماره",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.sendIntent(LoginIntent.ChangePhone) }
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }

                    xyz.sattar.javid.proqueue.core.ui.components.OTPTextField(
                        otp = uiState.otpCode,
                        onOTPChange = { viewModel.sendIntent(LoginIntent.OtpChanged(it)) },
                        isError = uiState.otpError != null,
                        errorMessage = uiState.otpError
                    )
                }
                LoginStep.REGISTER_NAME -> {
                    Text(
                        text = "لطفاً نام و نام خانوادگی خود را برای تکمیل ثبت‌نام وارد کنید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.sendIntent(LoginIntent.NameChanged(it)) },
                        label = "نام و نام خانوادگی",
                        isError = uiState.nameError != null,
                        errorMessage = uiState.nameError,
                        enabled = !uiState.isLoading,
                        keyboardType = KeyboardType.Text,
                        maxLength = 100,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }


            if (uiState.loginError != null) {
                Text(
                    text = uiState.loginError ?: "",
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
                when (uiState.step) {
                    LoginStep.PHONE -> {
                        AppButton(
                            text = "دریافت کد تأیید",
                            onClick = { viewModel.sendIntent(LoginIntent.SubmitPhone) },
                            enabled = !uiState.isLoading
                        )
                    }
                    LoginStep.OTP -> {
                        AppButton(
                            text = "تأیید کد",
                            onClick = { viewModel.sendIntent(LoginIntent.SubmitOtp) },
                            enabled = !uiState.isLoading
                        )
                        Text(
                            text = "ارسال مجدد کد",
                            modifier = Modifier
                                .clickable { viewModel.sendIntent(LoginIntent.ResendOtp) }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LoginStep.REGISTER_NAME -> {
                        AppButton(
                            text = "تکمیل ثبت‌نام",
                            onClick = { viewModel.sendIntent(LoginIntent.SubmitName) },
                            enabled = !uiState.isLoading
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
