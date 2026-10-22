package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A segmented control scrolled into view has its thumb, shadow and all, on the
 * first frame it is seen.
 *
 * Reported 2026-10-21 for 0.5.0, on the desktop gallery: *"with segmented
 * controls the shadows take a few frames to load in after you scroll the
 * element into view."* The gallery's pages are `LazyColumn`s, so a demo is
 * composed on the frame it scrolls in, and `SelectionIndicatorBox` treated the
 * thumb as appearing rather than as part of a control that was. Measured on a
 * control brought in by one jump of a lazy list:
 *
 * ```
 * frames 0-4    no thumb at all       19,162 px off the settled control
 * frames 5-11   thumb, no shadow      ~7,250 px off, the shadow's footprint
 * frame 12      whole                      0
 * ```
 *
 * The thumb's white fill barely shows on the light well, so what a reader saw
 * was the shadow, cut off by the fading layer's clip and arriving whole on the
 * fade's last frame. See `Arrival` in `SelectionIndicator.kt`.
 *
 * ### Not shadows in general
 *
 * An elevated [Card] brought in the same way is whole on its first frame, and
 * so is either control in a plain scrolling `Column`, which composes it long
 * before it is seen and lets the fade finish off screen. Both are checked
 * here, so that the next report of a late shadow can be told from this one.
 *
 * Measured against the same control left to settle, inside its own bounds, or
 * a card's shadow's reach outside them, on each of the [Watched] frames from
 * the first it is seen: no pixel may be more than [Tolerance] levels off. Every
 * frame rather than only the first, because the shadow was the last thing to
 * arrive, and a thumb drawn on time and still faded in would pass the first. The thumb's shadow is clipped to the track, so the control's
 * bounds hold all of it.
 */
class SegmentedShadowArrivalTest {

    @Test
    fun aSegmentedControlScrolledInByALazyListIsWholeOnItsFirstFrame() =
        assertWhole("a segmented control in a LazyColumn", lazy = true, bleed = 0f) { SegmentedThumb(it) }

    @Test
    fun aSegmentedControlScrolledInByAColumnIsWholeOnItsFirstFrame() =
        assertWhole("a segmented control in a scrolling Column", lazy = false, bleed = 0f) { SegmentedThumb(it) }

    @Test
    fun anElevatedCardScrolledInByALazyListIsWholeOnItsFirstFrame() =
        assertWhole("an elevated card in a LazyColumn", lazy = true, bleed = CardBleed) { RaisedCard(it) }

    private fun assertWhole(what: String, lazy: Boolean, bleed: Float, content: @Composable (Modifier) -> Unit) {
        val (seen, settled, bounds) = arrival(lazy, content)
        val region = Rect(bounds.left - bleed, bounds.top - bleed, bounds.right + bleed, bounds.bottom + bleed)
        val late = seen.mapIndexedNotNull { index, frame ->
            val (count, worst) = difference(frame, settled, region)
            if (worst > Tolerance) "  · frame $index: $count pixels off, by up to $worst levels" else null
        }
        assertTrue(
            late.isEmpty(),
            "$what is not whole from the first frame it is on screen. Something " +
                "in it is arriving late — for a segmented control, the thumb and " +
                "its shadow:\n" + late.joinToString("\n"),
        )
    }

    /**
     * The first [Watched] frames [content] is on screen after a jump far down
     * the page, and the same scene settled.
     */
    private fun arrival(lazy: Boolean, content: @Composable (Modifier) -> Unit): Triple<List<BufferedImage>, BufferedImage, Rect> {
        var bounds = Rect.Zero
        val list = LazyListState()
        var column: ScrollState? = null
        Scene(Side, Side) {
            Box(Modifier.fillMaxSize().background(Theme.colours.background)) {
                val subject = @Composable {
                    Box(Modifier.padding(Theme.spacing.xl)) {
                        content(Modifier.width(300.dp).reportBounds { bounds = it })
                    }
                }
                if (lazy) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        item { Spacer(Modifier.height(Fold)) }
                        item { subject() }
                        item { Spacer(Modifier.height(Fold)) }
                    }
                } else {
                    val scroll = rememberScrollState().also { column = it }
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Spacer(Modifier.height(Fold))
                        subject()
                        Spacer(Modifier.height(Fold))
                    }
                }
            }
        }.use { scene ->
            scene.frames(30)
            // One jump, the whole control in at once: what a wheel notch does
            // to a page, and what a finger's drag does not.
            onComposeUiThread {
                if (lazy) {
                    list.requestScrollToItem(1)
                } else {
                    column!!.dispatchRawDelta(Fold.value * scene.density)
                }
            }
            val first = scene.frame()
            check(bounds.bottom in 0f..Side.toFloat()) { "the subject is not on screen: $bounds" }
            val seen = listOf(first) + List(Watched - 1) { scene.frame() }
            return Triple(seen, scene.frames(60), bounds)
        }
    }

    /** How many pixels of [region] differ between [a] and [b], and by how much at most. */
    private fun difference(a: BufferedImage, b: BufferedImage, region: Rect): Pair<Int, Int> {
        var count = 0
        var worst = 0
        for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(a.height)) {
            for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(a.width)) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                val off = maxOf(
                    abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)),
                    abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)),
                    abs((p and 0xFF) - (q and 0xFF)),
                )
                if (off > Tolerance) count++
                worst = maxOf(worst, off)
            }
        }
        return count to worst
    }

    private companion object {
        const val Side = 800

        /** Further down than the scene is tall, so the subject starts well below the fold. */
        val Fold = 1200.dp

        /** How far an elevated card's shadow reaches outside it, in pixels, with room. */
        const val CardBleed = 60f

        /** Frames watched from the first: past the twelve the shadow used to take, with room. */
        const val Watched = 16

        /** Levels a pixel may be off. The fault was 23 at worst; a settled scene redraws exactly. */
        const val Tolerance = 2
    }
}

@Composable
private fun SegmentedThumb(modifier: Modifier) =
    SegmentedControl(
        options = listOf("Day", "Week", "Month"),
        selectedIndex = 0,
        onSelectedIndexChange = {},
        modifier = modifier,
    )

@Composable
private fun RaisedCard(modifier: Modifier) =
    Card(modifier = modifier) { Text("Perth Station") }
