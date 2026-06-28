package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.accept
import proqueue.composeapp.generated.resources.address
import proqueue.composeapp.generated.resources.business_name
import proqueue.composeapp.generated.resources.confirm
import proqueue.composeapp.generated.resources.create_business
import proqueue.composeapp.generated.resources.default_time_service
import proqueue.composeapp.generated.resources.example_work_end
import proqueue.composeapp.generated.resources.example_work_start
import proqueue.composeapp.generated.resources.phone
import proqueue.composeapp.generated.resources.work_end_hour
import proqueue.composeapp.generated.resources.work_start_hour
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
fun CreateBusinessRoute(
    viewModel: CreateBusinessViewModel = koinViewModel<CreateBusinessViewModel>(),
    businessId: Long? = null,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var defaultProgress by remember { mutableStateOf("") }
    var workStartHour by remember { mutableStateOf("9") }
    var workEndHour by remember { mutableStateOf("21") }

    var titleError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var defaultProgressError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var workHoursError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(businessId) {
        if (businessId != null && businessId != 0L) {
            viewModel.sendIntent(CreateBusinessIntent.LoadBusiness(businessId))
        }
    }

    LaunchedEffect(uiState.business) {
        uiState.business?.let {
            title = it.title
            phone = it.phone
            address = it.address
            defaultProgress = it.defaultServiceDuration.toString()
            workStartHour = it.workStartHour.toString()
            workEndHour = it.workEndHour.toString()
        }
    }

    HandleEvents(
        events = viewModel.events,
        onContinue = onContinue,
        onNavigateBack = onNavigateBack
    )

    CreateBusinessScreen(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        title = title,
        phone = phone,
        address = address,
        defaultProgress = defaultProgress,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        onTitle = {
            title = it
            titleError = null
        },
        onPhone = {
            phone = it
            phoneError = null
        },
        onAddress = {
            address = it
            addressError = null
        },
        onDefaultProgress = {
            defaultProgress = it
            defaultProgressError = null
        },
        onWorkStartHour = {
            workStartHour = it
            workHoursError = null
        },
        onWorkEndHour = {
            workEndHour = it
            workHoursError = null
        },
        titleError = titleError,
        phoneError = phoneError,
        addressError = addressError,
        defaultProgressError = defaultProgressError,
        workHoursError = workHoursError,
        onTitleErrorUpdate = { titleError = it },
        onPhoneErrorUpdate = { phoneError = it },
        onAddressErrorUpdate = { addressError = it },
        onDefaultProgressErrorUpdate = { defaultProgressError = it },
        onWorkHoursErrorUpdate = { workHoursError = it }
    )
}

@Composable
fun CreateBusinessScreen(
    modifier: Modifier = Modifier,
    uiState: CreateBusinessState,
    onIntent: (CreateBusinessIntent) -> Unit,
    title: String,
    phone: String,
    address: String,
    defaultProgress: String,
    workStartHour: String,
    workEndHour: String,
    onTitle: (String) -> Unit,
    onPhone: (String) -> Unit,
    onAddress: (String) -> Unit,
    onDefaultProgress: (String) -> Unit,
    onWorkStartHour: (String) -> Unit,
    onWorkEndHour: (String) -> Unit,
    titleError: String? = null,
    phoneError: String? = null,
    addressError: String? = null,
    defaultProgressError: String? = null,
    workHoursError: String? = null,
    onTitleErrorUpdate: (String?) -> Unit = {},
    onPhoneErrorUpdate: (String?) -> Unit = {},
    onAddressErrorUpdate: (String?) -> Unit = {},
    onDefaultProgressErrorUpdate: (String?) -> Unit = {},
    onWorkHoursErrorUpdate: (String?) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    action = {
                        TextButton(onClick = { data.dismiss() }) {
                            Text(stringResource(Res.string.confirm))
                        }
                    }
                ) {
                    Text(data.visuals.message)
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.businessId == 0L) stringResource(Res.string.create_business) else "ویرایش کسب‌وکار",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onIntent(CreateBusinessIntent.BackPress)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (uiState.businessCreated) {
            onIntent(CreateBusinessIntent.BusinessCreated)
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            AppTextField(
                value = title,
                onValueChange = onTitle,
                label = stringResource(Res.string.business_name),
                maxLength = 50,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Factory,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = titleError != null,
                errorMessage = titleError,
                enabled = !uiState.isLoading,
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                enabled = !uiState.isLoading,
                maxLength = 3,
                value = defaultProgress,
                onValueChange = onDefaultProgress,
                label = stringResource(Res.string.default_time_service),
                isError = defaultProgressError != null,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                errorMessage = defaultProgressError,
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppTextField(
                    enabled = !uiState.isLoading,
                    value = workStartHour,
                    onValueChange = onWorkStartHour,
                    label = stringResource(Res.string.work_start_hour),
                    isError = workHoursError != null,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                    placeholder = stringResource(Res.string.example_work_start)
                )

                AppTextField(
                    enabled = !uiState.isLoading,
                    value = workEndHour,
                    onValueChange = onWorkEndHour,
                    label = stringResource(Res.string.work_end_hour),
                    isError = workHoursError != null,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                    placeholder = stringResource(Res.string.example_work_end)
                )
            }

            if (workHoursError != null) {
                Text(
                    text = workHoursError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                enabled = !uiState.isLoading,
                maxLength = 11,
                value = phone,
                onValueChange = onPhone,
                label = stringResource(Res.string.phone),
                isError = phoneError != null,
                errorMessage = phoneError,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Phone
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                enabled = !uiState.isLoading,
                value = address,
                onValueChange = onAddress,
                label = stringResource(Res.string.address),
                maxLength = 100,
                isError = addressError != null,
                errorMessage = addressError,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AddLocation,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                maxLine = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    text = stringResource(Res.string.accept),
                    onClick = {
                        val t = title.trim()
                        val p = phone.trim()
                        val a = address.trim()
                        val d = defaultProgress.trim()
                        val ws = workStartHour.trim()
                        val we = workEndHour.trim()

                        val titleInvalid = t.length < 3 || t.length > 50
                        val phoneInvalid = p.length < 7
                        val defaultInvalid = d.isNotEmpty() && d.toIntOrNull() == null
                        val wsInt = ws.toIntOrNull()
                        val weInt = we.toIntOrNull()
                        val hoursInvalid = wsInt == null || weInt == null || wsInt < 0 || wsInt > 23 || weInt < 0 || weInt > 23 || wsInt >= weInt
                        val addressInvalid = a.isEmpty() || a.length > 300

                        onTitleErrorUpdate(if (titleInvalid) "نام باید بین ۳ تا ۵۰ کاراکتر باشد" else null)
                        onPhoneErrorUpdate(if (phoneInvalid) "شماره تلفن صحیح نیست" else null)
                        onAddressErrorUpdate(if (a.isEmpty()) "آدرس الزامی است" else if (a.length > 300) "آدرس نباید بیشتر از ۳۰۰ کاراکتر باشد" else null)
                        onDefaultProgressErrorUpdate(if (defaultInvalid) "مدت زمان سرویس باید عدد باشد" else null)
                        onWorkHoursErrorUpdate(if (hoursInvalid) "ساعات کاری معتبر نیستند" else null)

                        if (!titleInvalid && !phoneInvalid && !defaultInvalid && !hoursInvalid && !addressInvalid) {
                            onIntent(
                                CreateBusinessIntent.CreateBusiness(
                                    t,
                                    p,
                                    a,
                                    d,
                                    wsInt!!,
                                    weInt!!
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            
            if (uiState.business != null) {
                Text(uiState.business.title)
            }
        }
    }
}

@Composable
fun HandleEvents(
    events: Flow<CreateBusinessEvent>,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    events.collectWithLifecycleAware {
        when (it) {
            CreateBusinessEvent.NavigateToBusiness -> {
                onContinue()
            }

            CreateBusinessEvent.BackPressed -> {
                onNavigateBack()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDashboardScreen() {
    AppTheme {
        CreateBusinessScreen(
            uiState = CreateBusinessState(),
            onIntent = {},
            title = "",
            phone = "",
            address = "",
            defaultProgress = "",
            workStartHour = "9",
            workEndHour = "21",
            onTitle = {},
            onPhone = {},
            onAddress = {},
            onDefaultProgress = {},
            onWorkStartHour = {},
            onWorkEndHour = {},
            titleError = null,
            phoneError = null,
            addressError = null,
            defaultProgressError = null,
            workHoursError = null,
            onTitleErrorUpdate = {},
            onPhoneErrorUpdate = {},
            onAddressErrorUpdate = {},
            onDefaultProgressErrorUpdate = {},
            onWorkHoursErrorUpdate = {},
        )
    }
}
