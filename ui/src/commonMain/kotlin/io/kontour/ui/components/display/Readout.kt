package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.theme.Theme

/**
 * What a [Gauge], a [Meter] or a `Knob` reads out: the figure, and what it is a
 * figure of.
 *
 * ```kotlin
 * Gauge(value = speed, valueRange = 0f..120f, contentDescription = "Speed") {
 *     +"72"
 *     label("km/h")
 * }
 * ```
 *
 * A bare `+` is the figure, set large in the component's own style for its size;
 * [label] is the caption with it, small and muted. [value] and the composable
 * form of [label] take anything else — an icon, a figure in two weights — in the
 * same places and styles.
 *
 * **Collects rather than emits,** as a list row's regions do: where the figure
 * and the caption go, and how large, is the component's to decide — under a
 * needle's hub, in the middle of a dial, at either end of a bar.
 */
@LayoutScopeMarker
@Stable
class ReadoutScope internal constructor() {

    internal var value: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var label: (@Composable ContentScope.() -> Unit)? = null
        private set

    /** The figure: "72", "8.5k", "62%". */
    operator fun String.unaryPlus() {
        val text = this
        value = { +text }
    }

    /** The figure, carrying its own spans — a unit set smaller, say. */
    operator fun AnnotatedString.unaryPlus() {
        val text = this
        value = { +text }
    }

    /** The figure as any composable — an icon, a counter that animates. */
    fun value(content: @Composable ContentScope.() -> Unit) {
        value = content
    }

    /** What the figure is of: "km/h", "RPM", "Battery". */
    fun label(text: String) {
        label = { +text }
    }

    /** The caption as any composable. */
    fun label(content: @Composable ContentScope.() -> Unit) {
        label = content
    }

    internal val isEmpty: Boolean get() = value == null && label == null
}

/** Runs [content] and hands back what it claimed. */
internal fun readoutSlots(content: ReadoutScope.() -> Unit): ReadoutScope =
    ReadoutScope().apply(content)

/**
 * A dial's readout, the figure in [figure] over its caption, both centred.
 *
 * A gauge's figure is `displaySmall` when it has the middle of a dial of
 * [LargeDial] or more to itself, and `titleLarge` on a smaller dial or under a
 * needle's hub — see [dialFigure]; a knob's is `titleMedium`, on a face inside
 * its arc.
 */
@Composable
internal fun DialReadout(readout: ReadoutScope, figure: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        readout.value?.let { ReadoutFigure(figure.centred(), it) }
        readout.label?.let { ReadoutCaption(Theme.typography.labelSmall.centred(), it) }
    }
}

/** A gauge's figure style, from its [size] and whether a needle's hub is above it. */
@Composable
internal fun dialFigure(size: Dp, underHub: Boolean): TextStyle =
    if (!underHub && size >= LargeDial) Theme.typography.displaySmall else Theme.typography.titleLarge

/**
 * A bar's readout. Across a fixed horizontal [Meter] the caption is at the start
 * and the figure at the end, both in `labelMedium`; up a vertical one the
 * figure is over its caption; riding along with the reading the two sit side by
 * side, small enough to be a tag.
 */
@Composable
internal fun BarReadout(readout: ReadoutScope, horizontal: Boolean, atValue: Boolean) {
    when {
        atValue -> Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            readout.value?.let { ReadoutFigure(Theme.typography.labelMedium, it) }
            readout.label?.let { ReadoutCaption(Theme.typography.labelSmall, it) }
        }

        horizontal -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The caption is the row's name and takes the room; the figure is
            // its value, at the end. One size for both, a label's, so the two
            // read as a caption for the bar rather than as a heading over it.
            readout.label?.let { label ->
                Box(Modifier.weight(1f)) {
                    ProvideTextStyle(Theme.typography.labelMedium) { ContentSlot(maxLines = 1, content = label) }
                }
            }
            readout.value?.let { ReadoutFigure(Theme.typography.labelMedium, it) }
        }

        else -> Column {
            readout.value?.let { ReadoutFigure(Theme.typography.titleLarge, it) }
            readout.label?.let { ReadoutCaption(Theme.typography.labelSmall, it) }
        }
    }
}

@Composable
private fun ReadoutFigure(style: TextStyle, content: @Composable ContentScope.() -> Unit) {
    ProvideTextStyle(style) {
        ContentSlot(iconSize = Theme.sizing.iconLarge, maxLines = 1, content = content)
    }
}

@Composable
private fun ReadoutCaption(style: TextStyle, content: @Composable ContentScope.() -> Unit) {
    ProvideContentColour(Theme.colours.contentMuted) {
        ProvideTextStyle(style) {
            ContentSlot(iconSize = Theme.sizing.iconSmall, maxLines = 1, content = content)
        }
    }
}

private fun TextStyle.centred(): TextStyle = copy(textAlign = TextAlign.Center)

/** From this size a dial's figure is set in `displaySmall`. */
internal val LargeDial: Dp = 160.dp
