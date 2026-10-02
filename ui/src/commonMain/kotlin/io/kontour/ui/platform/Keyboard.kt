package io.kontour.ui.platform

import androidx.compose.runtime.Composable

/**
 * Says once, in a debuggable Android app, that its window pans for the keyboard.
 *
 * A window that pans moves the whole of itself up to keep the focused field in
 * sight, and tells the app about the keyboard as well. A sheet that lifts itself
 * above the keyboard is then lifted twice — once by the pan and once by itself —
 * which is the other way to get *"it sometimes pushes the sheet to way higher
 * than it needs to be"*, and one the library cannot see from inside the window.
 * The fix is the app's: `android:windowSoftInputMode="adjustResize"` on the
 * activity, which with edge-to-edge drawing leaves the keyboard to the insets.
 *
 * Nothing on any other platform, and nothing in a release build.
 */
@Composable
internal expect fun WarnIfWindowPansForKeyboard()
