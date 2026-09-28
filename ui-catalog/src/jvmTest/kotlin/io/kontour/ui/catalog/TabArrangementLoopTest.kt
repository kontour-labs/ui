package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The arrangement a fixed tab could not use, and can.
 *
 * `TabBar` used to centre a tab's label with `Arrangement.Center` and carry the
 * gap on the badge's own padding, because the tidier
 * `Arrangement.spacedBy(xs, CenterHorizontally)` made this row — itself a
 * weighted child of the bar — never reach an idle frame:
 * `ComponentContractTest` spun in `waitForIdle` to its one-minute deadline, on
 * all six contracts at once. The note left behind said the cause was in there
 * somewhere and had not been chased further than that.
 *
 * **Chased, and it does not reproduce.** Not in the hand-built shape below, not
 * in the real component, and not in `ComponentContractTest`, which passes all
 * seven. So the workaround is gone and this is what stands in its place: a
 * workaround whose cause has stopped existing makes the code read as though it
 * still does, and the only safe way to remove one is to keep asking its
 * question.
 *
 * ### Bounded, so a repro cannot become the thing it is testing
 *
 * `waitForIdle` is exactly what hangs, so nothing here calls it.
 * `Scene.stillAnimating` is `hasInvalidations()` — the same question with a
 * frame count instead of a deadline. A tree that has settled reports false; one
 * that re-invalidates itself every frame reports true forever, and forty frames
 * is far past anything with a real reason to still be working.
 */
class TabArrangementLoopTest {

    @Test
    fun aWeightedRowWithACentredSpacedArrangementSettles() {
        val settled = settles(Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally))
        val control = settles(Arrangement.Center)

        assertTrue(
            control,
            "the control did not settle either, so this test is measuring the " +
                "harness rather than the arrangement. `Arrangement.Center` is " +
                "what `TabBar` used to ship, and if it hangs too then the " +
                "difference this file is about is not the difference being seen.",
        )
        assertTrue(
            settled,
            "a weighted row arranged with `spacedBy(8.dp, CenterHorizontally)` " +
                "never stopped invalidating. This is the shape `TabBar` ships " +
                "again, and the hang it was written to avoid is back — which is " +
                "worth knowing here, in seconds, rather than as six contract " +
                "tests timing out a minute apart.",
        )
    }

    /**
     * The real component, which is the only thing that has ever shown it.
     *
     * The hand-built shape above is a reconstruction and could always be
     * missing the thing that mattered — the travelling indicator, say, since an
     * `Animatable` that never arrives is a tree that is never idle and looks
     * exactly like a layout loop from outside `waitForIdle`. This asks the real
     * component, so there is nothing left to have left out.
     */
    @Test
    fun theRealTabBarSettles() {
        var selected by mutableStateOf(0)
        Scene(width = 360, height = 200, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TabBar {
                    listOf("Departures", "Alerts", "Saved").forEachIndexed { index, label ->
                        Tab(
                            selected = index == selected,
                            onClick = { selected = index },
                            key = label,
                            badge = if (index == 1) 2 else null,
                        ) { +label }
                    }
                }
            }
        }.use { scene ->
            scene.advance(SettleFrames)
            assertTrue(
                !scene.stillAnimating(),
                "a settled `TabBar` was still asking for frames after " +
                    "$SettleFrames of them. Nothing is moving: the indicator is " +
                    "under the selected tab and no gesture is in flight.",
            )
        }
    }

    /** The tab bar's shape: a weighted tab, holding a weighted label row. */
    /** The tab bar's shape: a weighted tab, holding a weighted label row. */
    private fun settles(arrangement: Arrangement.Horizontal): Boolean {
        Scene(width = 600, height = 200, reduceMotion = true) {
            Row(
                modifier = Modifier.fillMaxSize().background(Color.White),
                horizontalArrangement = Arrangement.Start,
            ) {
                repeat(3) { index ->
                    Row(
                        modifier = Modifier.weight(1f).height(48.dp),
                        horizontalArrangement = arrangement,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Departures $index")
                        }
                        Box(Modifier.height(16.dp).fillMaxWidth(0f))
                    }
                }
            }
        }.use { scene ->
            scene.advance(SettleFrames)
            return !scene.stillAnimating()
        }
    }

    private companion object {
        /** Far past anything with a reason to still be working. */
        const val SettleFrames = 40
    }
}
