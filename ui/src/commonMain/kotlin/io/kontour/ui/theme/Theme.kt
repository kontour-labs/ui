package io.kontour.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.rememberInputModalityState
import io.kontour.ui.input.trackInputModality
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.HapticsLevel
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
 *         .background(Theme.colours.surface, Theme.shapes.medium)
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
    val colours: ColourScheme
        @Composable @ReadOnlyComposable get() = LocalColourScheme.current

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

    /**
     * Every word the library puts on screen that the caller did not supply.
     *
     * Read by parameter defaults rather than at the point of use, so a call site
     * can still override one without going through the theme.
     */
    val strings: Strings
        @Composable @ReadOnlyComposable get() = LocalStrings.current
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
 * Every token group is a parameter, so a screen or a product can override one
 * without forking the rest — `ui-docs/content/theming.md` has the
 * recipes.
 *
 * [darkTheme], [contrast] and [reduceMotion] default to what the operating
 * system reports and follow it live, so a user who turns on "Reduce Motion"
 * mid-session sees the change immediately. Pass an explicit value to let an
 * in-app setting win.
 *
 * @param darkTheme Whether to use the dark scheme. Follows the system by default.
 * @param contrast Which contrast tier to render at. Follows the system by default.
 * @param reduceMotion Whether to damp animation. Follows the system by default.
 * @param backdropBlur Whether a modal blurs the content behind it as well as
 *   dimming it. On by default. Turning it off costs the app a texture and
 *   nothing else — the shapes, the scrim, the motion and the layout are the same
 *   either way — so it is a performance dial rather than a design choice. Worth
 *   reaching for over a live map, where the whole screen is redrawing anyway,
 *   and worth measuring before you do.
 */
@Composable
fun KontourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    contrast: ContrastLevel = if (platformPrefersHighContrast()) ContrastLevel.High else ContrastLevel.Standard,
    reduceMotion: Boolean = platformPrefersReducedMotion(),
    backdropBlur: Boolean = true,
    /**
     * Whether a change of scheme cross-fades rather than cutting.
     *
     * On, because every other state change in the library animates and switching
     * to dark mode is the largest one there is. See `animatedColorScheme` for
     * what it costs: the scheme feeds a static composition local, so the fade
     * recomposes the whole application for its duration. That is the right trade
     * for a rare, deliberate change and the wrong one for anything frequent, so
     * an app driving [colours] from something that moves should turn it off.
     */
    animateThemeChanges: Boolean = true,
    colours: ColourScheme = remember(darkTheme, contrast) { kontourColourScheme(darkTheme, contrast) },
    typography: Typography = rememberDefaultTypography(),
    shapes: Shapes = remember { Shapes() },
    spacing: Spacing = remember { Spacing() },
    elevation: Elevation = remember(darkTheme) { kontourElevation(darkTheme) },
    motion: Motion = remember(reduceMotion) { kontourMotion(reduceMotion) },
    sizing: Sizing = remember(contrast) { kontourSizing(contrast) },
    strings: Strings = remember { Strings() },
    /**
     * How much physical feedback the app gives. See [HapticsLevel].
     *
     * Separate from [feedback], which decides what each intent *feels like*.
     * This decides how many of them fire at all, and is the one an app is likely
     * to want to put behind a user-facing setting.
     */
    haptics: HapticsLevel = HapticsLevel.Full,
    feedback: FeedbackDispatcher = rememberDefaultFeedbackDispatcher(haptics),
    content: @Composable () -> Unit,
) {
    // A nested KontourTheme — a screen forcing dark mode, say — re-provides the
    // token locals but must not install a second modality tracker: two Boxes
    // observing the same pointer stream is wasted work, and the inner one would
    // shadow the outer's state for part of the tree.
    val alreadyTracking = LocalInputModalityInstalled.current

    // Resolved here rather than in the parameters' defaults so it animates
    // whatever the caller passed — an app with its own scheme gets the
    // cross-fade too, not just one using the built-in light/dark pair.
    //
    // Colours and shadows go through together. They are separate tokens and it
    // would have been less code to animate the scheme alone, which is what this
    // did: the shadows then cut to their dark-mode strength on the fade's first
    // frame and waited there for the surfaces to catch up.
    val faded = if (animateThemeChanges) {
        animatedTheme(colours, elevation, motion)
    } else {
        ThemeFade(colours, elevation)
    }
    val resolvedColours = faded.colours

    CompositionLocalProvider(
        LocalColourScheme provides resolvedColours,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalSpacing provides spacing,
        LocalElevation provides faded.elevation,
        LocalMotion provides motion,
        LocalSizing provides sizing,
        LocalStrings provides strings,
        LocalContrastLevel provides contrast,
        LocalBackdropBlur provides backdropBlur,
        LocalContentColour provides resolvedColours.content,
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

internal val LocalColourScheme = staticCompositionLocalOf<ColourScheme> { error(NOT_IN_THEME) }
internal val LocalTypography = staticCompositionLocalOf<Typography> { error(NOT_IN_THEME) }
internal val LocalShapes = staticCompositionLocalOf<Shapes> { error(NOT_IN_THEME) }
internal val LocalSpacing = staticCompositionLocalOf<Spacing> { error(NOT_IN_THEME) }
internal val LocalElevation = staticCompositionLocalOf<Elevation> { error(NOT_IN_THEME) }
internal val LocalMotion = staticCompositionLocalOf<Motion> { error(NOT_IN_THEME) }
internal val LocalSizing = staticCompositionLocalOf<Sizing> { error(NOT_IN_THEME) }
internal val LocalStrings = staticCompositionLocalOf<Strings> { error(NOT_IN_THEME) }

/**
 * The tier the current theme is rendering at. Components rarely need this —
 * the tokens have already been resolved — but a few (charts, custom drawing)
 * legitimately want to know.
 */
val LocalContrastLevel = staticCompositionLocalOf { ContrastLevel.Standard }

/**
 * Whether a modal blurs the content behind it.
 *
 * Defaults to true outside a theme so an overlay rendered on its own — as the
 * contract suite does — behaves the way it does in an app.
 */
internal val LocalBackdropBlur = staticCompositionLocalOf { true }
