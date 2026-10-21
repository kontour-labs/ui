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
tabs after it sliding along with it. A closing tab keeps the look and the
width it had, its × included, fading as it narrows and cut where it ends. `onClose` is called at once: the tab is
drawn shrinking after it has gone from the caller's list, so the page under it
can change straight away. Under reduced motion tabs appear and go at once.

### Dragging

Held and dragged along the strip, a tab slides the others aside, a detent felt
as it passes each place, and lands where it is let go. It takes a neighbour's
place once it covers a little under half of it, so the second tab can be put
before the first. A mouse picks it up as it moves; a finger holds it first, as
a scrolling strip needs.

A tab rides its strip, as a browser's does: it stays on the strip, flat and
as it looks there, following the pointer along it, and goes no further than
the first tab's start or the last tab's end, so never over the "+".

### Between strips

With `movesBetweenStrips`, a tab can also leave its strip. Pulled down, it gives
a little towards the finger, then comes away, lifting off the page with its
bottom corners rounding, and follows the pointer anywhere. Carried into another
strip of the same `DragType` that has it on too, it settles onto that strip,
square again, and rides it as one of its own; dropped there, `onDrop` on that
strip's state is told where it came from, as a card moved between a board's
lists is. Two panes of a split view each with its own strip take each other's
tabs this way.

It is off by default, and is worth turning on only where there is more than one
strip. Off, a tab never leaves its strip however far it is pulled, a screen
reader is offered no "Move to" another strip, and the strip takes no other
strip's tabs.

Picking a tab up by pointer selects it, as a browser does.

Nothing can be carried without an [`OverlayHost`](../overlays.md) round the
app, which draws the carried tab. Without one the tabs still select, close,
open and scroll, and move by keyboard and by screen reader.

### Menus

<!--sample:DocumentTabsMenus-->
```kotlin
val papers = remember { mutableStateListOf(Paper(1, "Timetable"), Paper(2, "Fares"), Paper(3, "Route map")) }
val closed = remember { mutableStateListOf<Paper>() }
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

// A right click on a tab, or a finger held still on it, opens its menu
// with that tab; the "+" has one of its own. `item` closes the menu as it
// runs. A middle click still closes a tab: `closeOnMiddleClick = false`
// leaves the button to the app.
DocumentTabs(
    state = tabs,
    value = open,
    onValueChange = { open = it },
    onClose = { closing ->
        papers.remove(closing)
        closed.add(closing)
        if (open == closing) open = papers.lastOrNull()
    },
    onAdd = { open = Paper(next++, "Untitled").also(papers::add) },
    tabMenu = { paper ->
        item("Close others", enabled = papers.size > 1) {
            closed.addAll(papers.filter { it != paper })
            papers.retainAll { it == paper }
            open = paper
        }
    },
    addMenu = {
        item("Reopen closed tab", enabled = closed.isNotEmpty()) {
            open = closed.removeAt(closed.lastIndex).also(papers::add)
        }
    },
)
```

A browser's tabs and its "+" each have a menu, and so can these: `tabMenu` is
given the tab it was opened on, and `addMenu` is the "+"'s. Both are built
with a `MenuScope`, as a [`DropdownMenu`](../overlays.md)'s content is, so
`item` closes the menu as it runs. The strip has no menu of its own, and
without either there is none.

A right click opens the menu at the pointer, and does not choose the tab, as a
browser does not: the tab being read stays open while the others are closed
from behind it. On a touchscreen a finger held still opens it. A tab held is
also picked up, as ever, and its menu opens as it lifts; let go there, the
menu stays and nothing is chosen, and moved on instead, the finger carries the
tab, chosen, and the menu closes, as a context menu on iOS gives way to a
drag. The "+" has nothing to pick up, so a hold on it is only its menu, and
not also a press.

Like any menu, these need an `OverlayHost` round the app, and a disabled strip
opens neither.

### Middle click

A middle click closes a tab, as it does in a browser, wherever there is an
`onClose`. `closeOnMiddleClick = false` turns that off, and the click goes on
to whatever is under the strip, for an app that gives the button a meaning of
its own.

## Accessibility

The tabs are `Role.Tab` in a `selectableGroup()` with their places in it, so a
screen reader says "Fares, tab, 2 of 3" and which is selected. Each tab offers
its moves as actions — "Move left" and "Move right", the other way round right
to left, and, with `movesBetweenStrips`, "Move to" each other strip that would
take it — "Close" and its name, and with a `tabMenu`, "More options", which
opens it. The "+" offers its menu as "More options" too.

The strip is one stop for the Tab key, on the selected tab. The arrow keys move
between tabs, Home and End go to either end, Enter and Space select, and
Delete closes the focused tab and hands the focus to the tab after it, or at the
end to the one before. Alt with an arrow picks the tab up to carry it,
the arrows then move it, and Enter puts it down or Escape puts it back.
Shift+F10, or the menu key, opens the focused tab's menu, or the "+"'s, below
it. A tab's
× is named for its tab, "Close Fares", and pressed by pointer; it is not a stop
of its own, since Delete and the "Close" action do the same.

A middle click closes a tab, as it does in a browser, unless
`closeOnMiddleClick` is off. Ctrl+W, or Cmd+W, is the app's to give: the strip
does not take a key the app may already use.

At the high contrast tier the strip's line and the selected tab's edge are
drawn in the tier's edge colour, and a cut title is cut hard rather than faded.
Under reduced motion tabs do not grow or shrink, the strip does not slide to
the selected tab, and a carried tab does not ease back onto a strip.
