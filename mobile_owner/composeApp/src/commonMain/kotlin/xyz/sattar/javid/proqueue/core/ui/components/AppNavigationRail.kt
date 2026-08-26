package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.sattar.javid.proqueue.core.navigation.MainTab

/**
 * Desktop/tablet nav rail for [xyz.sattar.javid.proqueue.core.navigation.navHost.MainNavHost]'s
 * Medium/Expanded branch. Kept separate from `BottomNavigationBar` (the phone
 * bar) because the two share nothing visually — the phone bar is a custom
 * cutout shape, this is a plain Material rail with a brand header.
 *
 * RTL: the caller places this as the first child of a `Row` under the app's
 * forced RTL layout direction, which lands it on the right edge with the
 * content pane to its left. The hairline divider below is drawn on this
 * composable's own left edge for that reason — it always ends up on the
 * boundary with the content, regardless of which physical screen edge that
 * turns out to be.
 */
@Composable
fun AppNavigationRail(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerColor = MaterialTheme.colorScheme.outline

    // verticalScroll (rather than a fixed/weighted layout) is what actually
    // guarantees nothing clips: however short the viewport gets, the rail
    // scrolls instead of truncating the header or the last tab's label.
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(104.dp)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NoobatyarMark(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 28.dp)
                .size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedTab == tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.iconSelected else tab.iconUnSelected,
                        contentDescription = stringResource(tab.title)
                    )
                },
                label = { Text(stringResource(tab.title)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            if (index != tabs.lastIndex) {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
