package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import io.kontour.ui.components.selection.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row of stars is swipeable.
 *
 * It was five separate tap targets, which is the one interaction nobody tries
 * first — every rating widget anyone has used is set by dragging across it. The
 * taps still work, because the drag waits for touch slop.
 *
 * The marks are found by measuring rather than by dividing the row: each one is
 * grown to a 48dp touch target with a 4dp gap between, so `width / count` drifts
 * by a third of a mark by the fifth star. The last case here is what that drift
 * would break.
 */
class RatingDragTest {

    @Test
    fun draggingAcrossTheMarksSetsTheScore() {
        var score by mutableStateOf(0f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the rating never reported a size")
            scene.drag(from = bounds.alongX(0.05f), to = bounds.alongX(0.75f))
            scene.frames(2)
        }

        assertEquals(4f, score, "dragging to the fourth mark set $score")
    }

    @Test
    fun aDragBackLowersIt() {
        var score by mutableStateOf(5f)
        var bounds = Rect.Zero
        val seen = mutableListOf<Float>()

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it; seen += it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.95f), to = bounds.alongX(0.05f))
            scene.frames(2)
        }

        assertEquals(1f, score, "dragging back to the first mark left it at $score")
        assertTrue(
            seen == seen.sortedDescending(),
            "the score did not come down one mark at a time: $seen",
        )
    }

    @Test
    fun halfMarksAreOffUnlessAskedFor() {
        // The contract half of this: a caller who never opted in must not start
        // receiving 3.5 because the drag exists.
        var score by mutableStateOf(0f)
        var bounds = Rect.Zero
        val seen = mutableListOf<Float>()

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it; seen += it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.05f), to = bounds.alongX(0.95f), steps = 40)
            scene.frames(2)
        }

        assertTrue(
            seen.all { it == it.toInt().toFloat() },
            "a rating without allowHalf emitted a fraction: $seen",
        )
    }

    @Test
    fun withHalfMarksTheLeftOfAMarkIsAHalf() {
        var score by mutableStateOf(0f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it },
                    allowHalf = true,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            // Into the left edge of the third mark. Five marks, so a mark is a
            // fifth of the row: the third starts at 0.4 and its middle is 0.5.
            scene.drag(from = bounds.alongX(0.05f), to = bounds.alongX(0.43f), steps = 30)
            scene.frames(2)
        }

        assertEquals(2.5f, score, "the left half of the third mark gave $score")
    }

    @Test
    fun theLastMarkIsReachable() {
        // What dividing the row by `count` gets wrong. The marks are 48dp with
        // 4dp between, so the fifth one's centre sits well past `4.5 / 5` of the
        // row and a naive mapping selects the fourth.
        var score by mutableStateOf(0f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.05f), to = bounds.alongX(0.99f), steps = 30)
            scene.frames(2)
        }

        assertEquals(5f, score, "dragging to the far right of the row gave $score")
    }
}
