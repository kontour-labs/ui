package io.kontour.ui.overlay

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.relocation.BringIntoViewModifierNode

/**
 * Ends every bring-into-view request from inside an overlay at the overlay:
 * whatever scrolls inside it answers first, and nothing outside it is asked.
 *
 * Reported 2026-10-20 from Android: *"sometimes when a sheet (or dialog) opens
 * at exactly the same time as the keyboard, content gets pushed up. It should
 * just become scrollable behind the keyboard."* A field asks to be brought into
 * view when it takes focus, its caret asks as it does, and `FieldScaffold` asks
 * again on every frame the keyboard's height changes. Inside a sheet still on
 * its way up, the field is low on the screen, or under it, and nothing in the
 * overlay takes the request — a sheet moves by its own drag, not as a
 * scroller — so it reached the root. On Android the root hands an unanswered
 * request to `View.requestRectangleOnScreen`, and the window panned the whole
 * app, sheet and page alike, to show where the field had been for a frame.
 * Once the keyboard had settled the field was already clear of it and asked
 * for nothing, which is why it took the two arriving together.
 *
 * An overlay is placed by the library, keyboard and all — a sheet hands the
 * keyboard to its content as padding and a dialog centres itself above it — so
 * there is nothing outside it that should move for what is in it. A field the
 * keyboard covers is scrolled clear by the content's own scroller, as far as
 * the keyboard's padding gives it room: see `FieldScaffold`.
 */
internal fun Modifier.endsBringIntoView(): Modifier = this then EndsBringIntoViewElement

private data object EndsBringIntoViewElement : ModifierNodeElement<EndsBringIntoViewNode>() {
    override fun create(): EndsBringIntoViewNode = EndsBringIntoViewNode()
    override fun update(node: EndsBringIntoViewNode) = Unit
}

private class EndsBringIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) = Unit
}
