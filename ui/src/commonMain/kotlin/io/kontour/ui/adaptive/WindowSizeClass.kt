package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * How much horizontal room there is, in buckets.
 *
 * Our own rather than Material's `WindowSizeClass`, which would drag
 * `material3-window-size-class` — and with it Material — back into a design
 * system built specifically not to have it. The breakpoints are the same ones
 * every platform has converged on, so nothing is lost by owning them.
 *
 * | | Width | Typically |
 * |---|---|---|
 * | [Compact] | < 600dp | A phone in portrait |
 * | [Medium] | < 840dp | A phone in landscape, a small tablet, a split-screen window |
 * | [Expanded] | < 1200dp | A tablet, a modest desktop window |
 * | [Large] | ≥ 1200dp | A desktop window worth using two panes in |
 *
 * **Measure the window, not the device.** A phone in split-screen is compact; a
 * tablet with a side sheet open may be medium in the region that is left. That
 * is why [WindowSizeClassProvider] takes its measurement from a `BoxWithConstraints`
 * rather than from a platform API.
 */
@Immutable
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
    Large,
    ;

    /** True when there is room for a rail or a permanent drawer beside the content. */
    val hasRoomBeside: Boolean get() = this >= Medium

    /** True when there is room for two panes of content at once. */
    val hasRoomForTwoPanes: Boolean get() = this >= Expanded

    companion object {
        fun of(width: Dp): WindowWidthClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            width < 1200.dp -> Expanded
            else -> Large
        }
    }
}

/**
 * How much vertical room there is.
 *
 * Separate from the width class because the constraint is different: a phone in
 * landscape has plenty of width and almost none of height, and a component that
 * only consults the width will happily put a large top bar and a bottom bar on a
 * 360dp-tall window and leave nothing between them.
 */
@Immutable
enum class WindowHeightClass {
    /** < 480dp. A phone in landscape. Collapse anything that can be collapsed. */
    Compact,

    /** < 900dp. Most phones in portrait. */
    Medium,

    /** ≥ 900dp. A tablet or a desktop window. */
    Expanded,
    ;

    companion object {
        fun of(height: Dp): WindowHeightClass = when {
            height < 480.dp -> Compact
            height < 900.dp -> Medium
            else -> Expanded
        }
    }
}

/**
 * The window's size, in classes and in the measurements they came from.
 *
 * ### Two of these four are deliberately outside `equals`
 *
 * [widthDp] and [heightDp] are the raw measurement. They are here because a
 * component occasionally wants the real number, and they are **not** part of
 * equality because this value is provided through a
 * [staticCompositionLocalOf][LocalWindowSizeClass], and a static local does not
 * track which composable read what: when its value changes it invalidates the
 * entire content beneath it. With the raw pixels in `equals`, that is the whole
 * application.
 *
 * On a desktop, where a window sits still, nothing notices. On a phone browser
 * it is a catastrophe hiding in plain sight: the URL bar collapses and expands
 * *while the reader scrolls*, resizing the viewport by tens of pixels, so every
 * scroll recomposed every screen in the app. It was the largest single cost in
 * the documentation site and it was costing every consuming app too.
 *
 * What is left in `equals` is what a layout can actually act on — the two
 * buckets and [isLandscape]. Those change when the window genuinely becomes a
 * different shape, which is exactly when a layout should be reconsidered.
 */
@Immutable
class WindowSizeClass(
    val width: WindowWidthClass,
    val height: WindowHeightClass,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    /** True for a window that is much wider than it is tall. */
    val isLandscape: Boolean = widthDp > heightDp

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowSizeClass) return false
        return width == other.width &&
            height == other.height &&
            isLandscape == other.isLandscape
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + isLandscape.hashCode()
        return result
    }

    override fun toString(): String =
        "WindowSizeClass(width=$width, height=$height, ${widthDp.value}x${heightDp.value}dp)"

    companion object {
        fun of(width: Dp, height: Dp): WindowSizeClass = WindowSizeClass(
            width = WindowWidthClass.of(width),
            height = WindowHeightClass.of(height),
            widthDp = width,
            heightDp = height,
        )
    }
}

/**
 * The current window size class.
 *
 * **Under `KontourTheme` it is the window's** unless something said otherwise:
 * the theme reads the size of the window the app is in, so a 1440dp desktop
 * window is [WindowWidthClass.Large] with no [WindowSizeClassProvider] at all.
 * It used to default to a phone, and the root setup has no provider in it —
 * *"Leave it out and you get the bottom bar on a 1440px window."*
 *
 * A [WindowSizeClassProvider] measures a region instead, and wins inside it: a
 * pane, a frame, the room a permanent drawer leaves. So does a class provided
 * outright.
 *
 * Outside any theme, or where the platform has not said how big the window is,
 * it is a compact phone rather than an error, unlike `LocalOverlayHost`. A
 * component that guesses compact on a desktop looks cramped; one that throws
 * takes the app down. This is a layout hint, not a contract.
 */
val LocalWindowSizeClass = staticCompositionLocalOf { PhoneDefault }

/**
 * The class nothing has said: a 400×800 phone. Told apart by identity, so the
 * theme knows to put the window's own in its place and a phone provided
 * outright — the same value, another instance — is left alone.
 */
internal val PhoneDefault = WindowSizeClass.of(400.dp, 800.dp)

/**
 * The window's class, read from the platform: what [LocalWindowSizeClass] is
 * under a theme where nothing measured a region. Null where the platform has not
 * said how big the window is.
 *
 * Through a derived state, so a window that changes size without changing
 * class — a phone browser's URL bar sliding away — recomposes nothing; see
 * [WindowSizeClass] on `equals`.
 */
@Composable
internal fun rememberWindowSizeClassFromWindow(): WindowSizeClass? {
    val window = LocalWindowInfo.current
    val measured by remember(window) {
        derivedStateOf(structuralEqualityPolicy()) {
            val size = window.containerDpSize
            if (size.isSpecified && size.width > 0.dp && size.height > 0.dp) {
                WindowSizeClass.of(size.width, size.height)
            } else {
                null
            }
        }
    }
    return measured
}

/** The current window size class. Shorthand for [LocalWindowSizeClass]. */
val windowSizeClass: WindowSizeClass
    @Composable get() = LocalWindowSizeClass.current

/**
 * Measures its own bounds and provides them as [LocalWindowSizeClass].
 *
 * Not needed for the window: under `KontourTheme` the class is already the
 * window's. This is for a **region** — the content beside a permanent drawer, a
 * pane, a frame in a gallery — that should lay out against the room it has
 * rather than the room the window has.
 *
 * ```kotlin
 * KontourTheme {
 *     WindowSizeClassProvider {
 *         OverlayHost { AppRoot() }
 *     }
 * }
 * ```
 *
 * Install once at the root. Nesting is legal and occasionally right — a wide
 * window with a permanent drawer can re-provide the class for the *content*
 * region, so a two-pane layout inside it measures against the room it actually
 * has rather than the room the window has.
 */
@Composable
fun WindowSizeClassProvider(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(
            LocalWindowSizeClass provides WindowSizeClass.of(maxWidth, maxHeight),
            content = content,
        )
    }
}
