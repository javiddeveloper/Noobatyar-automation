package xyz.sattar.javid.proqueue.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.accept
import proqueue.composeapp.generated.resources.notice_section_title
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.EmergencyNoticeSection

/**
 * Own destination rather than an inline card in Settings — a switch + text
 * field sitting between "change business" and "auto messages" broke the flow
 * of an otherwise uniform list of nav rows.
 */
@Composable
fun EmergencyNoticeScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.notice_section_title),
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
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            EmergencyNoticeSection(
                enabled = uiState.noticeEnabled,
                message = uiState.noticeMessage,
                onEnabledChange = { viewModel.sendIntent(SettingsIntent.UpdateNoticeEnabled(it)) },
                onMessageChange = { viewModel.sendIntent(SettingsIntent.UpdateNoticeMessage(it)) },
                isEditable = !uiState.isSavingNotice
            )
            if (uiState.noticeDirty) {
                AppButton(
                    text = stringResource(Res.string.accept),
                    onClick = { viewModel.sendIntent(SettingsIntent.SaveNotice) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    isLoading = uiState.isSavingNotice
                )
            }
        }
    }
}
