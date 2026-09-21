package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A part arrives when the sheet is big enough for it, and not before.
 *
 * `part(from = SheetDetent.Half) { Header() }` is the sheet saying what it
 * contains at each of its sizes, once, rather than the caller re-deciding on
 * every frame of a drag. A sheet collapsed around a search field is the same
 * sheet as the one showing a header above it, and two code paths that have to
 * agree about that is the arrangement this replaces.
 *
 * ### What it is measured by
 *
 * A flat colour nothing else in the scene paints, counted. The part is a
 * fixed-height box of it, so "is it there" is a pixel count with two answers far
 * apart rather than a threshold — and counting rather than looking for an edge is
 * what makes the reading survive the part being half-way through its reveal.
 *
 * The sheet is driven by `animateTo` rather than by a finger. What is under test
 * is the rule — *this big, therefore this content* — and a gesture would be
 * testing the anchors on the way to it.
 */
class SheetPartsTest {

    @Test
    fun aPartArrivesWithTheDetentItDeclares() {
        var target by mutableStateOf<SheetDetent>(Bar)
        var atBar = 0
        var atHalf = 0
        var backAtBar = 0

        Scene(width = 600, height = 900) {
            KontourTheme(reduceMotion = true) {
                val state = rememberSheetState(
                    detents = listOf(Bar, SheetDetent.Half),
                    initialDetent = Bar,
                )
                // Driven from inside the scene: `animateTo` suspends, and what
                // is under test is where the sheet ends up rather than how it
                // was asked to get there.
                LaunchedEffect(target) { state.animateTo(target) }
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    BottomSheet(state = state, containerColour = Ground) {
                        part(from = SheetDetent.Half) {
                            Box(Modifier.fillMaxWidth().height(40.dp).background(HeaderColour))
                        }
                        part {
                            Box(Modifier.fillMaxWidth().height(40.dp).background(AlwaysColour))
                        }
                    }
                }
            }
        }.use { scene ->
            atBar = scene.frames(40).count(HeaderRgb)
            target = SheetDetent.Half
            atHalf = scene.frames(60).count(HeaderRgb)
            target = Bar
            backAtBar = scene.frames(60).count(HeaderRgb)
        }

        assertTrue(
            atBar == 0,
            "the header drew ${atBar}px at the bar detent, where it declared " +
                "itself `from = Half`. A part that is always there is a part " +
                "whose `from` does nothing",
        )
        assertTrue(
            atHalf > Substantial,
            "the header drew ${atHalf}px at Half, where a 40dp band across a " +
                "600px sheet is about ${40 * 2 * 600}px. Either the part never " +
                "arrived or the sheet never got there",
        )
        assertTrue(
            backAtBar == 0,
            "the header drew ${backAtBar}px after the sheet went back to the bar " +
                "detent — it arrives with its detent and it has to leave with it",
        )
    }

    /** A part with no `from` is there at every size, which is the other half. */
    @Test
    fun aPartWithoutADetentIsAlwaysThere() {
        var target by mutableStateOf<SheetDetent>(Bar)
        var atBar = 0
        var atHalf = 0

        Scene(width = 600, height = 900) {
            KontourTheme(reduceMotion = true) {
                val state = rememberSheetState(
                    detents = listOf(Bar, SheetDetent.Half),
                    initialDetent = Bar,
                )
                // Driven from inside the scene: `animateTo` suspends, and what
                // is under test is where the sheet ends up rather than how it
                // was asked to get there.
                LaunchedEffect(target) { state.animateTo(target) }
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    BottomSheet(state = state, containerColour = Ground) {
                        part {
                            Box(Modifier.fillMaxWidth().height(40.dp).background(AlwaysColour))
                        }
                    }
                }
            }
        }.use { scene ->
            atBar = scene.frames(40).count(AlwaysRgb)
            target = SheetDetent.Half
            atHalf = scene.frames(60).count(AlwaysRgb)
        }

        assertTrue(atBar > Substantial, "a part with no detent drew ${atBar}px at the bar detent")
        assertTrue(atHalf > Substantial, "a part with no detent drew ${atHalf}px at Half")
    }

    private fun BufferedImage.count(rgb: Int): Int {
        var found = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) and 0xFFFFFF) == rgb) found++
            }
        }
        return found
    }

    private companion object {
        /** Tall enough to show a 40dp part and short enough to be below `Half`. */
        val Bar = SheetDetent.height("bar", 72.dp)

        val Ground = Color(0xFF3355AA)
        val HeaderColour = Color(0xFFEE2211)
        const val HeaderRgb = 0xEE2211
        val AlwaysColour = Color(0xFF22EE11)
        const val AlwaysRgb = 0x22EE11

        /**
         * More ink than an antialiased edge and far less than a whole band.
         *
         * A 40dp band across a 600px-wide sheet is about 48,000px at this
         * density, so anything in the thousands is the band and nothing else is.
         */
        const val Substantial = 5_000
    }
}
