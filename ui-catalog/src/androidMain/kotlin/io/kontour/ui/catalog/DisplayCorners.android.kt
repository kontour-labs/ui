package io.kontour.ui.catalog

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import java.util.Locale

/**
 * The device, its density, and `WindowInsets.getRoundedCorner` at each corner in
 * pixels and in dp — re-read whenever the window lays out, as the library's own
 * reading now is, so the card follows a rotation and shows the first real answer
 * rather than the empty one a window has before its insets arrive.
 */
@Composable
internal actual fun platformCornerReadout(): List<String>? {
    val view = LocalView.current
    val lines = remember(view) { mutableStateOf(cornerLines(view)) }
    DisposableEffect(view) {
        fun reread() {
            val now = cornerLines(view)
            if (now != lines.value) lines.value = now
        }
        var observer = view.viewTreeObserver
        val onLayout = ViewTreeObserver.OnGlobalLayoutListener { reread() }
        observer.addOnGlobalLayoutListener(onLayout)
        reread()
        onDispose {
            if (!observer.isAlive) observer = view.viewTreeObserver
            observer.removeOnGlobalLayoutListener(onLayout)
        }
    }
    return lines.value
}

private fun cornerLines(view: View): List<String> {
    val metrics = view.resources.displayMetrics
    val density = metrics.density
    val header = listOf(
        "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.DEVICE}",
        "API ${Build.VERSION.SDK_INT} · density ${density.oneDecimal()} (${metrics.densityDpi} dpi)",
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return header + "No corners below API 31: the library uses its table"
    }
    val insets = view.rootWindowInsets ?: return header + "The window's insets have not arrived yet"
    return header + listOf(
        RoundedCorner.POSITION_TOP_LEFT to "Top left",
        RoundedCorner.POSITION_TOP_RIGHT to "Top right",
        RoundedCorner.POSITION_BOTTOM_RIGHT to "Bottom right",
        RoundedCorner.POSITION_BOTTOM_LEFT to "Bottom left",
    ).map { (position, name) ->
        val radius = insets.getRoundedCorner(position)?.radius
        if (radius == null) "$name  not reported" else "$name  ${radius}px · ${(radius / density).oneDecimal()}dp"
    }
}

private fun Float.oneDecimal(): String = String.format(Locale.ROOT, "%.1f", this)
