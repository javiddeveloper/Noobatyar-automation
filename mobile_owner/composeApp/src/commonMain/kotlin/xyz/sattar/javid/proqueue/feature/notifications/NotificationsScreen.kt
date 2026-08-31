package xyz.sattar.javid.proqueue.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.permissions.rememberNotificationPermissionLauncher
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.FeatureGate
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPayment: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberNotificationPermissionLauncher { granted ->
        viewModel.sendIntent(NotificationsIntent.PermissionResult(granted))
    }

    HandleEffects(
        events = viewModel.events,
        onNavigateBack = onNavigateBack,
        showSnackbar = { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        },
        onRequestPermission = {
            permissionLauncher.launch()
        },
        onOpenPaymentUrl = onNavigateToPayment
    )

    NotificationsPhoneContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::sendIntent
    )
}

@Composable
private fun NotificationsPhoneContent(
    uiState: NotificationsState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onIntent: (NotificationsIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.notifications),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            EnableNotificationsCard(uiState = uiState, onIntent = onIntent)

            // Client-reminder card. The switch above only governs the owner's
            // own reminder; nothing on this screen used to reach the client at
            // all, so the settings the backend reminder job reads
            // (enable_reminder_sms / reminder_delivery) were unreachable from
            // the app and every business sat on their defaults.
            if (uiState.isNotificationsEnabled) {
                ClientReminderCard(uiState = uiState, onIntent = onIntent)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            AppButton(
                text = stringResource(Res.string.save_settings),
                onClick = { onIntent(NotificationsIntent.SaveSettings) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** The owner's own reminder toggle + lead-time input, locked behind FeatureGate. */
@Composable
private fun EnableNotificationsCard(
    uiState: NotificationsState,
    onIntent: (NotificationsIntent) -> Unit
) {
    FeatureGate(
        entitlements = uiState.entitlements,
        plans = uiState.plans,
        featureKey = EntitlementKeys.PUSH_NOTIFICATIONS,
        title = stringResource(Res.string.enable_notifications),
        description = "برای دریافت اعلان نوبت جدید و یادآوری روی گوشی یا مرورگر خودتان، پلن شما باید این ویژگی را پوشش دهد.",
        onUpgrade = { planId -> onIntent(NotificationsIntent.UpgradePlan(planId)) }
    ) {
        EnableNotificationsCardContent(uiState, onIntent)
    }
}

@Composable
private fun EnableNotificationsCardContent(
    uiState: NotificationsState,
    onIntent: (NotificationsIntent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(Res.string.enable_notifications),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = uiState.isNotificationsEnabled,
                    onCheckedChange = { onIntent(NotificationsIntent.ToggleNotifications(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = "با فعال‌سازی، نزدیک زمان هر نوبت یک یادآوری برای شما ارسال می‌شود تا به مشتری یادآوری کنید و احتمال عدم حضور کاهش یابد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // isNotificationsEnabled is the saved server setting, shared
            // across every device/browser the owner uses — it stays true
            // even when THIS browser hasn't granted permission (granted
            // elsewhere, e.g. the Android app, or simply not decided yet
            // here). Without this, the switch reads "on" while this exact
            // browser silently can't deliver anything, which is what looked
            // like a broken toggle to begin with.
            if (uiState.isNotificationsEnabled && !uiState.hasPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "این مرورگر اجازه نمایش اعلان را نداده، پس یادآوری‌ها روی همین دستگاه دیده نمی‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onIntent(NotificationsIntent.ToggleNotifications(true)) }) {
                        Text("اجازه بده", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Reminder Time Input
            if (uiState.isNotificationsEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.notification_reminder_time),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AppTextField(
                        value = uiState.reminderMinutes,
                        onValueChange = { onIntent(NotificationsIntent.UpdateReminderMinutes(it)) },
                        label = "زمان یادآوری (دقیقه)",
                        keyboardType = KeyboardType.Number,
                        maxLength = 3,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Text(
                        text = stringResource(Res.string.notification_reminder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Client-reminder settings: MANUAL vs PANEL delivery choice. This is where
 * the entitlement gating lives — [NotificationsState.canUsePanelDelivery]
 * comes straight from the plan check in [NotificationsViewModel] and is only
 * read here, never altered.
 */
@Composable
private fun ClientReminderCard(
    uiState: NotificationsState,
    onIntent: (NotificationsIntent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "یادآوری برای مشتری",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = uiState.remindClient,
                    onCheckedChange = { onIntent(NotificationsIntent.ToggleRemindClient(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            if (uiState.remindClient) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = stringResource(Res.string.reminder_delivery_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // MANUAL opens the device's own SMS app (core/utils/
                    // ContactActions — openSms) — a desktop browser has no
                    // SIM card or SMS app to hand that off to, so this
                    // option is dropped on web instead of shown disabled.
                    // The stored value isn't touched: an owner already on
                    // MANUAL keeps that setting until they explicitly pick
                    // PANEL, which is what actually starts spending SMS
                    // quota (docs/NOTIFICATIONS.md — never silently).
                    if (!AppInfo.isWeb) {
                        FilterChip(
                            selected = uiState.reminderDelivery == ReminderDelivery.MANUAL.value,
                            onClick = {
                                onIntent(NotificationsIntent.SetDelivery(ReminderDelivery.MANUAL.value))
                            },
                            label = { Text(stringResource(Res.string.reminder_delivery_manual_title)) }
                        )
                    }
                    FilterChip(
                        selected = uiState.reminderDelivery == ReminderDelivery.PANEL.value,
                        onClick = {
                            onIntent(NotificationsIntent.SetDelivery(ReminderDelivery.PANEL.value))
                        },
                        label = { Text(stringResource(Res.string.reminder_delivery_panel_title)) }
                    )
                }
                if (AppInfo.isWeb) {
                    Text(
                        text = "روی وب سیم‌کارت وجود ندارد، پس یادآوری فقط با پیامک پنل قابل ارسال است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (uiState.reminderDelivery == ReminderDelivery.PANEL.value) {
                            stringResource(Res.string.reminder_delivery_panel_description)
                        } else {
                            stringResource(Res.string.reminder_delivery_manual_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!uiState.canUsePanelDelivery) {
                    Text(
                        text = stringResource(Res.string.reminder_delivery_panel_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}


@Composable
fun HandleEffects(
    events: Flow<NotificationsEvent>,
    onNavigateBack: () -> Unit,
    showSnackbar: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPaymentUrl: (String) -> Unit = {}
) {
    val savedMessage = stringResource(Res.string.settings_saved)
    events.collectWithLifecycleAware { event ->
        when (event) {
            NotificationsEvent.NavigateBack -> onNavigateBack()
            NotificationsEvent.ShowSavedConfirmation -> showSnackbar(savedMessage)
            NotificationsEvent.RequestPermission -> onRequestPermission()
            is NotificationsEvent.ShowError -> showSnackbar(event.message)
            is NotificationsEvent.OpenUrl -> onOpenPaymentUrl(event.url)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewNotificationsScreen() {
    AppTheme {
    }
}
