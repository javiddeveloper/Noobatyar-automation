package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware

@Composable
fun ProfileAvatar(
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    userViewModel: UserViewModel = koinViewModel()
) {
    val userState by userViewModel.uiState.collectAsState()
    val businessState by xyz.sattar.javid.proqueue.core.state.BusinessStateHolder.selectedBusiness.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }

    userViewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            UserEvent.LogoutSuccess -> onNavigateToLogin()
        }
    }

    val ring = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(40.dp)
            .shadow(6.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            .border(width = 1.5.dp, brush = ring, shape = CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { showProfileSheet = true },
        contentAlignment = Alignment.Center
    ) {
        val displayChar = userState.userName?.firstOrNull()?.uppercaseChar()?.toString()
            ?: businessState?.title?.firstOrNull()?.uppercaseChar()?.toString()
            ?: "?"

        Text(
            text = displayChar,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
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
