package io.kontour.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color

/*
 * A control's colour, faded when the control changes and not when the theme does.
 *
 * Raised 2026-10-29 for 0.5.1. A `Button`, a `Switch` and a dozen others each
 * ran `animateColorAsState` over `tweenFast` on a colour read from the theme. A
 * state change of their own — enabled to disabled, off to on — fades, as every
 * other state change in the library does. But a theme change moved the same
 * target, and each control then ran its own 150ms tween after it: behind the
 * theme's own fade when `animateThemeChanges` is on, so every control lagged
 * the surface it sat on, and after a cut when it is off, or under reduced
 * motion, so a switch meant to be one frame left every button and switch
 * recomposing for 150ms more. Asked for as: *"with a theme switch, there should
 * be no animation at all."* Measured as about 29% of a theme switch's frames'
 * allocation over a 40-row page.
 *
 * So the theme moving is told apart from the control moving. `Theme.colours`
 * is one object while the theme is still, a new one on each frame of a theme
 * fade, and a new one once on a cut; a colour whose scheme is not the one the
 * control last composed with came from the theme, and is shown as it is — on
 * the frame the theme shows it, with the fade's own frames or the cut — while
 * the control's own animation is snapped to it underneath. A change with the
 * scheme unchanged is the control's, and fades over [spec] exactly as before.
 */

/**
 * [target], faded over [spec] when the control's own state moves it, and shown
 * at once, with the theme, when the theme does. See the note above.
 */
@Composable
internal fun animateComponentColour(
    target: Color,
    spec: AnimationSpec<Color>,
    label: String,
): State<Color> {
    val scheme = Theme.colours
    val last = remember { SchemeSeen(scheme) }
    val byTheme = last.scheme !== scheme
    SideEffect { last.scheme = scheme }
    val animated = animateColorAsState(target, if (byTheme) snap() else spec, label = label)
    val now = rememberUpdatedState(target)
    return if (byTheme) now else animated
}

/** The scheme a control last composed with. A plain field: nothing redraws because it was written. */
private class SchemeSeen(var scheme: ColourScheme)
