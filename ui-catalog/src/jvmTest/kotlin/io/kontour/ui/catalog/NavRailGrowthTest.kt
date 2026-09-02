package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rail that *grows* into its expanded form rather than jumping into it.
 *
 * The rail's width has always animated. What did not was its contents: they
 * switched from a stacked column of icons to inline icon-and-label rows on the
 * frame `expanded` flipped, while the rail was still 88dp wide. The labels
 * appeared in a column with no room for them and were clipped until the width
 * caught up — a rail that looks broken for a quarter of a second every time it
 * opens.
 *
 * ### Measured by when the labels arrive, not by what they look like
 *
 * A still golden cannot show this: the start and end states were always right
 * and the fault lived entirely in between. So this drives the animation frame by
 * frame and asks one question — **at the frame the labels first appear, how wide
 * is the rail?** Under the fix, wide enough to hold them. Before it, 88dp.
 *
 * ### Counted by area, not by extent
 *
 * The first version of this asked how far right the rail was drawing, and it
 * was wrong: a collapsed rail centres its icons, so the icons drift rightwards
 * as the rail widens and cross any fixed line long before a label exists. Ink
 * *area* has no such problem — three words of text are twice as many pixels as
 * three glyphs however either of them is aligned. The step is from about 2,800
 * pixels to about 4,900, which no amount of drift is going to fake.
 *
 * ### Two colours doing the measuring
 *
 * The page behind the rail is magenta, because the rail's own surface is white
 * in the light scheme and an edge between two whites cannot be found. And the
 * selection indicator is transparent, because the accent pill runs the full
 * width of the row it marks and would swamp the count on its own.
 */
class NavRailGrowthTest {

    private val items = listOf(
        NavItem(label = "Nearby", icon = Tabler.Outline.MapPin, onClick = {}),
        NavItem(label = "Routes", icon = Tabler.Outline.Bus, onClick = {}),
        NavItem(label = "Saved", icon = Tabler.Outline.Star, onClick = {}),
    )

    @Test
    fun theLabelsWaitForTheRailToBeWideEnoughForThem() {
        var expanded by mutableStateOf(false)
        var widthWhenLabelsArrived = -1
        var arrivedAtFrame = -1

        Scene(width = 800, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NavRail(
                    items = items,
                    selectedIndex = 0,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    showLabels = true,
                    indicatorColour = Color.Transparent,
                )
            }
        }.use { scene ->
            val collapsed = scene.frames(8)
            assertTrue(
                collapsed.ink() < LabelInk,
                "a collapsed rail already draws ${collapsed.ink()} pixels, so this " +
                    "measurement cannot tell a label from an icon",
            )

            expanded = true
            for (frame in 0 until 90) {
                val image = scene.frame()
                if (arrivedAtFrame >= 0) continue
                if (image.ink() >= LabelInk) {
                    arrivedAtFrame = frame
                    widthWhenLabelsArrived = image.railWidth()
                }
            }
        }

        assertTrue(arrivedAtFrame >= 0, "the labels never appeared at all")
        assertTrue(
            widthWhenLabelsArrived >= Halfway,
            "the labels arrived on frame $arrivedAtFrame, when the rail was " +
                "${widthWhenLabelsArrived}px wide against the ${Halfway}px they " +
                "need — they are laid out inline in a rail that has not grown " +
                "yet, and clipped until it does",
        )
    }

    /** How far right the rail's surface reaches, which is how wide it is. */
    private fun BufferedImage.railWidth(): Int {
        val y = height / 2
        for (x in 0 until width) if (isPage(getRGB(x, y))) return x
        return width
    }

    /** How many pixels of the rail are something other than its own surface. */
    private fun BufferedImage.ink(): Int {
        var drawn = 0
        for (y in 0 until height) {
            val background = getRGB(2, y)
            for (x in 0 until width) {
                if (isPage(getRGB(x, y))) break
                if (getRGB(x, y) != background) drawn++
            }
        }
        return drawn
    }

    /** Whether this pixel is the page behind the rail rather than the rail. */
    private fun isPage(rgb: Int): Boolean = (rgb and 0xFFFFFF) == 0xFF00FF

    private companion object {
        /** 88dp and 280dp at this scene's density of 2. */
        const val CollapsedPx = 176
        const val ExpandedPx = 560
        const val Halfway = (CollapsedPx + ExpandedPx) / 2

        /**
         * More ink than this is a rail with labels on it.
         *
         * Three icons and a chevron come to about 2,800 pixels; the same rail
         * with three labels beside them comes to about 4,900. Halfway between is
         * a line neither state goes near.
         */
        const val LabelInk = 3_500
    }
}
