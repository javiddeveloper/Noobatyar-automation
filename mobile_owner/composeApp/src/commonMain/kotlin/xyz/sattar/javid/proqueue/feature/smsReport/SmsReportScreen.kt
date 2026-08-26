package xyz.sattar.javid.proqueue.feature.smsReport

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogStatus
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto

/** Longest preview shown in a row before the message is cut with an ellipsis. */
private const val MESSAGE_PREVIEW_LENGTH = 90

@Composable
fun SmsReportScreen(
    viewModel: SmsReportViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(SmsReportIntent.Load)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    // Same trigger the visitor list uses: fire once the tail comes into view.
    val shouldLoadMore = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            uiState.canLoadMore && !uiState.isLoading && !uiState.isPaginating &&
                    total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) viewModel.sendIntent(SmsReportIntent.LoadMore)
    }

    SmsReportScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        listState = listState,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::sendIntent
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * (stacked quota card + list of message cards) untouched; anything wider
 * gets the desktop layout — see [SmsReportWebContent].
 */
@Composable
fun SmsReportScreenContent(
    uiState: SmsReportState,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onNavigateBack: () -> Unit,
    onIntent: (SmsReportIntent) -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        SmsReportPhoneContent(uiState, snackbarHostState, listState, onNavigateBack, onIntent)
    } else {
        SmsReportWebContent(uiState, snackbarHostState, listState, onNavigateBack, onIntent)
    }
}

/**
 * Phone layout — unchanged. Quota as its own stacked card, filters below it,
 * then a column of full-width message cards. See [SmsReportWebContent] for
 * the desktop header + table.
 */
@Composable
private fun SmsReportPhoneContent(
    uiState: SmsReportState,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onNavigateBack: () -> Unit,
    onIntent: (SmsReportIntent) -> Unit
) {
    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.sms_report_title),
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            uiState.summary?.let { summary ->
                SmsQuotaCard(
                    summary = summary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            SearchField(
                query = uiState.searchQuery,
                onQueryChange = { onIntent(SmsReportIntent.SetSearchQuery(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatusFilterRow(
                selected = uiState.statusFilter,
                onSelect = { onIntent(SmsReportIntent.SetStatusFilter(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            DateRangeFilterRow(
                selected = uiState.dateRangeFilter,
                onSelect = { onIntent(SmsReportIntent.SetDateRangeFilter(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            SmsReportListBody(
                uiState = uiState,
                listState = listState,
                onIntent = onIntent,
                rowContent = { log -> SmsLogRow(log) }
            )
        }
    }
}

/**
 * Desktop layout. The quota summary moves from a stacked card into a
 * horizontal header band — it reads as page-level context ("here's your
 * account status") rather than one card among equals. Filters sit in a
 * single row since there's now room. The log itself becomes a table: a
 * column-header row followed by table-style rows instead of full-width
 * message cards, which is the shape a long, scannable log wants at desktop
 * width. Loading/empty/error states and pagination are the exact same
 * [SmsReportListBody] used by [SmsReportPhoneContent] — only [rowContent]
 * differs.
 */
@Composable
private fun SmsReportWebContent(
    uiState: SmsReportState,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onNavigateBack: () -> Unit,
    onIntent: (SmsReportIntent) -> Unit
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.sms_report_title),
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
            maxWidth = if (isExpanded) ContentWidth.Wide else ContentWidth.List
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))

                uiState.summary?.let { summary ->
                    SmsQuotaHeader(summary = summary, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // The search field gets a width cap of its own (ContentWidth.Form)
                // rather than stretching to the full 1100dp column — a search box
                // that wide reads oddly even where the table below it is wide.
                SearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { onIntent(SmsReportIntent.SetSearchQuery(it)) },
                    modifier = Modifier.widthIn(max = ContentWidth.Form).fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusFilterRow(
                        selected = uiState.statusFilter,
                        onSelect = { onIntent(SmsReportIntent.SetStatusFilter(it)) }
                    )
                    DateRangeFilterRow(
                        selected = uiState.dateRangeFilter,
                        onSelect = { onIntent(SmsReportIntent.SetDateRangeFilter(it)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.logs.isNotEmpty()) {
                    SmsLogTableHeader(modifier = Modifier.fillMaxWidth())
                }

                SmsReportListBody(
                    uiState = uiState,
                    listState = listState,
                    onIntent = onIntent,
                    rowContent = { log -> SmsLogTableRow(log) }
                )
            }
        }
    }
}

/**
 * Loading/error/empty/list switch shared by both layouts, parameterized only
 * on how a single log entry renders ([rowContent]) — the phone card and the
 * desktop table row. Keeping this shared means pagination
 * ([SmsReportState.canLoadMore]/[SmsReportState.isPaginating]) and the error
 * retry action can't diverge between layouts.
 */
@Composable
private fun ColumnScope.SmsReportListBody(
    uiState: SmsReportState,
    listState: LazyListState,
    onIntent: (SmsReportIntent) -> Unit,
    rowContent: @Composable (SmsLogDto) -> Unit
) {
    when {
        uiState.isLoading && uiState.logs.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(5) { ListItemShimmer(height = 88.dp) }
            }
        }

        uiState.errorMessage != null && uiState.logs.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.sms_report_error_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { onIntent(SmsReportIntent.Load) }) {
                        Text(stringResource(Res.string.sms_report_retry))
                    }
                }
            }
        }

        uiState.logs.isEmpty() -> {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                icon = Icons.Rounded.Sms,
                title = stringResource(Res.string.sms_report_empty_title),
                subtitle = stringResource(Res.string.sms_report_empty_subtitle)
            )
        }

        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.logs, key = { it.id }) { log ->
                    rowContent(log)
                }
                if (uiState.isPaginating) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quota picture for the current month. `monthly_quota == -1` means unlimited, in
 * which case "remaining" is meaningless and the progress bar is dropped.
 */
@Composable
private fun SmsQuotaCard(summary: SmsLogSummaryDto, modifier: Modifier = Modifier) {
    val unlimited = summary.monthlyQuota < 0
    val remaining = (summary.monthlyQuota - summary.monthlyUsed).coerceAtLeast(0)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.sms_report_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stringResource(Res.string.sms_report_summary_used)}: " +
                            summary.monthlyUsed.toString().toPersianDigits(),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(Res.string.sms_report_summary_remaining)}: " +
                            if (unlimited) stringResource(Res.string.sms_report_summary_unlimited)
                            else remaining.toString().toPersianDigits(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!unlimited && summary.monthlyQuota > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        (summary.monthlyUsed.toFloat() / summary.monthlyQuota).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuotaStat(
                    label = stringResource(Res.string.sms_report_summary_sent),
                    value = summary.sentThisMonth.toString().toPersianDigits()
                )
                QuotaStat(
                    label = stringResource(Res.string.sms_report_summary_failed),
                    value = summary.failedThisMonth.toString().toPersianDigits(),
                    valueColor = MaterialTheme.colorScheme.error
                )
                QuotaStat(
                    label = stringResource(Res.string.sms_report_summary_wallet),
                    value = summary.walletBalance.toString().toPersianDigits()
                )
            }
        }
    }
}

/**
 * Desktop counterpart of [SmsQuotaCard]: the exact same fields and the exact
 * same unlimited/remaining/progress computation, laid out as a horizontal
 * header band (title + progress on one line, the three stats spread across
 * the row) instead of a stacked card, so it reads as page context rather
 * than a list item.
 */
@Composable
private fun SmsQuotaHeader(summary: SmsLogSummaryDto, modifier: Modifier = Modifier) {
    val unlimited = summary.monthlyQuota < 0
    val remaining = (summary.monthlyQuota - summary.monthlyUsed).coerceAtLeast(0)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.sms_report_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    QuotaStat(
                        label = stringResource(Res.string.sms_report_summary_used),
                        value = summary.monthlyUsed.toString().toPersianDigits()
                    )
                    QuotaStat(
                        label = stringResource(Res.string.sms_report_summary_remaining),
                        value = if (unlimited) stringResource(Res.string.sms_report_summary_unlimited)
                                else remaining.toString().toPersianDigits()
                    )
                    QuotaStat(
                        label = stringResource(Res.string.sms_report_summary_sent),
                        value = summary.sentThisMonth.toString().toPersianDigits()
                    )
                    QuotaStat(
                        label = stringResource(Res.string.sms_report_summary_failed),
                        value = summary.failedThisMonth.toString().toPersianDigits(),
                        valueColor = MaterialTheme.colorScheme.error
                    )
                    QuotaStat(
                        label = stringResource(Res.string.sms_report_summary_wallet),
                        value = summary.walletBalance.toString().toPersianDigits()
                    )
                }
            }

            if (!unlimited && summary.monthlyQuota > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        (summary.monthlyUsed.toFloat() / summary.monthlyQuota).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun QuotaStat(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = valueColor)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Filters by the visitor's name or phone number — see `search` on the backend. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(Res.string.sms_report_search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(Res.string.sms_report_search_clear)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun DateRangeFilterRow(
    selected: SmsReportDateRange,
    onSelect: (SmsReportDateRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            SmsReportDateRange.ALL to Res.string.sms_report_date_filter_all,
            SmsReportDateRange.TODAY to Res.string.sms_report_date_filter_today,
            SmsReportDateRange.THIS_WEEK to Res.string.sms_report_date_filter_week,
            SmsReportDateRange.THIS_MONTH to Res.string.sms_report_date_filter_month
        )
        options.forEach { (range, label) ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(stringResource(label)) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun StatusFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.sms_report_filter_all)) },
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = selected == SmsLogStatus.SENT,
            onClick = { onSelect(SmsLogStatus.SENT) },
            label = { Text(stringResource(Res.string.sms_report_filter_sent)) },
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = selected == SmsLogStatus.FAILED,
            onClick = { onSelect(SmsLogStatus.FAILED) },
            label = { Text(stringResource(Res.string.sms_report_filter_failed)) },
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = selected == SmsLogStatus.SKIPPED_QUOTA,
            onClick = { onSelect(SmsLogStatus.SKIPPED_QUOTA) },
            label = { Text(stringResource(Res.string.sms_report_filter_skipped)) },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun SmsLogRow(log: SmsLogDto) {
    val failed = log.status == SmsLogStatus.FAILED
    val skipped = log.status == SmsLogStatus.SKIPPED_QUOTA
    // Not MaterialTheme.colorScheme.tertiary: this app's theme only overrides
    // `primary`, so tertiary falls back to Material3's baseline (a muddy
    // brownish-pink) — a hardcoded amber matches the warning color used
    // elsewhere (e.g. front_client's --color-warning).
    val statusColor = when {
        failed -> MaterialTheme.colorScheme.error
        skipped -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }
    // A null visitor means the message went to the owner (new-booking notice).
    val recipient = log.visitor?.let { visitor ->
        visitor.fullName.ifBlank { visitor.phoneNumber }
    } ?: stringResource(Res.string.sms_report_owner_recipient)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipient,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    log.visitor?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                        Text(
                            text = phone.toPersianDigits(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(
                            when {
                                failed -> Res.string.sms_report_status_failed
                                skipped -> Res.string.sms_report_status_skipped
                                else -> Res.string.sms_report_status_sent
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = log.messageText.previewText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if ((failed || skipped) && !log.errorDetail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.errorDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            log.sentAt?.takeIf { it.isNotBlank() }?.let { sentAt ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DateTimeUtils.formatDateTime(
                        DateTimeUtils.parseIsoToEpochMillis(sentAt)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Column header for [SmsLogTableRow]. Weights match that row's columns
 * exactly. Under the app's forced RTL layout direction, the first column
 * written here (recipient) lands on the right — the same reading order the
 * phone card uses top-to-bottom: who it went to, what it said, when, and
 * whether it succeeded.
 */
@Composable
private fun SmsLogTableHeader(modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("گیرنده", style = labelStyle, color = labelColor, modifier = Modifier.weight(0.22f))
        Text("متن پیام", style = labelStyle, color = labelColor, modifier = Modifier.weight(0.42f))
        Text("زمان ارسال", style = labelStyle, color = labelColor, modifier = Modifier.weight(0.18f))
        Text("وضعیت", style = labelStyle, color = labelColor, modifier = Modifier.weight(0.18f))
    }
}

/**
 * Table-row rendering of a single [SmsLogDto] for the desktop layout. Same
 * fields and the same failed/skipped/sent color mapping as [SmsLogRow] —
 * only the shape (one dense row instead of a padded card) differs.
 */
@Composable
private fun SmsLogTableRow(log: SmsLogDto) {
    val failed = log.status == SmsLogStatus.FAILED
    val skipped = log.status == SmsLogStatus.SKIPPED_QUOTA
    val statusColor = when {
        failed -> MaterialTheme.colorScheme.error
        skipped -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }
    val recipient = log.visitor?.let { visitor ->
        visitor.fullName.ifBlank { visitor.phoneNumber }
    } ?: stringResource(Res.string.sms_report_owner_recipient)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.22f)) {
                Text(
                    text = recipient,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                log.visitor?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                    Text(
                        text = phone.toPersianDigits(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = log.messageText.previewText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(0.42f)
            )

            Text(
                text = log.sentAt?.takeIf { it.isNotBlank() }?.let { sentAt ->
                    DateTimeUtils.formatDateTime(DateTimeUtils.parseIsoToEpochMillis(sentAt))
                } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.18f)
            )

            Column(modifier = Modifier.weight(0.18f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(
                            when {
                                failed -> Res.string.sms_report_status_failed
                                skipped -> Res.string.sms_report_status_skipped
                                else -> Res.string.sms_report_status_sent
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                if ((failed || skipped) && !log.errorDetail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.errorDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun String.previewText(): String =
    if (length <= MESSAGE_PREVIEW_LENGTH) this
    else take(MESSAGE_PREVIEW_LENGTH).trimEnd() + "…"
