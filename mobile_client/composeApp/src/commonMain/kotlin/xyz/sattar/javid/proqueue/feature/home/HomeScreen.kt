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
import androidx.compose.ui.platform.LocalUriHandler
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
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel<HomeViewModel>(),
    onNavigateToCalendar: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.sendIntent(HomeIntent.LoadData)
    }

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
        onNavigateToLogin = onNavigateToLogin
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
    onNavigateToLogin: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.home_menu_item),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
                            modifier= Modifier.size(20.dp),onClick = onNavigateToCalendar) {
                            Icon(
                                imageVector = Icons.Rounded.Event,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = "Calendar"
                            )
                        }
                    }

                    ProfileAvatar(
                        onNavigateToLogin = onNavigateToLogin
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .padding(top = paddingValues.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ۱. بخش بنرهای پلن اشتراک (دقیقاً در ابتدای لیست)
            if (uiState.plans.isNotEmpty()) {
                item {
                    PlanBannerSection(
                        plans = uiState.plans,
                        onPlanClick = { plan ->
                            onIntent(HomeIntent.PurchasePlan(plan.id))
                        }
                    )
                }
            }

            // ۲. اطلاعات کسب‌وکار
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 100)
                    )
                ) {
                    BusinessInfoHeader(uiState)
                }
            }

            // ۳. هدر تاریخ
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 200)
                    )
                ) {
                    DateHeader()
                }
            }

            // ۴. آمار داشبورد
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
                        animationSpec = tween(500, delayMillis = 300)
                    )
                ) {
                    DashboardStatsSection(stats = uiState.stats)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
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
    // گرادینت برای جذابیت بصری
    val gradient = if (plan.isVip) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFE65100), Color(0xFFFFB300))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            // آیکون پس‌زمینه بزرگ برای زیبایی
            Icon(
                imageVector = if (plan.isVip) Icons.Rounded.WorkspacePremium else Icons.Rounded.Stars,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp),
                tint = Color.White.copy(alpha = 0.15f)
            )

            Column(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = plan.durationDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                if (plan.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    plan.description.take(2).forEach { desc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.End
            ) {
                if (plan.discountPrice != null && plan.discountPrice < plan.price) {
                    // نمایش قیمت اصلی (بدون تخفیف) به صورت خط خورده
                    Text(
                        text = "${plan.price.toString().reversed().chunked(3).joinToString(",").reversed()} تومان",
                        style = MaterialTheme.typography.labelSmall.copy(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        ),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                // نمایش قیمت نهایی (فیلد price_display سرور) به صورت برجسته
                Text(
                    text = plan.priceDisplay,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                
                if (plan.isVip) {
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "VIP",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
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
fun BusinessInfoHeader(uiState: HomeState) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = uiState.business?.title?.ifEmpty { stringResource(Res.string.welcome_to_proqueue) }
                        ?: stringResource(Res.string.welcome_to_proqueue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (uiState.business != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocationOn, 
                            null, 
                            modifier = Modifier.size(14.dp), 
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.business.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Phone, 
                            null, 
                            modifier = Modifier.size(14.dp), 
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.business.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
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
