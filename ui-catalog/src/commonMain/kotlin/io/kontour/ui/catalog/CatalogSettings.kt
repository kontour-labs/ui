package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * ### Why three of these are nullable
 *
 * `dark`, `highContrast` and `reduceMotion` are `Boolean?`, where null means
 * "whatever the platform says". They were plain `false` on the site, passed
 * straight into `KontourTheme` — which defaults each to the operating system —
 * so the site documenting the library's reduced-motion support was overriding
 * every visitor's request for it with the answer "no". Found by measurement: a
 * browser with `prefers-reduced-motion: reduce` emulated counted the same number
 * of animation frames as one without.
 *
 * A theme may decline a mode (see the demo themes), and the resolution order is
 * *reader's preference, then the theme's policy*. Storing the preference rather
 * than overwriting it is what lets a switch that a dark-only theme has disabled
 * come back to what the reader had chosen when they leave it.
 */
@Stable
class CatalogSettings {

    /** Null follows the operating system. */
    var dark by mutableStateOf<Boolean?>(null)

    /** Null follows the operating system. */
    var highContrast by mutableStateOf<Boolean?>(null)

    /** Null follows the operating system. */
    var reduceMotion by mutableStateOf<Boolean?>(null)

    var rightToLeft by mutableStateOf(false)

    /**
     * A *platform* multiplier, not a type ramp.
     *
     * Applied through `LocalDensity` outside the theme, because the ramp is
     * already in sp and this is what makes sp mean something different. Scaling
     * the ramp instead would look similar and prove nothing.
     */
    var textScale by mutableStateOf(1f)

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
