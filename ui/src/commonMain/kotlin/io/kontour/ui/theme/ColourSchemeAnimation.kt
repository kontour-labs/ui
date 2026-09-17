package io.kontour.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp

/**
 * A scheme and an elevation scale at the same point of the same fade.
 *
 * They travel together or the transition has a seam in it: shadows at dark-mode
 * strength over surfaces that are still light is exactly the state this pair
 * exists to make unrepresentable.
 */
@Immutable
internal class ThemeFade(val colours: ColourScheme, val elevation: Elevation)

/**
 * A handle on a fade in progress, whose **identity never changes**.
 *
 * The reason there is a handle at all rather than a value. [LocalColourScheme]
 * is a `staticCompositionLocalOf`, which does not track reads: providing a new
 * value invalidates the entire subtree under the provider. That is the right
 * trade for a token family — see the note on the local — right up until the
 * value changes sixty times a second, which is what a fade does. A theme change
 * was measured at a 99.9ms peak frame on a phone, and about thirteen full-app
 * recompositions is the floor under it.
 *
 * So the static locals get the fade's **target**, which changes once, and this
 * gets the frames in between. [fade] is snapshot state, so a composable that
 * reads a colour registers with it and one that does not is untouched.
 *
 * ### [targetColours] and [targetElevation] are identities, not values
 *
 * `ProvideTokens` and any caller may provide `LocalColourScheme` for a subtree,
 * and below that point this handle is describing a fade between two schemes
 * neither of which is in effect any more. `Theme.colours` uses it only while the
 * value the static local carries is *the same object* this was installed
 * against; anything else was provided by something closer and wins.
 *
 * Identity rather than equality, because two equal schemes from different
 * providers are still different intentions, and the cheap test is the correct
 * one here.
 */
@Stable
internal class ThemeFadeState(initial: ThemeFade) {
    var fade: ThemeFade by mutableStateOf(initial)

    var targetColours: ColourScheme = initial.colours
    var targetElevation: Elevation = initial.elevation
}

/**
 * Cross-fades between themes instead of cutting.
 *
 * Switching to dark mode, changing the accent, or moving contrast tier used to
 * be a single frame: one composition with the old colours, the next with the
 * new. Every other state change in the library animates, and the largest one
 * did not.
 *
 * ### One animation, not fifty
 *
 * A scheme is 51 colours, counting the five nested in `accent` and the four
 * status tones and the four in `code`. Animating each with its own
 * `animateColorAsState` would be 51 `Animatable`s and 51 coroutines for a
 * transition where every one of them starts and ends together — so this runs
 * **one** float and lerps the scheme from it.
 *
 * ### What it used to cost, and the argument that was wrong
 *
 * [LocalColourScheme] is a `staticCompositionLocalOf`, so a new scheme
 * invalidates the whole content subtree. This used to provide one **per frame**,
 * which meant the entire application recomposed about a dozen times in a row as
 * fast as the device could manage — and the note here defended it: a theme
 * change is a rare, deliberate, user-initiated event, and it is *supposed* to
 * repaint everything.
 *
 * It is. Once. Twelve times is not twelve times more correct, and a phone
 * reported the difference as a 99.9ms peak frame.
 *
 * So the static local gets the fade's **target**, which changes once, and the
 * frames in between go through [ThemeFadeState], whose identity never moves and
 * whose field is snapshot state. A composable that reads a colour recomposes on
 * every frame of a fade, which it must; one that does not is untouched.
 * `ThemeFadeRecompositionTest` holds both halves of that.
 *
 * It is still a whole-app repaint, so it stays a parameter rather than a fact —
 * see `KontourTheme`'s `animateThemeChanges`.
 *
 * ### The elevation scale travels with it
 *
 * [Elevation] is a sibling token rather than part of the scheme, and it used to
 * be resolved from the `darkTheme` flag — so shadows cut to their dark-mode
 * strength on the **first** frame and sat there while the surfaces beneath them
 * were still moving. Both come out of one [Animatable] now, which is the only
 * way they cannot drift: two animations with the same spec agree until the day
 * one of the specs is tuned.
 *
 * It steps at the fade's **midpoint** rather than interpolating, and that is a
 * measurement rather than a preference — see [lerpTheme], which has the numbers.
 * A shadow whose alpha and radius move every frame cannot reuse the blur it
 * rasterised on the frame before, and blurs are about four fifths of what a fade
 * costs.
 *
 * ### Interrupting mid-fade
 *
 * A scheme arriving while a fade is running restarts from **where the fade
 * actually is**, not from where it began. Flipping dark mode twice quickly
 * otherwise jumps back to the first scheme before starting the second, which is
 * more visible than not animating at all.
 */
@Composable
internal fun animatedTheme(
    colours: ColourScheme,
    elevation: Elevation,
    motion: Motion,
): ThemeFadeState {
    val state = remember { ThemeFadeState(ThemeFade(colours, elevation)) }
    val fraction = remember { Animatable(1f) }

    LaunchedEffect(colours, elevation) {
        val to = ThemeFade(colours, elevation)
        if (state.fade.colours == to.colours && state.fade.elevation == to.elevation) {
            state.fade = to
            return@LaunchedEffect
        }
        // Where the fade actually is, which is what interrupting one has to
        // start from. It used to be reconstructed here as
        // `lerpTheme(from, to, fraction.value)`; it is simply the last frame
        // written now, because the frames are written rather than derived.
        val from = state.fade
        fraction.snapTo(0f)
        // **Written from the animation, not read in composition.**
        //
        // The fraction used to be a state read in the body of this function, so
        // `KontourTheme` recomposed on every frame of a fade and re-provided a
        // new scheme to a static local — which is the whole-subtree
        // invalidation this is here to stop. Nothing reads the fraction now: the
        // animation pushes each frame into [ThemeFadeState.fade] from a
        // coroutine, and the only composables that recompose are the ones that
        // read a colour out of it.
        fraction.animateTo(1f, motion.tweenDefault()) {
            state.fade = lerpTheme(from, to, value)
        }
        state.fade = to
    }

    return state
}

/**
 * Both halves, from one fraction — but the elevation **snaps** at the midpoint.
 *
 * ### Because the shadows are the frame
 *
 * `ThemeFadeCostDiagnostic` measures a fade three ways on the same tree. At
 * twenty cards a resting frame is 4.05ms, a fading frame is 15.91, and a fading
 * frame with the elevation held still is 11.62. With the shadows taken out
 * altogether the same fade costs 2.62 against 0.90 at rest — so **the shadows
 * are about four fifths of what a fade costs**, and interpolating their
 * parameters is a third of that again.
 *
 * The reason is cheap to state: a rasterised blur can be reused while its
 * parameters hold, and `lerp(Elevation)` moves every layer's alpha and radius on
 * every frame — dark's scale multiplies alpha by 2.4 — so every elevated surface
 * on screen misses the cache on every frame of the fade and re-rasterises both
 * of its layers. That is the jitter reported from a phone.
 *
 * ### Why snapping is not a compromise
 *
 * [ColourScheme.isDark] already switches at the midpoint rather than
 * interpolating, for the reason given there: it is not a quantity and cannot be
 * half-way. A shadow's *strength* is a quantity, but the half-way value of one
 * is not a thing anybody has asked for — what a reader sees is surfaces changing
 * colour, and the shadow under a card is a few pixels of near-black either way.
 * Stepping it once, in the middle, while the surfaces are mid-way, is the least
 * visible moment there is to do it.
 *
 * What it is *not* is pinning: the shadows still arrive at the new scale, which
 * is the whole reason `Elevation` travels with the scheme at all. They arrive in
 * one step instead of fourteen.
 *
 * Measured after the change, same machine, twenty cards: see the diagnostic's
 * table, which prints a fading frame beside the pinned scene it is now expected
 * to resemble.
 */
internal fun lerpTheme(start: ThemeFade, stop: ThemeFade, fraction: Float): ThemeFade =
    ThemeFade(
        colours = lerpColourScheme(start.colours, stop.colours, fraction),
        elevation = if (fraction < 0.5f) start.elevation else stop.elevation,
    )

/**
 * Every colour in the scheme, interpolated.
 *
 * [ColourScheme.isDark] is not a colour and cannot be half-way: it switches at
 * the midpoint, so anything reading it flips once, in the middle, rather than at
 * one end where it would disagree with what is on screen for most of the fade.
 *
 * Its readers are `Skeleton`, `Tag` and `Surface`, each choosing a *content*
 * colour by which ground it is on. [lerpTheme] steps the elevation scale at the
 * same midpoint and for a different reason — this one cannot be half-way, that
 * one must not be, because a blur that changes every frame cannot be reused.
 */
internal fun lerpColourScheme(start: ColourScheme, stop: ColourScheme, fraction: Float): ColourScheme =
    ColourScheme(
        isDark = if (fraction < 0.5f) start.isDark else stop.isDark,
    background = lerp(start.background, stop.background, fraction),
    surface = lerp(start.surface, stop.surface, fraction),
    surfaceSunken = lerp(start.surfaceSunken, stop.surfaceSunken, fraction),
    surfaceIndicator = lerp(start.surfaceIndicator, stop.surfaceIndicator, fraction),
    surfaceRaised = lerp(start.surfaceRaised, stop.surfaceRaised, fraction),
    surfaceInverse = lerp(start.surfaceInverse, stop.surfaceInverse, fraction),
    onSurfaceInverse = lerp(start.onSurfaceInverse, stop.onSurfaceInverse, fraction),
    content = lerp(start.content, stop.content, fraction),
    contentMuted = lerp(start.contentMuted, stop.contentMuted, fraction),
    contentSubtle = lerp(start.contentSubtle, stop.contentSubtle, fraction),
    contentDisabled = lerp(start.contentDisabled, stop.contentDisabled, fraction),
    outline = lerp(start.outline, stop.outline, fraction),
    outlineStrong = lerp(start.outlineStrong, stop.outlineStrong, fraction),
    outlineSubtle = lerp(start.outlineSubtle, stop.outlineSubtle, fraction),
    primary = lerp(start.primary, stop.primary, fraction),
    onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
    brand = lerp(start.brand, stop.brand, fraction),
    focusRing = lerp(start.focusRing, stop.focusRing, fraction),
    scrim = lerp(start.scrim, stop.scrim, fraction),
    overlayHover = lerp(start.overlayHover, stop.overlayHover, fraction),
    overlayPressed = lerp(start.overlayPressed, stop.overlayPressed, fraction),
    overlayDragged = lerp(start.overlayDragged, stop.overlayDragged, fraction),
    code = lerp(start.code, stop.code, fraction),
    accent = lerp(start.accent, stop.accent, fraction),
    success = lerp(start.success, stop.success, fraction),
    warning = lerp(start.warning, stop.warning, fraction),
    danger = lerp(start.danger, stop.danger, fraction),
    info = lerp(start.info, stop.info, fraction),
    )

/** [CodeColours] is four colours and interpolates as four colours. */
internal fun lerp(start: CodeColours, stop: CodeColours, fraction: Float): CodeColours =
    CodeColours(
        plain = lerp(start.plain, stop.plain, fraction),
        keyword = lerp(start.keyword, stop.keyword, fraction),
        literal = lerp(start.literal, stop.literal, fraction),
        comment = lerp(start.comment, stop.comment, fraction),
    )

/** [StatusColours] is five colours and interpolates as five colours. */
internal fun lerp(start: StatusColours, stop: StatusColours, fraction: Float): StatusColours =
    StatusColours(
        solid = lerp(start.solid, stop.solid, fraction),
        onSolid = lerp(start.onSolid, stop.onSolid, fraction),
        container = lerp(start.container, stop.container, fraction),
        onContainer = lerp(start.onContainer, stop.onContainer, fraction),
        border = lerp(start.border, stop.border, fraction),
    )
