package io.kontour.ui.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.adaptive.PaneScaffoldDefaults

/**
 * A list beside its detail when the window has room, and Navigation 3's own
 * single pane when it does not.
 *
 * ```kotlin
 * val backStack = remember { mutableStateListOf<Any>(Stops) }
 *
 * NavDisplay(
 *     backStack = backStack,
 *     onBack = { backStack.removeLastOrNull() },
 *     sceneStrategies = listOf(rememberListDetailSceneStrategy()),
 *     entryProvider = entryProvider {
 *         entry<Stops>(metadata = listPane()) { StopList(onOpen = { backStack += it }) }
 *         entry<Stop>(metadata = detailPane()) { StopDetail(it) }
 *     },
 * )
 * ```
 *
 * The layout is [ListDetailPaneScaffold]'s, unchanged; this is the part that
 * reads a back stack. Mark the list's entry with [listPane] and each detail's
 * with [detailPane], and a wide window shows the top detail beside the nearest
 * list below it. A narrow one gets `null` from here, and the next strategy —
 * by default Navigation 3's single pane — shows the top entry alone, which is the
 * whole of "one pane on a phone" and needs no code.
 *
 * **Picking another detail is not a new scene.** The scene is keyed on the
 * *list's* entry, so moving from one stop to the next changes what is inside one
 * scene and `NavDisplay` animates nothing but the detail's own content. Keyed on
 * the detail, every selection would slide the whole two-pane layout out and back.
 *
 * **Back is the back stack's.** A detail pushed over a list pops to the list —
 * alone on two panes, with the placeholder beside it — exactly as it pops on one.
 *
 * @param twoPane Whether there is room for both. Read here, at composition,
 *   because `calculateScene` is not a composable and cannot read the window
 *   itself. Width alone, as the scaffold's own default is.
 * @param listWeight How much of the width the list takes.
 * @param resizable Put a handle between the panes that can be dragged.
 * @param showDivider A hairline between the panes when they are not resizable.
 */
@Composable
fun <T : Any> rememberListDetailSceneStrategy(
    twoPane: Boolean = LocalWindowSizeClass.current.width.hasRoomForTwoPanes,
    listWeight: Float = PaneScaffoldDefaults.ListWeight,
    resizable: Boolean = false,
    showDivider: Boolean = true,
): SceneStrategy<T> = remember(twoPane, listWeight, resizable, showDivider) {
    ListDetailSceneStrategy(twoPane, listWeight, resizable, showDivider)
}

/**
 * Marks an entry as the list of a list-detail pair.
 *
 * @param detailPlaceholder What stands in the detail pane when the list is on
 *   top of the stack and there is room for two — "Pick a stop" rather than an
 *   empty half of the window. Nothing, when null.
 */
fun listPane(detailPlaceholder: (@Composable () -> Unit)? = null): Map<String, Any> =
    mapOf(ListPaneKey to ListPaneRole(detailPlaceholder))

/** Marks an entry as a detail, shown beside the nearest [listPane] below it. */
fun detailPane(): Map<String, Any> = mapOf(DetailPaneKey to DetailPaneRole)

/** Namespaced, so they cannot collide with another library's metadata. */
internal const val ListPaneKey = "io.kontour.ui.nav3.listPane"
internal const val DetailPaneKey = "io.kontour.ui.nav3.detailPane"

/**
 * The list's role, holding its placeholder.
 *
 * A class rather than the lambda itself, because a metadata map cannot hold a
 * null and "no placeholder" still has to say "this is a list".
 */
internal class ListPaneRole(val placeholder: (@Composable () -> Unit)?)

internal object DetailPaneRole

internal class ListDetailSceneStrategy<T : Any>(
    private val twoPane: Boolean,
    private val listWeight: Float,
    private val resizable: Boolean,
    private val showDivider: Boolean,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!twoPane) return null
        val top = entries.lastOrNull() ?: return null
        val layout = PaneLayout(listWeight, resizable, showDivider)

        (top.metadata[ListPaneKey] as? ListPaneRole)?.let { role ->
            return ListDetailScene(
                list = top,
                detail = null,
                placeholder = role.placeholder,
                previousEntries = entries.dropLast(1),
                layout = layout,
                onBack = onBack,
            )
        }

        if (!top.isDetail) return null
        val listAt = entries.indexOfLast { it.metadata[ListPaneKey] is ListPaneRole }
        if (listAt < 0) return null
        // Only through details. A settings screen pushed between the list and a
        // detail is a screen of its own, and pairing across it would hide it
        // without its having been popped.
        if (!entries.subList(listAt + 1, entries.size).all { it.isDetail }) return null
        val list = entries[listAt]
        return ListDetailScene(
            list = list,
            detail = top,
            placeholder = (list.metadata[ListPaneKey] as ListPaneRole).placeholder,
            previousEntries = entries.dropLast(1),
            layout = layout,
            onBack = onBack,
        )
    }
}

private val NavEntry<*>.isDetail: Boolean get() = metadata[DetailPaneKey] == DetailPaneRole

/** The three values a two-pane scene is laid out with, compared as values. */
internal data class PaneLayout(
    val weight: Float,
    val resizable: Boolean,
    val showDivider: Boolean,
)

/**
 * The list and, if there is one, its detail.
 *
 * `equals` covers what `NavDisplay` needs to decide whether the scene changed —
 * the key, the entries and the layout — and **not** [onBack] or the
 * placeholder, which are fresh lambdas every time the strategy runs. A scene
 * that compared them would be a new scene on every recomposition.
 */
internal class ListDetailScene<T : Any>(
    private val list: NavEntry<T>,
    private val detail: NavEntry<T>?,
    private val placeholder: (@Composable () -> Unit)?,
    override val previousEntries: List<NavEntry<T>>,
    private val layout: PaneLayout,
    private val onBack: () -> Unit,
) : Scene<T> {

    override val key: Any = list.contentKey

    override val entries: List<NavEntry<T>> = listOfNotNull(list, detail)

    override val content: @Composable () -> Unit = {
        ListDetailPaneScaffold(
            // Irrelevant on two panes, which is the only way this scene exists;
            // stated so the reader does not have to know that.
            focus = if (detail == null) PaneFocus.List else PaneFocus.Detail,
            onBack = onBack,
            list = { list.Content() },
            detail = { detail?.Content() ?: placeholder?.invoke() },
            twoPane = true,
            listWeight = layout.weight,
            resizable = layout.resizable,
            showDivider = layout.showDivider,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ListDetailScene<*> &&
            key == other.key &&
            entries == other.entries &&
            previousEntries == other.previousEntries &&
            layout == other.layout

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entries.hashCode()
        result = 31 * result + previousEntries.hashCode()
        result = 31 * result + layout.hashCode()
        return result
    }

    override fun toString(): String = "ListDetailScene(key=$key, entries=$entries)"
}
