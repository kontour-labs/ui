package io.kontour.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
 * Cross-fades between themes instead of cutting.
 *
 * Switching to dark mode, changing the accent, or moving contrast tier used to
 * be a single frame: one composition with the old colours, the next with the
 * new. Every other state change in the library animates, and the largest one
 * did not.
 *
 * ### One animation, not fifty
 *
 * A scheme is 46 colours. Animating each with its own `animateColorAsState`
 * would be 46 `Animatable`s and 46 coroutines for a transition where every one
 * of them starts and ends together — so this runs **one** float and lerps the
 * scheme from it.
 *
 * ### What it costs, and why that is the right trade anyway
 *
 * [LocalColourScheme] is a `staticCompositionLocalOf`, so a new scheme
 * invalidates the whole content subtree — the entire application, once per
 * frame, for the length of the fade. That is the exact cost round 18 removed
 * from the window size class, and it is being spent deliberately here: a theme
 * change is a rare, deliberate, user-initiated event, and it is *supposed* to
 * repaint everything. A window resize is neither of those things, which is why
 * one animates and the other must not.
 *
 * It is still a few hundred milliseconds of full recomposition, so it is a
 * parameter rather than a fact — see `KontourTheme`'s `animateThemeChanges`.
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
): ThemeFade {
    val target = ThemeFade(colours, elevation)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val fraction = remember { Animatable(1f) }

    LaunchedEffect(colours, elevation) {
        if (colours == to.colours && elevation == to.elevation) return@LaunchedEffect
        from = lerpTheme(from, to, fraction.value)
        to = ThemeFade(colours, elevation)
        fraction.snapTo(0f)
        fraction.animateTo(1f, motion.tweenDefault())
    }

    val f = fraction.value
    return remember(from, to, f) {
        when {
            f >= 1f -> to
            f <= 0f -> from
            else -> lerpTheme(from, to, f)
        }
    }
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
