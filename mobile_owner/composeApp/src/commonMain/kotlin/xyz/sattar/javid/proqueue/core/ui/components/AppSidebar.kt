package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.advanced_settings_title
import proqueue.composeapp.generated.resources.appName
import proqueue.composeapp.generated.resources.logout_label
import proqueue.composeapp.generated.resources.messages_auto_item
import proqueue.composeapp.generated.resources.notice_section_title
import proqueue.composeapp.generated.resources.reminders_notifications_item
import proqueue.composeapp.generated.resources.settings_sidebar_item
import proqueue.composeapp.generated.resources.sms_report_title
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.feature.profile.UserEvent
import xyz.sattar.javid.proqueue.feature.profile.UserIntent
import xyz.sattar.javid.proqueue.feature.profile.UserViewModel
import xyz.sattar.javid.proqueue.feature.settings.SettingsEvent
import xyz.sattar.javid.proqueue.feature.settings.SettingsIntent
import xyz.sattar.javid.proqueue.feature.settings.SettingsViewModel

/**
 * Desktop/tablet sidebar for [xyz.sattar.javid.proqueue.core.navigation.navHost.MainNavHost]'s
 * Medium/Expanded branch: the icon-only rail plus every action that used to
 * live only inside [xyz.sattar.javid.proqueue.feature.settings.SettingsScreen]'s
 * card grid (docs/OWNER_WEB_PLAN.md — an owner on a wide screen shouldn't have
 * to open Settings just to send an emergency notice or switch business).
 * Settings itself is unchanged and still reachable both as a tab here and as
 * its own full page — this sidebar is a shortcut layer on top, not a
 * replacement.
 *
 * Phone layout ([BottomNavigationBar]) is untouched; this is Medium/Expanded
 * only, reached from the same call site the plain rail used to occupy.
 *
 * Business management (change/edit/delete business) deliberately stays out
 * of this sidebar — those are rare, higher-stakes actions and belong only on
 * the Settings page, not one click away in a list of everyday shortcuts.
 *
 * Owns its own [SettingsViewModel]/[UserViewModel] instances rather than
 * sharing the ones [SettingsScreen] creates — same pattern already used by
 * [xyz.sattar.javid.proqueue.feature.profile.ProfileAvatar] for the exact
 * same reason: both instances read through to the same repository-backed
 * state, so they stay in sync without needing to be the same object, and
 * this sidebar can render (and act) independently of whether the Settings
 * screen is even in the back stack.
 */
@Composable
fun AppSidebar(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onNavigateToAdvancedSettings: (Long) -> Unit,
    onNavigateToEmergencyNotice: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSmsReport: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel()
) {
    val dividerColor = MaterialTheme.colorScheme.outline
    val settingsState by settingsViewModel.uiState.collectAsState()

    settingsViewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            SettingsEvent.NavigateToNotifications -> onNavigateToNotifications()
            SettingsEvent.NavigateToMessages -> onNavigateToMessages()
            SettingsEvent.NavigateToSmsReport -> onNavigateToSmsReport()
            SettingsEvent.NavigateToEmergencyNotice -> onNavigateToEmergencyNotice()
            else -> Unit
        }
    }

    userViewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            UserEvent.LogoutSuccess -> onNavigateToLogin()
        }
    }

    // verticalScroll (rather than a fixed/weighted layout) is what actually
    // guarantees nothing clips: however short the viewport gets, the sidebar
    // scrolls instead of truncating the header or the last row — same
    // reasoning the old AppNavigationRail used.
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
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
            .padding(vertical = 20.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoobatyarMark(
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.appName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                // Explicit: the sidebar paints its background with a modifier
                // rather than a Surface, so nothing provides LocalContentColor
                // and an unset color falls back to black on this dark panel.
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Ordered deliberately rather than by iterating `tabs`: that list's
        // order belongs to BottomNavigationBar, which needs Home in the
        // middle for its cutout notch. Here Home leads because it's the
        // landing screen, and the Settings tab is pulled out of this group
        // entirely — it sits down by logout with the other settings rows.
        listOf(MainTab.Home, MainTab.LastVisitors).filter { it in tabs }.forEach { tab ->
            val isSelected = selectedTab == tab
            SidebarItem(
                icon = if (isSelected) tab.iconSelected else tab.iconUnSelected,
                label = stringResource(tab.title),
                selected = isSelected,
                onClick = { onTabSelected(tab) }
            )
        }

        SidebarDivider()

        // Upsell shortcut — gets its own gradient pill rather than a plain
        // SidebarItem so it reads as the same "premium" surface as
        // AdvancedSettingsPromoCard on the Settings page, just condensed for
        // a single row instead of a full banner.
        settingsState.currentBusiness?.let { business ->
            AdvancedSettingsSidebarItem(
                onClick = { onNavigateToAdvancedSettings(business.id) }
            )
            SidebarDivider()
        }

        SidebarItem(
            icon = Icons.Rounded.Campaign,
            label = stringResource(Res.string.notice_section_title),
            onClick = { settingsViewModel.sendIntent(SettingsIntent.OnEmergencyNoticeClick) }
        )
        SidebarItem(
            icon = Icons.Rounded.Message,
            label = stringResource(Res.string.messages_auto_item),
            onClick = { settingsViewModel.sendIntent(SettingsIntent.OnMessagesClick) }
        )
        SidebarItem(
            icon = Icons.Rounded.Sms,
            label = stringResource(Res.string.sms_report_title),
            onClick = { settingsViewModel.sendIntent(SettingsIntent.OnSmsReportClick) }
        )
        SidebarItem(
            icon = Icons.Rounded.Notifications,
            label = stringResource(Res.string.reminders_notifications_item),
            onClick = { settingsViewModel.sendIntent(SettingsIntent.OnNotificationsClick) }
        )

        SidebarDivider()

        // The Settings tab, sitting with the settings rows rather than up
        // with Home/Visitors. Theme and About used to be their own rows here
        // and now live inside this page instead, so this is the way to them.
        if (MainTab.Settings in tabs) {
            val isSelected = selectedTab == MainTab.Settings
            SidebarItem(
                // Gear rather than MainTab.Settings' own icon, which is the
                // hamburger the phone bar wants — same reason this row uses
                // settings_sidebar_item for its label: here it reads as
                // "settings" among settings rows, not as a menu affordance.
                icon = if (isSelected) Icons.Rounded.Settings else Icons.Outlined.Settings,
                label = stringResource(Res.string.settings_sidebar_item),
                selected = isSelected,
                onClick = { onTabSelected(MainTab.Settings) }
            )
        }

        SidebarItem(
            icon = Icons.Rounded.Logout,
            label = stringResource(Res.string.logout_label),
            tint = MaterialTheme.colorScheme.error,
            onClick = { userViewModel.sendIntent(UserIntent.Logout) }
        )
    }
}

@Composable
private fun SidebarDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun AdvancedSettingsSidebarItem(onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(Res.string.advanced_settings_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
    val contentTint = if (selected) MaterialTheme.colorScheme.primary else tint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentTint
        )
    }
}
