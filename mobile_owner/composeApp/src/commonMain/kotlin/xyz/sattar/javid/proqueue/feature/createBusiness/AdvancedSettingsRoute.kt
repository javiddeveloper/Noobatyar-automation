package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.accept
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


/**
 * Standalone "advanced settings" screen (payment / capacity / reminders),
 * separated from the business-edit form. It reuses [CreateBusinessViewModel]:
 * the business is loaded fully, only the advanced fields are edited here, and on
 * save the basic fields are passed through unchanged from the loaded business.
 */
@Composable
fun AdvancedSettingsRoute(
    businessId: Long,
    viewModel: CreateBusinessViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Advanced fields (mirrors the ones hoisted in CreateBusinessRoute).
    var maxAppointmentsPerHour by remember { mutableStateOf("") }
    var depositMode by remember { mutableStateOf(DepositMode.NONE.value) }
    var depositAmount by remember { mutableStateOf("") }
    var acceptedPaymentMethods by remember { mutableStateOf(setOf<String>()) }
    var cardNumber by remember { mutableStateOf("") }
    var cardOwnerName by remember { mutableStateOf("") }
    var merchantId by remember { mutableStateOf("") }
    var paymentLink by remember { mutableStateOf("") }

    LaunchedEffect(businessId) {
        if (businessId != 0L) viewModel.sendIntent(CreateBusinessIntent.LoadBusiness(businessId))
    }
    LaunchedEffect(Unit) {
        viewModel.sendIntent(CreateBusinessIntent.LoadEntitlements)
    }
    LaunchedEffect(uiState.business) {
        uiState.business?.let {
            maxAppointmentsPerHour = it.maxAppointmentsPerHour?.toString() ?: ""
            depositMode = it.depositMode ?: DepositMode.NONE.value
            depositAmount = it.depositAmount?.toString() ?: ""
            acceptedPaymentMethods = it.acceptedPaymentMethods?.toSet() ?: setOf()
            cardNumber = it.cardNumber
            cardOwnerName = it.cardOwnerName
            merchantId = it.merchantId
            paymentLink = it.paymentLink
        }
    }

    HandleEvents(
        events = viewModel.events,
        onContinue = onNavigateBack,
        onNavigateBack = onNavigateBack
    )

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.sendIntent(CreateBusinessIntent.ClearMessage)
        }
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تنظیمات پیشرفته",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val business = uiState.business
            if (business != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                    AppButton(
                        text = stringResource(Res.string.accept),
                        onClick = {
                            viewModel.sendIntent(
                                CreateBusinessIntent.CreateBusiness(
                                    title = business.title,
                                    category = business.category,
                                    phone = business.phone,
                                    address = business.address,
                                    defaultProgress = business.defaultServiceDuration.toString(),
                                    workStartHour = business.workStartHour,
                                    workEndHour = business.workEndHour,
                                    allowAnonymousView = business.allowAnonymousView,
                                    notifyOwnerBySms = business.notifyOwnerBySms,
                                    bio = business.bio,
                                    logoBytes = business.logoBytes,
                                    maxAppointmentsPerHour = maxAppointmentsPerHour.toIntOrNull(),
                                    depositMode = depositMode,
                                    depositAmount = depositAmount.toIntOrNull() ?: 0,
                                    acceptedPaymentMethods = acceptedPaymentMethods.joinToString(","),
                                    cardNumber = cardNumber,
                                    cardOwnerName = cardOwnerName,
                                    merchantId = merchantId,
                                    paymentLink = paymentLink,
                                    // Owned by other screens (business edit /
                                    // reminder messages) — passed through
                                    // unchanged so saving this tab can't reset
                                    // them to their defaults.
                                    noticeEnabled = business.noticeEnabled,
                                    noticeMessage = business.noticeMessage,
                                    reminderDelivery = business.reminderDelivery
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        isLoading = uiState.isLoading
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val business = uiState.business
        if (business == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) {
                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                AdvancedSettingsTabs(
                    entitlements = uiState.entitlements,
                    plans = uiState.plans,
                    onUpgrade = { planId -> viewModel.sendIntent(CreateBusinessIntent.UpgradePlan(planId)) },
                    isLoading = uiState.isLoading,
                    acceptedPaymentMethods = acceptedPaymentMethods,
                    onAcceptedPaymentMethods = { acceptedPaymentMethods = it },
                    cardNumber = cardNumber,
                    onCardNumber = { cardNumber = it },
                    cardOwnerName = cardOwnerName,
                    onCardOwnerName = { cardOwnerName = it },
                    maxAppointmentsPerHour = maxAppointmentsPerHour,
                    onMaxAppointmentsPerHour = { maxAppointmentsPerHour = it },
                    depositMode = depositMode,
                    onDepositMode = { depositMode = it },
                    depositAmount = depositAmount,
                    onDepositAmount = { depositAmount = it },
                    merchantId = merchantId,
                    onMerchantId = { merchantId = it },
                    paymentLink = paymentLink,
                    onPaymentLink = { paymentLink = it }
                )

            }
        }
    }
}
