package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Holds a fixed aspect ratio while its width is decided by the layout.
 *
 * ```kotlin
 * AspectRatioBox(16f / 9f) { RouteMap() }
 * ```
 *
 * Thin, but it exists because the alternative — `Modifier.aspectRatio` applied
 * at each call site — reliably gets applied to the wrong node: on the *content*
 * it constrains the content and the container still collapses.
 *
 * The main use is a media slot whose content has not loaded yet. Reserving the
 * space up front is what stops the page reflowing under the user's finger when
 * the image arrives.
 *
 * ### Why there is no `fillMaxWidth` in here
 *
 * There used to be, on the theory that `aspectRatio` needs a width to work from.
 * It does not: given a bounded width it already takes the widest it is offered,
 * so the call was doing nothing in the case it was written for — and breaking the
 * two cases where the width should *not* win.
 *
 * `fillMaxWidth` pins the minimum width to the maximum, and `aspectRatio` only
 * accepts a size its constraints are satisfied by. So a height-derived width was
 * rejected out of hand: [matchHeightConstraintsFirst] silently did nothing, and
 * `AspectRatioBox(1f, Modifier.height(120.dp))` measured to nothing at all,
 * because no size satisfied a fixed width and a fixed height at once.
 *
 * A caller who does want the width pinned passes `Modifier.fillMaxWidth()`, which
 * is where it was available from all along.
 */
@Composable
fun AspectRatioBox(
    ratio: Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    matchHeightConstraintsFirst: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.aspectRatio(ratio, matchHeightConstraintsFirst),
        contentAlignment = contentAlignment,
        content = content,
    )
}
