package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuLayout
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shadow that is only wrong while something is moving.
 *
 * Items 4 and 23a, which are one defect wearing two costumes. A `graphicsLayer`
 * with `alpha < 1` composites offscreen into a buffer sized to the **layer's own
 * rectangle**; a shadow bleeds well outside the thing casting it; and `scale < 1`
 * shrinks the opaque shape inside those unchanged bounds and exposes the cut. The
 * result is a hard-edged rectangle of shadow, visible only while the animation is
 * between 0 and 1.
 *
 * That analysis is not new here — it is written out at
 * `overlay/OverlayAppearance.kt`, where it was diagnosed and fixed for menus,
 * popovers and dialogs, and [OverlayMotionScreenshotTest] is the golden that
 * keeps it fixed. The same construction was still in place in two components
 * that overlay code never touched.
 *
 * ### Why this needs its own frame
 *
 * Every other golden in the suite pins `reduceMotion = true`, which collapses
 * these animations to a snap: the pull indicator is drawn at full size, so both
 * conditions of the defect are gone and no existing render can see it — which is
 * why it survived a round that fixed the identical fault three components over.
 *
 * ### `FabMenu` is guarded by its resting goldens instead
 *
 * There was a second frame here, catching a menu with its items part-way out,
 * and it had to go: the anchor's icon turns from a plus to a cross as the menu
 * opens, and its angle is not identical from run to run. 721 pixels past the
 * channel tolerance against a cap of 600 — enough to fail, entirely inside the
 * anchor, and nothing to do with what the frame was watching.
 *
 * A golden that fails at random is worse than no golden, for the reason the
 * harness's own tolerance exists: a suite that cries wolf gets regenerated
 * without being read. And the coverage is not actually lost. The surprise in
 * this stage was that `fabmenu-vertical`, `-horizontal` and `-fan` were drawing
 * grey squares **at rest**, so those three pairs move if this regresses — which
 * is exactly what they did when it was fixed.
 *
 * ### What to look for when it moves
 *
 * A **hard-edged rectangle** of grey around the refresh circle. The shape is the
 * giveaway: a real shadow under a circle is round and fades out, and what this
 * draws is a square with a straight edge where the buffer ended.
 */
class ShadowMotionScreenshotTest {

    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

    /**
     * A `PullToRefresh` indicator part-way through growing in.
     *
     * Driven by `refreshing` rather than by a gesture: the indicator's scale and
     * alpha both animate from the offset spring, so switching refreshing on and
     * catching the spring mid-flight puts it at a partial scale without needing
     * a pointer at all.
     */
    @Test
    fun rendersARefreshIndicatorWhileItIsGrowingIn() {
        val file = Screenshot.render(
            name = "shadows-pulltorefresh",
            width = 700,
            height = 700,
            frames = 4,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var refreshing by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { refreshing = true }
                    PullToRefresh(
                        refreshing = refreshing,
                        onRefresh = {},
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(Theme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ListItem(position = ListItemPosition.First) { +"Perth Underground" }
                            ListItem(position = ListItemPosition.Middle) { +"Elizabeth Quay" }
                            ListItem(position = ListItemPosition.Last) { +"McIver" }
                        }
                    }
                }
            }
        }
        assertTrue(file.length() > 0, "shadows-pulltorefresh rendered an empty file")
    }
}
