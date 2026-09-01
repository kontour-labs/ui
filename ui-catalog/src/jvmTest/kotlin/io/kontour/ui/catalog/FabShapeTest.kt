package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A collapsed FAB is as wide as it is tall, at every size.
 *
 * Reported as "it's not a perfect circle when not expanded (in small and large
 * variants)", which is a claim about *layout* rather than about the corner —
 * a square box with a capsule corner is a circle, and an oblong one is not,
 * however the corner is specified.
 *
 * Measured rather than eyeballed because the golden cannot answer it: the FAB
 * casts a shadow, so the ink in the image is several pixels wider than the
 * control on every side and a bounding box over it is square whether the FAB is
 * or not.
 */
class FabShapeTest {

    private fun boundsOf(size: FabSize): Rect {
        var bounds = Rect.Zero
        Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    FloatingActionButton(
                        icon = SystemIcons.Plus,
                        contentDescription = "Add",
                        onClick = {},
                        size = size,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { it.frames(3) }
        return bounds
    }

    private fun extendedBoundsOf(size: FabSize): Rect {
        var bounds = Rect.Zero
        Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    ExtendedFloatingActionButton(
                        icon = SystemIcons.Plus,
                        contentDescription = "Add",
                        onClick = {},
                        size = size,
                        expanded = false,
                        modifier = Modifier.reportBounds { bounds = it },
                    ) { +"Add" }
                }
            }
        }.use { it.frames(6) }
        return bounds
    }

    @Test
    fun everyPlainSizeIsSquare() {
        val offenders = FabSize.entries.mapNotNull { size ->
            val b = boundsOf(size)
            if (abs(b.width - b.height) > 1f) "$size is ${b.width}×${b.height}" else null
        }

        assertTrue(
            offenders.isEmpty(),
            "a FAB has to be square or its capsule corner cannot draw a circle: " +
                offenders.joinToString("; "),
        )
    }

    /**
     * And the extended one, collapsed, is the same circle.
     *
     * This is the one that was reported and the one that was wrong. A collapsed
     * `ExtendedFloatingActionButton` is its icon plus a horizontal padding, and
     * that padding was a flat 16dp — which happens to equal `(56 - 24) / 2` and
     * so drew a circle at `Medium` and an oblong at both of its neighbours.
     * Exactly the sizes the report named.
     */
    @Test
    fun everyExtendedSizeIsSquareWhenCollapsed() {
        val offenders = FabSize.entries.mapNotNull { size ->
            val b = extendedBoundsOf(size)
            if (abs(b.width - b.height) > 1f) "$size is ${b.width}×${b.height}" else null
        }

        assertTrue(
            offenders.isEmpty(),
            "a collapsed extended FAB is not square, so it draws a lozenge where " +
                "the plain FAB beside it draws a circle: ${offenders.joinToString("; ")}",
        )
    }
}
