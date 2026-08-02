package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.service_duration_sheet_hint
import proqueue.composeapp.generated.resources.service_duration_sheet_title
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/** Shortest slot the business can sell, and the longest one worth listing. */
private const val MIN_DURATION_MINUTES = 10
private const val MAX_DURATION_MINUTES = 180
private const val DURATION_STEP_MINUTES = 5

/** 10, 15, 20 … 180 — every option the sheet offers. */
val serviceDurationOptions: List<Int> =
    (MIN_DURATION_MINUTES..MAX_DURATION_MINUTES step DURATION_STEP_MINUTES).toList()

/**
 * Human label for a duration in minutes: «۴۵ دقیقه», «۱ ساعت»,
 * «۱ ساعت و ۳۰ دقیقه». Persian digits, because this is display-only text.
 */
fun formatServiceDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    val hourPart = "$hours ساعت".toPersianDigits()
    val minutePart = "$rest دقیقه".toPersianDigits()
    return when {
        hours == 0 -> minutePart
        rest == 0 -> hourPart
        else -> "$hourPart و $minutePart"
    }
}

/**
 * Picker for the business's default/minimum service duration. A typed field let
 * owners enter values the scheduler can't honour (0, 7, 400); a fixed list of
 * 5-minute steps can only produce slots the calendar actually lays out.
 */
@Composable
fun ServiceDurationBottomSheet(
    selectedMinutes: Int?,
    onDurationSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    val listState = rememberLazyListState()

    // Open on the current value rather than at 10 minutes — otherwise a business
    // set to 90 minutes has to scroll half the list to see its own setting.
    LaunchedEffect(selectedMinutes) {
        val index = serviceDurationOptions.indexOf(selectedMinutes)
        if (index > 0) listState.scrollToItem(index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(Res.string.service_duration_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.service_duration_sheet_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(serviceDurationOptions, key = { it }) { minutes ->
                    val isSelected = minutes == selectedMinutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onDurationSelected(minutes) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatServiceDuration(minutes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
