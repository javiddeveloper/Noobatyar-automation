package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.home_menu_item
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    onNavigateToLogin: () -> Unit
) {
    val selectedBusiness by BusinessStateHolder.selectedBusiness.collectAsState()
    val displayTitle = title ?: selectedBusiness?.title ?: stringResource(Res.string.home_menu_item)

    TopAppBar(
        title = {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            actions()
            ProfileAvatar(onNavigateToLogin = onNavigateToLogin)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
