package io.kontour.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Sets the system bars' *appearance* — which is Android's word for the colour of
 * the icons in them, not the colour behind them.
 *
 * The bars themselves stay transparent: the app draws under them, which is what
 * `enableEdgeToEdge()` in the host is for and what the library's window-inset
 * padding assumes. What was missing is the other half. Light bars means dark
 * icons, for a light app; clearing it means light icons, for a dark one. Get it
 * wrong and the clock and the battery are invisible, which is exactly what a
 * dark app under a system set to light looked like.
 *
 * **Written against the platform rather than `WindowInsetsControllerCompat`**,
 * which would mean a new `androidx.core` dependency on a module that currently
 * has one Android dependency. The compat class is two branches wide and both of
 * them are below.
 */
@Composable
internal actual fun platformReportAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // An `Activity` reached through however many `ContextWrapper`s Compose and
    // AppCompat have put in the way. Null in a `ComposeView` hosted somewhere
    // that has no window of its own, which is a case to leave alone rather than
    // to guess at.
    val window = remember(view) { view.context.enclosingWindow() } ?: return
    // Applied after every successful composition rather than from a
    // `LaunchedEffect`: it is two flag writes, it must not be re-ordered after a
    // frame has drawn, and the window can be repainted by anything else in the
    // process between our composition and the next.
    SideEffect { window.setLightSystemBars(light = !dark) }
}

private tailrec fun Context.enclosingWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.enclosingWindow()
    else -> null
}

/**
 * API 30 has `WindowInsetsController`; 29 has the `systemUiVisibility` flags it
 * replaced, and `minSdk` is 29 — so both branches are here and the whole
 * function carries the suppression, because the flags are deprecated at their
 * *declaration* and reading one into a local is enough to trip `-Werror`.
 */
@Suppress("DEPRECATION")
private fun Window.setLightSystemBars(light: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        insetsController?.setSystemBarsAppearance(if (light) bars else 0, bars)
    } else {
        val bars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        decorView.systemUiVisibility = if (light) {
            decorView.systemUiVisibility or bars
        } else {
            decorView.systemUiVisibility and bars.inv()
        }
    }
}
