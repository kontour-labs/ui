package io.kontour.ui.foundation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.theme.Theme

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
 * The *colour* still animates, because that is a different event — a scrim stops
 * being the darkened one when another overlay opens above it, and that swap is
 * not tied to anything appearing or leaving.
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
    color: Color = Theme.colors.scrim,
    onScrollRequest: (() -> Unit)? = null,
) {
    val animated by animateColorAsState(
        targetValue = color,
        animationSpec = Theme.motion.tweenDefault(),
        label = "scrimColor",
    )

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val f = fraction().coerceIn(0f, 1f)
                if (f > 0f) drawRect(animated.copy(alpha = animated.alpha * f))
            }
            .then(
                if (onScrollRequest != null) {
                    Modifier.pointerInput(onScrollRequest) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) onScrollRequest()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (onDismissRequest != null) {
                    Modifier
                        .pointerInput(onDismissRequest) {
                            detectTapGestures { onDismissRequest() }
                        }
                        .semantics {
                            if (dismissLabel != null) contentDescription = dismissLabel
                            onClick(label = dismissLabel) {
                                onDismissRequest()
                                true
                            }
                        }
                } else {
                    // Still consumes pointer input, so taps cannot reach content
                    // the user can no longer see.
                    Modifier.pointerInput(Unit) { detectTapGestures { } }
                }
            )
    )
}
