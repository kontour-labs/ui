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
 *   either way — so it is a performance dial rather than a design choice.
 *
 *   What it is worth, measured. A side sheet opening in Chromium on a software
 *   rasteriser, frame times over the animation:
 *
 *   ```
 *   blur on    p95 250-267ms, 35 frames delivered in 1.5s
 *   blur off   p95 100-117ms, 46-50 frames delivered
 *   ```
 *
 *   So on a machine with no GPU it is roughly a third of the frames and double
 *   the worst ones. On a machine with one it is a shader pass and will cost far
 *   less — which is exactly why this stays on by default and why the honest
 *   advice is to measure your own target rather than take either number.
 *   `docs/measure-web.mjs` is the instrument, and it takes a `--click`.
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
    /**
     * Keyed off the *scheme*, not off [darkTheme].
     *
     * The two agree for every caller who lets [colours] default, which is why
     * this went unnoticed. They come apart the moment an app supplies a palette
     * of its own: [darkTheme] picks between the built-in schemes, so an app that
     * has declined them has no reason to set it, and a dark scheme was getting
     * light-mode alphas — every card, menu and dialog flat, for a reason nothing
     * on screen explains. [ColourScheme.isDark] is already the thing `Surface`,
     * `Tag` and `Skeleton` ask when they need to know which ground they are on.
     */
    elevation: Elevation = remember(colours.isDark) { kontourElevation(colours.isDark) },
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

/**
 * The eight token families, as composition locals.
 *
 * Read them through [Theme] — `Theme.colours` is `LocalColourScheme.current` and
 * is what every component in the library uses. These are public for the other
 * direction: *providing* one for part of a tree, which [ProvideTokens] does and
 * which nothing could do from outside this module while they were `internal`.
 *
 * ### Still `staticCompositionLocalOf`, deliberately
 *
 * A static local does not track reads: changing one recomposes everything below
 * the provider rather than only the composables that read it. That sounds like
 * the wrong trade and is the right one here — a token change *is* a change to
 * everything below it, and a tracking local would pay for read bookkeeping on
 * every `Theme.spacing` in the library to avoid a recomposition that has to
 * happen anyway. `animateThemeChanges` is where the cost shows up, and
 * `KontourTheme`'s KDoc says so at the parameter.
 *
 * ### They throw rather than defaulting
 *
 * Outside a [KontourTheme] there is no sensible palette, and a component drawing
 * in some fallback grey is a bug that looks like a design. The message names the
 * fix.
 */
val LocalColourScheme = staticCompositionLocalOf<ColourScheme> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalTypography = staticCompositionLocalOf<Typography> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalShapes = staticCompositionLocalOf<Shapes> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalSpacing = staticCompositionLocalOf<Spacing> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalElevation = staticCompositionLocalOf<Elevation> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalMotion = staticCompositionLocalOf<Motion> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalSizing = staticCompositionLocalOf<Sizing> { error(NOT_IN_THEME) }

/** @see LocalColourScheme */
val LocalStrings = staticCompositionLocalOf<Strings> { error(NOT_IN_THEME) }

/**
 * Change some tokens for a subtree, and **inherit the rest**.
 *
 * ```
 * ProvideTokens(colours = brandScheme) {
 *     Header()
 * }
 * ```
 *
 * ### Why this is not `KontourTheme` nested inside itself
 *
 * A nested [KontourTheme] does not inherit. Every parameter it is not given
 * re-runs its *default*, and those defaults read the platform rather than the
 * enclosing theme — so
 *
 * ```
 * KontourTheme(strings = german) {
 *     KontourTheme(darkTheme = true) { … }   // ← all 47 strings are English again
 * }
 * ```
 *
 * silently reverts the strings, resets `HapticsLevel.Off` to `Full`, and throws
 * away a custom `spacing`, `sizing` or `motion` with them. Nothing errors and
 * nothing looks wrong until somebody reads the German build. `ProvideTokens`
 * defaults every parameter to *the value already in scope*, so an argument you
 * do not pass is an argument that does not change.
 *
 * Use [KontourTheme] once, at the root. Use this for everything after it.
 *
 * ### The derived locals, which is the whole reason this is a function
 *
 * The obvious hand-rolled version —
 * `CompositionLocalProvider(LocalColourScheme provides scheme) { … }` — gets the
 * new scheme everywhere and the **old** content colour on every `Text` and
 * `Icon` in the subtree, because `LocalContentColour` is derived from the scheme
 * at the point [KontourTheme] provides it and is not re-derived by providing the
 * scheme again. Half the subtree changes. Nothing errors. The same trap sits
 * under `LocalTextStyle`, derived from `typography.bodyMedium`.
 *
 * So this re-provides both — but only where they have not been *narrowed*. A
 * `Surface` sets `LocalContentColour` to the colour that reads on the ground it
 * just painted, and that ground is still painted in the outgoing palette, so its
 * narrowing is not stale and must survive. The test is equality with the
 * outgoing token: unchanged means inherited from the theme and should follow the
 * theme; anything else was set by something closer and is left alone.
 *
 * ### What it deliberately does not take
 *
 * `contrast`, `backdropBlur`, `haptics` and `feedback` are [KontourTheme]'s
 * business — they are decisions about the app, not tokens a subtree restyles —
 * and each has a public local of its own for the rare case. A subtree that wants
 * a different *contrast tier* wants a different `ColourScheme` and a different
 * `Sizing`, which are two arguments here.
 */
@Composable
fun ProvideTokens(
    colours: ColourScheme = LocalColourScheme.current,
    typography: Typography = LocalTypography.current,
    shapes: Shapes = LocalShapes.current,
    spacing: Spacing = LocalSpacing.current,
    elevation: Elevation = LocalElevation.current,
    motion: Motion = LocalMotion.current,
    sizing: Sizing = LocalSizing.current,
    strings: Strings = LocalStrings.current,
    content: @Composable () -> Unit,
) {
    val outgoingColours = LocalColourScheme.current
    val outgoingTypography = LocalTypography.current

    val contentColour = LocalContentColour.current
    val textStyle = LocalTextStyle.current

    CompositionLocalProvider(
        LocalColourScheme provides colours,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalSpacing provides spacing,
        LocalElevation provides elevation,
        LocalMotion provides motion,
        LocalSizing provides sizing,
        LocalStrings provides strings,
        LocalContentColour provides
            if (contentColour == outgoingColours.content) colours.content else contentColour,
        LocalTextStyle provides
            if (textStyle == outgoingTypography.bodyMedium) typography.bodyMedium else textStyle,
        content = content,
    )
}

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
