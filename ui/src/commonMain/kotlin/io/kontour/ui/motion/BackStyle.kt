package io.kontour.ui.motion

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.kontour.ui.platform.platformBackStyle

/**
 * How going back looks.
 *
 * **One model, two feels.** On every platform back goes to the innermost thing
 * that can take it — the top overlay, then a stack inside it, then a pane, then
 * the page — and a gesture can be dragged, abandoned or let go. What differs is
 * what the hand sees while it drags, and each platform's users already know
 * theirs, the way they know how a list stretches or bounces at its end.
 *
 * Read from [LocalBackStyle], which follows the platform. Provide it to show one
 * platform's feel on another — the gallery does, so both can be judged on
 * whatever device is to hand.
 */
enum class BackStyle {
    /**
     * Android's predictive back. The page follows the finger from whichever
     * edge the gesture came from, over the page it will return to, before
     * anything is committed — it moves, whole, and does not shrink. An overlay
     * that may be dismissed runs its own exit backwards with the gesture.
     */
    Predictive,

    /**
     * iOS's swipe back. The page follows the finger one to one from the leading
     * edge, or from anywhere on the content as iOS 26 allows, casting a shadow
     * on the page beneath, which slides in from a third of the way across. Let
     * go, it carries on at the finger's speed. Of the overlays, back closes
     * only a side sheet, swiped towards its own edge; a bottom sheet is dragged
     * down and a dialog answered, as they always are there.
     */
    Swipe,
}

/**
 * The [BackStyle] everything under it draws with. Defaults to the platform's:
 * [BackStyle.Predictive] on Android, the desktop and the web, and
 * [BackStyle.Swipe] on iOS.
 *
 * The desktop and the web have no back gesture — Escape and the browser's button
 * complete at once — so there only a push or a pop is ever seen, and
 * `Predictive`'s is the shared axis every page in the library already moves on.
 */
val LocalBackStyle: ProvidableCompositionLocal<BackStyle> = compositionLocalOf { platformBackStyle }
