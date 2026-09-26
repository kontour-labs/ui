package io.kontour.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.theme.Theme
import kotlin.math.exp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where a back gesture aimed at one overlay has got to.
 *
 * One per entry, owned by the host and read by the entry's panel through
 * [LocalOverlayBack] — so a sheet, a dialog or a drawer can follow the hand
 * without knowing anything about where the gesture came from.
 */
@Stable
internal class OverlayBackState {
    /**
     * How far through the gesture, 0 to 1. Settles back to 0 when the gesture
     * is abandoned; is *held* when it commits, so the entry leaves from the pose
     * the hand left it in rather than snapping back first.
     */
    var progress by mutableFloatStateOf(0f)
        internal set

    /** Which edge the gesture started from — [NavigationEvent.EDGE_LEFT] and so on. */
    var swipeEdge by mutableIntStateOf(NavigationEvent.EDGE_NONE)
        internal set

    /**
     * True when back reaches this entry but may not close it — a dialog or a
     * sheet that says it is not dismissible. The gesture still moves it, a
     * little and against resistance, so the hand learns that it was heard.
     */
    var absorbing by mutableStateOf(false)
        internal set

    /**
     * What the panel is, once it has said — so the scrim can go with it at the
     * right rate. Null until a panel applies [overlayBackMotion].
     */
    var kind by mutableStateOf<OverlayBackKind?>(null)
        internal set

    /** Counts gestures let go past the point of no return; see `EntryHost`. */
    var commits by mutableIntStateOf(0)
        internal set
}

/** The back gesture aimed at the overlay this is inside, or null outside one. */
internal val LocalOverlayBack = compositionLocalOf<OverlayBackState?> { null }

/**
 * True for exactly the sheet a modal wraps, so that sheet — and not a plain
 * sheet someone put inside a dialog — follows the modal's back gesture. The
 * sheet sets it back to false for its own content.
 */
internal val LocalSheetFollowsBack = compositionLocalOf { false }

/** The info every overlay handler reports: nothing but that it is one. */
private object OverlayBackInfo : NavigationEventInfo()

/**
 * One entry's back handler.
 *
 * A plain `NavigationEventHandler` rather than `NavigationBackHandler`, because
 * the host decides which entry may answer by enabling its *dispatcher*, and
 * needs the gesture's progress in a state the panel can read in its drawing —
 * neither of which the composable form offers.
 */
internal class OverlayBackHandler(
    private val scope: CoroutineScope,
    private val back: OverlayBackState,
    /** Called when a gesture is let go past the point of no return. */
    private val onCommit: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(OverlayBackInfo, true) {

    private var settling: Job? = null
    private val settle = Animatable(0f)

    override fun onBackStarted(event: NavigationEvent) {
        settling?.cancel()
        back.swipeEdge = event.swipeEdge
        back.progress = event.progress
    }

    override fun onBackProgressed(event: NavigationEvent) {
        back.swipeEdge = event.swipeEdge
        back.progress = event.progress
    }

    override fun onBackCancelled() {
        settleBack()
    }

    override fun onBackCompleted() {
        if (back.absorbing) {
            // Heard, and refused: the entry goes back to where it was and
            // nothing is asked of the caller.
            settleBack()
        } else {
            back.commits++
            onCommit()
        }
    }

    /** Returns the panel to rest from wherever the gesture left it. */
    fun settleBack() {
        val from = back.progress
        if (from == 0f) return
        settling?.cancel()
        settling = scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f) { back.progress = value }
        }
    }
}

/**
 * A dispatcher under whatever owns back above this point, whose [enabled] is
 * applied in the same frame it changes.
 *
 * `rememberNavigationEventDispatcherOwner` exists and is not used, because it
 * applies its flag in a `LaunchedEffect` — a frame late, which is a frame in
 * which a back press opened over an overlay can still reach the page beneath.
 * And it throws when nothing above provides an owner, which a screenshot
 * harness or a preview does not; this returns null there instead, and the
 * overlays simply do not answer back.
 */
@Composable
internal fun rememberChildBackDispatcher(enabled: Boolean): NavigationEventDispatcherOwner? {
    val parent = LocalNavigationEventDispatcherOwner.current
    val owner = remember(parent) {
        parent?.let { above ->
            val dispatcher = NavigationEventDispatcher(above.navigationEventDispatcher)
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        }
    }
    SideEffect { owner?.navigationEventDispatcher?.isEnabled = enabled }
    DisposableEffect(owner) {
        onDispose { owner?.navigationEventDispatcher?.dispose() }
    }
    return owner
}

/**
 * Registers [handler] on [owner] for as long as this is composed, before
 * anything composed after it — which is what lets a stack inside a sheet win
 * over the sheet itself while it has somewhere to go back to.
 */
@Composable
internal fun RegisterBackHandler(owner: NavigationEventDispatcherOwner?, handler: NavigationEventHandler<*>) {
    DisposableEffect(owner, handler) {
        owner?.navigationEventDispatcher?.addHandler(handler)
        onDispose { handler.remove() }
    }
}

/** The per-entry back state and its handler, remembered for one entry. */
@Composable
internal fun rememberOverlayBack(onCommit: () -> Unit): Pair<OverlayBackState, OverlayBackHandler> {
    val scope = rememberCoroutineScope()
    val back = remember { OverlayBackState() }
    val commit = remember { CommitHolder() }
    commit.action = onCommit
    val handler = remember(scope, back) { OverlayBackHandler(scope, back) { commit.action() } }
    return back to handler
}

/** Holds the latest commit action, so the handler never needs replacing. */
private class CommitHolder {
    var action: () -> Unit = {}
}

/** What kind of surface a panel is, for how it answers a back gesture. */
internal enum class OverlayBackKind {
    /** A centred panel: a dialog, a command palette. */
    Dialog,

    /** A sheet at the bottom of the window. */
    BottomSheet,

    /** A sheet against the left edge of the window, whichever way text runs. */
    LeftSheet,

    /** A sheet against the right edge. */
    RightSheet,
}

/**
 * The panel following a back gesture aimed at the overlay it is in.
 *
 * Every transform the back gesture draws lives here, so there is one place that
 * reads `reduceMotion` and one place that says what each platform's feel is.
 * Reads the gesture only inside the layer block, so dragging does not recompose
 * the panel. Nothing happens outside an overlay or when no gesture is aimed here.
 *
 * | | [BackStyle.Predictive] | [BackStyle.Swipe] |
 * |---|---|---|
 * | Dialog | shrinks to nine tenths where it is | fades to three fifths — UIKit never drags an alert |
 * | Bottom sheet | shrinks towards its bottom edge | moves down, as a sheet is dismissed on iOS |
 * | Side sheet | shrinks towards its own edge | follows the finger towards its edge |
 *
 * A panel that may not close moves a little and against resistance. Reduced
 * motion moves nothing and only dims.
 */
@Composable
internal fun Modifier.overlayBackMotion(kind: OverlayBackKind): Modifier {
    val back = LocalOverlayBack.current ?: return this
    val style = LocalBackStyle.current
    val reduced = Theme.motion.reduceMotion
    SideEffect { back.kind = kind }
    return this.graphicsLayer {
        val p = back.progress
        if (p == 0f) return@graphicsLayer
        val absorbing = back.absorbing
        if (reduced) {
            alpha = 1f - (if (absorbing) 0.05f else 0.15f) * p
            return@graphicsLayer
        }
        // Resistance for a panel that will not go: most of the first stretch,
        // then less and less, so it never gets more than a little way.
        val give = if (absorbing) 1f - exp(-2.5f * p) else p
        when (kind) {
            OverlayBackKind.Dialog -> when (style) {
                BackStyle.Predictive -> {
                    val scale = 1f - (if (absorbing) 0.02f else 0.1f) * give
                    scaleX = scale
                    scaleY = scale
                }
                BackStyle.Swipe -> alpha = 1f - (if (absorbing) 0.1f else 0.4f) * give
            }
            OverlayBackKind.BottomSheet -> when (style) {
                BackStyle.Predictive -> {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val shrink = if (absorbing) 0.02f else 1f
                    scaleX = 1f - shrink * give * (48.dp.toPx() / size.width.coerceAtLeast(1f))
                    scaleY = 1f - shrink * give * (24.dp.toPx() / size.height.coerceAtLeast(1f))
                }
                BackStyle.Swipe -> {
                    translationY = if (absorbing) give * 24.dp.toPx() else p * size.height
                }
            }
            OverlayBackKind.LeftSheet, OverlayBackKind.RightSheet -> {
                val atLeft = kind == OverlayBackKind.LeftSheet
                val towardsEdge = if (atLeft) -1f else 1f
                when (style) {
                    BackStyle.Predictive -> {
                        transformOrigin = TransformOrigin(if (atLeft) 0f else 1f, 0.5f)
                        val shrink = if (absorbing) 0.02f else 1f
                        scaleX = 1f - shrink * give * (24.dp.toPx() / size.width.coerceAtLeast(1f))
                        scaleY = 1f - shrink * give * (48.dp.toPx() / size.height.coerceAtLeast(1f))
                    }
                    BackStyle.Swipe -> {
                        translationX = towardsEdge * if (absorbing) give * 24.dp.toPx() else p * size.width
                    }
                }
            }
        }
    }
}

/**
 * How much of an entry's scrim is left while a back gesture is aimed at it.
 *
 * The dim goes with whatever it dims: all of it for a sheet that is being
 * dragged away one to one, some of it for a panel that only shrinks, and none
 * of it for a panel that will not go.
 */
internal fun scrimUnderBack(back: OverlayBackState, style: BackStyle): Float {
    val p = back.progress
    if (p == 0f || back.absorbing) return 1f
    val followsFinger = back.kind != null && back.kind != OverlayBackKind.Dialog
    return when {
        style == BackStyle.Swipe && followsFinger -> 1f - p
        style == BackStyle.Swipe -> 1f - 0.5f * p
        else -> 1f - 0.3f * p
    }
}
