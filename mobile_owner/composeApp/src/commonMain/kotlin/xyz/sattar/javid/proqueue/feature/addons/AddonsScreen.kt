package xyz.sattar.javid.proqueue.feature.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.AddOnPackDto
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


/**
 * One-off add-on packs — SMS credit top-ups or a temporary feature unlock —
 * purchased outside the plan/subscription ladder (backend: accounting/addons/).
 */
@Composable
fun AddonsScreen(
    viewModel: AddonsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPayment: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(AddonsIntent.Load)
    }

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            is AddonsEvent.OpenUrl -> onNavigateToPayment(event.url)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("بسته‌های افزودنی", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isLoading && uiState.packs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.packs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "در حال حاضر بسته‌ای موجود نیست",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val smsPacks = uiState.packs.filter { it.kind == "sms_pack" }
                val appointmentPacks = uiState.packs.filter { it.kind == "appointment_pack" }
                val otherPacks = uiState.packs.filter { it.kind != "sms_pack" && it.kind != "appointment_pack" }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Text(
                            "بدون تغییر پلن، اعتبار پیامک یا نوبت اضافه کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (smsPacks.isNotEmpty()) {
                        item { SectionHeader("بسته‌های پیامک") }
                        items(smsPacks, key = { it.id }) { pack ->
                            AddOnPackCard(
                                pack = pack,
                                isPurchasing = uiState.purchasingPackId == pack.id,
                                enabled = uiState.purchasingPackId == null,
                                onBuy = { viewModel.sendIntent(AddonsIntent.Buy(pack.id)) }
                            )
                        }
                    }

                    if (appointmentPacks.isNotEmpty()) {
                        item { SectionHeader("بسته‌های نوبت") }
                        items(appointmentPacks, key = { it.id }) { pack ->
                            AddOnPackCard(
                                pack = pack,
                                isPurchasing = uiState.purchasingPackId == pack.id,
                                enabled = uiState.purchasingPackId == null,
                                onBuy = { viewModel.sendIntent(AddonsIntent.Buy(pack.id)) }
                            )
                        }
                    }

                    items(otherPacks, key = { it.id }) { pack ->
                        AddOnPackCard(
                            pack = pack,
                            isPurchasing = uiState.purchasingPackId == pack.id,
                            enabled = uiState.purchasingPackId == null,
                            onBuy = { viewModel.sendIntent(AddonsIntent.Buy(pack.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun AddOnPackCard(
    pack: AddOnPackDto,
    isPurchasing: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    val icon = when (pack.kind) {
        "sms_pack" -> Icons.Rounded.Sms
        "appointment_pack" -> Icons.Rounded.Event
        else -> Icons.Rounded.Bolt
    }
    val subtitle = when (pack.kind) {
        "sms_pack" -> "${pack.smsAmount} پیامک — بدون انقضا"
        "appointment_pack" -> "${pack.appointmentAmount} نوبت — بدون انقضا"
        else -> "فعال به مدت ${pack.durationDays} روز"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pack.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onBuy,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(pack.priceDisplay, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
