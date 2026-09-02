package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.ScrimStyle
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
)

/** [TextToolbarLabels] taking the theme's words. */
@Composable
fun textToolbarLabels(
    copy: String = Theme.strings.copy,
    cut: String = Theme.strings.cut,
    paste: String = Theme.strings.paste,
    selectAll: String = Theme.strings.selectAll,
): TextToolbarLabels = TextToolbarLabels(copy, cut, paste, selectAll)

/**
 * Adds items to the toolbar shown when the user selects text.
 *
 * ```kotlin
 * TextSelectionToolbar(
 *     actions = listOf(TextToolbarAction("Plan a trip") { planTrip() }),
 * ) {
 *     AppRoot()
 * }
 * ```
 *
 * ### With no actions it does nothing at all, and that is the point
 *
 * This used to replace the platform's selection popup with four buttons of our
 * own on desktop and web, on the grounds that Compose's default there is
 * unstyled. That was the wrong trade. A selection toolbar is a **system**
 * surface: on Android it carries "Look Up", "Translate", "Share", the user's
 * keyboard extensions and their configured text replacements; on iOS the same
 * plus the writing tools; on desktop it is what every other application on that
 * machine shows. Drawing our own removes functionality the user expects, in
 * exchange for matching a design system they did not ask the toolbar to match.
 *
 * So [actions] empty — the default — installs nothing, and every platform shows
 * its own toolbar exactly as it would without this library.
 *
 * ### With actions it has to draw one, because no platform lets us add to theirs
 *
 * `TextToolbar` is the whole of what Compose exposes in common code: a rectangle
 * and one nullable callback per built-in verb. There is no common way to append
 * an item to the platform's menu — Android would need an `ActionMode` with a
 * custom menu resource, iOS a `UIMenuController` we have no handle on, and web
 * has no system toolbar to append to. An app that genuinely needs "Plan a trip"
 * on its selection menu therefore has to trade the system surface for one it
 * controls, and this makes that trade explicit rather than making it for you.
 *
 * The drawn toolbar carries the built-in verbs the framework offered *plus*
 * [actions]. A verb the framework did not offer is absent rather than disabled:
 * a greyed-out "Paste" on an empty clipboard tells the user nothing they can act
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
 * @param actions Extra items, after the built-in verbs. Empty leaves the
 *   platform's own toolbar alone.
 * @param labels The four verbs, for when this draws the toolbar. Ignored when
 *   [actions] is empty, since then the platform supplies its own words.
 */
@Composable
fun TextSelectionToolbar(
    actions: List<TextToolbarAction> = emptyList(),
    labels: TextToolbarLabels = textToolbarLabels(),
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        content()
        return
    }

    var request by remember { mutableStateOf<ToolbarRequest?>(null) }
    val toolbar = remember { KontourTextToolbar { request = it } }
    val extras by rememberUpdatedState(actions)

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
    }

    val showing = request
    AnchoredDropdownMenu(
        visible = showing != null,
        anchor = showing?.rect,
        onDismissRequest = { toolbar.hide() },
        side = OverlaySide.Top,
        alignment = OverlayAlignment.Center,
        // Transparent rather than dimmed: the user is looking at the text they
        // just selected, and dimming it defeats the point.
        scrim = ScrimStyle.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(Theme.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            ToolbarAction(labels.cut, showing?.onCut, toolbar::hide)
            ToolbarAction(labels.copy, showing?.onCopy, toolbar::hide)
            ToolbarAction(labels.paste, showing?.onPaste, toolbar::hide)
            ToolbarAction(labels.selectAll, showing?.onSelectAll, toolbar::hide)
            extras.forEach { extra ->
                ToolbarAction(extra.label, extra.onClick, toolbar::hide)
            }
        }
    }
}

@Composable
private fun ToolbarAction(label: String, action: (() -> Unit)?, hide: () -> Unit) {
    // See the note on [TextSelectionToolbar]: absent rather than disabled.
    if (action == null) return
    Button(
        onClick = {
            action()
            hide()
        },
        variant = ButtonVariant.Ghost,
        size = ButtonSize.Small,
    ) { +label }
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
