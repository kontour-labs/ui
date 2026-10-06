package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.DropTargetState
import io.kontour.ui.dragdrop.ExternalDrops
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A drop zone shows where a drag stands with it: a plain dashed place at rest,
 * an accent edge while something it would take is carried, the accent's ground
 * once that is over it, the danger colour's when it would be turned away — and
 * nothing at all while it is off.
 *
 * Driven from outside the app's side, which a test can set; a real drag within
 * the app is `DropZoneDragTest`'s, in the catalog's scenes.
 */
@OptIn(ExperimentalTestApi::class)
class DropZoneTest {

    /** The zone's ground just inside its edge, and whether its edge has [edge] anywhere down its start side. */
    private fun ComposeUiTest.look(edge: Color): Pair<Color, Boolean> {
        waitForIdle()
        val bounds = onNodeWithTag("zone").fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val ground = image[bounds.left.toInt() + 10, bounds.center.y.toInt()]
        // The first few columns: a hairline at rest is one of them, the strong edge two.
        val edged = (bounds.top.toInt() + 24 until bounds.bottom.toInt() - 24).any { y ->
            (0..2).any { dx -> near(image[bounds.left.toInt() + dx, y], edge) }
        }
        return ground to edged
    }

    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) < 0.03f && abs(a.green - b.green) < 0.03f && abs(a.blue - b.blue) < 0.03f

    @Test
    fun aZoneShowsWhereADragStands() = runComposeUiTest {
        lateinit var state: DropTargetState<Nothing>
        lateinit var colours: ColourScheme
        var enabled by mutableStateOf(true)
        setContent {
            KontourTheme(darkTheme = false) {
                colours = Theme.colours
                Box(Modifier.background(Color.White).padding(16.dp)) {
                    state = rememberDropTargetState(label = "Attachments", external = ExternalDrops.files { _, _ -> })
                    DropZone(state, Modifier.testTag("zone"), enabled = enabled) { +"Drop files here" }
                }
            }
        }
        val accent = colours.accent.solid
        val danger = colours.danger.solid

        look(accent).let { (ground, edged) ->
            assertTrue(near(ground, Color.White), "at rest the zone has no ground of its own: $ground")
            assertFalse(edged, "at rest the edge is not the accent")
        }

        runOnIdle { state.externalStatus = DropStatus.Available }
        look(accent).let { (ground, edged) ->
            assertTrue(near(ground, Color.White), "carried elsewhere, the ground stays clear: $ground")
            assertTrue(edged, "carried elsewhere, the edge turns the accent")
        }

        runOnIdle { state.externalStatus = DropStatus.Over }
        look(accent).let { (ground, edged) ->
            assertTrue(near(ground, colours.accent.container), "over it, the ground is the accent's: $ground")
            assertTrue(edged, "over it, the edge is the accent")
        }

        runOnIdle { state.externalStatus = DropStatus.Refused }
        look(danger).let { (ground, edged) ->
            assertTrue(near(ground, colours.danger.container), "turned away, the ground is the danger colour's: $ground")
            assertTrue(edged, "turned away, the edge is the danger colour")
        }

        runOnIdle {
            state.externalStatus = DropStatus.Over
            enabled = false
        }
        look(accent).let { (ground, edged) ->
            assertTrue(near(ground, Color.White), "off, it shows nothing of a drag: $ground")
            assertFalse(edged, "off, the edge is not the accent")
        }
    }

    @Test
    fun aZoneIsNotNewsWhenItAppears() = runComposeUiTest {
        setContent {
            KontourTheme {
                val state = rememberDropTargetState(label = "Attachments", external = ExternalDrops.files { _, _ -> })
                DropZone(state, Modifier.testTag("zone")) {
                    +"Drop files here"
                    message { +"PDF or images" }
                }
            }
        }
        val node = onNodeWithTag("zone").fetchSemanticsNode()
        assertFalse(
            node.config.contains(SemanticsProperties.LiveRegion) ||
                onNodeWithText("Drop files here", useUnmergedTree = true).fetchSemanticsNode().config
                    .contains(SemanticsProperties.LiveRegion),
            "a drop zone is part of the page, not an announcement",
        )
    }
}
