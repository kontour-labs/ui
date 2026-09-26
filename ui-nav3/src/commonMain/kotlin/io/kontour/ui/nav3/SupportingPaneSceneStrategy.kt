package io.kontour.ui.nav3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.PaneScaffoldDefaults
import io.kontour.ui.adaptive.SupportingPaneScaffold
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first

/**
 * A supporting pane beside its main content when the window has room, and a
 * sheet over it when it does not.
 *
 * ```kotlin
 * NavDisplay(
 *     backStack = backStack,
 *     onBack = { backStack.removeLastOrNull() },
 *     sceneStrategies = listOf(rememberSupportingPaneSceneStrategy()),
 *     entryProvider = entryProvider {
 *         entry<Run>(metadata = mainPane()) { RunSummary(it, onConditions = { backStack += Conditions }) }
 *         entry<Conditions>(metadata = supportingPane()) { ConditionsPanel() }
 *     },
 * )
 * ```
 *
 * The same answer [SupportingPaneScaffold] gives on its own, told to a back
 * stack. Mark the main entry with [mainPane] and the helper with
 * [supportingPane]; pushing the helper opens it and popping it closes it, in
 * whichever form the window allows.
 *
 * **Wide, it is one scene** keyed on the main entry, whether or not the
 * supporting pane is open — so opening it is a pane arriving beside the content
 * rather than `NavDisplay` sliding the whole layout out and a new one in.
 *
 * **Narrow, it is an `OverlayScene`**: the page underneath stays exactly as it
 * was, worked out by the strategies below this one, and the supporting pane
 * rises over it in a [ModalBottomSheet]. The two ways out both pop exactly one
 * entry. A drag down or a tap on the scrim asks the back stack to pop; a system
 * back pops it directly. Either way `NavDisplay` then asks the overlay to leave,
 * and it stays composed until the sheet has finished going — a sheet already
 * dragged away finishes at once.
 *
 * **It needs an `OverlayHost` above the `NavDisplay`**, because that is where
 * `ModalBottomSheet` draws. The library says to install one at the root, and
 * without one this fails loudly rather than drawing nothing.
 *
 * @param twoPane Whether there is room for both. Read here, at composition,
 *   because `calculateScene` is not a composable. Width alone.
 * @param supportingWeight How much of the width the supporting pane takes on two
 *   panes, [SupportingPaneScaffold]'s own default unless given.
 * @param resizable Whether the seam between the two panes can be dragged.
 * @param showDivider A hairline between the panes.
 */
@Composable
fun <T : Any> rememberSupportingPaneSceneStrategy(
    twoPane: Boolean = LocalWindowSizeClass.current.width.hasRoomForTwoPanes,
    supportingWeight: Float = PaneScaffoldDefaults.SupportingWeight,
    resizable: Boolean = false,
    showDivider: Boolean = true,
): SceneStrategy<T> = remember(twoPane, supportingWeight, resizable, showDivider) {
    SupportingPaneSceneStrategy(twoPane, supportingWeight, resizable, showDivider)
}

/** Marks an entry as the main content of a main-and-supporting pair. */
fun mainPane(): Map<String, Any> = mapOf(MainPaneKey to MainPaneRole)

/** Marks an entry as the pane that supports the nearest [mainPane] below it. */
fun supportingPane(): Map<String, Any> = mapOf(SupportingPaneKey to SupportingPaneRole)

internal const val MainPaneKey = "io.kontour.ui.nav3.mainPane"
internal const val SupportingPaneKey = "io.kontour.ui.nav3.supportingPane"

internal object MainPaneRole
internal object SupportingPaneRole

private val NavEntry<*>.isMain: Boolean get() = metadata[MainPaneKey] == MainPaneRole
private val NavEntry<*>.isSupporting: Boolean get() = metadata[SupportingPaneKey] == SupportingPaneRole

internal class SupportingPaneSceneStrategy<T : Any>(
    private val twoPane: Boolean,
    private val supportingWeight: Float,
    private val resizable: Boolean,
    private val showDivider: Boolean,
) : SceneStrategy<T> {

    /**
     * One sheet's worth of state per overlay, kept here rather than on the scene.
     *
     * `NavDisplay` recalculates scenes freely and keeps whichever instance it
     * likes, so the instance it later asks to leave need not be the one whose
     * content is on screen. Keyed on the scene's key, both of them reach the same
     * sheet; the entry goes once the sheet has hidden.
     */
    private val sheets = mutableMapOf<Any, SheetHandle>()

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val top = entries.lastOrNull() ?: return null

        if (twoPane) {
            if (top.isMain) {
                return SupportingPaneScene(top, null, entries.dropLast(1), supportingWeight, resizable, showDivider, onBack)
            }
            if (!top.isSupporting) return null
            val main = entries.getOrNull(entries.lastIndex - 1)?.takeIf { it.isMain } ?: return null
            return SupportingPaneScene(main, top, entries.dropLast(1), supportingWeight, resizable, showDivider, onBack)
        }

        // Narrow, and only over the main content it supports: a supporting pane
        // with nothing under it to support is an ordinary page, and the single
        // pane strategy shows it as one.
        if (!top.isSupporting) return null
        if (entries.getOrNull(entries.lastIndex - 1)?.isMain != true) return null
        val key = top.contentKey
        val handle = sheets.getOrPut(key) { SheetHandle() }
        return SupportingSheetScene(
            supporting = top,
            below = entries.dropLast(1),
            handle = handle,
            onBack = onBack,
            onGone = { if (sheets[key] === handle) sheets.remove(key) },
        )
    }
}

/**
 * The main content, and the supporting pane beside it while it is on the stack.
 *
 * `equals` covers the key, the entries and the layout, and not [onBack], for the
 * reason `ListDetailScene` gives.
 */
internal class SupportingPaneScene<T : Any>(
    private val main: NavEntry<T>,
    private val supporting: NavEntry<T>?,
    override val previousEntries: List<NavEntry<T>>,
    private val supportingWeight: Float,
    private val resizable: Boolean,
    private val showDivider: Boolean,
    private val onBack: () -> Unit,
) : Scene<T> {

    override val key: Any = main.contentKey

    override val entries: List<NavEntry<T>> = listOfNotNull(main, supporting)

    override val content: @Composable () -> Unit = {
        // The supporting entry that was last on the stack, drawn while its pane
        // slides away. Popping it produces a scene with no supporting entry at
        // all, and the pane still has a slide to finish. Navigation 3 keeps a
        // popped entry's state for exactly as long as its content stays composed
        // and cleans it up the moment it leaves, so drawing it through the slide
        // is what its own exit animations do. A plain object held here, because
        // a lambda would not do: the compiler keeps one lambda per call site and
        // swaps its body, so a held one runs the new scene's.
        val leaving = remember { mutableStateOf<NavEntry<T>?>(null) }
        if (supporting != null) leaving.value = supporting
        // The pane follows a back gesture towards its edge, then closes the way
        // it always has, from the pose the hand left it in; opening again
        // starts from rest.
        val back = rememberSceneBack(enabled = supporting != null, runOut = false, onBack = onBack)
        LaunchedEffect(supporting != null) { if (supporting != null) back.reset() }
        val pane: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().paneBackMotion(back)) { leaving.value?.Content() }
        }

        SupportingPaneScaffold(
            main = { main.Content() },
            supporting = pane,
            supportingVisible = supporting != null,
            onDismissSupporting = onBack,
            twoPane = true,
            supportingWeight = supportingWeight,
            resizable = resizable,
            showDivider = showDivider,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is SupportingPaneScene<*> &&
            key == other.key &&
            entries == other.entries &&
            previousEntries == other.previousEntries &&
            supportingWeight == other.supportingWeight &&
            resizable == other.resizable &&
            showDivider == other.showDivider

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entries.hashCode()
        result = 31 * result + previousEntries.hashCode()
        result = 31 * result + supportingWeight.hashCode()
        result = 31 * result + resizable.hashCode()
        result = 31 * result + showDivider.hashCode()
        return result
    }

    override fun toString(): String = "SupportingPaneScene(key=$key, entries=$entries)"
}

/** Whether one overlay's sheet is wanted, and word of when it has gone. */
internal class SheetHandle {
    var wanted by mutableStateOf(true)
    val gone = CompletableDeferred<Unit>()

    /** Whether the sheet was ever composed — one that was not has nothing to wait for. */
    var composed = false
}

/**
 * The supporting pane in a sheet, over the page it supports.
 *
 * `overlaidEntries` is the stack beneath, which `NavDisplay` lays out through
 * the same strategies as if the sheet were not there.
 */
internal class SupportingSheetScene<T : Any>(
    private val supporting: NavEntry<T>,
    below: List<NavEntry<T>>,
    private val handle: SheetHandle,
    private val onBack: () -> Unit,
    private val onGone: () -> Unit,
) : OverlayScene<T> {

    override val key: Any = supporting.contentKey

    override val entries: List<NavEntry<T>> = listOf(supporting)

    /** Which sheet this instance drives, so a test can see two instances share one. */
    internal val handleForTest: SheetHandle get() = handle

    override val previousEntries: List<NavEntry<T>> = below

    override val overlaidEntries: List<NavEntry<T>> = below

    override val content: @Composable () -> Unit = {
        val state = rememberSheetState(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
            initialDetent = SheetDetent.Hidden,
        )
        ModalBottomSheet(
            visible = handle.wanted,
            onDismissRequest = onBack,
            state = state,
        ) {
            supporting.Content()
        }
        // Word that the sheet is off the screen, for [onRemove] to wait on. Only
        // once it has been let go of: a sheet that has not arrived yet is also
        // not visible, and is not gone.
        LaunchedEffect(handle, state) {
            snapshotFlow { !handle.wanted && !state.isVisible }.first { it }
            handle.gone.complete(Unit)
            onGone()
        }
        // And if the sheet leaves composition some other way — the whole display
        // going — nothing is left to wait for, and [onRemove] must not hang.
        DisposableEffect(handle) {
            handle.composed = true
            onDispose { handle.gone.complete(Unit) }
        }
    }

    /**
     * Keeps the overlay composed until its sheet has hidden.
     *
     * `NavDisplay` calls this once the entry has left the back stack, and takes
     * the overlay out of composition when it returns — so returning at once would
     * cut the sheet off mid-slide. A sheet already dragged away is already
     * hidden, and this returns as soon as that is seen.
     */
    override suspend fun onRemove() {
        handle.wanted = false
        if (handle.composed) handle.gone.await()
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is SupportingSheetScene<*> &&
            key == other.key &&
            entries == other.entries &&
            overlaidEntries == other.overlaidEntries

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entries.hashCode()
        result = 31 * result + overlaidEntries.hashCode()
        return result
    }

    override fun toString(): String = "SupportingSheetScene(key=$key, over=$overlaidEntries)"
}
