package io.kontour.ui.components.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.LocalFileChooser
import io.kontour.ui.files.PickedFile
import io.kontour.ui.files.rememberFilePicker
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import kotlin.math.round

/**
 * A field that holds files: a frame like any other field's, which opens the
 * platform's picker when pressed and says what it holds.
 *
 * ```kotlin
 * var files by remember { mutableStateOf(emptyList<PickedFile>()) }
 * FileField(
 *     files = files,
 *     onFilesChange = { files = it },
 *     label = "Timetable",
 *     types = FileTypes.of(".pdf", ".csv"),
 * )
 * ```
 *
 * One file reads as its name and size, with a button to clear it. Several read
 * as chips, each with its own remove. A field that takes [multiple] adds what is
 * picked to what it holds, leaving out a file it already has; one that takes a
 * single file replaces it.
 *
 * The picker is [rememberFilePicker]'s — the platform's own, unless
 * [LocalFileChooser] provides another — and what it hands back is checked
 * against [types] before the field sees it. The field holds [PickedFile]s and
 * reads none of them: reading, and uploading, are the caller's, when it is
 * ready.
 *
 * A screen reader hears a button named by [label], and what it holds: "Timetable,
 * timetable.pdf, 2.4 MB, button", or "No file chosen".
 *
 * @param placeholder What the empty field says. "Choose file…", or "Choose
 *   files…" for [multiple], by default.
 * @param types What the picker offers. See [FileTypes].
 * @param sizeLabel How a file's size is written. [FileFieldDefaults.sizeLabel]
 *   counts in thousands, as the platforms' file browsers do.
 */
@Composable
fun FileField(
    files: List<PickedFile>,
    onFilesChange: (List<PickedFile>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    types: FileTypes = FileTypes.Any,
    multiple: Boolean = false,
    sizeLabel: (bytes: Long) -> String = FileFieldDefaults.sizeLabel(),
    colours: TextFieldColours = TextFieldDefaults.colours(),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val strings = Theme.strings
    val picker = rememberFilePicker { picked ->
        if (picked.isEmpty()) return@rememberFilePicker
        onFilesChange(
            if (multiple) {
                files + picked.filter { new -> files.none { it.name == new.name && it.size == new.size } }
            } else {
                picked.take(1)
            },
        )
    }
    val single = files.singleOrNull()?.takeIf { !multiple }

    FieldScaffold(
        modifier = modifier,
        enabled = enabled,
        focused = focused,
        colours = colours,
        metrics = metrics,
        shape = Theme.shapes.field,
        label = label,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        trailing = {
            if (single != null) {
                IconButton(
                    icon = SystemIcons.Close,
                    contentDescription = strings.removeFile(single.name),
                    onClick = { onFilesChange(emptyList()) },
                    enabled = enabled,
                    size = ButtonSize.XSmall,
                )
            } else {
                Icon(
                    imageVector = SystemIcons.Attach,
                    contentDescription = null,
                    tint = if (enabled) colours.label else colours.contentDisabled,
                    size = Theme.sizing.iconMedium,
                )
            }
        },
        frameModifier = Modifier
            .semantics {
                role = Role.Button
                // Names the control; what it holds is the merged text, or this.
                if (label != null) contentDescription = label
                if (files.isEmpty()) stateDescription = strings.noFileChosen
                if (!enabled) disabled()
            }
            .pointerCursor(enabled = enabled)
            .clickable(
                interactionSource = interactions,
                // A whole field flinching is too much movement for a control
                // this large — as a `Select`'s does not.
                indication = kontourIndication(Theme.shapes.field, pressScale = 1f),
                enabled = enabled,
                // Straight from the press: a browser opens its picker only
                // while the press is being handled.
                onClick = { picker.launch(types, multiple) },
            ),
    ) {
        val contentColour = if (enabled) colours.content else colours.contentDisabled
        when {
            files.isEmpty() -> Text(
                text = placeholder ?: if (multiple) strings.chooseFiles else strings.chooseFile,
                // Said once, by the frame's state, rather than as a value.
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                style = Theme.typography.bodyMedium,
                colour = if (enabled) colours.placeholder else colours.contentDisabled,
                maxLines = 1,
            )
            files.size == 1 -> Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val file = files.single()
                Text(
                    text = file.name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = Theme.typography.bodyMedium,
                    colour = contentColour,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                val size = file.size
                if (size != null) {
                    Text(
                        text = sizeLabel(size),
                        style = Theme.typography.bodyMedium,
                        colour = if (enabled) colours.label else colours.contentDisabled,
                        maxLines = 1,
                    )
                }
            }
            else -> FlowRow(
                modifier = Modifier.weight(1f).padding(vertical = Theme.spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                for (file in files) {
                    InputChip(
                        onRemove = { onFilesChange(files - file) },
                        removeLabel = strings.removeFile(file.name),
                        enabled = enabled,
                    ) { +file.name }
                }
            }
        }
    }
}

/** What [FileField] takes by default. */
object FileFieldDefaults {
    /**
     * A file's size as the reader's platform would write it: "512 bytes",
     * "2.4 MB", "24 MB" — counted in thousands, as Finder, Files and Android's
     * storage settings all now count, in the reader's digits and words.
     */
    @Composable
    fun sizeLabel(): (bytes: Long) -> String {
        val strings = Theme.strings
        val formats = Theme.formats
        return { bytes ->
            val reading = readSize(bytes)
            when (reading.unit) {
                SizeUnit.Bytes -> strings.fileBytes(formats.quantity(reading.amount.toLong()))
                SizeUnit.Kilobytes -> strings.fileSize(formats.decimal(reading.amount, reading.fractionDigits), strings.kilobytes)
                SizeUnit.Megabytes -> strings.fileSize(formats.decimal(reading.amount, reading.fractionDigits), strings.megabytes)
                SizeUnit.Gigabytes -> strings.fileSize(formats.decimal(reading.amount, reading.fractionDigits), strings.gigabytes)
            }
        }
    }
}

internal enum class SizeUnit { Bytes, Kilobytes, Megabytes, Gigabytes }

/** A size as it is written: how much, of what, to how many places. */
internal data class SizeReading(val amount: Double, val unit: SizeUnit, val fractionDigits: Int)

/**
 * [bytes] in the largest unit there is at least one of, counted in thousands.
 * One place under ten and none from ten up — "2.4 MB", "24 MB" — and none where
 * the place would be a nought, so "2 MB" rather than "2.0 MB". A reading that
 * rounds up to a thousand is given in the next unit, so nothing is "1,000 KB".
 */
internal fun readSize(bytes: Long): SizeReading {
    if (bytes < 1000) return SizeReading(bytes.coerceAtLeast(0).toDouble(), SizeUnit.Bytes, 0)
    var amount = bytes / 1000.0
    var unit = SizeUnit.Kilobytes
    while (true) {
        val tenths = round(amount * 10) / 10
        val digits = if (tenths < 10 && tenths != round(tenths)) 1 else 0
        val shown = if (digits == 1) tenths else round(amount)
        if (shown >= 1000 && unit != SizeUnit.Gigabytes) {
            amount /= 1000
            unit = SizeUnit.entries[unit.ordinal + 1]
            continue
        }
        return SizeReading(shown, unit, digits)
    }
}
