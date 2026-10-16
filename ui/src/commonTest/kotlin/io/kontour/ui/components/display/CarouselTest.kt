package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The routes a swipe is not.
 *
 * The gesture itself is the one thing a JVM host cannot judge — the feel of the
 * snap, whether it fights the finger. Everything the project's own rule requires
 * *around* it can be checked here: a carousel reachable only by drag is
 * unreachable for anyone who cannot make one, and this is where that stops
 * being an intention.
 */
@OptIn(ExperimentalTestApi::class)
class CarouselTest {

    @Composable
    private fun Harness(
        onPageClick: ((Int) -> Unit)? = null,
        pages: Int = 4,
    ) {
        KontourTheme {
            val carousel = rememberCarouselState { pages }
            val scope = rememberCoroutineScope()
            Column {
                Carousel(
                    state = carousel,
                    contentDescription = "Stop photos",
                    modifier = Modifier.testTag("carousel").fillMaxWidth().height(120.dp),
                ) { page ->
                    Text("Page $page")
                }
                PageIndicator(
                    state = carousel,
                    modifier = Modifier.testTag("dots"),
                    onPageClick = onPageClick?.let { select ->
                        { page -> scope.launch { carousel.animateScrollToPage(page) }; select(page) }
                    },
                )
            }
        }
    }

    /**
     * The carousel carries previous and next as accessibility actions.
     *
     * Without them the only way to change page is a sustained horizontal drag,
     * which a screen-reader user cannot perform and a keyboard user has no way
     * to express. `SwipeActions` and `ReorderableItem` both already do this;
     * a carousel that does not is the same defect with a different gesture.
     */
    @Test
    fun theCarouselCarriesPreviousAndNextAsActions() = runComposeUiTest {
        setContent { Harness() }
        waitForIdle()

        onNodeWithTag("carousel").assert(
            SemanticsMatcher("has previous and next custom actions") { node ->
                val actions = node.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
                actions.map { it.label }.containsAll(listOf("Previous", "Next"))
            },
        )
    }

    /** And it says which page of how many. */
    @Test
    fun theCarouselAnnouncesWhichPage() = runComposeUiTest {
        setContent { Harness(pages = 4) }
        waitForIdle()

        onNodeWithTag("carousel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 of 4"),
        )
    }

    /**
     * `onPageClick` turns the dots into targets; without it they are silent.
     *
     * This is the pointer route. A carousel whose indicator is decoration and
     * whose only other affordance is a drag is operable by exactly one input
     * method, and the app has four.
     */
    @Test
    fun dotsAreTargetsOnlyWhenTheyCanChangeThePage() = runComposeUiTest {
        setContent { Harness(onPageClick = null, pages = 4) }
        waitForIdle()

        onNodeWithTag("dots").assert(
            SemanticsMatcher("has no pressable dots") { node ->
                node.children.none { it.config.contains(SemanticsActions.OnClick) }
            },
        )
    }

    @Test
    fun dotsBecomePressableWhenGivenAHandler() = runComposeUiTest {
        val selected = mutableListOf<Int>()
        setContent { Harness(onPageClick = { selected += it }, pages = 4) }
        waitForIdle()

        onNodeWithContentDescription("Page 3 of 4").performClick()
        waitForIdle()

        assertEquals(listOf(2), selected)
    }

    /**
     * A dot names the page it goes to, one-based.
     *
     * "Page 3 of 4" rather than "dot", and rather than "Page 2 of 4" for the
     * third one. The index is zero-based and the label is not, which is the same
     * off-by-one `Rating` has and the same reason it is worth pinning.
     */
    @Test
    fun everyDotNamesItsPage() = runComposeUiTest {
        setContent { Harness(onPageClick = {}, pages = 4) }
        waitForIdle()

        for (page in 1..4) {
            onNodeWithContentDescription("Page $page of 4").assertExists()
        }
    }

    /**
     * One clickable node per page, and no more.
     *
     * The *target* is one band over the whole strip now rather than one box per
     * dot — `PageIndicatorStripTest` is where that is measured — but the semantics
     * are still per page, and this is what says the two did not get confused with
     * each other. A strip that took the gesture and forgot to leave a node behind
     * would be reachable by finger and invisible to a reader.
     */
    @Test
    fun thereIsOneDotPerPage() = runComposeUiTest {
        setContent { Harness(onPageClick = {}, pages = 4) }
        waitForIdle()

        onAllNodes(hasClickAction()).assertCountEquals(4)
    }

    /**
     * A carousel over a list it was handed, the way the KDoc writes one.
     *
     * `{ photos.size }` captures a parameter, so it is a new lambda each time
     * the list changes — which is exactly what a state remembered against the
     * first lambda never sees.
     */
    @Composable
    private fun ListHarness(photos: List<String>, onState: (CarouselState) -> Unit = {}) {
        KontourTheme {
            val carousel = rememberCarouselState { photos.size }
            onState(carousel)
            Carousel(
                state = carousel,
                contentDescription = "Stop photos",
                modifier = Modifier.testTag("carousel").fillMaxWidth().height(120.dp),
            ) { page ->
                Text("Photo ${photos[page]}")
            }
        }
    }

    /**
     * A list that grows is counted again.
     *
     * The state used to keep the first lambda it was given, so a carousel over
     * a parameter stayed at the count it started with: two photos, then four,
     * and it still said "1 of 2" and never offered the other two.
     */
    @Test
    fun aCountCapturedFromAParameterFollowsIt() = runComposeUiTest {
        var photos by mutableStateOf(listOf("a", "b"))
        setContent { ListHarness(photos) }
        waitForIdle()
        onNodeWithTag("carousel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 of 2"),
        )

        photos = listOf("a", "b", "c", "d")
        waitForIdle()

        onNodeWithTag("carousel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 of 4"),
        )
    }

    /**
     * And one that shrinks under the page on show does not compose the pages it lost.
     *
     * With the first lambda kept, the carousel went on composing page 3 of a
     * list of two, and `photos[page]` threw.
     */
    @Test
    fun aListThatShrinksIsNotAskedForPagesItNoLongerHas() = runComposeUiTest {
        var photos by mutableStateOf(listOf("a", "b", "c", "d"))
        lateinit var carousel: CarouselState
        setContent { ListHarness(photos) { carousel = it } }
        waitForIdle()
        runOnIdle { runBlocking { carousel.scrollToPage(3) } }
        waitForIdle()
        onNodeWithTag("carousel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "4 of 4"),
        )

        photos = listOf("a", "b")
        waitForIdle()

        onNodeWithTag("carousel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 2"),
        )
    }
}
