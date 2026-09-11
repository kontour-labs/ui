package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import io.kontour.ui.demo.theme.demoThemes
import io.kontour.ui.input.InputModality

/**
 * Every display switch the gallery and the documentation site both offer.
 *
 * **One object, because there is one reader.** The site and the gallery each had
 * their own copy of this state, and the gallery is a *route on the site* — so
 * arriving at `#/gallery` crossed a boundary where the masthead's switches
 * stopped meaning anything. The dark toggle was the visible half of that: the
 * site's `KontourTheme` wraps the gallery's, and the inner one re-resolves every
 * argument it was not given, so it re-read the platform and drew light.
 *
 * That was documented as deliberate on the grounds that the gallery brings its
 * own theme. It does, and it has to — the desktop, Android, web and iOS hosts
 * are three lines each and none of them is a place to decide what a theme is.
 * What was wrong is that the two themes were *given different values*. Now they
 * read one object, so they agree by construction rather than by anybody
 * remembering to keep two argument lists in step.
 *
 * ### Why four of these are nullable
 *
 * `dark`, `highContrast`, `reduceMotion` and `textScale` are nullable, where null
 * means "whatever the platform says". The three booleans were plain `false` on
 * the site, passed straight into `KontourTheme` — which defaults each to the
 * operating system — so the site documenting the library's reduced-motion
 * support was overriding every visitor's request for it with the answer "no".
 * Found by measurement: a browser with `prefers-reduced-motion: reduce` emulated
 * counted the same number of animation frames as one without. `textScale` was
 * the fourth and stayed a plain `1f` a round longer; see its own note.
 *
 * A theme may decline a mode (see the demo themes), and the resolution order is
 * *reader's preference, then the theme's policy*. Storing the preference rather
 * than overwriting it is what lets a switch that a dark-only theme has disabled
 * come back to what the reader had chosen when they leave it.
 *
 * **Null has to be reachable both ways.** A nullable field with no gesture that
 * sets it back to null is a one-way door: the first tap on any switch pinned it
 * for the life of the process and the device's setting stopped being consulted
 * for good. [followsDevice] and [followDevice] are the way back.
 */
@Stable
class CatalogSettings {

    /**
     * Which look to draw. See `demoThemes`.
     *
     * A theme may decline a mode or a tier, and the three nullable switches
     * below are still the *reader's* preference — the theme's policy resolves
     * them at the point of use rather than overwriting them here, which is what
     * lets a switch a dark-only theme has disabled come back to what the reader
     * chose when they leave it.
     */
    var theme by mutableStateOf(demoThemes.first())

    /** Null follows the operating system. */
    var dark by mutableStateOf<Boolean?>(null)

    /** Null follows the operating system. */
    var highContrast by mutableStateOf<Boolean?>(null)

    /** Null follows the operating system. */
    var reduceMotion by mutableStateOf<Boolean?>(null)

    var rightToLeft by mutableStateOf(false)

    /**
     * The font scale to run at, or null to take the device's.
     *
     * Applied through `LocalDensity` outside the theme, because the ramp is
     * already in sp and this is what makes sp mean something different. Scaling
     * the ramp instead would look similar and prove nothing.
     *
     * **Nullable for the same reason the three switches above are**, and it was
     * the one that was not. It defaulted to `1f`, which is not "whatever the
     * phone is set to" — it is *exactly 100%*, and [hostDensity] wrote it over
     * the device's own scale. A phone set to 130% ran the gallery at 100% until
     * somebody opened the sheet and picked a size by hand.
     */
    var textScale by mutableStateOf<Float?>(null)

    /**
     * Whether all four settings above are still deferring to the device.
     *
     * Derived rather than stored, so it cannot disagree with them. What it is
     * *for* is the way back: each of the four is a preference the reader can
     * pin, and before this there was no gesture that unpinned one. Tapping a
     * switch made the field non-null for the life of the process, the platform
     * hooks underneath went on reporting changes to nobody, and the symptom was
     * "the accessibility controls don't react to my phone's settings" — which
     * they did, right up until the first tap.
     */
    val followsDevice: Boolean
        get() = dark == null && highContrast == null && reduceMotion == null && textScale == null

    /** Hands all four back to the device. */
    fun followDevice() {
        dark = null
        highContrast = null
        reduceMotion = null
        textScale = null
    }

    /**
     * Pins all four at what the device is currently giving.
     *
     * The other half of [followsDevice]: leaving Auto has to change nothing on
     * screen, or the switch reads as a setting of its own rather than as a
     * statement about where the settings are coming from. The caller passes the
     * values it has just resolved, because resolution is the theme's business —
     * a theme may decline a mode, and this object holds the reader's preference
     * rather than the theme's answer.
     */
    fun pinToDevice(
        dark: Boolean,
        highContrast: Boolean,
        reduceMotion: Boolean,
        textScale: Float,
    ) {
        this.dark = dark
        this.highContrast = highContrast
        this.reduceMotion = reduceMotion
        // The *device's* font scale, not 1f. Pinning 1f would shrink the type on
        // a phone set to 130% at the moment the reader said "stop following the
        // phone", which is the one thing this is shaped to avoid.
        this.textScale = textScale
    }

    /**
     * Forces the input modality, or follows real input when null.
     *
     * The gallery has had this and the site has not, which is the wrong way
     * round: hover and focus-visible styling is exactly the kind of thing a
     * reader of a component's page wants to check without owning the hardware.
     */
    var modality by mutableStateOf<InputModality?>(null)

    /** The frame-time readout. Gallery-only until the site has somewhere to put it. */
    var frameTimes by mutableStateOf(false)
}

@Composable
fun rememberCatalogSettings(): CatalogSettings = remember { CatalogSettings() }

/**
 * The density a host installs: the device's font scale, or the one the reader picked.
 *
 * **The `?:` is the whole of this function and it used to be absent.** Both hosts
 * wrote `Density(platform.density, settings.textScale)`, and `Density`'s second
 * parameter is the font scale — so `platform.fontScale`, which is where Android
 * and iOS put the user's text-size setting, was read out of the platform density
 * and dropped on the floor. With `textScale` defaulting to a plain `1f`, a phone
 * set to 130% ran the gallery at exactly 100% until somebody opened the sheet
 * and picked a size by hand.
 *
 * The library itself never had this bug: `KontourTheme` does not touch
 * `fontScale`, so an application embedding it scales normally. It was only ever
 * in the two hosts a person can install — which is also why no golden and no
 * browser run could see it. A JVM `ImageComposeScene` has a font scale of 1
 * unless a test says otherwise, and no test said otherwise.
 *
 * ### Replacing rather than multiplying
 *
 * A non-null [textScale] is the scale, not a multiplier of the device's. The
 * control offers 85, 100, 130 and 200%, and the accessibility page promises the
 * library copes at 200% — a promise you cannot check if picking 200% on a phone
 * already at 130% gives you 260%. "Auto" is the multiplier-free way to say
 * "whatever this device does", and it is the default.
 *
 * One function rather than the same line in `Catalog` and `Site`, because the
 * same line in two files is how the second one came to have the same bug.
 */
fun hostDensity(platform: Density, textScale: Float?): Density =
    Density(platform.density, textScale ?: platform.fontScale)

/**
 * The modalities worth offering, "Auto" first.
 *
 * Auto is the honest default — the tracker follows real input, and forcing one
 * is for checking a branch you cannot reach on the host you happen to be on.
 * `Stylus` is left out deliberately: it draws exactly what `Touch` draws, so
 * offering it would be a fifth segment that changes nothing on screen.
 */
val inputModalities: List<Pair<String, InputModality?>> = listOf(
    "Auto" to null,
    "Touch" to InputModality.Touch,
    "Mouse" to InputModality.Mouse,
    "Keyboard" to InputModality.Keyboard,
)
