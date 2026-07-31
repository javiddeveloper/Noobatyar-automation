package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.address
import proqueue.composeapp.generated.resources.home_menu_item
import proqueue.composeapp.generated.resources.phone
import proqueue.composeapp.generated.resources.welcome_to_proqueue
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.HomeButtonShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeChartShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeDashboardShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomePlanBannerShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeUsageShimmer
import xyz.sattar.javid.proqueue.core.ui.components.MainTopAppBar
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel<HomeViewModel>(),
    onNavigateToCalendar: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    // Initial data load happens once in HomeViewModel.init (it observes the
    // selected business). We deliberately don't re-trigger it here, so returning
    // to this tab does not fire a new server request.

    HandleEvents(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateToLogin = onNavigateToLogin
    )

    HomeScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent,
        onNavigateToCalendar = onNavigateToCalendar,
        onNavigateToLogin = onNavigateToLogin,
        onChangeBusiness = onChangeBusiness,
        onNavigateToAddons = onNavigateToAddons
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeState,
    snackbarHostState: SnackbarHostState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = {
            xyz.sattar.javid.proqueue.core.ui.components.ToastyHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MainTopAppBar(
                onNavigateToLogin = onNavigateToLogin,
                onChangeBusiness = onChangeBusiness,
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            modifier = Modifier.size(20.dp),
                            onClick = onNavigateToCalendar
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Event,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = "Calendar"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            // contentPadding (not a padding modifier) so items scroll *under* the
            // glass top bar rather than starting below an opaque gap.
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ۱. هدر تاریخ (سلام/زمینه‌ی امروز)
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 150)
                    )
                ) {
                    DateHeader()
                }
            }

            // ۲. آمار داشبورد امروز
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 200)
                    )
                ) {
                    when {
                        !uiState.statsLoaded -> HomeDashboardShimmer()
                        else -> DashboardStatsSection(stats = uiState.stats)
                    }
                }
            }

            // ۳. نمودار روند نوبت‌های ۷ روز اخیر
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 250)
                    )
                ) {
                    if (!uiState.chartLoaded) {
                        HomeChartShimmer()
                    } else if (uiState.dailyCounts.isNotEmpty()) {
                        NeonLineChart(counts = uiState.dailyCounts.map { it.count })
                    }
                }
            }

            // ۴. مصرف‌سنج ماهانه
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 300)
                    )
                ) {
                    if (!uiState.entitlementsLoaded) {
                        HomeUsageShimmer()
                    } else if (uiState.entitlements != null) {
                        UsageMeterSection(
                            entitlements = uiState.entitlements,
                            onNavigateToAddons = onNavigateToAddons
                        )
                    }
                }
            }

            // ۵. لینک دریافت نوبت
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 350)
                    )
                ) {
                    if (uiState.business == null && uiState.isLoading) {
                        HomeButtonShimmer()
                    } else if (uiState.business != null) {
                        BookingLinkButton(uiState.business)
                    }
                }
            }

            // ۶. بنرهای پلن اشتراک
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 400)
                    )
                ) {
                    if (!uiState.plansLoaded) {
                        HomePlanBannerShimmer()
                    } else if (uiState.plans.isNotEmpty()) {
                        PlanBannerSection(
                            plans = uiState.plans,
                            onPlanClick = { plan ->
                                onIntent(HomeIntent.PurchasePlan(plan.id))
                            }
                        )
                    }
                }
            }

            item { BottomBarSpacer() }
        }
    }
}

@Composable
fun PlanBannerSection(
    plans: List<PlanDto>,
    onPlanClick: (PlanDto) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { plans.size })

    // چرخش خودکار بنرها هر ۵ ثانیه
    LaunchedEffect(plans) {
        if (plans.isNotEmpty()) {
            while (true) {
                delay(5000)
                if (pagerState.pageCount > 0) {
                    val nextPage = (pagerState.currentPage + 1) % plans.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            pageSpacing = 8.dp
        ) { page ->
            val plan = plans[page]
            PlanBannerItem(
                plan = plan,
                onClick = { onPlanClick(plan) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // نشانگرهای صفحات (Dots)
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(plans.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                
                val width by animateDpAsState(
                    targetValue = if (pagerState.currentPage == iteration) 24.dp else 8.dp
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .width(width)
                        .height(8.dp)
                )
            }
        }
    }
}

@Composable
fun PlanBannerItem(
    plan: PlanDto,
    onClick: () -> Unit
) {
    val (gradientColors, badgeLabel) = when {
        plan.name.contains("پرو پلاس") -> Pair(
            listOf(Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFFE65100)),
            "💎 پرو پلاس"
        )
        plan.name.contains("پرو") -> Pair(
            listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF7C4DFF)),
            "🚀 پرو"
        )
        plan.name.contains("اکو") -> Pair(
            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047)),
            "🌿 اکو"
        )
        plan.name.contains("پایه") -> Pair(
            listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2)),
            "⚡ پایه"
        )
        else -> Pair(
            listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF607D8B)),
            "🌱 آزمایشی"
        )
    }

    val gradient = Brush.linearGradient(colors = gradientColors)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
        ) {
            // آیکون تزئینی پس‌زمینه
            Icon(
                imageVector = if (plan.isVip) Icons.Rounded.WorkspacePremium else Icons.Rounded.Stars,
                contentDescription = null,
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp),
                tint = Color.White.copy(alpha = 0.08f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // سطر اول: badge + قیمت
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge نام پلن
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // قیمت
                    Column(horizontalAlignment = Alignment.End) {
                        if (plan.price == 0L) {
                            Surface(
                                color = Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "رایگان",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Text(
                                text = plan.priceDisplay,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // سطر دوم: مدت اشتراک
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Timer,
                        null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = plan.durationDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // سطر سوم: تمام آیتم‌های توضیح
                if (plan.description.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))

                    // خط جداکننده
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    Spacer(Modifier.height(10.dp))

                    // نمایش همه آیتم‌ها در دو ستون
                    val half = (plan.description.size + 1) / 2
                    val col1 = plan.description.take(half)
                    val col2 = plan.description.drop(half)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ستون اول
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            col1.forEach { desc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        // ستون دوم
                        if (col2.isNotEmpty()) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                col2.forEach { desc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            null,
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // دکمه خرید (فقط وقتی قیمت دارد)
                if (plan.price > 0L) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "خرید اشتراک",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(modifier: Modifier = Modifier) {
    val currentTime = DateTimeUtils.systemCurrentMilliseconds()
    val formattedDate = DateTimeUtils.formatDate(currentTime)
    val formattedTime = DateTimeUtils.formatTime(currentTime)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CalendarToday,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "امروز، $formattedDate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "ساعت فعلی: $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DashboardStatsSection(stats: DashboardStats) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "نوبت‌های امروز",
                value = stats.totalAppointments.toString(),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = Icons.Rounded.Event
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "تکمیل شده",
                value = stats.completedAppointments.toString(),
                containerColor = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9),
                contentColor = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
                icon = Icons.Rounded.CheckCircle
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "عدم حضور",
                value = stats.noShowAppointments.toString(),
                containerColor = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
                contentColor = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828),
                icon = Icons.Rounded.Cancel
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "کل مراجعین",
                value = stats.totalVisitors.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Rounded.People
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            // Large background icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 15.dp, y = 15.dp),
                tint = contentColor.copy(alpha = 0.07f)
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun BookingLinkButton(business: Business) {
    val uniqueCode = business.uniqueCode ?: return
    val clipboard = LocalClipboardManager.current
    val link = "${xyz.sattar.javid.proqueue.BuildKonfig.BOOKING_BASE_URL}/b/$uniqueCode"
    OutlinedButton(
        onClick = { clipboard.setText(buildAnnotatedString { append(link) }) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("کپی لینک دریافت نوبت")
    }
}

@Composable
fun UsageMeterSection(
    entitlements: EntitlementsResponseDto,
    onNavigateToAddons: () -> Unit = {}
) {
    val appt = entitlements.usage.appointments
    val sms = entitlements.usage.sms
    // SMS "used this month" = quota - remaining (guard for unlimited).
    val smsUsed = if (sms.quota == EntitlementKeys.UNLIMITED) 0 else (sms.quota - sms.monthlyRemaining).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مصرف این ماه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            UsageRow(
                label = "نوبت‌ها",
                icon = Icons.Rounded.Event,
                used = appt.used,
                quota = appt.quota,
                trailingNote = if (appt.wallet > 0) "کیف‌پول: ${appt.wallet}" else null
            )

            Spacer(modifier = Modifier.height(14.dp))

            UsageRow(
                label = "پیامک",
                icon = Icons.Rounded.Sms,
                used = smsUsed,
                quota = sms.quota,
                trailingNote = if (sms.wallet > 0) "کیف‌پول: ${sms.wallet}" else null
            )

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = onNavigateToAddons,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("خرید بسته‌ی افزودنی", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun UsageRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    used: Int,
    quota: Int,
    trailingNote: String? = null
) {
    val unlimited = quota == EntitlementKeys.UNLIMITED
    val fraction = if (unlimited || quota <= 0) 0f else (used.toFloat() / quota.toFloat()).coerceIn(0f, 1f)
    val nearLimit = !unlimited && fraction >= 0.85f
    val barColor = if (nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (unlimited) "$used / ∞" else "$used / $quota",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (unlimited) {
            // A subtle full track to convey "unlimited".
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }

        if (trailingNote != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trailingNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun HandleEvents(
    events: Flow<HomeEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateToLogin: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    events.collectWithLifecycleAware { event ->
        when (event) {
            HomeEvent.NavigateToLogin -> onNavigateToLogin()
            is HomeEvent.OpenUrl -> {
                uriHandler.openUri(event.url)
            }
            is HomeEvent.ShowError -> {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    AppTheme {
        HomeScreenContent(
            uiState = HomeState(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onNavigateToCalendar = {},
            onNavigateToLogin = {}
        )
    }
}
