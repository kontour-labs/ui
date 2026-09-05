package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.PageTransition
import io.kontour.ui.motion.sharedElement
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A shared title arrives whole, on every frame of the way.
 *
 * Reported as "Perth Underground" rendering as "Perth" mid-transition. It is not
 * a clip, though it looks like one: Compose's `sharedElement` re-measures its
 * child against the *animated* bounds every frame, and re-measuring text is
 * re-laying it out. At an intermediate width narrower than the target's, a title
 * soft-wraps; the second line falls outside the animated one-line height; and
 * `Text`'s default `TextOverflow.Clip` removes it with no ellipsis to mark the
 * loss. A **measure**-phase truncation wearing a clip's clothes.
 *
 * Traced across a morph between two type scales, before the fix — the string is
 * *narrower than at either end of the transition* for a third of it:
 *
 * ```
 * at rest, small: 206px
 *   f3: 248   f6: 157   f9:  157      ← eight frames stuck at 157,
 *   f4: 322   f7: 157   f10: 157        full height, a fraction of the string
 *   f5: 157   f8: 157   f13: 553
 * at rest, large: 553px
 * ```
 *
 * ### Why the existing test could not see it
 *
 * `PageTransitionTest`'s subject is a solid magenta `Box` with a fixed `.size()`.
 * It has no intrinsic content width, so it re-measures to the animated bounds
 * identically however it is driven, and the assertions are about its bounding
 * rectangle rather than about anything inside. A shared element only truncates
 * if it has content that can be laid out two ways.
 */
class SharedTextTest {

    @Test
    fun aSharedTitleIsNeverNarrowerThanAtEitherEnd() {
        var detail by mutableStateOf(false)
        var small = 0
        var large = 0
        val widths = mutableListOf<Int>()

        Scene(width = 800, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                PageTransition(target = detail, modifier = Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Room for the whole string at either size, so nothing
                        // here wraps at rest and a narrow frame can only be the
                        // transition's doing.
                        Box(Modifier.width(600.dp)) {
                            Text(
                                text = Title,
                                modifier = Modifier.sharedElement(TitleKey),
                                style = if (page) {
                                    Theme.typography.displaySmall
                                } else {
                                    Theme.typography.labelSmall
                                },
                                colour = Color.Black,
                            )
                        }
                    }
                }
            }
        }.use { scene ->
            small = scene.frames(40).inkWidth()
            detail = true
            repeat(Frames) { widths += scene.frame().inkWidth() }
            large = scene.frames(60).inkWidth()
        }

        assertTrue(
            small > 100 && large > small * 2,
            "the two resting states were ${small}px and ${large}px, which is not the " +
                "morph this test is about — nothing below would mean anything",
        )

        val floor = small - Slack
        val pinched = widths.withIndex().filter { it.value < floor }
        assertTrue(
            pinched.isEmpty(),
            "the title was drawn narrower than it is at *either* end of the " +
                "transition on ${pinched.size} of $Frames frames — ${small}px small, " +
                "${large}px large, and as little as ${widths.min()}px on the way. " +
                "That is the string being re-laid-out at an intermediate width, " +
                "wrapping, and losing the line that does not fit. Frames: " +
                pinched.take(8).joinToString { "f${it.index}=${it.value}" },
        )

        // ...and it is a morph, not a jump. Measuring only the floor above would
        // pass a shared element that snapped to its final size on frame one,
        // which is what asking for the lookahead size *without* a content scale
        // does — tried, measured, and rejected on the way to this fix.
        val grew = widths.count { it in (small + Slack)..(large - Slack) }
        assertTrue(
            grew >= 4,
            "the title was caught between its two sizes on only $grew frames, so it " +
                "is teleporting rather than growing. Widths: " +
                widths.take(16).joinToString(),
        )
    }

    /** How wide the drawn string is, in pixels of ink. */
    private fun BufferedImage.inkWidth(): Int {
        var left = Int.MAX_VALUE
        var right = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) shr 16 and 0xFF) < 140) {
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        return if (right < 0) 0 else right - left + 1
    }

    private companion object {
        const val Title = "Perth Underground"
        const val TitleKey = "stop-title"
        const val Frames = 40

        /** Antialiasing, and a pixel of rounding at each end. */
        const val Slack = 8
    }
}
