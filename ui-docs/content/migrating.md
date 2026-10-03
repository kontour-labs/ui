# Migrating

**Moving an app to a newer release: what to change, with the code before and
after.** The [changelog](changelog.md) lists everything each release changed.
This page is the part of it an app has to act on. Newest first.

The library is before 1.0, and a release can break its API. Nothing old is kept
as a deprecated alias, so the compiler finds every call site that has to change,
and this page says what to write there. What the compiler cannot find is a
change that still compiles but looks or behaves differently. Each section lists
those under **Check**.

**Across several releases,** take the sections in turn, oldest first, starting
from the version the app is on.

---

## 0.3 to 0.4

From 0.3.0 to 0.4.0, released on 3 October 2026. One parameter is replaced,
and a few things look or behave differently enough to check.

### Before you start

- **Move all three modules at once.** `ui`, `ui-nav3` and `haptics` are one
  version: in a version catalog, `kontour = "0.4.0"`. See
  [Installing](installing.md).
- **Rebuild anything compiled against 0.3.** Functions that took a new parameter
  changed their signature — a chip's content is a `ChipScope` now, `TopBar`
  takes an `image` and an `expandedHeight` for its `Hero` style, and `Strings`
  has four more words — and a library of your own built against 0.3
  fails when it calls one, until it is rebuilt. Source that calls them compiles
  as it was.

### `markerDraggable` is now `markerDrag`

`NavBar(markerDraggable: Boolean)` is replaced by
`markerDrag: NavBarMarkerDrag`, which also says when a drag chooses a
destination. On `NavigationSuiteScaffold` it is `barMarkerDrag`.

```diff
- NavBar(markerDraggable = false) { … }
+ NavBar(markerDrag = NavBarMarkerDrag.Off) { … }

- NavigationSuiteScaffold(…, barMarkerDraggable = false) { … }
+ NavigationSuiteScaffold(…, barMarkerDrag = NavBarMarkerDrag.Off) { … }
```

`markerDraggable = true`, the default, is `NavBarMarkerDrag.SelectOnRelease`,
and still the default. `NavBarMarkerDrag.SelectWhileDragging` is new: each
destination is chosen as the marker reaches it.

### Check: the nav bar with labels

A bar with `showLabels = true` is about 18dp shorter: a floating one is 64dp with
labels as without. The icon is 20dp, not 24. A screen that reserved room for the
bar by a fixed number has more than it needs.

### Check: disabled controls

A disabled switch's, slider's and range slider's thumbs, and a knob's face, are
the page's colour now, and a disabled knob is grey rather than its own colours
faded. A `Checkbox`, `RadioButton` or `Switch` in a disabled `SelectionRow` or
`ListItem` draws itself disabled without being told.

### Check: tall swipe rows

A swipe action on a row taller than 64dp is 64dp tall and centred, not as tall
as the row. Pass `maxActionHeight = Dp.Infinity` to keep the old look.

### Check: a gapless `StepProgress`

`StepProgress(gap = 0.dp)` draws one continuous bar now. It used to draw the
segments end to end, pinched at each join.

### Check: a sheet at full height

A sheet at `SheetDetent.Full`, or at `Expanded` with content taller than the
window, reaches the top of the window and draws under the status bar; its handle
and header stay below the bar. It used to stop 12dp short. A screenshot test of
a full sheet changes by that strip.

### Check: a back button shown by the back stack's size

A `TopBar(onBack = if (backStack.size > 1) … else null)` shows its button on the
root page while the page over it is swiped away: the stack holds both until the
gesture ends. Pass `onBack = pageBack()` instead.

### Check: a field with focus when a modal opens

A dialog or a modal sheet opening takes the focus off a text field on the page,
or in an overlay under it, and on a phone its keyboard goes. A screen that
opened a dialog to ask about what was being typed, and expected the field to
keep the focus, has to give it back when the dialog closes. A menu or a
combobox's list leaves the field it opened from focused, as before.

### Check: overlays in a container short of the window

A sheet, a side sheet, a dialog and a toast count only the part of each inset
that reaches their container, measured from the window's edge. One in a
container that already ends at the keyboard, or starts below the status bar, is
no longer padded by them again. A `windowInsets` of your own is read the same
way: `WindowInsets(bottom = 80.dp)` is 80dp from the window's bottom edge, and
counts for nothing in a container that ends 80dp above it.

---

## 0.2 to 0.3

From 0.2.2 to 0.3.0, released on 30 September 2026. One component is removed,
seven take new parameters, and a few behaviours changed that an app may have
been built around.

### Before you start

- **Move all three modules at once.** `ui`, `ui-nav3` and `haptics` are one
  version: in a version catalog, `kontour = "0.3.0"`. See
  [Installing](installing.md).
- **Rebuild anything compiled against 0.2.** Eight functions changed signature,
  listed below. A library of your own that calls one of them and was built
  against 0.2 fails when it runs, on every platform, until it is rebuilt against
  0.3.0.

### `SettingRow` is now `SelectionRow`

`SettingRow` is removed. `SelectionRow(onClick = …)` takes the same content:
`+"Theme"`, `leading {}`, and the current value in `trailing {}`. It is the same
silent `Role.Button`, and `onClick = null` still shows a value that cannot be
changed there.

```kotlin
// 0.2
SettingRow(onClick = { openThemes() }) {
    +"Theme"
    leading { +Tabler.Outline.Moon }
    trailing { +"Match system" }
}

// 0.3
SelectionRow(onClick = { openThemes() }, position = GroupPosition.Only) {
    +"Theme"
    leading { +Tabler.Outline.Moon }
    trailing { +"Match system" }
}
```

**One default differs.** `SettingRow` drew the sunken card on its own, with
`position = GroupPosition.Only`. `SelectionRow`'s `position` defaults to `null`,
which is a bare row. Pass `GroupPosition.Only` to keep the card, as above.

| 0.2 | 0.3 |
|---|---|
| `SettingRow(onClick = …) { … }` | `SelectionRow(onClick = …, position = GroupPosition.Only) { … }` |
| `SettingRow { … }`, a value that cannot be changed | `SelectionRow(onClick = null, position = GroupPosition.Only) { … }` |
| `SettingRow(position = p, onClick = …) { … }` | `SelectionRow(onClick = …, position = p) { … }` |
| A `SettingRow` with a `Switch` in it | `SelectionRow(selected, onSelectedChange, role = Role.Switch) { … }` |
| `setting(…)` in a `ListSection` | Unchanged |

See [`SelectionRow`](components/selection-row.md#a-row-that-opens-a-choice).

### Parameters added partway through a list

Seven functions have new parameters in the middle of their lists. **A call that
names its arguments is unaffected**, and that is how the library's own examples
are written. A call that passes a later argument by position stops compiling at
that argument, with a type mismatch. Name the argument and it compiles again. No
call compiles to something different: each new parameter's type differs from
the argument that used to be in its place.

| Function | New parameters | A positional argument that now fails |
|---|---|---|
| `PageIndicator` | `orientation`, after `style` | `activeColour`, and those after it |
| `SwipeActions` | `actionShape`, after `shape` | `actionWidth`, and those after it |
| `SwipeToDismiss` | `actionShape`, after `shape` | `fullSwipeConfirmation` |
| `SelectionRow(selected, onSelectedChange, role)` | `position`, after `enabled` | `interactionSource` |
| `NavBar` | `shape`, `itemWidth`, `marker` and `markerDraggable`, after `arrangement`; `action`, after `windowInsets` | `windowInsets` |
| `NavigationSuiteScaffold` | `barShape`, `barItemWidth`, `barMarker` and `barMarkerDraggable`, after `barStyle` | `showLabels`, and those after it |
| `TopBar` | `collapseState`, after `collapseProgress` | `windowInsets` |

### Check: a `Large` `TopBar` over a list

A `Large` bar now collapses by carrying its title into the bar, and the bar
shrinks as it goes. So a list laid out under the bar moves as it shrinks. A bar
driven by that list's own scroll, `collapseProgress(listState)`, then carries the
rows twice as far as the finger. Give the scroll to the bar instead:

```kotlin
// 0.2
val list = rememberLazyListState()
Column {
    TopBar(style = TopBarStyle.Large, collapseProgress = collapseProgress(list)) {
        +"Perth Underground"
    }
    LazyColumn(state = list) { stopRows() }
}

// 0.3
val collapse = rememberTopBarCollapseState()
Column(Modifier.nestedScroll(collapse.nestedScrollConnection)) {
    TopBar(style = TopBarStyle.Large, collapseState = collapse) {
        +"Perth Underground"
    }
    LazyColumn { stopRows() }
}
```

The bar takes the scroll until it has collapsed, and the list takes the rest. A
bar drawn *over* its list, rather than above it, does not change the list's
room, and keeps `collapseProgress(listState)`. See
[`TopBar`](components/top-bar.md#large-and-how-it-collapses).

### Check: the space round a sheet's content

The drag handle's 24dp band is the content's top padding, as it was, and a
`SheetHeader` under it no longer adds padding of its own, or its close button's
empty space, on top of it. Its title is 16dp higher than in 0.2. Beside it and
below it is the sheet's inset, `BottomSheetDefaults.ContentPadding`, 16dp.
Before, a collapsed header had 44dp above its title and 16dp beside it.

- **A header is 20dp shorter.** A peek set with `sheetPeekAnchor` follows on its
  own. A peek written as a number shows more of what is under the header.
- **A `SheetHeader` padded by its container is now padded twice.** Make it the
  first thing in the sheet's content, and pad what follows it instead, as below.
- **A header of your own** wants `BottomSheetDefaults.ContentPadding` at its
  sides and below, and nothing above: the handle's band is the space above it.

```kotlin
// 0.2: the column's padding reached the header too
ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
    Column(Modifier.padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm)) {
        SheetHeader { +"Display settings" }
        DisplayControls()
    }
}

// 0.3: the header keeps the sheet's inset itself
ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
    SheetHeader { +"Display settings" }
    Column(Modifier.padding(horizontal = BottomSheetDefaults.ContentPadding)) {
        DisplayControls()
    }
}
```

See [`SheetHeader`](components/sheet-header.md).

### Check: a sheet's width on a wide window

A bottom sheet is at most 480dp wide, from 640dp, which was half of a modest
desktop. To keep the old width everywhere:

```kotlin
KontourTheme(componentDefaults = ComponentDefaults(sheetMaxWidth = 640.dp)) { … }
```

### Check: swipe actions in a group

A row's actions are drawn in `actionShape`, a lone row's corners by default,
where before they took the row's `shape`. In a group, a middle row's actions
came out nearly square. If your rows have corners of their own, pass the same
shape as `actionShape`.

### Check: the nav bar

- **The destinations share the bar in equal cells**, and the marker is round the
  whole current destination, glyph and label, in the bar's shape. In 0.2 each
  destination was a circle with the marker behind its glyph. To keep that:
  `NavBar(itemWidth = NavBarItemWidth.Natural, marker = NavBarDefaults.glyphMarker())`,
  or `barItemWidth` and `barMarker` on `NavigationSuiteScaffold`.
- **The marker can be dragged** from destination to destination. The one under
  the finger when it lifts has its `onClick` run once, and a tap is still a tap.
  `markerDraggable = false` turns it off; on `NavigationSuiteScaffold`, it is
  `barMarkerDraggable = false`.
- **A tap on the bar's surface stays on the bar.** Before, a tap between
  destinations reached whatever was underneath.
- **A floating bar's ends match the gaps between its destinations.** A bar given
  its own `arrangement` keeps the old padding.
- **`NavigationSuiteScaffold`'s `action` sits beside a floating bar,** not
  inside it. A docked bar keeps it at the end.

### Check: something tapped through a surface

A `Surface` now stops a tap from reaching anything drawn *beneath* it, and the
bars also stop a press on their blank parts from reaching a clickable container
round them. That was always the intent, and fixes taps that went through a top
bar or a card. If a layout relied on the old behaviour — a decorative surface
drawn over a control and expected to let taps through — draw it under the
control instead, or make it a plain `Box` with `Modifier.background`.

### Nothing to do

These changed too, and ask nothing of an app:

- A `RangeSlider` takes a finger on each thumb.
- On Android, a back gesture finishes at the speed the finger let go at.
- A long press keeps working after the input switches between mouse, keyboard
  and touch.
- A trackpad or a mouse can make a full swipe on a `SwipeActions` row. It runs
  the row's outermost action, as a drag past the point of no return does. If
  that action deletes something, it already needed an undo.

---

## 0.1 to 0.2

0.1.0 was published by hand and never tagged, and nobody built on it. The guide
for that move is kept in the repository's history:
[Migrating, 0.1.0 to 0.2.0](https://github.com/kontour-labs/ui/blob/7717dec541b87a794769a49af4c0e14d9bff2c88/ui-docs/content/migrating.md).
It lists every rename, reorder and changed type.

0.2.1 and 0.2.2 broke nothing.
