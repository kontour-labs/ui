package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * How a showcase page arranges its panels, and what goes in one.
 *
 * ### `FlowRow`, not `Row`
 *
 * Every showcase used to lay its panels out in a plain `Row` of fixed-width
 * `Column`s — three 380dp panels, say, which need 1,180dp with the gaps. That is
 * fine on the tablet-width canvas the goldens were recorded at and hopeless on a
 * phone: `Row` does not wrap, so the first panel took the whole 360dp and the
 * other two were slivers a few pixels wide pinned to the right edge, with every
 * specimen inside them squeezed to nothing.
 *
 * That is not a cosmetic problem. It is what crashed the app. A `Switch` in the
 * third panel was measured at **zero width**, and the thumb's
 * `coerceIn(0f, size.width - thumbWidth)` throws on an inverted range — from
 * inside draw, where nothing catches it. The switch has been made to degrade
 * instead, but a panel nobody can see is still a panel nobody can see.
 *
 * `FlowRow` wraps. Side by side where there is room, stacked where there is not,
 * and the wide goldens are unchanged because at those widths it puts everything
 * on one line exactly as `Row` did.
 */
@Composable
internal fun Panels(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.padding(Theme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        content = content,
    )
}

/**
 * One panel of a showcase.
 *
 * [width] is what it would *like* to be, not what it insists on: `widthIn(max =)`
 * rather than `width()`, so a 420dp panel is 420dp on a desktop and 360dp on a
 * phone instead of overflowing by sixty. Panels keep their own numbers because
 * they hold different things — a calendar needs more room than a row of chips —
 * and the number is now a ceiling rather than a demand.
 */
@Composable
internal fun Panel(
    width: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = Theme.spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.widthIn(max = width),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * A row of specimens whose widths are the exhibit.
 *
 * [Panels] is wrong for these. A panel captioned "Medium — rail on the leading
 * edge" is 700dp *because* that is the width that gets a rail, and letting it
 * wrap or shrink to fit a phone would destroy the thing it is demonstrating. So
 * these keep their widths and scroll instead: on a wide window nothing moves,
 * and on a phone all three are still reachable rather than two of them being
 * off the edge for ever.
 *
 * ### Only for specimens whose width is the exhibit
 *
 * Scrolling horizontally means measuring children at **infinite** width, which
 * silently turns [Panel]'s `widthIn(max =)` ceiling back into a fixed width —
 * the one thing that modifier exists to prevent. Two pages had drifted into
 * using this for ordinary panels, and on a phone the result was a 1,080dp strip
 * with its own section headings cut off mid-word and half its specimens several
 * screens to the right of where the page looked like it ended. Use [Panels], or
 * a bare `FlowRow`, for anything whose width is merely a preference.
 */
@Composable
internal fun DeviceStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        content = content,
    )
}

/** A titled group of specimens inside a [Panel]. */
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent.solid,
        )
        content()
    }
}
