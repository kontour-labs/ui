# `DocumentTabs`

<!--sample:DocumentTabsBasics-->
```kotlin
// Open documents as tabs: closed by their ×, opened by the "+", dragged
// into another order, and the open one joined to its page below.
val papers = remember { mutableStateListOf(Paper(1, "Timetable"), Paper(2, "Fares"), Paper(3, "Route map")) }
var open by remember { mutableStateOf<Paper?>(papers.first()) }
var next by remember { mutableIntStateOf(4) }
val tabs = rememberDropListState(
    listState = rememberLazyListState(),
    type = PaperTab,
    items = papers,
    key = { it.id },
    label = "Open documents",
    itemLabel = { it.title },
    onDrop = { drop ->
        papers.remove(drop.payload)
        papers.add(drop.index.coerceAtMost(papers.size), drop.payload)
    },
)

Column {
    DocumentTabs(
        state = tabs,
        value = open,
        onValueChange = { open = it },
        onClose = { closing ->
            papers.remove(closing)
            if (open == closing) open = papers.lastOrNull()
        },
        onAdd = {
            val untitled = Paper(next++, "Untitled")
            papers.add(untitled)
            open = untitled
        },
    )
    Surface(Modifier.fillMaxWidth().height(160.dp)) {
        Text(open?.title ?: "Nothing open")
    }
}
```

Open documents as a browser or an editor shows them: one tab selected and
joined to the page below it, the others flat on the strip between hairlines,
each with a ×, and a "+" after the last for another. Its tabs are things the
reader opened and will close, which is what sets it apart from
[`TabBar`](tab-bar.md), whose tabs are fixed views of one screen.

The strip is a [drop list](../drag-and-drop.md): `state` is a
`rememberDropListState` over the caller's list, which stays the one source of
truth. Nothing moves in it until `onDrop` moves it, and nothing else of the drop
list's goes on anything: the strip is its list.

### Joined to the page

The selected tab is drawn in `colours.selectedContainer`, the theme's `surface`
by default, with feet that curve out into whatever is under the strip. Put the
document directly under the strip on the same colour and the tab and the page
read as one sheet; the other tabs sit on the darker `container`.

At the high contrast tier the strip stands on a line, and the selected tab's
edge runs up from the line, round the tab and back down to it, with nothing
under it: the tab is open to its page.

### Many tabs

Tabs share the strip's width, no wider than `maxTabWidth` and no narrower than
`minTabWidth`. Past that many, the strip scrolls, fading at whichever end has
more, with a scrollbar along its top, and the selected tab is scrolled into
sight whenever it changes. A cut title fades out rather than ending in an
ellipsis, which in a narrow tab would be a letter's room.

The × shows on every tab wide enough for it, and on the selected and the
hovered tab however narrow. Closing a tab with the mouse keeps every other tab
at its width until the pointer leaves the strip, so the next tab's × comes under
the pointer and a row of tabs can be closed with one click after another.

### Opening and closing

A tab grows in from nothing as it opens and shrinks away as it closes, the
tabs after it sliding along with it. `onClose` is called at once: the tab is
drawn shrinking after it has gone from the caller's list, so the page under it
can change straight away. Under reduced motion tabs appear and go at once.

### Dragging

Held and dragged along the strip, a tab slides the others aside, a detent felt
as it passes each place, and lands where it is let go. A mouse picks it up as
it moves; a finger holds it first, as a scrolling strip needs.

A tab rides its strip, following the pointer along it but not off it, until
the pointer is pulled clear of the strip, when it comes away and follows the
pointer anywhere. Carried into another strip of the same `DragType`, it rides
that one, and dropped there, `onDrop` on that strip's state is told where it
came from, as a card moved between a board's lists is. Two panes of a split
view each with its own strip take each other's tabs this way.

Picking a tab up by pointer selects it, as a browser does.

Nothing can be carried without an [`OverlayHost`](../overlays.md) round the
app, which draws the carried tab. Without one the tabs still select, close,
open and scroll, and move by keyboard and by screen reader.

## Accessibility

The tabs are `Role.Tab` in a `selectableGroup()` with their places in it, so a
screen reader says "Fares, tab, 2 of 3" and which is selected. Each tab offers
its moves as actions — "Move left" and "Move right", the other way round right
to left, and "Move to" each other strip that would take it — and "Close" and
its name.

The strip is one stop for the Tab key, on the selected tab. The arrow keys move
between tabs, Home and End go to either end, Enter and Space select, and
Delete closes the focused tab. Alt with an arrow picks the tab up to carry it,
the arrows then move it, and Enter puts it down or Escape puts it back. A tab's
× is named for its tab, "Close Fares", and pressed by pointer; it is not a stop
of its own, since Delete and the "Close" action do the same.

A middle click closes a tab, as it does in a browser. Ctrl+W, or Cmd+W, is the
app's to give: the strip does not take a key the app may already use.

At the high contrast tier the strip's line and the selected tab's edge are
drawn in the tier's edge colour, and a cut title is cut hard rather than faded.
Under reduced motion tabs do not grow or shrink, the strip does not slide to
the selected tab, and a carried tab does not ease back onto a strip.
