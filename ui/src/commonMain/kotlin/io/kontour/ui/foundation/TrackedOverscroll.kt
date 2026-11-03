package io.kontour.ui.foundation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * The platform's overscroll, with an account kept of how far it has taken the
 * content past an end: the drag it absorbed rather than passed to the scroll.
 *
 * Made for 0.5.2, for a scrollbar's thumb that gives way as its list is pulled
 * past its end. That was first read as a nested-scroll parent of the list —
 * the drag the list could not take, on its way up to the overscroll — and it
 * worked on Android and not on an iPhone: *"the overscroll thing on the
 * scrollbar doesn't seem to work on ios"*. Android's stretch is offered every
 * frame of a pull through the scroll, but iOS's bounce, once the content is
 * past its end, takes the drag itself and offers it to nothing, so the parent
 * saw one frame of the pull and the thumb hardly moved. This sits where the
 * drag is decided, so it sees what the effect absorbs on any platform.
 *
 * **What the scroll did not take, not what the effect says it took.** 0.5.2
 * counted the second, and Android's stretch never says it: it reports as
 * consumed only what it gave back and what the scroll took, and the drag it
 * pulls the stretch out with is reported left over. So on Android the thumb
 * gave nothing while the finger was down, and only the fling's bump showed on
 * release — *"it's not shrinking, until i let it go"*, raised 2026-11-03 for
 * 0.5.3. iOS's bounce says the same of the frame a pull first goes past the
 * end, and only counts the frames after it as taken. What both do is the same:
 * a finger's drag the scroll and the nested scroll above it did not take goes
 * into the effect, as a pull out or a pull back.
 *
 * It changes nothing the effect does. Every call goes straight through, and
 * its node is the effect's own, delegated, so the stretch or the bounce draws
 * as it always did.
 *
 * Let go, the account springs back on the theme's spring as the platform's
 * own does. A fling that arrives at the end with speed to spare, which the
 * platform turns into a bounce, gives it a brief bump.
 */
internal class TrackedOverscroll(
    private val inner: OverscrollEffect,
    private val settle: AnimationSpec<Float>,
) : OverscrollEffect {

    /** How far past an end, in pixels of drag: positive past the end, negative past the start. */
    var pull by mutableFloatStateOf(0f)
        private set

    private var moving: Job? = null

    override val isInProgress: Boolean get() = inner.isInProgress

    override val node: DelegatableNode = TrackedOverscrollNode(this, inner.node)

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        var byScroll = Offset.Zero
        val taken = inner.applyToScroll(delta, source) { offered -> performScroll(offered).also { byScroll += it } }
        if (source == NestedScrollSource.UserInput) {
            // Whatever the effect reports: see **What the scroll did not take**.
            val absorbed = delta - byScroll
            // One axis moves; whichever it is.
            val along = if (abs(absorbed.y) >= abs(absorbed.x)) absorbed.y else absorbed.x
            if (along != 0f) {
                moving?.cancel()
                // A drag toward the start is a positive delta, so past the end
                // is the drag's opposite; a drag back takes the account back,
                // to nothing and no further.
                val next = pull - along
                pull = if (pull != 0f && sign(next) != sign(pull)) 0f else next
            }
        }
        return taken
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        // Let go: the platform springs back, and so does the account.
        settleTo(0f, settle)
        inner.applyToFling(velocity) { offered ->
            performFling(offered).also { left ->
                val spare = if (abs(left.y) >= abs(left.x)) left.y else left.x
                if (spare != 0f) bump(-spare * BumpPerVelocity)
            }
        }
    }

    private fun settleTo(target: Float, spec: AnimationSpec<Float>) {
        if (pull == target && moving?.isActive != true) return
        val scope = (node as TrackedOverscrollNode).takeIf { it.isAttached }?.coroutineScope
        moving?.cancel()
        if (scope == null) {
            pull = target
            return
        }
        moving = scope.launch { animate(pull, target, animationSpec = spec) { value, _ -> pull = value } }
    }

    private fun bump(by: Float) {
        val scope = (node as TrackedOverscrollNode).takeIf { it.isAttached }?.coroutineScope ?: return
        moving?.cancel()
        val peak = by.coerceIn(-BumpMost, BumpMost)
        moving = scope.launch {
            animate(pull, peak, animationSpec = tween(BumpOutMillis)) { value, _ -> pull = value }
            animate(pull, 0f, animationSpec = settle) { value, _ -> pull = value }
        }
    }
}

/** The effect's own node, delegated, and findable from the scroller it is on. */
private class TrackedOverscrollNode(
    val effect: TrackedOverscroll,
    inner: DelegatableNode,
) : DelegatingNode(), TraversableNode {
    init {
        delegate(inner)
    }

    override val traverseKey: Any get() = TrackedOverscrollKey
}

private object TrackedOverscrollKey

/**
 * Wraps the platform's overscroll so each effect keeps its account: provided by
 * `KontourTheme` over whatever factory it finds, and never twice.
 */
internal data class TrackedOverscrollFactory(
    private val inner: OverscrollFactory,
    private val settle: AnimationSpec<Float>,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        TrackedOverscroll(inner.createOverscrollEffect(), settle)
}

/**
 * Finds the [TrackedOverscroll] on the scroller this is put on: for a modifier
 * on a scrolling container, ahead of its scroll, whose effect's node comes
 * later in the same chain.
 *
 * Looked for once, the first time it is asked after it is attached, and not
 * again until it is attached again: a scroller with no effect on it costs one
 * search, not one a frame.
 */
internal class OverscrollProbe {
    internal var node: Modifier.Node? = null
    private var searched = false
    private var found: TrackedOverscroll? = null

    /** How far past an end the content under this has been pulled, or 0 where nothing overscrolls. */
    fun pull(): Float = effect()?.pull ?: 0f

    private fun effect(): TrackedOverscroll? {
        if (searched) return found
        val at = node?.takeIf { it.isAttached } ?: return null
        searched = true
        at.traverseDescendants(TrackedOverscrollKey) { hit ->
            found = (hit as TrackedOverscrollNode).effect
            TraverseDescendantsAction.CancelTraversal
        }
        return found
    }

    internal fun forget() {
        node = null
        searched = false
        found = null
    }
}

/** Puts [probe] on the scroller, so it can find the effect after it in the chain. */
internal fun Modifier.overscrollProbe(probe: OverscrollProbe): Modifier = this then OverscrollProbeElement(probe)

private class OverscrollProbeElement(private val probe: OverscrollProbe) : ModifierNodeElement<OverscrollProbeNode>() {
    override fun create() = OverscrollProbeNode(probe)
    override fun update(node: OverscrollProbeNode) {
        if (node.probe !== probe) {
            node.probe.forget()
            node.probe = probe
            if (node.isAttached) probe.node = node
        }
    }
    override fun equals(other: Any?) = other is OverscrollProbeElement && other.probe === probe
    override fun hashCode() = probe.hashCode()
    override fun InspectorInfo.inspectableProperties() { name = "overscrollProbe" }
}

private class OverscrollProbeNode(var probe: OverscrollProbe) : Modifier.Node() {
    override fun onAttach() {
        probe.forget()
        probe.node = this
    }

    override fun onDetach() = probe.forget()
}

/** How much of a fling's spare speed, in pixels a second, becomes a bump's pull. */
private const val BumpPerVelocity = 0.04f

/** The most a bump pulls, in pixels. */
private const val BumpMost = 160f

/** How long a bump takes to reach its peak. */
private const val BumpOutMillis = 90
