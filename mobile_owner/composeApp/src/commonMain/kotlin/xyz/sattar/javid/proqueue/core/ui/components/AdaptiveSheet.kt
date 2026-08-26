package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize

/**
 * Picker/list chrome shared by [DateBottomSheet], [TimeBottomSheet],
 * [ServiceCatalogBottomSheet], [ServiceDurationBottomSheet] and
 * [AppointmentsListBottomSheet] (see docs/OWNER_WEB_PLAN.md section ۸,
 * item 4): a bottom sheet on a phone, a centered dialog on a wider screen —
 * a sheet sliding up from the bottom of a 1920px monitor reads as a mobile
 * afterthought, not as this app's own surface.
 *
 * [Compact] renders the exact same [ModalBottomSheet] call these five sheets
 * used directly before this component existed, so phone layout is untouched.
 * [Medium]/[Expanded] render a plain centered [Surface] in a [Dialog]
 * instead — note this means [sheetState] (its drag-to-dismiss / hide()
 * machinery) isn't wired to anything on that path, since it's never attached
 * to a real ModalBottomSheet. Callers that use `sheetState.hide()` in their
 * own confirm handlers must guard that call behind
 * `LocalWindowSize.current == WindowSize.Compact` and call [onDismissRequest]
 * directly otherwise.
 */
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    when (LocalWindowSize.current) {
        WindowSize.Compact -> ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            content = content
        )

        WindowSize.Medium, WindowSize.Expanded -> Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                modifier = Modifier.widthIn(max = 480.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                tonalElevation = 6.dp
            ) {
                Column(content = content)
            }
        }
    }
}
