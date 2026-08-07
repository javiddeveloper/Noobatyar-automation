package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.HomeButtonShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeChartShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeDashboardShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomePlanBannerShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeUsageShimmer
import xyz.sattar.javid.proqueue.core.ui.components.MainTopAppBar
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.ToastyType
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel<HomeViewModel>(),
    onNavigateToCalendar: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {},
    onNavigateToVisitors: (VisitorsNavArgs) -> Unit = {}
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
        onNavigateToAddons = onNavigateToAddons,
        onNavigateToVisitors = onNavigateToVisitors
    )
}

/**
 * Arguments for jumping into the visitors/appointments tab pre-filtered —
 * used when tapping a Home stat card, the queue row, or the 7-day chart.
 */
data class VisitorsNavArgs(
    val status: String? = null,
    val tab: Int? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
)

@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeState,
    snackbarHostState: SnackbarHostState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {},
    onNavigateToVisitors: (VisitorsNavArgs) -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            onIntent(HomeIntent.ClearMessage)
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
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onNavigateToCalendar() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = "Calendar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { onIntent(HomeIntent.LoadData) },
            // Keeps the badge clear of the glass top bar, which the content
            // deliberately scrolls underneath.
            indicatorTopPadding = paddingValues.calculateTopPadding(),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            // contentPadding (not a padding modifier) so items scroll *under* the
            // glass top bar rather than starting below an opaque gap.
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ۱. هدر تاریخ (سلام/زمینه‌ی امروز) + تعداد نوبت‌های امروز
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 150)
                    )
                ) {
                    DateHeader(
                        todayAppointmentsCount = uiState.stats.totalAppointments.takeIf { uiState.statsLoaded }
                    )
                }
            }

            // ۲. آمار داشبورد امروز (۴ مستطیل + یک سطر کامل برای صف)
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 200)
                    )
                ) {
                    when {
                        !uiState.statsLoaded -> HomeDashboardShimmer()
                        else -> DashboardStatsSection(
                            stats = uiState.stats,
                            peopleInQueue = uiState.queue.size,
                            // tab = 0 (مراجعین/history) explicitly — LastVisitorsState
                            // defaults selectedTab to 1 (صف/queue), where a status
                            // filter like "cancelled" has nothing to show.
                            onStatClick = { status -> onNavigateToVisitors(VisitorsNavArgs(status = status, tab = 0)) },
                            onQueueClick = { onNavigateToVisitors(VisitorsNavArgs(tab = 1)) }
                        )
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
                        // نمودار «۷ روز اخیر» است (گذشته)، پس با تپ روی آن به تب
                        // مراجعین با همان بازه‌ی گذشته می‌رویم تا داده‌ی نمایش داده
                        // شده با مقصد ناوبری همخوانی داشته باشد (نه بازه‌ی پیش‌فرض
                        // «۷ روز آینده» آن تب).
                        NeonLineChart(
                            counts = uiState.dailyCounts.map { it.count },
                            onClick = {
                                val today = DateTimeUtils.startOfTodayMillis()
                                val sevenDaysAgo = today - 6L * 24 * 60 * 60 * 1000L
                                onNavigateToVisitors(
                                    VisitorsNavArgs(dateFrom = sevenDaysAgo, dateTo = DateTimeUtils.systemCurrentMilliseconds())
                                )
                            }
                        )
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
}

@Composable
fun PlanBannerSection(
    plans: List<PlanDto>,
    onPlanClick: (PlanDto) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { plans.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // contentPadding عمداً بیشتر از 4dp است: هر صفحه کمی باریک‌تر از عرض
        // صفحه می‌شود تا لبه‌ی بنر بعدی/قبلی دیده شود و کاربر بفهمد می‌تواند
        // آن‌ها را دستی swipe کند (دیگر چرخش خودکار نداریم).
        //
        // ارتفاع همه‌ی صفحات باید یکسان باشد، حتی اگر محتوای پلن‌ها متفاوت
        // باشد. قبلاً این کار با SubcomposeLayout انجام می‌شد، اما آن رویکرد
        // HorizontalPager را داخل یک subcomposition جدا قرار می‌داد و
        // gesture drag اصلاً به Pager نمی‌رسید — یعنی swipe کاملاً از کار
        // می‌افتاد (تأیید شده روی امولاتور واقعی). به‌جایش با
        // onSizeChanged روی هر صفحه، بلندترین ارتفاع دیده‌شده را ردیابی
        // می‌کنیم و به‌عنوان ارتفاع ثابت Pager می‌دهیم — Pager همچنان یک
        // composable معمولی می‌ماند و drag درست کار می‌کند.
        val pagerContentPadding = 28.dp
        val density = LocalDensity.current
        var maxCardHeight by remember(plans) { mutableStateOf(0.dp) }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (maxCardHeight > 0.dp) Modifier.height(maxCardHeight) else Modifier),
            contentPadding = PaddingValues(horizontal = pagerContentPadding),
            pageSpacing = 10.dp
        ) { page ->
            val plan = plans[page]
            PlanBannerItem(
                plan = plan,
                onClick = { onPlanClick(plan) },
                modifier = Modifier
                    .let { if (maxCardHeight > 0.dp) it.height(maxCardHeight) else it }
                    .onSizeChanged { size ->
                        val heightDp = with(density) { size.height.toDp() }
                        if (heightDp > maxCardHeight) maxCardHeight = heightDp
                    }
            )
        }

        if (plans.size > 1) {
            Text(
                text = "برای مشاهده‌ی سایر طرح‌ها بکشید ⟷",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
fun DateHeader(modifier: Modifier = Modifier, todayAppointmentsCount: Int? = null) {
    val currentTime = DateTimeUtils.systemCurrentMilliseconds()
    val formattedDate = DateTimeUtils.formatDate(currentTime)

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
            Text(
                text = "امروز، $formattedDate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // تعداد نوبت‌های امروز — قبلاً یک مستطیل جدا بود («نوبت‌های امروز»)،
        // حالا کنار هدر تاریخ به‌صورت یک بج کوچک نمایش داده می‌شود.
        if (todayAppointmentsCount != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$todayAppointmentsCount نوبت",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardStatsSection(
    stats: DashboardStats,
    peopleInQueue: Int = 0,
    onStatClick: (status: String?) -> Unit = {},
    onQueueClick: () -> Unit = {}
) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ۴ مستطیل آمار (۲×۲) — «نوبت‌های امروز» حذف شد چون تعدادش کنار هدر
        // تاریخ نمایش داده می‌شود.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "تکمیل شده",
                value = stats.completedAppointments.toString(),
                containerColor = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9),
                contentColor = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
                icon = Icons.Rounded.CheckCircle,
                onClick = { onStatClick("COMPLETED") }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "کل مراجعین",
                value = stats.totalVisitors.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Rounded.People,
                onClick = { onStatClick(null) }
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
                icon = Icons.Rounded.Cancel,
                onClick = { onStatClick("NO_SHOW") }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "لغو شده",
                value = stats.cancelledAppointments.toString(),
                containerColor = if (isDark) Color(0xFF4A148C).copy(alpha = 0.4f) else Color(0xFFF3E5F5),
                contentColor = if (isDark) Color(0xFFCE93D8) else Color(0xFF6A1B9A),
                icon = Icons.Rounded.EventBusy,
                onClick = { onStatClick("CANCELLED") }
            )
        }

        // سطر کامل: افراد در حال حاضر در صف (امروز، در انتظار)
        QueueStatRow(count = peopleInQueue, onClick = onQueueClick)
    }
}

@Composable
fun QueueStatRow(count: Int, onClick: () -> Unit = {}) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF01579B).copy(alpha = 0.35f) else Color(0xFFE1F5FE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "افراد در صف",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
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

    var copied by remember { mutableStateOf(false) }

    // بعد ۲ ثانیه برگردد
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (copied) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400)
    )
    val contentColor = Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(buildAnnotatedString { append(link) })
                copied = true
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (copied) "کپی شد! برای مشتریان بفرستید 🎉" else "لینک نوبت‌گیری آنلاین",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (copied) link else "مشتریان با یک کلیک نوبت می‌گیرن",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = copied,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(300))) togetherWith
                    (fadeOut(tween(200)) + scaleOut(tween(200)))
                }
            ) { isCopied ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
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
                snackbarHostState.showToasty(event.message, ToastyType.Error)
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
