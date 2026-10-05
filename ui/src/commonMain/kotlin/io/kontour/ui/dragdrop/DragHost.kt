package io.kontour.ui.dragdrop

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.roundToInt
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
            if (home != null && next.fromIndex != null) {
                next.target = home
                home.place(next, next.fromIndex)
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
        retarget()
    }

    /** Whether something under the pointer would scroll if given frames to do it. */
    fun wantsToScroll(edge: Float): Boolean {
        val current = session ?: return false
        // A keyboard carry moves by places, not by where a pointer is held.
        if (current.phase != DragPhase.Carrying || current.keyboard) return false
        return regions.filter { it.enabled }.map { it.wantsToScroll(current.pointer, edge) }.any { it }
    }

    /**
     * Scrolls what is under the pointer, innermost first, one frame's worth.
     * Returns whether anything moved; a list already at its end does not.
     */
    fun autoScroll(now: Duration, frame: Duration, edge: Float, maxSpeed: Float, ramp: Duration): Boolean {
        val current = session ?: return false
        if (current.phase != DragPhase.Carrying) return false
        val under = regions
            .filter { it.enabled }
            .mapNotNull { region -> region.bounds()?.takeIf { it.contains(current.pointer) }?.let { region to it } }
            .sortedBy { it.second.width * it.second.height }
        for ((region, _) in under) {
            val step = region.step(current.pointer, edge, maxSpeed, ramp, now, frame)
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
        current.pointer = pointerInRoot
        retarget()
    }

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

    /** The landing has finished: the drop happens, once, and the drag is over. */
    fun finish(ended: DragSession) {
        if (session !== ended) return
        val landedOn = ended.landingOn
        if (ended.phase == DragPhase.Landing && landedOn != null) {
            landedOn.drop(ended)
            onLanding(true)
            announce(strings.dragDropped(ended.label, landedOn.label))
            refocus = ended.payload
        }
        ended.target?.leave()
        for (target in targets) target.statusState = DropStatus.Idle
        session = null
        onEnded()
    }

    /** Called from a host that is leaving, or a source that has gone. */
    fun abandon(source: SourceHandle) {
        val current = session ?: return
        if (current.source !== source) return
        current.target?.leave()
        for (target in targets) target.statusState = DropStatus.Idle
        session = null
        onEnded()
    }
}

internal enum class DragPhase { Carrying, Landing, Returning }

/** One drag: what is carried, from where, and where it is now. */
internal class DragSession(
    val type: DragType<*>,
    val payload: Any,
    val label: String,
    val source: SourceHandle,
    val fromList: DropListState<*>?,
    val fromIndex: Int?,
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
    var phase: DragPhase by mutableStateOf(DragPhase.Carrying)

    /** Where in the target list it would land, counted without the source. */
    var index: Int? by mutableStateOf(null)

    var landingOn: DropTarget? = null
    var destination: Rect? = null

    val topLeft: Offset get() = pointer - grab

    companion object {
        /** A session for a move that is never carried: a screen reader's, straight to its place. */
        fun probe(spec: SourceSpec) = DragSession(
            type = spec.type,
            payload = spec.payload,
            label = spec.label,
            source = SourceHandle(),
            fromList = spec.fromList,
            fromIndex = spec.fromIndex,
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
                fromIndex = what.fromIndex,
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
                                fromIndex = what.fromIndex,
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
    DragAnnouncer(host)
    val snaps = rememberDetentTicker(FeedbackIntent.Snap)
    val landing = rememberLandingFeedback()
    DisposableEffect(host, snaps, landing) {
        // A place for each snap, counted up: the ticker wants a detent that
        // changed, and a drag's places have no order of their own.
        var crossings = 0
        host.onSnap = { snaps.at(crossings++) }
        host.onLanding = { landed -> if (landed) landing.landed() else landing.refused() }
        host.onEnded = { snaps.reset() }
        onDispose {
            host.onSnap = {}
            host.onLanding = {}
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
    val lift = remember(session) { Animatable(0f) }
    LaunchedEffect(session) {
        lift.animateTo(1f, motion.springOrTween(motion.springSnappy))
    }

    // Under the pointer while carried, with nothing between the two — or, carried
    // by a keyboard, over the place it would land, moving to each in turn.
    LaunchedEffect(session) {
        if (!session.keyboard) {
            snapshotFlow { session.phase to session.topLeft }.collect { (phase, at) ->
                if (phase == DragPhase.Carrying) position.snapTo(at)
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
    // Then to where it ends, and the drop happens when it gets there.
    LaunchedEffect(session, session.phase) {
        if (session.phase == DragPhase.Carrying) return@LaunchedEffect
        val to = session.destination
        if (motion.reduceMotion) {
            // No flight to set it down during: down with the drop.
            lift.snapTo(0f)
        } else {
            coroutineScope {
                // The same spring as the flight, so the two arrive together.
                launch { lift.animateTo(0f, motion.springOrTween(motion.springSnappy)) }
                if (to != null) {
                    position.animateTo(to.topLeft, motion.springOrTween(motion.springSnappy))
                } else {
                    // Into a target with no place of its own: in, and gone.
                    scale.animateTo(0.6f, motion.tweenFast())
                }
            }
        }
        host.finish(session)
    }

    val edge = contrastEdge()
    val grows = !motion.reduceMotion
    Box(
        Modifier
            .offset { (position.value - host.originOf()).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
            .size(with(density) { session.size.width.toDp() }, with(density) { session.size.height.toDp() })
            .graphicsLayer {
                val up = lift.value.coerceIn(0f, 1f)
                val grow = if (grows) 1f + (DragPreviewScale - 1f) * up else 1f
                scaleX = grow * scale.value
                scaleY = grow * scale.value
                alpha = if (scale.value < 1f) (scale.value - 0.6f) / 0.4f else 1f
                shadowElevation = DragLift.toPx() * up
                shape = session.shape
                clip = false
            }
            .then(if (edge != null) Modifier.border(edge, session.shape) else Modifier)
            .drawWithContent { session.layer?.let { drawLayer(it) } },
    )
}

