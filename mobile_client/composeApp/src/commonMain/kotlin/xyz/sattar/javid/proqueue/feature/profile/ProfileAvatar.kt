package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAvatar(
    onNavigateToLogin: () -> Unit,
    userViewModel: UserViewModel = koinViewModel()
) {
    val userState by userViewModel.uiState.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }

    userViewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            UserEvent.LogoutSuccess -> onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { showProfileSheet = true },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = userState.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    if (showProfileSheet) {
        ProfileBottomSheet(
            userName = userState.userName,
            userEmail = userState.userNumber,
            subscription = userState.subscription,
            onDismiss = { showProfileSheet = false },
            onLogout = {
                showProfileSheet = false
                userViewModel.sendIntent(UserIntent.Logout)
            }
        )
    }
}
