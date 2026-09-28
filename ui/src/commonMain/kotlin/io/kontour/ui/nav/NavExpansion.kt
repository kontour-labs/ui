package io.kontour.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import kotlin.math.min

/**
 * How much room a navigation surface is currently giving its slots.
 *
 * A [NavRail]'s `header` and `footer`, a [ModalNavRail]'s, and a [NavBar]'s
 * slots all sit inside a surface whose width is not theirs to choose — 88dp of
 * rail one moment and 280dp the next, or a hundred-odd dp between two pairs of
 * destinations. Slot content that wants to
 * respond to that has had no way to know.
 *
 * It is deliberately not about search. A profile row that shows an avatar alone
 * when narrow and an avatar with a name when wide, a "New trip" button that
 * drops its label, a filter chip that becomes an icon — all the same question,
 * and all previously answerable only by the caller threading its own copy of the
 * rail's `expanded` flag down by hand.
 *
 * ```kotlin
 * NavRail(header = {
 *     val room = LocalNavExpansion.current
 *     Row { Avatar(user); if (room.expanded) Text(user.name) }
 * }) { … }
 * ```
 *
 * @property expanded Whether there is room for a label beside an icon. What a
 *   rail decides from its animated width, so it flips partway through an
 *   expansion rather than on the frame the toggle was pressed.
 * @property onSurface Whether there is a panel behind this slot. True in every
 *   navigation surface — a bar, a rail and a modal rail all have one; false for
 *   content outside them. It decides which way a control has to go to be
 *   *seen*: raised and light over content, recessed against a panel. A control
 *   that picks one and keeps it is invisible in the other, which is how a search
 *   pill ended up the same colour as the page it was drawn on.
 *
 * Constructible, because the surfaces in this package are not the only things
 * that can host a slot: an app with its own navigation chrome should be able to
 * publish the same answer, and content written against this local then works
 * inside it without knowing it is not a [NavRail].
 */
@Stable
class NavExpansion private constructor(
    val expanded: Boolean,
    private val fixed: Float,
    private val progressOf: (() -> Float)?,
    val onSurface: Boolean,
) {
    /** Room that does not change while this is provided: a bar's, a modal rail's. */
    constructor(expanded: Boolean, progress: Float, onSurface: Boolean) :
        this(expanded, progress, null, onSurface)

    /**
     * Room that follows a resize: [progress] is read wherever [NavExpansion.progress]
     * is, so a surface animating its width provides this once and its slots
     * read the width frame by frame — in a draw or layout lambda, without
     * recomposing — rather than the surface providing a new value, and
     * recomposing every reader, on every frame.
     */
    constructor(expanded: Boolean, progress: () -> Float, onSurface: Boolean) :
        this(expanded, Float.NaN, progress, onSurface)

    /**
     * 0 when the surface is at its narrowest and 1 at its widest, for content
     * that wants to interpolate rather than switch. Always 1 in a surface that
     * does not resize.
     *
     * Read it where it is used — in `graphicsLayer {}`, `drawBehind {}` or a
     * `layout {}` block — and a resize redraws the reader without recomposing
     * it. Read in composition, every frame of a resize recomposes the reader;
     * for a decision, `derivedStateOf { room.progress > x }` recomposes it once.
     */
    val progress: Float get() = progressOf?.invoke() ?: fixed

    // Compared by value, because [LocalNavExpansion] is: a surface that provides
    // the same room on its next composition must not look like new room to
    // every slot reading it. A resizing surface's room is the same room for as
    // long as it keeps the same source for its progress.
    override fun equals(other: Any?): Boolean =
        other is NavExpansion &&
            expanded == other.expanded &&
            onSurface == other.onSurface &&
            progressOf === other.progressOf &&
            fixed.toRawBits() == other.fixed.toRawBits()

    override fun hashCode(): Int {
        var result = expanded.hashCode()
        result = 31 * result + (progressOf?.hashCode() ?: fixed.hashCode())
        result = 31 * result + onSurface.hashCode()
        return result
    }

    internal companion object {
        /** A bar's: no room for labels, on the bar's surface. */
        val Bar = NavExpansion(expanded = false, progress = 1f, onSurface = true)

        /** A modal rail's: all the room there is, on a panel. */
        val Drawer = NavExpansion(expanded = true, progress = 1f, onSurface = true)
    }
}

/**
 * The room available to the slot being composed.
 *
 * Defaults to fully expanded, because content that is not inside a navigation
 * surface has whatever width its parent gave it and should not act cramped.
 */
val LocalNavExpansion = compositionLocalOf {
    NavExpansion(expanded = true, progress = 1f, onSurface = false)
}

/** Where an expanded [NavExpandingSlot] goes. */
enum class NavExpandPlacement {
    /**
     * Over the keyboard, where the finger already is.
     *
     * The panel stays near the thumb that opened it and its content fills the
     * screen above it, which is the arrangement of every map app's search. Its
     * cost is that the content runs *up* from the control, so the first item is
     * furthest from the eye's resting point.
     */
    AboveKeyboard,

    /**
     * At the top of the screen, keyboard below it.
     *
     * Content reads downwards from the control in the order a list is normally
     * read, and the control ends up where a browser or a settings screen puts
     * one. Its cost is the reach: it travels the height of the screen away from
     * the thumb that tapped it.
     */
    Top,
}

/** Metrics for [NavExpandingSlot]. */
object NavExpandingSlotDefaults {
    /** How far the expanded panel sits in from the window's sides. */
    val Margin = 16.dp

    /**
     * What a control in a navigation slot should stand on to be seen.
     *
     * Here rather than private because the choice is not [NavExpandingSlot]'s
     * alone: anything a caller puts in a `header`, `action` or `footer` faces the
     * same question, and the answer depends on which surface it landed in rather
     * than on what it is.
     */
    @Composable
    @ReadOnlyComposable
    fun containerColour(): Color =
        if (LocalNavExpansion.current.onSurface) {
            Theme.colours.surfaceSunken
        } else {
            Theme.colours.surface
        }

    /** The shadow that goes with [containerColour]. A recess casts none. */
    @Composable
    @ReadOnlyComposable
    fun shadow(): Shadow =
        if (LocalNavExpansion.current.onSurface) Shadow.None else Theme.elevation.low
}

/**
 * A control in a navigation surface that opens into a panel over the screen.
 *
 * ```kotlin
 * var open by remember { mutableStateOf(false) }
 *
 * NavExpandingSlot(
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     expandedContent = { FilterList(onPick = { open = false }) },
 * ) {
 *     +Tabler.Outline.Filter
 *     +"Filters"
 * }
 * ```
 *
 * [NavSearch] is this with a search field in it, and is the reason it exists —
 * but nothing here knows about searching. A filter, a "where to?" prompt, an
 * account switcher and a compose box are the same shape: a small thing in the
 * bar that needs the whole screen once it is being used.
 *
 * ### Why the panel is an overlay rather than a taller bar
 *
 * A navigation bar clears `WindowInsets.bottomEdges`, whose own documentation
 * says why: *"A navigation bar holds no text field and should stay where it is
 * while the user types."* A bar that rose with the keyboard would contradict the
 * inset it asks for. So the control stays where it is and the panel goes into
 * the [io.kontour.ui.overlay.OverlayHost], the same way
 * [io.kontour.ui.overlay.CommandPalette] does, where it is free to take the
 * keyboard inset ([NavExpandPlacement.AboveKeyboard]) or the status bar's
 * ([NavExpandPlacement.Top]) without the bar knowing anything about it.
 *
 * The host brings the rest for nothing: a scrim over the content, back and
 * escape closing it, focus trapped inside it, and its own entrance.
 *
 * @param containerColour The collapsed control's ground. Chosen from
 *   [LocalNavExpansion] by default: a recessed `surfaceSunken` on a navigation
 *   surface, where a raised pill would be the surface's own colour, and a raised
 *   `surface` anywhere else.
 * @param interactionSource Hoisted, to observe the control's press and hover.
 * @param expandedContent The panel. Given a `ColumnScope` so it can weight a
 *   list to fill what is left.
 * @param content The collapsed control's contents, in a row: `+icon` and
 *   `+"Filters"` in the control's own style.
 */
@Composable
fun NavExpandingSlot(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placement: NavExpandPlacement = NavExpandPlacement.AboveKeyboard,
    containerColour: Color = NavExpandingSlotDefaults.containerColour(),
    contentColour: Color = Theme.colours.content,
    shadow: Shadow = NavExpandingSlotDefaults.shadow(),
    dismissLabel: String = Theme.strings.close,
    interactionSource: MutableInteractionSource? = null,
    expandedContent: @Composable ColumnScope.() -> Unit,
    content: @Composable RowContentScope.() -> Unit,
) {
    // **The control becomes the panel.** A capsule leaves the control where it
    // sits and grows into the panel's field — or the panel itself — the field's
    // own contents fade in once it has arrived, and anything under them after
    // that; closing runs the same movement back into the control, and the panel
    // stays until it has landed there. It used to appear over the page with
    // nothing joining it to what had been tapped.
    val motion = Theme.motion
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    val morph = remember { NavMorph { progress.value } }
    SideEffect {
        morph.reduced = motion.reduceMotion
        morph.easing = motion.standard
    }
    LaunchedEffect(expanded, motion.reduceMotion) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            // Linear in time: each part of the movement brings its own easing.
            // Out faster than in, as every overlay leaves.
            animationSpec = tween(
                durationMillis = when {
                    motion.reduceMotion -> motion.fast
                    expanded -> motion.slow
                    else -> motion.slow * 3 / 4
                },
                easing = LinearEasing,
            ),
        )
    }
    val showing by remember { derivedStateOf { progress.value > 0f } }

    CollapsedControl(
        onClick = { onExpandedChange(true) },
        modifier = modifier
            .onPlaced { morph.pill = it }
            // Gone while it is the capsule, which is where it went; there under
            // reduced motion, where nothing leaves it.
            .graphicsLayer { alpha = if (!morph.reduced && progress.value > 0f) 0f else 1f },
        enabled = enabled,
        containerColour = containerColour,
        contentColour = contentColour,
        shadow = shadow,
        interactionSource = interactionSource,
        content = content,
    )

    // Composed only while open or closing, so the *host* is only looked up then.
    // `LocalOverlayHost` throws when there is none rather than returning null —
    // deliberately, because an overlay with nowhere to go is a bug that looks
    // like a missing feature — but a collapsed control has nowhere it wants to
    // go yet. Reading it unconditionally meant merely *placing* one of these in
    // a surface outside a host crashed on composition, which caught the
    // gallery's own rail specimen.
    if (expanded || showing) {
        ExpandedPanel(
            closing = !expanded,
            onCollapse = { onExpandedChange(false) },
            placement = placement,
            dismissLabel = dismissLabel,
            morph = morph,
            controlColour = containerColour,
            content = expandedContent,
        )
    }
}

/**
 * How far a slot's panel has come out of the control that opened it, and where
 * the two are: read frame by frame, in draw, by the capsule that joins them and
 * by the panel's contents as they fade in.
 *
 * The phases, as shares of the movement: the capsule travels over the first
 * [GeometryEnd] and eases into place; the panel's contents fade in from
 * [ContentFrom] once it is nearly there; what is under a field — results — from
 * [ResultsFrom]; and the capsule goes once the contents cover it. Closing runs
 * them backwards.
 */
@Stable
internal class NavMorph(private val progress: () -> Float) {
    var reduced: Boolean = false
    var easing: Easing = LinearEasing
    var pill: LayoutCoordinates? = null
    var panel: LayoutCoordinates? = null

    /** What the capsule becomes, where the panel names one — a search's field. */
    var target: LayoutCoordinates? = null
    var targetColour: Color = Color.Unspecified

    val geometry: Float get() = if (reduced) 1f else easing.transform(share(0f, GeometryEnd))
    val content: Float get() = if (reduced) progress().coerceIn(0f, 1f) else share(ContentFrom, ContentTo)
    val results: Float get() = if (reduced) 1f else share(ResultsFrom, 1f)
    val capsule: Float get() = if (reduced) 0f else 1f - share(CapsuleFrom, CapsuleTo)

    private fun share(from: Float, to: Float): Float = ((progress() - from) / (to - from)).coerceIn(0f, 1f)

    private companion object {
        const val GeometryEnd = 0.6f
        const val ContentFrom = 0.45f
        const val ContentTo = 0.7f
        const val ResultsFrom = 0.65f
        const val CapsuleFrom = 0.7f
        const val CapsuleTo = 0.85f
    }
}

/** The morph of the panel being composed, for what is in it to join in. */
internal val LocalNavMorph = staticCompositionLocalOf<NavMorph?> { null }

/** Hears each rectangle the capsule is drawn in: for tests, which cannot see a drawing. */
internal val LocalNavMorphProbe = staticCompositionLocalOf<((Rect) -> Unit)?> { null }

/**
 * The capsule between the control and the panel, drawn over the scrim and under
 * the panel: the control's shape and colour at the start, the field's (or a
 * panel's rounded box) at the end.
 */
@Composable
private fun MorphCapsule(morph: NavMorph, controlColour: Color) {
    val fallback = Theme.colours.surface
    val panelCorner = with(LocalDensity.current) { NavExpandingSlotDefaults.Margin.toPx() }
    val probe = LocalNavMorphProbe.current
    val layer = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { layer[0] = it }
            .drawBehind {
                val alpha = morph.capsule
                if (alpha <= 0f) return@drawBehind
                val here = layer[0]?.takeIf { it.isAttached } ?: return@drawBehind
                val pill = morph.pill?.takeIf { it.isAttached } ?: return@drawBehind
                val target = morph.target?.takeIf { it.isAttached } ?: morph.panel?.takeIf { it.isAttached }
                    ?: return@drawBehind
                val from = here.localBoundingBoxOf(pill, clipBounds = false)
                val to = here.localBoundingBoxOf(target, clipBounds = false)
                val g = morph.geometry
                val rect = lerp(from, to, g)
                val endCorner = if (morph.target != null) to.height / 2f else panelCorner
                val corner = (from.height / 2f + (endCorner - from.height / 2f) * g).coerceAtMost(min(rect.width, rect.height) / 2f)
                drawRoundRect(
                    color = lerp(controlColour, morph.targetColour.takeOrElse { fallback }, g),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(corner),
                    alpha = alpha,
                )
                probe?.invoke(rect)
            },
    )
}

/**
 * The control in the bar.
 *
 * A button, whatever it looks like. Putting a live text field here would take
 * focus and raise the keyboard *inside the navigation bar*, which is the thing
 * the expansion exists to avoid — and it would need its own handling for every
 * way a field can be reached, of which tapping is only the most obvious.
 * `Role.Button` is what it is: pressing it opens something.
 */
@Composable
private fun CollapsedControl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    containerColour: Color,
    contentColour: Color,
    shadow: Shadow,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowContentScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.capsule

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape, enabled = enabled)
            .pointerCursor(enabled = enabled)
            .clickable(
                interactionSource = interactions,
                // Wide, so it shrinks by the amount a wide button does rather
                // than the amount a glyph does — but it does shrink: it is a
                // control that opens something, and nothing else here says so.
                indication = kontourIndication(shape, io.kontour.ui.interaction.IndicationDefaults.PressScale),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        containerColour = containerColour,
        contentColour = if (enabled) contentColour else Theme.colours.contentDisabled,
        shadow = shadow,
        // A recess in the bar, `surfaceSunken` on `surface`: 1.13:1 at the
        // enhanced tier, so the control was a word floating in the bar.
        border = contrastEdge(),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = Theme.sizing.controlHeightMedium)
                .padding(horizontal = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(Theme.typography.bodyMedium) {
                contentScope(iconSize = Theme.sizing.iconMedium, maxLines = 1, content = content)
            }
        }
    }
}

/** The panel, in the overlay host. */
@Composable
private fun ExpandedPanel(
    closing: Boolean,
    onCollapse: () -> Unit,
    placement: NavExpandPlacement,
    dismissLabel: String,
    morph: NavMorph,
    controlColour: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    // Identity for the host's stack. `remember` rather than the content lambda,
    // which is a new object every recomposition and would push a fresh entry
    // each time instead of replacing the one already there.
    val key = remember { Any() }
    val latestPlacement by rememberUpdatedState(placement)
    val latestCollapse by rememberUpdatedState(onCollapse)
    val latestContent by rememberUpdatedState(content)
    val latestControlColour by rememberUpdatedState(controlColour)

    DisposableEffect(key) {
        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Dialog,
                scrim = ScrimStyle.Dimmed,
                dismissOnOutside = true,
                dismissOnBack = true,
                trapFocus = true,
                dismissLabel = dismissLabel,
                // It goes back into the control on its own clock and is taken
                // down when it has landed there, and the scrim follows it.
                managesOwnExit = true,
                visibility = { if (morph.reduced) morph.content else morph.geometry },
                onDismiss = { latestCollapse() },
                content = {
                    MorphCapsule(morph, latestControlColour)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // The whole point of the two placements. A panel over
                            // the keyboard has to clear the keyboard, which is
                            // `sheetEdges`; one at the top has to clear the status
                            // bar and the cutout, which is `topEdges`. Both tokens
                            // already existed and already meant this.
                            .windowInsetsPadding(
                                when (latestPlacement) {
                                    NavExpandPlacement.AboveKeyboard ->
                                        WindowInsets.sheetEdges
                                    NavExpandPlacement.Top -> WindowInsets.topEdges
                                }
                            )
                            .padding(NavExpandingSlotDefaults.Margin),
                        contentAlignment = when (latestPlacement) {
                            NavExpandPlacement.AboveKeyboard -> Alignment.BottomCenter
                            NavExpandPlacement.Top -> Alignment.TopCenter
                        },
                    ) {
                        CompositionLocalProvider(LocalNavMorph provides morph) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPlaced { morph.panel = it }
                                    .graphicsLayer { alpha = morph.content },
                                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                                content = latestContent,
                            )
                        }
                    }
                },
            )
        )
        onDispose { host.hide(key) }
    }

    // Going back into the control is the panel on its way out: the page is
    // the reader's again from the moment it starts, not once it has landed.
    LaunchedEffect(closing) {
        if (closing) host.letGo(key) else host.takeBack(key)
    }
}

