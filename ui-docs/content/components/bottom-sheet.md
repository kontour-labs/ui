# `BottomSheet`

A panel from the bottom edge that shares the screen with whatever is behind it.
The map stays usable; the sheet rests at a detent and is dragged between them.

<!--sample:BottomSheetBasics-->
```kotlin
val sheet = rememberSheetState(
    detents = listOf(
        SheetDetent.Hidden,
        SheetDetent.peek(140.dp),
        SheetDetent.Half,
        SheetDetent.Expanded,
    ),
    initialDetent = SheetDetent.peek(140.dp),
)

BottomSheet(
    state = sheet,
    // Controls that ride up with the sheet rather than being covered by
    // it — the map's "recentre" button is the case this is for.
    floatingControls = {
        IconButton(
            icon = Tabler.Outline.CurrentLocation,
            contentDescription = "Recentre",
            onClick = { recentre() },
            variant = ButtonVariant.Secondary,
        )
    },
) {
    // `sheetPeekAnchor` is what makes `peek` mean "as tall as this", so the
    // peek height follows the header instead of being a number to maintain.
    SheetHeader(modifier = Modifier.sheetPeekAnchor()) {
        +"Perth Underground"
        supporting { +"Platform 2 · Joondalup line" }
    }
    Departures()
}
```

Non-modal, which is the whole difference from
[`ModalBottomSheet`](modal-bottom-sheet.md): there is no scrim, nothing is
blocked, and the sheet is a second surface rather than an interruption. That is
what makes it right for a stop list over a map and wrong for a form.

Its resting positions come from a `rememberSheetState(detents = …)`. `Hidden`,
`Half`, `Expanded`, `Full` and `peek(…)` are values rather than an enum entries
list, so an app can add its own — [the sheet guide](sheets.md#the-model)
explains why that matters and what `peek` measures.

**Both ends give.** Pulled above its tallest detent the sheet follows the finger
a little way, each pixel buying less than the last, and springs back when the
finger lifts — a sheet that stops dead at a boundary is a gesture that has
stopped answering. The stretch is drawn and nothing else: no anchor, no detent
and nothing the caller sees knows it happened.

**A sheet whose bottom is held rather than open does the same thing downward.**
That is `dismissible = false`, here as well as on
[`ModalBottomSheet`](modal-bottom-sheet.md): a drag past the lowest resting detent
stretches and springs back instead of putting the sheet away. `Hidden` stays in
the anchors, so the app can still close it with `state.hide()` — what the flag
refuses is the *user* doing it. On a modal sheet it also closes the tap outside
and the back gesture, neither of which a plain sheet has.

A sheet at [`SheetDetent.Full`](sheets.md) is the exception, and only to the
*drawing*: it already fills its container, so moving it up would lift its bottom
edge off the bottom of the screen and show a band of background under it. It
still absorbs the pull, which it did not used to — a full-height sheet that did
nothing with an upward drag left the gesture live, so the couple of dozen pixels
a finger travels back down as it leaves the glass read as a flick and dropped the
sheet a detent.

---

## `part` — content that belongs to the sizes that can show it

A sheet that collapses to a bar and opens to half a screen is showing two
different things, and writing that as a `when` over the current detent is two
code paths that have to agree. **A part declares which sizes it is for instead.**

```kotlin
BottomSheet(state) {
    part { StopHeader(stop) }
    part(from = SheetDetent.Half) { Departures() }
}
```

`from = null` is a part that is there at every size. It is worth writing anyway:
it puts every piece of the sheet in the same shape, and makes the ones further
down read as the ones you have to drag for.

### Nothing appears, and that is the point

**A part is laid out in place, at its full height, whatever the sheet is doing.**
What hides it is the sheet's own bottom edge — a sheet is a column pinned to the
top of a card, the card is only so tall, and a part further down the column is
already drawn below what the card shows. Dragging the sheet up uncovers it at
exactly the speed of the finger. Nothing fades in, nothing is composed on the
frame the gesture starts, and nothing can arrive late.

Two earlier versions appeared instead, and both were reported as appearing: one
composed the part when the sheet settled at its detent, the other composed it
always and revealed it with a clipped height once the drag committed. The third
answer is to stop revealing anything.

**So order the content the way it is read.** The sheet hides its content at the
bottom, so a part that belongs to a taller detent goes *below* the parts that are
always shown — which is the order a header and its details are written in anyway.
A gated part written above them is above the fold, and will be visible whatever
its `from` says.

What `from` does decide is whether the part is in the **assistive tree**: off the
bottom of the window a part cannot be seen or tapped, and it is not read out
either.

The content lambda's receiver is a `SheetContentScope`, which is a `ColumnScope`
with `part` and `pinned` added — so `weight`, `align` and everything else a
column offers are unchanged, and content written before this existed reads and
behaves the same.

---

## `pinned` — a part that stays at an edge, with the content passing behind

<!--sample:BottomSheetPinnedSearch-->
```kotlin
val sheet = rememberSheetState(
    detents = listOf(SheetDetent.pinned(), SheetDetent.Half, SheetDetent.Expanded),
    initialDetent = SheetDetent.pinned(),
)
val query = rememberTextFieldState()

BottomSheet(state = sheet) { padding ->
    // On the bottom of what the sheet shows at every size, over the list
    // and on the sheet's own colour, with the sheet's inset round it.
    // Collapsed, it is all there is: the search, and the account beside it.
    pinned {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, placeholder = "Search stops", modifier = Modifier.weight(1f))
            IconButton(onClick = {}, icon = Tabler.Outline.User, contentDescription = "Account")
        }
    }
    // The padding has grown by the field's height, so the last row
    // scrolls out from behind it. At the sides, the sheet's inset, the
    // same as the field's.
    LazyColumn(
        modifier = Modifier.padding(horizontal = BottomSheetDefaults.ContentPadding),
        contentPadding = padding,
    ) { stopRows() }
}
```

Asked for as *"a way to pin a component to certain parts of the sheet… a
[search] bar to the bottom of the sheet. Content should be scrollable behind it,
and when the sheet is collapsed, only the search bar should show."* And asked for
again, separately, for a map: a search field with an account button beside it,
in reach at every height the sheet is at, which is the example above.

**`SheetPin.Bottom`, the default, rides the bottom of what the sheet shows.**
That is the window's edge for an edge sheet and the sheet's own for a floating
one, above the safe area, at every detent and on every frame of a drag. Pulled
down past the point where it would cover the handle, it stops there and leaves
with the sheet. **`SheetPin.Top` sits under the drag handle**, where the content
starts.

**It is drawn over the content, with the sheet's inset round it.** The sheet pads
it by `BottomSheetDefaults.ContentPadding`, 16dp, at the sides and below, and the
drag handle's 24dp band above is the space over it. A sheet collapsed to its
search field shows the field with the handle in the middle of the band above it
and 16dp beside and below it. A top part moves in from a display's rounder
corners as the header does; see [`SheetHeader`](sheet-header.md#the-space-round-it).

**On the sheet's own colour by default**, so whatever scrolls behind it is hidden
rather than showing through. Once a bottom part has risen off the band, it has the
inset in the sheet's colour above it too, so the content never meets it edge to
edge. The colour runs the full width and, at the bottom, down through the safe
area. At high contrast a line marks the edge the content passes behind, where the
two grounds would otherwise be the same colour.

**`containerColour = Color.Transparent` lets the content go under it** and show
round it, with only the part itself — a search field's own ground — drawn over
the rows. Asked for as *"the ability for the content to go under the pinned
item"*. The padding is the same either way, so the last row can still be scrolled
clear of the part. Any other colour is drawn where the sheet's would be.

**The padding the content is handed grows by the pinned parts' heights**, and by
the inset above a bottom part. A `LazyColumn` given it as `contentPadding` scrolls
its first and last rows out from behind them, just as it does for the safe area. Content that ignores the padding
runs under them. The padding has no inset at the sides, so rows that should line
up with the pinned part take `BottomSheetDefaults.ContentPadding` there
themselves, as the example does.

**`SheetDetent.pinned()` shows the pinned parts and nothing else**: the handle,
whatever is pinned to the top, and whatever is pinned to the bottom with the
safe area under it. That is the collapsed sheet that is only a search field, at
whatever height the field turns out to be at the reader's font scale. Until the
parts have been measured, and on a sheet with none, it uses its `fallback`.

Where `pinned` is written in the content does not decide where it is drawn.
Several at one edge stack in the order they were first composed. A pinned part is
always on screen, so it is always in the assistive tree, and read after the
content.

---

## `presentation` — a drawer out of the screen, or a panel over it

**`BottomSheet` floats by default.** `SheetPresentation.Floating` lifts the sheet
off all three edges and rounds every corner. It reads as a panel *over* the screen rather
than a drawer pulled out of it, and it can shrink to the size of a control without
looking broken — **a bar-height sheet flush to the bottom of the window reads as a
drawer that failed to open**, and the same thing floating reads as a search field.
And expanded, it stops floating: see below.

`SheetPresentation.Edge` is flush to the bottom and to both sides, top corners
rounded, bottom corners square because there is no bottom edge to round. **It is
the default for [`ModalBottomSheet`](modal-bottom-sheet.md).** A modal sheet recedes
the page behind it into a frame of its own, and a floating panel in front of that
is two frames around one thing; tried on a phone, it did not feel right. Ask for
`Edge` by name on a `BottomSheet` that should read as a drawer out of the window
at every size, or for `Floating` on a modal one. The side sheets are always flush
to their edge.

```kotlin
val search = rememberSheetState(
    // No `Hidden`, so there is nowhere to be swiped away to.
    detents = listOf(SheetDetent.height("bar", 64.dp), SheetDetent.Half),
    initialDetent = SheetDetent.height("bar", 64.dp),
)

BottomSheet(state = search) {
    TextField(state = query, placeholder = "Search stops")
}
```

**Collapsing instead of dismissing is a detent question, not a presentation
one.** A sheet's anchors come from its own detent list, so one with no
`SheetDetent.Hidden` in it has no anchor to be dragged away to, and stretches
past its lowest detent exactly as it does above its top. That works at either
presentation; `presentation` only decides what it looks like while it does. Both
halves are checked on every build.

The margin is `Theme.componentDefaults.sheetFloatingInset`, unioned with the window insets
rather than added to them — it is a *minimum* clearance, so on a phone with a
24dp gesture bar the sheet floats 24dp up and not 36. The same line is what lifts
a floating search field above the keyboard, since the sheet's insets carry the
IME.

**Only the part of an inset that reaches the sheet's container counts**, the rule
an [anchored overlay](../overlays.md) already keeps. An app whose root has made room
for the keyboard — `imePadding()`, a scaffold padded by `safeDrawing`, a window
that resized — ends the container where the keyboard begins, and the sheet lifts
no further. It used to lift by the whole keyboard again, and stood a keyboard's
height above it. The same goes for every edge: a container below the status bar
gives the sheet's chrome nothing to clear at the top. A dialog, a side sheet and a
toast keep the same rule.

One double lift is out of the library's sight: an Android window that *pans* for
the keyboard moves everything up, sheet included, and reports the keyboard as
well. Set `android:windowSoftInputMode="adjustResize"` on the activity, as
[`TextField`](text-field.md) asks; a debuggable app whose window pans, or has not
said, gets one line in its log saying so.

One cost, and it is structural. An edge sheet's surface is as tall as its
container at every detent and merely translated, so its size never changes and
its shadow is rasterised once; the surplus hangs off the bottom of the screen
where nothing sees it. A floating sheet has a bottom edge that *is* seen, so it
is exactly as tall as it is visible and that changes on every frame of a drag.
Two edges that both move cannot be drawn by a box that never resizes.

**The margin is given back on the way out.** Below the lowest detent that is
somewhere to *be*, the sheet is leaving and there is nothing under it but hidden,
so the margin shrinks to zero and the sheet lands on the window's edge as it
goes. It used to keep the full margin at every offset, which meant a closing sheet
had a strip of background under it the whole way down and then vanished with the
strip still there — reported as the sheet staying floating while it closed.
Because the pay-back is linear in the sheet's visible height rather than timed, a
sheet dragged down by hand gives it back at the speed of the hand and one let go
gives it back at the speed of the spring, with no second animation to keep in
step.

### Expanded, a floating sheet becomes an edge sheet

A floating panel is right at a sheet's smaller sizes and wrong at its largest: a
sheet pulled up to fill the screen *is* the screen, and a screen with a strip of
background round three sides and rounded corners at the bottom reads as a card
that has been stretched. So **across the last step of its travel** — from the
resting detent just below the top, to the top — a floating sheet morphs into the
edge sheet it would otherwise have been. Its margins close up, its corners become
the edge sheet's, and it arrives as one exactly as it settles.

**The edges it reaches are the ones the edge sheet reaches**, which is what makes
it right on every size of device without a rule per size:

| Window | Where the expanded sheet ends up |
|---|---|
| Phone, portrait or landscape | The bottom and both sides, top corners concentric with the display's |
| Tablet or desktop, wider than the 480dp cap | The bottom only, at the cap's width, wherever `alignment` put it |
| Cutouts, side insets, a gesture bar | The surface reaches the window's edge and the **content** is handed the insets, exactly as an edge sheet's is |

It follows the sheet's position rather than a clock, like the margin paid back on
the way out: held halfway through the step by a finger, the sheet is halfway
there, and let go it finishes at the speed of the spring carrying it.

```kotlin
BottomSheet(
    state = sheet,
    // The default: the last step, into the edge sheet's shape.
    edgeMorph = SheetEdgeMorph(),
    // Or finish earlier, from the bar up to Half:
    // edgeMorph = SheetEdgeMorph(from = bar, until = SheetDetent.Half),
    // Or never: a floating panel at every size.
    // edgeMorph = null,
) { … }
```

`expandedShape` is what `shape` becomes, and defaults to the edge sheet's. Corners
and the squircle's curve interpolate when both are corner-based shapes.

**A sheet with only one detent it can rest at** — a floating `ModalBottomSheet`
on its default `[Hidden, Expanded]` — has no step to morph across, so **where it
rests decides**: content tall enough to reach the top is an edge sheet from the
first frame of opening to the last of closing, and short content floats the whole
way. The top is where a full-height sheet actually stops: the top of the window.
Content that rests within `nearTop` (64dp) of it is partly morphed, which is the
edge of the rule and why the distance is short; say `from` to morph over a step
of your own. It used to follow the live position over that last 64dp, and a tall
sheet opened floating and changed shape in its final frames — reported as the
sheet "snapping" open near the top.

**The content keeps its floating width.** As the sheet widens by its side margins
the surface grows out around the content and the content stays where it was, so
an expanded sheet's content sits a margin further in than an edge sheet's would.
That is the price of not re-flowing in the middle of a drag: a line that wraps
differently at each width changes the sheet's height, and its detents with it,
while a finger is on it — the other half of the reported snap. It also means
nothing is re-measured across the step.

---

## `alignment` — where a sheet sits once the window is wider than it

A sheet stops at 480dp, a side sheet's width, so on a wide window the two kinds of
sheet are one width. It stopped at 640dp until a modest desktop showed that as half
the screen. On a phone 480dp is wider than the screen, so the sheet is the screen
and nothing in this section applies: **a phone never sees any of it.**
On a tablet in landscape or a desktop window the sheet is a panel with room either
side of it, and `alignment` says where the panel goes — `Center` by default, or
against the `Start` or `End` edge. Start and end follow the layout direction, so an
end-aligned sheet moves to the left in a right-to-left locale on its own.

```kotlin
BottomSheet(
    state = sheet,
    alignment = OverlayAlignment.End,
    floatingControlsAlignment = OverlayAlignment.Start,
    floatingControls = { RecentreButton() },
) { … }
```

**The cap is older than the parameter, and did not work until the parameter
arrived.** The width was always meant to stop at a cap; the sheet was built as
`fillMaxWidth()` and then `widthIn(max = …)`, and `fillMaxWidth` fixes the
minimum width at the window's, which a later maximum cannot lower. So every sheet on
every desktop spanned the whole window. The fix is the other order — cap, then fill
— and a capped sheet then has to sit *somewhere*, which is what this decides.

`OverlayAlignment` is the same type a `Popover`, a `DropdownMenu` and a `Select` use
for the same question: how something lines up along the edge it sits on.

**`floatingControlsAlignment` is relative to the sheet, not the window.** The
controls row is exactly the sheet's width and stands exactly over it, so
`OverlayAlignment.Start` puts a recentre button over the sheet's own left corner —
which, on an end-aligned sheet in a wide window, is nowhere near the window's left
edge. It is `End` by default, which is where the controls always were.

**Do not align the sheet with a modifier.** `Modifier.align(Alignment.BottomCenter)`
on a sheet's `modifier` looks natural inside a `Box` and is wrong twice over: the
sheet places itself against the top of its container and moves down by its own
offset, so aligning it to the bottom as well counts its height twice, and it would
override `alignment` besides. The sheet fills whatever it is put in; that is all it
needs from you.

---

## Accessibility

Pass **`paneTitle`**. The sheet sets it as pane semantics, which is how a screen
reader says what the region that just appeared *is* — without it the user is told
a new area exists and not what it holds. It is optional in the signature because
a sheet whose first child is a titled [`SheetHeader`](sheet-header.md) already
says so; it is not optional in any other case.

A non-modal sheet leaves the content behind it reachable, which is the whole
reason to choose one — so nothing here traps focus, and the reader can move
between the map and the sheet freely.

The detents are reachable without the gesture: [`DragHandle`](drag-handle.md)
exposes `expand` and `collapse` as semantic actions, so the sheet can be resized
from a screen reader's rotor rather than only by dragging.

At [high contrast](../accessibility.md#what-high-actually-changes) the sheet
takes an edge in the scheme's `outline`, since its raised ground is otherwise
barely distinct from the page; nothing else about it changes.

Under [reduced motion](../tokens.md#motion) a sheet the app opens or closes —
`show()`, `hide()`, `animateTo` — fades in at its detent and out where it rests
instead of sliding, and a move between two open detents takes the new height at
once. A drag is untouched: the sheet follows the finger at every setting.
