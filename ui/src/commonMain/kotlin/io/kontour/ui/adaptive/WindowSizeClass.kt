package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** The window's size, in classes and in the measurements they came from. */
@Immutable
data class WindowSizeClass(
    val width: WindowWidthClass,
    val height: WindowHeightClass,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    /** True for a window that is much wider than it is tall. */
    val isLandscape: Boolean get() = widthDp > heightDp

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
 * Defaults to a compact phone rather than throwing, unlike `LocalOverlayHost`.
 * A component that guesses compact on a desktop looks cramped; one that throws
 * takes the app down. This is a layout hint, not a contract.
 */
val LocalWindowSizeClass = staticCompositionLocalOf {
    WindowSizeClass.of(400.dp, 800.dp)
}

/** The current window size class. Shorthand for [LocalWindowSizeClass]. */
val windowSizeClass: WindowSizeClass
    @Composable get() = LocalWindowSizeClass.current

/**
 * Measures its own bounds and provides them as [LocalWindowSizeClass].
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
