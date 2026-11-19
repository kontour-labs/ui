package io.kontour.ui.interaction

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * How many fingers are holding something down to pick it up, on a platform
 * whose browser takes what the app does not: see [detectPickUp]'s
 * `claimsStrays`. Read on the UI thread, where pointer input runs, by
 * [keepsHeldTouches].
 */
internal object HeldTouches {
    var count: Int = 0
}

/**
 * While a finger holds something down to pick it up, a scroll's leftover at
 * the edge is the app's rather than the browser's.
 *
 * Compose's web canvas lets the browser pan, and on each touch move decides
 * whether to stop it from what it saw at the root of the nested scroll: a
 * scroll that consumed something keeps the browser out, a scroll that consumed
 * nothing — an edge, nowhere left to go — lets it in, so a page at its end can
 * be pulled to refresh. A card held at the top of a page met exactly that: the
 * fingertip rolled a few points down, the page's list began a scroll it had no
 * room for, and the browser took the touch with the hold still counting down.
 * Driven in Chrome on Android, ten pixels of downward drift lost every held
 * card; reported for 0.5.6 from the documentation on a phone, where cards
 * *"seem to just randomly drop themselves"*.
 *
 * So during a hold the leftover is taken here, at the root, after every
 * scroll inside has had what it wanted: a finger that means to scroll the
 * page still scrolls it, and only the part with nowhere to go is kept from the
 * browser. Nothing changes outside a hold. Installed by `KontourTheme` where
 * [io.kontour.ui.platform.browserTakesUnclaimedTouchMoves] says so.
 */
internal fun Modifier.keepsHeldTouches(): Modifier = nestedScroll(HeldLeftovers)

private object HeldLeftovers : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && HeldTouches.count > 0) available else Offset.Zero
}
