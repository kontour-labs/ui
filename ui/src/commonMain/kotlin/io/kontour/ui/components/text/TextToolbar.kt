package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** The labels the selection toolbar shows. Pull them from your string resources. */
data class TextToolbarLabels(
    val copy: String = "Copy",
    val cut: String = "Cut",
    val paste: String = "Paste",
    val selectAll: String = "Select all",
)

/**
 * Replaces the platform's text-selection popup with one drawn in the design
 * system.
 *
 * ```kotlin
 * KontourTheme {
 *     OverlayHost {
 *         KontourTextToolbar {
 *             AppRoot()
 *         }
 *     }
 * }
 * ```
 *
 * Worth doing on **desktop and web**, where Compose's default is a bare
 * unstyled row that looks like it belongs to a different application — and where
 * on web it can be clipped by the canvas rather than escaping it.
 *
 * Deliberately **not** worth doing on Android or iOS. There the platform toolbar
 * is a real system surface: it carries "Look Up", "Translate", "Share", the
 * user's own keyboard extensions, and the text-replacement entries they have
 * configured. Replacing it with four buttons removes functionality the user
 * expects and knows how to reach. This composable is a no-op on those targets
 * for that reason — install it unconditionally at the root and it does the right
 * thing per platform.
 *
 * @param labels Override to localise. The defaults are English.
 */
@Composable
fun KontourTextToolbar(
    labels: TextToolbarLabels = TextToolbarLabels(),
    content: @Composable () -> Unit,
) {
    if (!platformWantsCustomTextToolbar) {
        content()
        return
    }

    var state by remember { mutableStateOf<ToolbarRequest?>(null) }
    val toolbar = remember { KontourTextToolbarImpl { state = it } }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
    }

    val request = state
    AnchoredDropdownMenu(
        expanded = request != null,
        anchor = request?.rect,
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
            ToolbarAction(labels.cut, request?.onCut, toolbar::hide)
            ToolbarAction(labels.copy, request?.onCopy, toolbar::hide)
            ToolbarAction(labels.paste, request?.onPaste, toolbar::hide)
            ToolbarAction(labels.selectAll, request?.onSelectAll, toolbar::hide)
        }
    }
}

@Composable
private fun ToolbarAction(label: String, action: (() -> Unit)?, hide: () -> Unit) {
    // Absent rather than disabled. A greyed-out "Paste" on an empty clipboard
    // tells the user nothing they can act on, and four permanent buttons make
    // the two that apply harder to hit.
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
internal class KontourTextToolbarImpl(
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

/**
 * Whether this platform's built-in selection toolbar is worth replacing.
 *
 * True on desktop and web, where the default is unstyled. False on Android and
 * iOS, where it is a system surface carrying more than we could reproduce — see
 * [KontourTextToolbar].
 */
internal expect val platformWantsCustomTextToolbar: Boolean
