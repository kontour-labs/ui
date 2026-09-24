package io.kontour.ui.platform

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `WindowInsets.getRoundedCorner`, all four of them, and a table under it.
 *
 * ### Three tiers, and the top one is not the interesting one
 *
 * **API 31 and up** the platform reports a radius per corner and that is exact.
 * **Below it** — `minSdk` here is 29 — there is nothing at all, and that is the
 * gap `DeviceRadiusTable` fills: the devices that declare a corner radius in a
 * LineageOS overlay are overwhelmingly 2017-2019 hardware, so the table and the
 * platform cover disjoint sets rather than disagreeing about one.
 *
 * **The curve is a table at every tier**, because no tier reports it. See
 * [deviceSmoothingFor], which is honest about being a judgement.
 *
 * ### What is deliberately not read
 *
 * `Resources.getIdentifier("rounded_corner_radius", "dimen", "android")` would
 * reach the same overlay at runtime on any API level. It is not read here and
 * that is a decision rather than an oversight: it is exactly the class of
 * private-name access that `Accessibility.android.kt` records killing every
 * launch on every device above API 31, and the payoff is two API levels of a
 * shrinking population that the table covers provably instead.
 *
 * `DisplayShape` (API 34) is not read either. Where an OEM leaves
 * `config_mainDisplayShape` empty the framework synthesises the path from the
 * radii — a circle — so fitting a smoothing to it returns zero for almost every
 * device, which is a wrong answer that looks like a measured one.
 *
 * No `runCatching` anywhere: `RoundedCorner` and a string lookup cannot throw. A
 * read that cannot throw is better than one that is caught.
 *
 * ### Read again whenever the window lays out
 *
 * The corners are state, re-read on every global layout and on attach, and not a
 * value read once in composition. Reported from a Pixel 11 Pro XL as the sheet and
 * the receding page having corners *too tight* — the same curve as a Pixel 9, a
 * smaller radius. The one read happened wherever the composition first ran, which
 * for the backdrop is the app's first frame, and until the platform has delivered
 * the window's insets `rootWindowInsets` has no corners in it. That read answered
 * "nothing", nothing asked again, and the shapes fell back to the scale's own
 * 34dp. Whether the insets are there on the first frame is a race a device can win
 * or lose, which is how two phones with the same corner could disagree.
 *
 * A global layout follows every insets dispatch, and a rotation, so the first real
 * answer and every later change recompose whoever asked. It is written only when
 * it changes, so an ordinary layout costs four reads and a comparison.
 */
@Composable
internal actual fun platformDeviceCorners(): DeviceCorners? {
    val view = LocalView.current
    if (view.isInEditMode) return null
    val density = LocalDensity.current

    val smoothing = remember { deviceSmoothingFor(Build.MANUFACTURER, Build.DEVICE) }
    // Parsed at most once per process, and on API 31 with a cooperative device
    // never at all — the lazy is the reason the table is a packed string rather
    // than a map built in a static initialiser.
    val listed = remember { deviceRadiiFrom(DeviceRadiusTable, Build.DEVICE) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return listed?.copy(smoothing = smoothing)
    }

    val corners = remember(view, density) {
        mutableStateOf(readCorners(view, density, listed, smoothing))
    }
    DisposableEffect(view, density) {
        fun reread() {
            val now = readCorners(view, density, listed, smoothing)
            if (now != corners.value) corners.value = now
        }
        // Kept, not looked up again on dispose: a view's observer before it is
        // attached is a stand-in that is merged into the window's on attach, and
        // removing from one that is no longer alive throws.
        var observer = view.viewTreeObserver
        val onLayout = ViewTreeObserver.OnGlobalLayoutListener { reread() }
        val onAttach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = reread()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        observer.addOnGlobalLayoutListener(onLayout)
        view.addOnAttachStateChangeListener(onAttach)
        // The effect runs after the composition that read the first value, and the
        // insets may have arrived in between.
        reread()
        onDispose {
            if (!observer.isAlive) observer = view.viewTreeObserver
            observer.removeOnGlobalLayoutListener(onLayout)
            view.removeOnAttachStateChangeListener(onAttach)
        }
    }
    return corners.value
}

/**
 * The four corners as the window reports them now, the table filling what it does
 * not. API 31 and up only; the caller has checked.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun readCorners(
    view: View,
    density: Density,
    listed: DeviceCorners?,
    smoothing: Float?,
): DeviceCorners? {
    // Null until the view is attached, which is an ordinary state on the first
    // composition rather than an error — and the table is a better answer than
    // nothing for that frame too.
    val insets = view.rootWindowInsets ?: return listed?.copy(smoothing = smoothing)

    fun radius(position: Int): Dp? =
        insets.getRoundedCorner(position)?.radius?.takeIf { it > 0 }?.let {
            with(density) { it.toDp() }
        }

    val topLeft = radius(RoundedCorner.POSITION_TOP_LEFT)
    val topRight = radius(RoundedCorner.POSITION_TOP_RIGHT)
    val bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT)
    val bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT)

    // A corner the platform declined falls back to the table's own pair, and
    // then to the other corners: a device that reports three of four is not
    // saying the fourth is square, it is saying it has not been asked.
    val fallbackTop = listed?.topLeft ?: topLeft ?: topRight
    val fallbackBottom = listed?.bottomLeft ?: bottomLeft ?: bottomRight
    val corners = DeviceCorners(
        topLeft = topLeft ?: fallbackTop ?: 0.dp,
        topRight = topRight ?: fallbackTop ?: 0.dp,
        bottomRight = bottomRight ?: fallbackBottom ?: 0.dp,
        bottomLeft = bottomLeft ?: fallbackBottom ?: 0.dp,
        smoothing = smoothing,
    )
    return if (corners.isSquare) null else corners
}
