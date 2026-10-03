package io.kontour.ui.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay

/**
 * One back stack for an app with a nav bar or a rail: its pages inside the
 * navigation, and the pages that cover it — a player, a photo, a checkout —
 * over all of it, bar included.
 *
 * ```kotlin
 * val backStack = remember { mutableStateListOf<Any>(Home) }
 * val suite = rememberNavigationSuiteSceneStrategy<Any> { pages ->
 *     NavigationSuiteScaffold(destinations = { appDestinations(backStack) }) { pages() }
 * }
 *
 * NavDisplay(
 *     backStack = backStack,
 *     onBack = { backStack.removeLastOrNull() },
 *     sceneStrategies = listOf(suite),
 *     sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
 *     entryProvider = entryProvider {
 *         entry<Home> { HomePage() }
 *         entry<Stop> { StopPage(it) }
 *         entry<Player>(metadata = coversNavigation()) { PlayerPage() }
 *     },
 * )
 * ```
 *
 * Asked for as *"an easy way to put screens above nav components"*.
 *
 * **Inside the navigation, the bar stays put.** The pages are shown by a
 * `NavDisplay` of their own inside [frame], so a push or a back moves the page
 * and not the bar, and pages move as [sceneDecoratorStrategies] says — the
 * platform's push, pop and back gesture by default. [sceneStrategies] lay them
 * out inside the frame: a list beside its detail, a supporting pane.
 *
 * **An entry marked [coversNavigation] covers it**, and so does everything
 * pushed after it, as on iOS: it is a page of the outer `NavDisplay`, so it
 * slides in over the bar and the page under it, and a back gesture from it
 * uncovers both. Popping it returns to the navigation as it was left.
 *
 * Back goes to the inner pages first, then — at the root of the navigation —
 * to the platform. Read the bar's height with
 * [navigationSuitePadding][io.kontour.ui.nav.navigationSuitePadding], since
 * the pages are not written inside the scaffold's own `content`; and show a
 * back button with [pageBack][io.kontour.ui.motion.pageBack].
 *
 * @param frame The navigation around the pages, usually a
 *   [NavigationSuiteScaffold][io.kontour.ui.nav.NavigationSuiteScaffold].
 *   Call `pages()` where the pages go.
 */
@Composable
fun <T : Any> rememberNavigationSuiteSceneStrategy(
    sceneStrategies: List<SceneStrategy<T>> = emptyList(),
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = listOf(rememberPageTransitionStrategy()),
    frame: @Composable (pages: @Composable () -> Unit) -> Unit,
): SceneStrategy<T> {
    val parts = SuiteParts(
        frame = rememberUpdatedState(frame),
        strategies = rememberUpdatedState(sceneStrategies),
        decorators = rememberUpdatedState(sceneDecoratorStrategies),
    )
    return remember { NavigationSuiteSceneStrategy(parts) }
}

/**
 * Marks an entry as a page over the navigation: it and every page after it
 * cover the bar or the rail. See [rememberNavigationSuiteSceneStrategy].
 */
fun coversNavigation(): Map<String, Any> = mapOf(CoversNavigationKey to true)

/** Namespaced, so it cannot collide with another library's metadata. */
internal const val CoversNavigationKey = "io.kontour.ui.nav3.coversNavigation"

/** Read live, so a scene recalculated without them still draws the latest. */
internal class SuiteParts<T : Any>(
    val frame: State<@Composable (pages: @Composable () -> Unit) -> Unit>,
    val strategies: State<List<SceneStrategy<T>>>,
    val decorators: State<List<SceneDecoratorStrategy<T>>>,
)

internal class NavigationSuiteSceneStrategy<T : Any>(private val parts: SuiteParts<T>) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.isEmpty()) return null
        // Covered from the first page that covers: the outer display's own
        // single pane shows the top page over everything.
        if (entries.any { it.metadata[CoversNavigationKey] == true }) return null
        return NavigationSuiteScene(entries, parts, onBack)
    }
}

/** The one key every navigation scene shares, so the outer display never moves between them. */
internal object NavigationSuiteSceneKey

/**
 * The navigation and the pages inside it.
 *
 * **No pages behind it**, as far as the outer display can see: going back
 * inside the navigation is the inner display's, whose handler is registered
 * after the outer one's and takes back while it has somewhere to go. At the
 * root of the navigation neither does, and back is the platform's. A covering
 * page pushed over it has the navigation behind it, and pops back to it.
 *
 * `equals` covers the entries, and not the frame or [onBack], which are new
 * lambdas each time the strategy runs.
 */
internal class NavigationSuiteScene<T : Any>(
    override val entries: List<NavEntry<T>>,
    private val parts: SuiteParts<T>,
    private val onBack: () -> Unit,
) : Scene<T> {

    override val key: Any = NavigationSuiteSceneKey

    override val previousEntries: List<NavEntry<T>> get() = emptyList()

    override val content: @Composable () -> Unit = {
        parts.frame.value {
            NavDisplay(
                entries = entries,
                sceneStrategies = parts.strategies.value,
                sceneDecoratorStrategies = parts.decorators.value,
                onBack = onBack,
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is NavigationSuiteScene<*> && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "NavigationSuiteScene(entries=$entries)"
}
