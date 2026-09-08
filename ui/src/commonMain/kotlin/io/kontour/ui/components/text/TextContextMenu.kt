package io.kontour.ui.components.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.kontour.ui.overlay.AnchoredDropdownMenu

/** What a secondary press asked for: which field, and where the pointer was. */
internal class TextMenuRequest(
    val state: TextFieldState,
    val anchor: Rect,
    val editable: Boolean,
)

/**
 * Opens the text context menu, or null where there is nothing to open it into.
 *
 * Null is the ordinary case for a field drawn outside an
 * [io.kontour.ui.overlay.OverlayHost], and it has to be: `LocalOverlayHost`
 * throws when there is no host, so a field that reached for one unconditionally
 * would break every app and every test that draws a field on its own. The host
 * publishes a way to open the menu; a field uses it if it is there.
 */
internal val LocalTextContextMenu = compositionLocalOf<((TextMenuRequest) -> Unit)?> { null }

/**
 * The right-click menu for every text box below an
 * [io.kontour.ui.overlay.OverlayHost].
 *
 * ### What was there before, which is two different things
 *
 * Reported as "replace the right-click menu in every text box", and the answer
 * depended on where you looked. **On the JVM** Compose opens a context menu of
 * its own, in a separate popup window — a second semantics root, with none of
 * this library's shape, type or colour. **In a browser** nothing happens at all:
 * measured against the built site, the `contextmenu` event comes back
 * `prevented` with no menu drawn in its place, on a field and on prose alike.
 *
 * `LocalContextMenuRepresentation` would replace the first and is declared in
 * foundation's **desktop** source set — it does not exist on the platform the
 * report came from. So the menu is the library's own [AnchoredDropdownMenu],
 * opened from a secondary press caught on [PointerEventPass.Initial]: the
 * Initial pass runs parent to child, so the press is consumed on the field's
 * frame before the input's own detector can raise the platform's menu, and the
 * surface is the one every other menu in the library uses.
 *
 * ### It does not work in a browser yet, and that is measured rather than hoped
 *
 * On the JVM this replaces the platform's popup: two semantics roots before, one
 * after, with the verbs on it. On the **web it does nothing at all** — rebuilt
 * site, right-click on the `TextField` demo, no menu drawn.
 *
 * That is not the harness failing to deliver the click. `measure-web.mjs`
 * records the secondary button arriving as a real `pointerdown` **and**
 * `mousedown` with `button === 2`, and the `contextmenu` that follows is
 * `prevented` — so the browser hands the press to the page, something in the
 * web runtime swallows the platform menu, and no secondary press appears to
 * reach this handler. Where exactly it is lost is not established, and inventing
 * an answer is how a round of repeats gets started. Desktop is fixed; the web
 * half of C9a is open, with the measurement that will start the next attempt.
 */
@Composable
internal fun TextContextMenuHost(content: @Composable () -> Unit) {
    // Two pieces of state rather than one nullable request, for the reason
    // `ContextMenuArea` documents: clearing the request *is* the dismissal, and
    // a menu whose anchor and items vanish on the first frame of its exit
    // animates out empty and from the corner of the window.
    var request by remember { mutableStateOf<TextMenuRequest?>(null) }
    var open by remember { mutableStateOf(false) }

    val opener = remember {
        { asked: TextMenuRequest ->
            request = asked
            open = true
        }
    }

    CompositionLocalProvider(LocalTextContextMenu provides opener) { content() }

    val labels = textToolbarLabels()
    TextContextMenu(
        request = request,
        open = open,
        labels = labels,
        onDismissRequest = { open = false },
    )
}

@Composable
@Suppress("DEPRECATION")
private fun TextContextMenu(
    request: TextMenuRequest?,
    open: Boolean,
    labels: TextToolbarLabels,
    onDismissRequest: () -> Unit,
) {
    // `LocalClipboardManager` rather than `LocalClipboard`, deliberately and
    // with the deprecation suppressed. The replacement is suspending and carries
    // a `ClipEntry` that is a platform type with no common way to build one from
    // text — so on the two platforms this menu exists for, the newer API cannot
    // express "put this string on the clipboard" from common code at all. When
    // it can, this moves.
    val clipboard = LocalClipboardManager.current

    AnchoredDropdownMenu(
        visible = open,
        anchor = request?.anchor,
        onDismissRequest = onDismissRequest,
    ) {
        val asked = request ?: return@AnchoredDropdownMenu
        val state = asked.state
        val selection = state.selection
        val selected = !selection.collapsed

        // Absent rather than disabled, the same rule the selection toolbar
        // follows: a greyed-out "Cut" over no selection tells the user nothing
        // they can act on, and four permanent rows make the two that apply
        // harder to hit.
        if (selected && asked.editable) {
            item(labels.cut) {
                clipboard.setText(AnnotatedString(state.selectedText()))
                state.edit { replace(selection.min, selection.max, "") }
                onDismissRequest()
            }
        }
        if (selected) {
            item(labels.copy) {
                clipboard.setText(AnnotatedString(state.selectedText()))
                onDismissRequest()
            }
        }
        if (asked.editable) {
            item(labels.paste) {
                val pasted = clipboard.getText()?.text
                if (!pasted.isNullOrEmpty()) {
                    state.edit { replace(selection.min, selection.max, pasted) }
                }
                onDismissRequest()
            }
        }
        if (state.text.isNotEmpty()) {
            item(labels.selectAll) {
                state.edit { this.selection = TextRange(0, length) }
                onDismissRequest()
            }
        }
    }
}

private fun TextFieldState.selectedText(): String =
    text.substring(selection.min, selection.max)

/**
 * Opens the library's text context menu on a secondary press.
 *
 * On [PointerEventPass.Initial] and consuming, which is what stops the platform
 * drawing its own — the Initial pass runs parent to child, so this sees the
 * press before the text input inside it does.
 */
@Composable
internal fun Modifier.textContextMenu(
    state: TextFieldState,
    editable: Boolean,
    enabled: Boolean,
): Modifier {
    val open = LocalTextContextMenu.current
    if (open == null || !enabled) return this

    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(open, editable) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press) continue
                    if (!event.buttons.isSecondaryPressed) continue
                    val change = event.changes.firstOrNull() ?: continue
                    change.consume()
                    val root = coordinates?.localToRoot(change.position) ?: Offset.Zero
                    // A zero-size anchor: the menu hangs off the point itself,
                    // which is what makes it belong to the click rather than to
                    // the field.
                    open(TextMenuRequest(state, Rect(root, root), editable))
                }
            }
        }
}
