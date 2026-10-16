package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.foundation.Text
import io.kontour.ui.locale.PseudoLocale
import io.kontour.ui.locale.PseudoLocalised
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * No component says a word of the library's English that a translation could
 * not reach.
 *
 * Every registry specimen, under the accented pseudo-locale, and every text,
 * description and state it exposes to a screen reader read back. A value that is
 * exactly one of `Strings`' English defaults came from a literal in a
 * component rather than through `Theme.strings` — the pseudo-locale would have
 * turned it into `[Ďîšɱîšš ···]`. Words a specimen passes in itself are
 * the caller's, not the library's, and are left out by reading the registry's
 * own source.
 */
@OptIn(ExperimentalTestApi::class)
class PseudoLocaleSweepTest {

    @Test
    fun noLibraryWordEscapesTheStrings() {
        val english = Strings()
        val defaults = Strings::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { field -> field.isAccessible = true; field.get(english) as String }
            .toSet()
        val registrySource = File("src/commonMain/kotlin/io/kontour/ui/contract/ComponentRegistry.kt").readText()
        val callerWords = defaults.filter { "\"$it\"" in registrySource }.toSet()
        val watched = defaults - callerWords

        val leaks = mutableListOf<String>()
        for (spec in componentRegistry) {
            said { spec.content(Modifier, true) {} }.filter { it in watched }.forEach { leaks += "${spec.name}: \"$it\"" }
        }
        assertTrue(
            leaks.isEmpty(),
            "${leaks.size} library word(s) reached the screen without going through Theme.strings:\n" +
                leaks.joinToString("\n") { "  · $it" },
        )
    }

    /**
     * Under marks stacked above and below every letter, at one and a half times
     * the type size, which specimens cut their text off at the top or bottom —
     * a fixed height, a line height too tight, a trimmed leading. None may.
     */
    @Test
    fun tallTextIsNotCutOff() {
        val cut = componentRegistry.filter { spec -> cutsTallText { spec.content(Modifier, true) {} } }.map { it.name }
        assertTrue(
            cut.isEmpty(),
            "${cut.size} specimen(s) cut tall text off: $cut — give the text a minimum height rather than a " +
                "height, or leading the script can use",
        )
    }

    @Test
    fun aFixedHeightIsCaught() {
        // The control: a label in a box its own height at 100%, as the buttons
        // used to be, is cut once its letters carry marks.
        assertTrue(cutsTallText { Box(Modifier.height(20.dp)) { Text("Departures") } }, "the sweep cannot see a cut")
        assertTrue(!cutsTallText { Box(Modifier.heightIn(min = 20.dp)) { Text("Departures") } }, "a minimum height is not a cut")
    }

    /**
     * Whether [content], under the tall pseudo-locale at one and a half times the
     * type size, cuts any of its text at the top or bottom.
     */
    private fun cutsTallText(content: @Composable () -> Unit): Boolean {
        var overflowed = false
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                    KontourTheme(reduceMotion = true) {
                        PseudoLocalised(PseudoLocale.Tall) {
                            OverlayHost {
                                Box(Modifier.width(420.dp)) { content() }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            overflowed = onAllNodes(hasTextLayout, useUnmergedTree = true).fetchSemanticsNodes().any { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                // Cut at the top or bottom, not truncated to its lines: a
                // single-line label ending in an ellipsis is a choice.
                layouts.any { it.didOverflowHeight && !it.multiParagraph.didExceedMaxLines }
            }
        }
        return overflowed
    }

    @Test
    fun aWordWrittenInsideAComponentIsCaught() {
        // The control: a library word written straight into a `Text`, as the
        // components used to, is still English under the pseudo-locale.
        val english = Strings()
        assertTrue(english.dismiss in said { Text(english.dismiss) }, "the sweep cannot see a literal word")
        assertTrue(english.dismiss !in said { Text(Theme.strings.dismiss) }, "the sweep flags a word that went through Strings")
    }

    /**
     * No component joins words, writes digits or separates with a slash on its
     * own, where a language could not change it.
     *
     * Under the right-to-left pseudo-locale, every word that went through
     * `Theme.strings` and every number, date and list that went through
     * `Theme.formats` arrives wrapped in a right-to-left run. Whatever is left
     * once the runs and the specimen's own words are taken out was put there by
     * a component: a ", " between two strings, a ". " between sentences, a
     * " / " between a position and a count, or a number written with
     * `toString()`. Each is English punctuation or Western digits a translation
     * cannot reach, and each is read in the wrong order in a right-to-left
     * language.
     */
    @Test
    fun nothingIsJoinedOrNumberedOutsideTheStrings() {
        val registrySource = File("src/commonMain/kotlin/io/kontour/ui/contract/ComponentRegistry.kt").readText()
        val callerWords = callerPieces(registrySource)
        val leaks = mutableListOf<String>()
        for (spec in componentRegistry) {
            said(PseudoLocale.Bidi) { spec.content(Modifier, true) {} }.forEach { text ->
                strayIn(text, callerWords)?.let { leaks += "${spec.name}: $it in \"${text.filter { c -> c != Rlo && c != Pdf }}\"" }
            }
        }
        assertTrue(
            leaks.isEmpty(),
            "${leaks.size} piece(s) of English punctuation or Western digits were written by a component:\n" +
                leaks.joinToString("\n") { "  · $it" },
        )
    }

    @Test
    fun aCommaWrittenBetweenTwoStringsIsCaught() {
        // The control: two strings joined with a comma in the component are
        // caught, and the same two joined by the locale's list are not.
        val joined = said(PseudoLocale.Bidi) { Text(Theme.strings.dismiss + ", " + Theme.strings.close) }
        assertTrue(joined.any { strayIn(it, emptySet()) != null }, "the sweep cannot see a written comma: $joined")
        val listed = said(PseudoLocale.Bidi) { Text(Theme.formats.list(listOf(Theme.strings.dismiss, Theme.strings.close))) }
        assertTrue(listed.none { strayIn(it, emptySet()) != null }, "the sweep flags the locale's own list: $listed")
        val counted = said(PseudoLocale.Bidi) { Text(3.toString()) }
        assertTrue(counted.any { strayIn(it, emptySet()) != null }, "the sweep cannot see a number written by toString")
    }

    /**
     * What of [text] a component wrote: the text with every right-to-left run
     * and every piece of the specimen's own words taken out, if what is left
     * has a joiner, a slash or a Western digit in it.
     */
    private fun strayIn(text: String, callerWords: Set<String>): String? {
        var left = buildString {
            var depth = 0
            for (char in text) {
                when (char) {
                    Rlo -> depth++
                    Pdf -> depth = maxOf(0, depth - 1)
                    else -> if (depth == 0) append(char)
                }
            }
        }
        callerWords.sortedByDescending { it.length }.forEach { left = left.replace(it, " ") }
        return Stray.find(left)?.value?.let { "\"$it\"" }
    }

    /**
     * The words a specimen passes in itself: every string literal in the
     * registry, split at its templates, so `"Route ${it.route}"` leaves
     * `"Route "`. Only pieces with a letter or a digit in them, so a literal
     * `", "` in the registry hides nothing.
     */
    private fun callerPieces(source: String): Set<String> =
        Regex("""("(?:[^"\\]|\\.)*")""").findAll(source)
            .map { it.value.removeSurrounding("\"") }
            .flatMap { it.split(Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")) }
            .map { it.trim() }
            .filter { piece -> piece.any { it.isLetterOrDigit() } }
            .toSet()

    /** Everything [content] says to a screen reader, under the accented pseudo-locale. */
    private fun said(content: @Composable () -> Unit): Set<String> = said(PseudoLocale.Accented, content)

    /** Everything [content] says to a screen reader, under [mode]. */
    private fun said(mode: PseudoLocale, content: @Composable () -> Unit): Set<String> {
        val said = mutableSetOf<String>()
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    PseudoLocalised(mode) {
                        OverlayHost {
                            Box(Modifier.width(420.dp)) { content() }
                        }
                    }
                }
            }
            waitForIdle()
            collect(onRoot(useUnmergedTree = true).fetchSemanticsNode(), said)
        }
        return said
    }

    private fun collect(node: SemanticsNode, into: MutableSet<String>) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { into += it.text }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { into += it }
        node.config.getOrNull(SemanticsProperties.StateDescription)?.let { into += it }
        node.children.forEach { collect(it, into) }
    }

    private companion object {
        val hasTextLayout = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)

        /** The start and end of a right-to-left run, as the bidi pseudo-locale wraps a word. */
        const val Rlo = '\u202E'
        const val Pdf = '\u202C'

        /** What a component may not write itself: a joiner, a slash between numbers, or a Western digit. */
        val Stray = Regex(""", |\. | / |[0-9]""")
    }
}
