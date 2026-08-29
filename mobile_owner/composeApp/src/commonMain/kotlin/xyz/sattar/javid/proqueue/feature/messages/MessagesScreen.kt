package xyz.sattar.javid.proqueue.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddons: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            MessagesEvent.NavigateToAddons -> onNavigateToAddons()
        }
    }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(MessagesIntent.Load)
    }

    MessagesScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::sendIntent
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * untouched; anything wider gets the desktop editor-beside-preview layout —
 * see [MessagesWebContent].
 */
@Composable
fun MessagesScreenContent(
    uiState: MessagesState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onIntent: (MessagesIntent) -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        MessagesPhoneContent(uiState, snackbarHostState, onNavigateBack, onIntent)
    } else {
        MessagesWebContent(uiState, snackbarHostState, onNavigateBack, onIntent)
    }
}

/**
 * Phone layout — unchanged. One long vertical stack of cards. See
 * [MessagesWebContent] for the desktop editor/preview split.
 */
@Composable
private fun MessagesPhoneContent(
    uiState: MessagesState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onIntent: (MessagesIntent) -> Unit
) {
    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.messages_title),
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            DeliveryCard(uiState = uiState, onIntent = onIntent)
            LeadTimeCard(uiState = uiState, onIntent = onIntent)
            EditorCard(uiState = uiState, onIntent = onIntent)
            PreviewCard(uiState = uiState)
            ReadyTemplatesCard(uiState = uiState, onIntent = onIntent)

            Spacer(modifier = Modifier.weight(1f))

            AppButton(
                onClick = { onIntent(MessagesIntent.Save) },
                text = stringResource(Res.string.messages_save),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Desktop layout. The delivery-channel choice and lead-time chips stay
 * full-width banners above the fold (they're one decision each, not a list).
 * The template editor and its live preview/token reference — the part that
 * benefits most from width — become two side-by-side columns at
 * [WindowSize.Expanded] instead of three stacked cards. At
 * [WindowSize.Medium] there isn't room for two columns without squeezing the
 * editor too narrow to be useful, so it stays a single stacked column, same
 * as phone but width-capped.
 *
 * Every card is the exact same composable used by [MessagesPhoneContent], so
 * the token set, template storage and delivery-channel entitlement gating
 * ([MessagesState.canUsePanelDelivery]) can't drift between the two layouts.
 */
@Composable
private fun MessagesWebContent(
    uiState: MessagesState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onIntent: (MessagesIntent) -> Unit
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.messages_title),
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AppScaffold(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            maxWidth = ContentWidth.Wide
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                DeliveryCard(uiState = uiState, onIntent = onIntent)
                LeadTimeCard(uiState = uiState, onIntent = onIntent)

                if (isExpanded) {
                    // Row's first child lands on the right under the app's
                    // forced RTL layout direction. The editor is the thing
                    // being worked on, so it takes the right (primary)
                    // column; the read-only preview and ready-made templates
                    // sit to its left as reference material.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EditorCard(uiState = uiState, onIntent = onIntent)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PreviewCard(uiState = uiState)
                            ReadyTemplatesCard(uiState = uiState, onIntent = onIntent)
                        }
                    }
                } else {
                    EditorCard(uiState = uiState, onIntent = onIntent)
                    PreviewCard(uiState = uiState)
                    ReadyTemplatesCard(uiState = uiState, onIntent = onIntent)
                }

                Spacer(modifier = Modifier.weight(1f))

                AppButton(
                    onClick = { onIntent(MessagesIntent.Save) },
                    text = stringResource(Res.string.messages_save),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** Delivery Card — who actually sends the reminder. This is the first
 * decision on the screen: the message being written below reaches the
 * client either from the owner's SIM or from the server. */
@Composable
private fun DeliveryCard(uiState: MessagesState, onIntent: (MessagesIntent) -> Unit) {
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.reminder_delivery_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.reminder_delivery_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // MANUAL hands the message off to the device's own SMS app
            // (core/utils/ContactActions — openSms). A desktop browser has
            // no SIM card or SMS app to hand that off to, so this option is
            // dropped on web instead of shown disabled — there's nothing to
            // "unlock" the way the entitlement-gated PANEL option below has.
            // The stored value isn't touched: an owner already on MANUAL
            // keeps it until they explicitly pick PANEL, which is what
            // actually starts spending SMS quota (docs/NOTIFICATIONS.md —
            // never silently).
            if (!AppInfo.isWeb) {
                DeliveryOption(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = stringResource(Res.string.reminder_delivery_manual_title),
                    description = stringResource(Res.string.reminder_delivery_manual_description),
                    selected = uiState.reminderDelivery == ReminderDelivery.MANUAL.value,
                    locked = false,
                    onClick = {
                        onIntent(MessagesIntent.SetDelivery(ReminderDelivery.MANUAL.value))
                    }
                )
            }

            DeliveryOption(
                icon = Icons.Rounded.CloudUpload,
                title = stringResource(Res.string.reminder_delivery_panel_title),
                description = stringResource(Res.string.reminder_delivery_panel_description),
                selected = uiState.reminderDelivery == ReminderDelivery.PANEL.value,
                locked = !uiState.canUsePanelDelivery,
                lockedHint = stringResource(Res.string.reminder_delivery_panel_locked),
                onClick = {
                    onIntent(MessagesIntent.SetDelivery(ReminderDelivery.PANEL.value))
                },
                onUpgrade = {
                    onIntent(MessagesIntent.UpgradeForPanelDelivery)
                }
            )
        }
    }
}

/** Lead-time Card — the «{minutes}» in the template used to come from a
 * preference the owner had no way of seeing from this screen. */
@Composable
private fun LeadTimeCard(uiState: MessagesState, onIntent: (MessagesIntent) -> Unit) {
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.reminder_lead_time_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(Res.string.reminder_lead_time_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                reminderLeadTimeOptions.forEach { minutes ->
                    FilterChip(
                        selected = uiState.reminderMinutes == minutes,
                        onClick = { onIntent(MessagesIntent.SetReminder(minutes)) },
                        label = { Text("${minutes.toString().toPersianDigits()} دقیقه") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

/** Editor Card — variables + text field together, so it's clear the chips
 * insert into the message being edited. */
@Composable
private fun EditorCard(uiState: MessagesState, onIntent: (MessagesIntent) -> Unit) {
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.message_text),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "با لمس هر متغیر، آن را به متن اضافه کنید؛ هنگام ارسال با اطلاعات واقعی مشتری جایگزین می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                MessageToken.entries.forEach { token ->
                    TokenChip(label = token.label) {
                        onIntent(MessagesIntent.InsertToken(token.token))
                    }
                }
            }
            OutlinedTextField(
                value = uiState.template,
                onValueChange = { onIntent(MessagesIntent.UpdateTemplate(it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

/** Preview Card — rendered as an incoming SMS bubble. */
@Composable
private fun PreviewCard(uiState: MessagesState) {
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.messages_preview_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 4.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                )
            ) {
                Text(
                    text = uiState.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f
                )
            }
        }
    }
}

/** Ready Templates */
@Composable
private fun ReadyTemplatesCard(uiState: MessagesState, onIntent: (MessagesIntent) -> Unit) {
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.messages_ready_subtitle),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                uiState.readyTemplates.forEachIndexed { index, tpl ->
                    SuggestionChip(
                        onClick = { onIntent(MessagesIntent.ApplyReadyTemplate(tpl)) },
                        label = { Text("الگو ${index + 1}") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

/**
 * One reminder-delivery mode. The description is not optional decoration: the
 * two modes differ in who pays and who has to remember to press send, and that
 * is exactly what the labels alone don't say.
 *
 * A [locked] option keeps its description (so the owner can see what they'd be
 * buying) but is not selectable; the upsell button points at the same add-ons
 * screen the rest of the app uses for paid capabilities.
 */
@Composable
private fun DeliveryOption(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    lockedHint: String? = null,
    onUpgrade: () -> Unit = {}
) {
    val borderColor = when {
        locked -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onClick,
        enabled = !locked
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    enabled = !locked,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(if (locked) 0.6f else 1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp).alpha(if (locked) 0.6f else 1f)
            )
            if (locked) {
                Spacer(modifier = Modifier.height(10.dp))
                if (lockedHint != null) {
                    Text(
                        text = lockedHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 34.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = onUpgrade,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(start = 34.dp)
                ) {
                    Text("فعال‌سازی", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TokenChip(label: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            labelColor = MaterialTheme.colorScheme.primary
        )
    )
}

enum class MessageToken(val label: String, val token: String) {
    Visitor("نام مشتری", "{visitor}"),
    Business("نام کسب‌وکار", "{business}"),
    Address("آدرس", "{address}"),
    Date("تاریخ", "{date}"),
    Time("ساعت", "{time}"),
    Minutes("دقیقه یادآوری", "{minutes}"),
    Duration("زمان سرویس", "{duration}")
}
