package xyz.sattar.javid.proqueue.feature.visitorSelection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.archive
import proqueue.composeapp.generated.resources.back
import proqueue.composeapp.generated.resources.cancel
import proqueue.composeapp.generated.resources.create_first_visitor
import proqueue.composeapp.generated.resources.create_new_visitor
import proqueue.composeapp.generated.resources.delete
import proqueue.composeapp.generated.resources.edit
import proqueue.composeapp.generated.resources.no_visitors_found
import proqueue.composeapp.generated.resources.search_placeholder
import proqueue.composeapp.generated.resources.visitor_delete_message
import proqueue.composeapp.generated.resources.visitor_delete_title
import proqueue.composeapp.generated.resources.visitor_selection_title
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

@Composable
fun VisitorSelectionScreen(
    viewModel: VisitorSelectionViewModel = koinViewModel<VisitorSelectionViewModel>(),
    onNavigateToCreateAppointment: (Long) -> Unit,
    onNavigateToEditVisitor: (Long) -> Unit,
    onNavigateToCreateVisitor: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(VisitorSelectionIntent.LoadVisitors)
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToCreateAppointment = onNavigateToCreateAppointment,
        onNavigateToEditVisitor = onNavigateToEditVisitor,
        onNavigateToCreateVisitor = onNavigateToCreateVisitor,
        onNavigateBack = onNavigateBack
    )

    VisitorSelectionScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * (edge-to-edge search field, FAB) untouched; Medium/Expanded get the
 * desktop layout — see [VisitorSelectionWebContent]. The delete dialog and
 * pagination trigger are shared here since they're state, not layout.
 */
@Composable
fun VisitorSelectionScreenContent(
    modifier: Modifier = Modifier,
    uiState: VisitorSelectionState,
    onIntent: (VisitorSelectionIntent) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var visitorToDelete by remember { mutableStateOf<Long?>(null) }

    if (showDeleteDialog && visitorToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.visitor_delete_title)) },
            text = { Text(stringResource(Res.string.visitor_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        visitorToDelete?.let { id ->
                            onIntent(VisitorSelectionIntent.DeleteVisitor(id))
                        }
                        showDeleteDialog = false
                        visitorToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    val onDeleteRequest: (Long) -> Unit = { id ->
        visitorToDelete = id
        showDeleteDialog = true
    }

    if (LocalWindowSize.current == WindowSize.Compact) {
        VisitorSelectionPhoneContent(
            modifier = modifier,
            uiState = uiState,
            onIntent = onIntent,
            onDeleteRequest = onDeleteRequest
        )
    } else {
        VisitorSelectionWebContent(
            modifier = modifier,
            uiState = uiState,
            onIntent = onIntent,
            onDeleteRequest = onDeleteRequest
        )
    }
}

/**
 * Phone layout — unchanged. Edge-to-edge search field, single-column list
 * and a FAB for "create visitor", right for thumb reach on a phone. See
 * [VisitorSelectionWebContent] for the desktop layout.
 */
@Composable
private fun VisitorSelectionPhoneContent(
    modifier: Modifier = Modifier,
    uiState: VisitorSelectionState,
    onIntent: (VisitorSelectionIntent) -> Unit,
    onDeleteRequest: (Long) -> Unit
) {
    val lazyListState = rememberLazyListState()

    // Pagination logic
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = lazyListState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            uiState.canLoadMore && !uiState.isLoading && lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onIntent(VisitorSelectionIntent.LoadMore)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onIntent(VisitorSelectionIntent.CreateNewVisitor)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = stringResource(Res.string.create_new_visitor)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.visitor_selection_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(VisitorSelectionIntent.BackPress) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            VisitorSearchField(
                query = uiState.searchQuery,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                uiState.filteredVisitors.isEmpty() && uiState.isLoading && uiState.currentPage == 1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(6) {
                            xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                        }
                    }
                }

                uiState.filteredVisitors.isEmpty() && uiState.searchQuery.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyVisitorState()
                    }
                }

                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                    ) {
                        itemsIndexed(
                            items = uiState.filteredVisitors,
                            key = { _, visitor -> visitor.id }
                        ) { index, visitor ->
                            VisitorItem(
                                visitor = visitor,
                                onClick = { onIntent(VisitorSelectionIntent.SelectVisitor(visitor.id)) },
                                onEdit = { onIntent(VisitorSelectionIntent.EditVisitor(visitor.id)) },
                                onDelete = { onDeleteRequest(visitor.id) }
                            )
                        }

                        if (uiState.isLoading && uiState.currentPage > 1) {
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

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * Desktop layout. A floating corner FAB is a phone pattern, so "create
 * visitor" moves into the top bar action instead (same move as
 * [xyz.sattar.javid.proqueue.feature.businessList.BusinessListScreen]'s web
 * content). The search field is width-capped rather than spanning edge to
 * edge, and results lay out as a grid — 2 columns at Medium, 3 at Expanded —
 * so the list uses the extra width instead of leaving it empty like a phone
 * column would.
 */
@Composable
private fun VisitorSelectionWebContent(
    modifier: Modifier = Modifier,
    uiState: VisitorSelectionState,
    onIntent: (VisitorSelectionIntent) -> Unit,
    onDeleteRequest: (Long) -> Unit
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded
    val gridState = rememberLazyGridState()

    // Pagination logic — same trigger as the phone column, just watching the
    // grid's layout info instead of a list's.
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            uiState.canLoadMore && !uiState.isLoading && lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onIntent(VisitorSelectionIntent.LoadMore)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.visitor_selection_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(VisitorSelectionIntent.BackPress) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onIntent(VisitorSelectionIntent.CreateNewVisitor) }) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(Res.string.create_new_visitor))
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
            Column(modifier = Modifier.fillMaxSize()) {
                VisitorSearchField(
                    query = uiState.searchQuery,
                    onIntent = onIntent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                when {
                    uiState.filteredVisitors.isEmpty() && uiState.isLoading && uiState.currentPage == 1 -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            repeat(6) {
                                xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                            }
                        }
                    }

                    uiState.filteredVisitors.isEmpty() && uiState.searchQuery.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyVisitorState()
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (isExpanded) 3 else 2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 8.dp
                            )
                        ) {
                            gridItems(
                                items = uiState.filteredVisitors,
                                key = { visitor -> visitor.id }
                            ) { visitor ->
                                VisitorItem(
                                    visitor = visitor,
                                    onClick = { onIntent(VisitorSelectionIntent.SelectVisitor(visitor.id)) },
                                    onEdit = { onIntent(VisitorSelectionIntent.EditVisitor(visitor.id)) },
                                    onDelete = { onDeleteRequest(visitor.id) }
                                )
                            }

                            if (uiState.isLoading && uiState.currentPage > 1) {
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

                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The search field, shared by [VisitorSelectionPhoneContent] and
 * [VisitorSelectionWebContent] so the two can't drift — only its width
 * differs (edge-to-edge on phone, capped by [ContentWidth.List] via
 * [AppScaffold] on desktop).
 */
@Composable
private fun VisitorSearchField(
    query: String,
    onIntent: (VisitorSelectionIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onIntent(VisitorSelectionIntent.SearchVisitors(it)) },
        modifier = modifier,
        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        maxLines = 1,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        singleLine = true
    )
}

@Composable
fun EmptyVisitorState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.no_visitors_found),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.create_first_visitor),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VisitorItem(
    visitor: Visitor,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = visitor.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = visitor.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HandleEvents(
    events: Flow<VisitorSelectionEvent>,
    onNavigateToCreateAppointment: (Long) -> Unit,
    onNavigateToEditVisitor: (Long) -> Unit,
    onNavigateToCreateVisitor: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    events.collectWithLifecycleAware {
        when (it) {
            is VisitorSelectionEvent.NavigateToCreateAppointment -> {
                scope.launch {
                    onNavigateToCreateAppointment(it.visitorId)
                }
            }

            VisitorSelectionEvent.NavigateToCreateVisitor -> {
                scope.launch {
                    onNavigateToCreateVisitor()
                }
            }

            VisitorSelectionEvent.NavigateBack -> {
                onNavigateBack()
            }

            is VisitorSelectionEvent.NavigateToEditVisitor -> {
                onNavigateToEditVisitor(it.visitorId)
            }
        }
    }
}
