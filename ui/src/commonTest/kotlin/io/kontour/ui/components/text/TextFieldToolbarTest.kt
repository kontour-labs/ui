package io.kontour.ui.components.text

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A text box under an `OverlayHost` has the library's selection toolbar, with no
 * wrapper of any kind.
 *
 * `LocalTextToolbar` was provided in exactly one place in the whole repository —
 * inside [TextSelectionToolbar] — and the only caller of that was a single
 * catalog demo. Every other text box in every app got Compose's own fallback:
 * measured in a phone-sized browser against the built site, a rounded pill
 * reading `Copy  Paste  Cut`, against the library's `Cut  Copy  Paste` after.
 * Both real, and only one of them belongs to the app.
 *
 * The install moved to `OverlayHost`, which is the component that renders
 * overlays and which menus, sheets and toasts already require; a selection
 * toolbar is an overlay.
 *
 * The field would be the obvious place and it is the wrong one:
 * `LocalOverlayHost` **throws** when there is no host, so a `TextField` that
 * reached for one would break every app and every test that draws a field on its
 * own — including two in this same directory.
 *
 * ### This asserts the install, not the gesture, and that is deliberate
 *
 * See [kontourTextToolbarInstalled]. The gesture cannot be driven here: a mouse
 * double-click selects a word — `TextRange(6, 13)` — and never calls `showMenu`,
 * because a pointer selection on a desktop goes to the platform context menu
 * instead; a touch long-press does not select at all, leaving a collapsed
 * `TextRange(13, 13)`. Both measured rather than assumed. What the install buys
 * is measured in a browser instead, at phone width, where the touch selection is
 * a real one and the two toolbars are a screenshot apart.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldToolbarTest {

    @Test
    fun aTextBoxUnderTheHostHasTheLibrarysToolbar() {
        var installed = false
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        installed = kontourTextToolbarInstalled()
                        TextField(state = rememberTextFieldState("Perth Station"))
                    }
                }
            }
            waitForIdle()
        }

        assertTrue(
            installed,
            "a text box inside an `OverlayHost` — the ordinary way every app is " +
                "built — is still using the platform's fallback text toolbar. " +
                "Nothing installed the library's, which is what left every field " +
                "outside the one catalog demo with no toolbar a user can see.",
        )
    }

    @Test
    fun aFieldWithNoHostIsLeftAlone() {
        // The control, and the reason the install is not on the field: reaching
        // for `LocalOverlayHost` where there is no host throws. A bare field has
        // to keep working, and this is what says the assertion above can fail.
        var installed = true
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    installed = kontourTextToolbarInstalled()
                    TextField(state = rememberTextFieldState("Perth Station"))
                }
            }
            waitForIdle()
        }

        assertFalse(
            installed,
            "a field with no `OverlayHost` above it reported the library's " +
                "toolbar installed — so the test above would pass whatever the " +
                "host did, and proves nothing",
        )
    }
}
