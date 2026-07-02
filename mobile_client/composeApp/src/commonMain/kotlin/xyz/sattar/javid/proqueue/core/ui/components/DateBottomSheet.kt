package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.confirm
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateBottomSheet(
    initialDate: Long, // Epoch Millis
    onDateSelected: (Long) -> Unit, // Returns Epoch Millis
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val scope = rememberCoroutineScope()
    
    // Convert initialDate to Persian
    val initialPersian = remember(initialDate) { DateTimeUtils.getJalaliDateParts(initialDate) }
    
    var year by remember { mutableStateOf(initialPersian.year) }
    var month by remember { mutableStateOf(initialPersian.month) }
    var day by remember { mutableStateOf(initialPersian.dayOfMonth) }

    val persianMonths = remember {
        listOf(
            "فروردین /01", "اردیبهشت /02", "خرداد /03", "تیر /04",
            "مرداد /05", "شهریور /06", "مهر /07", "آبان /08",
            "آذر /09", "دی /10", "بهمن /11", "اسفند /12"
        )
    }

    ModalBottomSheet(
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
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                }
            ) {
                Text(stringResource(Res.string.confirm))
            }
        }
    }
}
