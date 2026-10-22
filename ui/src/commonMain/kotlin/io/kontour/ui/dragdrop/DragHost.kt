package io.kontour.ui.dragdrop

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.focusable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.pointerIconFor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.PickUpAxis
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberLandingFeedback
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The drag under way in one [io.kontour.ui.overlay.OverlayHost], and every
 * target that could take it.
 *
 * Provided by the host, which draws the carried thing above everything else in
 * it — overlays included, so a card can be carried out of a sheet and onto the
 * page. Targets register here as they attach and leave as they detach; nothing
 * about the drag lives in the source, which may be scrolled out of sight while
 * its thing is carried.
 */
@Stable
internal class DragHost {
    var session: DragSession? by mutableStateOf(null)
        private set

    val targets = mutableListOf<DropTarget>()
    val regions = mutableListOf<AutoScrollRegion>()

    /** The host's top left in the root, so the layer can draw in root coordinates. */
    var originOf: () -> Offset = { Offset.Zero }

    /** Layers below this are behind a dimmed modal and take nothing. */
    var blockedBelow: Int = 0

    /** Told of every change of target or of index, for the snap under the finger. */
    var onSnap: () -> Unit = {}

    /** Told where a carried thing ended: true for landed, false for refused. */
    var onLanding: (landed: Boolean) -> Unit = {}

    /** Told as a carried thing comes off the list it rode along, and as it boards one again. */
    var onTear: (off: Boolean) -> Unit = {}

    /** What a screen reader was last told, from the live region the host keeps. */
    var said: String by mutableStateOf("")
        private set

    /** Tells a screen reader [text], through [DragAnnouncer]. */
    fun announce(text: String) {
        said = text
    }

    /** The words and numbers it says things in, from the theme. */
    var strings: Strings = Strings()
    var formats: LocaleFormats? = null

    /** What its distances in dp are in pixels, from where the carried thing is drawn. */
    var density: Density = Density(1f)

    /** Whether a jump in where the thing is held is eased away: not under reduced motion. */
    var eases: Boolean = true

    /** Targets as they attach, read where the moves offered to a screen reader are worked out. */
    val attached = mutableStateListOf<DropTarget>()

    /** Told when a drag is over, however it ended. */
    var onEnded: () -> Unit = {}

    /** A keyboard-carried thing just put down, for its new place to take the focus. */
    var refocus: Any? by mutableStateOf(null)

    fun pickUp(next: DragSession) {
        if (session != null) return
        session = next
        announce(strings.dragPickedUp(next.label))
        if (next.keyboard) {
            // In its own list, at its own place, until a key moves it.
            val home = next.fromList?.target
            val at = next.fromIndex
            if (home != null && at != null) {
                next.target = home
                home.place(next, at)
                for (target in targets) {
                    target.statusState = when {
                        target.type !== next.type -> DropStatus.Idle
                        target === home -> DropStatus.Over
                        target.accepts(next.payload) -> DropStatus.Available
                        else -> DropStatus.Idle
                    }
                }
            }
            return
        }
        // Nothing scrolls until the thing has been somewhere other than an
        // edge: picked up beside one, it is not asking to be carried past it.
        for (region in regions) {
            region.armed = false
            region.enteredAt = null
        }
        // Picked up from a list it rides along, on the line it was picked up on.
        next.fromList?.takeIf { it.tearOff != null }?.let { list -> board(next, list, next.pointer.acrossOf(list)) }
        retarget()
    }

    /**
     * Puts [current] on [list]'s line, [across] from its top (or its start, for
     * a column) — where the pointer held it as it was picked up, or, boarding
     * again, where it is held on the thing, so the thing lies along the list.
     */
    private fun board(current: DragSession, list: DropListState<*>, across: Float) {
        val bounds = list.target.boundsInRoot() ?: return
        val depth = if (list.orientation == Orientation.Horizontal) bounds.height else bounds.width
        current.riding = list
        current.line = across.coerceIn(0f, (depth - 1f).coerceAtLeast(0f))
    }

    /** How far across [list] this point is, from its top or its start. */
    private fun Offset.acrossOf(list: DropListState<*>): Float {
        val bounds = list.target.boundsInRoot() ?: return 0f
        return if (list.orientation == Orientation.Horizontal) y - bounds.top else x - bounds.left
    }

    /**
     * Where [current] is held for a pointer at [at]: on the line of the list it
     * rides, along it as far as the list goes, until the pointer strays further
     * from the list than its tear-off — and from then on, under the pointer.
     */
    private fun ride(current: DragSession, at: Offset): Offset {
        val list = current.riding ?: return at
        val reach = list.tearOff ?: return at
        val bounds = list.target.boundsInRoot() ?: return at
        if (!bounds.inflate(reach).contains(at)) {
            current.riding = null
            current.scrollProbe = null
            current.leftBehind = list
            current.nudge = Offset.Zero
            onTear(true)
            return at
        }
        val horizontal = list.orientation == Orientation.Horizontal
        // What scrolls the list is the pointer, held inside the list's own
        // bounds — right and bottom are its first pixels outside — as it was
        // when the pointer was what was held there.
        current.scrollProbe = if (horizontal) {
            Offset(at.x.coerceIn(bounds.left, bounds.right - 1f), bounds.top + current.line)
        } else {
            Offset(bounds.left + current.line, at.y.coerceIn(bounds.top, bounds.bottom - 1f))
        }
        // The thing itself, not the pointer, is kept between the list's ends:
        // from where its first thing starts to where its last one ends. Held by
        // the pointer, a tab grabbed at its start could be pushed its whole
        // width past the end of its strip — over the "+" after the last tab.
        val run = list.runAlong(if (horizontal) bounds.width else bounds.height)
        val (lo, hi) = when {
            !horizontal -> bounds.top + run.start to bounds.top + run.endInclusive
            list.layoutDirection == LayoutDirection.Rtl -> bounds.right - run.endInclusive to bounds.right - run.start
            else -> bounds.left + run.start to bounds.left + run.endInclusive
        }
        // **Across, it is held on the line**, and only drawn a little towards
        // a pointer pulling it off: see [stretch]. From 2026-10-19 it gave half
        // the pull, so as not to sit dead still and then leap, and a tab
        // wobbled out of line with the others under a hand moving it along.
        // Reported 2026-10-21 for 0.5.0: *"it also feels too easy to drag out
        // of alignment with the rest of the tabs in the bar"*. Where it is
        // held is what it is over, so it is over its list until it comes
        // away; the leap as it does is eased away by the layer instead.
        val outside = if (horizontal) at.y.beyond(bounds.top, bounds.bottom) else at.x.beyond(bounds.left, bounds.right)
        val give = if (reach.isFinite()) stretch(outside) else 0f
        return if (horizontal) {
            val left = (at.x - current.grab.x).coerceIn(lo, maxOf(lo, hi - current.size.width))
            current.nudge = Offset(0f, give)
            Offset(left + current.grab.x, bounds.top + current.line)
        } else {
            val top = (at.y - current.grab.y).coerceIn(lo, maxOf(lo, hi - current.size.height))
            current.nudge = Offset(give, 0f)
            Offset(bounds.left + current.line, top + current.grab.y)
        }
    }

    /** How far this is outside [start] to [end]: negative before, positive after, nothing between. */
    private fun Float.beyond(start: Float, end: Float): Float = when {
        this < start -> this - start
        this > end -> this - end
        else -> 0f
    }

    /**
     * How far a thing riding a list is drawn off its line for a pointer
     * [outside] the list by that much: not at all for the first [RideHold],
     * then more and more slowly, never as far as [RideStretch]. A rubber band,
     * so a pull is seen to be taken up and a wobble is not seen at all.
     */
    private fun stretch(outside: Float): Float {
        val hold = with(density) { RideHold.toPx() }
        val most = with(density) { RideStretch.toPx() }
        val past = (abs(outside) - hold).coerceAtLeast(0f)
        if (past == 0f || most <= 0f) return 0f
        return sign(outside) * most * (1f - exp(-past * RideResistance / most))
    }

    /**
     * Where along this list its things run, from its start, in pixels of a list
     * [length] long: from the first thing's start, past the padding before it,
     * to the last one's end. Either end that has scrolled out of sight is the
     * list's own.
     */
    private fun DropListState<*>.runAlong(length: Float): ClosedFloatingPointRange<Float> {
        val info = listState.layoutInfo
        val before = info.beforeContentPadding.toFloat()
        val seen = info.visibleItemsInfo
        val start = seen.firstOrNull()?.takeIf { it.index == 0 }?.let { before + it.offset } ?: 0f
        val end = seen.lastOrNull()?.takeIf { it.index == info.totalItemsCount - 1 }
            ?.let { before + seen.maxOf { item -> item.offset + item.size } } ?: length
        val from = start.coerceIn(0f, length)
        return from..end.coerceIn(from, length)
    }

    /**
     * How far outside a list, across it and past its ends, a free thing is
     * taken in: the list's own depth, but never so far that a thing just
     * taken in is close to tearing off again. See [CaptureShare].
     */
    private fun captureOf(list: DropListState<*>, bounds: Rect): Float {
        val depth = if (list.orientation == Orientation.Horizontal) bounds.height else bounds.width
        val reach = list.tearOff ?: return 0f
        return minOf(depth, reach * CaptureShare)
    }

    /** A list a free thing could board, and how far outside it the pointer is: across it, and past its ends. */
    private class Near(val list: DropListState<*>, val bounds: Rect, val across: Float, val along: Float)

    /**
     * The lists a free thing at [at] could board — that ride their things,
     * would take this one, and are not behind a modal — nearest first, with
     * how far outside each the pointer is.
     */
    private fun nearby(current: DragSession, at: Offset): List<Near> = targets
        .filter { it.enabled && it.type === current.type && it.layer >= blockedBelow && it.list?.tearOff != null }
        .filter { it.accepts(current.payload) }
        .mapNotNull { target ->
            val list = target.list ?: return@mapNotNull null
            val bounds = target.boundsInRoot() ?: return@mapNotNull null
            val horizontal = list.orientation == Orientation.Horizontal
            val across = if (horizontal) at.y.beyond(bounds.top, bounds.bottom) else at.x.beyond(bounds.left, bounds.right)
            val along = if (horizontal) at.x.beyond(bounds.left, bounds.right) else at.y.beyond(bounds.top, bounds.bottom)
            Near(list, bounds, abs(across), abs(along))
        }
        .sortedWith(compareBy<Near> { maxOf(it.across, it.along) }.thenByDescending { it.list.target.layer })

    /**
     * The list a free thing at [at] boards: the nearest that would, under the
     * pointer or within its capture.
     *
     * **Taken in from near it, not only from over it.** It boarded only once
     * the pointer was over the strip itself, a band 48dp deep to aim a tab
     * carried back at, and a hand that came a little short or went a little
     * past carried it on by. Reported 2026-10-21 for 0.5.0: *"it should also
     * snap into a bar a bit easier"*. Not from inside another target beside
     * it, which is what the pointer is aiming at.
     */
    private fun boardable(current: DragSession, at: Offset): DropListState<*>? {
        val near = nearby(current, at).firstOrNull() ?: return null
        if (near.across == 0f && near.along == 0f) return near.list
        val capture = captureOf(near.list, near.bounds)
        if (near.across > capture || near.along > capture) return null
        val elsewhere = targets.any { other ->
            other.enabled && other.type === current.type && other.layer >= blockedBelow && other.list?.tearOff == null &&
                other.boundsInRoot()?.let { it.contains(at) && !it.contains(near.bounds.center) } == true
        }
        return if (elsewhere) null else near.list
    }

    /**
     * How far a free thing at [at] is drawn towards the line of a list it is
     * coming up to: from nothing a list's depth outside its capture to the
     * whole way at the capture's edge, on a smooth step, so it eases onto the
     * line and is on it as it is taken in. Not towards the list it last came
     * off until it has been clear of this, or it would hang back as it was
     * pulled away.
     */
    private fun magnet(current: DragSession, at: Offset): Offset {
        for (near in nearby(current, at)) {
            val horizontal = near.list.orientation == Orientation.Horizontal
            val depth = if (horizontal) near.bounds.height else near.bounds.width
            val capture = captureOf(near.list, near.bounds)
            val within = near.along <= capture && near.across <= capture + depth
            if (near.list === current.leftBehind) {
                if (within) continue
                current.leftBehind = null
            }
            if (!within || depth <= 0f) return Offset.Zero
            val t = ((capture + depth - near.across) / depth).coerceIn(0f, 1f)
            val share = t * t * (3f - 2f * t)
            // Where it is held across the list once on its line, as [board] puts it.
            val line = current.grab.acrossOf(near.list.orientation).coerceIn(0f, (depth - 1f).coerceAtLeast(0f))
            return if (horizontal) {
                Offset(0f, (near.bounds.top + line - at.y) * share)
            } else {
                Offset((near.bounds.left + line - at.x) * share, 0f)
            }
        }
        return Offset.Zero
    }

    /** Whether something under the pointer would scroll if given frames to do it. */
    fun wantsToScroll(edge: Float): Boolean {
        val current = session ?: return false
        // A keyboard carry moves by places, not by where a pointer is held.
        if (current.phase != DragPhase.Carrying || current.keyboard) return false
        val probe = current.scrollProbe ?: current.pointer
        return regions.filter { it.enabled }.map { it.wantsToScroll(probe, edge) }.any { it }
    }

    /**
     * Scrolls what is under the pointer, innermost first, one frame's worth.
     * Returns whether anything moved; a list already at its end does not.
     */
    fun autoScroll(now: Duration, frame: Duration, edge: Float, maxSpeed: Float, ramp: Duration): Boolean {
        val current = session ?: return false
        if (current.phase != DragPhase.Carrying) return false
        val probe = current.scrollProbe ?: current.pointer
        val under = regions
            .filter { it.enabled }
            .mapNotNull { region -> region.bounds()?.takeIf { it.contains(probe) }?.let { region to it } }
            .sortedBy { it.second.width * it.second.height }
        for ((region, _) in under) {
            val step = region.step(probe, edge, maxSpeed, ramp, now, frame)
            if (step == 0f) continue
            if (region.state.dispatchRawDelta(step) != 0f) {
                retarget()
                return true
            }
        }
        return false
    }

    /** The pointer is at [pointerInRoot]. */
    fun carry(pointerInRoot: Offset) {
        val current = session ?: return
        if (current.phase != DragPhase.Carrying) return
        val was = current.drawn
        val rode = current.riding
        if (current.riding == null && !current.keyboard) {
            boardable(current, pointerInRoot)?.let { list ->
                board(current, list, current.grab.acrossOf(list.orientation))
                current.leftBehind = null
                onTear(false)
            }
        }
        current.pointer = ride(current, pointerInRoot)
        if (current.riding == null && !current.keyboard) current.nudge = magnet(current, pointerInRoot)
        // Coming off a line or boarding one moves where the thing is held by
        // up to the tear-off at once. This frame draws it where it was, and the
        // layer eases the difference away while it goes on following the pointer.
        if (current.riding !== rode && eases) {
            current.jolt = was - (current.topLeft + current.nudge + current.easing)
            current.jolts++
        }
        retarget()
    }

    /** How far into the thing, across a list running [orientation], it is held. */
    private fun Offset.acrossOf(orientation: Orientation): Float = if (orientation == Orientation.Horizontal) y else x

    /**
     * Works out, again, what the carried thing is over: after it moved, and after
     * whatever is under it scrolled.
     */
    fun retarget() {
        val current = session ?: return
        if (current.phase != DragPhase.Carrying) return
        val probe = current.pointer
        val hit = targets
            .filter { it.enabled && it.type === current.type && it.layer >= blockedBelow }
            .mapNotNull { target -> target.boundsInRoot()?.takeIf { it.contains(probe) }?.let { target to it } }
            // The topmost layer, and within it the innermost: the smallest that
            // contains the pointer is the one drawn inside the others.
            .sortedWith(compareByDescending<Pair<DropTarget, Rect>> { it.first.layer }.thenBy { it.second.width * it.second.height })
            .firstOrNull()?.first
        val before = current.target
        val moved = hit !== before
        if (moved) {
            before?.leave()
            current.target = hit
            current.index = null
        }
        val indexBefore = current.index
        hit?.over(current, probe)
        // A snap for each place it could land: a new target, or a new position
        // in a list. Nothing for leaving every target — that is not a place.
        if (hit != null && (moved || current.index != indexBefore)) {
            onSnap()
            announce(hit.positionText(current))
        }
        for (target in targets) {
            target.statusState = when {
                target.type !== current.type -> DropStatus.Idle
                target === hit -> if (target.accepts(current.payload)) DropStatus.Over else DropStatus.Refused
                target.accepts(current.payload) -> DropStatus.Available
                else -> DropStatus.Idle
            }
        }
    }

    /** Let go: to the target if it takes it, home if not. */
    fun putDown() {
        val current = session ?: return
        if (current.phase != DragPhase.Carrying) return
        val target = current.target
        if (target != null && target.accepts(current.payload) && target.changes(current)) {
            current.landingOn = target
            current.destination = target.landingBounds(current)
            current.phase = DragPhase.Landing
        } else {
            val refused = target != null && !target.accepts(current.payload)
            goHome(current)
            if (refused) {
                onLanding(false)
                announce(strings.dragRefused(target.label))
            } else {
                announce(strings.dragCancelled(current.label))
            }
        }
    }

    /**
     * Back where it came from. Whatever it was over gives up the room it made,
     * now rather than when it lands, so the place it left opens as it flies to it.
     */
    private fun goHome(current: DragSession) {
        current.target?.leave()
        current.target = null
        current.index = null
        current.phase = DragPhase.Returning
        current.destination = current.source.homeBounds()
        for (target in targets) target.statusState = DropStatus.Idle
    }

    /** Called off: Escape, the pointer taken away, the source gone. */
    fun cancel() {
        val current = session ?: return
        if (current.phase != DragPhase.Carrying) return
        goHome(current)
        announce(strings.dragCancelled(current.label))
    }

    /**
     * The landing has reached its place: the drop happens, once.
     *
     * The drag is not over yet. It stays [DragPhase.Dropped] — the gap held,
     * the dropped item laid out in it without fading in — until the lists the
     * caller moves it between show the move, so the carried thing hands over to
     * the item in its place rather than to a list that has not caught up.
     */
    fun land(ended: DragSession) {
        if (session !== ended || ended.phase != DragPhase.Landing) return
        val landedOn = ended.landingOn ?: return
        ended.phase = DragPhase.Dropped
        landedOn.drop(ended)
        onLanding(true)
        announce(strings.dragDropped(ended.label, landedOn.label))
        refocus = ended.payload
    }

    /** The drag is over: home, or dropped and shown. */
    fun finish(ended: DragSession) {
        if (session !== ended) return
        ended.target?.leave()
        for (target in targets) target.statusState = DropStatus.Idle
        session = null
        onEnded()
    }

    /** Called from a host that is leaving, or a source that has gone. */
    fun abandon(source: SourceHandle) {
        val current = session ?: return
        if (current.source !== source) return
        // Dropped, the source going is the drop being shown — the item has left
        // the list it was in — and the item in its new place takes over.
        if (current.phase == DragPhase.Dropped) {
            source.gone = true
            return
        }
        current.target?.leave()
        for (target in targets) target.statusState = DropStatus.Idle
        session = null
        onEnded()
    }
}

/**
 * Where a drag is: carried; flying to where it was let go, or home; or dropped,
 * and waiting for the lists to show it.
 */
internal enum class DragPhase { Carrying, Landing, Returning, Dropped }

/** One drag: what is carried, from where, and where it is now. */
internal class DragSession(
    val type: DragType<*>,
    val payload: Any,
    val label: String,
    val source: SourceHandle,
    val fromList: DropListState<*>?,
    /** Where it was in [fromList] when it was picked up: see [fromIndex]. */
    private val pickedUpAt: Int?,
    /** The pointer's distance from the source's top left when it was picked up. */
    val grab: Offset,
    val size: IntSize,
    val layer: GraphicsLayer?,
    val shape: Shape,
    val keyboard: Boolean,
    pointer: Offset,
) {
    var pointer: Offset by mutableStateOf(pointer)
    var target: DropTarget? by mutableStateOf(null)

    /**
     * The list the thing rides along, held on its line until the pointer pulls
     * it clear by the list's [DropListState.tearOff]; `null` while it follows
     * the pointer anywhere. A document tab rides its strip.
     */
    var riding: DropListState<*>? by mutableStateOf(null)

    /** How far across [riding] the thing is held: from its top, or its start for a column. */
    var line: Float = 0f

    /**
     * The list it last came off, whose magnet does not draw it back until it
     * has been carried clear of it: see `DragHost.magnet`.
     */
    var leftBehind: DropListState<*>? = null

    /**
     * How far from where it is held it is drawn, for the eye only: a little
     * towards a pointer pulling it off the list it rides, or towards the line
     * of one it is coming up to. What it is over is where it is held.
     */
    var nudge: Offset by mutableStateOf(Offset.Zero)

    /**
     * The jump in where it is held as it came off a line or boarded one, not
     * yet handed to [easing], and how many there have been, which the layer
     * waits on. Set by the move that made it, so the frame that draws the move
     * draws the thing where it was.
     */
    var jolt: Offset by mutableStateOf(Offset.Zero)
    var jolts: Int by mutableIntStateOf(0)

    /** What is left of the jumps, eased to nothing by the layer. */
    var easing: Offset by mutableStateOf(Offset.Zero)

    /** Where its top left is drawn while a pointer carries it: under the pointer, and the rest. */
    val drawn: Offset get() = topLeft + nudge + easing + jolt

    /**
     * How far off the page it is, 0 to 1: its shadow, the little it grows, and
     * for a [LiftedShape] its shape. Here rather than in the layer that draws
     * it, so what it was carried from can draw itself to match.
     */
    val lift = Animatable(0f)

    /**
     * Where what is under the thing is scrolled from, while it rides a list:
     * the pointer, held inside the list. The thing is kept between the list's
     * ends, and a tab held at its start would otherwise never come near the
     * edge it is pushed against. `null` while it follows the pointer.
     */
    var scrollProbe: Offset? = null
    var phase: DragPhase by mutableStateOf(DragPhase.Carrying)

    /** Where in the target list it would land, counted without the source. */
    var index: Int? by mutableStateOf(null)

    var landingOn: DropTarget? = null

    /** Where it was going when it was let go: what [aim] falls back on. */
    var destination: Rect? = null

    /**
     * Where it is going, read again each frame of the flight: a list can scroll
     * under a landing, and a place opening again for a thing on its way home
     * moves as the list lays itself out.
     */
    fun aim(): Rect? = when (phase) {
        DragPhase.Landing -> landingOn?.landingBounds(this)
        DragPhase.Returning -> fromList?.target?.landingBounds(this) ?: source.homeBounds()
        else -> null
    } ?: destination

    val topLeft: Offset get() = pointer - grab

    /**
     * Where the carried thing is in [fromList] **now**, found by its key, and
     * where it was picked up from if the list no longer has it.
     *
     * Not fixed at the pick-up. The list it came from can change while it is
     * carried — a row above it removed, one arriving from the network — and a
     * drop reporting where it *was* had the documented `removeAt(fromIndex)`
     * take out the row beside it, and the place it would land counted against
     * the wrong row being away.
     */
    val fromIndex: Int?
        get() {
            val at = pickedUpAt ?: return null
            return fromList?.indexOfPayload(payload) ?: at
        }

    companion object {
        /** A session for a move that is never carried: a screen reader's, straight to its place. */
        fun probe(spec: SourceSpec) = DragSession(
            type = spec.type,
            payload = spec.payload,
            label = spec.label,
            source = SourceHandle(),
            fromList = spec.fromList,
            pickedUpAt = spec.fromIndex,
            grab = Offset.Zero,
            size = IntSize.Zero,
            layer = null,
            shape = androidx.compose.ui.graphics.RectangleShape,
            keyboard = true,
            pointer = Offset.Zero,
        )
    }
}

/** A source's place in the layout, read while its thing is carried. */
internal class SourceHandle {
    var coordinates: LayoutCoordinates? = null
    var layer: GraphicsLayer? = null
    var shape: Shape = androidx.compose.ui.graphics.RectangleShape

    /**
     * Whether the element has gone since its thing was dropped — taken out of
     * the list it was in. Its layer goes with it, and the carried thing, drawn
     * from that layer, is no longer drawn.
     */
    var gone: Boolean by mutableStateOf(false)

    /** Where the thing goes back to, in the root. */
    fun homeBounds(): Rect? = coordinates?.takeIf { it.isAttached }?.let {
        Rect(it.positionInRoot(), it.size.let { size -> androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()) })
    }
}

/**
 * A target of any kind, as the host sees it: where it is, what it takes, and
 * what it does with a drop. A [DropTargetState] holds one, and a
 * [DropListState] is one.
 */
internal abstract class DropTarget(
    val type: DragType<*>,
    label: String,
) {
    var label: String by mutableStateOf(label)
    var statusState: DropStatus by mutableStateOf(DropStatus.Idle)
    var coordinates: LayoutCoordinates? = null
    var layer: Int = 0
    var enabled: Boolean = true

    fun boundsInRoot(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()

    abstract fun accepts(payload: Any): Boolean

    /** The carried thing is over this, at [pointerInRoot]. Lists work out where. */
    open fun over(session: DragSession, pointerInRoot: Offset) = Unit

    /** The carried thing has left, or the drag is over. */
    open fun leave() = Unit

    /** Whether a drop here would change anything: a row let go in its own place would not. */
    open fun changes(session: DragSession): Boolean = true

    /** Where a thing landing here goes, in the root; `null` to shrink where it is. */
    open fun landingBounds(session: DragSession): Rect? = null

    /** What is said as the thing comes over this. */
    open fun positionText(session: DragSession): String = label

    /** The list this target is, if it is one. */
    open val list: DropListState<*>? get() = null

    /** Whether it runs down the screen, for which arrows move along it. */
    open val vertical: Boolean get() = true

    /** How many places it has for a keyboard to step through; `null` for one. */
    open fun positions(session: DragSession): Int? = null

    /** Puts the carried thing at place [index], from a keyboard or an action. */
    open fun place(session: DragSession, index: Int) = Unit

    /** Let go over this, and taken. Called once per drag. */
    abstract fun drop(session: DragSession)
}

/** The drag host of the nearest [io.kontour.ui.overlay.OverlayHost], if there is one. */
internal val LocalDragHost = staticCompositionLocalOf<DragHost?> { null }

/**
 * How high in the overlay stack this is drawn: 0 on the page, and one more for
 * each overlay entry. Provided by the host, read by targets so one in a sheet
 * outranks the page under it.
 */
internal val LocalOverlayLayer = staticCompositionLocalOf { 0 }

/** Registers [target] with the nearest host while attached, with its bounds and its layer. */
internal fun Modifier.registersDropTarget(target: DropTarget, enabled: Boolean): Modifier =
    this then RegistersDropTargetElement(target, enabled)

private class RegistersDropTargetElement(
    val target: DropTarget,
    val enabled: Boolean,
) : ModifierNodeElement<RegistersDropTargetNode>() {
    override fun create() = RegistersDropTargetNode(target, enabled)

    override fun update(node: RegistersDropTargetNode) {
        if (node.target !== target) {
            node.unregister()
            node.target = target
            node.register()
        }
        node.target.enabled = enabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dropTarget"
        properties["enabled"] = enabled
    }

    override fun equals(other: Any?) =
        other is RegistersDropTargetElement && other.target === target && other.enabled == enabled

    override fun hashCode() = target.hashCode() * 31 + enabled.hashCode()
}

private class RegistersDropTargetNode(
    var target: DropTarget,
    enabled: Boolean,
) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var host: DragHost? = null

    init {
        target.enabled = enabled
    }

    override fun onAttach() = register()

    override fun onDetach() = unregister()

    fun register() {
        val found = currentValueOf(LocalDragHost) ?: return
        host = found
        target.layer = currentValueOf(LocalOverlayLayer)
        if (target !in found.targets) {
            found.targets += target
            found.attached += target
        }
    }

    fun unregister() {
        val found = host ?: return
        found.targets -= target
        found.attached -= target
        target.statusState = DropStatus.Idle
        host = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        target.coordinates = coordinates
    }
}

/**
 * The element half of a source: draws itself into a layer the host draws the
 * carried thing from, says where it is, and while its thing is carried shows a
 * faint ghost of itself — or, in a list that has folded its place, nothing.
 *
 * It is pinned while carried, so a lazy list scrolled under a long carry keeps
 * it composed, and the pointer it is following with it. The cursor is here
 * rather than on the layer the thing is drawn in: Compose fixes the nodes a held
 * pointer reports to when it goes down, so a cursor on something that appeared
 * at the pick-up is never asked. See `heldCursor`.
 */
@Composable
internal fun Modifier.carriedElement(handle: SourceHandle, list: Boolean, shape: Shape): Modifier {
    val host = LocalDragHost.current ?: return this
    val layer = rememberGraphicsLayer()
    handle.layer = layer
    handle.shape = shape

    val session = host.session
    val carrying = session != null && session.source === handle
    val refused = carrying && session.target?.statusState == DropStatus.Refused

    val pinnable = LocalPinnableContainer.current
    DisposableEffect(carrying, pinnable) {
        val pinned: PinnableContainer.PinnedHandle? = if (carrying) pinnable?.pin() else null
        onDispose { pinned?.release() }
    }
    // A source that goes while its thing is still carried ends the drag with it.
    DisposableEffect(host, handle) { onDispose { host.abandon(handle) } }

    return this
        .onGloballyPositioned { handle.coordinates = it }
        .pointerHoverIcon(
            icon = pointerIconFor(
                when {
                    refused -> Cursor.NotAllowed
                    carrying -> Cursor.Grabbing
                    else -> Cursor.Default
                },
            ),
            overrideDescendants = carrying,
        )
        // The ghost's alpha on a layer of its own, outside the one the host
        // draws from: a layer's own alpha would be the carried thing's as well.
        .graphicsLayer { alpha = if (carrying && !list) GhostAlpha else 1f }
        .drawWithContent {
            // Recorded every time it draws, carried or not, so the thing in the
            // host's layer is the element as it is now — a progress ring still
            // turning, a count still changing — rather than a photograph of it.
            layer.record { this@drawWithContent.drawContent() }
            if (!carrying || !list) drawLayer(layer)
        }
}

/**
 * The gesture half of a source: picks the element [handle] stands for up on
 * [start]'s terms, wherever on it this is — the whole of it, or a grip — and
 * hands the carry to the host.
 */
@Composable
internal fun Modifier.pickUpGesture(
    handle: SourceHandle,
    spec: () -> SourceSpec,
    enabled: Boolean,
    start: DragStart,
    interactionSource: MutableInteractionSource?,
    grip: Boolean = false,
    /**
     * Whether this node offers a screen reader the moves. An item with a handle
     * offers them on the handle, which sits inside the row a reader lands on
     * and merges into it; the item's own node has no words of its own, and is
     * passed over.
     */
    offersMoves: Boolean = true,
): Modifier {
    val host = LocalDragHost.current
    val strings = Theme.strings
    val direction = LocalLayoutDirection.current
    val currentSpec by rememberUpdatedState(spec)
    // The moves a screen reader is offered work with no host at all: nothing is
    // carried, so nothing has to be drawn.
    val moves = if (!offersMoves) Modifier else Modifier.semantics {
        if (enabled) customActions = host.movesFor(currentSpec(), strings, direction)
    }
    if (host == null) {
        warnNoHost()
        return this.then(moves)
    }
    val longPressed = rememberLongPressFeedback()
    val modality = LocalInputModality.current
    val mouse by rememberUpdatedState(!modality.needsLargeTargets)
    val own = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val drag = remember { mutableStateOf<DragInteraction.Start?>(null) }

    fun byKeyboard(): Boolean {
        val element = handle.coordinates?.takeIf { it.isAttached } ?: return false
        if (host.session != null) return false
        val what = currentSpec()
        val centre = Offset(element.size.width / 2f, element.size.height / 2f)
        host.pickUp(
            DragSession(
                type = what.type,
                payload = what.payload,
                label = what.label,
                source = handle,
                fromList = what.fromList,
                pickedUpAt = what.fromIndex,
                grab = centre,
                size = element.size,
                layer = handle.layer,
                shape = handle.shape,
                keyboard = true,
                pointer = element.positionInRoot() + centre,
            ),
        )
        return host.session?.source === handle
    }

    return this
        .then(moves)
        .then(if (grip && enabled) Modifier.focusable() else Modifier)
        .onPreviewKeyEvent { event -> enabled && host.session == null && picksUp(event, grip) && byKeyboard() }
        .onGloballyPositioned { own[0] = it }
        .pointerInput(host, handle, enabled, start) {
            if (!enabled) return@pointerInput
            // Whether *this* node took the thing: a row and its handle are two of
            // these over one place, and both see every press.
            var owned = false
            val immediate = {
                when (start) {
                    DragStart.Immediate -> true
                    DragStart.LongPress -> false
                    DragStart.Auto -> mouse
                }
            }
            fun toRoot(local: Offset): Offset? = own[0]?.takeIf { it.isAttached }?.localToRoot(local)
            detectPickUp(
                axis = PickUpAxis.Free,
                immediate = immediate,
                onPickUp = { at ->
                    val element = handle.coordinates?.takeIf { it.isAttached }
                    val layer = handle.layer
                    val pointer = toRoot(at)
                    if (host.session == null && element != null && layer != null && pointer != null) {
                        val what = currentSpec()
                        host.pickUp(
                            DragSession(
                                type = what.type,
                                payload = what.payload,
                                label = what.label,
                                source = handle,
                                fromList = what.fromList,
                                pickedUpAt = what.fromIndex,
                                grab = pointer - element.positionInRoot(),
                                size = element.size,
                                layer = layer,
                                shape = handle.shape,
                                keyboard = false,
                                pointer = pointer,
                            ),
                        )
                        owned = host.session?.source === handle
                        if (owned) {
                            if (!immediate()) longPressed()
                            interactionSource?.let { source ->
                                val started = DragInteraction.Start()
                                drag.value = started
                                source.tryEmit(started)
                            }
                        }
                    }
                },
                onCarry = { change, _ ->
                    if (owned) {
                        change.consume()
                        toRoot(change.position)?.let(host::carry)
                    }
                },
                onPutDown = {
                    if (owned) {
                        owned = false
                        host.putDown()
                        drag.value?.let { interactionSource?.tryEmit(DragInteraction.Stop(it)) }
                        drag.value = null
                    }
                },
                onCancel = {
                    if (owned) {
                        owned = false
                        host.cancel()
                        drag.value?.let { interactionSource?.tryEmit(DragInteraction.Cancel(it)) }
                        drag.value = null
                    }
                },
            )
        }
}

/** An open hand over a grip, until it closes on the pick-up. */
@Composable
internal fun Modifier.grabCursor(): Modifier = pointerHoverIcon(pointerIconFor(Cursor.Grab))

/** How much of a plain source shows where it was while its thing is carried. */
private const val GhostAlpha = 0.35f

private var warnedNoHost = false

private fun warnNoHost() {
    if (warnedNoHost) return
    warnedNoHost = true
    println(
        "Kontour UI: a drag source has no OverlayHost above it, which draws what is " +
            "being carried, so it cannot be dragged. Put the app's content inside an OverlayHost.",
    )
}

/**
 * The carried thing, drawn by the host above everything in it, and its landing:
 * to its place in a list, into a target, or home again.
 */
@Composable
internal fun DragLayer(host: DragHost) {
    host.strings = Theme.strings
    host.formats = Theme.formats
    host.density = LocalDensity.current
    host.eases = !Theme.motion.reduceMotion
    DragAnnouncer(host)
    val snaps = rememberDetentTicker(FeedbackIntent.Snap)
    val landing = rememberLandingFeedback()
    // Coming off the list it rode along is a threshold passed, and boarding one
    // again the softer way back: the thing is free, then it is held again.
    val tear = rememberDetentTicker(FeedbackIntent.DragThreshold, back = FeedbackIntent.DragThresholdBack)
    DisposableEffect(host, snaps, landing, tear) {
        // A place for each snap, counted up: the ticker wants a detent that
        // changed, and a drag's places have no order of their own.
        var crossings = 0
        host.onSnap = { snaps.at(crossings++) }
        host.onLanding = { landed -> if (landed) landing.landed() else landing.refused() }
        host.onTear = { off ->
            // Armed on the line as the first tear comes, which the ticker needs
            // a first call for.
            tear.at(if (off) 0f else 1f)
            tear.at(if (off) 1f else 0f)
        }
        host.onEnded = {
            snaps.reset()
            tear.reset()
        }
        onDispose {
            host.onSnap = {}
            host.onLanding = {}
            host.onTear = {}
            host.onEnded = {}
        }
    }

    val session = host.session ?: return
    val motion = Theme.motion
    val density = LocalDensity.current

    // Scrolls what is under a carried thing held near its edge — and asks for
    // frames only while that is so, and while something moves for it: a list
    // at its end, or a thing held still in the middle, leaves the clock alone.
    LaunchedEffect(session, density) {
        val edge = with(density) { AutoScrollEdge.toPx() }
        val speed = with(density) { AutoScrollMaxSpeed.toPx() }
        while (session.phase == DragPhase.Carrying) {
            snapshotFlow { session.pointer to session.phase }
                .first { (_, phase) -> phase != DragPhase.Carrying || host.wantsToScroll(edge) }
            var last = -1L
            var moving = true
            while (moving && session.phase == DragPhase.Carrying && host.wantsToScroll(edge)) {
                withFrameMillis { now ->
                    if (last >= 0) moving = host.autoScroll(now.milliseconds, (now - last).milliseconds, edge, speed, AutoScrollRamp)
                    last = now
                }
                // At its end: nothing more until the pointer moves.
                if (!moving) {
                    val at = session.pointer
                    snapshotFlow { session.pointer to session.phase }.first { (pointer, phase) -> pointer != at || phase != DragPhase.Carrying }
                    moving = true
                    last = -1L
                }
            }
        }
    }
    val position = remember(session) { Animatable(session.topLeft, Offset.VectorConverter) }
    val scale = remember(session) { Animatable(1f) }

    // How far off the page it is, 0 to 1: its shadow, and — where things may
    // move — the little it grows. It rises as the thing is picked up rather
    // than arriving with it, and comes back down as the thing goes to its
    // place, so it is flat by the time it gets there and the item it hands
    // over to, which casts no shadow, takes over without a step.
    val lift = session.lift
    // Not while it rides a list, as a tab rides its strip: there it is still on
    // the page, flat and its own size, sliding along among the others, and it
    // rises only as it comes away — and settles again boarding another.
    LaunchedEffect(session) {
        snapshotFlow { session.phase to session.riding }.collectLatest { (phase, riding) ->
            if (phase != DragPhase.Carrying) return@collectLatest
            lift.animateTo(if (riding == null) 1f else 0f, motion.springOrTween(motion.springSnappy))
        }
    }

    // Under the pointer while carried, with nothing between the two — or, carried
    // by a keyboard, over the place it would land, moving to each in turn.
    //
    // **Drawn where the session says, read as it is placed**, not chased by an
    // animation. As it came off the line of a list it rode, or boarded one, it
    // was flown to each new place on a spring, restarted by every move — and a
    // spring restarted each frame is always on its first frame, which moves
    // nothing. A tab pulled off its strip stood still where it came away, as
    // far behind the pointer as the pointer had gone, until the pointer
    // stopped. Reported 2026-10-21 for 0.5.0: *"dragging document tabs around
    // outside of the bar is still janky as hell"*. Now the jump alone is eased
    // away, on `easing`, and every move is drawn on the frame it arrives.
    var flying by remember(session) { mutableStateOf(session.keyboard) }
    LaunchedEffect(session) {
        if (!session.keyboard) {
            val snappy = motion.springSnappy
            val catchUp = spring(snappy.dampingRatio, snappy.stiffness, visibilityThreshold = Offset.VisibilityThreshold)
            snapshotFlow { session.jolts }.collectLatest {
                if (session.phase != DragPhase.Carrying) return@collectLatest
                val from = Snapshot.withMutableSnapshot {
                    val all = session.easing + session.jolt
                    session.easing = all
                    session.jolt = Offset.Zero
                    all
                }
                if (from == Offset.Zero) return@collectLatest
                animate(Offset.VectorConverter, from, Offset.Zero, animationSpec = catchUp) { value, _ -> session.easing = value }
            }
        } else {
            snapshotFlow { Triple(session.phase, session.target, session.index) }.collect { (phase, target, _) ->
                if (phase != DragPhase.Carrying) return@collect
                // A frame first, so the room a list made for it has been laid out.
                withFrameMillis { }
                val to = target?.landingBounds(session)?.topLeft
                    ?: target?.boundsInRoot()?.let { it.center - Offset(session.size.width / 2f, session.size.height / 2f) }
                    ?: session.source.homeBounds()?.topLeft
                    ?: return@collect
                session.pointer = to + session.grab
                if (motion.reduceMotion) position.snapTo(to) else position.animateTo(to, motion.springOrTween(motion.springSnappy))
            }
        }
    }
    // Then to where it ends, and the drop happens when it gets there. Keyed on
    // whether it has been let go, not on the phase: the phase moves on to
    // Dropped inside this, and must not restart it.
    //
    // **The drop happens as it settles, not after.** It waited for the flight's
    // spring to finish, and a spring with no threshold of its own runs on to a
    // hundredth of a pixel — on a short flight, a tenth of a second after the eye
    // has seen the card arrive; and for the shadow, on a spring of its own.
    // Reported as *"the final 'drop' happens just a fraction of a second too
    // late"*. So the flight ends within half a pixel of its place, and the
    // shadow finishes coming down beside the drop rather than ahead of it.
    val letGo = session.phase != DragPhase.Carrying
    LaunchedEffect(session, letGo) {
        if (!letGo) return@LaunchedEffect
        // From where it was last drawn, which the pointer no longer moves.
        if (!flying) {
            position.snapTo(session.drawn)
            flying = true
        }
        var settling: Job? = null
        if (motion.reduceMotion) {
            // No flight: to its place at once, and down with the drop.
            session.aim()?.let { position.snapTo(it.topLeft) }
            lift.snapTo(0f)
        } else {
            val snappy = motion.springSnappy
            val spring = spring(snappy.dampingRatio, snappy.stiffness, visibilityThreshold = Offset.VisibilityThreshold)
            // The same spring as the flight, so the two arrive together.
            settling = launch { lift.animateTo(0f, motion.springOrTween(snappy)) }
            if (session.aim() != null) {
                chase(position, session::aim, spring)
            } else {
                // Into a target with no place of its own: in, and gone.
                scale.animateTo(0.6f, motion.tweenFast())
            }
        }
        if (session.phase == DragPhase.Landing) {
            // The lists the move is between, as they were before it.
            val shown = listOfNotNull(session.fromList, session.landingOn?.list).distinct()
            val before = shown.map { it.keys() }
            host.land(session)
            if (shown.isNotEmpty()) {
                // Until the caller's lists show the move — at once, for one
                // that moves its items in `onDrop` — and then one layout of
                // them with the dropped item still held, so it appears in its
                // place without fading in and the carried thing hands over to it.
                // Timed on the frame clock, as the flight is.
                val since = withFrameMillis { it }
                while (shown.map { it.keys() } == before) {
                    if ((withFrameMillis { it } - since).milliseconds >= DropShownWithin) break
                }
                withFrameMillis { }
            }
        }
        // Flat before it hands over to the item in its place, which casts no
        // shadow: by now the last few hundredths of it, if anything.
        settling?.join()
        host.finish(session)
    }

    val edge = contrastEdge()
    val grows = !motion.reduceMotion
    Box(
        Modifier
            .offset { ((if (flying) position.value else session.drawn) - host.originOf()).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
            .size(with(density) { session.size.width.toDp() }, with(density) { session.size.height.toDp() })
            .graphicsLayer {
                val up = lift.value.coerceIn(0f, 1f)
                val grow = if (grows) 1f + (DragPreviewScale - 1f) * up else 1f
                val shrink = scale.value
                scaleX = grow * shrink
                scaleY = grow * shrink
                // It grows from where it is held, so what is under the pointer
                // stays there as it lifts: grown from its middle, a tab held at
                // its glyph slid out from under the pointer as it came away.
                // And it shrinks into a target from its middle, as it did.
                val w = size.width
                val h = size.height
                if (w > 0f && h > 0f) {
                    transformOrigin = TransformOrigin(session.grab.x / w, session.grab.y / h)
                    translationX = grow * (w / 2f - session.grab.x) * (1f - shrink)
                    translationY = grow * (h / 2f - session.grab.y) * (1f - shrink)
                }
                alpha = when {
                    // Its source has gone with the drop: the item in its new place is drawn instead.
                    session.source.gone -> 0f
                    scale.value < 1f -> (scale.value - 0.6f) / 0.4f
                    else -> 1f
                }
                shadowElevation = DragLift.toPx() * up
                shape = (session.shape as? LiftedShape)?.lifted(up) ?: session.shape
                clip = false
            }
            // Riding a list, it is drawn as it is there, its own edge and all.
            .then(if (edge != null && session.riding == null) Modifier.border(edge, (session.shape as? LiftedShape)?.lifted(1f) ?: session.shape) else Modifier)
            .drawWithContent { if (!session.source.gone) session.layer?.let { drawLayer(it) } },
    )
}

/**
 * Flies [position] to where [aim] says, following it if it moves on the way:
 * each frame the aim is read again, and a new one is flown to from where the
 * thing is, at the speed it already has.
 */
private suspend fun chase(
    position: Animatable<Offset, AnimationVector2D>,
    aim: () -> Rect?,
    spec: AnimationSpec<Offset>,
) = coroutineScope {
    var to = aim()?.topLeft ?: return@coroutineScope
    var flight = launch { position.animateTo(to, spec) }
    val watch = launch {
        while (true) {
            withFrameMillis { }
            val now = aim()?.topLeft ?: continue
            if ((now - to).getDistance() > 0.5f) {
                to = now
                // Takes over from the flight under way, keeping its velocity.
                flight = launch { position.animateTo(to, spec) }
            }
        }
    }
    // Over the moment the last flight arrives, not a frame later: a loop that
    // looked each frame saw it finished on the one after.
    while (true) {
        val current = flight
        current.join()
        if (current === flight) break
    }
    watch.cancel()
}

/**
 * A carried thing's shape that changes as it lifts off the page, [lifted] by 0
 * to 1: a document tab's bottom corners round as it comes away from its strip
 * and square again as it lands, where it joins the page below.
 */
internal interface LiftedShape : Shape {
    fun lifted(by: Float): Shape
}

/**
 * How far outside the list it rides a pointer goes before the thing is drawn
 * towards it at all: a fingertip's roll, or a hand drifting as it moves along.
 */
internal val RideHold: Dp = 8.dp

/** How far a thing riding a list is drawn off its line at most, neared and never reached, however hard it is pulled. */
internal val RideStretch: Dp = 6.dp

/**
 * How much of a pull the stretch takes up at first: about a third, and less
 * and less as it nears [RideStretch].
 */
private const val RideResistance = 0.35f

/**
 * The most of a list's tear-off its capture can be: three quarters, so a
 * thing just taken in has a quarter of the tear-off still to go before it
 * comes away again, and does not board and tear on alternate moves.
 */
private const val CaptureShare = 0.75f

/**
 * How long a dropped thing waits, in its place, for the lists to show the drop
 * before the drag ends anyway. A caller that moves its items in `onDrop` takes a
 * frame; one that moves them on a round trip somewhere gets this long.
 */
private val DropShownWithin: Duration = 500.milliseconds
