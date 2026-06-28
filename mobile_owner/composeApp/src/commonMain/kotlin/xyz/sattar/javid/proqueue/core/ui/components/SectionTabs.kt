package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        divider = { Divider(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        labels.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                modifier = Modifier.height(58.dp),
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = if (selectedIndex == index) 18.sp else 16.sp,
                        color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            )
        }
    }
}

