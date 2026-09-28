package io.kontour.ui.locale

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.intl.Locale
import java.util.concurrent.atomic.AtomicInteger
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A change of locale rewrites what reads the formats, once, and nothing else
 * about the app changing makes it do so.
 */
@OptIn(ExperimentalTestApi::class)
class LocaleFormatsRecompositionTest {

    @Test
    fun aLocaleChangeReachesItsReadersOnceAndNothingElseDoes() = runComposeUiTest {
        var locale by mutableStateOf(Locale("en-AU"))
        var unrelated by mutableIntStateOf(0)
        // A `val`: a lambda that captures a `var` is never remembered, so the
        // reader would be handed a new one each time and could not skip.
        val compositions = AtomicInteger()
        setContent {
            KontourTheme(locale = locale, formats = LocaleFormats.of(locale)) {
                Column {
                    Text("tick $unrelated")
                    Reader { compositions.incrementAndGet() }
                }
            }
        }
        waitForIdle()
        onNodeWithText("1,234").assertExists()
        assertEquals(1, compositions.get())

        unrelated++
        waitForIdle()
        assertEquals(1, compositions.get(), "state the reader does not read recomposed it")

        locale = Locale("de-DE")
        waitForIdle()
        onNodeWithText("1.234").assertExists()
        assertEquals(2, compositions.get(), "a change of locale should rewrite the number once")
    }
}

/** Top level, so it can skip: a member function takes the test class as a parameter. */
@Composable
private fun Reader(onComposed: () -> Unit) {
    onComposed()
    Text(Theme.formats.integer(1234))
}
