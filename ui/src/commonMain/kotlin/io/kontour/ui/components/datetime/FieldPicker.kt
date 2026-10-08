package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme

/**
 * Where a [DateField], [TimeField] or [DateTimeField] opens its picker: a sheet
 * from the foot of a phone, and a dialog in the middle of anything wider.
 *
 * ```
 * var date by remember { mutableStateOf<LocalDate?>(null) }
 * var picking by remember { mutableStateOf(false) }
 *
 * DateField(value = date, onClick = { picking = true }, label = "Post date")
 *
 * FieldPicker(visible = picking, onDismissRequest = { picking = false }, title = "Post date") {
 *     DatePicker(value = date, onValueChange = { date = it; picking = false })
 * }
 * ```
 *
 * One presentation for all three fields, so a form with a date and a time on
 * it opens both the same way: a date in a dialog and a time in a sheet, side by
 * side, read as two different kinds of thing. A sheet on a compact window —
 * under 600dp wide, where the thumb is at the foot of the screen — with the
 * title in its header and a close button; a dialog wider, where a sheet across
 * a tablet is a long way from the field that opened it, with the title as its
 * heading and Done.
 *
 * The picker sets the value as it is used, so there is no Cancel: the close,
 * Done, a tap outside or back puts it away with the value as it is. A picker
 * that is done at one tap — a date — can close itself, as above.
 *
 * @param title What is being picked: the field's label, usually.
 * @param content The picker, centred.
 */
@Composable
fun FieldPicker(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWindowSizeClass.current.width == WindowWidthClass.Compact) {
        ModalBottomSheet(visible = visible, onDismissRequest = onDismissRequest, modifier = modifier) { padding ->
            SheetHeader { +title }
            Column(
                Modifier.fillMaxWidth().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    } else {
        Dialog(
            visible = visible,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = { +title },
            actions = { confirm(Theme.strings.done, onClick = onDismissRequest) },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}
