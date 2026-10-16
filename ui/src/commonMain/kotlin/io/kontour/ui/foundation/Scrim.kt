package io.kontour.ui.foundation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * Dims everything behind a modal surface.
 *
 * More than decoration: it is also the thing that swallows input aimed at the
 * content underneath. A modal without a scrim covering the full window will let
 * a stray tap reach a button the user cannot see, which is how "I pressed
 * nothing and it deleted my trip" happens.
 *
 * Rendered inside the overlay host rather than by each modal, so a stack of
 * overlays gets one scrim at the right depth instead of several multiplying
 * into opacity.
 *
 * ### The scrim does not animate itself
 *
 * [fraction] is how far in the thing *in front* of it is, and the scrim is
 * simply that much dark. It used to run its own `animateColorAsState` — 220ms on
 * the standard easing — while the overlay it belonged to left on a 150ms exit
 * tween, or, for a modal sheet, on a gentle spring it knew nothing about. Two
 * animations of different lengths and different shapes describing one event, so
 * the dimming and the thing being dimmed always drifted apart. Worst on sheets,
 * because a spring and a tween disagree most in the middle.
 *
 * A lambda rather than a value, read in the draw phase: the fraction changes
 * every frame, and reading it during composition would recompose the scrim and
 * everything under it for each one.
 *
 * Nor does its colour, any more. It animated while an overlay host drew only its
 * topmost dimmed entry's scrim dark, so that a scrim going clear under a newly
 * opened dialog did not do it in one frame — and that swap was on a 220ms clock
 * of its own, which is what made the page lighten and darken again round a
 * dialog opened over a sheet. The host hands the dimming over by the dialog's
 * own fraction now, so there is no swap left to animate.
 *
 * @param onDismissRequest Called when the scrim is tapped. Pass `null` for a modal that
 *   must be dismissed explicitly — a destructive confirmation, say — and the
 *   scrim will still block input without offering a way out.
 * @param dismissLabel What a screen reader announces for the dismiss action.
 *   Required when [onDismissRequest] is set, because "button" is not a useful thing to
 *   hear when a dialog opens.
 * @param onScrollRequest Called when the wheel turns over the scrim.
 *
 *   A scrim is hit-tested before anything behind it and Compose's hit test
 *   stops at the topmost node, so a scrim blocks the wheel whether or not it
 *   consumes it — which is why the page went dead under an open dropdown. There
 *   is no way to be transparent to one kind of pointer event and opaque to
 *   another; a full-window node is in the way or it is not.
 *
 *   So a light overlay gets out of the way instead: this dismisses it, and the
 *   rest of the scroll lands on the page a frame later. That is also the right
 *   thing on its own terms — a menu is anchored to something that is about to
 *   move, and one left hanging beside a field that has scrolled away is worse
 *   than one that closed. Pass `null` for an overlay that must survive a
 *   scroll, which is any of them holding something the user is part way
 *   through.
 */
@Composable
fun Scrim(
    fraction: () -> Float,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    colour: Color = Theme.colours.scrim,
    onScrollRequest: (() -> Unit)? = null,
) = ScrimLayer(fraction, onDismissRequest, modifier, dismissLabel, colour, onScrollRequest, blocks = true)

/**
 * [Scrim], and whether it stands in the way at all.
 *
 * [blocks] false is a scrim that is only drawn: an overlay's on its way out,
 * which dims what is behind it as it goes and no longer keeps a finger off it.
 */
@Composable
internal fun ScrimLayer(
    fraction: () -> Float,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    colour: Color = Theme.colours.scrim,
    onScrollRequest: (() -> Unit)? = null,
    blocks: Boolean = true,
    hole: (() -> Rect?)? = null,
) {
    // What the scrim does with a pointer, kept apart from what it draws and
    // says: with a [hole], the drawing and the dismiss action stay on the
    // whole window and the pointer handling goes round the hole instead.
    val input = Modifier
        .then(
            if (blocks && onScrollRequest != null) {
                Modifier.pointerInput(onScrollRequest) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // **A scroll something else used is not a scroll at
                            // the scrim.**
                            //
                            // This fires because the page behind cannot scroll
                            // while a scrim is over it, so a scroll here means
                            // "close this and let me read what is underneath".
                            // A scroll the overlay's *own* panel consumed means
                            // the opposite, and the scrim is `fillMaxSize` so it
                            // is in the hit path for every pointer over that
                            // panel too.
                            //
                            // It cost nothing until anchored panels were bounded
                            // to their own side and given scrollers to cope —
                            // from which point scrolling a popover closed it
                            // mid-gesture.
                            //
                            // A panel with nothing left to scroll consumes
                            // nothing, so an overlay whose content fits still
                            // dismisses exactly as it always did.
                            if (
                                event.type == PointerEventType.Scroll &&
                                event.changes.none { it.isConsumed }
                            ) {
                                onScrollRequest()
                            }
                        }
                    }
                }
            } else {
                Modifier
            }
        )
        .then(
            if (!blocks) {
                Modifier
            } else if (onDismissRequest != null) {
                Modifier.pointerInput(onDismissRequest) {
                    detectTapGestures { onDismissRequest() }
                }
            } else {
                // Still consumes pointer input, so taps cannot reach content
                // the user can no longer see.
                Modifier.pointerInput(Unit) { detectTapGestures { } }
            }
        )
    val said = if (blocks && onDismissRequest != null) {
        Modifier.semantics {
            if (dismissLabel != null) contentDescription = dismissLabel
            onClick(label = dismissLabel) {
                onDismissRequest()
                true
            }
        }
    } else {
        Modifier
    }
    val drawn = modifier
        .fillMaxSize()
        .drawBehind {
            val f = fraction().coerceIn(0f, 1f)
            if (f > 0f) drawRect(colour.copy(alpha = colour.alpha * f))
        }

    if (hole == null || !blocks) {
        Box(drawn.then(input).then(said))
    } else {
        Box(drawn.then(said)) { AroundHole(hole, input) }
    }
}

/**
 * [input] over everything but [hole]: four bands round it, so a press inside the
 * hole meets nothing here and goes on to what is underneath.
 *
 * Four nodes rather than one with a hole cut in it, because Compose hit-tests a
 * node by its rectangle and by nothing else — there is no shape a pointer can
 * fall through. A band with no area is not placed, and so is not there to be
 * hit at all, near or direct.
 *
 * The hole is read in measure, so one that moves — a field on a page scrolled
 * under an open list — is followed without recomposing anything.
 */
@Composable
private fun AroundHole(hole: () -> Rect?, input: Modifier) {
    Layout(
        content = { repeat(4) { Box(input) } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val cut = hole()
        val left = cut?.left?.roundToInt()?.coerceIn(0, width) ?: 0
        val top = cut?.top?.roundToInt()?.coerceIn(0, height) ?: 0
        val right = cut?.right?.roundToInt()?.coerceIn(left, width) ?: left
        val bottom = cut?.bottom?.roundToInt()?.coerceIn(top, height) ?: top
        val bands = if (right <= left || bottom <= top) {
            // No hole, or one entirely off this host: the scrim is whole.
            listOf(IntRect(0, 0, width, height), IntRect.Zero, IntRect.Zero, IntRect.Zero)
        } else {
            listOf(
                IntRect(0, 0, width, top),
                IntRect(0, bottom, width, height),
                IntRect(0, top, left, bottom),
                IntRect(right, top, width, bottom),
            )
        }
        val placeables = measurables.mapIndexed { index, band ->
            band.measure(Constraints.fixed(bands[index].width, bands[index].height))
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val band = bands[index]
                // Where the band is in the window, whichever way the layout
                // runs: the hole is in the window's own coordinates, which do
                // not turn round right to left, so a relative placement is
                // handed the mirror of it.
                val start = if (layoutDirection == LayoutDirection.Ltr) band.left else width - band.right
                if (band.width > 0 && band.height > 0) placeable.placeRelative(start, band.top)
            }
        }
    }
}
