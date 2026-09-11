package io.kontour.ui.platform

import androidx.compose.runtime.Composable

/**
 * Nothing to tell: a desktop window has no chrome the application's own
 * appearance should drive.
 *
 * The title bar belongs to the window manager and follows the *operating
 * system's* appearance, not the application's — a macOS window does not get a
 * dark title bar because the app inside it drew dark, and should not. There is
 * no equivalent of a status bar to get wrong.
 */
@Composable
internal actual fun platformReportAppearance(dark: Boolean) = Unit
