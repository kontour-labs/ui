package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.kontour.ui.components.display.skeletonFill
import io.kontour.ui.theme.Theme

/**
 * Whether the subtree is standing in for content that has not arrived.
 *
 * Read by [Text] and [Icon], and the default for [Modifier.redacted] — so a
 * screen wrapped in [Redacted] draws as a skeleton of itself without anything
 * inside it being told twice.
 *
 * `false` everywhere unless [Redacted] says otherwise.
 */
val LocalRedacted = compositionLocalOf { false }

/**
 * Draws everything inside as a placeholder in the shape of the real thing.
 *
 * ```
 * Redacted(departures == null) {
 *     DepartureRow(departures ?: Departure.Placeholder)
 * }
 * ```
 *
 * ### Why this and not a second set of skeleton components
 *
 * [io.kontour.ui.components.display.Skeleton] and its two shaped helpers are
 * what you draw *instead of* your content, and they have a cost that only shows
 * up later: the placeholder is a second, parallel drawing of a layout you
 * already have. It starts matching and drifts, because nothing makes the two
 * agree — a row grows a third line and its skeleton does not, and nobody
 * notices until someone photographs a loading state.
 *
 * This is the other way round. The real layout draws, with real spacing and real
 * line breaks, and the *ink* is replaced. So a row that grows a third line grows
 * a third bar, and there is nothing to keep in step.
 *
 * SwiftUI's `redacted(reason:)` is the same idea and this is deliberately shaped
 * like it, including the way you take it back: nest a `Redacted(false)` around
 * the part that must still read — a price, an error, a countdown — which is what
 * `unredacted()` is there.
 *
 * ### What it does to the subtree
 *
 * Text becomes one bar **per line**, from the real text layout, so a paragraph
 * looks like a paragraph and a label looks like a label. Icons become a rounded
 * square of their own size. Anything else — a custom node, an image, a chart —
 * opts in with [Modifier.redacted], which reads [LocalRedacted] by default and
 * so needs no argument inside here.
 *
 * Everything redacted is **removed from the accessibility tree and stops taking
 * input**. There is nothing to announce and nothing to press: a screen reader
 * walking a dozen unlabelled bars is noise, and the container is where the
 * loading announcement belongs.
 *
 * @param enabled False draws the content normally, which is the whole of
 *   "unredacted": `Redacted(false) { … }` inside a redacted subtree.
 */
@Composable
fun Redacted(enabled: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRedacted provides enabled, content = content)
}

/**
 * Replaces this node's drawing with a placeholder of the same size.
 *
 * ```
 * AsyncImage(url, Modifier.size(64.dp).redacted())
 * ```
 *
 * For anything that is not [Text] or [Icon], which handle themselves. The node
 * still **measures and lays out exactly as it would have**, which is the point:
 * a placeholder the size of the real thing is a layout that does not jump when
 * the data lands.
 *
 * Defaults to [LocalRedacted], so inside a [Redacted] block this is bare, and
 * outside one it is a no-op until you pass `true`. Passing it explicitly is for
 * the single element that loads on its own.
 *
 * @param shape The placeholder's corners. A node that is already round — an
 *   avatar — should say so, since the drawing is clipped rather than derived.
 */
@Composable
fun Modifier.redacted(
    enabled: Boolean = LocalRedacted.current,
    shape: Shape = Theme.shapes.extraSmall,
): Modifier {
    if (!enabled) return this
    return this
        .clearAndSetSemantics { }
        // Swallows presses rather than relying on the caller to disable what is
        // underneath. A placeholder that can be tapped is a button whose label
        // the user cannot read.
        //
        // **Consumed on the Initial pass**, which is the half that is easy to
        // leave out: awaiting an event is not taking it. Initial runs parent to
        // child, and a `clickable` this sits inside is a *parent* in the chain,
        // so consuming here is what stops it — on Main the click would already
        // have been arbitrated.
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes.forEach { it.consume() }
                }
            }
        }
        .clip(shape)
        .skeletonFill()
        // After the fill, which draws behind. This is what stops the real
        // content drawing at all — the fill has already painted by the time the
        // chain reaches here.
        .drawWithContent { }
}
