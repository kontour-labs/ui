package io.kontour.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.PageIndicatorDefaults
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.ScrollState
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
import io.kontour.ui.motion.smoothstep
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
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
     *
     * Several pictures, swiped between, are a [HeroImages] here, with its
     * page dots just above the title.
     */
    image: (@Composable () -> Unit)? = null,
    containerColour: Color = LocalContainerColour.current.takeOrElse { Theme.colours.background },
    contentColour: Color = Theme.colours.content,
    showDivider: Boolean = false,
    /**
     * Whether content passing under the bar fades out beneath it, rather than
     * stopping square at its foot: the bar's ground thins out over its last
     * 12dp, so content shows faintly through there, and is gone 4dp past the
     * foot — about where a divider would be. Any style, and a collapsing bar's
     * fade follows its foot.
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
     * through [topBarCollapse] on the content. The usual way
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
    val fades = fadeUnder && !highContrast()
    val density = LocalDensity.current
    // How tall it is open, for content that scrolls under it to pad its top by.
    val sized = if (collapseState != null) modifier.onSizeChanged { collapseState.laidOut(it.height, density) } else modifier
    val outer = if (fades) sized.fadedGround(containerColour) else sized
    // Fading, the bar's ground is the fade's own, drawn behind the surface;
    // a solid one would hide the fade inside the bar.
    val ground = if (fades) Color.Transparent else containerColour
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
            ground = ground,
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
    BarSurface(modifier = outer.fillMaxWidth(), containerColour = ground, contentColour = contentColour) {
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
                    leading = { BarNavigation(onBack, backLabel, navigation) },
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
                    leading = { BarNavigation(onBack, backLabel, navigation) },
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
 * A bar's ground in [colour], solid down to [FadeUnderInside] above its foot
 * and thinning from there to nothing [FadeUnderOutside] past it, over whatever
 * is under it. See `TopBar`'s `fadeUnder`.
 *
 * Mostly inside the bar. It ran 24dp on past the foot at first, and over the
 * page that read as a wash rather than an edge — *"the fade under the top bar
 * looks a bit "fat". it needs to basically end where the line would normally
 * be (or maybe a tiny bit further down)"*. Eased rather than straight, as a
 * hero's fade is, so there is no line where it starts or ends.
 */
private fun Modifier.fadedGround(colour: Color): Modifier = drawWithCache {
    val inside = FadeUnderInside.toPx().coerceAtMost(size.height)
    val outside = FadeUnderOutside.toPx()
    val from = size.height - inside
    val brush = easedFade(colour, solidAt = from, clearAt = size.height + outside)
    onDrawBehind {
        // One rect for the solid part and one for the fade, meeting on a whole
        // pixel so their edges do not leave a seam between them.
        val seam = from.toInt().toFloat()
        drawRect(colour, size = Size(size.width, seam))
        drawRect(brush, topLeft = Offset(0f, seam), size = Size(size.width, size.height + outside - seam))
    }
}

/** How much of a bar's own height `fadeUnder` thins out over, and how far past its foot. */
private val FadeUnderInside = 12.dp
private val FadeUnderOutside = 4.dp

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
    /** What the surface itself paints: [containerColour], or nothing when a fade paints it instead. */
    ground: Color,
    contentColour: Color,
    showDivider: Boolean,
    open: Dp,
    collapseProgress: Float,
    collapseState: TopBarCollapseState?,
    windowInsets: WindowInsets,
) {
    val progress = { collapseState?.progress ?: collapseProgress }
    val geometry = remember { HeroGeometry() }
    // Where a `HeroImages` in the picture says it is, for the dots this bar
    // draws over the fade, above the title.
    val images = remember { HeroImageHost() }
    val dotSize = PageIndicatorDefaults.DotSize
    // How wide the dots are as drawn, which the indicator's box — its touch
    // target — is not; and the room the last line of the title block keeps
    // clear for them at its end.
    val dotCount = images.carousel?.pageCount ?: 0
    val dotsWidth = if (dotCount < 2) {
        0.dp
    } else {
        PageIndicatorDefaults.ActiveWidth + (PageIndicatorDefaults.DotSize + PageIndicatorDefaults.Gap) * (dotCount - 1)
    }
    val dotsReserve = if (dotCount < 2) 0.dp else dotsWidth + HeroDotsGap
    val insetTop = with(LocalDensity.current) { windowInsets.getTop(this).toDp() }
    // Through the latest `progress`, not the first: a `remember`ed lambda keeps
    // what it closed over, and the first composition's closed over the
    // `collapseProgress` of that frame. A screen come back to is composed again
    // part-way down, so the discs behind the actions stayed at that frame's
    // opacity — or none — however far the reader then scrolled.
    val latestProgress by rememberUpdatedState(progress)
    val backing = remember(containerColour) { HeroBacking(containerColour) { 1f - latestProgress() } }
    val solid = highContrast()
    val reduce = Theme.motion.reduceMotion
    val rise = HeroFadeRise
    // How much of the picture there is, from 1 open to 0 collapsed.
    val shown = { 1f - smoothstep(HeroFadeFrom, HeroFadeTo, progress().coerceIn(0f, 1f)) }

    BarSurface(modifier = modifier.fillMaxWidth(), containerColour = ground, contentColour = contentColour) {
        Layout(
            contents = listOf(
                {
                    Box(Modifier.clearAndSetSemantics {}) {
                        CompositionLocalProvider(LocalHeroImageHost provides images) { image() }
                    }
                },
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
                                leading = { BarNavigation(onBack, backLabel, navigation) },
                                actions = actions,
                                slots = slots,
                                minExpanded = (open - insetTop).coerceAtLeast(0.dp),
                                hero = geometry,
                                endReserve = dotsReserve,
                            )
                        }
                        if (showDivider) HorizontalDivider()
                    }
                },
                // Over the fade and under the title: the pictures' dots, when
                // there are pictures to page between.
                { images.carousel?.let { HeroDots(it) } },
            ),
            modifier = Modifier.fillMaxWidth().clipToBounds(),
        ) { (picturePart, fadePart, barPart, dotsPart), constraints ->
            val bar = barPart.first().measure(constraints)
            val p = progress().coerceIn(0f, 1f)
            val height = (bar.height + geometry.shrink * p).roundToInt().coerceAtLeast(bar.height)
            val picture = picturePart.first().measure(Constraints.fixed(bar.width, height))
            val fade = fadePart.first().measure(Constraints.fixed(bar.width, height))
            val dots = dotsPart.firstOrNull()?.measure(Constraints(maxWidth = bar.width, maxHeight = height))
            layout(bar.width, bar.height) {
                picture.placeWithLayer(0, 0) {
                    val now = progress().coerceIn(0f, 1f)
                    // Rises at half the pace of the bar's foot, which reads as
                    // depth; under reduced motion it stays and only fades.
                    translationY = if (reduce) 0f else -geometry.shrink * now * HeroParallax
                    alpha = 1f - smoothstep(HeroFadeFrom, HeroFadeTo, now)
                }
                fade.place(0, 0)
                if (dots != null) {
                    // At the end of the title block's last line, over the fade —
                    // asked for as "move that pager indicator over the faded
                    // bit, down in the bottom-right of the header" — sitting on
                    // its baseline, in from the end by the title's margin.
                    // Carried up with the title as the bar collapses, and gone
                    // before the title nears the first line.
                    //
                    // Placed by the dots as drawn, not by the indicator's box,
                    // which is its touch target: 48dp tall on a phone round 8dp
                    // dots, and wider than three of them.
                    val rise = (geometry.textTopCollapsed - geometry.textTopOpen) * p
                    val baseline = windowInsets.getTop(this@Layout) + geometry.lastBaselineOpen + rise
                    val centreY = baseline - dotSize.toPx() / 2f
                    val drawn = dotsWidth.toPx()
                    val centreX = if (layoutDirection == LayoutDirection.Rtl) {
                        geometry.margin + drawn / 2f
                    } else {
                        bar.width - geometry.margin - drawn / 2f
                    }
                    val x = centreX - dots.width / 2f
                    val y = centreY - dots.height / 2f
                    dots.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                        alpha = 1f - smoothstep(0f, HeroDotsGoneBy, progress().coerceIn(0f, 1f))
                    }
                }
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
    /** The baseline of the title block's last line, open: the subtitle's, or the title's without one. */
    var lastBaselineOpen: Float = 0f
    /** The title's margin from either side. */
    var margin: Float = 0f
}

/** The carousel a hero's picture is, if it is one: told by [HeroImages], read by the bar for its dots. */
@Stable
internal class HeroImageHost {
    var carousel: CarouselState? by mutableStateOf(null)
}

/** Provided round a [TopBarStyle.Hero] bar's picture. */
internal val LocalHeroImageHost = staticCompositionLocalOf<HeroImageHost?> { null }

/**
 * Several pictures behind a [TopBarStyle.Hero] bar, swiped between.
 *
 * ```kotlin
 * val photos = rememberCarouselState { stop.photos.size }
 * TopBar(
 *     style = TopBarStyle.Hero,
 *     onBack = ::goBack,
 *     image = {
 *         HeroImages(photos) { page ->
 *             Image(stop.photos[page], contentDescription = null, contentScale = ContentScale.Crop)
 *         }
 *     },
 * ) { +stop.name }
 * ```
 *
 * A [Carousel] filling the picture, edge to edge and under the status bar, and
 * the carousel's [PageIndicator] at the bottom end of the bar, over the fade:
 * on the last line of the title block — the subtitle, or the title without
 * one — which stops short of it. A finger swipes anywhere on the picture that
 * is not a button; a pointer clicks or drags along the dots. One picture shows
 * no dots.
 *
 * Collapsing, the pictures do what a hero's picture does — rise at half the
 * bar's pace and fade over the second half — and the dots go up with the title
 * and are gone a third of the way in, before the title nears the first line.
 *
 * Outside a hero bar it is the same carousel with its dots at the foot, and
 * announced by `Theme.strings.images`.
 *
 * **Decorative, like any hero picture**, and hidden from screen readers with
 * its dots: say what matters in the title. A set of pictures someone needs to
 * page through — a listing's photos — is a [Carousel] in the page, where its
 * pages are announced and it carries previous and next.
 *
 * @param state Which picture is showing, and how many there are.
 * @param content The picture for each page. Fill the box and crop.
 */
@Composable
fun HeroImages(
    state: CarouselState,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    val host = LocalHeroImageHost.current
    if (host != null) {
        DisposableEffect(host, state) {
            host.carousel = state
            onDispose { if (host.carousel === state) host.carousel = null }
        }
    }
    Box(modifier.fillMaxSize()) {
        // Hidden with the rest of the picture inside a hero bar; the words are
        // for anywhere else it is put.
        Carousel(
            state = state,
            contentDescription = Theme.strings.images,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            peek = 0.dp,
        ) { page ->
            Box(Modifier.fillMaxSize()) { content(page) }
        }
        // Outside a hero bar there is no title to sit over: the dots go at the
        // pictures' foot.
        if (host == null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Theme.spacing.md)) {
                HeroDots(state)
            }
        }
    }
}

/**
 * A hero carousel's dots, on the picture as they are: a capsule of the bar's
 * colour behind them was tried and taken away, asked for as *"can we get rid
 * of the square bubble altogether?"*. Hidden from screen readers with the
 * pictures; a pointer can click and drag along them.
 */
@Composable
private fun HeroDots(state: CarouselState) {
    if (state.pageCount < 2) return
    val scope = rememberCoroutineScope()
    PageIndicator(
        state,
        modifier = Modifier
            // Room round the dots for the pill a drag along them brings up,
            // inside the layer the bar places them in: drawn past the
            // indicator's own box, the pill was cut off where a renderer
            // clips a layer to its bounds — reported from an iPad, *"that
            // popup bubble gets cut off"*. Even all round, so the dots' centre,
            // which the bar places them by, does not move.
            .padding(HeroDotsPillRoom)
            .clearAndSetSemantics {},
        onPageClick = { page -> scope.launch { state.animateScrollToPage(page) } },
    )
}

/** Round a hero's dots, the pill's own pad past them and a little more. */
private val HeroDotsPillRoom = 12.dp

/** The room between the end of a hero's last line of text and its carousel's dots. */
private val HeroDotsGap = 16.dp

/** How far into a hero bar's collapse its carousel's dots are gone: well before the title nears the first line. */
private const val HeroDotsGoneBy = 0.35f

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
    /**
     * Room kept clear at the end of the title block's last line — the
     * subtitle, or the title without one — for what a hero draws there.
     */
    endReserve: Dp = 0.dp,
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
        // The last line stops short of what is kept clear at its end.
        val lastWidth = (largeWidth - endReserve.roundToPx()).coerceAtLeast(0)
        val hasSubtitle = subtitlePart.isNotEmpty()
        val big = largePart.firstOrNull()?.measure(Constraints(maxWidth = if (hasSubtitle) largeWidth else lastWidth))
        val tiny = smallPart.firstOrNull()?.measure(Constraints(maxWidth = smallWidth))
        val sub = subtitlePart.firstOrNull()?.measure(Constraints(maxWidth = lastWidth))
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
            hero.margin = marginPx.toFloat()
            fun lastBaseline(placeable: Placeable): Int =
                placeable[LastBaseline].takeIf { it != AlignmentLine.Unspecified } ?: placeable.height
            hero.lastBaselineOpen = textTop + when {
                sub != null -> (big?.height ?: 0) + lastBaseline(sub)
                big != null -> lastBaseline(big)
                else -> 0
            }.toFloat()
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
 * Column(Modifier.topBarCollapse(collapse)) {
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
 * Pulled open at the top of the list, the bar opens over its own height, so the
 * content comes down with the finger, and a large title on the way back is
 * where the finger put it.
 *
 * A collapse left part-way when the finger lifts finishes — the way the finger
 * was going if it was let go moving, to whichever end is nearer if it was let go
 * still — since a title stopped between its two places reads as a layout fault.
 * A fling that carries the list back to its top opens the bar with what is
 * left of it. A mouse wheel has no lift, and moves the bar a notch at a time;
 * see [topBarCollapse].
 */
@Stable
class TopBarCollapseState internal constructor(initialProgress: Float) {

    /** The bar's own collapse, where it takes the scroll itself. */
    private var own: Float by mutableFloatStateOf(initialProgress.coerceIn(0f, 1f))

    /**
     * How far the content under the bar has scrolled, in pixels, for a bar that
     * follows it — see [rememberTopBarCollapseState]'s overloads — or null for
     * one that takes the scroll itself.
     */
    internal var follows: (() -> Float)? = null

    /** 0 fully open, 1 collapsed to a single line. */
    val progress: Float
        get() {
            val scrolled = follows ?: return own
            return collapseProgress(scrolled(), followedTravel)
        }

    /**
     * How much shorter the bar is collapsed than open, in pixels, as it last
     * measured. Not state — nothing draws from it.
     */
    private var shrink: Float = 0f

    /** [travel], as state: a following bar's progress is read against it. */
    private var followedTravel: Float by mutableFloatStateOf(0f)

    /**
     * How tall the bar is open, status bar included, as it last measured: the
     * padding to give the top of content that scrolls under a bar following
     * it. Before the bar has measured, the height it was remembered with.
     */
    var openHeight: Dp by mutableStateOf(Dp.Unspecified)
        internal set

    /** The bar laid out [height] pixels tall at its present [progress]. */
    internal fun laidOut(height: Int, density: Density) {
        // In whole pixels: worked back from a height rounded to a pixel at each
        // step of a collapse, it came out a fraction different every frame,
        // and every fraction moved the padding of the list under it.
        val open = with(density) { (height + shrink * progress.coerceIn(0f, 1f)).roundToInt().toDp() }
        if (openHeight == Dp.Unspecified || with(density) { (open - openHeight).toPx() }.let { kotlin.math.abs(it) >= 1f }) openHeight = open
    }

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
        if (followedTravel != travel) followedTravel = travel
    }

    /** How a part-way collapse finishes. */
    internal var settle: AnimationSpec<Float> = spring()

    /** How fast a finger has to be going, in pixels a second, for the finish to follow it. */
    internal var flingThreshold: Float = 0f

    /**
     * The scroll half of [topBarCollapse], for putting on the content with
     * `Modifier.nestedScroll` yourself. Prefer [topBarCollapse]: on this alone a
     * mouse wheel at the top of a list cannot open the bar, because the list
     * declines the notch before it becomes a scroll.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            // Towards the content's end: the bar collapses before the list moves,
            // taking as much of the scroll as its height gives up.
            if (available.y < 0f && follows == null) Offset(0f, spend(available.y) * shareOfScroll()) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            // Towards the start, with the list already at its top: the bar opens,
            // and has the whole of what is left, which nothing else can use —
            // over its own height, so the content, which only the bar's growing
            // moves now, comes down with the finger. Spread over [travel], as a
            // collapse is, a bar with less height than travel to give back opened
            // slower than the finger pulled, the list lagging under it: *"it
            // doesn't always feel like they follow the finger"*.
            if (available.y > 0f && follows == null) Offset(0f, spend(available.y, opening = true)) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (follows != null || travel <= 0f || progress <= 0f || progress >= 1f) return Velocity.Zero
            // The way the finger was going when it let go, if it was going
            // anywhere: the scroll's whole velocity, what the list spent of it
            // and what it had no room for. Towards the content's start opens.
            // The nearer end only for a finger let go still. Always the nearer
            // end, as it was, finished a bar the finger had been opening back
            // to collapsed, and a list flung back to its top left its bar shut
            // with the fling still going — *"other times they don't expand
            // properly"*.
            val velocity = consumed.y + available.y
            val target = when {
                velocity > flingThreshold -> 0f
                velocity < -flingThreshold -> 1f
                progress < 0.5f -> 0f
                else -> 1f
            }
            // Progress runs against the scroll: towards the start is opening.
            settling?.cancel()
            animate(progress, target, -velocity / travel, animationSpec = settle) { value, _ -> own = landed(value) }
            // What the list had no room for, spent opening the bar, is not
            // left over for an overscroll to stretch the page with as well.
            return if (target == 0f && available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
        }
    }

    /** A wheel's [finishForWheel], while it runs, and where it is going. */
    private var settling: Job? = null
    private var settlingTo: Float = 0f

    /**
     * Finishes the bar towards [target] — 0 open, 1 collapsed — for a wheel's
     * notch the content declined, in [scope]. Whether that used the notch: not
     * when the bar is already there, nor when it does not collapse at all.
     */
    internal fun finishForWheel(target: Float, scope: CoroutineScope): Boolean {
        if (travel <= 0f || follows != null) return false
        if (settling?.isActive == true) {
            if (settlingTo == target) return true
            settling?.cancel()
        } else if (progress == target) {
            return false
        }
        settlingTo = target
        settling = scope.launch {
            animate(progress, target, animationSpec = settle) { value, _ -> own = landed(value) }
        }
        return true
    }

    /**
     * A finishing spring's value, held to the bar's two ends.
     *
     * Reported: on a screen only just long enough to scroll, *"that bar just
     * jumps around when you try to scroll it"*. The finish is a spring, handed
     * the fling's speed, and a spring sent at its end that fast goes past it
     * and comes back: the bar opened taller than open, or collapsed shorter
     * than a small bar, and the content under it went with it, there and back.
     * A long list spends most of a fling scrolling and leaves the bar little;
     * one that can barely scroll leaves it all of it. Past an end there is no
     * bar to draw, so the overshoot is spent at the end, where the bar waits
     * for the spring to come back to it.
     */
    private fun landed(value: Float): Float = value.coerceIn(0f, 1f)

    /**
     * Moves the collapse along by a scroll of [delta] pixels, and returns how
     * much of it that was. [opening] at the content's start, where the scroll
     * is spent over the bar's height alone; see `onPostScroll`.
     */
    private fun spend(delta: Float, opening: Boolean = false): Float {
        // A scroll is the more recent word than a wheel's settle.
        settling?.cancel()
        if (travel <= 0f) return 0f
        val length = if (opening && shrink > 0f) shrink else travel
        val before = own
        own = (own - delta / length).coerceIn(0f, 1f)
        return (before - own) * length
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

/**
 * How fast a finger let go of a collapsing bar has to be moving, a second, for
 * the bar to finish the way it was going rather than to the nearer end.
 */
private val FinishFling: Dp = 125.dp

/** Remembers a [TopBarCollapseState], open to begin with, kept across recreation. */
@Composable
fun rememberTopBarCollapseState(): TopBarCollapseState {
    val state = rememberSaveable(saver = TopBarCollapseState.Saver) { TopBarCollapseState(0f) }
    // Under reduced motion a collapse left part-way lands at once rather than
    // travelling the rest of a bar's height.
    val motion = Theme.motion
    state.settle = if (motion.reduceMotion) snap() else motion.springOrTween(motion.springSnappy)
    state.flingThreshold = with(LocalDensity.current) { FinishFling.toPx() }
    return state
}

/**
 * A [TopBarCollapseState] that follows [list]'s scroll rather than taking the
 * scroll itself — for a bar drawn **over** the list, which pads its top by the
 * bar's [TopBarCollapseState.openHeight]:
 *
 * ```kotlin
 * val list = rememberLazyListState()
 * val collapse = rememberTopBarCollapseState(list)
 * Box {
 *     LazyColumn(state = list, contentPadding = PaddingValues(top = collapse.openHeight)) { … }
 *     TopBar(style = TopBarStyle.Hero, collapseState = collapse, image = { … }) { +stop.name }
 * }
 * ```
 *
 * The way to wire a [TopBarStyle.Hero] bar. The list is the only thing that
 * scrolls, and the bar is drawn from where it has got to: as the rows move up
 * the bar's foot moves with them, the picture going as the title comes in, and
 * there is nothing for the bar and the list to share out between them. Taking
 * the scroll itself, a hero laid out above its list was reported on an iPhone
 * as *"sometimes when i start scrolling up, it doesn't and just does the
 * overscroll animation"*: the system's stretch, relaxing from a pull a moment
 * before, spent the start of the next drag before the bar was asked for it. And
 * a collapse left part-way stays where the list is, rather than finishing on
 * its own — over a tall picture, a slow look back up was snapped shut.
 *
 * @param initialOpenHeight How tall the bar is open, status bar included,
 *   until it has measured: what [TopBarCollapseState.openHeight] says in the
 *   first frame, so the list does not start under the bar for one.
 */
@Composable
fun rememberTopBarCollapseState(
    list: LazyListState,
    initialOpenHeight: Dp = TopBarDefaults.HeroHeight,
): TopBarCollapseState = remember(list) {
    TopBarCollapseState(0f).apply {
        this.openHeight = initialOpenHeight
        follows = {
            // How far the list has gone while its first row is in view, and
            // past the end of any collapse once it is not. An empty list — a
            // page still loading — is open.
            val info = list.layoutInfo
            if (info.totalItemsCount == 0) {
                0f
            } else {
                val first = info.visibleItemsInfo.firstOrNull { it.index == 0 }
                first?.let { 0f - it.offset } ?: Float.MAX_VALUE
            }
        }
    }
}

/**
 * A [TopBarCollapseState] that follows [scroll] — content scrolled with
 * `Modifier.verticalScroll(scroll)` under a bar drawn over it. See the lazy
 * list's overload, which this is in every way but where the distance is read.
 */
@Composable
fun rememberTopBarCollapseState(
    scroll: ScrollState,
    initialOpenHeight: Dp = TopBarDefaults.HeroHeight,
): TopBarCollapseState = remember(scroll) {
    TopBarCollapseState(0f).apply {
        this.openHeight = initialOpenHeight
        follows = { scroll.value.toFloat() }
    }
}

/**
 * Collapses [state]'s bar with the scroll of the content this is on, and opens
 * it again. How a [TopBarCollapseState] is wired.
 *
 * ```kotlin
 * val collapse = rememberTopBarCollapseState()
 * Column(Modifier.topBarCollapse(collapse)) {
 *     TopBar(style = TopBarStyle.Hero, collapseState = collapse, image = { … }) { +stop.name }
 *     LazyColumn { … }
 * }
 * ```
 *
 * Put it on something that holds the scrolling content: the column or
 * [io.kontour.ui.adaptive.Scaffold] the bar and the list share, or the list
 * itself.
 *
 * It is `Modifier.nestedScroll` with the state's
 * [TopBarCollapseState.nestedScrollConnection], and one thing more, for a mouse
 * wheel. A list declines a wheel's notch outright when it cannot scroll any
 * further that way, so the notch never becomes a scroll and no connection hears
 * of it — and a list at its top is exactly where a bar opens. So a notch the
 * content declined finishes the bar the way it turned: towards the content's
 * start, open; towards its end, collapsed. A notch the content scrolled by moves
 * the bar as any scroll does.
 */
fun Modifier.topBarCollapse(state: TopBarCollapseState): Modifier =
    nestedScroll(state.nestedScrollConnection).pointerInput(state) {
        coroutineScope {
            awaitPointerEventScope {
                while (true) {
                    // The main pass reaches this after the content's own
                    // scrollables, so a notch they took is already consumed.
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.isConsumed) continue
                    val delta = change.scrollDelta
                    if (delta.y == 0f || abs(delta.y) < abs(delta.x)) continue
                    val target = if (delta.y > 0f) 1f else 0f
                    if (state.finishForWheel(target, this@coroutineScope)) change.consume()
                }
            }
        }
    }

/**
 * A bar's leading navigation: [navigation] if there is one, otherwise a back
 * button for [onBack], otherwise nothing. A [TopBar]'s, and a
 * [SheetHeader][io.kontour.ui.sheet.SheetHeader]'s, so the two read the same.
 */
@Composable
internal fun BarNavigation(
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
