package io.kontour.ui.platform

import androidx.compose.runtime.Composable

/**
 * Tells the host which appearance the application is drawing.
 *
 * **Every platform has chrome the application does not paint** — Android's
 * status and navigation bars, iOS's status bar, a mobile browser's address bar,
 * a form control's native rendering — and every one of them decides its own
 * colours from a flag the *host* owns rather than from anything on the canvas.
 * Nothing had ever set that flag.
 *
 * The symptom, reported from an Android phone: turn the app to dark and the
 * status bar's icons stay dark on dark. `enableEdgeToEdge()` picks its bar style
 * from the *system's* `uiMode`, which is the right guess for an app whose theme
 * follows the system and the wrong one for an app with a dark switch in it —
 * and this library's whole point is that the switch exists.
 *
 * So [KontourTheme][io.kontour.ui.theme.KontourTheme] says so out loud, once,
 * whenever the resolved scheme changes tier. The target scheme is what is
 * reported rather than the cross-fading one: the bars flip once at the start of
 * the fade instead of twelve times during it, and there is no interpolation to
 * be had anyway — a status bar is light or dark.
 *
 * Only the outermost theme reports. A nested `KontourTheme` — a screen forcing
 * dark over a light app — re-provides the tokens for its subtree and has no
 * business repainting the window, the same reason it does not install a second
 * input-modality tracker.
 */
@Composable
internal expect fun platformReportAppearance(dark: Boolean)
