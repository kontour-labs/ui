package io.kontour.ui.nav3

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import io.kontour.ui.adaptive.PaneScaffoldDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What each strategy makes of a back stack, without drawing anything.
 *
 * `calculateScene` is a plain function of the entries, which is what makes these
 * cheap: the questions — is it a scene at all, which one, is it the same one as
 * before — are answered before anything is composed.
 */
class SceneStrategyTest {

    private data class Stop(val name: String)
    private object Stops
    private object Run
    private object Conditions
    private object Settings

    private fun entry(key: Any, metadata: Map<String, Any> = emptyMap()) =
        NavEntry(key = key, metadata = metadata) {}

    // The scope's `onBack` constructor is internal to Navigation 3, so the
    // strategies are asked through the public one, and the callback tests build
    // their scenes directly with two different lambdas.
    private fun <T : Any> SceneStrategy<T>.sceneFor(entries: List<NavEntry<T>>): Scene<T>? =
        with(this) { SceneStrategyScope<T>().calculateScene(entries) }

    private val list = entry(Stops, listPane())
    private val perth = entry(Stop("Perth"), detailPane())
    private val mciver = entry(Stop("McIver"), detailPane())

    // --- list and detail ---------------------------------------------------

    @Test
    fun aNarrowWindowDeclinesTheScene() {
        val strategy = ListDetailSceneStrategy<Any>(twoPane = false, 0.4f, false, true)
        assertNull(
            strategy.sceneFor(listOf(list, perth)),
            "a narrow window should leave the stack to the single-pane strategy",
        )
    }

    @Test
    fun aDetailSitsBesideTheListBelowIt() {
        val strategy = ListDetailSceneStrategy<Any>(twoPane = true, 0.4f, false, true)
        val scene = assertNotNull(strategy.sceneFor(listOf(list, perth)))
        assertEquals(listOf(list, perth), scene.entries)
        assertEquals(listOf(list), scene.previousEntries, "back from a detail is the list")
    }

    @Test
    fun selectingADifferentDetailKeepsTheSameSceneKey() {
        val strategy = ListDetailSceneStrategy<Any>(twoPane = true, 0.4f, false, true)
        val first = assertNotNull(strategy.sceneFor(listOf(list, perth)))
        val second = assertNotNull(strategy.sceneFor(listOf(list, mciver)))
        val alone = assertNotNull(strategy.sceneFor(listOf(list)))
        assertEquals(first.key, second.key, "moving between details swapped the whole scene")
        assertEquals(first.key, alone.key, "closing the detail swapped the whole scene")
    }

    @Test
    fun aSceneIsEqualToTheOneBeforeItWhenOnlyTheCallbacksChanged() {
        val layout = PaneLayout(0.4f, resizable = false, showDivider = true)
        val before = ListDetailScene(list, perth, placeholder = null, listOf(list), layout, onBack = {})
        val after = ListDetailScene(
            list, perth, placeholder = { println("a new placeholder") }, listOf(list), layout,
            onBack = { println("a new lambda") },
        )
        assertEquals(before, after, "a fresh onBack made a new scene, so every recomposition would be one")
        assertEquals(before.hashCode(), after.hashCode())
    }

    @Test
    fun aScreenPushedBetweenTheListAndADetailIsNotHidden() {
        val strategy = ListDetailSceneStrategy<Any>(twoPane = true, 0.4f, false, true)
        assertNull(
            strategy.sceneFor(listOf(list, entry(Settings), perth)),
            "pairing across an unmarked screen would hide it without its being popped",
        )
    }

    @Test
    fun anUnmarkedTopIsLeftToTheNextStrategy() {
        val strategy = ListDetailSceneStrategy<Any>(twoPane = true, 0.4f, false, true)
        assertNull(strategy.sceneFor(listOf(list, entry(Settings))))
    }

    // --- main and supporting -----------------------------------------------

    private val main = entry(Run, mainPane())
    private val supporting = entry(Conditions, supportingPane())

    @Test
    fun aNarrowWindowPutsTheSupportingPaneOverTheMainOne() {
        val strategy = SupportingPaneSceneStrategy<Any>(twoPane = false, PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true)
        val scene = assertIs<OverlayScene<Any>>(strategy.sceneFor(listOf(main, supporting)))
        assertEquals(listOf(supporting), scene.entries)
        assertEquals(
            listOf(main),
            scene.overlaidEntries,
            "the page under the sheet is the stack below it, laid out as if the sheet were not there",
        )
    }

    @Test
    fun aNarrowWindowLeavesTheMainPaneAlone() {
        val strategy = SupportingPaneSceneStrategy<Any>(twoPane = false, PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true)
        assertNull(strategy.sceneFor(listOf(main)), "the main pane alone is an ordinary page")
    }

    @Test
    fun aWideWindowKeepsOneSceneAsTheSupportingPaneOpensAndCloses() {
        val strategy = SupportingPaneSceneStrategy<Any>(twoPane = true, PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true)
        val closed = assertNotNull(strategy.sceneFor(listOf(main)))
        val open = assertNotNull(strategy.sceneFor(listOf(main, supporting)))
        assertEquals(closed.key, open.key, "opening the supporting pane swapped the whole scene")
        assertEquals(listOf(main, supporting), open.entries)
    }

    @Test
    fun aSupportingSceneIsEqualWhenOnlyTheCallbacksChanged() {
        assertEquals(
            SupportingPaneScene(main, supporting, listOf(main), PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true, onBack = {}),
            SupportingPaneScene(main, supporting, listOf(main), PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true, onBack = { println("a new lambda") }),
        )
        val handle = SheetHandle()
        assertEquals(
            SupportingSheetScene(supporting, listOf(main), handle, onBack = {}, onGone = {}),
            SupportingSheetScene(supporting, listOf(main), handle, onBack = { println("a new lambda") }, onGone = {}),
        )
    }

    @Test
    fun theSameSheetIsReachedFromEveryRecalculation() {
        // `NavDisplay` may ask a different instance to leave than the one it drew,
        // so both have to reach the same sheet.
        val strategy = SupportingPaneSceneStrategy<Any>(twoPane = false, PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true)
        val first = assertIs<SupportingSheetScene<Any>>(strategy.sceneFor(listOf(main, supporting)))
        val again = assertIs<SupportingSheetScene<Any>>(strategy.sceneFor(listOf(main, supporting)))
        assertEquals(first.handleForTest, again.handleForTest)
    }
}
