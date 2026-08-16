package io.kontour.ui.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.components.display.Banner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.components.selection.Stepper
import io.kontour.ui.components.text.PasswordField
import io.kontour.ui.foundation.SystemIcons
import androidx.compose.foundation.text.input.TextFieldState
import kotlin.test.Test

/**
 * A word supplied to the theme reaches the components that draw it.
 *
 * Forty-seven English literals used to live in parameter defaults, where the
 * only way to change one was to pass it at every call site. They read
 * [Theme.strings] now, and this is the test that says so — one component per
 * mechanism, because they do not all consume a string the same way:
 *
 * - `Banner` puts it on an icon button's `contentDescription` — one the caller
 *   has to opt into by supplying `dismissIcon`, since the library bundles no
 *   icon set for callers
 * - `Stepper` puts it on two of them
 * - `PasswordField` swaps between two words as its own state changes
 *
 * Reverting any of those defaults to a literal fails here. Rendering the
 * *default* English proves nothing — a literal passes that too — so every
 * assertion is against a word no component could produce on its own.
 */
@OptIn(ExperimentalTestApi::class)
class StringsReachComponentsTest {

    private val german = Strings(
        dismiss = "Schließen",
        decrease = "Weniger",
        increase = "Mehr",
        showPassword = "Passwort anzeigen",
    )

    @Test
    fun aBannersDismissAffordanceTakesTheThemesWord() = runComposeUiTest {
        setContent {
            KontourTheme(strings = german) {
                Banner(
                    tone = BannerTone.Info,
                    onDismissRequest = {},
                    dismissIcon = SystemIcons.Close,
                ) { +"Delayed" }
            }
        }

        onNodeWithContentDescription("Schließen").assertIsDisplayed()
    }

    @Test
    fun aSteppersTwoButtonsBothTakeIt() = runComposeUiTest {
        setContent {
            KontourTheme(strings = german) {
                Stepper(value = 2, onValueChange = {}, contentDescription = "Passengers")
            }
        }

        onNodeWithContentDescription("Weniger").assertIsDisplayed()
        onNodeWithContentDescription("Mehr").assertIsDisplayed()
    }

    @Test
    fun aPasswordFieldsRevealAffordanceTakesIt() = runComposeUiTest {
        setContent {
            KontourTheme(strings = german) {
                PasswordField(
                    state = TextFieldState(),
                    label = "Password",
                    revealIcon = SystemIcons.Check,
                    hideIcon = SystemIcons.Close,
                )
            }
        }

        onNodeWithContentDescription("Passwort anzeigen").assertIsDisplayed()
    }

    /**
     * The default set is still English, so an app that supplies nothing is
     * unchanged. Guards the other direction: a `Strings()` whose fields were
     * blank would pass every test above.
     */
    @Test
    fun theDefaultSetIsStillEnglish() = runComposeUiTest {
        setContent {
            KontourTheme {
                Banner(
                    tone = BannerTone.Info,
                    onDismissRequest = {},
                    dismissIcon = SystemIcons.Close,
                ) { +"Delayed" }
            }
        }

        onNodeWithContentDescription("Dismiss").assertIsDisplayed()
    }

    /**
     * A call site still wins over the theme.
     *
     * The parameters did not go away — they default from the theme — and this
     * is the property that makes that worth doing rather than replacing them
     * with reads at the point of use.
     */
    @Test
    fun aCallSiteStillOverridesTheTheme() = runComposeUiTest {
        setContent {
            KontourTheme(strings = german) {
                Banner(
                    tone = BannerTone.Info,
                    onDismissRequest = {},
                    dismissIcon = SystemIcons.Close,
                    dismissLabel = "Verwerfen",
                ) { +"Delayed" }
            }
        }

        onNodeWithContentDescription("Verwerfen").assertIsDisplayed()
    }
}
