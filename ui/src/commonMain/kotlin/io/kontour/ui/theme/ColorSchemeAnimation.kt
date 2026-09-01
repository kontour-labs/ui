package io.kontour.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp

/**
 * Cross-fades between colour schemes instead of cutting.
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
 * [LocalColorScheme] is a `staticCompositionLocalOf`, so a new scheme
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
 * ### What does not fade
 *
 * [Elevation] is a sibling token, not part of the scheme, and it is resolved
 * from the `darkTheme` flag rather than from the scheme — so shadows cut to
 * their dark-mode strength at the start of the fade instead of interpolating
 * across it. It is one step in alpha and blur on a surface that is already
 * changing colour underneath it, which is why it has been left: making it fade
 * means lerping a `List<ShadowSpec>` whose length is only *conventionally* the
 * same between the two schemes, and a mismatch there is a crash rather than a
 * cosmetic flaw.
 *
 * ### Interrupting mid-fade
 *
 * A scheme arriving while a fade is running restarts from **where the fade
 * actually is**, not from where it began. Flipping dark mode twice quickly
 * otherwise jumps back to the first scheme before starting the second, which is
 * more visible than not animating at all.
 */
@Composable
internal fun animatedColorScheme(target: ColorScheme, motion: Motion): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val fraction = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = lerpColorScheme(from, to, fraction.value)
        to = target
        fraction.snapTo(0f)
        fraction.animateTo(1f, motion.tweenDefault())
    }

    val f = fraction.value
    return remember(from, to, f) {
        when {
            f >= 1f -> to
            f <= 0f -> from
            else -> lerpColorScheme(from, to, f)
        }
    }
}

/**
 * Every colour in the scheme, interpolated.
 *
 * [ColorScheme.isDark] is not a colour and cannot be half-way: it switches at
 * the midpoint, so anything reading it — the elevation scale cuts its shadows
 * harder on a dark ground — flips once, in the middle, rather than at one end
 * where it would disagree with what is on screen for most of the fade.
 */
internal fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme =
    ColorScheme(
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
    accent = lerp(start.accent, stop.accent, fraction),
    success = lerp(start.success, stop.success, fraction),
    warning = lerp(start.warning, stop.warning, fraction),
    danger = lerp(start.danger, stop.danger, fraction),
    info = lerp(start.info, stop.info, fraction),
    )

/** [StatusColors] is five colours and interpolates as five colours. */
internal fun lerp(start: StatusColors, stop: StatusColors, fraction: Float): StatusColors =
    StatusColors(
        solid = lerp(start.solid, stop.solid, fraction),
        onSolid = lerp(start.onSolid, stop.onSolid, fraction),
        container = lerp(start.container, stop.container, fraction),
        onContainer = lerp(start.onContainer, stop.onContainer, fraction),
        border = lerp(start.border, stop.border, fraction),
    )
