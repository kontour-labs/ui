package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.RadioGroup
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The dot of a newly chosen option grows all the way.
 *
 * A radio button in a group shrinks its dot by a third while a *sibling* is
 * being pressed, so the option being left goes as the option being taken
 * arrives. The yield is driven by a counter on the group, and the counter went
 * up and never came down: `DisposableEffect`'s `onDispose` re-read `pressed`
 * through its delegate, and by the time dispose runs the press has already
 * ended, so the effect that took the count returned without giving it back.
 *
 * Every option then believed a sibling was permanently held, and the one that
 * *had* the selection sat at two thirds of its size for the life of the screen.
 * Reported as "the inner circle does not expand to full size when choosing an
 * option that was not already selected", which is exactly what it is.
 *
 * ### Measured against the same control before it was touched
 *
 * The at-rest dot is photographed first and is the yardstick, so this asserts
 * nothing about how big a dot ought to be — only that choosing one does not
 * leave it smaller than one nobody has interacted with. A test written against
 * a hard-coded diameter would have to be updated by whoever changes the dot,
 * and would pass at 67% of a new number.
 */
class RadioGroupYieldTest {

    @Test
    fun theChosenOptionsDotGrowsAllTheWayBack() {
        var selected by mutableStateOf("Train")
        var bounds = Rect.Zero
        var rest = 0
        var chosen = 0
        var abandoned = 0

        Scene(width = 700, height = 260) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                RadioGroup(
                    options = listOf("Train", "Bus"),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.reportBounds { bounds = it },
                    label = { it },
                )
            }
        }.use { scene ->
            val settled = scene.frames(30)
            assertTrue(bounds.height > 0f, "the radio group never reported a size")

            rest = settled.dotWidth(bounds, row = 0)

            // Tap the second option — press and release, which is the gesture
            // the counter is supposed to survive.
            scene.tap(bounds.rowCentre(1))
            val after = scene.frames(120)

            chosen = after.dotWidth(bounds, row = 1)
            abandoned = after.dotWidth(bounds, row = 0)
        }

        assertTrue(
            rest > 4,
            "the selected option's dot measured ${rest}px across before anything " +
                "was touched, which is too small to be a dot — the scan is " +
                "finding something else",
        )
        assertTrue(
            chosen >= rest - 1,
            "the option just chosen drew a ${chosen}px dot where the option " +
                "already selected drew ${rest}px. A newly chosen option has to " +
                "come all the way back to full size; short of it means the " +
                "group still believes one of its buttons is being held.",
        )
        assertTrue(
            abandoned < rest / 2,
            "the option that lost the selection still draws a ${abandoned}px " +
                "dot against the ${rest}px of a selected one",
        )
    }
}

/**
 * The widest run of ink across the trailing control on [row] of a two-row group.
 *
 * The button sits at the row's trailing edge, so the last 70px of the group's
 * width crosses its ring and its dot and nothing else. Along the row's centre
 * line the ring contributes two runs a stroke wide and the dot contributes one
 * the width of the dot — so the widest of them is the measurement, without this
 * having to know how many runs to expect or how large a dot should be.
 */
private fun BufferedImage.dotWidth(group: Rect, row: Int): Int {
    val centre = group.rowCentre(row)
    val from = (group.right - 70f).toInt()
    val to = group.right.toInt()
    return runsIn(centre.y.toInt())
        .filter { it.first >= from && it.last <= to }
        .maxOfOrNull { it.last - it.first + 1 } ?: 0
}

/** The centre of [row] of two equal rows. */
private fun Rect.rowCentre(row: Int): Offset =
    Offset(right - 35f, top + height * (row * 2 + 1) / 4f)
