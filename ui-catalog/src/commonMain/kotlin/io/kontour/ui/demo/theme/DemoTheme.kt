package io.kontour.ui.demo.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.kontour.ui.catalog.CatalogSettings
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.ComponentDefaults
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.Elevation
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.Shapes
import io.kontour.ui.theme.Sizing
import io.kontour.ui.theme.Typography
import io.kontour.ui.theme.kontourColourScheme
import io.kontour.ui.theme.kontourElevation
import io.kontour.ui.theme.kontourMotion
import io.kontour.ui.theme.kontourSizing
import io.kontour.ui.theme.kontourTypography
import io.kontour.ui.theme.outfitFontFamily

/** Which grounds a theme is willing to be drawn on. */
enum class ThemeMode { Light, Dark }

/**
 * A whole look, as data, so the gallery and the site can offer several.
 *
 * **Not a framework.** It is a class of values and small factory lambdas, every
 * one defaulting to the library's own factory, plus a list with a uniqueness
 * check — the same shape as `componentDemos`, where adding one is adding a line.
 * There is nothing to implement and nothing to register.
 *
 * The library ships no product in it, deliberately: a library that shipped
 * somebody's brand would make every app using it look like that somebody. These
 * live in `:ui-catalog` rather than `:ui` for that reason, and that reasoning
 * covers a demonstration brand as much as a real one.
 *
 * ### The one rule
 *
 * A theme answers [colours] for **every** combination in [modes] × [tiers], and
 * claims no combination it cannot serve. That single rule is what makes the
 * disabled switches truthful and what lets the contrast suite walk it —
 * `DemoThemeContrastTest` asks for exactly the combinations claimed here.
 *
 * @param modes What this theme can be asked for. A theme that offers one mode is
 *   not a bug: GTurbo is a near-black product, and a light GTurbo would be a
 *   different design rather than this one with the values inverted. What matters
 *   is that it *says so*, so a switch that cannot do anything is drawn disabled
 *   rather than drawn live and ignored.
 * @param tiers Which contrast tiers it has authored a palette for. Declining
 *   `High` is a downgrade to make knowingly rather than by omission.
 * @param componentDefaults The geometry a brand adjusts, and the casing of a
 *   control's label. Everything on it defaults to the library's own value, so a
 *   theme names only what it changes.
 */
@Immutable
class DemoTheme(
    val name: String,
    val colours: (dark: Boolean, contrast: ContrastLevel) -> ColourScheme,
    val modes: Set<ThemeMode> = setOf(ThemeMode.Light, ThemeMode.Dark),
    val tiers: Set<ContrastLevel> = setOf(ContrastLevel.Standard, ContrastLevel.High),
    val shapes: Shapes = Shapes(),
    val elevation: (dark: Boolean) -> Elevation = ::kontourElevation,
    val motion: (reduceMotion: Boolean) -> Motion = ::kontourMotion,
    val sizing: (contrast: ContrastLevel) -> Sizing = ::kontourSizing,
    val typography: @Composable () -> Typography = { kontourTypography(outfitFontFamily()) },
    val componentDefaults: ComponentDefaults = ComponentDefaults(),
) {
    init {
        require(modes.isNotEmpty()) { "$name offers no mode at all" }
        require(tiers.isNotEmpty()) { "$name offers no contrast tier at all" }
    }

    /**
     * The mode this theme will actually draw, given what the reader asked for.
     *
     * The reader's preference is *stored* and this resolves it, rather than the
     * preference being overwritten — which is what lets a switch a dark-only
     * theme has disabled come back to the reader's own choice when they leave it.
     */
    fun resolveDark(requested: Boolean): Boolean {
        val wanted = if (requested) ThemeMode.Dark else ThemeMode.Light
        return (if (wanted in modes) wanted else modes.first()) == ThemeMode.Dark
    }

    /** The tier this theme will actually draw, given what the reader asked for. */
    fun resolveTier(requested: ContrastLevel): ContrastLevel =
        if (requested in tiers) requested else tiers.first()

    /** True when the reader's dark switch can do anything under this theme. */
    val offersBothModes: Boolean get() = modes.size > 1

    /** True when the reader's high-contrast switch can do anything under this theme. */
    val offersBothTiers: Boolean get() = tiers.size > 1

    override fun toString(): String = name
}

/** The library's own look, named so it can sit in a list beside the others. */
val kontourDemoTheme = DemoTheme(
    name = "Kontour",
    colours = { dark, contrast -> kontourColourScheme(dark, contrast) },
)

/**
 * Every theme the gallery and the site offer, in the order they are shown.
 *
 * Kontour first: it is the default, and a picker whose first entry is the thing
 * you already have is a picker you can read without pressing anything.
 */
val demoThemes: List<DemoTheme> = listOf(kontourDemoTheme, gTurboDemoTheme).also { themes ->
    require(themes.map(DemoTheme::name).distinct().size == themes.size) {
        "two demo themes share a name: " +
            themes.groupBy(DemoTheme::name).filterValues { it.size > 1 }.keys.joinToString()
    }
}

/**
 * Installs [CatalogSettings]'s chosen theme, in one place.
 *
 * **The only `KontourTheme` call either surface makes.** That is the point
 * rather than tidiness: a nested `KontourTheme` re-resolves every argument it is
 * not given, so two call sites that each pass "the arguments they care about"
 * disagree about everything else — which is how the gallery came to ignore the
 * site's dark switch, and how it would go on to ignore its typography and
 * shapes. One function, all ten token arguments, no way to forget one.
 *
 * @param systemDark What the platform reports, resolved by the caller so a
 *   golden can pin it.
 */
@Composable
fun DemoThemeProvider(
    settings: CatalogSettings,
    systemDark: Boolean,
    systemHighContrast: Boolean,
    systemReduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val theme = settings.theme
    val dark = theme.resolveDark(settings.dark ?: systemDark)
    val tier = theme.resolveTier(
        if (settings.highContrast ?: systemHighContrast) ContrastLevel.High
        else ContrastLevel.Standard
    )
    val reduceMotion = settings.reduceMotion ?: systemReduceMotion

    // A theme swap fades its colours and snaps its geometry.
    //
    // **This reverses a decision, on evidence.** It used to cut: `swapping` was
    // computed here and passed as `animateThemeChanges = !swapping`, on the
    // reasoning that `animatedTheme` interpolates the scheme and the elevation
    // and nothing else — so across a swap the corners and the typeface would
    // change on the first frame while the palette drifted for 220ms after them,
    // and "a half-animated transition reads as broken".
    //
    // The premise is still true: there is no `lerp(Shapes)` or `lerp(Typography)`
    // anywhere in this repository and a font family is a discrete resource, so
    // token interpolation genuinely cannot carry them. What was wrong is the
    // conclusion, and a phone is what showed it: the alternative to a
    // half-animated swap is not a clean cut, it is **every colour on screen
    // changing between two frames**, which on a device reads as a glitch rather
    // than as a decision. Half of a large change animating beats none of it.
    //
    // The geometry snapping is also what keeps the frame affordable — see
    // `lerpTheme`, where the elevation now steps at the midpoint for the same
    // reason. What is not attempted is a `Crossfade` of two rendered trees,
    // which *would* carry the shapes and the type: `OverlayHost`, the toast host
    // state, every scroll position and every `SheetState` live inside the
    // content lambda, so the incoming tree would get fresh `remember` slots —
    // closing an open sheet and jumping every scrolled page back to the top.
    KontourTheme(
        darkTheme = dark,
        contrast = tier,
        reduceMotion = reduceMotion,
        colours = theme.colours(dark, tier),
        typography = theme.typography(),
        shapes = theme.shapes,
        elevation = theme.elevation(dark),
        motion = theme.motion(reduceMotion),
        sizing = theme.sizing(tier),
        componentDefaults = theme.componentDefaults,
        content = content,
    )
}
