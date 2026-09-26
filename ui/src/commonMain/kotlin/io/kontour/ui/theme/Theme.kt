package io.kontour.ui.theme

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
import io.kontour.ui.interaction.FeedbackFloor
import io.kontour.ui.interaction.LocalFeedbackFloor
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.rememberDefaultFeedbackDispatcher
import io.kontour.ui.platform.platformPrefersHighContrast
import io.kontour.ui.platform.platformPrefersReducedMotion
import io.kontour.ui.platform.platformReportAppearance
import io.kontour.ui.platform.platformSystemDark

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
    /**
     * The scheme in effect, which during a cross-fade is not the one the local
     * carries.
     *
     * See [ThemeFadeState]. The static local holds the fade's **target** so that
     * providing it costs one whole-subtree invalidation per theme change rather
     * than one per frame; the frames in between come off a handle whose identity
     * does not move, and reading them here is a snapshot read, so a composable
     * that reads a colour recomposes and one that does not is left alone.
     *
     * The handle applies only while the value the local carries is the same
     * object it was installed against. Anything else — `ProvideTokens`, or a
     * caller providing `LocalColourScheme` directly — was set closer and wins.
     */
    val colours: ColourScheme
        @Composable @ReadOnlyComposable get() {
            val provided = LocalColourScheme.current
            val fading = LocalThemeFade.current
            return if (fading != null && fading.targetColours === provided) {
                fading.fade.colours
            } else {
                provided
            }
        }

    val typography: Typography
        @Composable @ReadOnlyComposable get() = LocalTypography.current

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    /** @see colours — the elevation scale travels with the scheme and resolves the same way. */
    val elevation: Elevation
        @Composable @ReadOnlyComposable get() {
            val provided = LocalElevation.current
            val fading = LocalThemeFade.current
            return if (fading != null && fading.targetElevation === provided) {
                fading.fade.elevation
            } else {
                provided
            }
        }

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val sizing: Sizing
        @Composable @ReadOnlyComposable get() = LocalSizing.current

    /**
     * The geometry a brand adjusts. See [ComponentDefaults] for what earns a
     * field there and what belongs in one of the families above instead.
     */
    val componentDefaults: ComponentDefaults
        @Composable @ReadOnlyComposable get() = LocalComponentDefaults.current

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
 * Whether the **device** is set to dark, which is not `isSystemInDarkTheme()`.
 *
 * Public because the difference is a trap for any application that offers a dark
 * switch of its own, and this library makes that trap: [KontourTheme] tells the
 * host what appearance it is drawing, and on iOS the only property that says so
 * is also the one Compose Multiplatform reads its system theme back out of. An
 * app that reports dark and then asks `isSystemInDarkTheme()` what the device is
 * set to gets `true`, whatever the phone says.
 *
 * So an application with a **"follow the device" setting** should resolve it
 * through this and not through Compose's own. An application that simply follows
 * the system and has no switch can use either; there is no override installed to
 * read back.
 *
 * `io.kontour.ui.platform.platformSystemDark` has the mechanism and the reason
 * per platform.
 */
@Composable
fun deviceInDarkTheme(): Boolean = platformSystemDark()

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
    darkTheme: Boolean = deviceInDarkTheme(),
    contrast: ContrastLevel = if (platformPrefersHighContrast()) ContrastLevel.High else ContrastLevel.Standard,
    reduceMotion: Boolean = platformPrefersReducedMotion(),
    backdropBlur: Boolean = true,
    /**
     * Whether a change of scheme cross-fades rather than cutting.
     *
     * **Off**, and this reverses the decision twice over, so it is worth the
     * paragraph. It was off, then on — *"the alternative to a half-animated swap
     * is not a clean cut, it is every colour on screen changing between two
     * frames, which on a device reads as a glitch"* — and it is off again now,
     * on the report that switching dark mode is *"ridiculously laggy"* on
     * Android and on the reader's own verdict once they had seen both:
     * *"it's already not visible on iOS, and I think it looks fine."*
     *
     * The cost is what makes that verdict easy to act on. Measured by
     * `ThemeFadeCostDiagnostic` at twenty cards: a resting frame is 5.83ms and a
     * fading one 20.52, and with no shadows at all the same pair is 1.54 and
     * 9.53 — so more than half of a fading frame is blurred silhouettes being
     * re-rasterised for a change nobody was looking at. Sixteen of those frames
     * is what a switch used to cost, and `FirstThemeSwitchCostDiagnostic` puts
     * the first one at 805ms of frame time against 298 by the fourth.
     *
     * Nothing about the mechanism is deleted, because a scheme driven by
     * something an app animates itself may well want it: pass `true` and the
     * fade is exactly what it was. What it costs then is a frame's work for
     * every composable that reads a colour, for the fade's duration — see
     * `animatedTheme`. It used to cost the whole application recomposing once
     * per frame, because the scheme fed a static composition local directly; it
     * feeds that local the fade's *target* now and the frames go through a
     * handle that tracks its readers, so the difference is between "everything,
     * a dozen times" and "the things that are changing colour".
     */
    animateThemeChanges: Boolean = false,
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
    componentDefaults: ComponentDefaults = remember { ComponentDefaults() },
    strings: Strings = remember { Strings() },
    /**
     * How much physical feedback the app gives. See [HapticsLevel].
     *
     * Separate from [feedback], which decides what each intent *feels like*.
     * This decides how many of them fire at all, and is the one an app is likely
     * to want to put behind a user-facing setting.
     */
    hapticsLevel: HapticsLevel = HapticsLevel.Standard,
    feedback: FeedbackDispatcher = rememberDefaultFeedbackDispatcher(hapticsLevel),
    content: @Composable () -> Unit,
) {
    val feedbackFloor = remember { FeedbackFloor() }
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
        animatedTheme(colours, elevation, motion, contrast)
    } else {
        null
    }
    // Written before the content composes, and read by `Theme.colours` from
    // anywhere below. A plain field rather than snapshot state on purpose:
    // nothing should recompose because the *target* was re-stated, and it is
    // set on every composition of this function, which is the only place the
    // static locals below are provided from.
    faded?.targetColours = colours
    faded?.targetElevation = elevation
    val resolvedColours = faded?.fade?.colours ?: colours

    // The host's chrome — Android's status and navigation bars, iOS's status
    // bar, a browser's scrollbars and address bar — takes its colours from a
    // flag nothing here had ever set, so a dark app under a light system got
    // dark icons on a dark bar. Reported from a phone.
    //
    // `colours.isDark`, not `resolvedColours.isDark`: the target rather than the
    // cross-fading scheme. A status bar is light or dark with nothing in
    // between, so it flips once at the start of the fade instead of being
    // rewritten on every frame of it.
    //
    // Guarded by the same question the modality tracker asks. A nested theme —
    // a screen forcing dark over a light app — re-provides tokens for its
    // subtree and has no business repainting the window.
    if (!alreadyTracking) platformReportAppearance(colours.isDark)

    CompositionLocalProvider(
        // **The target, not the frame.** This is a static local, so providing it
        // invalidates everything below — which is right for a theme change and
        // ruinous once a fade provides a new scheme sixty times a second. The
        // frames go through `LocalThemeFade`, which never changes identity, and
        // `Theme.colours` puts the two back together.
        LocalColourScheme provides colours,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalSpacing provides spacing,
        LocalElevation provides elevation,
        LocalThemeFade provides faded,
        LocalMotion provides motion,
        LocalSizing provides sizing,
        LocalComponentDefaults provides componentDefaults,
        LocalStrings provides strings,
        LocalContrastLevel provides contrast,
        LocalBackdropBlur provides backdropBlur,
        LocalContentColour provides resolvedColours.content,
        LocalTextStyle provides typography.bodyMedium,
        LocalFeedback provides feedback,
        // One floor per theme, so every light haptic under it shares a rate
        // limit. A floor per component is a floor per component, and a hand does
        // not feel components.
        LocalFeedbackFloor provides feedbackFloor,
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
 * happen anyway.
 *
 * It holds because what this carries changes **once** per theme change. A fade
 * used to provide a new scheme per frame through here, which is a dozen full-app
 * recompositions in a row and was measured on a phone as a 99.9ms peak; the
 * frames go through [LocalThemeFade] now and this carries the fade's target. See
 * `Theme.colours`, which is where a reader meets the two.
 *
 * ### They throw rather than defaulting
 *
 * Outside a [KontourTheme] there is no sensible palette, and a component drawing
 * in some fallback grey is a bug that looks like a design. The message names the
 * fix.
 */
val LocalColourScheme = staticCompositionLocalOf<ColourScheme> { error(NOT_IN_THEME) }

/**
 * The fade [LocalColourScheme] is the target of, or null when nothing is fading.
 *
 * Internal, and it stays internal: [LocalColourScheme] is public so that a
 * caller can *provide* a scheme for a subtree, and this is the mechanism by
 * which a provided one is recognised and obeyed rather than something anybody
 * needs to hand a value to. Providing the scheme is still the whole public
 * story; `Theme.colours` is where the two meet.
 *
 * Null rather than throwing, unlike every local beside it, because a theme with
 * `animateThemeChanges = false` legitimately has no fade and the absence is the
 * answer rather than a mistake.
 */
internal val LocalThemeFade = staticCompositionLocalOf<ThemeFadeState?> { null }

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

/** @see Theme.componentDefaults */
val LocalComponentDefaults = staticCompositionLocalOf<ComponentDefaults> { error(NOT_IN_THEME) }

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
 *     KontourTheme(darkTheme = true) { … }   // ← every string is English again
 * }
 * ```
 *
 * silently reverts the strings, resets `HapticsLevel.Off` to `Standard`, and throws
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
    componentDefaults: ComponentDefaults = LocalComponentDefaults.current,
    strings: Strings = LocalStrings.current,
    content: @Composable () -> Unit,
) {
    // What is actually in effect above, which mid-fade is not what the local
    // carries — see `Theme.colours`. The test below is "was this inherited from
    // the theme", and comparing against the fade's target instead would answer
    // no on every frame of one.
    val outgoingColours = Theme.colours
    // And what will be in effect below: a caller who did not change the scheme
    // has not stopped the fade, so the ink goes on fading with it.
    val incomingColours = if (colours === LocalColourScheme.current) outgoingColours else colours
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
        LocalComponentDefaults provides componentDefaults,
        LocalStrings provides strings,
        LocalContentColour provides
            if (contentColour == outgoingColours.content) {
                incomingColours.content
            } else {
                contentColour
            },
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
