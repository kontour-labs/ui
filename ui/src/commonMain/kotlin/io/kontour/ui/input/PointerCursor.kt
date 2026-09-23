package io.kontour.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The shape the mouse pointer takes over a component.
 *
 * Named for what the pointer is *over*, not for what it looks like anywhere in
 * particular. [ResizeColumn] is an east-west arrow on a desktop, a horizontal
 * double arrow on an Android tablet with a mouse and a hand in a browser, and the
 * component asking for it should not have to know which.
 *
 * Ten, and each is here because something in this library points at it or is the
 * other half of a pair that does. The list is the part that grows, so the bar for
 * an eleventh is a component that needs it — not a shape a platform happens to
 * offer.
 *
 * @see pointerCursor
 */
enum class Cursor {
    /**
     * The arrow. What a disabled control shows, and what a child asks for to keep
     * an enclosing hand from reaching it.
     */
    Default,

    /** A hand: this answers a click. What [pointerCursor] gives unless told otherwise. */
    Pointer,

    /** An I-beam, over text that can be typed into or selected. */
    Text,

    /** A crosshair, for picking a *point* rather than a thing — a colour out of a spectrum. */
    Crosshair,

    /** A divider between two columns that is dragged sideways: a pane splitter. */
    ResizeColumn,

    /** A divider between two rows that is dragged up and down: a sheet's grip. */
    ResizeRow,

    /** Something that can be picked up and moved: a scrollbar thumb, a reorder handle. */
    Grab,

    /** The same thing, while it is held. */
    Grabbing,

    /**
     * Refused here — a drop target that will not take what is being dragged over
     * it, a row that is locked.
     *
     * **Not a disabled control.** A disabled Save button in a form that is not
     * finished yet is inert, not forbidden, and a no-entry sign on it tells the
     * reader they have done something wrong. Disabled controls show [Default].
     */
    NotAllowed,

    /**
     * Working in the background, with the pointer still usable — an import running
     * behind a page that can still be scrolled.
     *
     * Not *waiting*. Nothing in this library blocks the pointer, and the busy shapes
     * some platforms offer mean "the application has stopped", which would be an
     * untrue thing to say about a 400ms spinner. Where a platform has no honest
     * shape for this it shows the arrow.
     */
    Progress,
}

/**
 * Sets the mouse cursor over this component.
 *
 * ```kotlin
 * Modifier
 *     .pointerCursor(enabled = enabled)
 *     .focusRing(interactions, shape)
 *     .clip(shape)
 *     .clickable(interactionSource = interactions, indication = …) { … }
 * ```
 *
 * ### Not part of [focusRing], which is the tidy-looking mistake
 *
 * Almost every component that wants a cursor already asks for a focus ring, so
 * folding one into the other would have been one edit instead of thirty. But
 * [focusRing] returns `this` unchanged unless the element is *focused* — a
 * cursor is a **hover** affordance and has nothing to do with focus, so it
 * would appear only on elements the keyboard had already reached. Removing that
 * early return to make room for it would put a node on every one of those call
 * sites on every platform, including the touch ones where a cursor is
 * meaningless.
 *
 * ### Gated on the modality, like every other hover affordance here
 *
 * `pointerHoverIcon` is inert without a pointer, so this gate buys a node
 * rather than correctness — the same trade `kontourIndication` already makes
 * for its hover wash. On a hybrid device the first mouse movement is what sets
 * the modality, so the icon appears from the second event rather than the
 * first, which is invisible: the movement that reveals a cursor is the one that
 * created it.
 *
 * ### There were four cursors, and the reason for stopping at four was wrong
 *
 * Compose Multiplatform's common [PointerIcon] has four shapes — the arrow, the
 * crosshair, the I-beam and the hand — and this used to say that was the whole
 * set: no resize cursor for a pane splitter, no grab for a scrollbar thumb, and
 * no way to add either without a per-target `expect`/`actual`, because desktop
 * could build a [PointerIcon] from an AWT cursor and *no other target we ship
 * could*. The first half was true. The second was never checked against the
 * artifacts. Android has a public [PointerIcon] built from one of its own
 * `PointerIcon.TYPE_*` constants — grab and grabbing and both double arrows among
 * them — for a mouse or a stylus on a tablet or a Chromebook. So the pane splitter
 * showed a hand, on two of the three platforms that could have done better, for as
 * long as the note stood. The web is the third, and there the note was right for a
 * reason it did not give: Compose has a CSS-keyword cursor for the browser and
 * keeps it internal, so a library cannot make one.
 *
 * The per-target `expect`/`actual` the note was afraid of is four files of one
 * `when` each. What each platform draws for each [Cursor]:
 *
 * | | Desktop | Android | Web | iOS |
 * |---|---|---|---|---|
 * | [Cursor.ResizeColumn] | east-west arrow | horizontal double arrow | *hand* | — |
 * | [Cursor.ResizeRow] | north-south arrow | vertical double arrow | *hand* | — |
 * | [Cursor.Grab] | *hand* | grab | *hand* | — |
 * | [Cursor.Grabbing] | four-way move | grabbing | *hand* | — |
 * | [Cursor.NotAllowed] | *arrow* | no-drop | *arrow* | — |
 * | [Cursor.Progress] | *arrow* | *arrow* | *arrow* | — |
 *
 * Italics are the nearest of the four common shapes, standing in where a platform
 * has nothing that means the same thing — or, on the web, has it and will not lend
 * it. Anything dragged falls back to the hand, which is what it showed before and
 * at least says the thing does something; the arrow would say nothing. AWT has no
 * grab and no forbidden shape, and AWT's and Android's only busy cursors mean
 * *stopped*. The other four [Cursor]s are Compose's own and look the
 * same everywhere. iOS has no cursor backend at all, and a pointer there keeps the
 * system's own shape.
 *
 * Nothing in this repository can *see* a cursor — a rendered frame has no pointer
 * in it — so the mapping is checked to compile on every target and to resolve to
 * the table above on the desktop, and the rest is seen by hovering the desktop
 * showcase.
 *
 * @param cursor What the component is. [Cursor.Pointer] for anything that answers
 *   a click, [Cursor.Text] over editable text; see [Cursor] for the rest.
 * @param enabled Pass the component's own `enabled` through. A disabled control
 *   shows [Cursor.Default] — the arrow, *set*, rather than nothing. It used to set
 *   nothing, and nothing is not neutral: a disabled button inside a clickable card
 *   then showed the card's hand, promising a click the button would not answer.
 */
@Composable
fun Modifier.pointerCursor(
    cursor: Cursor = Cursor.Pointer,
    enabled: Boolean = true,
): Modifier {
    if (!LocalInputModality.current.supportsHover) return this
    return pointerHoverIcon(pointerIconFor(if (enabled) cursor else Cursor.Default))
}

/**
 * [Cursor.Grabbing] over this component and everything inside it, while [held].
 *
 * For a thing being dragged, whose cursor belongs to the drag rather than to
 * whatever the pointer happens to be over. Compose has no pointer capture for
 * cursors — a held pointer that leaves the element that set its cursor gets the
 * arrow back — so the nearest equivalent is to put the cursor on the whole of the
 * thing being moved and let it win over its children, which may have hands of
 * their own.
 *
 * **Present before the press, not added at the pickup.** Compose fixes the nodes
 * a held pointer reports to at the moment it goes down, so a cursor node that
 * only appeared once the drag had begun was never consulted — measured, not
 * assumed: the row showed the arrow. So the node is always there, and until
 * [held] it is the arrow overriding nothing, which lets every child's own cursor
 * through exactly as if it were absent.
 */
@Composable
internal fun Modifier.heldCursor(held: Boolean): Modifier {
    if (!LocalInputModality.current.supportsHover) return this
    return pointerHoverIcon(
        icon = pointerIconFor(if (held) Cursor.Grabbing else Cursor.Default),
        overrideDescendants = held,
    )
}

/**
 * What [cursor] draws on this platform: its own shape where it has one, and the
 * nearest of the common four where it does not.
 */
internal fun pointerIconFor(cursor: Cursor): PointerIcon =
    platformPointerIcon(cursor) ?: cursor.nearest

/**
 * The nearest of the four shapes Compose draws on every platform — which, for
 * those four, is the shape itself.
 *
 * Decided once, here, rather than in each platform's mapping, so two platforms
 * cannot disagree about what a missing shape falls back to.
 */
internal val Cursor.nearest: PointerIcon
    get() = when (this) {
        Cursor.Default -> PointerIcon.Default
        Cursor.Pointer -> PointerIcon.Hand
        Cursor.Text -> PointerIcon.Text
        Cursor.Crosshair -> PointerIcon.Crosshair
        // Everything that is dragged falls back to the hand, not the arrow. The
        // hand is imprecise — it says *click*, and these are dragged — but it
        // says *this does something*, and the arrow says nothing at all. It is
        // also what all four showed before they had shapes of their own, so a
        // platform that cannot draw theirs loses nothing it had.
        Cursor.ResizeColumn, Cursor.ResizeRow, Cursor.Grab, Cursor.Grabbing ->
            PointerIcon.Hand
        Cursor.NotAllowed, Cursor.Progress -> PointerIcon.Default
    }

/**
 * This platform's own [PointerIcon] for [cursor], or null where it has none.
 *
 * Null for the four common shapes too, which [nearest] already is exactly.
 */
internal expect fun platformPointerIcon(cursor: Cursor): PointerIcon?
