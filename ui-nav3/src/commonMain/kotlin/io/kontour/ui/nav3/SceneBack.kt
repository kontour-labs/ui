package io.kontour.ui.nav3

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A back gesture a scene answers itself, before `NavDisplay` does.
 *
 * For the pops `NavDisplay` cannot animate: a detail replaced by the one before
 * it in a two-pane layout, or a supporting pane closing beside its main one,
 * keep the scene's key, so as far as `NavDisplay` is concerned nothing moved.
 * The scene takes the gesture instead — its handler registers after
 * `NavDisplay`'s, so it wins while it is enabled — moves the pane with the
 * hand, and on release runs the pane out and then pops, exactly once.
 */
@Stable
internal class SceneBackState {
    /** How far through the gesture, 0 to 1. */
    var progress by mutableFloatStateOf(0f)
        internal set

    var swipeEdge by mutableIntStateOf(NavigationEvent.EDGE_NONE)
        internal set

    /** Back to rest at once: for a pane that is opening again after a held pose. */
    fun reset() {
        progress = 0f
    }
}

private object SceneBackInfo : NavigationEventInfo()

private class SceneBackHandler(
    private val scope: CoroutineScope,
    private val state: SceneBackState,
    private val runOut: Boolean,
    private val onBack: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(SceneBackInfo, false) {

    private val animatable = Animatable(0f)
    private var job: Job? = null

    override fun onBackStarted(event: NavigationEvent) {
        job?.cancel()
        state.swipeEdge = event.swipeEdge
        state.progress = event.progress
    }

    override fun onBackProgressed(event: NavigationEvent) {
        state.swipeEdge = event.swipeEdge
        state.progress = event.progress
    }

    override fun onBackCancelled() = settleTo(0f) {}

    override fun onBackCompleted() {
        // A press with no gesture before it — a key, a button — pops at once;
        // the pane's own change carries the rest. So does a pane that animates
        // its own way out, which holds the pose the hand left it in meanwhile.
        if (state.progress == 0f || !runOut) {
            onBack()
            return
        }
        settleTo(1f) {
            onBack()
            state.progress = 0f
        }
    }

    private fun settleTo(target: Float, then: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            animatable.snapTo(state.progress)
            animatable.animateTo(target, tween(SettleMillis, easing = LinearEasing)) { state.progress = value }
            then()
        }
    }
}

/**
 * Registers a scene's own back handler, enabled while [enabled], calling
 * [onBack] once when a gesture is let go or back is pressed.
 *
 * @param runOut Whether a let-go gesture first carries the pane the rest of the
 *   way before popping — right for a detail replaced by the one before it —
 *   or pops at once and holds the pose, for a pane that animates its own exit.
 */
@Composable
internal fun rememberSceneBack(enabled: Boolean, runOut: Boolean = true, onBack: () -> Unit): SceneBackState {
    val state = remember { SceneBackState() }
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onBack)
    val handler = remember(scope, state, runOut) { SceneBackHandler(scope, state, runOut) { latest() } }
    SideEffect { handler.isBackEnabled = enabled }
    val owner = LocalNavigationEventDispatcherOwner.current
    DisposableEffect(owner, handler) {
        owner?.navigationEventDispatcher?.addHandler(handler)
        onDispose { handler.remove() }
    }
    return state
}

/**
 * A pane following [back]: towards the trailing edge under swipe back, lifting
 * off under predictive back, and only fading under reduced motion. Reads the
 * gesture in the layer, so dragging does not recompose the pane.
 */
@Composable
internal fun Modifier.paneBackMotion(back: SceneBackState): Modifier {
    val style = LocalBackStyle.current
    val reduced = Theme.motion.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val corner = Theme.shapes.extraLarge
    val motion = this.graphicsLayer {
        val p = back.progress
        if (p == 0f) {
            shape = RectangleShape
            clip = false
            return@graphicsLayer
        }
        if (reduced) {
            alpha = 1f - 0.5f * p
            return@graphicsLayer
        }
        when (style) {
            BackStyle.Swipe -> translationX = (if (rtl) -1f else 1f) * p * size.width
            BackStyle.Predictive -> {
                val direction = when (back.swipeEdge) {
                    NavigationEvent.EDGE_RIGHT -> -1f
                    NavigationEvent.EDGE_LEFT -> 1f
                    else -> if (rtl) -1f else 1f
                }
                transformOrigin = TransformOrigin.Center
                val scale = 1f - 0.08f * p
                scaleX = scale
                scaleY = scale
                translationX = direction * p * 8.dp.toPx()
                alpha = 1f - 0.4f * p
                shape = corner
                clip = true
            }
        }
    }
    // Under swipe back the pane casts a shadow from its leading edge, as a
    // page does, onto the ground it is uncovering.
    return if (style == BackStyle.Swipe && !reduced) {
        motion.drawWithContent {
            drawContent()
            val p = back.progress
            if (p == 0f) return@drawWithContent
            val width = 16.dp.toPx()
            val x = if (rtl) size.width else -width
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (rtl) {
                        listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f))
                    },
                    startX = x,
                    endX = x + width,
                ),
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
            )
        }
    } else {
        motion
    }
}

/** How long a let-go or abandoned pane takes to finish its travel. */
private const val SettleMillis = 200
