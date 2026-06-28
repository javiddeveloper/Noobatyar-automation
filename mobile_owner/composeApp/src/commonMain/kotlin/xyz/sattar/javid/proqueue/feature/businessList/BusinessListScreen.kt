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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.business_list
import proqueue.composeapp.generated.resources.cancel
import proqueue.composeapp.generated.resources.create_first_business
import proqueue.composeapp.generated.resources.delete
import proqueue.composeapp.generated.resources.delete_business
import proqueue.composeapp.generated.resources.delete_business_confirmation
import proqueue.composeapp.generated.resources.no_business_found
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessListScreenContent(
    modifier: Modifier = Modifier,
    uiState: BusinessListState,
    onIntent: (BusinessListIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDeleteRequest: (Long) -> Unit
) {
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.business_list),
                        style = MaterialTheme.typography.titleLarge
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

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
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
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
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
            Icon(
                imageVector = Icons.Default.Factory,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary
            )

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
