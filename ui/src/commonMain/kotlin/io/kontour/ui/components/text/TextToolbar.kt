package io.kontour.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.action.ToolbarDivider
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.AnchoredOverlayLayout
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.MenuDefaults
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.platform.platformHasSystemTextToolbar
import io.kontour.ui.theme.Theme

/**
 * An extra item on the text-selection toolbar.
 *
 * @param label What it says. Yours to localise; the four built-in verbs come
 *   from [io.kontour.ui.theme.Strings] instead.
 * @param onClick What it does. Runs and then dismisses the toolbar.
 */
@Immutable
class TextToolbarAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The labels the four built-in verbs use, when the toolbar is drawn here.
 *
 * Defaulted from [io.kontour.ui.theme.Strings] rather than from literals, so an
 * app that has already told the theme its words does not have to tell this as
 * well. Construct it inside composition — [textToolbarLabels] — or pass every
 * field.
 */
data class TextToolbarLabels(
    val copy: String,
    val cut: String,
    val paste: String,
    val selectAll: String,
    val more: String,
)

/** [TextToolbarLabels] taking the theme's words. */
@Composable
fun textToolbarLabels(
    copy: String = Theme.strings.copy,
    cut: String = Theme.strings.cut,
    paste: String = Theme.strings.paste,
    selectAll: String = Theme.strings.selectAll,
    more: String = Theme.strings.more,
): TextToolbarLabels = TextToolbarLabels(copy, cut, paste, selectAll, more)

object TextToolbarDefaults {
    /**
     * How many items go in the bar before the rest go behind "More".
     *
     * Four, which is what fits across a phone at the largest type size and is
     * also what every platform's own selection toolbar shows before its own
     * overflow. A toolbar wider than the selection it belongs to is a toolbar
     * that has stopped pointing at anything.
     */
    const val MaxInline: Int = 4
}

/**
 * The toolbar shown when the user selects text.
 *
 * ```kotlin
 * TextSelectionToolbar(
 *     actions = listOf(TextToolbarAction("Plan a trip") { planTrip() }),
 * ) {
 *     AppRoot()
 * }
 * ```
 *
 * ### It defers to the platform where the platform has something to defer to
 *
 * A selection toolbar is a **system** surface on a phone: on Android it carries
 * Look Up, Translate, Share, the user's keyboard extensions and their configured
 * text replacements; on iOS the same plus the writing tools. Drawing our own
 * there removes functionality the user expects in exchange for matching a design
 * system they never asked the toolbar to match. So on Android and iOS, with no
 * [actions] to add, this installs nothing at all.
 *
 * **Desktop and the web have no such surface**, and that is the half this used
 * to get wrong. Compose falls back to a bare unstyled popup on the JVM and to
 * nothing recognisable in a browser, so "leave the platform alone" left those
 * users with less rather than more — the opposite of the reason for deferring.
 * See [io.kontour.ui.platform.platformHasSystemTextToolbar]: the rule is *show
 * the richest toolbar available*, which is the system's where there is one and
 * this one where there is not.
 *
 * ### It is a [Toolbar], because that is what it is
 *
 * It used to be a row of ghost buttons inside a menu panel — a menu doing a
 * toolbar's job, with a menu's shape and a menu's padding. [Toolbar] is the
 * library's own answer to "a floating surface holding actions, over content it
 * does not belong to", which is a selection toolbar exactly, and using it means
 * the elevation, shape and traversal semantics come from one place rather than
 * from a second set of numbers here.
 *
 * ### Past [TextToolbarDefaults.MaxInline] items, the rest go in a menu
 *
 * A toolbar that outgrows the selection it points at has stopped pointing at
 * anything. The built-in verbs the framework offered come first, then [actions],
 * and whatever does not fit goes behind "More" as a [DropdownMenu] — which is
 * also how every platform's own toolbar handles the same problem.
 *
 * ### With actions, it has to draw one even where a system toolbar exists
 *
 * `TextToolbar` is the whole of what Compose exposes in common code: a rectangle
 * and one nullable callback per built-in verb. There is no common way to append
 * an item to the platform's menu — Android would need an `ActionMode` with a
 * custom menu resource, iOS a `UIMenuController` we have no handle on. An app
 * that genuinely needs "Plan a trip" on its selection menu therefore has to
 * trade the system surface for one it controls, and this makes that trade
 * explicit rather than making it for you.
 *
 * A verb the framework did not offer is absent rather than disabled: a
 * greyed-out "Paste" on an empty clipboard tells the user nothing they can act
 * on, and four permanent buttons make the two that apply harder to hit.
 *
 * ### What an action cannot have
 *
 * The selected text. Compose hands `showMenu` a rectangle and four callbacks and
 * nothing else, so an action here fires against whatever the app already knows
 * — a screen's current field, a view model — rather than against a string passed
 * in. Anything that needs the text itself belongs on that screen, where the
 * selection lives.
 *
 * @param actions Extra items, after the built-in verbs.
 * @param labels The words, for when this draws the toolbar. Ignored where the
 *   platform's own toolbar is used, since it supplies its own.
 */
@Composable
fun TextSelectionToolbar(
    actions: List<TextToolbarAction> = emptyList(),
    labels: TextToolbarLabels = textToolbarLabels(),
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty() && platformHasSystemTextToolbar) {
        content()
        return
    }

    var request by remember { mutableStateOf<ToolbarRequest?>(null) }
    val toolbar = remember { KontourTextToolbar { request = it } }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
    }

    SelectionToolbarOverlay(
        request = request,
        actions = actions,
        labels = labels,
        onDismissRequest = toolbar::hide,
    )
}

/**
 * The bar itself, published into the overlay host and anchored to the selection.
 *
 * Built from [AnchoredOverlayLayout] and [Toolbar] rather than from
 * `AnchoredDropdownMenu`, which would bring a menu panel with it. Same anchoring
 * machinery, correct surface.
 */
@Composable
private fun SelectionToolbarOverlay(
    request: ToolbarRequest?,
    actions: List<TextToolbarAction>,
    labels: TextToolbarLabels,
    onDismissRequest: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Read live by the overlay's measure pass rather than captured when the
    // entry was built — see `AnchoredOverlayLayout`. The selection's rectangle
    // moves as the user drags a handle, and the bar has to follow it.
    val latest by rememberUpdatedState(request)
    val latestActions by rememberUpdatedState(actions)
    val latestLabels by rememberUpdatedState(labels)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(request != null) {
        if (request == null) {
            host.hide(key)
            return@LaunchedEffect
        }
        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Menu,
                // Transparent rather than dimmed: the user is looking at the
                // text they just selected, and dimming it defeats the point.
                scrim = ScrimStyle.Transparent,
                dismissLabel = "Close",
                onDismiss = { dismiss() },
                content = {
                    AnchoredOverlayLayout(
                        anchorInRoot = { latest?.rect },
                        side = OverlaySide.Top,
                        alignment = OverlayAlignment.Center,
                        gap = Theme.spacing.xxs,
                        margin = MenuDefaults.ScreenMargin,
                    ) {
                        SelectionToolbar(
                            items = latest.items(latestLabels, latestActions),
                            more = latestLabels.more,
                            onDismissRequest = { dismiss() },
                        )
                    }
                },
            )
        )
    }
}

/** One thing the toolbar can do, whether it came from the framework or the app. */
private class ToolbarItem(val label: String, val onClick: () -> Unit)

/**
 * The verbs the framework offered, then the app's own.
 *
 * A null callback means "not available here" — which is how a read-only field
 * ends up with copy but not cut — and those are left out rather than disabled.
 */
private fun ToolbarRequest?.items(
    labels: TextToolbarLabels,
    actions: List<TextToolbarAction>,
): List<ToolbarItem> = buildList {
    fun offer(label: String, action: (() -> Unit)?) {
        if (action != null) add(ToolbarItem(label, action))
    }
    offer(labels.cut, this@items?.onCut)
    offer(labels.copy, this@items?.onCopy)
    offer(labels.paste, this@items?.onPaste)
    offer(labels.selectAll, this@items?.onSelectAll)
    actions.forEach { add(ToolbarItem(it.label, it.onClick)) }
}

@Composable
private fun SelectionToolbar(
    items: List<ToolbarItem>,
    more: String,
    onDismissRequest: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    // The overflow control is itself an item's worth of width, so a list one
    // over the limit puts *two* into the menu rather than one — otherwise
    // adding the button to fit the fifth is what pushes it out again.
    val fits = if (items.size <= TextToolbarDefaults.MaxInline) {
        items.size
    } else {
        TextToolbarDefaults.MaxInline - 1
    }

    Toolbar(contentPadding = Theme.spacing.xxs) {
        items.take(fits).forEach { item ->
            Button(
                onClick = {
                    item.onClick()
                    onDismissRequest()
                },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            ) { +item.label }
        }
        if (fits < items.size) {
            ToolbarDivider()
            IconButton(
                icon = SystemIcons.More,
                contentDescription = more,
                onClick = { overflowOpen = true },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            )
            DropdownMenu(
                visible = overflowOpen,
                onDismissRequest = { overflowOpen = false },
            ) {
                items.drop(fits).forEach { item ->
                    item(item.label, onClick = {
                        item.onClick()
                        overflowOpen = false
                        onDismissRequest()
                    })
                }
            }
        }
    }
}

/** What the framework asked to be shown, and where. */
internal class ToolbarRequest(
    val rect: Rect,
    val onCopy: (() -> Unit)?,
    val onPaste: (() -> Unit)?,
    val onCut: (() -> Unit)?,
    val onSelectAll: (() -> Unit)?,
)

/**
 * The `TextToolbar` the framework talks to.
 *
 * Compose hands it a rectangle covering the selection and one callback per
 * action it supports — a null callback meaning "not available here", which is
 * how a read-only field ends up with copy but not cut.
 */
internal class KontourTextToolbar(
    private val onRequest: (ToolbarRequest?) -> Unit,
) : TextToolbar {

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        status = TextToolbarStatus.Shown
        onRequest(
            ToolbarRequest(
                rect = rect,
                onCopy = onCopyRequested,
                onPaste = onPasteRequested,
                onCut = onCutRequested,
                onSelectAll = onSelectAllRequested,
            )
        )
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        onRequest(null)
    }
}
