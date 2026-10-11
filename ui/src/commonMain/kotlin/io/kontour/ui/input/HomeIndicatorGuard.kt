package io.kontour.ui.input

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity

/**
 * A finger that lands on the home indicator is the system's to swipe with, not
 * the app's to scroll with: past touch slop, its movement is taken before
 * anything in the app sees it.
 *
 * iOS hands the app every touch until it has recognised a swipe home or across
 * to another app, and only then cancels it, so the first part of that swipe
 * went to whatever was under it — a list scrolled as the app left the screen:
 * *"when i swipe out of the app using gesture navigation on ios, it sometimes
 * scrolls lists"*. UIKit's own scroll views do not take a drag that begins
 * there. The strip is the window's bottom inset, the home indicator's room, and
 * nothing on a phone with a home button.
 *
 * Only movement past touch slop, and only from a finger that went down in the
 * strip: a tap there — on the edge of a tab bar — still lands, jitter and all,
 * and a finger that strays into the strip from above is the app's as it was.
 */
@Composable
internal fun Modifier.guardHomeIndicator(): Modifier {
    val density = LocalDensity.current
    val strip by rememberUpdatedState(WindowInsets.navigationBars.getBottom(density).toFloat())
    return guardBottomStrip { strip }
}

/** [guardHomeIndicator] over a strip [strip] px tall at the foot of this node. */
internal fun Modifier.guardBottomStrip(strip: () -> Float): Modifier =
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            // Each finger that went down in the strip, and how far it has gone.
            val fromStrip = HashMap<PointerId, Offset>()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                for (change in event.changes) {
                    if (change.changedToDownIgnoreConsumed()) {
                        val tall = strip()
                        if (tall > 0f && change.position.y >= size.height - tall) fromStrip[change.id] = Offset.Zero
                        continue
                    }
                    val gone = fromStrip[change.id] ?: continue
                    if (!change.pressed) {
                        fromStrip.remove(change.id)
                        continue
                    }
                    val travelled = gone + change.positionChange()
                    fromStrip[change.id] = travelled
                    if (travelled.getDistance() > slop) change.consume()
                }
            }
        }
    }
