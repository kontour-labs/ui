package io.kontour.ui.sheet

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet's content is measured at the height it will actually be given.
 *
 * Reported from a phone, and it is the difference between a long panel you can
 * scroll and a long panel whose bottom rows do not exist. The sheet used to
 * measure its content at `maxHeight = Constraints.Infinity` — so that
 * `SheetDetent.Expanded` could mean "as tall as the content" — and then place
 * that same placeable in whatever room the surface has. The child laid itself
 * out believing it had infinite height; everything past the window's bottom edge
 * was simply not drawn.
 *
 * The part that makes it unrecoverable from outside is that **a scroller cannot
 * save you** — it is worse than that. Measured here: `verticalScroll` inside the
 * unfixed sheet does not scroll badly, it **throws**, with Compose's own
 * "Vertically scrollable component was measured with an infinity maximum height
 * constraints, which is disallowed". A `LazyColumn` is the other half of the
 * same rule: at an infinite viewport it composes every item and ends up as tall
 * as its contents. Both are what the component's own KDoc recommends for long
 * content.
 *
 * ### What this measures, and why it is a constraint rather than a picture
 *
 * The window here is 500dp tall and the content asks for 2000dp. A golden would
 * show a cropped sheet and a *fixed* sheet identically — both draw the top 500dp
 * of something — so the picture cannot tell you which one you have. The
 * constraint can: infinite means the child was lied to, finite means it was
 * told the truth and can lay itself out accordingly.
 *
 * Against the unfixed component the first assertion reports
 * `maxHeight = 2147483647`, which is `Constraints.Infinity`.
 *
 * ### What the fix had to move, and it was not this block
 *
 * The first draft simply took the incoming constraints, and thirteen tests
 * across five classes failed with "nothing was drawn at all". The sheet's
 * surface is sized to `SheetState.containerHeight`, which used to arrive from an
 * `onSizeChanged` on the host — a *layout*-phase callback — so for the whole of
 * the first measure pass it was zero. Measuring the content against zero is
 * worse than measuring it unbounded: `sheetHeight` comes out 0, `Expanded`
 * resolves to "hidden", and the sheet never appears.
 *
 * So the container is read in the measure phase instead, from the host's own
 * constraints, which `fillMaxSize` has already fixed to the window. The first
 * pass is then correct and nothing needs an unbounded fallback — the sheet keeps
 * one only for a host with a genuinely unbounded height, where Compose would
 * refuse a scroller whatever this component did.
 *
 * The `updateAnchors` call stays out of that block deliberately: rebuilding the
 * anchors there resolves every detent from a `sheetHeight` of 0 and settles the
 * sheet at "hidden" before it has been measured, which is the same thirteen
 * failures by a different route.
 */
@OptIn(ExperimentalTestApi::class)
class SheetContentConstraintsTest {

    @Test
    fun contentIsMeasuredAtAFiniteHeightNoTallerThanTheWindow() {
        runComposeUiTest {
            var window = -1
            var offered = -1

            setContent {
                KontourTheme(reduceMotion = true) {
                    Box(
                        Modifier
                            .requiredSize(360.dp, 500.dp)
                            .layout { measurable, constraints ->
                                window = constraints.maxHeight
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                    ) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            ModalBottomSheet(visible = true, onDismissRequest = {}) {
                                Box(
                                    Modifier
                                        .layout { measurable, constraints ->
                                            offered = constraints.maxHeight
                                            val placeable = measurable.measure(constraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.place(0, 0)
                                            }
                                        }
                                        // Far taller than the window, which is
                                        // the whole case: a settings panel at
                                        // 200% type against a 5" phone.
                                        .height(2000.dp)
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()

            assertTrue(
                offered != Constraints.Infinity,
                "the sheet measured its content at maxHeight = Constraints.Infinity " +
                    "($offered), so anything past the window's bottom edge is cropped " +
                    "rather than scrollable — a verticalScroll inside would have an " +
                    "infinite viewport and nothing to scroll",
            )
            assertTrue(
                window > 0 && offered in 1..window,
                "the sheet offered its content maxHeight = $offered in a window " +
                    "$window px tall; it has to be finite and no taller than the room " +
                    "the sheet actually has",
            )
        }
    }

    /**
     * And therefore a scroller inside it has something to scroll.
     *
     * This is the reported case stated as a number. `ScrollState.maxValue` is
     * content minus viewport, and under the old measure those were the same
     * thing: the scroller was handed an unbounded height, measured its child at
     * 2000dp, and reported *itself* as 2000dp tall — so there was nothing to
     * scroll, `maxValue` was 0, and the 1500dp past the window's bottom edge
     * were simply not drawn and not reachable by any gesture.
     *
     * The assertion is deliberately `> 0` rather than an exact figure: what the
     * reader needs is that the overflow is reachable, and pinning the arithmetic
     * would make this a test of the drag handle's height.
     */
    @Test
    fun aScrollerInsideTheSheetHasSomethingToScroll() {
        runComposeUiTest {
            lateinit var scroll: ScrollState

            setContent {
                KontourTheme(reduceMotion = true) {
                    Box(Modifier.requiredSize(360.dp, 500.dp)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            ModalBottomSheet(visible = true, onDismissRequest = {}) {
                                scroll = rememberScrollState()
                                Column(Modifier.verticalScroll(scroll)) {
                                    Box(Modifier.height(2000.dp))
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()

            assertTrue(
                scroll.maxValue > 0,
                "a verticalScroll holding 2000dp inside a 500dp-tall sheet reported " +
                    "maxValue = ${scroll.maxValue}, so it has nothing to scroll — its " +
                    "viewport was measured as tall as its content, and everything past " +
                    "the window's bottom edge is unreachable",
            )
        }
    }
}
