package io.kontour.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * A squircle's clip proxy is the squircle's own corners as a rounded rectangle —
 * the corners it actually draws, not the ones it was asked for.
 *
 * The proxy was a `RoundedCornerShape` of the squircle's corner sizes, and a
 * rounded rectangle settles a corner too big for its box differently from the
 * squircle. It scales a pair down only once the two on one side overlap; the
 * squircle clamps every corner to half the short side and then shares each edge
 * between its two corners. So a row with 24dp corners at the top and 4dp at the
 * bottom, 40dp tall, drew 20dp corners and clipped its content at 24 — cutting
 * into what sat inside the squircle's edge.
 */
class SquircleClipProxyTest {

    private val density = Density(1f)

    private fun Outline.radii(): List<Float> {
        val r = (this as? Outline.Rounded ?: fail("the proxy is not a rounded rectangle: $this")).roundRect
        return listOf(
            r.topLeftCornerRadius.x,
            r.topRightCornerRadius.x,
            r.bottomRightCornerRadius.x,
            r.bottomLeftCornerRadius.x,
        )
    }

    @Test
    fun aCornerTooBigForItsBoxIsClippedWhereTheSquircleDrawsIt() {
        val row = SquircleShape(CornerSize(24.dp), CornerSize(24.dp), CornerSize(4.dp), CornerSize(4.dp))
        val size = Size(200f, 40f)

        val radii = row.clipProxy().createOutline(size, LayoutDirection.Ltr, density).radii()
        assertEquals(
            listOf(20f, 20f, 4f, 4f),
            radii,
            "24dp corners on a 40px row draw at 20 — half the short side — and the clip " +
                "has to stop there too, or it cuts into what is inside the drawn edge",
        )
    }

    @Test
    fun anOrdinaryCardIsClippedAsBefore() {
        val card = SquircleShape(22.dp)
        val size = Size(320f, 200f)
        assertEquals(
            RoundedCornerShape(22.dp).createOutline(size, LayoutDirection.Ltr, density).radii(),
            card.clipProxy().createOutline(size, LayoutDirection.Ltr, density).radii(),
            "a card whose corners fit its box clips exactly where a rounded rectangle of them does",
        )
    }

    /** Already right before the proxy changed, and kept right by it. */
    @Test
    fun theProxyFollowsTheLayoutDirection() {
        val leading = SquircleShape(CornerSize(24.dp), CornerSize(4.dp), CornerSize(4.dp), CornerSize(24.dp))
        val radii = leading.clipProxy().createOutline(Size(40f, 200f), LayoutDirection.Rtl, density).radii()
        assertEquals(listOf(4f, 20f, 20f, 4f), radii, "right to left, the leading corners are on the right")
    }
}
