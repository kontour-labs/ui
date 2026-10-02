package io.kontour.ui.platform

import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView

/** Once per process: the setting is the activity's, not the host's. */
private var warned = false

/**
 * Read off the window's own attributes, through the root view, once the view is
 * attached; a window that has not said either way may pan, and is told so too.
 */
@Composable
internal actual fun WarnIfWindowPansForKeyboard() {
    val view = LocalView.current
    LaunchedEffect(view) {
        if (warned || view.isInEditMode) return@LaunchedEffect
        val debuggable = view.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return@LaunchedEffect
        val window = view.rootView.layoutParams as? WindowManager.LayoutParams ?: return@LaunchedEffect
        val adjust = window.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        val pans = adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        val mayPan = adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
        if (!pans && !mayPan) return@LaunchedEffect
        warned = true
        Log.w(
            "Kontour",
            "This window ${if (pans) "pans" else "may pan"} for the keyboard, and the keyboard's inset is " +
                "reported too, so a sheet over a text field can be lifted twice. Set " +
                "android:windowSoftInputMode=\"adjustResize\" on the activity.",
        )
    }
}
