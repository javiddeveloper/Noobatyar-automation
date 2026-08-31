package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
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
import proqueue.composeapp.generated.resources.cancel
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
import xyz.sattar.javid.proqueue.core.ui.components.ImageCropperDialog
import xyz.sattar.javid.proqueue.core.ui.components.ModerationBanner
import xyz.sattar.javid.proqueue.core.ui.components.SelectedServiceChipsRow
import xyz.sattar.javid.proqueue.core.ui.components.ServiceCatalogBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.ServiceDurationBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.formatServiceDuration
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.ModerationStatus
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import androidx.compose.foundation.layout.Box
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.String
import xyz.sattar.javid.proqueue.core.utils.rememberImagePicker
import xyz.sattar.javid.proqueue.core.utils.toImageBitmapOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.AddLocation
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth


@Composable
fun CreateBusinessRoute(
    viewModel: CreateBusinessViewModel = koinViewModel<CreateBusinessViewModel>(),
    businessId: Long? = null,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToPayment: (String) -> Unit = {},
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
    var services by remember { mutableStateOf<List<String>>(emptyList()) }
    var allowClientAddService by remember { mutableStateOf(false) }
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

    // The pickable chips are category-scoped, so they reload whenever the
    // owner switches category — a salon must not be offered clinic services.
    LaunchedEffect(category) {
        viewModel.sendIntent(CreateBusinessIntent.LoadServiceCatalog(category))
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
            services = it.services
            allowClientAddService = it.allowClientAddService
            logoBytes = it.logoBytes
        }
    }

    HandleEvents(
        events = viewModel.events,
        onContinue = onContinue,
        onNavigateBack = onNavigateBack,
        onOpenPaymentUrl = onNavigateToPayment
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
        services = services,
        allowClientAddService = allowClientAddService,
        onToggleService = { name ->
            services = if (services.contains(name)) services - name else services + name
        },
        onAddService = { name ->
            // Selected locally right away rather than waiting for the catalog
            // round-trip: the owner just typed it, so it is obviously wanted on
            // their own menu. The intent adds it to the shared category catalog
            // so other businesses can pick it too.
            if (!services.contains(name)) services = services + name
            viewModel.sendIntent(CreateBusinessIntent.AddServiceCatalogItem(category, name))
        },
        onAllowClientAddService = { allowClientAddService = it },
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
    services: List<String>,
    allowClientAddService: Boolean,
    onToggleService: (String) -> Unit,
    onAddService: (String) -> Unit,
    onAllowClientAddService: (Boolean) -> Unit,
    onLogoBytes: (ByteArray?) -> Unit,
    maxAppointmentsPerHour: String,
    depositMode: String,
    depositAmount: String,
    acceptedPaymentMethods: Set<String>,
    cardNumber: String,
    cardOwnerName: String,
    merchantId: String,
    paymentLink: String,
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
    var showServicesSheet by remember { mutableStateOf(false) }

    // Save action parked behind the "this goes back for review" confirmation.
    var pendingSubmit by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Photo waiting to be cropped: the picker hands over the raw file, and the
    // cropper turns it into the square logo that actually gets uploaded.
    var pendingCrop by remember { mutableStateOf<ByteArray?>(null) }

    val singleImagePicker = rememberImagePicker(
        onSingleImagePicked = { bytes ->
            pendingCrop = bytes
        }
    )

    pendingCrop?.let { raw ->
        val cropBitmap = remember(raw) {
            raw.toImageBitmapOrNull()
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

    // Shared submit logic for both call sites (bottomBar button on phone,
    // inline action card on web) so they can never drift apart on
    // validation or on which fields get sent.
    val onSubmit: () -> Unit = submit@{
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
            val intent =
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
                    // Owned by the dedicated emergency-notice
                    // screen (Settings); pass the loaded
                    // value straight through so editing the
                    // business never resets it.
                    noticeEnabled = uiState.business?.noticeEnabled ?: false,
                    noticeMessage = uiState.business?.noticeMessage ?: "",
                    // Owned by the reminder-messages screen;
                    // pass the loaded value straight through
                    // so editing the business never resets it.
                    reminderDelivery = uiState.business?.reminderDelivery
                        ?: ReminderDelivery.MANUAL.value,
                    services = services,
                    allowClientAddService = allowClientAddService
                )

            // Editing title/bio/address/logo on an approved
            // business sends it back to the review queue and
            // drops it out of public listings, so say that
            // before the save — not after. Saving anything
            // else (hours, payment, switches) goes straight
            // through so we don't nag on every change.
            // Must track backend/business/models.py's MODERATED_FIELDS
            // exactly: title, bio, address, logo, notice_message. This
            // screen has no notice_message field yet, so it's absent
            // here — but if one is ever added to this or ANY screen
            // that saves a Business, it has to be added to this check
            // too, or an owner can silently de-list an approved
            // business (e.g. via the "temporarily closed" banner) with
            // no warning shown.
            val saved = uiState.business
            val moderatedFieldChanged = saved != null && (
                    t != saved.title ||
                            bio.trim() != saved.bio ||
                            a != saved.address ||
                            logoBytes != null
                    )
            if (saved?.moderationStatus == ModerationStatus.APPROVED &&
                moderatedFieldChanged
            ) {
                pendingSubmit = { onIntent(intent) }
            } else {
                onIntent(intent)
            }
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
            // Pinned to the viewport bottom only on phone (thumb reach). On
            // web the same action rides the normal scroll instead — see the
            // SubmitActionCard at the end of [CreateBusinessWebContent].
            if (!uiState.isLoading && LocalWindowSize.current == WindowSize.Compact) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                    AppButton(
                        text = stringResource(Res.string.accept),
                        onClick = onSubmit,
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
        } else if (uiState.businessCreated && uiState.businessId != 0L) {
            // Edit flow: nothing new to explain, leave straight away. A brand
            // new business gets the "waiting for approval" notice below first.
            onIntent(CreateBusinessIntent.BusinessCreated)
        }
        if (LocalWindowSize.current == WindowSize.Compact) {
            CreateBusinessPhoneContent(
                modifier = modifier,
                paddingValues = paddingValues,
                uiState = uiState,
                category = category,
                title = title,
                onTitle = onTitle,
                titleError = titleError,
                bio = bio,
                onBio = onBio,
                defaultProgress = defaultProgress,
                defaultProgressError = defaultProgressError,
                services = services,
                onToggleService = onToggleService,
                allowClientAddService = allowClientAddService,
                onAllowClientAddService = onAllowClientAddService,
                workStartHour = workStartHour,
                workEndHour = workEndHour,
                workHoursError = workHoursError,
                onWorkStartHour = onWorkStartHour,
                onWorkEndHour = onWorkEndHour,
                phone = phone,
                onPhone = onPhone,
                phoneError = phoneError,
                address = address,
                onAddress = onAddress,
                addressError = addressError,
                allowAnonymousView = allowAnonymousView,
                onAllowAnonymousView = onAllowAnonymousView,
                notifyOwnerBySms = notifyOwnerBySms,
                onNotifyOwnerBySms = onNotifyOwnerBySms,
                logoBytes = logoBytes,
                onLogoPickerClick = { singleImagePicker.launch() },
                onCategoryClick = { if (!uiState.isLoading) showCategorySheet = true },
                onDurationClick = { if (!uiState.isLoading) showDurationSheet = true },
                onServicesClick = { if (!uiState.isLoading) showServicesSheet = true }
            )
        } else {
            CreateBusinessWebContent(
                modifier = modifier,
                paddingValues = paddingValues,
                uiState = uiState,
                category = category,
                title = title,
                onTitle = onTitle,
                titleError = titleError,
                bio = bio,
                onBio = onBio,
                defaultProgress = defaultProgress,
                defaultProgressError = defaultProgressError,
                services = services,
                onToggleService = onToggleService,
                allowClientAddService = allowClientAddService,
                onAllowClientAddService = onAllowClientAddService,
                workStartHour = workStartHour,
                workEndHour = workEndHour,
                workHoursError = workHoursError,
                onWorkStartHour = onWorkStartHour,
                onWorkEndHour = onWorkEndHour,
                phone = phone,
                onPhone = onPhone,
                phoneError = phoneError,
                address = address,
                onAddress = onAddress,
                addressError = addressError,
                allowAnonymousView = allowAnonymousView,
                onAllowAnonymousView = onAllowAnonymousView,
                notifyOwnerBySms = notifyOwnerBySms,
                onNotifyOwnerBySms = onNotifyOwnerBySms,
                logoBytes = logoBytes,
                onLogoPickerClick = { singleImagePicker.launch() },
                onCategoryClick = { if (!uiState.isLoading) showCategorySheet = true },
                onDurationClick = { if (!uiState.isLoading) showDurationSheet = true },
                onServicesClick = { if (!uiState.isLoading) showServicesSheet = true },
                onSubmit = onSubmit
            )
        }
    }

    // Re-review warning: only reached when a moderated field of an approved
    // business actually changed.
    pendingSubmit?.let { submit ->
        AlertDialog(
            onDismissRequest = { pendingSubmit = null },
            title = { Text("ارسال دوباره برای بررسی") },
            text = {
                Text(
                    "با تغییر نام، معرفی، آدرس یا لوگو، کسب‌وکار شما دوباره به صف " +
                            "بررسی ادمین می‌رود و تا زمان تأیید، برای مراجعین نمایش داده " +
                            "نمی‌شود. نوبت‌های ثبت‌شده‌ی فعلی تغییری نمی‌کنند."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSubmit = null
                    submit()
                }) {
                    Text("ذخیره و ارسال برای بررسی")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubmit = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    // Post-create expectation: the business is saved but not live yet.
    if (uiState.businessCreated && uiState.businessId == 0L) {
        AlertDialog(
            onDismissRequest = { onIntent(CreateBusinessIntent.BusinessCreated) },
            title = { Text("کسب‌وکار شما ثبت شد") },
            text = {
                Text(
                    "کسب‌وکار شما پس از تأیید ادمین منتشر می‌شود. تا آن زمان می‌توانید " +
                            "تنظیمات را کامل کنید، اما مراجعین آن را نمی‌بینند."
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(CreateBusinessIntent.BusinessCreated) }) {
                    Text(stringResource(Res.string.confirm))
                }
            }
        )
    }

    if (showServicesSheet) {
        ServiceCatalogBottomSheet(
            // The owner's own picks stay visible even if the shared catalog
            // hasn't loaded (or no longer contains a name they added earlier),
            // so opening the sheet can never silently drop their menu.
            catalog = (uiState.serviceCatalog + services).distinct(),
            selected = services,
            isLoading = uiState.isServiceCatalogLoading,
            onToggle = onToggleService,
            onAddNew = onAddService,
            onDismiss = { showServicesSheet = false }
        )
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

/**
 * Phone layout — unchanged. The exact original single-column form: avatar,
 * category, name, bio, duration, services, toggles, hours, phone, address,
 * remaining toggles. See [CreateBusinessWebContent] for the desktop layout.
 */
@Composable
private fun CreateBusinessPhoneContent(
    modifier: Modifier = Modifier,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    uiState: CreateBusinessState,
    category: BusinessCategory,
    title: String,
    onTitle: (String) -> Unit,
    titleError: String?,
    bio: String,
    onBio: (String) -> Unit,
    defaultProgress: String,
    defaultProgressError: String?,
    services: List<String>,
    onToggleService: (String) -> Unit,
    allowClientAddService: Boolean,
    onAllowClientAddService: (Boolean) -> Unit,
    workStartHour: String,
    workEndHour: String,
    workHoursError: String?,
    onWorkStartHour: (String) -> Unit,
    onWorkEndHour: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    phoneError: String?,
    address: String,
    onAddress: (String) -> Unit,
    addressError: String?,
    allowAnonymousView: Boolean,
    onAllowAnonymousView: (Boolean) -> Unit,
    notifyOwnerBySms: Boolean,
    onNotifyOwnerBySms: (Boolean) -> Unit,
    logoBytes: ByteArray?,
    onLogoPickerClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDurationClick: () -> Unit,
    onServicesClick: () -> Unit
) {
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

        // Why the owner is here: a rejected/suspended business shows the
        // reviewer's note right above the fields they need to fix.
        uiState.business?.let { saved ->
            ModerationBanner(business = saved)
            if (saved.moderationStatus != null &&
                saved.moderationStatus != ModerationStatus.APPROVED
            ) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        LogoPickerAvatar(uiState = uiState, logoBytes = logoBytes, onClick = onLogoPickerClick)
        Spacer(modifier = Modifier.height(16.dp))

        CategoryPickerField(uiState = uiState, category = category, onClick = onCategoryClick)
        Spacer(modifier = Modifier.height(16.dp))

        BusinessTitleField(uiState = uiState, title = title, onTitle = onTitle, titleError = titleError)
        Spacer(modifier = Modifier.height(16.dp))

        BusinessBioField(uiState = uiState, bio = bio, onBio = onBio)
        Spacer(modifier = Modifier.height(16.dp))

        DurationPickerField(
            uiState = uiState,
            defaultProgress = defaultProgress,
            defaultProgressError = defaultProgressError,
            onClick = onDurationClick
        )
        Spacer(modifier = Modifier.height(16.dp))

        ServicesSection(
            uiState = uiState,
            services = services,
            onToggleService = onToggleService,
            onClick = onServicesClick
        )
        Spacer(modifier = Modifier.height(16.dp))

        AllowClientAddServiceToggle(
            allowClientAddService = allowClientAddService,
            onAllowClientAddService = onAllowClientAddService
        )
        Spacer(modifier = Modifier.height(16.dp))

        WorkHoursSection(
            uiState = uiState,
            workStartHour = workStartHour,
            workEndHour = workEndHour,
            workHoursError = workHoursError,
            onWorkStartHour = onWorkStartHour,
            onWorkEndHour = onWorkEndHour
        )
        Spacer(modifier = Modifier.height(16.dp))

        BusinessPhoneField(uiState = uiState, phone = phone, onPhone = onPhone, phoneError = phoneError)
        Spacer(modifier = Modifier.height(16.dp))

        BusinessAddressField(uiState = uiState, address = address, onAddress = onAddress, addressError = addressError)
        Spacer(modifier = Modifier.height(16.dp))

        AllowAnonymousViewToggle(
            allowAnonymousView = allowAnonymousView,
            onAllowAnonymousView = onAllowAnonymousView
        )
        Spacer(modifier = Modifier.height(16.dp))

        NotifyOwnerBySmsToggle(
            notifyOwnerBySms = notifyOwnerBySms,
            onNotifyOwnerBySms = onNotifyOwnerBySms
        )

        // Emergency notice is configured on its own screen, reachable from
        // Settings (feature/settings/EmergencyNoticeScreen.kt) — not shown
        // here anymore. Its value is still loaded and saved above so
        // editing the business from this screen doesn't drop it.

        // Advanced settings (payment / capacity / reminders) now live on a
        // separate screen, reachable from the profile. The values are still
        // loaded and saved here so editing the business doesn't drop them.

        // Spacer replaced by bottomBar button


        if (uiState.business != null) {
            Text(uiState.business.title)
        }
    }
}

/**
 * Desktop layout. Fields are grouped into labelled panels — identity
 * (logo/category/name/bio), scheduling (duration/hours), services, contact
 * (phone/address) and the toggles — instead of two ragged columns of
 * unrelated fields. Every field is the same shared composable the phone
 * column uses, just re-arranged into panels — no handler, no validation and
 * no field was duplicated or altered.
 *
 * At Expanded width the panels split into two columns, balanced by field
 * count rather than by topic, so neither column trails far past the other:
 * identity(4 fields) + scheduling(2) vs services(1) + contact(2) +
 * toggles(3) — six rows each. Medium keeps a single column of the same
 * panels; two columns at that width would squeeze every field under ~300dp
 * and wrap the longer Persian labels. See SettingsScreen.kt's
 * SettingsWebContent for the same balancing pattern.
 *
 * RTL: the app forces RTL in ui/theme/Theme.kt, so in the [Row] below the
 * first column lands on the *right* — identity/scheduling there so a
 * Persian reader meets the business's identity first, services/contact/
 * toggles follow to its left.
 *
 * The submit action rides the normal scroll as [SubmitActionCard] instead
 * of a Scaffold bottomBar — a bar pinned to the viewport bottom is a phone
 * (thumb-reach) pattern that would just float below an otherwise empty
 * desktop window.
 */
@Composable
private fun CreateBusinessWebContent(
    modifier: Modifier = Modifier,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    uiState: CreateBusinessState,
    category: BusinessCategory,
    title: String,
    onTitle: (String) -> Unit,
    titleError: String?,
    bio: String,
    onBio: (String) -> Unit,
    defaultProgress: String,
    defaultProgressError: String?,
    services: List<String>,
    onToggleService: (String) -> Unit,
    allowClientAddService: Boolean,
    onAllowClientAddService: (Boolean) -> Unit,
    workStartHour: String,
    workEndHour: String,
    workHoursError: String?,
    onWorkStartHour: (String) -> Unit,
    onWorkEndHour: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    phoneError: String?,
    address: String,
    onAddress: (String) -> Unit,
    addressError: String?,
    allowAnonymousView: Boolean,
    onAllowAnonymousView: (Boolean) -> Unit,
    notifyOwnerBySms: Boolean,
    onNotifyOwnerBySms: (Boolean) -> Unit,
    logoBytes: ByteArray?,
    onLogoPickerClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDurationClick: () -> Unit,
    onServicesClick: () -> Unit,
    onSubmit: () -> Unit
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentWidth.Wide)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            uiState.business?.let { saved ->
                ModerationBanner(business = saved)
            }

            if (isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        IdentityPanel(
                            uiState = uiState,
                            category = category,
                            title = title,
                            onTitle = onTitle,
                            titleError = titleError,
                            bio = bio,
                            onBio = onBio,
                            logoBytes = logoBytes,
                            onLogoPickerClick = onLogoPickerClick,
                            onCategoryClick = onCategoryClick
                        )
                        SchedulingPanel(
                            uiState = uiState,
                            defaultProgress = defaultProgress,
                            defaultProgressError = defaultProgressError,
                            onDurationClick = onDurationClick,
                            workStartHour = workStartHour,
                            workEndHour = workEndHour,
                            workHoursError = workHoursError,
                            onWorkStartHour = onWorkStartHour,
                            onWorkEndHour = onWorkEndHour
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        ServicesPanel(
                            uiState = uiState,
                            services = services,
                            onToggleService = onToggleService,
                            onServicesClick = onServicesClick
                        )
                        ContactPanel(
                            uiState = uiState,
                            phone = phone,
                            onPhone = onPhone,
                            phoneError = phoneError,
                            address = address,
                            onAddress = onAddress,
                            addressError = addressError
                        )
                        TogglesPanel(
                            allowClientAddService = allowClientAddService,
                            onAllowClientAddService = onAllowClientAddService,
                            allowAnonymousView = allowAnonymousView,
                            onAllowAnonymousView = onAllowAnonymousView,
                            notifyOwnerBySms = notifyOwnerBySms,
                            onNotifyOwnerBySms = onNotifyOwnerBySms
                        )
                    }
                }
            } else {
                // Medium: same panels, one column — see the doc comment above.
                IdentityPanel(
                    uiState = uiState,
                    category = category,
                    title = title,
                    onTitle = onTitle,
                    titleError = titleError,
                    bio = bio,
                    onBio = onBio,
                    logoBytes = logoBytes,
                    onLogoPickerClick = onLogoPickerClick,
                    onCategoryClick = onCategoryClick
                )
                SchedulingPanel(
                    uiState = uiState,
                    defaultProgress = defaultProgress,
                    defaultProgressError = defaultProgressError,
                    onDurationClick = onDurationClick,
                    workStartHour = workStartHour,
                    workEndHour = workEndHour,
                    workHoursError = workHoursError,
                    onWorkStartHour = onWorkStartHour,
                    onWorkEndHour = onWorkEndHour
                )
                ServicesPanel(
                    uiState = uiState,
                    services = services,
                    onToggleService = onToggleService,
                    onServicesClick = onServicesClick
                )
                ContactPanel(
                    uiState = uiState,
                    phone = phone,
                    onPhone = onPhone,
                    phoneError = phoneError,
                    address = address,
                    onAddress = onAddress,
                    addressError = addressError
                )
                TogglesPanel(
                    allowClientAddService = allowClientAddService,
                    onAllowClientAddService = onAllowClientAddService,
                    allowAnonymousView = allowAnonymousView,
                    onAllowAnonymousView = onAllowAnonymousView,
                    notifyOwnerBySms = notifyOwnerBySms,
                    onNotifyOwnerBySms = onNotifyOwnerBySms
                )
            }

            if (uiState.business != null) {
                Text(uiState.business.title)
            }

            if (!uiState.isLoading) {
                SubmitActionCard(
                    text = stringResource(Res.string.accept),
                    isLoading = uiState.isLoading,
                    onClick = onSubmit
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Identity: logo, category, name and bio — the fields that describe *what
 *  this business is* to a client, grouped in the panel a Persian (RTL)
 *  reader meets first. */
@Composable
private fun IdentityPanel(
    uiState: CreateBusinessState,
    category: BusinessCategory,
    title: String,
    onTitle: (String) -> Unit,
    titleError: String?,
    bio: String,
    onBio: (String) -> Unit,
    logoBytes: ByteArray?,
    onLogoPickerClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    FormPanel(title = "هویت کسب‌وکار") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LogoPickerAvatar(uiState = uiState, logoBytes = logoBytes, onClick = onLogoPickerClick)
        }
        CategoryPickerField(uiState = uiState, category = category, onClick = onCategoryClick)
        BusinessTitleField(uiState = uiState, title = title, onTitle = onTitle, titleError = titleError)
        BusinessBioField(uiState = uiState, bio = bio, onBio = onBio)
    }
}

/** Scheduling: default service duration and work hours — both feed the
 *  calendar grid, so they belong together. */
@Composable
private fun SchedulingPanel(
    uiState: CreateBusinessState,
    defaultProgress: String,
    defaultProgressError: String?,
    onDurationClick: () -> Unit,
    workStartHour: String,
    workEndHour: String,
    workHoursError: String?,
    onWorkStartHour: (String) -> Unit,
    onWorkEndHour: (String) -> Unit
) {
    FormPanel(title = "زمان‌بندی") {
        DurationPickerField(
            uiState = uiState,
            defaultProgress = defaultProgress,
            defaultProgressError = defaultProgressError,
            onClick = onDurationClick
        )
        WorkHoursSection(
            uiState = uiState,
            workStartHour = workStartHour,
            workEndHour = workEndHour,
            workHoursError = workHoursError,
            onWorkStartHour = onWorkStartHour,
            onWorkEndHour = onWorkEndHour
        )
    }
}

/** Services picker plus its selected chips — its own panel since it can grow
 *  to several lines and would otherwise unbalance whatever panel it sat in. */
@Composable
private fun ServicesPanel(
    uiState: CreateBusinessState,
    services: List<String>,
    onToggleService: (String) -> Unit,
    onServicesClick: () -> Unit
) {
    FormPanel(title = "خدمات") {
        ServicesSection(
            uiState = uiState,
            services = services,
            onToggleService = onToggleService,
            onClick = onServicesClick
        )
    }
}

/** Contact: how a client reaches or finds the business. */
@Composable
private fun ContactPanel(
    uiState: CreateBusinessState,
    phone: String,
    onPhone: (String) -> Unit,
    phoneError: String?,
    address: String,
    onAddress: (String) -> Unit,
    addressError: String?
) {
    FormPanel(title = "اطلاعات تماس") {
        BusinessPhoneField(uiState = uiState, phone = phone, onPhone = onPhone, phoneError = phoneError)
        BusinessAddressField(uiState = uiState, address = address, onAddress = onAddress, addressError = addressError)
    }
}

/** The three switches, grouped since each pairs with a helper sentence and
 *  reads as one "visibility & notifications" decision block. */
@Composable
private fun TogglesPanel(
    allowClientAddService: Boolean,
    onAllowClientAddService: (Boolean) -> Unit,
    allowAnonymousView: Boolean,
    onAllowAnonymousView: (Boolean) -> Unit,
    notifyOwnerBySms: Boolean,
    onNotifyOwnerBySms: (Boolean) -> Unit
) {
    FormPanel(title = "نمایش و اطلاع‌رسانی") {
        AllowClientAddServiceToggle(
            allowClientAddService = allowClientAddService,
            onAllowClientAddService = onAllowClientAddService
        )
        AllowAnonymousViewToggle(
            allowAnonymousView = allowAnonymousView,
            onAllowAnonymousView = onAllowAnonymousView
        )
        NotifyOwnerBySmsToggle(
            notifyOwnerBySms = notifyOwnerBySms,
            onNotifyOwnerBySms = onNotifyOwnerBySms
        )
    }
}

/** Shared bounded-panel look every group above renders in — a titled card so
 *  differing field heights inside read as intentional grouping instead of
 *  misalignment. Mirrors SettingsScreen.kt's SettingsCard. */
@Composable
private fun FormPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

/** Desktop-only submit action, part of the normal scroll instead of a pinned
 *  bottomBar (see the doc comment on [CreateBusinessWebContent]). Mirrors
 *  CreateAppointmentScreen.kt's SubmitActionCard. */
@Composable
private fun SubmitActionCard(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        AppButton(
            text = text,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            isLoading = isLoading
        )
    }
}

/** Avatar/logo picker — opens [rememberImagePicker], which hands its result
 *  to [ImageCropperDialog] back in [CreateBusinessScreen]. Only where this
 *  sits changes between phone and web; the picker/cropper flow itself does
 *  not. */
@Composable
private fun LogoPickerAvatar(
    uiState: CreateBusinessState,
    logoBytes: ByteArray?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (logoBytes != null) {
            val bitmap = remember(logoBytes) {
                logoBytes.toImageBitmapOrNull()
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
            val url = if (path.startsWith("http")) path else "${xyz.sattar.javid.proqueue.core.AppConfig.BASE_URL}$path"
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
}

@Composable
private fun CategoryPickerField(
    uiState: CreateBusinessState,
    category: BusinessCategory,
    onClick: () -> Unit
) {
    val selectedCategoryText = category.persianName

    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
}

@Composable
private fun BusinessTitleField(
    uiState: CreateBusinessState,
    title: String,
    onTitle: (String) -> Unit,
    titleError: String?
) {
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
}

@Composable
private fun BusinessBioField(
    uiState: CreateBusinessState,
    bio: String,
    onBio: (String) -> Unit
) {
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
}

@Composable
private fun DurationPickerField(
    uiState: CreateBusinessState,
    defaultProgress: String,
    defaultProgressError: String?,
    onClick: () -> Unit
) {
    // Duration is picked from a fixed 5-minute ladder rather than typed:
    // free text let owners save slots the calendar can't lay out.
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
}

/** Service picker field plus its selected-services chip row — the two
 *  always travel together, in both layouts. */
@Composable
private fun ServicesSection(
    uiState: CreateBusinessState,
    services: List<String>,
    onToggleService: (String) -> Unit,
    onClick: () -> Unit
) {
    // ── Service menu ──
    // Defined once here, then reused everywhere a service has to be
    // named: the owner's own booking screen, and the client's public
    // booking page. A client who picks "رنگ مو" instead of typing a
    // sentence is a slot the owner can actually plan — which is the
    // whole reason this moved out of the free-text description.
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        AppTextField(
            value = if (services.isEmpty()) "" else "${services.size} خدمت انتخاب شده",
            onValueChange = {},
            label = "لیست خدمات",
            placeholder = "خدماتی که ارائه می‌دهید را انتخاب کنید",
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
            modifier = Modifier.fillMaxWidth(),
            enabled = false, // Disable to act just as a clickable button
        )
    }

    if (services.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        SelectedServiceChipsRow(
            selected = services,
            onRemove = onToggleService
        )
    }
}

@Composable
private fun AllowClientAddServiceToggle(
    allowClientAddService: Boolean,
    onAllowClientAddService: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { onAllowClientAddService(!allowClientAddService) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "مشتری بتواند خدمت خارج از لیست اضافه کند",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Switch(
                checked = allowClientAddService,
                onCheckedChange = { onAllowClientAddService(it) }
            )
        }
        Text(
            text = "اگر خاموش باشد، مشتری هنگام رزرو فقط از همین لیست " +
                    "انتخاب می‌کند و نمی‌تواند خدمت جدیدی بنویسد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkHoursSection(
    uiState: CreateBusinessState,
    workStartHour: String,
    workEndHour: String,
    workHoursError: String?,
    onWorkStartHour: (String) -> Unit,
    onWorkEndHour: (String) -> Unit
) {
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
}

@Composable
private fun BusinessPhoneField(
    uiState: CreateBusinessState,
    phone: String,
    onPhone: (String) -> Unit,
    phoneError: String?
) {
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
}

@Composable
private fun BusinessAddressField(
    uiState: CreateBusinessState,
    address: String,
    onAddress: (String) -> Unit,
    addressError: String?
) {
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
}

@Composable
private fun AllowAnonymousViewToggle(
    allowAnonymousView: Boolean,
    onAllowAnonymousView: (Boolean) -> Unit
) {
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
}

@Composable
private fun NotifyOwnerBySmsToggle(
    notifyOwnerBySms: Boolean,
    onNotifyOwnerBySms: (Boolean) -> Unit
) {
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
}

@Composable
fun HandleEvents(
    events: Flow<CreateBusinessEvent>,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenPaymentUrl: (String) -> Unit = {}
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

            is CreateBusinessEvent.OpenUrl -> {
                onOpenPaymentUrl(it.url)
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
            services = emptyList(),
            allowClientAddService = false,
            onToggleService = {},
            onAddService = {},
            onAllowClientAddService = {},
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
        )
    }
}
