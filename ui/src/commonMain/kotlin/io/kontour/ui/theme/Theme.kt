package io.kontour.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.rememberInputModalityState
import io.kontour.ui.input.trackInputModality
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.rememberDefaultFeedbackDispatcher
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion

/**
 * The design system's tokens, for reading inside a composable.
 *
 * ```
 * Box(
 *     Modifier
 *         .background(Theme.colors.surface, Theme.shapes.medium)
 *         .padding(Theme.spacing.md)
 * ) {
 *     Text("Departures", style = Theme.typography.titleMedium)
 * }
 * ```
 *
 * Every component reads through here. A component that hardcodes a colour, a
 * radius or a duration cannot be re-themed, cannot respond to the contrast
 * setting, and will not honour reduced motion — so it fails review, and the
 * component contract test that ships alongside it.
 */
object Theme {
    val colors: ColorScheme
        @Composable @ReadOnlyComposable get() = LocalColorScheme.current

    val typography: Typography
        @Composable @ReadOnlyComposable get() = LocalTypography.current

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val elevation: Elevation
        @Composable @ReadOnlyComposable get() = LocalElevation.current

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val sizing: Sizing
        @Composable @ReadOnlyComposable get() = LocalSizing.current
}

/**
 * Installs the design system.
 *
 * Wrap the whole app in this once, above everything else. Nothing in
 * `io.kontour.ui` works outside it — the composition locals below fail loudly
 * rather than falling back to a default theme, because a component silently
 * rendering in the wrong palette is a worse bug than one that refuses to render.
 *
 * ```
 * KontourTheme {
 *     AppRoot()
 * }
 * ```
 *
 * ### Overriding
 *
 * Every token group is a parameter, so a product or a screen can override one
 * without forking the rest:
 *
 * ```
 * // A screen that is always dark, whatever the system says
 * KontourTheme(darkTheme = true) { MapScreen() }
 *
 * // A different brand on the same components
 * KontourTheme(colors = lightColorScheme(accent = Ocean, focusRing = Ocean)) { … }
 * ```
 *
 * ### Accessibility defaults
 *
 * [contrast] and [reduceMotion] default to what the operating system reports,
 * and follow it live — a user who turns on "Reduce Motion" mid-session sees the
 * change immediately. Pass an explicit value to let an in-app setting win.
 *
 * @param darkTheme Whether to use the dark scheme. Follows the system by default.
 * @param contrast Which contrast tier to render at. Follows the system by default.
 * @param reduceMotion Whether to damp animation. Follows the system by default.
 */
@Composable
fun KontourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    contrast: ContrastLevel = if (platformPrefersHighContrast()) ContrastLevel.High else ContrastLevel.Standard,
    reduceMotion: Boolean = platformPrefersReducedMotion(),
    colors: ColorScheme = remember(darkTheme, contrast) { kontourColorScheme(darkTheme, contrast) },
    typography: Typography = kontourTypography(outfitFontFamily()),
    shapes: Shapes = remember { Shapes() },
    spacing: Spacing = remember { Spacing() },
    elevation: Elevation = remember(darkTheme) { kontourElevation(darkTheme) },
    motion: Motion = remember(reduceMotion) { kontourMotion(reduceMotion) },
    sizing: Sizing = remember { Sizing() },
    feedback: FeedbackDispatcher = rememberDefaultFeedbackDispatcher(),
    content: @Composable () -> Unit,
) {
    // A nested KontourTheme — a screen forcing dark mode, say — re-provides the
    // token locals but must not install a second modality tracker: two Boxes
    // observing the same pointer stream is wasted work, and the inner one would
    // shadow the outer's state for part of the tree.
    val alreadyTracking = LocalInputModalityInstalled.current

    CompositionLocalProvider(
        LocalColorScheme provides colors,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalSpacing provides spacing,
        LocalElevation provides elevation,
        LocalMotion provides motion,
        LocalSizing provides sizing,
        LocalContrastLevel provides contrast,
        LocalContentColor provides colors.content,
        LocalTextStyle provides typography.bodyMedium,
        LocalFeedback provides feedback,
    ) {
        if (alreadyTracking) {
            content()
        } else {
            val modality = rememberInputModalityState()
            CompositionLocalProvider(
                LocalInputModality provides modality.current,
                LocalInputModalityInstalled provides true,
            ) {
                Box(Modifier.trackInputModality(modality)) {
                    content()
                }
            }
        }
    }
}

/**
 * Whether an ancestor [KontourTheme] has already installed the input-modality
 * tracker. Lets nested themes re-provide tokens without duplicating the tracker.
 */
internal val LocalInputModalityInstalled = staticCompositionLocalOf { false }

private const val NOT_IN_THEME =
    "No KontourTheme found. Wrap your app in KontourTheme { … } — components " +
        "read their tokens from it and have no sensible default without one."

internal val LocalColorScheme = staticCompositionLocalOf<ColorScheme> { error(NOT_IN_THEME) }
internal val LocalTypography = staticCompositionLocalOf<Typography> { error(NOT_IN_THEME) }
internal val LocalShapes = staticCompositionLocalOf<Shapes> { error(NOT_IN_THEME) }
internal val LocalSpacing = staticCompositionLocalOf<Spacing> { error(NOT_IN_THEME) }
internal val LocalElevation = staticCompositionLocalOf<Elevation> { error(NOT_IN_THEME) }
internal val LocalMotion = staticCompositionLocalOf<Motion> { error(NOT_IN_THEME) }
internal val LocalSizing = staticCompositionLocalOf<Sizing> { error(NOT_IN_THEME) }

/**
 * The tier the current theme is rendering at. Components rarely need this —
 * the tokens have already been resolved — but a few (charts, custom drawing)
 * legitimately want to know.
 */
val LocalContrastLevel = staticCompositionLocalOf { ContrastLevel.Standard }
