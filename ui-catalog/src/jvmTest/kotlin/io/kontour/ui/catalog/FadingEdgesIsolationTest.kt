package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A list's faded edge erases the list, and only the list.
 *
 * The fade is `BlendMode.DstOut`, which erases whatever is under it in the
 * layer it is drawn into. Drawn into the list's own isolated layer, that is
 * the list's rows. Drawn anywhere else, it takes the surface the list sits on
 * as well, and whatever is under that shows through: in an app, a map under a
 * sheet showed through the sheet at the top of its list. Here the "map" is red
 * and the sheet white, and anything in the fade but opaque white is the erase
 * reaching too far: red, or nothing, where it took the red with it.
 */
class FadingEdgesIsolationTest {

    @Test
    fun theFadeNeverShowsWhatIsUnderTheSurface() {
        Scene(width = WidthPx, height = HeightPx) {
            Box(Modifier.fillMaxSize().background(Color.Red).padding(40.dp)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val state = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().fadingEdges(state), state = state) {
                        items(60) { Text("Row $it", Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }.use { scene ->
            scene.frames(2)
            // Down and back up, a notch at a time, so the top fade comes and
            // goes and the bottom one goes, every frame checked.
            val wheel = Offset(WidthPx / 2f, HeightPx / 2f)
            val seen = ArrayList<String>()
            for (notch in List(30) { 1f } + List(30) { -1f }) {
                scene.scroll(wheel, Offset(0f, notch))
                repeat(2) {
                    val image = scene.frame()
                    for (y in listOf(TopFadeY, BottomFadeY)) {
                        // White, and opaque: the surface. Anything else is the
                        // erase reaching through it, to the red or, having taken
                        // that too, to nothing at all.
                        val p = image.getRGB(ProbeX, y)
                        val opaqueWhite = (p ushr 24) == 0xFF && listOf(16, 8, 0).all { (p shr it and 0xFF) > 0xF0 }
                        if (!opaqueWhite) seen += "y=$y argb=${Integer.toHexString(p)}"
                    }
                }
            }
            assertTrue(seen.isEmpty(), "the fade erased the surface under the list, showing what is under it: ${seen.take(6)}")
        }
    }

    /**
     * The case it was seen in: a list with faded edges in a floating sheet over
     * a map, scrolled, and the sheet dragged up and down with it.
     */
    @Test
    fun inASheetOverAMapTheFadeNeverShowsTheMap() {
        Scene(width = WidthPx, height = HeightPx) {
            KontourTheme {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.Red))
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Half,
                    )
                    BottomSheet(
                        state = sheet,
                        presentation = SheetPresentation.Floating,
                        containerColour = Color.White,
                        dragHandle = null,
                    ) { padding ->
                        val state = rememberLazyListState()
                        LazyColumn(
                            // A margin of the sheet's own surface either side, which
                            // nothing erases, to find the sheet's top by.
                            Modifier.fillMaxSize().padding(horizontal = 24.dp).fadingEdges(state),
                            state = state,
                            contentPadding = padding,
                        ) {
                            items(60) { Text("Row $it", Modifier.fillMaxWidth().height(40.dp)) }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(20)
            val seen = ArrayList<String>()
            fun check(image: BufferedImage, step: String) {
                val top = (0 until image.height).firstOrNull { image.isOpaqueWhite(MarginX, it) } ?: return
                for (y in top + 4 until minOf(image.height, top + FadePx)) {
                    if (!image.isOpaqueWhite(ProbeX, y)) {
                        seen += "$step y=${y - top} below the sheet's top argb=${Integer.toHexString(image.getRGB(ProbeX, y))}"
                        return
                    }
                }
            }
            val over = Offset(WidthPx / 2f, HeightPx * 0.8f)
            for ((i, notch) in (List(20) { 1f } + List(20) { -1f }).withIndex()) {
                scene.scroll(over, Offset(0f, notch))
                repeat(2) { check(scene.frame(), "wheel $i") }
            }
            // A finger: the list up, the sheet up with it, and back down.
            for (pass in 0 until 3) {
                scene.drag(from = over, to = over - Offset(0f, 400f), steps = 20) { step, image -> check(image, "up $pass/$step") }
                repeat(10) { check(scene.frame(), "settle up $pass") }
                scene.drag(from = over - Offset(0f, 300f), to = over, steps = 20) { step, image -> check(image, "down $pass/$step") }
                repeat(10) { check(scene.frame(), "settle down $pass") }
            }
            assertTrue(seen.isEmpty(), "the fade erased the sheet, showing the map under it: ${seen.take(6)}")
        }
    }

    private fun BufferedImage.isOpaqueWhite(x: Int, y: Int): Boolean {
        val p = getRGB(x, y)
        return (p ushr 24) == 0xFF && listOf(16, 8, 0).all { (p shr it and 0xFF) > 0xF0 }
    }

    private companion object {
        const val WidthPx = 600
        const val HeightPx = 800

        /** Past the rows' text, on the white surface, inside the 80px inset. */
        const val ProbeX = 480

        /** Inside the top fade, and inside the bottom one, past the 80px inset. */
        const val TopFadeY = 90
        const val BottomFadeY = 710

        /**
         * In the floating sheet, which is 12dp in from the window's sides, and in
         * the 24dp of its surface either side of the list.
         */
        const val MarginX = 60

        /** The top of the list's fade, 24dp, short of where it lifts. */
        const val FadePx = 44
    }
}
