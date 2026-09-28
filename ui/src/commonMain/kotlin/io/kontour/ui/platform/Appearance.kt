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

/**
 * What the **device** is set to, which is not always what the window says.
 *
 * `isSystemInDarkTheme()` is Compose's own and is the obvious thing to read. On
 * iOS it is also the thing this library has already written to, and that is a
 * closed loop rather than a subtlety:
 *
 * - [platformReportAppearance]'s iOS actual sets `overrideUserInterfaceStyle` on
 *   the application's windows, which is what puts UIKit chrome and any system
 *   sheet on the same side as the canvas. It overrides the trait environment for
 *   the whole view hierarchy beneath it.
 * - Compose Multiplatform's iOS scene reads its system theme out of that same
 *   trait environment — the hosting view controller's
 *   `traitCollection.userInterfaceStyle`, pushed into `LocalSystemTheme` from
 *   `traitCollectionDidChange`. Confirmed in the shipped `ui` klib for
 *   1.12.0-rc01 rather than assumed.
 *
 * So an application that reports its appearance and then asks what the device is
 * set to gets its own answer back. Reported from an iPhone in light mode: turn
 * the app's dark switch on, then turn "follow device" on, and dark does not go
 * away — because by then the device *is* dark, as far as anything can tell.
 *
 * Android and web have the same reporter and no loop: one sets system-bar icon
 * tint flags, which nothing reads back, and the other sets `color-scheme`, which
 * does not feed `prefers-color-scheme`.
 *
 * ### Two halves, and the second is what actually closes it
 *
 * This reads a source the override cannot reach — on iOS, the **screen's** trait
 * collection rather than a window's. That is necessary and not sufficient,
 * because a window pinned by an override stops reporting device changes at all,
 * so nothing recomposes when the device changes.
 *
 * The other half is in the reporter: it installs an override only when the
 * application's appearance **differs** from the device's, and clears it back to
 * unspecified when they agree. An app that is following the device therefore has
 * no override installed, its window follows the device live, and this function
 * and `isSystemInDarkTheme()` agree. An app that is pinned has an override and a
 * poisoned window — and the only moment its device value matters again is when
 * the reader asks to follow the device, which is a tap, which recomposes.
 */
@Composable
internal expect fun platformSystemDark(): Boolean
