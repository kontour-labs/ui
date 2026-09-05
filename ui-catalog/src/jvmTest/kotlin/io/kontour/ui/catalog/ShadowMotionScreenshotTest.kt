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
 * The two shadows that are only wrong while something is moving.
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
 * these animations to a snap: the FAB items are drawn at full scale and full
 * opacity, the pull indicator at full size. Both conditions of the defect are
 * gone, so no existing render can see it — which is why it survived a round that
 * fixed the identical fault three components over.
 *
 * ### What to look for when it moves
 *
 * A **hard-edged rectangle** of grey around a small round button, or around the
 * refresh circle. The shape is the giveaway: a real shadow under a circle is
 * round and fades out, and what this draws is a square with a straight edge
 * where the buffer ended.
 */
class ShadowMotionScreenshotTest {

    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

    /**
     * A `FabMenu` caught with its items part-way out.
     *
     * Each item carries its own `alpha` *and* `scale` off a staggered spring, so
     * a single frame holds several different points on the animation at once —
     * which is more useful than one point would be, because the cut is widest
     * where the scale is smallest.
     */
    @Test
    fun rendersAFabMenuWhileItsItemsAreStillArriving() {
        val file = Screenshot.render(
            name = "shadows-fabmenu",
            width = 900,
            height = 900,
            // Enough to compose, expand and get the stagger moving; short of the
            // spring settling, which is where both conditions stop holding.
            frames = 9,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The menu's items render into the overlay layer, not
                    // inline, so there has to be one for them to land in.
                    OverlayHost {
                    var expanded by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { expanded = true }
                    Box(Modifier.fillMaxSize().padding(Theme.spacing.lg)) {
                        FabMenu(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            icon = Tabler.Outline.Plus,
                            contentDescription = "Add",
                            modifier = Modifier.align(Alignment.BottomEnd),
                            layout = FabMenuLayout.Vertical,
                        ) {
                            item(Tabler.Outline.Star, "Save stop") {}
                            item(Tabler.Outline.CurrentLocation, "Nearby") {}
                        }
                    }
                    }
                }
            }
        }
        assertTrue(file.length() > 0, "shadows-fabmenu rendered an empty file")
    }

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
