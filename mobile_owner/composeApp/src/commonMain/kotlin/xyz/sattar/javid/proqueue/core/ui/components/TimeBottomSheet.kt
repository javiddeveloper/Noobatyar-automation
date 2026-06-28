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
import androidx.compose.material3.MaterialTheme
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
import proqueue.composeapp.generated.resources.hour_label
import proqueue.composeapp.generated.resources.minute_label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeBottomSheet(
    initialTime: String, // "HH:mm"
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val scope = rememberCoroutineScope()
    val parts = initialTime.split(":")
    var hour by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 12) }
    var minute by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }

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
                // Minute Picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.minute_label), style = MaterialTheme.typography.labelMedium)
                    NumberPicker(
                        range = 0..59,
                        initialValue = minute,
                        onValueChange = { minute = it },
                        modifier = Modifier.width(80.dp),
                        itemFormatter = { it.toString().padStart(2, '0') }
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Hour Picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.hour_label), style = MaterialTheme.typography.labelMedium)
                    NumberPicker(
                        range = 0..23,
                        initialValue = hour,
                        onValueChange = { hour = it },
                        modifier = Modifier.width(80.dp),
                        itemFormatter = { it.toString().padStart(2, '0') }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val formattedTime = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                    onTimeSelected(formattedTime)
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
