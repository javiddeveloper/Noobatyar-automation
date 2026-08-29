package xyz.sattar.javid.proqueue.feature.createVisitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.confirm
import proqueue.composeapp.generated.resources.create_visitor
import proqueue.composeapp.generated.resources.edit
import proqueue.composeapp.generated.resources.edit_visitor
import proqueue.composeapp.generated.resources.phone
import proqueue.composeapp.generated.resources.register_visitor
import proqueue.composeapp.generated.resources.visitor_name
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun CreateVisitorRoute(
    visitorId: Long? = null,
    viewModel: CreateVisitorViewModel = koinViewModel<CreateVisitorViewModel>(),
    onContinue: (Long) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visitorId) {
        if (visitorId != null) {
            viewModel.sendIntent(CreateVisitorIntent.LoadVisitor(visitorId))
        }
    }

    LaunchedEffect(uiState.loadedVisitor) {
        uiState.loadedVisitor?.let {
            fullName = it.fullName
            phoneNumber = it.phoneNumber
        }
    }

    HandleEvents(
        events = viewModel.events,
        onContinue = onContinue,
        onNavigateBack = onNavigateBack
    )

    CreateVisitorScreen(
        uiState = uiState,
        onIntent = { intent ->
            if (intent is CreateVisitorIntent.CreateVisitor) {
                if (visitorId != null) {
                    viewModel.sendIntent(
                        CreateVisitorIntent.EditVisitor(
                            fullName = intent.fullName,
                            phoneNumber = intent.phoneNumber,
                            visitorId = visitorId
                        )
                    )
                } else {
                    viewModel.sendIntent(intent)
                }
            } else {
                viewModel.sendIntent(intent)
            }
        },
        fullName = fullName,
        phoneNumber = phoneNumber,
        onFullName = {
            fullName = it
            nameError = null
        },
        onPhoneNumber = {
            phoneNumber = it
            phoneError = null
        },
        isEditing = visitorId != null,
        nameError = nameError,
        phoneError = phoneError,
        onNameErrorUpdate = { nameError = it },
        onPhoneErrorUpdate = { phoneError = it }
    )
}

@Composable
fun CreateVisitorScreen(
    modifier: Modifier = Modifier,
    uiState: CreateVisitorState,
    onIntent: (CreateVisitorIntent) -> Unit,
    fullName: String,
    phoneNumber: String,
    onFullName: (String) -> Unit,
    onPhoneNumber: (String) -> Unit,
    isEditing: Boolean = false,
    nameError: String? = null,
    phoneError: String? = null,
    onNameErrorUpdate: (String?) -> Unit = {},
    onPhoneErrorUpdate: (String?) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Shared validate-then-submit logic so the phone and web layouts can't
    // drift on what counts as a valid name/phone.
    val onSubmit: () -> Unit = {
        val name = fullName.trim()
        val phone = phoneNumber.trim()
        val nameInvalid = name.length < 3
        val phoneInvalid = phone.length < 7
        onNameErrorUpdate(if (nameInvalid) "نام صحیح نیست" else null)
        onPhoneErrorUpdate(if (phoneInvalid) "شماره تلفن صحیح نیست" else null)
        if (!nameInvalid && !phoneInvalid) {
            onIntent(CreateVisitorIntent.CreateVisitor(name, phone))
        }
    }

    if (LocalWindowSize.current == WindowSize.Compact) {
        CreateVisitorPhoneContent(
            modifier = modifier,
            uiState = uiState,
            onIntent = onIntent,
            fullName = fullName,
            phoneNumber = phoneNumber,
            onFullName = onFullName,
            onPhoneNumber = onPhoneNumber,
            isEditing = isEditing,
            nameError = nameError,
            phoneError = phoneError,
            snackbarHostState = snackbarHostState,
            onSubmit = onSubmit
        )
    } else {
        CreateVisitorWebContent(
            modifier = modifier,
            uiState = uiState,
            onIntent = onIntent,
            fullName = fullName,
            phoneNumber = phoneNumber,
            onFullName = onFullName,
            onPhoneNumber = onPhoneNumber,
            isEditing = isEditing,
            nameError = nameError,
            phoneError = phoneError,
            snackbarHostState = snackbarHostState,
            onSubmit = onSubmit
        )
    }
}

/**
 * Phone layout — unchanged. A Scaffold whose bottomBar welds the submit
 * button to the bottom of the viewport, which is right on a phone (thumb
 * reach) and wrong on a desktop browser — see [CreateVisitorWebContent] for
 * that case, where the button instead sits directly under the fields.
 */
@Composable
private fun CreateVisitorPhoneContent(
    modifier: Modifier = Modifier,
    uiState: CreateVisitorState,
    onIntent: (CreateVisitorIntent) -> Unit,
    fullName: String,
    phoneNumber: String,
    onFullName: (String) -> Unit,
    onPhoneNumber: (String) -> Unit,
    isEditing: Boolean,
    nameError: String?,
    phoneError: String?,
    snackbarHostState: SnackbarHostState,
    onSubmit: () -> Unit
) {
    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(Res.string.edit_visitor) else stringResource(Res.string.create_visitor),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onIntent(CreateVisitorIntent.BackPress)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                AppButton(
                    text = if (isEditing) stringResource(Res.string.edit) else stringResource(Res.string.register_visitor),
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    isLoading = uiState.isLoading
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            CreateVisitorFormFields(
                fullName = fullName,
                phoneNumber = phoneNumber,
                onFullName = onFullName,
                onPhoneNumber = onPhoneNumber,
                nameError = nameError,
                phoneError = phoneError,
                isLoading = uiState.isLoading
            )

            // Spacer replaced by bottomBar button
        }
    }
}

/**
 * Desktop layout. The bottomBar phone pattern (a full-width button welded to
 * the viewport bottom, with the two fields floating far above it) is the
 * clearest "stretched phone app" tell on a wide browser window, so here the
 * submit button moves to sit directly under the fields instead, inside a
 * card capped at [ContentWidth.Form] — a two-field form stretched to 1900px
 * reads badly long before a list or dashboard screen does.
 */
@Composable
private fun CreateVisitorWebContent(
    modifier: Modifier = Modifier,
    uiState: CreateVisitorState,
    onIntent: (CreateVisitorIntent) -> Unit,
    fullName: String,
    phoneNumber: String,
    onFullName: (String) -> Unit,
    onPhoneNumber: (String) -> Unit,
    isEditing: Boolean,
    nameError: String?,
    phoneError: String?,
    snackbarHostState: SnackbarHostState,
    onSubmit: () -> Unit
) {
    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(Res.string.edit_visitor) else stringResource(Res.string.create_visitor),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onIntent(CreateVisitorIntent.BackPress)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
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
            maxWidth = ContentWidth.Form
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CreateVisitorFormFields(
                            fullName = fullName,
                            phoneNumber = phoneNumber,
                            onFullName = onFullName,
                            onPhoneNumber = onPhoneNumber,
                            nameError = nameError,
                            phoneError = phoneError,
                            isLoading = uiState.isLoading
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        AppButton(
                            text = if (isEditing) stringResource(Res.string.edit) else stringResource(Res.string.register_visitor),
                            onClick = onSubmit,
                            modifier = Modifier.fillMaxWidth(),
                            isLoading = uiState.isLoading
                        )
                    }
                }
            }
        }
    }
}

/**
 * The two form fields, shared by [CreateVisitorPhoneContent] and
 * [CreateVisitorWebContent] so they can't drift apart — only the chrome
 * (app bar, action placement, card) differs between the two.
 */
@Composable
private fun ColumnScope.CreateVisitorFormFields(
    fullName: String,
    phoneNumber: String,
    onFullName: (String) -> Unit,
    onPhoneNumber: (String) -> Unit,
    nameError: String?,
    phoneError: String?,
    isLoading: Boolean
) {
    AppTextField(
        value = fullName,
        onValueChange = onFullName,
        label = stringResource(Res.string.visitor_name),
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        isError = nameError != null,
        errorMessage = nameError,
        enabled = !isLoading,
    )

    Spacer(modifier = Modifier.height(16.dp))

    AppTextField(
        enabled = !isLoading,
        maxLength = 11,
        value = phoneNumber,
        onValueChange = onPhoneNumber,
        label = stringResource(Res.string.phone),
        isError = phoneError != null,
        errorMessage = phoneError,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Phone
    )
}

@Composable
fun HandleEvents(
    events: Flow<CreateVisitorEvent>,
    onContinue: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    events.collectWithLifecycleAware {
        when (it) {
            is CreateVisitorEvent.VisitorCreated -> {
                scope.launch {
                    onContinue(it.visitorId)
                }
            }

            CreateVisitorEvent.BackPressed -> {
                scope.launch {
                    onNavigateBack()
                }
            }

            is CreateVisitorEvent.VisitorUpdated ->  {
                scope.launch {
                    onNavigateBack()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewCreateVisitorScreen() {
    AppTheme {
        CreateVisitorScreen(
            uiState = CreateVisitorState(),
            onIntent = {},
            fullName = "",
            phoneNumber = "",
            onFullName = {},
            onPhoneNumber = {},
            nameError = null,
            phoneError = null,
            onNameErrorUpdate = {},
            onPhoneErrorUpdate = {}
        )
    }
}
