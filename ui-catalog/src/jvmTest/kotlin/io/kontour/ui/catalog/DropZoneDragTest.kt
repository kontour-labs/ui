package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.DropZone
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.DropTargetState
import io.kontour.ui.dragdrop.dragSource
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A drop zone takes what is carried to it within the app, and shows on its
 * ground whether it would: the accent's while it would, the danger colour's
 * while its `accepts` turns the thing away — and then the thing goes home.
 */
class DropZoneDragTest {

    private fun carry(accepts: Boolean): Triple<DropStatus, Color, List<String>> {
        val archived = mutableListOf<String>()
        var card = Rect.Zero
        var zone = Rect.Zero
        lateinit var bin: DropTargetState<String>
        lateinit var colours: ColourScheme
        var status = DropStatus.Idle
        var ground = Color.Unspecified

        Scene(width = 400, height = 500) {
            colours = Theme.colours
            OverlayHost {
                Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .dragSource(Card, "Timetable", label = "Timetable")
                            .reportBounds { card = it },
                    )
                    bin = rememberDropTargetState(Card, label = "Archive", onDrop = { archived += it }, accepts = { accepts })
                    DropZone(bin, Modifier.padding(top = 40.dp).reportBounds { zone = it }) { +"Archive" }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val from = card.center
            val to = zone.center
            // A mouse is a mouse once it has moved: that is what tells the theme.
            scene.move(from - Offset(4f, 4f), PointerType.Mouse)
            scene.frames(2)
            scene.press(from, PointerType.Mouse)
            scene.frame()
            repeat(10) { i ->
                scene.move(from + (to - from) * ((i + 1) / 10f), PointerType.Mouse)
                scene.frame()
            }
            val image = scene.frames(20)
            status = bin.status
            ground = image.colourAt(Offset(zone.left + 20f, zone.center.y))
            scene.release(to, PointerType.Mouse)
            scene.frames(40)
        }
        assertEquals(if (accepts) DropStatus.Over else DropStatus.Refused, status, "the zone should know the card is over it")
        val expected = if (accepts) colours.accent.container else colours.danger.container
        assertTrue(near(ground, expected), "over the zone its ground should be $expected, was $ground")
        return Triple(status, ground, archived)
    }

    @Test
    fun aCardCarriedToTheZoneIsTaken() {
        val (status, _, archived) = carry(accepts = true)
        assertEquals(DropStatus.Over, status)
        assertEquals(listOf("Timetable"), archived)
    }

    @Test
    fun aCardTheZoneTurnsAwayGoesHome() {
        val (status, _, archived) = carry(accepts = false)
        assertEquals(DropStatus.Refused, status)
        assertEquals(emptyList(), archived)
    }

    private fun BufferedImage.colourAt(at: Offset): Color = Color(getRGB(at.x.toInt(), at.y.toInt()))

    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) < 0.03f && abs(a.green - b.green) < 0.03f && abs(a.blue - b.blue) < 0.03f

    private companion object {
        val Card = DragType<String>("card")
    }
}
