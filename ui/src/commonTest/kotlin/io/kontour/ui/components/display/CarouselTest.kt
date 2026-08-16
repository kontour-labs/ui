package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.launch
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
        onPageSelect: ((Int) -> Unit)? = null,
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
                    onPageSelect = onPageSelect?.let { select ->
                        { page -> scope.launch { carousel.scrollToPage(page) }; select(page) }
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
     * `onPageSelect` turns the dots into targets; without it they are silent.
     *
     * This is the pointer route. A carousel whose indicator is decoration and
     * whose only other affordance is a drag is operable by exactly one input
     * method, and the app has four.
     */
    @Test
    fun dotsAreTargetsOnlyWhenTheyCanChangeThePage() = runComposeUiTest {
        setContent { Harness(onPageSelect = null, pages = 4) }
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
        setContent { Harness(onPageSelect = { selected += it }, pages = 4) }
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
        setContent { Harness(onPageSelect = {}, pages = 4) }
        waitForIdle()

        for (page in 1..4) {
            onNodeWithContentDescription("Page $page of 4").assertExists()
        }
    }

    /** Every dot is its own target, so there are as many as there are pages. */
    @Test
    fun thereIsOneDotPerPage() = runComposeUiTest {
        setContent { Harness(onPageSelect = {}, pages = 4) }
        waitForIdle()

        onAllNodes(hasClickAction()).assertCountEquals(4)
    }
}
