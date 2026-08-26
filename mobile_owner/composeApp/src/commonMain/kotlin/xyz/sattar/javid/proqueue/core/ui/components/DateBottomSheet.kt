package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils

@Composable
fun DateBottomSheet(
    initialDate: Long, // Epoch Millis
    onDateSelected: (Long) -> Unit, // Returns Epoch Millis
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val scope = rememberCoroutineScope()
    val windowSize = LocalWindowSize.current

    // Convert initialDate to Persian
    val initialPersian = remember(initialDate) { DateTimeUtils.getJalaliDateParts(initialDate) }
    
    var year by remember { mutableStateOf(initialPersian.year) }
    var month by remember { mutableStateOf(initialPersian.month) }
    var day by remember { mutableStateOf(initialPersian.dayOfMonth) }

    val month_1 = stringResource(Res.string.month_1)
    val month_2 = stringResource(Res.string.month_2)
    val month_3 = stringResource(Res.string.month_3)
    val month_4 = stringResource(Res.string.month_4)
    val month_5 = stringResource(Res.string.month_5)
    val month_6 = stringResource(Res.string.month_6)
    val month_7 = stringResource(Res.string.month_7)
    val month_8 = stringResource(Res.string.month_8)
    val month_9 = stringResource(Res.string.month_9)
    val month_10 = stringResource(Res.string.month_10)
    val month_11 = stringResource(Res.string.month_11)
    val month_12 = stringResource(Res.string.month_12)

    val persianMonths = remember(
        month_1, month_2, month_3, month_4, month_5, month_6,
        month_7, month_8, month_9, month_10, month_11, month_12
    ) {
        listOf(
            month_1, month_2, month_3, month_4,
            month_5, month_6, month_7, month_8,
            month_9, month_10, month_11, month_12
        )
    }

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Year
                 NumberPicker(
                    range = 1300..1499,
                    initialValue = year,
                    onValueChange = { year = it },
                    modifier = Modifier.width(80.dp),
                    itemFormatter = { it.toString() }
                )
                
                Spacer(modifier = Modifier.width(16.dp))

                // Month
                NumberPicker(
                    range = 1..12,
                    initialValue = month,
                    onValueChange = { month = it },
                    modifier = Modifier.width(140.dp), // Wider for text
                    itemFormatter = { index -> 
                        if(index in 1..12) persianMonths[index - 1] else "" 
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))
                
                // Day
                NumberPicker(
                    range = 1..31,
                    initialValue = day,
                    onValueChange = { day = it },
                    modifier = Modifier.width(80.dp),
                    itemFormatter = { it.toString().padStart(2, '0') }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val millis = DateTimeUtils.jalaliToGregorian(year, month, day)
                    onDateSelected(millis)
                    // sheetState.hide() only makes sense when we're actually inside a
                    // ModalBottomSheet (Compact) — on the AdaptiveSheet dialog path it
                    // has no anchors attached to anything, so we dismiss directly there.
                    if (windowSize == WindowSize.Compact) {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) onDismiss()
                        }
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(Res.string.confirm))
            }
        }
    }
}
