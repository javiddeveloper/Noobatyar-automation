package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.home_menu_item
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.LocalHazeState
import xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MainTopAppBar(
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {}
) {
    val selectedBusiness by BusinessStateHolder.selectedBusiness.collectAsState()
    val displayTitle = title ?: selectedBusiness?.title ?: stringResource(Res.string.home_menu_item)
    val hazeState = LocalHazeState.current
    val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)

    TopAppBar(
        windowInsets = WindowInsets.statusBars,
        modifier = Modifier.hazeEffect(
            state = hazeState,
            style = HazeMaterials.regular(glassTint)
        ),
        title = {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            actions()
            ProfileAvatar(
                onNavigateToLogin = onNavigateToLogin,
                onChangeBusiness = onChangeBusiness
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )
}
