package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * What a surface pinned to the window's edge has to keep its content clear of.
 *
 * The system bars and the display cutout — **not** the keyboard. `safeDrawing`
 * includes the IME, and a bottom navigation bar built on it rides up the screen
 * as the user types, which is not something a navigation bar does. A sheet or a
 * text field genuinely does want the keyboard inset and should ask for it by
 * name; edge furniture wants this.
 *
 * ```kotlin
 * TopBar() {
       +"Favourites"
   }                       // clears the status bar and cutout
 * TopBar(windowInsets = WindowInsets(0)) {
       +"Favourites"
   } // inside a Scaffold that already did
 * ```
 *
 * ### Why every bar takes this as a parameter
 *
 * A bar is not always at the edge. Put one inside a pane of a two-pane layout, a
 * sheet, or a dialog, and padding it for the status bar puts a gap in the middle
 * of the screen. Passing `WindowInsets(0)` is how a caller says "something above
 * me already handled this", and it is the reason this is a default rather than
 * something applied unconditionally.
 */
val WindowInsets.Companion.edges: WindowInsets
    @Composable get() = systemBars.union(displayCutout)

/** [edges], limited to the top and the sides. What a [io.kontour.ui.nav.TopBar] clears. */
val WindowInsets.Companion.topEdges: WindowInsets
    @Composable get() = edges.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** [edges], limited to the bottom and the sides. What a [io.kontour.ui.nav.NavBar] clears. */
val WindowInsets.Companion.bottomEdges: WindowInsets
    @Composable get() = edges.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

/**
 * [edges], limited to the leading side and the top and bottom.
 *
 * What a [io.kontour.ui.nav.NavRail] or [io.kontour.ui.nav.NavDrawer] clears.
 * `Start`, not `Left`: the rail moves to the right edge under RTL and has to
 * clear whatever is there instead.
 */
val WindowInsets.Companion.leadingEdges: WindowInsets
    @Composable get() = edges.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)

/**
 * [edges] **plus the keyboard**, limited to the bottom and the sides.
 *
 * What a bottom sheet or a toast clears. The opposite call to [bottomEdges], and
 * for the opposite reason: a sheet routinely holds a text field, and one sitting
 * behind the keyboard is unusable rather than merely untidy. A navigation bar
 * holds no text field and should stay where it is while the user types.
 */
val WindowInsets.Companion.sheetEdges: WindowInsets
    @Composable get() = edges.union(ime).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

/**
 * Every edge, keyboard included. What something centred in the window clears.
 *
 * A dialog is not pinned to an edge, so there is no side it can safely ignore —
 * and it is as likely to hold a text field as a sheet is.
 */
val WindowInsets.Companion.allEdges: WindowInsets
    @Composable get() = edges.union(ime)
