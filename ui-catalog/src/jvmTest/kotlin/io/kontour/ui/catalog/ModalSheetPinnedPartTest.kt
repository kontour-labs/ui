package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetPartPin
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A modal sheet with a part pinned to its top opens, and draws.
 *
 * It opened — its state at `Expanded`, the scrim over the page — with nothing
 * drawn at all: the surface was measured once, before the sheet had anchors, at
 * no height, and the anchors arriving under that same measure did not ask it to
 * measure again. Found by the component gallery's sheet-header demo, whose
 * header was pinned for the first time and whose sheet never appeared. A sheet
 * with no pinned part happened to be measured a second time, and was fine.
 */
class ModalSheetPinnedPartTest {

    @Test
    fun aModalSheetWithAPinnedHeaderIsDrawn() {
        var headerTop = Float.NaN
        var headerHeight = -1
        var rowTop = Float.NaN
        val width = 720
        val height = 1040
        val scene = ImageComposeScene(width, height, Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.Yellow))
                    ModalBottomSheet(visible = true, onDismissRequest = {}) { padding ->
                        part(pin = SheetPartPin.Top) {
                            SheetHeader(
                                modifier = Modifier.onGloballyPositioned {
                                    headerTop = it.positionInRoot().y
                                    headerHeight = it.size.height
                                },
                            ) { +"Departures" }
                        }
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(padding)) {
                            repeat(3) { index ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .then(if (index == 0) Modifier.onGloballyPositioned { rowTop = it.positionInRoot().y } else Modifier),
                                ) { Text("Row $index") }
                            }
                        }
                    }
                }
            }
        }
        val image = try {
            var last: org.jetbrains.skia.Image? = null
            repeat(90) { frame -> last = scene.render(16_000_000L * frame) }
            requireNotNull(last).toComposeImageBitmap().toAwtImage()
        } finally {
            scene.close()
        }

        assertTrue(headerHeight > 0, "the pinned header was measured at no height: $headerHeight")
        assertTrue(rowTop >= headerTop + headerHeight - 1, "the first row at $rowTop is not below the header, $headerTop + $headerHeight")
        // Inside the sheet, under its rows: the surface's own colour, not the
        // page or the scrim over it, both of which are yellow with no blue.
        val inside = image.getRGB(width / 2, height - 20)
        val blue = inside and 0xFF
        assertTrue(blue > 200, "the sheet's surface was not drawn: the pixel under its rows is ${Integer.toHexString(inside)}")
    }
}
