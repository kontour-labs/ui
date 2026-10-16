package io.kontour.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Changing a theme setting changes how the app looks, and leaves what it holds
 * where it was.
 *
 * ### An in-app language written the other way
 *
 * `KontourTheme` lays a locale out in its own direction when that differs from
 * the device's, and it did so by calling the app's content from one of two
 * places: inside a direction provider, or not. Two call sites are two places in
 * the composition, so moving an in-app language setting between English and
 * Arabic disposed the whole application and built it again from nothing —
 * every scroll position, every field half filled in, every open sheet, gone for
 * a setting that should only have turned the layout round.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeKeepsItsContentTest {

    @Test
    fun aLanguageWrittenTheOtherWayKeepsTheAppsState() = runComposeUiTest {
        var locale by mutableStateOf(Locale("en-AU"))
        var held: Any? = null
        var direction: LayoutDirection? = null
        setContent {
            KontourTheme(locale = locale) {
                held = remember { Any() }
                direction = LocalLayoutDirection.current
            }
        }
        waitForIdle()
        val before = held
        assertEquals(LayoutDirection.Ltr, direction, "English did not lay out left to right")

        locale = Locale("ar")
        waitForIdle()
        assertEquals(LayoutDirection.Rtl, direction, "Arabic did not turn the layout round, so this measured nothing")
        assertSame(
            before,
            held,
            "switching the app's language to one written right to left threw the " +
                "app's remembered state away. The content was being called from a " +
                "different place in the theme for each direction.",
        )

        locale = Locale("en-AU")
        waitForIdle()
        assertSame(before, held, "and switching it back threw it away again")
    }
}
