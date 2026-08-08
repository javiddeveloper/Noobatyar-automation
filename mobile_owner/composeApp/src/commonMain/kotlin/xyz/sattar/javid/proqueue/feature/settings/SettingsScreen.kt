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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.ModerationBadge
import xyz.sattar.javid.proqueue.core.ui.components.ModerationBanner
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    userViewModel: xyz.sattar.javid.proqueue.feature.profile.UserViewModel = koinViewModel(),
    onNavigateToAbout: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onNavigateToEditBusiness: (Long) -> Unit = {},
    onNavigateToAdvancedSettings: (Long) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val userState by userViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotificationToast by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val themeMode by ThemeStateHolder.themeMode.collectAsState()

    // Initial load happens once in SettingsViewModel.init (it observes the
    // selected business). Not re-triggered here to avoid repeat requests when
    // returning to this tab.

    HandleEvents(
        events = viewModel.events,
        onNavigateToAbout = onNavigateToAbout,
        onChangeBusiness = onChangeBusiness,
        onNavigateToEditBusiness = onNavigateToEditBusiness,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToMessages = onNavigateToMessages
    )

    userViewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            xyz.sattar.javid.proqueue.feature.profile.UserEvent.LogoutSuccess -> onNavigateToLogin()
        }
    }

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
        userName = userState.userName,
        userPhone = userState.userNumber,
        subscription = userState.subscription,
        isRefreshing = uiState.isLoading || userState.isLoading,
        onRefresh = {
            viewModel.sendIntent(SettingsIntent.RefreshSettings)
            userViewModel.sendIntent(xyz.sattar.javid.proqueue.feature.profile.UserIntent.LoadProfile)
        },
        onIntent = viewModel::sendIntent,
        onShowThemeSheet = { showThemeSheet = true },
        onShowDeleteDialog = { showDeleteDialog = true },
        onNavigateToLogin = onNavigateToLogin,
        onLogout = { userViewModel.sendIntent(xyz.sattar.javid.proqueue.feature.profile.UserIntent.Logout) },
        onChangeBusiness = onChangeBusiness,
        onAdvancedSettings = {
            uiState.currentBusiness?.let {
                onNavigateToAdvancedSettings(it.id)
            }
        }
    )
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    uiState: SettingsState,
    userName: String? = null,
    userPhone: String? = null,
    subscription: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onIntent: (SettingsIntent) -> Unit,
    onShowThemeSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onAdvancedSettings: () -> Unit
) {
    // No top app bar here: the profile hero below is this screen's header, so a
    // separate toolbar showing the business title would just overlap it. We only
    // reserve the status-bar inset so the hero sits below the status bar.
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Profile identity hero — the owner sees who they are and which
            // business they're currently managing, front and center.
            ProfileHeaderCard(
                userName = userName,
                userPhone = userPhone,
                business = uiState.currentBusiness,
                subscription = subscription
            )

            // Moderation state of the active business. Only renders for
            // pending/rejected/suspended — an approved business just keeps its
            // badge in the hero above. Separate from the plan/subscription
            // badge on purpose: "not reviewed yet" is not "plan lapsed".
            uiState.currentBusiness?.let { business ->
                ModerationBanner(
                    business = business,
                    onEditClick = { onIntent(SettingsIntent.OnEditBusinessClick(business.id)) }
                )
            }

            // Advanced settings — a separate, eye-catching card with a looping
            // shine animation to draw the owner in (upsell to premium features).
            AdvancedSettingsPromoCard(onClick = onAdvancedSettings)

            // Business actions: change + delete, grouped together.
            SettingsCard {
                Column {
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
                        title = stringResource(Res.string.edit_business_title),
                        subtitle = null,
                        onClick = {
                            uiState.currentBusiness?.let { business ->
                                onIntent(SettingsIntent.OnEditBusinessClick(business.id))
                            }
                        },
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
                        title = stringResource(Res.string.about_us_label),
                        subtitle = stringResource(Res.string.about_us_subtitle),
                        onClick = { onIntent(SettingsIntent.OnAboutClick) }
                    )
                }
            }

            // Logout (previously reached via the top-bar avatar, which this screen
            // no longer shows).
            SettingsCard {
                SettingsItem(
                    icon = Icons.Rounded.Logout,
                    title = stringResource(Res.string.logout_label),
                    subtitle = null,
                    onClick = onLogout,
                    tint = MaterialTheme.colorScheme.error,
                    centerVertically = true
                )
            }

            // Subtle Noobatyar branding footer (moved out of the header, which now
            // belongs to the owner's own identity).
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.main_icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).alpha(0.7f)
                    )
                    Text(
                        text = stringResource(Res.string.appName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${stringResource(Res.string.app_version)} ${uiState.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            BottomBarSpacer()
        }
        }
    }
}

@Composable
private fun AdvancedSettingsPromoCard(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "adv-promo")
    val shine by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )
    // Gentle breathing scale so the card subtly "pulls" the eye.
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val bandWidthPx = widthPx * 0.35f
            val x = -bandWidthPx + shine * (widthPx + bandWidthPx)

            // The moving diagonal shine band.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { bandWidthPx.toDp() })
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.advanced_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.advanced_settings_promo),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    userName: String?,
    userPhone: String?,
    business: Business?,
    subscription: SubscriptionDto?
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(gradient)) {
            // Decorative oversized glyph in the corner.
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(170.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 45.dp, y = (-40).dp),
                tint = Color.White.copy(alpha = 0.10f)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Owner identity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "؟",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName ?: stringResource(Res.string.noobatyar_user),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (!userPhone.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Phone,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = userPhone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    SubscriptionBadge(subscription = subscription)
                }

                // Active business — so the owner always knows which one they're managing.
                if (business != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (business.logoPath.isNotEmpty()) {
                                    val url = if (business.logoPath.startsWith("http")) business.logoPath
                                    else "${xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL}${business.logoPath}"
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Storefront,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.active_business),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = business.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = business.category.persianName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            // The decorative "verified" tick used to sit here
                            // unconditionally, which now reads as a moderation
                            // claim. Show the real state when we know it.
                            if (business.moderationStatus != null) {
                                ModerationBadge(business = business)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionBadge(subscription: SubscriptionDto?) {
    val isValid = subscription?.isValid == true
    val isVip = subscription?.plan?.isVip == true
    val label = when {
        isVip -> stringResource(Res.string.subscription_vip)
        isValid -> stringResource(Res.string.subscription_active)
        else -> stringResource(Res.string.subscription_none)
    }
    val icon = if (isValid) Icons.Rounded.Star else Icons.Rounded.Info

    Surface(
        color = Color.White.copy(alpha = if (isValid) 0.25f else 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
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

            is SettingsEvent.NavigateToEditBusiness -> {
                scope.launch { onNavigateToEditBusiness(event.businessId) }
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
