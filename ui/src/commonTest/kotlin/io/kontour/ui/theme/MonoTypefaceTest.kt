package io.kontour.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import io.kontour.ui.components.display.KbdDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bundled mono is really monospaced, and really reaches the keycaps.
 *
 * Two claims worth a number rather than a comment.
 *
 * **Figures line up.** That is the whole reason [Typography.mono] exists — a
 * readout redrawn in place must not shuffle sideways as its digits change. It
 * holds two ways: by construction in a monospaced face, and by `tnum` in a
 * proportional one, which is why the style is worth having even under a theme
 * that never bundled a mono. Outfit ships the feature; whether Skia honours it
 * is a fact about this stack, not a fact about the font file, so it is measured.
 *
 * **`Kbd`'s modifier symbols come from the bundle.** ⌘ ⌥ ⇧ ⌃ were the four the
 * component's own KDoc measured at a 1.5dp spread because they fell through to
 * whatever the platform had. In a monospaced face every glyph is one advance
 * wide, so a symbol drawn from the bundle measures exactly as wide as a letter
 * and one that fell back almost certainly does not. That makes the fallback
 * detectable from inside a single JVM, which is the only place these tests run.
 */
@OptIn(ExperimentalTestApi::class)
class MonoTypefaceTest {

    /** Widths of [text] in [style], measured through the real font resolver. */
    private fun widths(vararg samples: String, style: (Typography) -> TextStyle): List<Float> {
        val out = mutableStateOf<List<Float>>(emptyList())
        runComposeUiTest {
            setContent {
                KontourTheme {
                    val measurer = rememberTextMeasurer()
                    val s = style(Theme.typography)
                    out.value = samples.map { measurer.measure(it, s).size.width.toFloat() }
                }
            }
            waitForIdle()
        }
        return out.value
    }

    @Test
    fun figuresAreTheSameWidthWhateverTheyAre() {
        val measured = widths(Zeroes, Ones, Mixed) { it.mono }
        assertTrue(measured.isNotEmpty(), "nothing was measured, so this proves nothing")
        assertEquals(
            1, measured.distinct().size,
            "ten digits do not share an advance in Theme.typography.mono: " +
                "\"$Zeroes\" $Ones\" and \"$Mixed\" measured $measured. A number " +
                "redrawn in place will shuffle sideways as its digits change, " +
                "which is the one thing this style is for.",
        )
    }

    /**
     * And the default scale does *not* — the control that gives the above meaning.
     *
     * `bodyMedium` is Outfit without `tnum`, so its figures are proportional. If
     * this ever starts passing, either the default family changed or the measure
     * above has stopped measuring anything.
     */
    @Test
    fun theProseStyleIsProportionalAndThatIsWhyMonoExists() {
        val measured = widths(Zeroes, Ones) { it.bodyMedium }
        assertTrue(
            measured.distinct().size > 1,
            "\"$Zeroes\" and \"$Ones\" measured the same in bodyMedium ($measured), " +
                "so either Outfit's figures became tabular or this test measures nothing",
        )
    }

    @Test
    fun theModifierSymbolsComeFromTheBundle() {
        val letter = widths("M") { it.mono }.single()
        val symbols = listOf(
            "Command" to KbdDefaults.Command,
            "Option" to KbdDefaults.Option,
            "Shift" to KbdDefaults.Shift,
            "Control" to KbdDefaults.Control,
        )
        val measured = widths(*symbols.map { it.second }.toTypedArray()) { it.mono }
        val wrong = symbols.map { it.first }.zip(measured).filter { it.second != letter }

        assertTrue(
            wrong.isEmpty(),
            "${wrong.size} of ${symbols.size} modifier symbols are not one " +
                "monospaced advance wide, so they fell through to a platform " +
                "font: " + wrong.joinToString(", ") { "${it.first} ${it.second}px" } +
                " against ${letter}px for a letter. Check the subset in " +
                "jetBrainsMonoFontFamily covers their codepoints.",
        )
    }

    private companion object {
        const val Zeroes = "0000000000"
        const val Ones = "1111111111"
        const val Mixed = "1234567890"
    }
}
