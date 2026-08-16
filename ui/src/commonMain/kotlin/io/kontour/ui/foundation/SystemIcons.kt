package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.X
import com.composables.icons.tabler.filled.Star as FilledStar

/**
 * The few glyphs the design system draws on its own behalf.
 *
 * Everything a *caller* puts in a component — a button's leading icon, a menu
 * item's icon, an empty state's illustration — is still passed in as an
 * [ImageVector], so an app can use whatever set it likes. This object covers
 * only glyphs that are part of a component's *structure*: the tick that means
 * "this one is selected", the chevron that means "there is more inside", the
 * cross that means "close".
 *
 * Those are not decoration. A submenu with no chevron gives no sign it opens
 * anything, and a dismissible banner with no cross has no visible dismissal.
 * Making the caller supply them means every component ships looking broken until
 * someone remembers to pass one in.
 *
 * They are [Tabler] outline, matching the app. Each is a separate top-level
 * declaration, so the ones this module never touches are dropped by R8 and by
 * the JS/Wasm dead-code eliminator rather than travelling with the artifact.
 *
 * **Public**, because they are shipped rather than hidden. `SheetHeader.closeIcon`
 * defaults to [Close], and a default a caller can read but cannot write is worse
 * than no default — you can see what it is and have no way to say it. The type
 * is [ImageVector], from a dependency this module already exposes, so nothing
 * about the icon set leaks into the API with them.
 */
object SystemIcons {
    /** Marks the selected item in a list of choices. */
    val Check: ImageVector get() = Tabler.Outline.Check

    /** The indeterminate state of a tri-state control, and a stepper's "less". */
    val Dash: ImageVector get() = Tabler.Outline.Minus

    /** A stepper's "more". Structural: a stepper with no `+` is two blank buttons. */
    val Plus: ImageVector get() = Tabler.Outline.Plus

    /**
     * A `Rating`'s empty and filled marks.
     *
     * Structural in the same sense as the tick: a rating is *made of* these, and
     * one with no glyph is five blank squares. They are still parameters on the
     * component, because a rating of hearts is a reasonable thing to want — this
     * is only what it falls back to.
     */
    val Star: ImageVector get() = Tabler.Outline.Star
    val StarFilled: ImageVector get() = Tabler.Filled.FilledStar

    /** Dismiss. */
    val Close: ImageVector get() = Tabler.Outline.X

    /** Expands downward — accordions, selects, dropdown triggers. */
    val ChevronDown: ImageVector get() = Tabler.Outline.ChevronDown

    /** Collapses upward. */
    val ChevronUp: ImageVector get() = Tabler.Outline.ChevronUp

    /** Physically rightward, regardless of layout direction. */
    val ChevronRight: ImageVector get() = Tabler.Outline.ChevronRight

    /** Physically leftward, regardless of layout direction. */
    val ChevronLeft: ImageVector get() = Tabler.Outline.ChevronLeft

    /**
     * "There is more this way" — a submenu, a next page, a disclosure.
     *
     * Follows the reading direction rather than the compass, so it points left
     * in an RTL locale. Resolved here rather than with `autoMirror` on the
     * vector, because Tabler's chevrons are correctly *not* auto-mirroring: an
     * icon named "chevron right" should point right wherever it is drawn. It is
     * this *use* of it that is directional, not the glyph.
     */
    val ChevronForward: ImageVector
        @Composable @ReadOnlyComposable
        get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            ChevronLeft
        } else {
            ChevronRight
        }

    /** "Back the way you came." Follows the reading direction — see [ChevronForward]. */
    val ChevronBack: ImageVector
        @Composable @ReadOnlyComposable
        get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            ChevronRight
        } else {
            ChevronLeft
        }
}
