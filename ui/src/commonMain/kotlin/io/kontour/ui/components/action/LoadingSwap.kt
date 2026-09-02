package io.kontour.ui.components.action

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.theme.Theme

/**
 * How small the content shrinks to as it leaves, and the spinner starts from.
 *
 * One number for both halves, because they are two views of one exchange: the
 * thing leaving and the thing arriving have to meet at the same size or there is
 * a seam in the middle of the transition.
 */
internal const val LoadingSwapScale = 0.6f

/**
 * A control's content, exchanged for a spinner while it is working.
 *
 * ### One exchange, one implementation
 *
 * Every button-like control in the library can be `loading`, and before this
 * only `Button` could — so an app with a loading FAB and a loading button had
 * one that swapped and one that did nothing. Sharing the swap is also what
 * stops the two drifting: a duration tuned here is tuned for all of them.
 *
 * The spinner is [Spinner], the library's only loader. `CircularProgress` with
 * a null progress goes through it too, so there is exactly one rotating arc in
 * the product.
 *
 * ### The content stays measured
 *
 * It is scaled and faded in a **layer**, so none of it reaches layout: the
 * control keeps its full width underneath and does not collapse to spinner width
 * and shove its neighbours sideways. That matters most for a text button, where
 * the difference is the whole label.
 *
 * It is also cleared from the semantics tree while loading, so a screen reader
 * reads the control's loading state rather than a label that is no longer
 * describing what the control is doing.
 *
 * @param spinnerSize The icon-sized slot the spinner occupies — usually the same
 *   glyph size the control's own icon uses, so the spinner lands where the icon
 *   was rather than at some size of its own.
 */
@Composable
internal fun LoadingSwap(
    loading: Boolean,
    spinnerSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = Theme.motion

    Box(modifier, contentAlignment = Alignment.Center) {
        val presence by animateFloatAsState(
            targetValue = if (loading) 0f else 1f,
            animationSpec = motion.tweenFast(),
            label = "loadingSwapContent",
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = presence
                    val shrink = LoadingSwapScale + (1f - LoadingSwapScale) * presence
                    scaleX = shrink
                    scaleY = shrink
                }
                .then(if (loading) Modifier.clearAndSetSemantics { } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }

        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                // The spinner pops in rather than fading — a slightly overscaled
                // entrance is the difference between "something is happening"
                // and "something appeared".
                (fadeIn(motion.tweenFast()) + scaleIn(motion.tweenFast(), initialScale = LoadingSwapScale))
                    .togetherWith(
                        fadeOut(motion.tweenFast()) +
                            scaleOut(motion.tweenFast(), targetScale = LoadingSwapScale)
                    )
            },
            label = "loadingSwapSpinner",
        ) { isLoading ->
            if (isLoading) {
                Spinner(modifier = Modifier.size(spinnerSize), colour = LocalContentColour.current)
            } else {
                Box(Modifier.size(0.dp))
            }
        }
    }
}
