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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ListItem
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
import xyz.sattar.javid.proqueue.core.ui.components.EmergencyNoticeSection
import xyz.sattar.javid.proqueue.core.ui.components.ImageCropperDialog
import xyz.sattar.javid.proqueue.core.ui.components.ServiceDurationBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.formatServiceDuration
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import androidx.compose.foundation.layout.Box
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.String
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.toImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.AddLocation
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun CreateBusinessRoute(
    viewModel: CreateBusinessViewModel = koinViewModel<CreateBusinessViewModel>(),
    businessId: Long? = null,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BusinessCategory.OTHER) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var defaultProgress by remember { mutableStateOf("") }
    var workStartHour by remember { mutableStateOf("9") }
    var workEndHour by remember { mutableStateOf("21") }
    var allowAnonymousView by remember { mutableStateOf(false) }
    var notifyOwnerBySms by remember { mutableStateOf(true) }
    var bio by remember { mutableStateOf("") }
    var logoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var maxAppointmentsPerHour by remember { mutableStateOf("") }
    var depositMode by remember { mutableStateOf(DepositMode.NONE.value) }
    var depositAmount by remember { mutableStateOf("") }
    // CASH ("پرداخت در محل") needs no card number or merchant id to work, so it
    // is the one method every business can accept from the moment it's
    // created. Starting from an empty set here sent accepted_payment_methods:
    // [] on every create-business request, which overrode the backend's own
    // default and left a brand-new business with zero working payment options.
    var acceptedPaymentMethods by remember { mutableStateOf(setOf("CASH")) }
    var cardNumber by remember { mutableStateOf("") }
    var cardOwnerName by remember { mutableStateOf("") }
    var merchantId by remember { mutableStateOf("") }
    var paymentLink by remember { mutableStateOf("") }
    var noticeEnabled by remember { mutableStateOf(false) }
    var noticeMessage by remember { mutableStateOf("") }

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

    LaunchedEffect(Unit) {
        viewModel.sendIntent(CreateBusinessIntent.LoadEntitlements)
    }

    LaunchedEffect(uiState.business) {
        uiState.business?.let {
            title = it.title
            category = it.category
            phone = it.phone
            address = it.address
            defaultProgress = it.defaultServiceDuration.toString()
            workStartHour = it.workStartHour.toString()
            workEndHour = it.workEndHour.toString()
            allowAnonymousView = it.allowAnonymousView
            notifyOwnerBySms = it.notifyOwnerBySms
            maxAppointmentsPerHour = it.maxAppointmentsPerHour?.toString() ?: ""
            depositMode = it.depositMode ?: DepositMode.NONE.value
            depositAmount = it.depositAmount?.toString() ?: ""
            acceptedPaymentMethods = it.acceptedPaymentMethods?.toSet() ?: setOf()
            cardNumber = it.cardNumber
            cardOwnerName = it.cardOwnerName
            merchantId = it.merchantId
            paymentLink = it.paymentLink
            bio = it.bio
            logoBytes = it.logoBytes
            noticeEnabled = it.noticeEnabled
            noticeMessage = it.noticeMessage
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
        category = category,
        phone = phone,
        address = address,
        defaultProgress = defaultProgress,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        allowAnonymousView = allowAnonymousView,
        notifyOwnerBySms = notifyOwnerBySms,
        bio = bio,
        logoBytes = logoBytes,
        maxAppointmentsPerHour = maxAppointmentsPerHour,
        depositMode = depositMode,
        depositAmount = depositAmount,
        acceptedPaymentMethods = acceptedPaymentMethods,
        cardNumber = cardNumber,
        cardOwnerName = cardOwnerName,
        merchantId = merchantId,
        paymentLink = paymentLink,
        noticeEnabled = noticeEnabled,
        noticeMessage = noticeMessage,
        onNoticeEnabled = { noticeEnabled = it },
        onNoticeMessage = { noticeMessage = it },
        onMaxAppointmentsPerHour = { maxAppointmentsPerHour = it },
        onDepositMode = { depositMode = it },
        onDepositAmount = { depositAmount = it },
        onAcceptedPaymentMethods = { acceptedPaymentMethods = it },
        onCardNumber = { cardNumber = it },
        onCardOwnerName = { cardOwnerName = it },
        onMerchantId = { merchantId = it },
        onPaymentLink = { paymentLink = it },
        onUpgrade = { planId -> viewModel.sendIntent(CreateBusinessIntent.UpgradePlan(planId)) },
        onTitle = {
            title = it
            titleError = null
        },
        onCategory = {
            category = it
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
        onAllowAnonymousView = { allowAnonymousView = it },
        onNotifyOwnerBySms = { notifyOwnerBySms = it },
        onBio = { bio = it },
        onLogoBytes = { logoBytes = it },
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
    category: BusinessCategory,
    phone: String,
    address: String,
    defaultProgress: String,
    workStartHour: String,
    workEndHour: String,
    onTitle: (String) -> Unit,
    onCategory: (BusinessCategory) -> Unit,
    onPhone: (String) -> Unit,
    onAddress: (String) -> Unit,
    onDefaultProgress: (String) -> Unit,
    onWorkStartHour: (String) -> Unit,
    onWorkEndHour: (String) -> Unit,
    allowAnonymousView: Boolean,
    notifyOwnerBySms: Boolean,
    onAllowAnonymousView: (Boolean) -> Unit,
    onNotifyOwnerBySms: (Boolean) -> Unit,
    bio: String,
    logoBytes: ByteArray?,
    onBio: (String) -> Unit,
    onLogoBytes: (ByteArray?) -> Unit,
    maxAppointmentsPerHour: String,
    depositMode: String,
    depositAmount: String,
    acceptedPaymentMethods: Set<String>,
    cardNumber: String,
    cardOwnerName: String,
    merchantId: String,
    paymentLink: String,
    noticeEnabled: Boolean,
    noticeMessage: String,
    onNoticeEnabled: (Boolean) -> Unit,
    onNoticeMessage: (String) -> Unit,
    onMaxAppointmentsPerHour: (String) -> Unit,
    onDepositMode: (String) -> Unit,
    onDepositAmount: (String) -> Unit,
    onAcceptedPaymentMethods: (Set<String>) -> Unit,
    onCardNumber: (String) -> Unit,
    onCardOwnerName: (String) -> Unit,
    onMerchantId: (String) -> Unit,
    onPaymentLink: (String) -> Unit,
    onUpgrade: (Int) -> Unit,
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
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDurationSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Photo waiting to be cropped: the picker hands over the raw file, and the
    // cropper turns it into the square logo that actually gets uploaded.
    var pendingCrop by remember { mutableStateOf<ByteArray?>(null) }

    val singleImagePicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays ->
            val bytes = byteArrays.firstOrNull()
            if (bytes != null) {
                pendingCrop = bytes
            }
        }
    )

    pendingCrop?.let { raw ->
        val cropBitmap = remember(raw) {
            try {
                raw.toImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (cropBitmap != null) {
            ImageCropperDialog(
                source = raw,
                bitmap = cropBitmap,
                onDismiss = { pendingCrop = null },
                onCropped = { cropped ->
                    pendingCrop = null
                    onLogoBytes(cropped)
                }
            )
        } else {
            // Undecodable file — fall back to uploading it untouched rather than
            // leaving the user with a dialog that can never open.
            LaunchedEffect(raw) {
                onLogoBytes(raw)
                pendingCrop = null
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onIntent(CreateBusinessIntent.ClearMessage)
        }
    }
    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
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
        },
        bottomBar = {
            if (!uiState.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
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
                            // The duration now comes from a fixed list, so the
                            // only way it can be wrong is not having been picked.
                            val defaultInvalid = d.toIntOrNull() == null
                            val wsInt = ws.toIntOrNull()
                            val weInt = we.toIntOrNull()
                            val hoursInvalid =
                                wsInt == null || weInt == null || wsInt < 0 || wsInt > 23 || weInt < 0 || weInt > 23 || wsInt >= weInt
                            val addressInvalid = a.isEmpty() || a.length > 300

                            onTitleErrorUpdate(if (titleInvalid) "نام باید بین ۳ تا ۵۰ کاراکتر باشد" else null)
                            onPhoneErrorUpdate(if (phoneInvalid) "شماره تلفن صحیح نیست" else null)
                            onAddressErrorUpdate(if (a.isEmpty()) "آدرس الزامی است" else if (a.length > 300) "آدرس نباید بیشتر از ۳۰۰ کاراکتر باشد" else null)
                            onDefaultProgressErrorUpdate(if (defaultInvalid) "مدت زمان سرویس را انتخاب کنید" else null)
                            onWorkHoursErrorUpdate(if (hoursInvalid) "ساعات کاری معتبر نیستند" else null)

                            if (!titleInvalid && !phoneInvalid && !defaultInvalid && !hoursInvalid && !addressInvalid) {
                                onIntent(
                                    CreateBusinessIntent.CreateBusiness(
                                        title = t,
                                        category = category,
                                        phone = p,
                                        address = a,
                                        defaultProgress = d,
                                        workStartHour = wsInt!!,
                                        workEndHour = weInt!!,
                                        allowAnonymousView = allowAnonymousView,
                                        notifyOwnerBySms = notifyOwnerBySms,
                                        bio = bio.trim(),
                                        logoBytes = logoBytes,
                                        maxAppointmentsPerHour = maxAppointmentsPerHour.toIntOrNull(),
                                        depositMode = depositMode,
                                        depositAmount = depositAmount.toIntOrNull() ?: 0,
                                        acceptedPaymentMethods = acceptedPaymentMethods.joinToString(","),
                                        cardNumber = cardNumber,
                                        cardOwnerName = cardOwnerName,
                                        merchantId = merchantId,
                                        paymentLink = paymentLink,
                                        noticeEnabled = noticeEnabled,
                                        noticeMessage = noticeMessage.trim(),
                                        // Owned by the reminder-messages screen;
                                        // pass the loaded value straight through
                                        // so editing the business never resets it.
                                        reminderDelivery = uiState.business?.reminderDelivery
                                            ?: ReminderDelivery.MANUAL.value
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        isLoading = uiState.isLoading
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) {
                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                }
            }
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

            // Avatar Section
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                    .clickable {
                        singleImagePicker.launch()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (logoBytes != null) {
                    val bitmap = remember(logoBytes) {
                        try {
                            logoBytes.toImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            "عکس انتخاب شد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!uiState.business?.logoPath.isNullOrEmpty()) {
                    val path = uiState.business!!.logoPath!!
                    val url = if (path.startsWith("http")) path else "${xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL}$path"
                    AsyncImage(
                        model = url,
                        contentDescription = "Business Logo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Logo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val selectedCategoryText = category.persianName

            Box(modifier = Modifier.fillMaxWidth().clickable {
                if (!uiState.isLoading) showCategorySheet = true
            }) {
                AppTextField(
                    value = selectedCategoryText,
                    onValueChange = {},
                    label = "دسته‌بندی",
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = false, // Disable to act just as a clickable button
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                value = bio,
                onValueChange = onBio,
                label = "معرفی کسب‌وکار (Bio)",
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
                enabled = !uiState.isLoading,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Duration is picked from a fixed 5-minute ladder rather than typed:
            // free text let owners save slots the calendar can't lay out.
            Box(modifier = Modifier.fillMaxWidth().clickable {
                if (!uiState.isLoading) showDurationSheet = true
            }) {
                AppTextField(
                    value = defaultProgress.toIntOrNull()?.let { formatServiceDuration(it) } ?: "",
                    onValueChange = {},
                    label = stringResource(Res.string.default_time_service),
                    isError = defaultProgressError != null,
                    errorMessage = defaultProgressError,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false, // Disable to act just as a clickable button
                )
            }

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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onAllowAnonymousView(!allowAnonymousView) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "نمایش اطلاعات تماس به کاربران مهمان",
                    style = MaterialTheme.typography.bodyLarge
                )
                androidx.compose.material3.Switch(
                    checked = allowAnonymousView,
                    onCheckedChange = { onAllowAnonymousView(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onNotifyOwnerBySms(!notifyOwnerBySms) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "اطلاع‌رسانی پیامکی نوبت جدید به خودم",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    androidx.compose.material3.Switch(
                        checked = notifyOwnerBySms,
                        onCheckedChange = { onNotifyOwnerBySms(it) }
                    )
                }
                Text(
                    text = "این پیامک از سهمیه‌ی پیامک شما کم می‌شود. اگر نوبت‌های " +
                            "جدید را در همین اپ دنبال می‌کنید، خاموش کردنش هزینه‌ی هر " +
                            "نوبت را یک پیامک کمتر می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            EmergencyNoticeSection(
                enabled = noticeEnabled,
                message = noticeMessage,
                onEnabledChange = onNoticeEnabled,
                onMessageChange = onNoticeMessage,
                isEditable = !uiState.isLoading
            )

            // Advanced settings (payment / capacity / reminders) now live on a
            // separate screen, reachable from the profile. The values are still
            // loaded and saved here so editing the business doesn't drop them.

            // Spacer replaced by bottomBar button


            if (uiState.business != null) {
                Text(uiState.business.title)
            }
        }
    }

    if (showDurationSheet) {
        ServiceDurationBottomSheet(
            selectedMinutes = defaultProgress.toIntOrNull(),
            onDurationSelected = { minutes ->
                onDefaultProgress(minutes.toString())
                onDefaultProgressErrorUpdate(null)
                showDurationSheet = false
            },
            onDismiss = { showDurationSheet = false }
        )
    }

    if (showCategorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        var searchQuery by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "انتخاب دسته‌بندی",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جستجو...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                val filteredOptions = BusinessCategory.entries.filter {
                    it.persianName.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredOptions) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCategory(option)
                                    showCategorySheet = false
                                }
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = option.persianName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
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
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    events.collectWithLifecycleAware {
        when (it) {
            CreateBusinessEvent.NavigateToBusiness -> {
                onContinue()
            }

            CreateBusinessEvent.BackPressed -> {
                onNavigateBack()
            }

            is CreateBusinessEvent.OpenUrl -> {
                uriHandler.openUri(it.url)
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
            category = BusinessCategory.OTHER,
            phone = "",
            address = "",
            defaultProgress = "",
            workStartHour = "9",
            workEndHour = "21",
            onTitle = {},
            onCategory = {},
            onPhone = {},
            onAddress = {},
            onDefaultProgress = {},
            onWorkStartHour = {},
            onWorkEndHour = {},
            allowAnonymousView = false,
            notifyOwnerBySms = true,
            onAllowAnonymousView = {},
            onNotifyOwnerBySms = {},
            bio = "",
            logoBytes = null,
            onBio = {},
            onLogoBytes = {},
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
            maxAppointmentsPerHour = "String",
            depositMode = DepositMode.NONE.value,
            depositAmount = "String",
            acceptedPaymentMethods = setOf(),
            cardNumber = "",
            cardOwnerName = "",
            merchantId = "",
            paymentLink = "",
            onMaxAppointmentsPerHour = {},
            onDepositMode = {},
            onDepositAmount = {},
            onAcceptedPaymentMethods = {},
            onCardNumber = {},
            onCardOwnerName = {},
            onMerchantId = {},
            onPaymentLink = {},
            onUpgrade = {},
            noticeEnabled = false,
            noticeMessage = "",
            onNoticeEnabled = {},
            onNoticeMessage = {},
        )
    }
}
