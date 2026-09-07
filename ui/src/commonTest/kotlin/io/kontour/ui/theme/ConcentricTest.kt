package io.kontour.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * The concentricity API, in numbers.
 *
 * The rule it exists to keep is one sentence — an inner radius is its
 * container's radius minus the space between them — and every call site in the
 * library used to restate it by hand against a token picked by eye. What is
 * worth pinning is not the sentence but the three ways a naive version of it
 * goes wrong: it has to nest, it has to fall back rather than guess, and it has
 * to decline where the question has no answer.
 */
@OptIn(ExperimentalTestApi::class)
class ConcentricTest {

    private val density = Density(1f)

    /** A box big enough that no proportional corner saturates on it. */
    private fun CornerBasedShape.radius(): Float =
        topStart.toPx(Size(400f, 400f), density)

    private fun shapeIn(content: @Composable (@Composable () -> Unit) -> Unit): Float {
        var radius = Float.NaN
        runComposeUiTest {
            setContent {
                KontourTheme {
                    content {
                        radius = Theme.shapes.concentric().radius()
                    }
                }
            }
        }
        return radius
    }

    @Test
    fun outsideAnyContainerItIsTheComponentsOwnDefault() {
        // The property that lets a component use it unconditionally. A `Button`
        // that asks for a concentric corner has to keep its normal one when
        // nothing is wrapping it, or the API can only be used by call sites that
        // already know where they are — which is the knowledge it exists to
        // remove.
        var concentric = Float.NaN
        var control = Float.NaN
        runComposeUiTest {
            setContent {
                KontourTheme {
                    concentric = Theme.shapes.concentric().radius()
                    control = Theme.shapes.control.radius()
                }
            }
        }
        assertEquals(control, concentric, "outside a container this has to be the fallback")
        assertEquals(
            18f,
            concentric,
            "and the fallback is `control`, which on a 400px box is the cap",
        )
    }

    @Test
    fun insideACardItIsTheCardsCornerLessTheCardsPadding() {
        val radius = shapeIn { inner -> Card { inner() } }
        assertEquals(
            22f - 16f,
            radius,
            "a `Card` is `container` (22dp) with `spacing.md` (16dp) of padding, " +
                "so what sits inside it wants 6dp — measured $radius",
        )
    }

    @Test
    fun itNestsToTheThingActuallyAroundIt() {
        // The failure this rules out is measuring against the outermost box. An
        // inner card with a different ring has to win, or a control two levels
        // down gets the corner of something it is nowhere near.
        val radius = shapeIn { inner ->
            Card {
                Card(contentPadding = PaddingValues(4.dp)) { inner() }
            }
        }
        assertEquals(
            22f - 4f,
            radius,
            "the inner card publishes 22dp less its own 4dp ring, so 18 — " +
                "measured $radius, and 6 would mean the outer card won",
        )
    }

    @Test
    fun anUnevenRingDeclinesRatherThanPickingASide() {
        // With 16dp at the sides and 8 top and bottom there is no single inner
        // radius that keeps the ring even: it is genuinely uneven and no corner
        // fixes it. Choosing one of the two numbers would look right on two edges
        // and wrong on the other two, silently. Falling back is the honest answer
        // and it is the one a caller can see.
        val radius = shapeIn { inner ->
            Card(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { inner() }
        }
        assertEquals(
            18f,
            radius,
            "an uneven ring has no concentric answer, so this has to be the " +
                "`control` fallback rather than 6 or 14 — measured $radius",
        )
    }

    @Test
    fun aContainerWithNoCornersToSubtractFromDeclines() {
        // An arbitrary `Shape` is a path. There is no radius in it to take a gap
        // off, so there is nothing to publish.
        val radius = shapeIn { inner ->
            Card(shape = RectangleShape) { inner() }
        }
        assertEquals(
            18f,
            radius,
            "a path-shaped container publishes nothing and the fallback stands",
        )
    }

    @Test
    fun theModifierIsFreeWhereItCannotApply() {
        // `Modifier.concentric()` outside a container must add *nothing* — not a
        // clip to some default shape, which would be a square-cornered clip
        // appearing from nowhere on a component that happened to be placed at the
        // top level.
        var outside: Modifier? = null
        var inside: Modifier? = null
        runComposeUiTest {
            setContent {
                KontourTheme {
                    outside = Modifier.concentric()
                    Card { inside = Modifier.concentric() }
                }
            }
        }
        assertSame(Modifier, outside, "outside a container the modifier is a no-op")
        assertNotSame(Modifier, inside, "inside one it has to clip")
    }
}
