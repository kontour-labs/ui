package io.kontour.ui.components.text

import androidx.compose.foundation.clickable
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.motion.ScrollDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.dragdrop.ExternalDrops
import io.kontour.ui.dragdrop.OutsideOnly
import io.kontour.ui.dragdrop.dropTarget
import io.kontour.ui.dragdrop.rememberDropTargetState
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
 * Each file is a chip with its name and size, in one row that scrolls sideways
 * and fades at the edge it scrolls past, so the field is a field's height
 * however many it holds. A field that takes [multiple] adds what is picked to
 * what it holds, leaving out a file it already has, and each chip has its own
 * remove; one that takes a single file replaces it, and its clear button is
 * where the attach glyph was.
 *
 * The picker is [rememberFilePicker]'s — the platform's own, unless
 * [LocalFileChooser] provides another — and what it hands back is checked
 * against [types] before the field sees it. The field holds [PickedFile]s and
 * reads none of them: reading, and uploading, are the caller's, when it is
 * ready.
 *
 * Files dragged in from outside the app — the desktop, the Files app, another
 * window — can be dropped on it as well, where the platform allows, and are
 * taken as a pick is. While one is over it the frame lights as it does for
 * focus, and the empty field says "Drop file here".
 *
 * A screen reader hears a button named by [label], and what it holds: "Timetable,
 * timetable.pdf, 2.4 MB, button", or "No file chosen".
 *
 * @param placeholder What the empty field says. "Choose file…", or "Choose
 *   files…" for [multiple], by default.
 * @param types What the picker offers. See [FileTypes].
 * @param sizeLabel How a file's size is written. [FileFieldDefaults.sizeLabel]
 *   counts in thousands, as the platforms' file browsers do.
 * @param acceptsDrops Whether files dragged in from outside the app can be
 *   dropped on it. Only files of [types] are offered it.
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
    acceptsDrops: Boolean = true,
    colours: TextFieldColours = TextFieldDefaults.colours(),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val strings = Theme.strings
    // A pick and a drop are taken the same way.
    val take: (List<PickedFile>) -> Unit = { picked ->
        if (picked.isNotEmpty()) {
            onFilesChange(
                if (multiple) {
                    files + picked.filter { new -> files.none { it.name == new.name && it.size == new.size } }
                } else {
                    picked.take(1)
                },
            )
        }
    }
    val picker = rememberFilePicker(take)
    val drops = rememberDropTargetState(
        OutsideOnly,
        label = label ?: strings.chooseFile,
        onDrop = {},
        external = if (acceptsDrops) ExternalDrops.files(types) { dropped, _ -> take(dropped) } else null,
    )
    val dropOver = enabled && drops.isOver
    val single = files.singleOrNull()?.takeIf { !multiple }

    FieldScaffold(
        modifier = modifier.dropTarget(drops, enabled = enabled && acceptsDrops),
        enabled = enabled,
        focused = focused || dropOver,
        colours = colours,
        metrics = metrics,
        shape = Theme.shapes.field,
        label = label,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        trailing = {
            // The attach glyph's box, whichever is in it: the clear button is
            // laid out round the same centre, its target spilling into the
            // padding, rather than widening the slot and moving its glyph in
            // from where the attach glyph was.
            Box(Modifier.size(Theme.sizing.iconMedium), contentAlignment = Alignment.Center) {
                if (single != null) {
                    IconButton(
                        icon = SystemIcons.Close,
                        contentDescription = strings.removeFile(single.name),
                        onClick = { onFilesChange(emptyList()) },
                        enabled = enabled,
                        size = ButtonSize.XSmall,
                        modifier = Modifier.wrapContentSize(unbounded = true),
                    )
                } else {
                    Icon(
                        imageVector = SystemIcons.Attach,
                        contentDescription = null,
                        tint = if (enabled) colours.label else colours.contentDisabled,
                        size = Theme.sizing.iconMedium,
                    )
                }
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
        when {
            files.isEmpty() -> Text(
                text = when {
                    dropOver -> if (multiple) strings.dropFilesHere else strings.dropFileHere
                    else -> placeholder ?: if (multiple) strings.chooseFiles else strings.chooseFile
                },
                // Said once, by the frame's state, rather than as a value.
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                style = Theme.typography.bodyMedium,
                colour = if (enabled) colours.placeholder else colours.contentDisabled,
                maxLines = 1,
            )
            else -> {
                // **One row of chips, one file or many.** A single file was its
                // name and size as text and several were wrapping chips, which
                // read as two different fields: asked for as "several files
                // looks too different from a single file. maybe we should make
                // all of them come up as chips like that, and also show the file
                // size inside the chip".
                //
                // And the field stays a field's height: the row scrolls sideways
                // rather than wrapping, fading at the edge it scrolls past, and
                // tells the field it is a line of text tall — a chip and its
                // touch target are taller, and spill into the field's own
                // padding instead of growing it.
                val scroll = rememberScrollState()
                val line = (metrics.minHeight - metrics.verticalPadding * 2).coerceAtLeast(0.dp)
                Box(Modifier.weight(1f).atMostTall(line)) {
                    Row(
                        modifier = Modifier
                            .fadingEdges(scroll, Orientation.Horizontal, fadeLength = Theme.spacing.md)
                            .horizontalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior()),
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (file in files) {
                            FileChip(
                                file = file,
                                sizeLabel = sizeLabel,
                                enabled = enabled,
                                // A single file is cleared by the field's own
                                // button, where the attach glyph was; several
                                // each have their own remove.
                                onRemove = if (single == null) ({ onFilesChange(files - file) }) else null,
                                removeLabel = strings.removeFile(file.name),
                                muted = if (enabled) colours.label else colours.contentDisabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A file in a [FileField]: its name, and its size after it in the field's quieter ink. */
@Composable
private fun FileChip(
    file: PickedFile,
    sizeLabel: (bytes: Long) -> String,
    enabled: Boolean,
    onRemove: (() -> Unit)?,
    removeLabel: String,
    muted: Color,
) {
    InputChip(
        onRemove = { onRemove?.invoke() },
        removeLabel = removeLabel,
        enabled = enabled,
        removeIcon = if (onRemove != null) SystemIcons.Close else null,
    ) {
        +file.name
        val size = file.size
        if (size != null) Text(text = sizeLabel(size), colour = muted, maxLines = 1)
    }
}

/**
 * Reports at most [height] to its parent, and centres what it holds on that,
 * letting it spill over above and below: a row of chips in a field, which is a
 * line of text tall to the field and a chip tall to the eye.
 */
private fun Modifier.atMostTall(height: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val reported = minOf(placeable.height, height.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, reported) { placeable.placeRelative(0, (reported - placeable.height) / 2) }
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
