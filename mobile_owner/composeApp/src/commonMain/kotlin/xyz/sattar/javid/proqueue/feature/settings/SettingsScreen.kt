package xyz.sattar.javid.proqueue.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateToAbout: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onNavigateToEditBusiness: (Long) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotificationToast by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val themeMode by ThemeStateHolder.themeMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(SettingsIntent.LoadSettings)
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToAbout = onNavigateToAbout,
        onChangeBusiness = onChangeBusiness,
        onNavigateToEditBusiness = onNavigateToEditBusiness,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToMessages = onNavigateToMessages
    )

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = sheetState
        ) {
            ThemeSelectionContent(
                currentMode = themeMode,
                onThemeSelected = { mode ->
                    ThemeStateHolder.setThemeMode(mode)
                    scope.launch {
                        PreferencesManager.setThemeMode(mode)
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showThemeSheet = false
                        }
                    }
                }
            )
        }
    }

    if (showNotificationToast) {
        AlertDialog(
            onDismissRequest = { showNotificationToast = false },
            confirmButton = {
                TextButton(onClick = { showNotificationToast = false }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            title = { Text(stringResource(Res.string.notification_title)) },
            text = { Text(stringResource(Res.string.coming_soon_message)) }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.delete_business)) },
            text = { Text(stringResource(Res.string.delete_business_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.sendIntent(SettingsIntent.OnDeleteBusinessClick)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    SettingsContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        onShowThemeSheet = { showThemeSheet = true },
        onShowDeleteDialog = { showDeleteDialog = true },
        onNavigateToLogin = onNavigateToLogin,
        onEditBusiness = {
            uiState.currentBusiness?.let {
                onNavigateToEditBusiness(it.id)
            }
        }
    )
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    uiState: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onShowThemeSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onEditBusiness: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_menu_item),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    ProfileAvatar(
                        onNavigateToLogin = onNavigateToLogin
                    )
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
                .padding(top = paddingValues.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App Info (No Card)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.main_icon),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(Res.string.appName),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(Res.string.smart_queue_management),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Actions Card (Moved to top)
            SettingsCard {
                Column(
                ) {
                    SettingsItem(
                        icon = Icons.Rounded.Factory,
                        title = stringResource(Res.string.change_business),
                        subtitle = null,
                        onClick = { onIntent(SettingsIntent.OnChangeBusinessClick) },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Edit,
                        title = "ویرایش کسب‌وکار",
                        subtitle = null,
                        onClick = onEditBusiness,
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(Res.string.delete_business),
                        subtitle = null,
                        onClick = onShowDeleteDialog,
                        tint = MaterialTheme.colorScheme.error,
                        centerVertically = true
                    )
                }
            }

            // Options Card
            SettingsCard {
                Column {
                    SettingsItem(
                        icon = Icons.Rounded.Message,
                        title = stringResource(Res.string.messages_auto_item),
                        subtitle = stringResource(Res.string.messages_auto_subtitle),
                        onClick = { onIntent(SettingsIntent.OnMessagesClick) }
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(Res.string.reminders_notifications_item),
                        subtitle = stringResource(Res.string.reminders_notifications_subtitle),
                        onClick = { onIntent(SettingsIntent.OnNotificationsClick) }
                    )
                }
            }

            // Appearance & Info Card
            SettingsCard {
                Column {
                    SettingsItem(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(Res.string.theme_appearance),
                        subtitle = stringResource(Res.string.theme_settings),
                        onClick = onShowThemeSheet
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = "درباره ما",
                        subtitle = "آشنایی با نوبت‌یار و ارتباط با ما",
                        onClick = { onIntent(SettingsIntent.OnAboutClick) }
                    )
                }
            }

            Text(
                modifier= Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = "${stringResource(Res.string.app_version)} ${uiState.appVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(180.dp))
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
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.3f
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(/*modifier = Modifier.padding(vertical = 12.dp)*/) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    centerVertically: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.size(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = if (subtitle == null) FontWeight.Bold else FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(4.dp))
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.7f)
                )
            }
        }
        if (!centerVertically) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ThemeSelectionContent(
    currentMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.select_theme),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ThemeItem(
            title = stringResource(Res.string.theme_system),
            isSelected = currentMode == AppThemeMode.SYSTEM,
            onClick = { onThemeSelected(AppThemeMode.SYSTEM) }
        )
        ThemeItem(
            title = stringResource(Res.string.theme_light),
            isSelected = currentMode == AppThemeMode.LIGHT,
            onClick = { onThemeSelected(AppThemeMode.LIGHT) }
        )
        ThemeItem(
            title = stringResource(Res.string.theme_dark),
            isSelected = currentMode == AppThemeMode.DARK,
            onClick = { onThemeSelected(AppThemeMode.DARK) }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThemeItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun HandleEvents(
    events: kotlinx.coroutines.flow.Flow<SettingsEvent>,
    onNavigateToAbout: () -> Unit,
    onChangeBusiness: () -> Unit,
    onNavigateToEditBusiness: (Long) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    events.collectWithLifecycleAware { event ->
        when (event) {
            SettingsEvent.NavigateToAbout -> {
                scope.launch { onNavigateToAbout() }
            }

            SettingsEvent.NavigateToBusinessSelection -> {
                scope.launch { onChangeBusiness() }
            }

            SettingsEvent.BusinessDeleted -> {
                scope.launch { onChangeBusiness() }
            }

            SettingsEvent.NavigateToNotifications -> {
                scope.launch { onNavigateToNotifications() }
            }

            SettingsEvent.NavigateToMessages -> {
                scope.launch { onNavigateToMessages() }
            }
        }
    }
}
