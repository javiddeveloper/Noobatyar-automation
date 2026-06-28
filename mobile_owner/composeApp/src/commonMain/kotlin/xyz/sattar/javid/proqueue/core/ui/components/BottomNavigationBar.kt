package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import xyz.sattar.javid.proqueue.feature.profile.UserViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState

class BottomBarShape(private val cutoutRadius: androidx.compose.ui.unit.Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            val width = size.width
            val height = size.height
            val centerX = width / 2
            
            val r = with(density) { cutoutRadius.toPx() }
            val curveWidth = r * 2.5f

            lineTo(centerX - curveWidth, 0f)

            cubicTo(
                centerX - r * 1.5f, 0f,
                centerX - r * 1.2f, r,
                centerX, r
            )
            
            cubicTo(
                centerX + r * 1.2f, r,
                centerX + r * 1.5f, 0f,
                centerX + curveWidth, 0f
            )

            lineTo(width, 0f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun BottomNavigationBar(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main Navigation Bar Background
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp + navigationBarsPadding),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = BottomBarShape(48.dp),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = navigationBarsPadding)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    
                    if (tab is MainTab.Home) {
                        Spacer(modifier = Modifier.width(90.dp))
                    } else {
                        StandardNavigationItem(
                            isSelected = isSelected,
                            tab = tab,
                            onClick = { onTabSelected(tab) }
                        )
                    }
                }
            }
        }

        // Floating Home Button
        tabs.find { it is MainTab.Home }?.let { homeTab ->
            HomeNavigationItem(
                isSelected = selectedTab is MainTab.Home,
                tab = homeTab,
                modifier = Modifier
                    .padding(bottom = 32.dp + navigationBarsPadding)
                    .size(64.dp),
                onClick = { onTabSelected(homeTab) }
            )
        }
    }
}

@Composable
private fun StandardNavigationItem(
    isSelected: Boolean,
    tab: MainTab,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(300)
    )

    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (tab is MainTab.Settings) {
            val userViewModel: UserViewModel = koinViewModel()
            val userState by userViewModel.uiState.collectAsState()
            
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userState.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Icon(
                imageVector = if (isSelected) tab.iconSelected else tab.iconUnSelected,
                contentDescription = stringResource(tab.title),
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = stringResource(tab.title),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun HomeNavigationItem(
    isSelected: Boolean,
    tab: MainTab,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateDpAsState(
        targetValue = if (isSelected) 64.dp else 58.dp,
        animationSpec = tween(400)
    )

    val orangeGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFF9800), Color(0xFFFF5722))
    )

    Box(
        modifier = modifier
            .size(scale)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = Color(0xFFFF5722)
            )
            .clip(CircleShape)
            .background(orangeGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = tab.iconSelected,
            contentDescription = stringResource(tab.title),
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}
