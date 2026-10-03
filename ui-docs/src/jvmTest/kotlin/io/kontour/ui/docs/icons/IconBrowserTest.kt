package io.kontour.ui.docs.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.docs.lastCopied
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The icon browser lists every icon alphabetically, a page at a time, fetches
 * the paths of only the icons it shows, finds a house when asked for a home,
 * and says what to write to use the one pressed.
 *
 * Asked for as *"an icon searcher/picker, similar to how fontawesome or google
 * fonts (icons) do it … it shouldn't load in every icon at once, that would be
 * dumb. it should be infinite scroll though."*
 *
 * Over a made-up set: a thousand `glyph-NNNN`, every tenth with a filled form,
 * and `home`, tagged *house* as Tabler tags it. At 128 icons to a chunk that is
 * eight chunks of outlines; the source counts which are asked for.
 */
@OptIn(ExperimentalTestApi::class)
class IconBrowserTest {

    private class Fake : IconSource {
        val asked = mutableListOf<String>()
        private val files = HashMap<String, String>()

        init {
            val names = ((0 until 1000).map { "glyph-" + it.toString().padStart(4, '0') } + "home").sorted()
            fun filled(name: String) = name == "home" || name.removePrefix("glyph-").toIntOrNull()?.rem(10) == 0
            val square = "01;;0;M4 4h16v16h-16z"
            val filledNames = names.filter(::filled)
            files["manifest.txt"] = "data\n"
            files["data/index.txt"] = (
                listOf("tabler\t3.48.0\t${names.size}\t${filledNames.size}\t128") +
                    names.map { name ->
                        val tags = if (name == "home") "house,dashboard,building" else "shape"
                        "$name\tTest\t$tags\t${if (filled(name)) 1 else 0}"
                    }
                ).joinToString("\n")
            for ((style, order) in listOf("outline" to names, "filled" to filledNames)) {
                order.chunked(128).forEachIndexed { n, chunk ->
                    files["data/$style/${n.toString().padStart(3, '0')}.txt"] =
                        chunk.joinToString("") { "$it\t$square\n" }
                }
            }
        }

        override suspend fun fetch(path: String): String? {
            asked += path
            return files[path]
        }

        fun chunks(style: String) = asked.filter { it.startsWith("data/$style/") }.toSet()
    }

    private fun ComposeUiTest.browser(width: Int = 1000): Fake {
        val fake = Fake()
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.requiredSize(width.dp, 800.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        IconsPage(source = fake)
                    }
                }
            }
        }
        waitForIdle()
        return fake
    }

    /**
     * Types [text] into the search field and waits for the results to say they
     * are for it. The field's debounce is a quarter of a second of wall-clock
     * time, which the test clock does not advance, so this waits in real time
     * for the count that only a query shows.
     */
    private fun ComposeUiTest.search(text: String) {
        onNode(hasSetTextAction()).performTextInput(text)
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(Counted).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.shows(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun itOpensAlphabeticallyWithOnlyTheFirstChunk() = runComposeUiTest {
        val fake = browser()
        assertTrue(shows("glyph-0000"), "the list does not open on the first icon")
        assertTrue(!shows("glyph-0120"), "the list holds more than its first page")
        assertEquals(setOf("data/outline/000.txt"), fake.chunks("outline"), "the first screen fetched more than its chunk")
        assertTrue(fake.chunks("filled").isEmpty())
    }

    @Test
    fun scrollingToTheFootLoadsTheNextPage() = runComposeUiTest {
        val fake = browser()
        // Nine to a row at 1000dp, so the first page is 14 rows and the foot is
        // the fifteenth item.
        onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        waitForIdle()
        onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        waitForIdle()
        assertTrue(shows("glyph-0130"), "the next page did not arrive at the foot of the first")
        val fetched = fake.chunks("outline")
        assertTrue("data/outline/001.txt" in fetched, "the second page's icons were not fetched: $fetched")
        assertTrue(fetched.size <= 3, "scrolling one page fetched $fetched")
    }

    @Test
    fun aHouseIsAHome() = runComposeUiTest {
        browser()
        search("house")
        assertTrue(shows("home"), "searching for a house did not find home")
        assertTrue(!shows("glyph-0000"), "searching for a house still lists everything")
    }

    @Test
    fun pressingAnIconSaysHowToUseIt() = runComposeUiTest {
        browser()
        search("home")
        // The cell, not the search field, which says "home" too.
        onAllNodes(hasText("home") and hasClickAction() and !hasSetTextAction()).onFirst().performClick()
        waitForIdle()
        onNodeWithText("Tabler.Outline.Home").assertExists()
        onNodeWithText("import io.kontour.icons.tabler.outline.Home").assertExists()
        onNodeWithText("Tabler.Filled.Home").assertExists()
        onNodeWithContentDescription("Copy outline property").performClick()
        assertEquals("Tabler.Outline.Home", lastCopied)
    }

    @Test
    fun filledListsOnlyTheIconsThatHaveOne() = runComposeUiTest {
        val fake = browser()
        onNodeWithText("Filled").performClick()
        waitForIdle()
        assertTrue(shows("glyph-0000") && shows("glyph-0010"), "filled icons are missing")
        assertTrue(!shows("glyph-0001"), "an icon with no filled form is listed under Filled")
        assertEquals(setOf("data/filled/000.txt"), fake.chunks("filled"))
    }

    @Test
    fun onAPhoneAPressedIconOpensInASheet() = runComposeUiTest {
        browser(width = 390)
        onAllNodesWithText("glyph-0003").onFirst().performClick()
        waitForIdle()
        onNodeWithText("Tabler.Outline.Glyph0003").assertExists()
    }

    private companion object {
        /** The count a query shows: "1 icon", "34 icons". */
        val Counted = SemanticsMatcher("shows a count of icons") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("[\\d,.\u00a0\u202f]+ icons?").matches(it.text) }
        }
    }
}
