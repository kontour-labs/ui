package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.AvatarGroup
import io.kontour.ui.components.display.AvatarSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An avatar group is as wide as it is drawn.
 *
 * Reported from an iPhone: the group in the catalog showed a line through its last
 * avatar and no "+2". The avatars were overlapped with `Modifier.offset`, which moves
 * what is drawn and not what is laid out, so the group asked its row for five whole
 * avatars' width and drew them in three and a half. On a phone the demo's row could
 * not give it that, the overflow chip was measured last against what was left — a
 * sliver — and a pill-clipped sliver with a ring round it is a line. The "+2" wrapped
 * inside it and was clipped away.
 */
class AvatarGroupWidthTest {

    @Test
    fun theGroupReportsTheWidthItDraws() {
        var bounds = Rect.Zero
        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                AvatarGroup(
                    names = listOf("Aaron", "Sunny", "Jamie", "Kit", "Robin", "Sam"),
                    size = AvatarSize.Medium,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene -> scene.frames(4) }

        // Four avatars and the "+2" chip, each overlapping the last by a third.
        val diameter = AvatarSize.Medium.diameter.value * 2
        val drawn = diameter + 4 * (diameter - diameter / 3)
        assertTrue(
            abs(bounds.width - drawn) <= 3,
            "the group laid itself out ${bounds.width}px wide and draws ${drawn}px — it is " +
                "asking its row for room it does not use",
        )
    }
}
