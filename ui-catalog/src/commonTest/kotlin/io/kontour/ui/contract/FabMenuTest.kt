package io.kontour.ui.contract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuLayout
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The three arrangements, and the wave that carries them out.
 *
 * Everything else about this component the shared suites already hold it to —
 * the contract's seven rules, the width sweep, the type-scale sweep, the render.
 * What is left is what is only true of a `FabMenu`: that the layouts are three
 * *different* arrangements rather than three names for a column, that they open
 * away from the wall the button is against, that the items do not pile up when
 * there is no room, and that they arrive one after another rather than all at
 * once.
 *
 * ### Measured off the items themselves
 *
 * Each item is named by its label, so the test asks the semantics tree where
 * "Save stop" ended up rather than looking for circles in a bitmap. That also
 * means a layout that draws an item somewhere it cannot be reached fails here:
 * `getBoundsInRoot` is where the *node* is, and the node is what a tap finds.
 */
@OptIn(ExperimentalTestApi::class)
class FabMenuTest {

    @Test
    fun theThreeLayoutsAreThreeArrangements() = runComposeUiTest {
        val vertical = openAt(FabMenuLayout.Vertical)
        val horizontal = openAt(FabMenuLayout.Horizontal)
        val fan = openAt(FabMenuLayout.Fan)

        // A column: one x, three ys.
        assertTrue(
            vertical.spreadX < Tolerance && vertical.spreadY > MinSpread,
            "Vertical put the items ${vertical.spreadX}px apart horizontally and " +
                "${vertical.spreadY}px vertically — a column varies in y and " +
                "nothing else",
        )
        assertTrue(
            horizontal.spreadY < Tolerance && horizontal.spreadX > MinSpread,
            "Horizontal put the items ${horizontal.spreadY}px apart vertically and " +
                "${horizontal.spreadX}px horizontally — a row varies in x and " +
                "nothing else",
        )
        // An arc: both axes move, and every item is the same distance out.
        assertTrue(
            fan.spreadX > MinSpread && fan.spreadY > MinSpread,
            "Fan varies by ${fan.spreadX}px in x and ${fan.spreadY}px in y — an " +
                "arc bends, so an arrangement flat in either axis is a row or a " +
                "column wearing the name",
        )
        val radii = fan.centres.map { hypot(it.x - fan.anchor.x, it.y - fan.anchor.y) }
        assertTrue(
            (radii.max() - radii.min()) < RadiusTolerance,
            "Fan's items are ${radii.min()}px to ${radii.max()}px from the button " +
                "— they are meant to be on one arc, and an arc has one radius",
        )
    }

    @Test
    fun theMenuOpensAwayFromTheCornerItIsIn() = runComposeUiTest {
        val bottomEnd = openAt(FabMenuLayout.Vertical, Alignment.BottomEnd)
        val topStart = openAt(FabMenuLayout.Vertical, Alignment.TopStart)

        assertTrue(
            bottomEnd.centres.all { it.y < bottomEnd.anchor.y },
            "a button in the bottom corner opened downward, off the screen — the " +
                "direction is supposed to come from where the button found itself",
        )
        assertTrue(
            topStart.centres.all { it.y > topStart.anchor.y },
            "a button in the top corner opened upward, off the screen",
        )
    }

    /**
     * The defect the geometry was rewritten for.
     *
     * Clamping each item to the window independently is the obvious way to keep
     * them on screen, and once the run is longer than the room every item past
     * the wall clamps to the *same point*. Three actions render as one pile, and
     * the ones underneath cannot be tapped. So: no two items may share a
     * position, however little room there is.
     */
    @Test
    fun aWindowTooShortForTheMenuCompressesItRatherThanStackingIt() = runComposeUiTest {
        val squeezed = openAt(FabMenuLayout.Vertical, height = SqueezedHeight)

        val gaps = squeezed.centres.zipWithNext { a, b -> abs(b.y - a.y) }
        assertTrue(
            gaps.all { it > MinSpread },
            "two items are ${gaps.minOrNull()}px apart in a ${SqueezedHeight}dp " +
                "window — they " +
                "have piled up, so the menu shows fewer actions than it has and " +
                "the ones underneath cannot be reached",
        )
        assertTrue(
            squeezed.centres.all { it.y > 0f && it.y < squeezed.anchor.y },
            "an item is at ${squeezed.centres.map { it.y }} above an anchor at " +
                "${squeezed.anchor.y} in a ${SqueezedHeight}dp window — compressing " +
                "is meant to keep every one of them on screen",
        )
    }

    /**
     * They arrive in order, not together.
     *
     * Sampled mid-flight with the clock held, which is the only place a stagger
     * exists: at rest every item is home and the picture is the same either way.
     *
     * Measured as **each item's fraction of its own journey**, not as raw
     * distance. The items have different distances to cover — the last one is
     * three times as far out as the first — so a comparison of distances would
     * be a comparison of destinations. What a stagger means is that the first
     * item is further along *its* path than the second is along *its*.
     */
    @Test
    fun theItemsLeaveOneAfterAnother() = runComposeUiTest {
        val progress = travelFractions(reduceMotion = false)

        assertTrue(
            progress[0] > progress[1] && progress[1] > progress[2],
            "${StaggerSampleMillis}ms in, the items are $progress of the way out " +
                "— they are moving as one block, so the menu appears all at once " +
                "instead of unfolding",
        )
        assertTrue(
            progress[0] - progress[2] > MinStagger,
            "the first item is only ${progress[0] - progress[2]} ahead of the last " +
                "— that is a wave nobody can see, and the stagger may as well not " +
                "be there",
        )
    }

    /** The same moment, with the preference on: no wave, because a wave is motion. */
    @Test
    fun reducedMotionDropsTheStagger() = runComposeUiTest {
        val progress = travelFractions(reduceMotion = true)

        assertTrue(
            progress.max() - progress.min() < ReducedMotionTolerance,
            "under reduced motion the items are $progress of the way out — they " +
                "are still leaving in sequence, and a sequence drags the eye " +
                "across the screen exactly as the preference asks it not to",
        )
    }

    /**
     * How far each item has come, as a fraction of how far it is going.
     *
     * Both numbers are measured from the same run: the mid-flight sample with
     * the clock held, then the settled one once it has been let go. Deriving the
     * destination rather than declaring it means the test says nothing about
     * spacing — which is a different test's job, and would otherwise have to be
     * kept in step with this one.
     */
    private fun ComposeUiTest.travelFractions(reduceMotion: Boolean): List<Float> {
        mainClock.autoAdvance = false
        val open = mutableStateOf(false)

        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                Window {
                    Menu(
                        expanded = open.value,
                        layout = FabMenuLayout.Vertical,
                        alignment = Alignment.BottomEnd,
                    )
                }
            }
        }
        mainClock.advanceTimeBy(FrameMillis)
        open.value = true

        // Far enough in that the first item is moving, not so far that the last
        // has caught up: the stagger is 28ms an item and the spring runs for a
        // few hundred.
        mainClock.advanceTimeBy(StaggerSampleMillis)
        val midway = travelled()

        mainClock.advanceTimeBy(SettleMillis)
        val settled = travelled()

        return midway.zip(settled) { part, whole -> if (whole > 0f) part / whole else 0f }
    }

    /** How far each item is from the anchor, right now. */
    private fun ComposeUiTest.travelled(): List<Float> {
        val anchor = centreOf(Anchor)
        return Labels.map { abs(centreOf(it).y - anchor.y) }
    }

    // --- harness ---------------------------------------------------------

    private class Opened(val anchor: Offset, val centres: List<Offset>) {
        val spreadX: Float get() = centres.maxOf { it.x } - centres.minOf { it.x }
        val spreadY: Float get() = centres.maxOf { it.y } - centres.minOf { it.y }
    }

    /** Opens a menu in a window of the given shape and reports where everything landed. */
    private fun ComposeUiTest.openAt(
        layout: FabMenuLayout,
        alignment: Alignment = Alignment.BottomEnd,
        height: Int = 640,
    ): Opened {
        setContent {
            // `reduceMotion` so the items are home on the first frame — this is
            // a question about where they end up, and the springs are the
            // subject of a different test.
            KontourTheme(reduceMotion = true) {
                Window(height) {
                    Menu(expanded = true, layout = layout, alignment = alignment)
                }
            }
        }
        waitForIdle()
        return Opened(anchor = centreOf(Anchor), centres = Labels.map { centreOf(it) })
    }

    /**
     * A window of a stated size, rather than whatever the test host hands out.
     *
     * The size has to be imposed *outside* the `OverlayHost`, because the host
     * is what the menu measures itself against — it reads the container it is
     * given and picks its direction and its spacing from that. A `requiredSize`
     * on the host is therefore the whole of what "a short window" means here; a
     * size applied inside it would leave the menu still laying itself out
     * against the full screen.
     */
    @Composable
    private fun Window(height: Int = 640, content: @Composable () -> Unit) {
        Box(Modifier.requiredSize(WindowWidth.dp, height.dp)) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun ComposeUiTest.centreOf(description: String): Offset =
        onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center

    @Composable
    private fun Menu(expanded: Boolean, layout: FabMenuLayout, alignment: Alignment) {
        val state = remember(expanded) { mutableStateOf(expanded) }
        Box(Modifier.fillMaxSize()) {
            FabMenu(
                expanded = state.value,
                onExpandedChange = { state.value = it },
                icon = Tabler.Outline.Plus,
                contentDescription = Anchor,
                modifier = Modifier.align(alignment),
                layout = layout,
                showLabels = false,
                // The anchor renames itself to "Close" while the menu is open,
                // which is right on a real screen and useless here — every
                // measurement below is taken *while* it is open, and a control
                // that changes its name mid-test is a control the test cannot
                // find. Pinned to one name so the subject stays the geometry.
                expandedContentDescription = Anchor,
            ) {
                item(Tabler.Outline.Star, Labels[0]) {}
                item(Tabler.Outline.CurrentLocation, Labels[1]) {}
                item(Tabler.Outline.Stack, Labels[2]) {}
            }
        }
    }

    private companion object {
        const val Anchor = "Add"

        val Labels = listOf("Save stop", "Nearby", "Routes")

        const val WindowWidth = 400

        /** Too short for three items at full spacing, which is the point. */
        const val SqueezedHeight = 200

        const val FrameMillis = 16L

        /** Long enough for the last item's spring to settle. */
        const val SettleMillis = 3000L

        /** One spring in, three staggers out. */
        const val StaggerSampleMillis = 60L

        /** Sub-pixel: two items on one axis should be exactly aligned. */
        const val Tolerance = 1f

        /** Smaller than one item, so anything less is items on top of each other. */
        const val MinSpread = 24f

        /** An arc's radius is one number; rounding to whole pixels is the slack. */
        const val RadiusTolerance = 4f

        /** Under a tenth of a journey apart is a wave nobody sees. */
        const val MinStagger = 0.1f

        const val ReducedMotionTolerance = 0.05f
    }
}
