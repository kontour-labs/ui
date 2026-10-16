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

## 0.4.5 to 0.5.0

Unreleased, and growing. `SystemIcons` is the theme's now, so code that named
one of its glyphs changes; `Strings` has new fields, defaulted and last.
Charts are a new module, which an app takes only if it wants them.

### `SystemIcons.Close` is `Theme.icons.close`

The library's own glyphs are read from the theme, so that an app can replace
them. Each is a property of a `SystemIcons`, in lower camel case, and
`DatePickerDefaults.TodayIcon` is one of them, `today`.

```diff
- IconButton(icon = SystemIcons.Close, contentDescription = "Close", onClick = close)
+ IconButton(icon = Theme.icons.close, contentDescription = "Close", onClick = close)
- DatePicker(…, todayIcon = DatePickerDefaults.TodayIcon)
+ DatePicker(…, todayIcon = Theme.icons.today)
```

Outside a composition, where `Theme` cannot be read (a list built at the top
of a file), `SystemIcons.Default.close` is Tabler's.

To use another icon set's glyphs throughout, give the theme a set:

```diff
- KontourTheme { App() }
+ KontourTheme(icons = SystemIcons(close = FontAwesome.Solid.Times)) { App() }
```

### Check

- **`Strings` built by position.** `labelledValue`, `selectAllRows`,
  `sortAscending`, `sortDescending`, `notSorted` and `showDay` are new and come last, so a
  `Strings(...)` with named arguments, or `copy(...)`, is untouched. An app that
  bridges its own resources with `Strings.from` gets their English until it adds
  `kontour_labelledValue` (`%1$s, %2$s`), `kontour_selectAllRows`,
  `kontour_sortAscending`, `kontour_sortDescending`, `kontour_notSorted` and
  `kontour_showDay`.
- **What a stat says.** Its supporting line and trend are joined with
  `Strings.sentences` rather than an English full stop. In English that is the
  same sentence; an app with its own `sentences` hears its own separator.
- **Fields that grew a glyph.** `SearchField` leads with a magnifier and ends
  with a clear button, `NavSearch` leads with the magnifier, `PasswordField` and
  `SecretField` have their reveal toggle, and `Accordion` and
  `ExpandingListItem` their chevron, without being given one: each was `null`,
  which drew nothing, and for `SearchField` and the password fields left out
  the control itself. Pass `null` to keep the old look.
- **A side sheet's back is a chevron**, `Theme.icons.chevronBack`, as a top
  bar's and a sheet header's are. It was the one arrow in the library.

---

## 0.4.4 to 0.4.5

The repository moves, and two things to rewrite — `PullToRefresh` takes its
list's scroll state, and `NavBarMarkerDrag` is `NavMarkerDrag` — which the
compiler finds. `Select`, `MultiSelect`, `Combobox`, `ListSection` and `Strings`
have new parameters, defaulted and last. A few things look or behave differently
enough to check.

### Before you start

- **Swap the repository.** 0.4.5 and the icons it is drawn with are on
  `https://maven.kontour.io`, Kontour's own Maven repository, which needs no
  token; they are not on GitHub Packages.

  ```diff
   // settings.gradle.kts
   dependencyResolutionManagement {
       repositories {
           google()
           mavenCentral()
  -        maven("https://maven.pkg.github.com/kontour-labs/ui") {
  -            credentials {
  -                username = providers.gradleProperty("gpr.user").orNull
  -                password = providers.gradleProperty("gpr.key").orNull
  -            }
  -            content { includeGroup("io.kontour") }
  -        }
  +        exclusiveContent {
  +            forRepository { maven("https://maven.kontour.io") }
  +            filter { includeGroup("io.kontour") }
  +        }
       }
   }
  ```

  Then the token can go: `gpr.user` and `gpr.key` from `~/.gradle/gradle.properties`,
  and the `KONTOUR_PACKAGES_*` secrets, or whatever CI passed the token as, from
  the workflows that resolve the app's dependencies.
- **Move all three modules at once**: `kontour = "0.4.5"`, and rebuild anything
  compiled against 0.4.4.

### `PullToRefresh` takes the list's scroll state

Hand it the state of the list it holds, which says whether the list is at its
top. `null` for content that does not scroll, or whose state cannot be had.

```diff
- PullToRefresh(refreshing, onRefresh, Modifier.weight(1f)) {
-     LazyColumn { … }
+ val rows = rememberLazyListState()
+ PullToRefresh(refreshing, onRefresh, rows, Modifier.weight(1f)) {
+     LazyColumn(state = rows) { … }
  }
```

### `NavBarMarkerDrag` is `NavMarkerDrag`

The rail, the modal rail and the tab bar take it too, and the navigation
suite's parameter is `markerDrag`, for the rail as well as the bar.

```diff
- NavBar(markerDrag = NavBarMarkerDrag.SelectWhileDragging) { … }
+ NavBar(markerDrag = NavMarkerDrag.SelectWhileDragging) { … }
- NavigationSuiteScaffold(…, barMarkerDrag = NavBarMarkerDrag.Off)
+ NavigationSuiteScaffold(…, markerDrag = NavMarkerDrag.Off)
```

### Check

- **A floating bottom sheet's foot.** It now stands the sides' margin off the
  window's edge under a home indicator or a gesture handle, and its content is
  handed the rest of that strip as bottom padding. Content that takes the
  padding it is given — a list's `contentPadding` — keeps clear of the handle as
  before; content that ignored it, and relied on the old, higher margin, now
  reaches into the strip. Over three buttons or the keyboard the sheet stands a
  margin above them, where it sat on them.
- **List sections over something other than rows.** Their titles and footers
  now start at the content's edge. Pass `textInset = ListItemDefaults.ContentInset`
  to keep the old indent.
- **A date-time picker narrower than its wheels and calendar side by side**
  shows the calendar with a time row under it, whose pill opens the wheels in a
  popover, where it showed one half at a time under a segmented control. The
  popover needs an `OverlayHost` round the app, as a menu does; without one the
  pill shows the time and does nothing.
- **A rail, a modal rail or a tab bar whose destinations fit** now drags its
  marker, as a nav bar did. `markerDrag = NavMarkerDrag.Off` keeps it to taps.
- **A hero top bar that follows its list**, on iOS: put
  `Modifier.topBarCollapse(collapse)` on the list, ahead of its
  `verticalScroll`, for the bar to take the push past the list's end.
- **Content that reads a `Scaffold`'s padding in composition**, as
  `padding.calculateTopPadding()` in a composable's body rather than as a
  modifier or a `contentPadding`, sees a change a frame later than it did.

---

## 0.4.3 to 0.4.4

From 0.4.3 to 0.4.4, released on 11 October 2026. Three days and a schedule
join `CalendarView`, a date picker's day decorations become one
`DayDecoration`, a few arguments passed by position need naming, and anything
built against 0.4.3 has to be rebuilt. An event calendar's month and year
scroll rather than page, a bottom sheet moves only on a flick that is meant,
and several views look different enough to check.

### Before you start

- **Move all three modules at once**: `kontour = "0.4.4"`, and rebuild anything
  compiled against 0.4.3 — `EventCalendar`, `EventCalendarDefaults.Header`,
  `CalendarEvent`, `rememberEventCalendarState`, `ToastHost`, `SheetHeader`,
  `TimelineItem`, a timeline's `item` and `Strings` have new parameters.

### A `when` over `CalendarView` has two more branches

`CalendarView.ThreeDays` and `CalendarView.Schedule` are new, so a `when` that
names every view no longer compiles. Give each a branch, or put three days with
the day and the week:

```diff
  when (view) {
-     CalendarView.Day, CalendarView.Week -> showHours()
+     CalendarView.Day, CalendarView.ThreeDays, CalendarView.Week -> showHours()
      CalendarView.Month -> showMonth()
      CalendarView.Year -> showYear()
+     CalendarView.Schedule -> showList()
  }
```

`EventCalendarDefaults.Views` has both, three days between the day and the
week and the schedule last. To keep the calendar as it was, give it the views:
`EventCalendar(views = listOf(Day, Week, Month, Year))`.

### A date picker's days are decorated with one `DayDecoration`

`markerFor`, `containerColourFor` and `annotationFor`, and `DayAnnotation`, are
gone from `DatePicker`, `DateRangePicker`, `DateTimePicker` and `CalendarMonth`.
One `decorationFor` takes their place, giving a day a
[`DayDecoration`](components/calendar-month.md#daydecoration) with all three in it,
more than one dot, an icon, and the choice to keep its colours on a chosen day:

```diff
  DatePicker(
      value = date,
      onValueChange = { date = it },
-     markerFor = { day -> if (day in booked) trip else null },
-     containerColourFor = { day -> if (day in holidays) holiday else null },
-     annotationFor = { day -> fares[day]?.let { DayAnnotation(it.label, colour = cheap) } },
+     decorationFor = { day ->
+         DayDecoration(
+             dots = if (day in booked) listOf(trip) else emptyList(),
+             containerColour = if (day in holidays) holiday else Color.Unspecified,
+             text = fares[day]?.label,
+             textColour = cheap,
+         )
+     },
  )
```

A `DayAnnotation`'s `colour` is `textColour` now. A calendar given only dots, as
`markerFor` was, says `showDecorationLine = false`: without it the weeks are a
line taller to make room for text, as they were with `annotationFor`.

### A few arguments passed by position need naming

```diff
- TimelineItem(TimelineConnector.Solid, Modifier, false, 16.dp) { +"Left the depot" }
+ TimelineItem(TimelineConnector.Solid, Modifier, false, nodeSize = 16.dp) { +"Left the depot" }
```

The same for:

- a `TimelineItem`'s arguments after `loading`, where `icon` is new;
- a timeline `item`'s `connector` and after, also after a new `icon`;
- `ToastHost`'s `windowInsets`, after a new `countdown`;
- `SheetHeader`'s `actions`, after `onBack`, `backLabel` and `navigation`;
- `EventCalendar`'s `formats` and after, after `daysStyle`, `showTimedEvents`
  and `views`;
- the date pickers' and `CalendarMonth`'s arguments after `isDateSelectable`,
  where `decorationFor` and `showDecorationLine` take the place of three.

Each fails to compile rather than meaning something else. Name the argument.

### Check

- **An event calendar offers six views.** `EventCalendarDefaults.Views` has
  three days and the schedule, and a header too narrow for every view side by
  side offers them in a menu. Pass `views` to keep the four it had.
- **A decoration in the band of a range keeps its colours.** A day annotation
  between a range's ends took the band's ink; a decoration keeps its own there.
  On the range's ends, and on a chosen day, it still takes the fill's ink unless
  `keepColours` is set.
- **Today's ring is a circle inside a range**, not the width of the day.
- **An event calendar's month scrolls up and down** by the week, and holds six
  weeks whatever the month. A month's `visibleDates` covers the weeks on
  screen, which can be one more than the month's own. An app that loads events
  by `visibleDates` needs no change.
- **Overlapping events in the hours** are laid over each other when one starts
  below the other's title, rather than side by side.
- **Event titles** are in a slightly smaller type, and fade out where they are
  cut rather than ending in "…". A busy day's "+N" is a row of its own.
- **The edge arrow, carrying an event with a mouse,** is level with the pointer.
- **An event calendar's year scrolls up and down** through the years, and a
  screen reader reaches its months, not each of its days.
- **A toast's clock stops while a mouse rests on it.** A test that hovers a
  toast and waits for it to go needs to move the pointer away first.
- **A bottom sheet moves only on a flick that is meant**: once the finger has
  run about 48dp one way at 300dp/s or more, to the detent nearest where the
  throw carries it, and past the next detent only at 5000dp/s. A small quick
  flick that used to move it a detent now settles where it is nearest. A test
  that flicks a sheet a short way to move it needs a longer or faster flick.
- **A bottom sheet waits a little longer before a vertical drag is its own**,
  1.75 times the touch slop, and a swipe across a carousel, chips or a pager
  inside it is theirs up to about sixty degrees from across.
- **A modal bottom sheet put away by a gesture always calls
  `onDismissRequest`**, including after it was caught as it opened or settled,
  where before it could be put away without the call.
- **A floating bottom sheet's content widens with it** as it fills to the
  sides, and a part pinned to a sheet's foot has the content fade out 24dp above
  it rather than 16dp.
- **Swipe actions in a reorderable row** wait for the finger to go 24dp across,
  and a row held to pick up and let go where it was no longer also taps.

---

## 0.4.2 to 0.4.3

From 0.4.2 to 0.4.3, released on 8 October 2026. A sheet's `pinned { }` becomes a `part` with a `pin`, the
library is on Compose Multiplatform 1.12.1, a few arguments passed by position
need naming, and anything built against 0.4.2 has to be rebuilt. Several things
look or behave differently enough to check: badges, coach mark tours, swipe rows,
scrollbars beside an index, undismissable sheets, the time pickers' height, a
top bar's collapse and a `StateSwitcher`.

### Before you start

- **Move all three modules at once**: `kontour = "0.4.3"`.
- **Move Compose Multiplatform to 1.12.1**, from 1.12.0-rc01, and rebuild
  anything compiled against 0.4.2.

### A sheet's `pinned` is a pinned `part`

```diff
  BottomSheet(sheet) { padding ->
-     pinned { SearchField(query, placeholder = "Search stops") }
+     part(pin = SheetPartPin.Bottom) { SearchField(query, placeholder = "Search stops") }
      LazyColumn(contentPadding = padding) { stopRows() }
  }
```

`pinned(to = SheetPin.Top) { … }` is `part(pin = SheetPartPin.Top) { … }`. A
top part has no inset of its own, as the content has none: a `SheetHeader`
there insets itself; a part of your own that relied on the 16dp at its sides
and below takes `Modifier.padding(horizontal = BottomSheetDefaults.ContentPadding)`
and a bottom padding itself. A bottom part keeps the inset.

```diff
- rememberSheetState(detents = listOf(SheetDetent.pinned(), SheetDetent.Half))
+ rememberSheetState(detents = listOf(SheetDetent.parts(), SheetDetent.Half))
```

`pinned(containerColour = Color.Transparent)`, which let the content show round
the part, is `fadeUnder = false` on the sheet. Another colour for the part's
ground is the part's own: a `Surface` round it.

A bottom part is at the foot of what the sheet shows on every frame, a drag
included, as the 0.4.2 part was. The content now fades out under it the way it
does under a floating navigation bar, rather than stopping at a band of the
sheet's colour.

### Arguments passed by position

```diff
- DatePicker(date, { date = it }, Modifier, true, today, { true }, marks, back, on)
+ DatePicker(date, { date = it }, Modifier, true, today, { true }, marks, previousIcon = back, nextIcon = on)
```

The same for:

- `DateRangePicker`'s, `DateTimePicker`'s and `CalendarMonth`'s arguments after
  `markerFor`;
- `TimePicker`'s `formats` and `DateTimePicker`'s `navigation` after
  `minuteStep`, where `now` is new;
- `DateField`'s `style` and `formats` after `placeholder`, and `TimeField`'s and
  `DateTimeField`'s `formats` after `label`;
- a sheet's `dragHandle` after `draggable`;
- a select's `interactionSource` after `otherLabel`;
- `Modifier.coachmark`'s `priority` after `alignment`, and
  `Modifier.coachmarkStep`'s `enabled` after `shape`, where `tone` is new.

Each fails to compile rather than meaning something else. Name the argument.

### Check: badges

A [`BadgedBox`](components/badge.md) is exactly the size of what it badges, and
the badge hangs outside it. Layouts round a badged thing move by up to half a
badge, and screenshots change. A parent that clips — a `Surface`, an avatar
clipped to a circle — needs room for half a badge, or cuts it.

### Check: coach mark tours

A tour is not ended by a tap on the dim, Back or Escape; its own buttons end it.
`rememberCoachmarkTour(dismissible = true)` is 0.4.2's behaviour, and a test that
tapped outside to end a tour needs it, or a press on the tour's button. Between
steps a tour morphs by default; `transition = CoachmarkTransition.Fade`
crossfades.

### Check: swipe rows

A row showing its actions closes at a press anywhere else. A test that opened a
row and then pressed something else, expecting the row still open, sees it
closed.

### Check: a scrollbar beside an index

A list with an [`IndexScroller`](components/index-scroller.md) draws no
scrollbar while the index is there. A test that found the scrollbar there finds
nothing.

### Check: an undismissable sheet

A modal sheet with `dismissible = false` and one size has no drag handle. A test
that looked for the handle there finds none.

### Check: the time pickers

A `TimePicker` and a `DateTimePicker` have a Now button under their wheels, so
they and the field pickers that hold them are taller. `now = null` takes it
away.

### Check: a large or hero top bar

Let go part-way, it finishes the way the finger was going, not to the nearer
end. A test that let go of a bar moving and expected the nearer end may see
the other.

### Check: a `StateSwitcher` with several things in a state

They are laid out one under another, where they were drawn over each other. A
state that relied on that — a badge over a picture — wraps them in a `Box`.

---

## 0.4.1 to 0.4.2

From 0.4.1 to 0.4.2, released on 4 October 2026. A lambda literal compiles as
it did everywhere; a builder held in a variable, and a few arguments passed by
position, need a change. Several things look or behave differently enough to
check, and tests that find controls by role or by text should be run.

### Before you start

- **Move all three modules at once**: `kontour = "0.4.2"`.
- **Rebuild anything compiled against 0.4.1.** `ListGroup`, `KeyValueList`,
  `Stat`, `Gauge`, `Meter` and `Knob` are `inline` now, and `Dialog`, `NavBar`,
  `NavigationSuiteScaffold`, the four selection controls, `ComponentDefaults`
  and `Strings` gained parameters, so a library of your own built against 0.4.1
  fails when it calls one, until it is rebuilt.

### Builders are composable

The builders of `ListGroup`, `KeyValueList`, `Stat` and the readouts of
`Gauge`, `Meter` and `Knob` are `@Composable` now, so the theme's formats and
strings are read where a row or a figure is written. A lambda literal compiles
as before. A builder kept in a variable, or a function passed for one, has to be
composable too:

```diff
- val rows: ListGroupScope.() -> Unit = { item("Help") }
+ val rows: @Composable ListGroupScope.() -> Unit = { item("Help") }
  ListGroup(content = rows)

- fun KeyValueScope.fare(trip: Trip) = item("Fare", trip.fare)
+ @Composable fun KeyValueScope.fare(trip: Trip) = item("Fare", trip.fare)
```

What was worked out above the builder can move into it:

```diff
- val updated = Theme.formats.date(trip.updated)
- KeyValueList { item("Updated", updated) }
+ KeyValueList { item("Updated", Theme.formats.date(trip.updated)) }
```

### Parameters added partway through a list

**A call that names its arguments is unaffected.** One that passes a later
argument by position stops compiling at it, with a type mismatch; name it and it
compiles again. No call compiles to something different: each new parameter's
type differs from the argument that was in its place.

| Function | New parameters | A positional argument that now fails |
|---|---|---|
| `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioButton` | `contentDescription`, after `enabled` | `interactionSource` |
| `Button`, `TextButton`, `SplitButton` | `contentDescription`, after `enabled` | `variant` (`colour` on `TextButton`), and those after it |
| `TextField` | `code`, after `trailing` | `variant`, and those after it |
| `TextArea` | `code`, after `lineLimits` | `variant`, and those after it |
| `NavBar` | `fadeUnder`, before `windowInsets` | `windowInsets`, and those after it |
| `NavigationSuiteScaffold` | `barFadeUnder`, after `railExpandedWidth` | `containerColour`, and those after it |

`Dialog`'s `actions` comes after `title`, before the content, which a call
passes as a trailing lambda; `ComponentDefaults`' `scrollbarTouchThickness` is
the last field, after every one before it.

### Check: a wide window without a `WindowSizeClassProvider`

`KontourTheme` reads the window size class from the window when nothing outside
it provides one. An app that left the provider out had a phone's layout
everywhere; on a tablet or a desktop window it now gets a rail and two panes. A
provider, or a class provided outright, is kept as it is.

### Check: a floating `NavBar`

It sits lower over iOS's home indicator and Android's gesture handle, and the
page fades out under it. A screen that measured its own room above the bar
should use the scaffold's padding, which comes down with it; `fadeUnder = false`
keeps the page as it was, and rims the capsule instead.

### Check: tests that find a select's options, or a search field

A `Select`'s and a `Combobox`'s options are radio buttons now, chosen or not, and
a `MultiSelect`'s say whether they are ticked. A test that found them as buttons
should find them by text, or as radio buttons. A `SearchField` is named by its
placeholder, and the placeholder is no longer a node of its own while it is that
name: find the field by its content description, or by `hasSetTextAction()`.

### Check: a fitted `Table` column

A column with neither `width` nor `weight` is measured against rows spread
through the data, not only the first fifty, and again when `items` is a new
list. It can come out wider than it was. One that must be a fixed width should
be given `width`.

### Check: a scrollbar under touch

A `Scrollbar` shows, thinner, while the content moves under a finger, and fades
after it stops, where it drew nothing before. While it is drawn, a finger on its
thumb drags it, and a press there is no longer the row's under it.
`alwaysVisible = false` was always the default and still is; it is touch that
changed.

### Check: a date picker given no arrows

A `DatePicker`, `DateRangePicker` or `DateTimePicker` called without
`previousIcon` and `nextIcon` has the library's chevrons either side of its
month, where it had no buttons. One that is meant to page only by a swipe
should pass `null` for both.

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
