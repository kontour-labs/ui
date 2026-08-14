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
 * @param onDismiss Called when the scrim is tapped. Pass `null` for a modal that
 *   must be dismissed explicitly — a destructive confirmation, say — and the
 *   scrim will still block input without offering a way out.
 * @param dismissLabel What a screen reader announces for the dismiss action.
 *   Required when [onDismiss] is set, because "button" is not a useful thing to
 *   hear when a dialog opens.
 */
@Composable
fun Scrim(
    visible: Boolean,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    color: Color = Theme.colors.scrim,
) {
    val target = if (visible) color else Color.Transparent
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = Theme.motion.tweenDefault(),
        label = "scrim",
    )

    if (!visible && animated.alpha == 0f) return

    Box(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(animated) }
            .then(
                if (onDismiss != null && visible) {
                    Modifier
                        .pointerInput(onDismiss) {
                            detectTapGestures { onDismiss() }
                        }
                        .semantics {
                            if (dismissLabel != null) contentDescription = dismissLabel
                            onClick(label = dismissLabel) {
                                onDismiss()
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
