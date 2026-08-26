package xyz.sattar.javid.proqueue.feature.businessList

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.business_list
import proqueue.composeapp.generated.resources.cancel
import proqueue.composeapp.generated.resources.create_business
import proqueue.composeapp.generated.resources.create_first_business
import proqueue.composeapp.generated.resources.delete
import proqueue.composeapp.generated.resources.delete_business
import proqueue.composeapp.generated.resources.delete_business_confirmation
import proqueue.composeapp.generated.resources.no_business_found
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun BusinessListScreen(
    viewModel: BusinessListViewModel = koinViewModel<BusinessListViewModel>(),
    onNavigateToMain: (Business) -> Unit,
    onNavigateToCreateBusiness: () -> Unit,
    onNavigateToEditBusiness: (Long) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var businessToDelete by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(BusinessListIntent.ObserveBusinesses)
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToMain = onNavigateToMain,
        onNavigateToCreateBusiness = onNavigateToCreateBusiness,
        onNavigateToEditBusiness = onNavigateToEditBusiness,
        onNavigateToLogin = onNavigateToLogin,
        snackbarHostState = snackbarHostState,
        onRetry = { viewModel.sendIntent(BusinessListIntent.RetryFetch) }
    )

    if (businessToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { businessToDelete = null },
            title = { Text(stringResource(Res.string.delete_business)) },
            text = { Text(stringResource(Res.string.delete_business_confirmation)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        businessToDelete?.let {
                            viewModel.sendIntent(BusinessListIntent.OnDeleteBusinessClick(it))
                        }
                        businessToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { businessToDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    BusinessListScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        onNavigateToLogin = onNavigateToLogin,
        snackbarHostState = snackbarHostState,
        onDeleteRequest = { businessToDelete = it }
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * (single-column list, FAB) untouched; Medium/Expanded get a card grid — see
 * [BusinessListWebContent]. This is the first screen after login, so its
 * layout sets the tone for the whole desktop panel.
 */
@Composable
fun BusinessListScreenContent(
    modifier: Modifier = Modifier,
    uiState: BusinessListState,
    onIntent: (BusinessListIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDeleteRequest: (Long) -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        BusinessListPhoneContent(
            modifier = modifier,
            uiState = uiState,
            onIntent = onIntent,
            onNavigateToLogin = onNavigateToLogin,
            snackbarHostState = snackbarHostState,
            onDeleteRequest = onDeleteRequest
        )
    } else {
        BusinessListWebContent(
            modifier = modifier,
            uiState = uiState,
            onIntent = onIntent,
            onNavigateToLogin = onNavigateToLogin,
            snackbarHostState = snackbarHostState,
            onDeleteRequest = onDeleteRequest
        )
    }
}

/**
 * Phone layout — unchanged. A FAB-triggered single column list, right for
 * thumb reach on a phone. See [BusinessListWebContent] for the desktop grid.
 */
@Composable
private fun BusinessListPhoneContent(
    modifier: Modifier = Modifier,
    uiState: BusinessListState,
    onIntent: (BusinessListIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDeleteRequest: (Long) -> Unit
) {
    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.business_list),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(BusinessListIntent.OnCreateBusinessClick) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->

        // AppScaffold is a pass-through Box at Compact/Medium (unchanged
        // layout); only Expanded centers and width-caps this content.
        AppScaffold(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(5) {
                            xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                        }
                    }
                }

                uiState.businesses.isEmpty() -> {
                    EmptyBusinessState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val shouldLoadMore by androidx.compose.runtime.remember {
                        androidx.compose.runtime.derivedStateOf {
                            val totalItems = listState.layoutInfo.totalItemsCount
                            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisibleItem >= totalItems - 2
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && !uiState.isLoading && !uiState.isPaginating) {
                            onIntent(BusinessListIntent.LoadNextPage)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.businesses) { business ->
                            BusinessItem(
                                business = business,
                                onClick = { onIntent(BusinessListIntent.OnBusinessClick(business)) },
                                onEdit = { onIntent(BusinessListIntent.OnEditBusinessClick(business.id)) },
                                onDelete = { onDeleteRequest(business.id) }
                            )
                        }
                        if (uiState.isPaginating) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop layout. A single stacked column of full-width rows reads as a
 * narrow ribbon on a 1920px monitor, so this becomes a card grid instead —
 * 2 columns at Medium (~800dp), 3 at Expanded, capped by [ContentWidth.List]
 * the same way [xyz.sattar.javid.proqueue.feature.settings.SettingsContent]
 * caps its own card list. The FAB is a mobile pattern (thumb reach on a
 * bottom corner), so on desktop the "create business" affordance moves into
 * the top bar instead, staying just as prominent without floating over the
 * grid.
 */
@Composable
private fun BusinessListWebContent(
    modifier: Modifier = Modifier,
    uiState: BusinessListState,
    onIntent: (BusinessListIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDeleteRequest: (Long) -> Unit
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.business_list),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    TextButton(onClick = { onIntent(BusinessListIntent.OnCreateBusinessClick) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(Res.string.create_business))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        AppScaffold(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            maxWidth = ContentWidth.List
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(5) {
                            xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                        }
                    }
                }

                uiState.businesses.isEmpty() -> {
                    EmptyBusinessState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    val shouldLoadMore by androidx.compose.runtime.remember {
                        androidx.compose.runtime.derivedStateOf {
                            val totalItems = gridState.layoutInfo.totalItemsCount
                            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisibleItem >= totalItems - 2
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && !uiState.isLoading && !uiState.isPaginating) {
                            onIntent(BusinessListIntent.LoadNextPage)
                        }
                    }

                    // 2 columns at Medium — a third would squeeze each card
                    // below ~230dp inside the 760dp List cap, tight for a
                    // logo + title + category + moderation badge.
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(if (isExpanded) 3 else 2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        gridItems(uiState.businesses) { business ->
                            BusinessCardWeb(
                                business = business,
                                onClick = { onIntent(BusinessListIntent.OnBusinessClick(business)) },
                                onEdit = { onIntent(BusinessListIntent.OnEditBusinessClick(business.id)) },
                                onDelete = { onDeleteRequest(business.id) }
                            )
                        }
                        if (uiState.isPaginating) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grid tile for [BusinessListWebContent]. Same fields and handlers as
 * [BusinessItem] (logo, title, category, moderation state, edit/delete
 * menu) — the phone row is just wide-and-short where this is
 * narrow-and-tall, since a grid cell can't rely on unlimited width for the
 * subtitle the way a full-width row can.
 */
@Composable
private fun BusinessCardWeb(
    business: Business,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (business.logoPath.isNotEmpty()) {
                        val url = if (business.logoPath.startsWith("http")) business.logoPath
                        else "${xyz.sattar.javid.proqueue.core.AppConfig.BASE_URL}${business.logoPath}"
                        coil3.compose.AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Factory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // weight(1f) pushes the menu button to the row's end, which
                // in RTL is the left edge — same corner a desktop user
                // expects a card's overflow menu to live in.
                Spacer(modifier = Modifier.weight(1f))

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ویرایش") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = business.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = business.category.persianName,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val moderationStatus = business.moderationStatus
            if (moderationStatus != null) {
                Spacer(modifier = Modifier.height(10.dp))
                xyz.sattar.javid.proqueue.core.ui.components.ModerationBadge(business = business)
                if (!business.isPubliclyVisible) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "برای مراجعین نمایش داده نمی‌شود",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BusinessItem(
    business: Business,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (business.logoPath.isNotEmpty()) {
                    val url = if (business.logoPath.startsWith("http")) business.logoPath
                    else "${xyz.sattar.javid.proqueue.core.AppConfig.BASE_URL}${business.logoPath}"
                    coil3.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Factory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = business.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = business.address,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Moderation state, so the owner can tell at a glance which of
                // their businesses clients can actually see.
                val moderationStatus = business.moderationStatus
                if (moderationStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    xyz.sattar.javid.proqueue.core.ui.components.ModerationBadge(business = business)
                    // Checks both moderation AND the billing lock — a business can be
                    // APPROVED but still invisible to clients because the plan lapsed,
                    // and moderationStatus.isPubliclyVisible alone would miss that.
                    if (!business.isPubliclyVisible) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "برای مراجعین نمایش داده نمی‌شود",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("ویرایش") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyBusinessState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Factory,
            contentDescription = null,
            modifier = Modifier.size(50.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.no_business_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.create_first_business),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HandleEvents(
    events: Flow<BusinessListEvent>,
    onNavigateToMain: (Business) -> Unit,
    onNavigateToCreateBusiness: () -> Unit,
    onNavigateToEditBusiness: (Long) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onRetry: () -> Unit
) {
    val scope = rememberCoroutineScope()
    events.collectWithLifecycleAware {
        when (it) {
            is BusinessListEvent.NavigateToMain -> {
                scope.launch {
                    onNavigateToMain(it.business)
                }
            }

            BusinessListEvent.NavigateToCreateBusiness -> {
                scope.launch {
                    onNavigateToCreateBusiness()
                }
            }

            is BusinessListEvent.NavigateToEditBusiness -> {
                scope.launch {
                    onNavigateToEditBusiness(it.businessId)
                }
            }

            BusinessListEvent.NavigateToLogin -> {
                scope.launch {
                    onNavigateToLogin()
                }
            }

            is BusinessListEvent.ShowMessage -> {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = it.message,
                        actionLabel = "تلاش مجدد",
                        duration = androidx.compose.material3.SnackbarDuration.Long
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        onRetry()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    AppTheme {
        BusinessListScreenContent(
            uiState = BusinessListState(),
            onIntent = {},
            onNavigateToLogin = {},
            snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
            onDeleteRequest = {}
        )
    }
}
