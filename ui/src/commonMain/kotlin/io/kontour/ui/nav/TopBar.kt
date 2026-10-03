package io.kontour.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import io.kontour.ui.a11y.highContrast
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyListState
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.CentredBar
import io.kontour.ui.foundation.CentredBarTitle
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.LocalContainerColour
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.BarSurface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.theme.Theme
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/** How much room a [TopBar] takes, and where its title sits. */
enum class TopBarStyle {
    /** One line, title on the leading side. The default. */
    Small,

    /**
     * One line, title centred on the bar — or, where the controls leave it too
     * little room to be centred, exactly [Small].
     *
     * For a screen with actions on both sides, where a leading title would sit
     * off-balance. A title that does not fit centred is not squeezed or pushed
     * off-centre: it starts against the leading controls, as a [Small] one does.
     */
    Centred,

    /**
     * Two lines: actions on the first, a large title on the second.
     *
     * Collapses to [Small] as the content scrolls: the bar grows shorter, and
     * the title travels up into the first line, shrinking as it goes, to where
     * a [Small] bar's title sits — beside the back button when there is one.
     * The bar does not own the scroll state: hand it a
     * [rememberTopBarCollapseState] and put that state's connection on the
     * content, or pass `collapseProgress` yourself. For a screen whose title is
     * the thing the user came for, like a route or a stop.
     */
    Large,

    /**
     * [Large] over a picture: a place, a route, a photo, passed as `TopBar`'s
     * `image`, with the title at the foot of it.
     *
     * **The picture runs under the status bar**, to the top of the window, and
     * the controls and the title keep clear of the bar as any `TopBar`'s do.
     * Open, the bar is `expandedHeight` tall with the status bar —
     * [TopBarDefaults.HeroHeight] unless you say — or taller if its title needs
     * it, and the title rests at the bottom, over the picture.
     *
     * **The picture runs under the title and fades into the bar's colour** at
     * its foot: untouched down to a little above the title, then eased into the
     * bar's background, so the title and its subtitle stand over the fade in the
     * bar's own ink and read on any picture — and the page below starts where
     * the picture ends, with no line between them. At high contrast the fade is a
     * solid band of the bar's colour under the words. The back button and the
     * actions each sit on a disc of the bar's colour while the picture is behind
     * them.
     *
     * **Collapsing**, it is [Large]'s collapse: the title travels into the first
     * line, and the picture rises at half the pace of the bar's foot and fades
     * over the second half of the way. Collapsed, it is a [Small] bar to the
     * pixel. Under reduced motion the picture does not rise; it fades where it
     * is.
     *
     * Without an `image` it is a [Large] bar that opens to the same height, so a
     * screen whose picture is sometimes missing does not jump.
     */
    Hero,
}

/** What a [TopBar] takes by default. */
object TopBarDefaults {
    val Height: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.topBarHeight
    val LargeHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.topBarLargeHeight

    /** A [TopBarStyle.Hero] bar open: its picture's height, the status bar included. */
    val HeroHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.topBarHeroHeight
}

/**
 * A screen's title and its actions.
 *
 * ```kotlin
 * TopBar(
 *     onBack = navController::popBackStack,
 *     actions = { IconButton(icon = Tabler.Outline.Star, contentDescription = "Favourite", onClick = ::favourite) },
 * ) { +"Perth Underground" }
 * ```
 *
 * **Not a navigation surface.** The app's destinations live in a [NavBar] at the
 * bottom or a [NavRail] on the leading edge; this bar carries the current
 * screen's identity and the actions that apply to it. Putting destinations up
 * here is a website's layout, and on a phone it puts them as far from the thumb
 * as the screen allows.
 *
 * The title is marked as a heading, so a screen reader can jump straight to it,
 * and the back button gets a real label rather than a bare glyph.
 *
 * @param onBack Draws a back button. Give it the same thing the system back
 *   gesture does — a visible back that goes somewhere different from the gesture
 *   is worse than no visible back.
 * @param containerColour The ground of whatever it sits on — the window's, or a
 *   sheet's inside a sheet — so a bar at the top of a page in a sheet is not a
 *   band of a different colour.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    style: TopBarStyle = TopBarStyle.Small,
    onBack: (() -> Unit)? = null,
    backLabel: String = Theme.strings.back,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /**
     * The picture behind a [TopBarStyle.Hero] bar, filling it from edge to edge
     * and under the status bar: crop it rather than fit it. Decorative, and
     * hidden from screen readers: say what matters in the title. Only `Hero`
     * draws it.
     */
    image: (@Composable () -> Unit)? = null,
    containerColour: Color = LocalContainerColour.current.takeOrElse { Theme.colours.background },
    contentColour: Color = Theme.colours.content,
    showDivider: Boolean = false,
    /**
     * Whether content passing under the bar fades out beneath it, rather than
     * stopping square at its foot: the bar's colour runs on past the foot and
     * thins to nothing 24dp below, over whatever is there. Any style, and a
     * collapsing bar's fade follows its foot.
     *
     * Only content *under* the bar shows it, so put the bar over the content —
     * a [io.kontour.ui.adaptive.Scaffold] does, handing the content the bar's
     * height to pad by — rather than above it in a `Column`. The fade is drawn,
     * not touched: a tap there reaches the content. Not at high contrast, where
     * content stops square at the bar's foot.
     */
    fadeUnder: Boolean = false,
    /**
     * How tall a [TopBarStyle.Hero] or [TopBarStyle.Large] bar is open, the
     * status bar included. Unspecified is the style's own:
     * [TopBarDefaults.HeroHeight] for `Hero`, and what its lines need for
     * `Large`. A floor, not a size: a title that needs more room gets it, and
     * rests at the foot of the extra. Ignored by `Small` and `Centred`.
     */
    expandedHeight: Dp = Dp.Unspecified,
    /**
     * 0 when the content is at the top, 1 when it has scrolled far enough to
     * collapse a [TopBarStyle.Large] or [TopBarStyle.Hero] bar. Ignored by the
     * other styles, and by these when [collapseState] is given.
     */
    collapseProgress: Float = 0f,
    /**
     * A [TopBarStyle.Large] or [TopBarStyle.Hero] bar's collapse, driven by the content's scroll
     * through its [TopBarCollapseState.nestedScrollConnection]. The usual way
     * to collapse one: the bar takes the scroll until it has collapsed, so the
     * content moves with the finger while the bar shrinks above it.
     */
    collapseState: TopBarCollapseState? = null,
    /**
     * What the bar's *content* keeps clear of. The status bar and the display
     * cutout by default; pass `WindowInsets(0)` when something above has already
     * accounted for them, or when this bar is not at the top of the window.
     */
    windowInsets: WindowInsets = WindowInsets.topEdges,
    /**
     * The bar's title, and its subtitle. `+` fills the title.
     *
     * A collected builder rather than a composable slot, and
     * [TopBarStyle.Large] is why: it renders the title *twice* — large, and in
     * the small title's type for the end of its journey into the first line —
     * so the content has to be something the component can compose in two
     * places at two sizes. A slot that emitted where it was written could only
     * be in one of them.
     */
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val outer = if (fadeUnder && !highContrast()) modifier.fadeBelow(containerColour) else modifier
    if (style == TopBarStyle.Hero && image != null) {
        HeroBar(
            image = image,
            slots = slots,
            modifier = outer,
            onBack = onBack,
            backLabel = backLabel,
            navigation = navigation,
            actions = actions,
            containerColour = containerColour,
            contentColour = contentColour,
            showDivider = showDivider,
            open = expandedHeight.takeOrElse { TopBarDefaults.HeroHeight },
            collapseProgress = collapseProgress,
            collapseState = collapseState,
            windowInsets = windowInsets,
        )
        return
    }
    // How far a Large bar, or a Hero one without its picture, opens at least:
    // the height asked for, less the inset above, which the bar is also that
    // much taller by. Read only when there is one, so an ordinary Large bar does
    // not take on a read of the insets.
    val open = when {
        style == TopBarStyle.Hero -> expandedHeight.takeOrElse { TopBarDefaults.HeroHeight }
        style == TopBarStyle.Large && expandedHeight.isSpecified -> expandedHeight
        else -> Dp.Unspecified
    }
    val floor = if (open.isSpecified) {
        (open - with(LocalDensity.current) { windowInsets.getTop(this).toDp() }).coerceAtLeast(0.dp)
    } else {
        0.dp
    }
    // A bar surface: a tap on the bar between its title and its actions is a
    // tap on nothing, and goes no further — not to a list scrolled under the
    // bar, and not to a clickable container round it. See [BarSurface].
    BarSurface(modifier = outer.fillMaxWidth(), containerColour = containerColour, contentColour = contentColour) {
        // Inside the surface, so the container colour still reaches the top of the
        // window and the status bar sits on the bar rather than on a strip of
        // whatever is behind it.
        Column(Modifier.windowInsetsPadding(windowInsets)) {
            when (style) {
                // `CentredBar` rather than a `Row`, and only it can do this: a
                // weighted title is centred in the space its controls left over,
                // which is the middle of the bar only when both sides are the
                // same width. With a back button and no actions — or actions and
                // no back button — the title sits off-centre, and it *moves* the
                // moment the back button appears or goes.
                TopBarStyle.Small, TopBarStyle.Centred -> CentredBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TopBarDefaults.Height)
                        .padding(horizontal = Theme.spacing.xs),
                    centred = style == TopBarStyle.Centred,
                    leading = { Leading(onBack, backLabel, navigation) },
                    title = {
                        // Centred when the bar could centre it and started when it
                        // could not — see `CentredBarTitle` — so a Centred bar
                        // whose title does not fit is exactly a Small one.
                        CentredBarTitle(modifier = Modifier.padding(horizontal = Theme.spacing.xs)) {
                            TitleLines(slots = slots)
                        }
                    },
                    trailing = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions?.invoke(this)
                        }
                    },
                )

                TopBarStyle.Large, TopBarStyle.Hero -> LargeBar(
                    progress = { collapseState?.progress ?: collapseProgress },
                    onMeasured = { shrink, travel -> collapseState?.measured(shrink, travel) },
                    // The first line is left out when it would be empty, so the
                    // large title takes the space the back button would have
                    // had: a bar with nothing to put on that line was drawing
                    // 56dp of nothing above its own title.
                    hasControls = onBack != null || navigation != null || actions != null,
                    leading = { Leading(onBack, backLabel, navigation) },
                    actions = actions,
                    slots = slots,
                    minExpanded = floor,
                )
            }

            if (showDivider) HorizontalDivider()
        }
    }
}

/**
 * A bar's [colour] run on past its foot and thinned to nothing over
 * [FadeUnderLength], over whatever is under it there. See `TopBar`'s `fadeUnder`.
 *
 * Eased out rather than straight, as a hero's fade is, so there is no line
 * where it ends: most of the colour for the first quarter, none by the last.
 */
private fun Modifier.fadeBelow(colour: Color): Modifier = drawWithCache {
    val length = FadeUnderLength.toPx()
    val brush = Brush.verticalGradient(
        0f to colour,
        0.25f to colour.copy(alpha = colour.alpha * 0.84f),
        0.5f to colour.copy(alpha = colour.alpha * 0.5f),
        0.75f to colour.copy(alpha = colour.alpha * 0.16f),
        1f to colour.copy(alpha = 0f),
        startY = size.height,
        endY = size.height + length,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush, topLeft = Offset(0f, size.height), size = Size(size.width, length))
    }
}

/** How far below a bar's foot `fadeUnder` reaches. */
private val FadeUnderLength = 24.dp

/**
 * A [TopBarStyle.Hero] bar: [LargeBar] over [image], the picture fading into
 * the bar's colour under the title. See the style for what it does; this is how.
 *
 * Three layers, laid out as one: the picture, the height of the open bar
 * whatever the bar is now, so a scroll moves it and never lays it out again; the
 * fade, drawn from where the title is; and the bar itself, which is a
 * [TopBarStyle.Large] bar told how far to open and where its title went.
 */
@Composable
private fun HeroBar(
    image: @Composable () -> Unit,
    slots: ListItemScope,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    backLabel: String,
    navigation: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    containerColour: Color,
    contentColour: Color,
    showDivider: Boolean,
    open: Dp,
    collapseProgress: Float,
    collapseState: TopBarCollapseState?,
    windowInsets: WindowInsets,
) {
    val progress = { collapseState?.progress ?: collapseProgress }
    val geometry = remember { HeroGeometry() }
    val insetTop = with(LocalDensity.current) { windowInsets.getTop(this).toDp() }
    val backing = remember(containerColour) { HeroBacking(containerColour) { 1f - progress() } }
    val solid = highContrast()
    val reduce = Theme.motion.reduceMotion
    val rise = HeroFadeRise
    // How much of the picture there is, from 1 open to 0 collapsed.
    val shown = { 1f - smoothstep(HeroFadeFrom, HeroFadeTo, progress().coerceIn(0f, 1f)) }

    BarSurface(modifier = modifier.fillMaxWidth(), containerColour = containerColour, contentColour = contentColour) {
        Layout(
            contents = listOf(
                { Box(Modifier.clearAndSetSemantics {}) { image() } },
                // Under the title: from a little above it to the bar's foot, the
                // picture fading into the bar's own colour.
                {
                    Box(
                        Modifier.drawBehind {
                            if (shown() <= 0f) return@drawBehind
                            val p = progress().coerceIn(0f, 1f)
                            val inset = windowInsets.getTop(this)
                            val titleTop = inset + geometry.textTopOpen +
                                (geometry.textTopCollapsed - geometry.textTopOpen) * p
                            val foot = size.height - geometry.shrink * p
                            // Every rect below runs on to the bottom of this
                            // box, under the bar's foot whichever way it was
                            // rounded. They stopped at the foot unrounded, which
                            // left up to half the bar's last line of pixels
                            // over the picture, every other few pixels of
                            // scroll — reported as a line that flashes as you
                            // scroll.
                            if (solid) {
                                // Nothing of the picture behind the words.
                                if (foot > titleTop) {
                                    drawRect(containerColour, topLeft = Offset(0f, titleTop), size = Size(size.width, size.height - titleTop))
                                }
                                return@drawBehind
                            }
                            val from = (titleTop - rise.toPx()).coerceAtLeast(0f)
                            if (foot <= from) return@drawBehind
                            // Eased in, so the fade has no line where it starts:
                            // barely begun halfway down to the title, most of the
                            // way by the title's top, and the bar's colour alone
                            // from partway between the title's top and the foot —
                            // where the subtitle is, and where the page begins.
                            val span = foot - from
                            val atTitle = ((titleTop - from) / span).coerceIn(0.05f, 1f)
                            val whole = atTitle + (1f - atTitle) * HeroFadeWholeAt
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to containerColour.copy(alpha = 0f),
                                    atTitle * 0.5f to containerColour.copy(alpha = HeroFadeHalfway),
                                    atTitle to containerColour.copy(alpha = HeroFadeAtTitle),
                                    whole to containerColour,
                                    1f to containerColour,
                                    startY = from,
                                    endY = foot,
                                ),
                                topLeft = Offset(0f, from),
                                size = Size(size.width, size.height - from),
                            )
                        },
                    )
                },
                {
                    Column(Modifier.windowInsetsPadding(windowInsets)) {
                        CompositionLocalProvider(LocalHeroBacking provides backing) {
                            LargeBar(
                                progress = progress,
                                onMeasured = { shrink, travel -> collapseState?.measured(shrink, travel) },
                                hasControls = onBack != null || navigation != null || actions != null,
                                leading = { Leading(onBack, backLabel, navigation) },
                                actions = actions,
                                slots = slots,
                                minExpanded = (open - insetTop).coerceAtLeast(0.dp),
                                hero = geometry,
                            )
                        }
                        if (showDivider) HorizontalDivider()
                    }
                },
            ),
            modifier = Modifier.fillMaxWidth().clipToBounds(),
        ) { (picturePart, fadePart, barPart), constraints ->
            val bar = barPart.first().measure(constraints)
            val p = progress().coerceIn(0f, 1f)
            val height = (bar.height + geometry.shrink * p).roundToInt().coerceAtLeast(bar.height)
            val picture = picturePart.first().measure(Constraints.fixed(bar.width, height))
            val fade = fadePart.first().measure(Constraints.fixed(bar.width, height))
            layout(bar.width, bar.height) {
                picture.placeWithLayer(0, 0) {
                    val now = progress().coerceIn(0f, 1f)
                    // Rises at half the pace of the bar's foot, which reads as
                    // depth; under reduced motion it stays and only fades.
                    translationY = if (reduce) 0f else -geometry.shrink * now * HeroParallax
                    alpha = 1f - smoothstep(HeroFadeFrom, HeroFadeTo, now)
                }
                fade.place(0, 0)
                bar.place(0, 0)
            }
        }
    }
}

/** Where a hero's title block sits in its bar, open and collapsed, and how far the bar shrinks. */
internal class HeroGeometry {
    var textTopOpen: Float = 0f
    var textTopCollapsed: Float = 0f
    var shrink: Float = 0f
}

/**
 * A disc of [colour] behind an icon button, at [alpha] — a [TopBarStyle.Hero]
 * bar's controls over its picture. Read by the icon button itself.
 */
internal class HeroBacking(val colour: Color, val alpha: () -> Float)

/** Set by a [TopBarStyle.Hero] bar round its controls. */
internal val LocalHeroBacking = staticCompositionLocalOf<HeroBacking?> { null }

/** How fast a hero's picture rises against its bar's foot. */
private const val HeroParallax = 0.5f

/** How far above a hero's title its picture starts fading into the bar. */
private val HeroFadeRise = 96.dp

/**
 * How far a hero's picture has faded into the bar: halfway down to the title,
 * at the title's top, and where it is the bar's colour alone — a share of the
 * way from the title's top to the foot. The title is large type in the bar's
 * own ink and needs 3:1: at its top 40% of the picture is left, which gives
 * about 5:1 over the worst of pictures in either scheme, and below that it only
 * gets better. The subtitle, small type, is on the bar's colour alone.
 */
private const val HeroFadeHalfway = 0.15f
private const val HeroFadeAtTitle = 0.6f
private const val HeroFadeWholeAt = 0.5f

/** Over which part of the collapse a hero's picture fades out. */
private const val HeroFadeFrom = 0.5f
private const val HeroFadeTo = 0.9f

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * A [TopBarStyle.Large] bar, at [progress] of the way to collapsed.
 *
 * **One title, moved.** It used to be two — a large one that faded and slid up
 * sixteen pixels, and a small one that faded in on the first line — with the bar
 * the same height throughout. Asked for instead: the title animates into the top
 * rather than fading, with and without a back button, and the bar grows shorter
 * as it does.
 *
 * So the large title is laid out once and *carried*: its start travels from the
 * large title's margin to where a [TopBarStyle.Small] bar's title starts — after
 * the back button, when there is one, or at the same margin when there is not,
 * which leaves it moving straight up — and its baseline from its own line to
 * the first line's, shrinking from the large type's size to the small's. The bar
 * shrinks from the height of both lines to one line's in step.
 *
 * Scaling the large type down ends in the large type's weight and tracking at
 * the small size, which is not the small title: [TopBarStyle.Large] is heavier
 * and tighter. So over the last part of the journey the small title takes over,
 * drawn at *the same size and on the same baseline* as the large one at that
 * moment — the two are the same glyphs within a weight — and the handover reads
 * as the weight easing rather than as one title fading into another. At the end
 * it is exactly the small title a [TopBarStyle.Small] bar draws, where it draws
 * it.
 *
 * **The subtitle travels too**, under the title all the way, and lands where a
 * [TopBarStyle.Small] bar puts its supporting line: under the small title, the
 * two centred in the bar together. It used to fade out early, and the collapsed
 * bar lost it — *"can we also keep the subtitle text? we basically want it to
 * morph into a small top bar"*. It is the same type at both ends, so it does not
 * scale; it is laid out twice, at the large title's width and at the small
 * title's, and the two hand over with the titles, which shows only where the
 * narrower one had to cut the line short.
 *
 * **A large title's second line** does not travel. A title the large type wraps
 * arrives as two lines where the small title has one, and the handover showed
 * both — the small title's one line with the large one's second line hanging
 * under it, the same word twice. The first line of the large title is the start
 * of the small one, glyph over glyph; the second fades out early, cut at the gap
 * between the lines, as the rest of the small title fades in beside the first
 * line. The subtitle rises into the room it leaves.
 *
 * Laid out rather than composed from rows, because the positions are all read
 * from each other — the small title's start from the back button's width, its
 * room from the actions', the height from the title's lines — and [progress] is
 * read here, in layout, so a scroll relays the bar out without recomposing it.
 */
@Composable
private fun LargeBar(
    progress: () -> Float,
    onMeasured: (shrink: Float, travel: Float) -> Unit,
    hasControls: Boolean,
    leading: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
    slots: ListItemScope,
    /**
     * The least the bar is open: a [TopBarStyle.Hero] bar's picture's height, or
     * the height asked of a [TopBarStyle.Large] one, less the inset above. The
     * title then rests at the bottom of it.
     */
    minExpanded: Dp = 0.dp,
    /** Where the title block sits, open and collapsed, for a hero's fade. */
    hero: HeroGeometry? = null,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val large = Theme.typography.displaySmall
    val small = Theme.typography.titleMedium
    // The small title's size as a share of the large one's: 16 over 32.
    val ratio = small.fontSize.value / large.fontSize.value
    val rowHeight = TopBarDefaults.Height
    // The least scroll a collapse is spread over. See `TopBarCollapseState.travel`.
    val leastTravel = TopBarDefaults.LargeHeight - TopBarDefaults.Height
    val gutter = Theme.spacing.xs
    val margin = Theme.spacing.md
    val below = Theme.spacing.sm
    val title = slots.label
    val subtitle = slots.supporting
    // Where the large title's first line ends, measured below and drawn from.
    val cut = remember { LineCut() }

    Layout(
        contents = listOf(
            { leading() },
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions?.invoke(this)
                }
            },
            {
                if (title != null) {
                    Box(
                        Modifier
                            .semantics { heading() }
                            // Its own buffer, so the second line can be taken
                            // out of the title's pixels rather than painted over.
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val gone = secondLineGone(progress())
                                if (gone > 0f && cut.y > 0f && cut.y < size.height) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(0f, cut.y),
                                        size = Size(size.width, size.height - cut.y),
                                        alpha = gone,
                                        blendMode = BlendMode.DstOut,
                                    )
                                }
                            },
                    ) {
                        ProvideTextStyle(large) { ContentSlot(maxLines = 2, content = title) }
                    }
                }
            },
            {
                // Said once, by the large title: this one is the same words.
                if (title != null) {
                    Box(Modifier.clearAndSetSemantics {}) {
                        ProvideTextStyle(small) { ContentSlot(maxLines = 1, overflow = TextOverflow.Ellipsis, content = title) }
                    }
                }
            },
            {
                if (subtitle != null) {
                    Box {
                        ProvideContentColour(Theme.colours.contentMuted) {
                            ProvideTextStyle(Theme.typography.bodySmall) {
                                ContentSlot(maxLines = 1, overflow = TextOverflow.Ellipsis, content = subtitle)
                            }
                        }
                    }
                }
            },
            {
                // The same line at the small title's width. Said once, by the
                // one above.
                if (subtitle != null) {
                    Box(Modifier.clearAndSetSemantics {}) {
                        ProvideContentColour(Theme.colours.contentMuted) {
                            ProvideTextStyle(Theme.typography.bodySmall) {
                                ContentSlot(maxLines = 1, overflow = TextOverflow.Ellipsis, content = subtitle)
                            }
                        }
                    }
                }
            },
        ),
        modifier = Modifier.fillMaxWidth().clipToBounds(),
    ) { parts, constraints ->
        val (leadingPart, actionsPart, largePart, smallPart, subtitlePart) = parts
        val smallSubtitlePart = parts[5]
        val width = constraints.maxWidth
        val row = rowHeight.roundToPx()
        val gutterPx = gutter.roundToPx()
        val marginPx = margin.roundToPx()
        val loose = Constraints(maxWidth = width)
        val lead = leadingPart.firstOrNull()?.measure(loose.copy(maxHeight = row))
        val acts = actionsPart.firstOrNull()?.measure(loose.copy(maxHeight = row))
        val leadWidth = lead?.width ?: 0
        val actsWidth = acts?.width ?: 0

        // Where a Small bar's title goes: after the back button and a gutter
        // each side of it, or a margin in; and up to a gutter short of the
        // actions. See `CentredBar`, which lays the Small bar out.
        val smallStart = if (leadWidth > 0) gutterPx + leadWidth + gutterPx else marginPx
        val smallEnd = width - gutterPx - actsWidth - gutterPx
        val largeWidth = (width - marginPx * 2).coerceAtLeast(0)
        val smallWidth = (smallEnd - smallStart).coerceAtLeast(0)
        val big = largePart.firstOrNull()?.measure(Constraints(maxWidth = largeWidth))
        val tiny = smallPart.firstOrNull()?.measure(Constraints(maxWidth = smallWidth))
        val sub = subtitlePart.firstOrNull()?.measure(Constraints(maxWidth = largeWidth))
        val subSmall = smallSubtitlePart.firstOrNull()?.measure(Constraints(maxWidth = smallWidth))

        // Collapsed, it is a Small bar: the title and the subtitle under it,
        // centred in the bar together, and the bar at least a row tall.
        val stack = (tiny?.height ?: 0) + (subSmall?.height ?: 0)
        val collapsed = maxOf(row, stack)
        val top = if (hasControls) row else 0
        val needed = top + (big?.height ?: 0) + (sub?.height ?: 0) + below.roundToPx()
        val expanded = maxOf(needed, collapsed, minExpanded.roundToPx())
        // A hero's room above the title: the title rests at the bottom of the
        // picture, over the part of it that fades into the bar.
        val textTop = top + (expanded - maxOf(needed, collapsed)).coerceAtLeast(0)
        val shrink = (expanded - collapsed).toFloat()
        onMeasured(shrink, maxOf(shrink, leastTravel.toPx()))
        if (hero != null) {
            hero.textTopOpen = textTop.toFloat()
            hero.textTopCollapsed = Alignment.CenterVertically.align(stack, collapsed).toFloat()
            hero.shrink = shrink
        }
        val p = progress().coerceIn(0f, 1f)
        val height = (expanded + (collapsed - expanded) * p).roundToInt()

        layout(width, height) {
            // The first line: a row, and the whole collapsed bar by the end.
            val line = (row + (collapsed - row) * p).roundToInt()
            lead?.placeRelative(gutterPx, (line - lead.height) / 2)
            acts?.placeRelative(width - gutterPx - actsWidth, (line - acts.height) / 2)

            // Anchored on the first baseline, so the two titles meet line on
            // line whatever their line heights; a title that is not text falls
            // back to its own height.
            fun baselineOf(placeable: Placeable): Int =
                placeable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: placeable.height
            val bigBaseline = big?.let(::baselineOf) ?: 0
            val tinyBaseline = tiny?.let(::baselineOf) ?: 0
            // A second line starts a line below the first; its tallest glyphs
            // reach up to about three quarters of the type size above its
            // baseline, and the first line's descenders down to about a
            // quarter below. A third of the size under the first baseline is
            // clear of both.
            cut.y = if (big != null && big.height > large.lineHeight.toPx() * 1.5f) {
                bigBaseline + large.fontSize.toPx() / 3f
            } else {
                0f
            }
            val fromBaseline = textTop + bigBaseline
            // Rounded as `CentredBar` rounds it, so it lands on the Small bar's pixel.
            val stackTop = Alignment.CenterVertically.align(stack, collapsed)
            val toBaseline = stackTop + tinyBaseline

            val start = marginPx + (smallStart - marginPx) * p
            val baseline = fromBaseline + (toBaseline - fromBaseline) * p
            // The size the title is drawn at, as a share of the large type.
            val scale = 1f + (ratio - 1f) * p
            // The start edge stays put as the title shrinks: the leading one.
            val origin = if (rtl) TransformOrigin(1f, 0f) else TransformOrigin(0f, 0f)
            val handover = ((p - HandoverFrom) / (HandoverTo - HandoverFrom)).coerceIn(0f, 1f)

            big?.placeRelativeWithLayer(start.roundToInt(), (baseline - bigBaseline * scale).roundToInt()) {
                scaleX = scale
                scaleY = scale
                transformOrigin = origin
                alpha = 1f - handover
            }
            tiny?.let {
                val tinySize = scale / ratio
                it.placeRelativeWithLayer(start.roundToInt(), (baseline - tinyBaseline * tinySize).roundToInt()) {
                    scaleX = tinySize
                    scaleY = tinySize
                    transformOrigin = origin
                    alpha = handover
                }
            }
            // Under what still shows of the large title — both of its lines,
            // then the first as the second fades — and corrected along the way
            // onto the small subtitle's place, which is under the small title.
            fun shown(gone: Float): Float {
                val whole = (big?.height ?: 0).toFloat()
                return if (cut.y > 0f) whole + (cut.y - whole) * gone else whole
            }
            val underTitle = baseline - bigBaseline * scale + shown(secondLineGone(p)) * scale
            val underTitleCollapsed = toBaseline - bigBaseline * ratio + shown(1f) * ratio
            val landing = stackTop + (tiny?.height ?: 0)
            val subTop = if (big != null) {
                underTitle + (landing - underTitleCollapsed) * p
            } else {
                textTop + (landing - textTop) * p
            }

            // One of the two is drawn throughout when the line fits both
            // widths: two copies of a line half-faded over each other read
            // lighter than either. They hand over only where the narrower one
            // had to cut the line short.
            val same = sub != null && subSmall != null && sub.width == subSmall.width && sub.height == subSmall.height
            sub?.placeRelativeWithLayer(start.roundToInt(), subTop.roundToInt()) {
                alpha = if (same) 1f else 1f - handover
            }
            subSmall?.placeRelativeWithLayer(start.roundToInt(), subTop.roundToInt()) {
                alpha = if (same) 0f else handover
            }
        }
    }
}

/**
 * Over which part of the collapse the small title takes over from the large one.
 * The second half, so the two are close in size when they swap: the difference
 * between them is weight and tracking, and the nearer the size, the less it
 * shows. Finished a little before the end, so the last of the travel is the
 * small title's own.
 */
private const val HandoverFrom = 0.4f
private const val HandoverTo = 0.95f

/**
 * When a large title's second line fades, as shares of the collapse. It
 * overlaps the handover's start, and that is deliberate: the small title's
 * words arrive on the *first* line, well clear of the second, so the words of
 * the second line read as moving up into the first rather than disappearing
 * and coming back. Gone well before the two lines' letters could meet.
 */
private const val SecondLineFrom = 0.15f
private const val SecondLineGoneBy = 0.6f

/** How much of a large title's second line has faded, at [progress] of the collapse. */
private fun secondLineGone(progress: Float): Float =
    ((progress - SecondLineFrom) / (SecondLineGoneBy - SecondLineFrom)).coerceIn(0f, 1f)

/** Where a large title's first line ends, in its own pixels; 0 for a title of one line. */
private class LineCut {
    var y: Float = 0f
}

/**
 * A [TopBarStyle.Large] bar's collapse, driven by the scroll of the content
 * under it.
 *
 * ```kotlin
 * val collapse = rememberTopBarCollapseState()
 * Column(Modifier.nestedScroll(collapse.nestedScrollConnection)) {
 *     TopBar(style = TopBarStyle.Large, collapseState = collapse) { +stop.name }
 *     LazyColumn { … }
 * }
 * ```
 *
 * **The bar takes the scroll first.** A large bar grows shorter as it collapses,
 * and the content under it moves up by as much. Collapsing it from the list's
 * own scroll position would then move the content twice — once by scrolling and
 * once by the bar shrinking above it — so the connection spends a scroll on the
 * bar until it has collapsed, and only then lets the list have it: the content
 * moves with the finger throughout. Scrolling back, the list returns to its top
 * first and the bar opens after, as a large title does on iOS.
 *
 * A collapse left part-way when the finger lifts finishes, to whichever end is
 * nearer — a title stopped between its two places reads as a layout fault.
 */
@Stable
class TopBarCollapseState internal constructor(initialProgress: Float) {

    /** 0 fully open, 1 collapsed to a single line. */
    var progress: Float by mutableFloatStateOf(initialProgress.coerceIn(0f, 1f))
        private set

    /**
     * How much shorter the bar is collapsed than open, in pixels, as it last
     * measured. Not state — nothing draws from it.
     */
    private var shrink: Float = 0f

    /**
     * How far a scroll goes to collapse the bar, in pixels: [shrink], or the
     * difference between [TopBarDefaults.LargeHeight] and
     * [TopBarDefaults.Height] if that is more.
     *
     * Not simply [shrink], because a bar can have next to nothing to give up —
     * with no back button or actions, and a title of one line, the open bar is
     * no taller than the collapsed one — and a collapse spread over no scroll
     * at all never happens: the title stayed large however far the page went.
     * So the title's journey has a length of its own, and a scroll along it is
     * shared: the bar takes the part its height gives up, and the content
     * scrolls by the rest. Either way the content moves exactly as far as the
     * finger.
     */
    private var travel: Float = 0f

    /** What the bar measured: see [shrink] and [travel]. */
    internal fun measured(shrink: Float, travel: Float) {
        this.shrink = shrink
        this.travel = travel
    }

    /** How a part-way collapse finishes. */
    internal var settle: AnimationSpec<Float> = spring()

    /** Put on the scrolling content's ancestor with `Modifier.nestedScroll`. */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            // Towards the content's end: the bar collapses before the list moves,
            // taking as much of the scroll as its height gives up.
            if (available.y < 0f) Offset(0f, spend(available.y) * shareOfScroll()) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            // Towards the start, with the list already at its top: the bar opens,
            // and has the whole of what is left, which nothing else can use.
            if (available.y > 0f) Offset(0f, spend(available.y)) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (progress > 0f && progress < 1f) {
                val target = if (progress < 0.5f) 0f else 1f
                animate(progress, target, animationSpec = settle) { value, _ -> progress = value }
            }
            return Velocity.Zero
        }
    }

    /** Moves the collapse along by a scroll of [delta] pixels, and returns how much of it that was. */
    private fun spend(delta: Float): Float {
        if (travel <= 0f) return 0f
        val before = progress
        progress = (progress - delta / travel).coerceIn(0f, 1f)
        return (before - progress) * travel
    }

    /** The part of a collapsing scroll the bar's height takes up; the content scrolls by the rest. */
    private fun shareOfScroll(): Float = if (travel <= 0f) 0f else shrink / travel

    internal companion object {
        val Saver: Saver<TopBarCollapseState, Float> = Saver(
            save = { it.progress },
            restore = { TopBarCollapseState(it) },
        )
    }
}

/** Remembers a [TopBarCollapseState], open to begin with, kept across recreation. */
@Composable
fun rememberTopBarCollapseState(): TopBarCollapseState {
    val state = rememberSaveable(saver = TopBarCollapseState.Saver) { TopBarCollapseState(0f) }
    // Under reduced motion a collapse left part-way lands at once rather than
    // travelling the rest of a bar's height.
    val motion = Theme.motion
    state.settle = if (motion.reduceMotion) snap() else motion.springOrTween(motion.springSnappy)
    return state
}

@Composable
private fun Leading(
    onBack: (() -> Unit)?,
    backLabel: String,
    navigation: (@Composable () -> Unit)?,
) {
    when {
        navigation != null -> navigation()
        onBack != null -> IconButton(
            icon = SystemIcons.ChevronBack,
            contentDescription = backLabel,
            onClick = onBack,
        )
    }
}

/** The title and its supporting line, each a line of whatever stacks them. */
@Composable
private fun TitleLines(slots: ListItemScope, large: Boolean = false) {
    slots.label?.let { title ->
        Box(Modifier.semantics { heading() }) {
            ProvideTextStyle(if (large) Theme.typography.displaySmall else Theme.typography.titleMedium) {
                ContentSlot(maxLines = if (large) 2 else 1, content = title)
            }
        }
    }
    slots.supporting?.let { subtitle ->
        ProvideContentColour(Theme.colours.contentMuted) {
            ProvideTextStyle(Theme.typography.bodySmall) {
                // Ellipsised rather than cut: a centred title is judged on its
                // heading alone, and the line under it made to fit.
                ContentSlot(maxLines = 1, overflow = TextOverflow.Ellipsis, content = subtitle)
            }
        }
    }
}

/**
 * How far a [TopBarStyle.Large] bar has collapsed, given a scroll position.
 *
 * ```kotlin
 * val listState = rememberLazyListState()
 * TopBar(style = TopBarStyle.Large, collapseProgress = collapseProgress(listState)) { +stop.name }
 * LazyColumn(state = listState) { … }
 * ```
 *
 * Pure, and tested, because the failure is a bar that never quite finishes
 * collapsing or finishes before the user has scrolled — both of which read as a
 * stutter and neither of which shows in a still.
 */
fun collapseProgress(scrolled: Float, distance: Float): Float =
    if (distance <= 0f) 0f else (scrolled / distance).coerceIn(0f, 1f)

/** [collapseProgress] for a lazy list, using the first item's scroll offset. */
@Composable
fun collapseProgress(
    state: LazyListState,
    distance: Dp = TopBarDefaults.LargeHeight - TopBarDefaults.Height,
): Float {
    val distancePx = with(androidx.compose.ui.platform.LocalDensity.current) { distance.toPx() }
    val scrolled = if (state.firstVisibleItemIndex > 0) {
        distancePx
    } else {
        state.firstVisibleItemScrollOffset.toFloat()
    }
    return collapseProgress(scrolled, distancePx)
}
