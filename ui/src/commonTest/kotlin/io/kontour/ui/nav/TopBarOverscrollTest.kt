package io.kontour.ui.nav

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A push past the content's end, under a collapsing bar, never carries the
 * content up under the bar.
 *
 * Reported from an iPhone: *"scrolling under large or hero top bars on iOS does
 * weird things when there's not enough content on the screen for it to be
 * properly scrollable. i think it's because the ios overscroll animation (which
 * physically pulls content down/up) makes it go under the top bar"*. iOS's
 * overscroll is not on the desktop, so the scrollables here are handed
 * [SlidingOverscroll], which does the part of it that mattered: what nothing
 * else took of a drag slides the content bodily. The bar is told it is on such
 * a platform, as it is told on iOS.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarOverscrollTest {

    private lateinit var collapse: TopBarCollapseState
    private lateinit var list: LazyListState
    private val made = Made()

    /** A hero drawn over [rows] rows and following them, with the collapse on the list. */
    private fun ComposeUiTest.following(rows: Int, takesPushes: Boolean = true) {
        setContent {
            Platform {
                list = rememberLazyListState()
                collapse = rememberTopBarCollapseState(list)
                collapse.takesPushes = takesPushes
                Box(Modifier.size(360.dp, 700.dp)) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().topBarCollapse(collapse),
                        contentPadding = PaddingValues(top = collapse.openHeight),
                    ) {
                        items(rows) { index -> Stripe(index) }
                    }
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = TopBarStyle.Hero,
                        image = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        collapseState = collapse,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                }
            }
        }
        waitForIdle()
    }

    /**
     * A Large bar with a back button in a column above its list, taking the
     * scroll itself. The list is [overflow] longer than the room the open bar
     * leaves it — negative for a page that cannot scroll at all — or 40 rows.
     */
    private fun ComposeUiTest.above(overflow: Dp?, takesPushes: Boolean = true) {
        setContent {
            Platform {
                list = rememberLazyListState()
                collapse = rememberTopBarCollapseState()
                collapse.takesPushes = takesPushes
                Column(Modifier.size(360.dp, 700.dp).topBarCollapse(collapse)) {
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = TopBarStyle.Large,
                        onBack = {},
                        collapseState = collapse,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val tall = remember { maxHeight + (overflow ?: 0.dp) }
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            item { Stripe(0) }
                            if (overflow == null) {
                                items(39) { index -> Stripe(index + 1) }
                            } else {
                                item { Spacer(Modifier.fillMaxWidth().height((tall - RowHeight).coerceAtLeast(0.dp))) }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Composable
    private fun Platform(content: @Composable () -> Unit) {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalOverscrollFactory provides SlidingOverscrollFactory(made), content = content)
        }
    }

    @Composable
    private fun Stripe(index: Int) {
        Box(Modifier.fillMaxWidth().height(RowHeight).background(Color.Gray).testTag("row$index"))
    }

    /** A finger down on the list, pushed up [steps] moves of 8px, and held. */
    private fun ComposeUiTest.push(steps: Int) {
        onRoot().performTouchInput {
            down(Offset(100f, centerY + 100f))
            repeat(steps) { moveBy(Offset(0f, -8f)) }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.lift() {
        onRoot().performTouchInput { up() }
        waitForIdle()
    }

    private fun ComposeUiTest.barFoot(): Dp = onNodeWithTag("bar").getUnclippedBoundsInRoot().bottom

    private fun ComposeUiTest.firstRowTop(): Dp = onNodeWithTag("row0").getUnclippedBoundsInRoot().top

    /** How far the platform's overscroll has slid any content, in pixels: up is negative. */
    private fun slid(): Float = made.effects.minOfOrNull { it.slid } ?: 0f

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) <= 1.5f, "$what: expected $expected, was $actual")

    /**
     * Without the bar taking the push, the stand-in does what iOS did: the rows
     * slide up under a bar left where the scroll stopped. Here so that the
     * tests after it are known to be able to fail.
     */
    @Test
    fun aPushTheBarDoesNotTakeSlidesTheRowsUnderIt() = runComposeUiTest {
        following(rows = 8, takesPushes = false)
        push(30)
        assertTrue(slid() < -40f, "the stand-in overscroll slid the rows only ${slid()}px")
        assertTrue(firstRowTop() < barFoot() - 20.dp, "the first row, at ${firstRowTop()}, is not under the bar's foot at ${barFoot()}")
        lift()
    }

    /**
     * A list that scrolls less far than the bar collapses: past its end the
     * rows go on up with the bar's foot, and come back down with it to where
     * the scroll left them.
     */
    @Test
    fun followingPastTheListsEndTheBarsFootGoesWithTheRows() = runComposeUiTest {
        following(rows = 8)
        push(30)
        val scrolled = collapse.progress
        assertTrue(list.firstVisibleItemScrollOffset > 0, "the list never scrolled, so this measured nothing")
        assertEquals(0f, slid(), "the platform's overscroll was handed some of the push")
        near(firstRowTop(), barFoot(), "pushed past the end")
        assertTrue(scrolled > 0f && scrolled < 1f, "pushed past the end, the bar was at $scrolled")
        lift()
        near(firstRowTop(), barFoot(), "let go")
        assertTrue(collapse.progress < scrolled, "let go, the bar stayed at ${collapse.progress}")
        assertTrue(collapse.progress > 0f, "let go, the bar opened past where the list is")
    }

    /** A list that cannot scroll at all has no overscroll anywhere, and the bar leaves it so. */
    @Test
    fun followingAListThatCannotScrollNothingMoves() = runComposeUiTest {
        following(rows = 3)
        val top = firstRowTop()
        push(30)
        assertEquals(0f, collapse.progress)
        near(top, firstRowTop(), "pushed")
        near(firstRowTop(), barFoot(), "pushed")
        lift()
        assertEquals(0f, collapse.progress)
    }

    /**
     * Content only just long enough to scroll, under a bar taking the scroll
     * itself: what is left of the push once the list is at its end, with the bar
     * still collapsing, carries the collapse on rather than sliding the list up
     * behind the bar's foot, and the collapse finishes. Pushed only as far as
     * the bar's travel, so the bar is still collapsing throughout.
     */
    @Test
    fun aboveAPageThatScrollsALittleThePushCarriesTheCollapseOn() = runComposeUiTest {
        above(overflow = 1.dp)
        push(6)
        val held = collapse.progress
        assertTrue(held > 0f && held < 1f, "pushed, the bar was at $held, so this measured nothing")
        assertEquals(0f, slid(), "the platform's overscroll slid the list under the bar's foot")
        lift()
        val settled = collapse.progress
        assertTrue(settled == 0f || settled == 1f, "let go, the bar was left part-way, at $settled")
    }

    /**
     * Over a page that cannot scroll at all, the bar folds part of the way under
     * the push, the page with its foot, and opens again when the finger lifts,
     * rather than staying collapsed over nothing.
     */
    @Test
    fun aboveAPageThatCannotScrollTheBarOpensAgain() = runComposeUiTest {
        above(overflow = (-200).dp)
        push(30)
        val held = collapse.progress
        assertTrue(held > 0.2f && held < 1f, "pushed, the bar was at $held")
        near(firstRowTop(), barFoot(), "pushed")
        lift()
        assertEquals(0f, collapse.progress, "let go, the bar stayed at ${collapse.progress}")
        near(firstRowTop(), barFoot(), "let go")
    }

    /** A long list is as it was: the bar collapses ahead of it, and stays collapsed as it scrolls on. */
    @Test
    fun aboveALongListNothingChanges() = runComposeUiTest {
        above(overflow = null)
        onRoot().performTouchInput {
            down(Offset(100f, centerY + 200f))
            repeat(40) { moveBy(Offset(0f, -10f)) }
            up()
        }
        waitForIdle()
        assertEquals(1f, collapse.progress)
        assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0, "the list never scrolled")
        assertEquals(0f, slid())
    }

    /**
     * A following hero over [rows] rows, with the collapse on the list only
     * while [attached] says so — a list swapped out by tabs, an
     * `AnimatedContent` or a `key` while the state is held above it.
     */
    private fun ComposeUiTest.followingDetachable(rows: Int, attached: () -> Boolean) {
        setContent {
            Platform {
                list = rememberLazyListState()
                collapse = rememberTopBarCollapseState(list)
                collapse.takesPushes = true
                Box(Modifier.size(360.dp, 700.dp)) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().then(if (attached()) Modifier.topBarCollapse(collapse) else Modifier),
                        contentPadding = PaddingValues(top = collapse.openHeight),
                    ) {
                        items(rows) { index -> Stripe(index) }
                    }
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = TopBarStyle.Hero,
                        image = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        collapseState = collapse,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                }
            }
        }
        waitForIdle()
    }

    /**
     * The collapse taken off the content in the middle of a push lets go of
     * it, and forgets the finger.
     *
     * What lets go of a push is the finger lifting, which the collapse heard
     * through its own pointer input — and a modifier taken off hears nothing
     * more. The push stayed where the finger had left it, the rows held up
     * under a bar part-way, with nothing to bring them down; and the finger
     * stayed down as far as the state knew, so the next press was not a press
     * to it, and the drag before's flags carried over into it.
     */
    @Test
    fun aCollapseTakenOffMidPushLetsGoOfIt() = runComposeUiTest {
        var attached by mutableStateOf(true)
        followingDetachable(rows = 8) { attached }
        push(30)
        assertTrue(collapse.contentShift < 0f, "the push never reached the bar, so this measured nothing")

        runOnUiThread { attached = false }
        waitForIdle()
        // Within a hair of none, not exactly none: a push let go of is `-0f`,
        // which a `Float`'s `equals` tells from `0f`.
        assertEquals(0f, collapse.contentShift, 0.01f, "taken off mid-push, the rows stayed up with the bar's foot")
        assertFalse(collapse.pressed, "taken off mid-push, the state still thinks a finger is down")
        lift()
    }

    /**
     * A bar saved in the middle of a push comes back where the push would have
     * left it, not where the finger held it.
     *
     * The state saved its `progress`, which a push is part of: over a page
     * that cannot scroll, a bar saved held part-way by the band came back
     * part-way, over content that has no scroll to finish it with.
     */
    @Test
    fun aBarSavedMidPushIsSavedWithoutThePush() = runComposeUiTest {
        above(overflow = (-200).dp)
        push(30)
        val held = collapse.progress
        assertTrue(held > 0.2f, "pushed, the bar was at $held, so this measured nothing")
        val saved = with(TopBarCollapseState.Saver) { SaveAnything.save(collapse) }
        assertEquals(0f, saved, "saved mid-push over a page that cannot scroll, the bar would come back at $saved")
        lift()
    }

    /**
     * A finger pushed past the end, brought back towards the start and let go
     * still moving that way: the list flings back with it.
     *
     * A fling was spent letting go of the push whenever there was one, the
     * whole of its velocity, whichever way it went — so a release back towards
     * the start, with some of the push still held, stopped the list dead where
     * it was rather than sending it back.
     */
    @Test
    fun aReleaseBackTowardsTheStartFlingsTheListBack() = runComposeUiTest {
        following(rows = 8)
        // Pushed further than the other tests push, so that a move back of
        // 120px still leaves some of the push held when the finger lets go: it
        // is that remainder the fling used to be spent on.
        push(40)
        val scrolled = scrolledPx()
        assertTrue(scrolled > 0f, "the list never scrolled, so this measured nothing")
        assertTrue(collapse.contentShift < -60f, "the push was ${collapse.contentShift}px, too little to keep some of")

        // Back down by less than the push, and let go moving down.
        onRoot().performTouchInput {
            repeat(4) { moveBy(Offset(0f, 30f)) }
        }
        waitForIdle()
        assertTrue(collapse.contentShift < 0f, "the move back took the whole push, so the release has none to keep")
        assertEquals(scrolled, scrolledPx(), 0.5f, "the move back scrolled the list before it took back the push")
        onRoot().performTouchInput { up() }
        waitForIdle()
        // Within a hair of none, not exactly none: a push let go of is `-0f`.
        assertEquals(0f, collapse.contentShift, 0.01f, "let go, the push was still held")
        assertTrue(
            scrolledPx() < scrolled / 2,
            "let go moving back towards the start, the list stayed at ${scrolledPx()}px of $scrolled",
        )
    }

    /**
     * How far the list has scrolled, in pixels: every row is [RowHeight], so
     * the rows gone past and the offset into the first one say it whole — the
     * first row goes past before a list of eight has reached its end.
     */
    private fun ComposeUiTest.scrolledPx(): Float =
        list.firstVisibleItemIndex * with(density) { RowHeight.toPx() } + list.firstVisibleItemScrollOffset

    /**
     * The collapse round a box that holds the bar as well as its list — what
     * the guidance once said to do — carries the rows up past the list's end,
     * and not the bar with them.
     */
    @Test
    fun aCollapseRoundTheBarAndTheListLeavesTheBarWhereItIs() = runComposeUiTest {
        setContent {
            Platform {
                list = rememberLazyListState()
                collapse = rememberTopBarCollapseState(list)
                collapse.takesPushes = true
                Box(Modifier.size(360.dp, 700.dp).topBarCollapse(collapse)) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = collapse.openHeight),
                    ) {
                        items(8) { index -> Stripe(index) }
                    }
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = TopBarStyle.Hero,
                        image = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        collapseState = collapse,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                }
            }
        }
        waitForIdle()
        push(30)
        assertTrue(collapse.contentShift < 0f, "the push never reached the bar, so this measured nothing")
        near(0.dp, onNodeWithTag("bar").getUnclippedBoundsInRoot().top, "the bar's top, pushed")
        near(firstRowTop(), barFoot(), "pushed")
        lift()
    }

    /** Saves whatever it is asked to: the platform's registry is not what is under test. */
    private object SaveAnything : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private companion object {
        val RowHeight = 56.dp
    }
}

/** The stand-in overscrolls one test made, to read how far they slid. */
private class Made {
    val effects = mutableListOf<SlidingOverscroll>()
}

/**
 * iOS's overscroll, without its curve or its spring: whatever a drag has left
 * once the content and everything above it have taken what they will slides
 * the content bodily, and it is back when the finger lifts.
 */
private class SlidingOverscroll : OverscrollEffect {
    var slid by mutableFloatStateOf(0f)

    override val isInProgress: Boolean
        get() = slid != 0f

    override val node: DelegatableNode = SlideNode { slid }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val consumed = performScroll(delta)
        if (source != NestedScrollSource.UserInput) return consumed
        slid += delta.y - consumed.y
        return Offset(consumed.x, delta.y)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        performFling(velocity)
        slid = 0f
    }
}

/** Places the content [by] pixels down: up, for a negative slide. */
private class SlideNode(private val by: () -> Float) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, by().roundToInt()) }
    }
}

/** A `data class` because [OverscrollFactory] asks for equality: see `SheetChildOverscrollFactory`. */
private data class SlidingOverscrollFactory(private val made: Made) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = SlidingOverscroll().also { made.effects += it }
}
