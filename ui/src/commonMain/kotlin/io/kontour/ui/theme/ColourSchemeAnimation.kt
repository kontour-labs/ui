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
 * strength on the first frame and sat there while the surfaces beneath them were
 * still moving. Both now come out of **one** [Animatable], which is the only way
 * they cannot drift: two animations with the same spec agree until the day one
 * of the specs is tuned.
 *
 * The reason to leave it was that lerping a `List<ShadowSpec>` risks a length
 * mismatch. That turned out to have a one-line answer — a layer that does not
 * exist is that layer at alpha zero — which is in `lerp(Shadow, Shadow, Float)`.
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

/** Both halves, from one fraction. */
internal fun lerpTheme(start: ThemeFade, stop: ThemeFade, fraction: Float): ThemeFade =
    ThemeFade(
        colours = lerpColourScheme(start.colours, stop.colours, fraction),
        elevation = lerp(start.elevation, stop.elevation, fraction),
    )

/**
 * Every colour in the scheme, interpolated.
 *
 * [ColourScheme.isDark] is not a colour and cannot be half-way: it switches at
 * the midpoint, so anything reading it flips once, in the middle, rather than at
 * one end where it would disagree with what is on screen for most of the fade.
 *
 * Its readers are `Skeleton`, `Tag` and `Surface`, each choosing a *content*
 * colour by which ground it is on. The elevation scale used to be the example
 * here and no longer is — it interpolates now, so it has nothing to ask.
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
