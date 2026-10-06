# Changelog

What changed in each release, newest first. `io.kontour:ui`, `:ui-nav3` and
`:haptics` are released together at one version, so one entry covers all three.

The library is before 1.0, and a release can break its API. When one does, the
break is listed under **Breaking** with what to write instead. There are no
deprecated aliases in between, so the compiler finds every call site. Changes
that still compile but look or behave differently are under **Changed**. Read
those rather than finding them on a device.

**Upgrading an app, as a person or an agent:**

1. Read every entry after the version the app is on, up to and including the one
   it is moving to.
2. Make each **Breaking** change. [Migrating](migrating.md) has each one as
   code, before and after.
3. Check each **Changed** line against the screens that use it.

The version an app is on is the `kontour` entry in its version catalog. See
[Installing](installing.md).

---

## Unreleased

On `main`, and in the next release: 0.4.3.

Mobile web, mostly: the keyboard on the first tap of a text field on an iPhone,
a focused field's selection and menu that are the browser's own, and tabs,
checkboxes and switches named as themselves to a screen reader in a browser. A
sheet's top and foot become slots of the sheet, with a footer that moves with it.
Days in a date picker can carry a colour and a few characters, a scrollbar keeps
clear of bars over its list, a large top bar follows the finger, and a page
slides under a navigation rail. On Compose Multiplatform 1.12.1, with Java 11
bytecode on the JVM.

A sheet's `pinned { }` is now its `header` and `footer`, and a few arguments
passed by position need naming — see **Breaking**, and
[Migrating](migrating.md#042-to-043).

### Breaking

- **A [bottom sheet](components/bottom-sheet.md#header-and-footer--the-sheets-top-and-foot-with-the-content-passing-behind)'s
  `pinned { }` is its `header` and `footer`.** Asked for: *"refine the 'pinned'
  logic … it just doesn't feel intuitive, and it doesn't feel like it fits in
  with [the rest] of the library"*. They are slots of `BottomSheet` and
  `ModalBottomSheet` now, as a `Scaffold`'s bars are, rather than a call that
  could be written anywhere in the content and was drawn somewhere else.
  `pinned(to = SheetPin.Top)` is `header = { … }`, laid out as the content is,
  so a `SheetHeader` in it insets itself; `pinned { }` is `footer = { … }`.
  `SheetPin` is gone, and `SheetDetent.pinned()` is
  `SheetDetent.headerAndFooter()`. What changed on screen is under **Changed**.
- **Recompile against Compose Multiplatform 1.12.1.** The library moved from
  1.12.0-rc01 to the stable release.
- **A few arguments passed by position no longer compile**, where a parameter
  arrived in the middle of a list, each of a different type from what was there
  so nothing silently means something else: `DatePicker`, `DateRangePicker` and
  `DateTimePicker` take `containerColourFor` and `annotationFor` after
  `markerFor`; `DateField` takes `supporting` and `errorMessage` after
  `placeholder`, `TimeField` and `DateTimeField` after `label`; `BottomSheet`
  and `ModalBottomSheet` take `fadeUnder` after `draggable`; `Select` and
  `MultiSelect` take `otherPlaceholder` after `otherLabel`. Name the argument.

### Changed

- **On an iPhone or iPad, the first tap on a text field opens the keyboard.**
  0.4.2's fix did not: *"the first tap doesn't open it, but a second tap
  does"*. Driven in a browser with an iPhone's agent, Compose focuses its input
  while the finger is still down, a frame after it lands, which WebKit does not
  count as the reader's; 0.4.2's primer then took the focus from it as the
  finger lifted, and Compose took it back a frame later, outside the touch
  again. The primer now focuses Compose's own input again as the finger lifts,
  which is the focus WebKit opens the keyboard for. Untested on a device here.
- **On a phone or a tablet, in any browser, a focused text field's selection,
  handles and menu are the browser's own**: *"it shows the compose one instead,
  it needs to show just the native one"*. Compose's input is laid over the
  field's text while it has the focus, in the same face at the same size, and
  takes the touches on it; Compose's own caret, selection, handles and toolbar
  stand aside for the field. Paste, Cut and drops from the browser's menu reach
  the field. A field whose text is drawn as something else — a phone number
  grouped as it is typed — keeps Compose's. See
  [installing](installing.md#per-platform).
- **In a browser, tabs, checkboxes, switches and radio buttons are named as
  themselves**, with their checked or selected state: *"toggleable rows show up
  as buttons in the browser's accessibility tree, and so do tabs"*. Compose
  1.12's web layer names any clickable node a button and writes no state; the
  library's own controls put theirs back. See
  [In the browser](accessibility.md#in-the-browser).
- **A sheet's footer moves with the sheet**, at the foot of the size it was
  resting at, and slides to the foot of the new size once the sheet has come to
  rest. It rode the bottom of the window through every frame of a drag. A sheet
  moved in code takes it along at its foot, as before.
- **Content fades out under a sheet's header and footer**, as a page does under
  a `TopBar`, where a pinned part stood on a solid band of the sheet's colour,
  with 16dp of it above a part that had risen. `fadeUnder = false` lets the
  content show round them, as `containerColour = Color.Transparent` did.
- **A part in a sheet's header has no inset of its own.** A part pinned to the
  top was padded by 16dp at the sides and below; the header is laid out as the
  content is, so a `SheetHeader` there looks as it does in the content, and
  anything else is as wide as the sheet.
- **A page sliding towards a [`NavRail`](components/nav-rail.md) goes under
  it.** *"the old screen goes above the nav rail briefly and then cuts out of
  existence. it should go behind the nav rail/drawer"*. The rail draws over
  what is beside it now, in a `NavigationSuiteScaffold` or anywhere else.
- **A large or hero [`TopBar`](components/top-bar.md) follows the finger.**
  *"sometimes they half collapse and stop, other times they don't expand
  properly"*. Pulled open at the top of a list, it opens over its own height,
  so the list comes down with the finger, where a bar with less height than
  its title's journey opened slower than the pull. Let go part-way, it finishes
  the way the finger was going rather than to the nearer end, and a fling that
  carries the list back to its top opens it.
- **[`StateSwitcher`](components/empty-state.md) lays each state out as a
  column**: *"When a page's content had several sections, they drew on top of
  each other."* A state that wants a box writes one.
- **The JVM artifacts are Java 11 bytecode.** `:ui` and `:ui-nav3` were the
  toolchain's Java 25, and with the builders inline, an app using them had to
  compile for Java 25 too.

### Added

- **A day's own colour and a few characters under its number**, in a
  [`DatePicker`](components/date-picker.md#what-the-app-has-to-say-about-a-day),
  `DateRangePicker`, `DateTimePicker` and `CalendarMonth`: *"the ability to
  colour and annotate dates (with things like small numbers or text or stuff)"*.
  `containerColourFor` fills a day, under the selection and the range;
  `annotationFor` gives a `DayAnnotation`, its text, colour and what a screen
  reader says after the date. Given annotations, every week is a line taller.
- **A [scrollbar](components/scrollbar.md) keeps clear of bars over its
  scroller**: `contentPadding` on `Modifier.scrollbar` and `Scrollbar`, usually
  the padding the list's own content keeps clear of, so the track stops short
  of a top bar or a floating tab or nav bar. *"we need to make sure the
  scrollbar modifier can keep the bar clear of floating tabs or top bars"*.
- **`otherPlaceholder` on a [`Select`](components/select.md) and a
  `MultiSelect`**, for the hint in the "Other" box. `otherLabel` already named
  the option in the app's own words; the example now shows it.
- **`supporting` and `errorMessage` on a [`DateField`](components/date-field.md)**,
  `DateTimeField` and `TimeField`, as a text field has.

---

## 0.4.2 — 4 October 2026

Feedback from building an app on 0.4.1. A loading state and a way to move
between states, a scrollbar that shows under touch and goes on any scroller, a
row of buttons for a form dialog, date fields, money in the theme's formats,
builders that read the theme where they are written, and a "More" for a bar with
more destinations than room. Fixes for overlays left up when their caller went,
a select's options and a search field as a screen reader meets them, a table
column fitted to more than its first rows, swipe back over a moving list,
`LoadMore` in a plain column, helpers in a navigation group and a POSIX browser
locale. The docs gain recipes for images from the network and for a question
that needs more than an answer.

A lambda literal compiles everywhere as it did, but a builder held in a variable
and a few arguments passed by position need a change, and anything built
against 0.4.1 has to be recompiled — see **Breaking**, and
[Migrating](migrating.md#041-to-042) for the code before and after. Four things
look different without any change: an app with no `WindowSizeClassProvider` now
gets a rail and two panes on a wide window rather than a phone's layout, a
floating `NavBar` sits lower over a home indicator, with the page fading out
under it (`fadeUnder = false` keeps the page as it was), a date picker given
no icons has arrows either side of its month, and a bottom sheet's top corners
on a round phone stop at 40dp.

### Breaking

- **[`ListGroup`](components/list-item.md), [`KeyValueList`](components/key-value-list.md),
  [`Stat`](components/stat.md) and the readouts of [`Gauge`](components/gauge.md),
  [`Meter`](components/meter.md) and [`Knob`](components/knob.md) take
  composable builders**, so `Theme.formats`, `Theme.strings` and a string
  resource are read where a row or a figure is written —
  `item("Departs", supporting = Theme.formats.time(departure))`,
  `value(Theme.formats.currency(total, "AUD"))` — rather than hoisted above it.
  All six are `inline`, as `Column` is, so the builder runs in the caller's
  composition and a state it reads recomposes what draws it. **A lambda literal
  compiles as before; a builder held in a variable of the old, non-composable
  type, or a plain function passed for one, no longer does** — mark it
  `@Composable`. `listGroup` in a `LazyColumn` is unchanged.
- **A selection control's `interactionSource`, or a button's or a text field's
  `variant`, passed by position no longer compiles.** `Checkbox`,
  `TriStateCheckbox`, `Switch`, `RadioButton`, `Button`, `TextButton` and
  `SplitButton` take a `contentDescription` after `enabled`; `TextField` and
  `TextArea` take `code` before `variant`; `NavBar` takes `fadeUnder` before
  `windowInsets`, and `NavigationSuiteScaffold` `barFadeUnder` after
  `railExpandedWidth`. Each is a different type from what was in its place, so a
  call that passed those arguments by position fails to compile rather than
  meaning something else. Name the argument.
- **Recompile rather than swap the jar.** The six builders above are `inline`
  now, and `Dialog`, `NavBar`, `NavigationSuiteScaffold`, the selection
  controls, the buttons, the text fields, `ComponentDefaults`, `LocaleFormats`
  and `Strings` gained members,
  so a library built against 0.4.1 fails when it calls one until it is rebuilt.

### Changed

- **A [bottom sheet](components/bottom-sheet.md)'s top corners stop at 40dp** on
  a round display. They followed the display's corners all the way — an iPhone's
  55pt, a newer Pixel's 51dp — at any height, and at half height that curve sat
  close to the header's text: *"on iphones and newer pixels, it starts to look
  like the rounded corners get a bit close to the text in the sheet header"*. A
  display rounder than the theme's 34dp still rounds them, to 40dp at most; at
  full height the display's own corner covers them either way. A theme whose
  sheet corner is rounder than 40dp keeps it.
- **A [`PageIndicator`](components/page-indicator.md#dragged-along-page-by-page)'s
  scrub pill fades in as it grows** and is its colour from half its thickness
  on, and fades as it shrinks back. The line through the dots it starts as was
  drawn at full colour, a hard thin bar for a frame or two. The fade rides the
  same spring as the size.
- **Under touch, a [scrollbar](components/scrollbar.md) shows while the content
  moves**, thinner, and fades 600ms after it stops, where it used to draw
  nothing at all. While it is drawn a finger on its thumb takes hold of it and
  drags the content, as a mouse does — *"if the scrollbar is visible (even on
  mobile), then dragging it should do what you expect"*. Only the thumb: the
  rest of the strip at a list's edge is the list's, which it was not under an
  `alwaysVisible` bar. With a keyboard it is drawn at rest, as with a mouse and
  as the docs always said.
- **Without a `WindowSizeClassProvider`, the [window size class](components/window-size-class.md)
  is the window's**, read by `KontourTheme`, where it used to be a phone. Apps
  that leave the provider out — the root setup has none — now get a rail and two
  panes on a tablet or a desktop window rather than the bottom bar. A provider
  still measures a region, and wins inside it; a class provided outright is
  kept.
- **A floating [`NavBar`](components/nav-bar.md#where-a-floating-bar-sits-and-what-is-under-it)
  sits just clear of iOS's home indicator and Android's gesture handle**, about
  28dp up from the window's foot, rather than above the whole strip the system
  keeps for them: 14dp lower on an iPhone, 4dp lower over a gesture handle.
  Android's three buttons, and a docked bar, keep the whole inset, as does a
  `windowInsets` taller than the handle's, which is kept as given. There is no
  switch for this: it is where the bar belongs. The scaffold's padding comes
  down with it.
- **The page fades out under a floating bar**, which stands off it on a shadow
  a little deeper than the theme's `high`. Over the rows of a list, which are
  `surface` as the bar is, it was a shadow apart from them. `fadeUnder = false`
  (`barFadeUnder` on `NavigationSuiteScaffold`) leaves the page as it is and
  gives the capsule a rim, a hairline of its own ink, instead. The fade is the
  colour the bar sits on, `LocalContainerColour`.
- **An [`AlertDialog`](components/alert-dialog.md)'s buttons stay on screen
  under a long message**, which scrolls, rather than scrolling away below it.
  A dialog's content fades at the edge it scrolls under while there is more
  past it.
- **A [`DatePicker`](components/date-picker.md#the-header-is-the-way-out-of-paging),
  `DateRangePicker` and `DateTimePicker` have their paging arrows by default**:
  the library's chevrons, back and on in the reading direction. Given no icons
  they had no buttons either side of the month, and paged only by a swipe —
  *"there's no left/right buttons"*, of one in a dialog. `previousIcon = null`
  and `nextIcon = null` leave them out.
- **A [toast](components/toast.md) stands 8dp off its edge**, the window's top
  or foot inside its insets, and 8dp above a navigation bar, where it stood 16dp
  off the window and 28dp above the bar: *"the clearance between the toast and
  the top/bottom of the page (or the clearance above the nav bar) is a bit too
  large"*.

### Added

- **Several pictures behind a `Hero` [`TopBar`](components/top-bar.md)**:
  *"with the hero header, can we make an option for a carousel of images. try
  to use our existing carousel and page indicators"*. Give `image` a
  `HeroImages(state) { page -> … }` and the picture is a
  [`Carousel`](components/carousel.md), one page at a time, swiped anywhere
  that is not a button, with its [`PageIndicator`](components/page-indicator.md)
  at the bottom end of the bar, over the fade, on the last line of the title
  block, which stops short of it.
  The dots go up with the title as the bar collapses and are gone a third of
  the way in. Decorative like a single hero picture, so hidden from screen
  readers, dots and all; outside a hero bar it is a carousel announced as
  `Theme.strings.images`, with its dots at the foot.
- **"Other" on a [`Select`](components/select.md) and a
  [`MultiSelect`](components/multi-select.md)**, for an answer none of the
  options give: *"when this option is selected, a text box expands within the
  dropdown's outline, allowing the user to enter a custom value"*. Pass
  `onOtherTextChange` and the menu ends with "Other"; picked, a filled text
  field opens inside the outline, under the value, inset for concentric
  corners, and takes the keyboard. `otherText` holds what is
  typed, and picking an option instead clears it, so the answer is
  `otherText ?: value`. The words are `Theme.strings.selectOther` and
  `selectOtherHint`, or `otherLabel`. Nothing changes for a select not given it.
- **[Drag and drop](drag-and-drop.md) across the app**, so a card moves
  between a board's columns, which `ReorderableItem` could not do.
  `Modifier.dragSource` carries a typed payload, `Modifier.dropTarget` with
  `rememberDropTargetState` takes it, and `rememberDropListState`,
  `Modifier.dropList` and `dragItems` make a lazy list both. Each drop is one
  `onDrop`, with the index it lands at and where it came from, and nothing in
  your lists moves before then. The list opens a gap where the card would land,
  and scrolls as the card nears either end; anything else that scrolls takes
  `Modifier.dragAutoScroll`. The nearest `OverlayHost` draws the carried card
  above everything, so it can leave a sheet. Every move also works without a
  pointer: *Move to Done*, *Move up* and *Move down* for a screen reader, Space,
  Enter and the arrows for a keyboard, and each step is said aloud through
  `Theme.strings`.

- **[`FileField`](components/file-field.md)**, and **`rememberFilePicker`**
  under it, which opens each platform's own picker: Android's document picker, iOS's,
  a desktop file dialog and a browser's file input. Each file is a chip with its
  name and size, in one row that scrolls sideways so the field keeps its height;
  a single file clears with the button where the attach glyph was. `FileTypes` says what to
  offer (`image/png`, a whole family, `.pdf`), and what comes back is checked
  against it again. What is picked arrives as `PickedFile`s, read on demand.
  `LocalFileChooser` swaps the picker for a test's or an app's own. Files
  dragged in from outside the app can be dropped on the field too. Before, it
  took a button and a text area. On Android, `:ui` now depends on
  `androidx.activity:activity-compose` for the picker's result registry.

- **Drops from outside the app**: files from the desktop, the Files app or
  another window, and text. A drop target or a drop list takes an
  `ExternalDrops` — `ExternalDrops.files(types)` for files of some types — and
  hears each drop once, as `PickedFile`s read on demand and, on a list, the
  position it was let go at where the platform says (a desktop and Android; a
  browser and iOS do not). A list shows a line where it would land. On Android
  the drop's read permission is asked for through the activity, and lasts as
  long as the activity does.

- **[`DropZone`](components/drop-zone.md)**, a drop target that shows where a
  drag stands with it — a dashed place at rest, the accent while something it
  would take is carried, the accent's ground over it, the danger colour's when
  it would turn the thing away — laid out as an `EmptyState`, with room for a
  picker beside the drop. `rememberDropTargetState(label, external)` makes a
  target that takes only drags from outside the app, for an upload area.

- **[`Button`](components/button.md), [`TextButton`](components/text-button.md)
  and [`SplitButton`](components/split-button.md) take a `contentDescription`**,
  read in place of the label where the label alone does not say enough — "Save"
  on screen, "Save this trip to Perth" read aloud. It took a `semantics`
  modifier of your own. An [`ExtendedFloatingActionButton`](components/fab.md)'s
  description is now its name open as well as collapsed, as its documentation
  said; open, the label was.

- **[`SecretField`](components/specialised-fields.md#a-key-is-not-a-password)**
  for an API key, a token or a recovery code: masked, revealed and showing the
  character just typed as `PasswordField` does, and not a password to the
  platform — no autofill hint, an ASCII keyboard, the mono face — so nothing
  offers to save a key as the reader's login. On iOS the keyboard may learn from
  it, as it does from any field that is not secure entry; the page says what
  each platform still does. Its toggle reads `Theme.strings.show` and `hide`.

- **[`TextArea`](components/text-area.md#code) and
  [`TextField`](components/text-field.md) take `code = true`** for a snippet, a
  config file or an identifier: the text and placeholder in the theme's mono
  face, at the field's own size, and a keyboard asked for no autocorrect and no
  capitalisation. The fields take no text style, so there was no asking for one.

- **`Theme.formats.currency(amount, code)`** writes an amount of money as the
  reader writes it — `€3.20`, `3,20 €`, `￥320` — with the currency's own places,
  from the platform's CLDR. See [`LocaleFormats`](components/locale-formats.md#money).

- **[`LoadingState`](components/empty-state.md#loading-and-the-move-between-them)**,
  the third of the states beside `EmptyState` and `ErrorState`, drawn as they
  are with a spinner where their icon is.
- **[`StateSwitcher`](components/empty-state.md#loading-and-the-move-between-them)**
  puts loading, empty, failed and the content in one place and moves between
  them when its `target` changes: the state leaving fades through the one
  arriving, and the space they take changes size smoothly. Under reduced motion
  it cross-fades and takes the new size at once.

- **`contentDescription` on [`Checkbox`](components/checkbox.md),
  [`TriStateCheckbox`](components/tri-state-checkbox.md),
  [`Switch`](components/switch.md) and [`RadioButton`](components/radio-button.md)**,
  for one that stands alone with no row to name it — a select-all box over a
  table. It came out as an unnamed control. In a `SelectionRow` the row's label
  is still the name; leave it `null` there.

- **[`DateField`](components/date-field.md) and `DateTimeField`**, the tappable
  field a form shows for a `DatePicker` and a `DateTimePicker`, as `TimeField`
  is for a `TimePicker`. A `DateField` with no date yet shows its placeholder.

- **[`SegmentedControl`](components/segmented-control.md) takes values**:
  `value`, `options`, `onValueChange` and `optionLabel`, as `Select` and
  `RadioGroup` do, so nothing maps an index back to an option. The overload
  taking strings and an index stays.

- **[`Modifier.scrollbar(state)`](components/scrollbar.md)** puts a scrollbar
  on the scroller itself — a `Column` before its `verticalScroll`, a
  `LazyColumn`, a grid, a `LazyRow` with `Orientation.Horizontal` — with nothing
  to wrap it in and nothing to align. `Scrollbar` is the same bar as a component
  of its own.

- **[`Dialog`](components/dialog.md) takes `actions`**, the row of buttons an
  `AlertDialog` draws: `actions = { cancel(); confirm("Save", enabled = valid) { … } }`.
  The row sits under the content rather than in it, so a form taller than the
  window scrolls and its Save stays on screen. The builder is composable, so a
  label can come from `Theme.strings` or a string resource where it is written.
  It is also how to ask a question that needs more than an answer — type the
  account's email to delete it, give a reason — with a field above a confirm
  that waits for it; [the alert dialog's page](components/alert-dialog.md#a-question-that-needs-more-than-an-answer)
  has the recipe. A destructive `confirm` warns once as the dialog arrives, as
  a destructive `AlertDialog` does, and anything else written in the row — a
  button of your own — is drawn too, leading it. An `AlertDialog` keeps its
  fixed shape.

- **A [`NavBar`](components/nav-bar.md#dragging-the-marker)'s marker can be
  dragged to a group.** Let go over one and its menu opens, and the group keeps
  the marker while it is open; closed without a pick, the marker goes back to
  where it was. A drag across a group to the destination beyond opens nothing.

- **[`more { }`](components/nav-bar.md#more-than-a-bar-has-room-for)** on any
  `NavScope`: the destinations a bar has no room for, behind a circle of three
  dots at its end, named `Theme.strings.more`, that opens a menu of them. A
  group with no icon, name or key to choose. A rail has room, and draws them in
  line, so one block reads right in a `NavigationSuiteScaffold`.

### Fixed

- **[`SegmentedControl`](components/segmented-control.md#when-the-labels-do-not-fit)'s
  labels keep apart at phone width.** A segment keeps
  `SegmentedControlDefaults.LabelPadding`, 4dp, either side of its label, and the
  control stacks its options rather than let two labels closer than 8dp. A label
  could fill its share to the last dp, and "Standard" met "Keyboard" with nothing
  between them. A control that fitted with a few dp to spare now stacks: the
  gallery's settings at 130% text, for one.
- **`PasswordField(newPassword = true)` tells autofill it is a new password.**
  Its hint was on the field's frame, which no autofill service reads; the input
  said `Password`, from the password keyboard, so a password manager offered a
  saved password where it should have offered to make one. The specialised
  fields' hints are now on the input itself.
- **On an iPhone or iPad, the keyboard opens on the tap that focuses a text
  field in the browser.** WebKit opens it only for an input focused during the
  tap, and Compose focuses its own a frame later — later still for a combobox,
  the nav bar's search and a knob's typed value, which focus from an effect.
  `KontourTheme` installs a primer that holds the keyboard open for them. See
  [installing](installing.md#per-platform); untested on a device here.
- **An [`Avatar`](components/avatar.md) or a [`Badge`](components/badge.md)
  inside a row no longer names it.** A clickable `Card`, a `ListItem`, a
  `SelectionRow` and a button each merge into one node, and a description in it
  is read instead of the row's text — so a chat row was announced as its person,
  or as "3 unread", and nothing else. Inside one, an avatar says no name and a
  badge says itself as text, read with the rest of the row; an `AvatarGroup` is
  "3 people" rather than "3 people, Ada, Grace, Katherine". An avatar given a
  `contentDescription` still says it.
- **An edge [bottom sheet](components/bottom-sheet.md#header-and-footer--the-sheets-top-and-foot-with-the-content-passing-behind)
  collapsed to a part pinned to its top clears the safe area.** The pinned
  detent counted the safe area only under a part pinned to the bottom, so with
  the part at the top the sheet came to rest with its foot behind the home
  indicator: *"the "edge" bottom sheet style now doesn't clear the safe area
  when it's collapsed with a "pinned" item"*. Not new in this release — the
  arithmetic is 0.3.0's.
- **A [coachmark tour](components/coachmark.md#coachmarktour) dims the page
  once, from one step to the next.** Each step had a dim of its own, and the
  next arrived while the last was leaving, so the page went twice as dark
  between steps and came back. The dim stays, and the hole and the bubble
  travel to the next control.
- **A menu's dividers can be seen on the menu.** They were `outlineSubtle`, the
  faintest rule, chosen for a list on `surface`; on a menu's raised panel that
  was 1.15:1 in light and, in dark, 1.06:1 and darker than the panel. A
  [`MenuDivider`](components/dropdown-menu.md), and every menu's `divider()`, is
  the menu's text colour at 12% now — 1.28:1 and 1.39:1, what a list's divider
  stands off a list — and the tier's `outline` at high contrast, as before.
- **A [`Table`](components/table.md)'s fitted column is measured against rows
  spread through the data** — the first twenty, the last ten and twenty between —
  rather than the first fifty alone, and again, widening only, when `items` is a
  new list. A wider date past the first rows was cut off as it scrolled into
  view, and data loaded later never refitted the column.
- **A [`SearchField`](components/search-field.md) is named by its placeholder**,
  and "Search" without one, as are the fields in `NavSearch` and the command
  palette. It had no name at all — a screen reader announced an edit box — and
  nothing from outside could give it one.
- **A [`Select`](components/select.md)'s and a
  [`Combobox`](components/combobox.md)'s options are each a radio button, chosen
  or not, in one group.** Only the chosen one was; the rest announced as
  buttons, so nothing said they were choices. A
  [`MultiSelect`](components/multi-select.md)'s checkboxes now say whether they
  are ticked. A menu of actions is unchanged.
- **A `Dialog`, `AlertDialog` or `CommandPalette` that leaves composition while
  it is up goes with it**, as a popover, a menu and a sheet already did. It
  stayed on screen when its caller went — a screen navigated away from, an `if`
  round it turning false — with nothing left to close it. A dialog whose `key`
  changed while it was up was shown twice. The toast stack's host is the same.
- **A scrollbar on a grid, a staggered grid or a pager is drawn.** It read only
  `ScrollState`s and lazy lists, and drew nothing for anything else; it reads
  the position every scroller in foundation reports now.
- **A browser reporting a POSIX locale no longer blanks the page.** Headless
  Chromium's `navigator.language` is `en-US@posix`, and Compose's `Locale(tag)`
  on the web is `new Intl.Locale(tag)`, which throws on it, through the first
  composition. The browser's languages are cleaned — the `@` modifier and the
  charset dropped, `_` made `-` — and the first `Intl` accepts is used, with
  `en-AU`, the library's own default, when none is. Compose's own
  `Locale.current` reads the same list through the same constructor if anything
  inside Compose asks for it; that half is Compose's.
- **[`LoadMore`](components/load-more.md) asks for the next page when it is on
  screen, not when it is composed.** At the foot of a plain scrolling `Column`,
  which composes everything at once, it chained through every page to the end
  before anyone scrolled. It now waits until some of it is in the window; a
  lazy list's prefetch no longer asks early either.
- **A destination written through a helper draws where the helper is called.**
  A helper written in a navigation block — `fun stop(id: String) = item(…)` —
  closes over that block's scope, and called inside a `group { }` it drew as
  the outer scope's: over a shut group in a rail, unindented in an open one,
  as a circle of its own in a bar, and opening the bar's menu claimed its key
  twice and threw. Every destination call now draws through the scope composing
  the place it is called from. `NavScope` already carried a DSL marker; that
  refuses an implicit outer receiver, but cannot see a captured one.
- **On iOS, swiping back works over a list that is still moving.** A list moving
  from a throw takes the press that lands on it, to stop, and the swipe back
  read that as a control taking the press and left it alone — from the very
  edge as well, and for seconds after a throw, which keeps moving long after it
  stops looking like it. A press that catches a list moving up or down no longer
  counts: a sideways swipe goes back, and one up or down is still the list's.

---

## 0.4.1 — 4 October 2026

A `Hero` bar a mouse wheel could not open again, a segmented control's thumb
that jiggled on a click, and a page indicator you can drag along. Nothing
breaks: an app on 0.4.0 moves to 0.4.1 by changing the version. To get the
wheel fix, put `Modifier.topBarCollapse(state)` where
`Modifier.nestedScroll(state.nestedScrollConnection)` was.

### Added

- **`Modifier.topBarCollapse(state)`** wires a
  [collapsing top bar](components/top-bar.md#large-and-how-it-collapses) to the
  content's scroll. It takes the place of
  `Modifier.nestedScroll(state.nestedScrollConnection)`, and answers a mouse
  wheel as well, below. The connection is still there for wiring the scroll
  yourself.
- **A [`PageIndicator`](components/page-indicator.md#dragged-along-page-by-page)
  can be dragged along.** With `onPageClick`, a press that moves along the dots
  goes to each page it passes, with a `Snap` for each. A pill in the theme's
  `pill` shape grows out of the dots while it is held, and back into them on
  release: `surfaceSunken` in the light, `outline` in the dark. Across or down,
  as the strip runs. A drag used to be a tap wherever it ended.

### Fixed

- **A `Hero` bar scrolled away with a mouse wheel opens all the way when it is
  scrolled back.** It stopped part-way: a list at its top declines a wheel's
  notch before it becomes a scroll, so the bar opened by what was left of the
  notch that brought the list home, and no further. A `Large` bar's collapse is
  short enough for that remainder to cover, and a `Hero` bar's is not. With
  `topBarCollapse` on the content, a notch the list declines finishes the bar the
  way the wheel turned, open or collapsed. Put it where
  `Modifier.nestedScroll(state.nestedScrollConnection)` was to get this.
- **A clicked segment's thumb goes straight across.** A press leans the thumb
  toward the segment, and on release the lean sprang back while the slide began
  from rest, so the thumb stepped back before it went: a jiggle on every mouse
  click, and on a held tap. The slide now starts from where the lean left it.
  The same goes for a `NavBar`, `NavRail` or `TabBar` marker.

---

## 0.4.0 — 3 October 2026

Pages that cover the nav bar on one back stack, a back button that knows its
page, two new components, a top bar over a picture and a tag inside a chip, a
sheet that reaches the top
of the window and clears the keyboard once, and one break: `markerDraggable`.
[Migrating](migrating.md#03-to-04) has everything an upgrade from 0.3 has to
change, as code.

### Breaking

**`NavBar(markerDraggable)` is now `markerDrag: NavBarMarkerDrag`**, and on
`NavigationSuiteScaffold` `barMarkerDraggable` is `barMarkerDrag`.
`markerDraggable = false` is `NavBarMarkerDrag.Off`, and the default is
`NavBarMarkerDrag.SelectOnRelease`, as it was. The new
`NavBarMarkerDrag.SelectWhileDragging` chooses each destination as the marker
reaches it. See [Migrating](migrating.md#markerdraggable-is-now-markerdrag).

### Changed

- **A [`WheelSelect`](components/wheel-select.md#when-to-use-it) keeps its value
  on the field near the top or foot of the window.** It opens about the field
  where there is room above and below, as before. Where there is not, it opens
  away from the edge: the rest of the wheel runs to the side with room, as a
  `Select`'s menu picks its side, and half of the next row fades out on the
  other. Before, it was pushed along to fit, and its value was no longer on the
  field.
- **`Modifier.parallax` is subtler, and stops when the page does.** A spring
  chased its offset and was started over on almost every frame of a scroll, so
  the content moved rigidly with the page and then glided on by itself for half
  a second after it stopped, by up to 30dp near the window's edges. The offset
  now follows the page directly, eases off towards 16dp, and the default
  `ScrollEffectDefaults.ParallaxStrength` is `0.05`, down from `0.08`.
- **Icons come from Kontour Labs' own build of Tabler**,
  `io.kontour:tabler-icons-outline` and `-filled`, in place of composables'
  `icons-tabler-*-cmp`: every icon of Tabler 3.48.0, where composables' stopped
  at an older release. The library's few system icons are drawn from it, so it
  comes with the library, from the same GitHub Packages repository. The API is
  the same under `io.kontour.icons.tabler`; a few glyphs Tabler has redrawn
  since, the star among them, are a pixel or two different.
- **A coach-mark tour's last step says Done**, beside Back, and the way on is
  filled in the bubble's ink so it reads as a button. It said "Got it", and
  next to Back as two ghost buttons on the accent the pair read as words. A
  tour of one step still says "Got it". `Strings.done` is new.
- **A swipe row's actions are at most 64dp tall**, a two-line row's height,
  however tall the row. On a taller row they sit centred, and a full swipe's
  takeover grows across the strip and not down it. Before, they were as tall
  as the row. `SwipeActions(maxActionHeight = Dp.Infinity)` keeps the old
  height.
- **`StepProgress(gap = 0.dp)` is one continuous bar**, with no seams and a fill
  that stops round at the end of its step. Before, its segments met at their
  round ends and the bar pinched at every step.
- **A disabled control is quieter than an enabled one, everywhere on it.** A
  switch's thumb, a slider's and a range slider's thumbs and a knob's face are
  the page's colour, where the switch's was near-black in light and white in
  dark. A slider's thumb is ringed in the disabled grey. A disabled knob is
  drawn in that grey rather than faded to 38%, which kept a coloured scale
  coloured. A disabled rating's empty marks are fainter than its filled ones. A
  stepper's value, and a field's placeholder, are in the disabled grey. See
  [Accessibility](accessibility.md#disabled-is-quieter-everywhere-on-the-control).
  `SliderDefaults.colours(thumbDisabled)` now defaults to `surface`.
- **A checkbox, radio button or switch in a disabled `SelectionRow` or
  `ListItem` draws itself disabled.** Before, it looked as if it could still be
  changed unless it was given `enabled = false` as well.

- **A `NavBar` with labels is the height of one without.** A labelled
  destination's icon is 20dp, two points above its label, and the two are
  centred in the touch target: a floating bar is 64dp with labels or without,
  where it was 82 with them. Its label and badge stay inside the marker round
  the current destination. A label is ellipsised where the marker's rounded
  bottom runs out, and a badge is moved in off the marker's edge. A bar with
  `NavBarDefaults.glyphMarker()` keeps its 40dp circle and the label under it.
- **A sheet at full height reaches the top of the window**, under the status
  bar, with its top corners the display's own, and its drag handle and header
  padded below the bar. So does one at `Expanded` whose content is taller than
  the window. Both used to stop 12dp short, and a tall `Expanded` sheet a status
  bar short. A floating sheet without `edgeMorph` keeps its margin.
- **A sheet's header keeps clear of a display's rounder corners.** On an iPhone,
  whose corners are about 55pt, or a newer Pixel's 51dp, against the sheet's
  34dp, the header, the drag handle's band and a part pinned to the top move in
  by what the rounder corner cuts into its diagonal: 6dp at 55. The title's
  corner had come within 5dp of the curve. A sheet with the theme's own corners
  is unchanged.
- **A modal opening takes the focus off whatever had it, in another overlay
  too.** A text field in a sheet lets go, and its keyboard goes, when a dialog
  opens over the sheet. Before, only the page lost its focus. A menu or a
  combobox's list leaves the field it opened from focused, as before.
- **Date pickers are a phone's size wherever they are, and shorter.** A day
  cell stops at 44dp, so a calendar stops at 308dp (it was 511dp, with digits
  half as large again); a week is at most 40dp tall where it was as tall as a
  day is wide; and the header and the weekday initials sit closer. At a
  phone's width a `DatePicker` is 307dp tall rather than 379dp. A chosen
  day is a circle as tall as its week, and the arrow a range's dragged end
  shows on a month's first or last day is sized to the day, beside its number
  rather than against it.
- **A command palette puts the closest commands first.** With something typed,
  the label that is the query comes first, then one that starts with it, then a
  word in a label, then a keyword, then the query inside a word; within each,
  the one it covers more of. It kept the caller's order, so the highlight, and
  Enter, could sit on a command that only had the query in the middle of a word.
  With nothing typed the order is the caller's, as before. The ranking is
  `commandSimilarity`; a `similarity` parameter beside `matches` takes another.

### Added

- **A selection marker reaches for the item being pressed.** In a `NavBar`, a
  `NavRail`, a `TabBar` and a `SegmentedControl`, a press on another item leans
  the marker a little toward it, stretching from the edge it is leaving, before
  the finger lifts and the marker goes there. A press that slides off lets it
  settle back. A dragged nav-bar marker now stretches as it leans toward the
  finger, as a segmented control's thumb does. None of it under reduced motion.
  See [`NavBar`](components/nav-bar.md#the-marker-reaches-for-a-press).
- [`CircularStepProgress`](components/progress.md): `StepProgress` round a
  ring, one arc a step from twelve o'clock, through the same animation.
  `gap = 0.dp` makes it one continuous ring.

- **`pageBack()`**, for a back button: going back from the page it is read in,
  or null when back would not leave it. `TopBar(onBack = pageBack())`. A button
  shown while `backStack.size > 1` appeared on the root page during a back
  gesture, because the stack holds the page leaving until the gesture ends. Set
  by `:ui-nav3`'s pages and panes and a one-pane `ListDetailPaneScaffold`'s
  detail; `LocalPageBack` and `rememberPageBack` for a host of your own. See
  [Navigation 3](navigation3.md#a-back-button-from-the-page).

- **Pages over the nav bar, with one back stack:** `:ui-nav3`'s
  `rememberNavigationSuiteSceneStrategy` puts a `NavigationSuiteScaffold`, or
  any frame, round the pages of a `NavDisplay`, so the bar stays put as they
  move, and an entry marked `coversNavigation()` slides in over the bar and the
  page under it. `navigationSuitePadding()` is the bar's height, for a page
  inside the scaffold that is not written in its `content`. See
  [Navigation 3](navigation3.md#pages-inside-the-nav-bar-and-pages-over-it).

- **A tag at the end of a chip:** `FilterChip { +"Buses"; tag { +"3" } }`. A
  count or a "New" inside the chip's pill, read with the chip as one, "Buses,
  3"; the chip's end comes in so the tag sits concentric with it. `Chip`,
  `FilterChip` and `InputChip` take their content as a `ChipScope`, which is a
  `RowContentScope` with `tag` added, so every existing chip compiles as it
  was. See [`Chip`](components/chip.md#a-count-at-the-end).

- [`TopBarStyle.Hero`](components/top-bar.md#hero-a-large-bar-over-a-picture):
  `TopBar(style = TopBarStyle.Hero, image = { … })` is a `Large` bar over a
  picture that runs under the status bar, collapsing into a `Small` bar as
  `Large` does. The picture runs under the title and fades into the bar's
  colour there, the title standing over the fade in the bar's ink, with a solid
  band at high contrast; the back button and actions sit on discs of the bar's
  colour until the picture has gone.
  `expandedHeight` sets how tall it opens: `TopBarDefaults.HeroHeight`, 360dp,
  from `ComponentDefaults.topBarHeroHeight`, unless you say. On `Large` it is
  the least the bar opens to.

- [`TopBar(fadeUnder)`](components/top-bar.md#content-fading-under-the-bar):
  content passing under a bar fades out beneath it rather than stopping square
  at its foot, the bar's ground thinning out over its last 12dp and gone 4dp
  past it. Any style, off
  by default, and none at high contrast. The bar has to be over the content, as
  a `Scaffold` puts it.

- [`Avatar(border)`](components/avatar.md#a-border): a ring round the avatar,
  any `BorderStroke` — a colour, the accent, a gradient — over the picture's
  edge and under its decorations.

- [`DateTimePicker`](components/date-time-picker.md): a `DatePicker` and a
  `TimePicker` as one `LocalDateTime`, side by side from 560dp and one at a
  time under a segmented control narrower. A day tapped keeps the time and a
  wheel turned keeps the day; `onValueChangeFinished` comes once a day is
  tapped or a wheel has come to rest.

- [`TextFieldVariant.Plain`](components/select.md): a field with no ground and
  no edge at rest, for a select at the end of a row or in a sentence — every
  select takes it, `Select`, `MultiSelect`, `Combobox` and `WheelSelect`, and
  stays outlined by default. Focus brings back the tint and the accent edge, an
  error its edge, and at high contrast the edge stays.

- **Wheels say when they have stopped.** `WheelPicker(onSelectedIndexChangeFinished)`,
  `WheelSelect(onValueChangeFinished)` and `TimePicker(onValueChangeFinished)`
  are called once a wheel comes to rest after the reader turned it, as a slider's
  and a rating's already were once the finger lifted. `onValueChange` still
  follows every row passed; a save, a request or a toast belongs in the new one.
  Not called when the caller sets the value.

- [`listSection`](components/list-section.md#in-a-lazy-list-listsection): a
  titled group of rows inside a `LazyColumn`, as `ListSection` is outside one.
  It draws the heading, each row's `position`, the hairline between rows, the
  footer and the space before the next section, which a lazy list's single
  `verticalArrangement` could not, and its rows slide when the list changes
  (fading in place under reduced motion). A section per `indexedBy` group is
  where an `IndexScroller`'s letters land.

- [`IndexScroller`](components/index-scroller.md): an A–Z rail beside a long
  list. Press a letter or run a finger down them and the list jumps to its
  section, with a tick a letter and the letter shown large beside the finger.
  Every letter stays on the rail, the empty ones dimmed and going to the next
  section; a short rail folds runs of letters into dots and still reaches each.
  `IndexAlphabet` files a word under its label in Latin, Cyrillic, Greek,
  Hebrew, Arabic, kana rows and Hangul initials, by the reader's locale, and
  `indexedBy` groups a list and says where each section starts. Four new
  `Strings`: `sectionIndex`, `otherSection`, `nextSection`, `previousSection`.

### Fixed

- **A card carried by `sharedBounds` keeps its shadow while it travels.** Its
  contents cross-faded in a layer as big as the card, and a layer at part
  opacity is drawn offscreen at its own size, so the shadow outside the card was
  cut off for the whole of a `PageTransition` and came back the frame it ended.
  The cross-fade now fades each thing as it is drawn, with no layer to be cut
  by.
- **A sheet over a text field is lifted clear of the keyboard once.** A bottom
  sheet, a side sheet, a dialog and a toast count only the part of each inset
  that reaches their container. In an app whose root had already made room for
  the keyboard, with `imePadding()` or a padded scaffold, a sheet was lifted by
  the whole keyboard again and stood a keyboard's height above it. On Android, a
  debuggable app whose window pans for the keyboard, which lifts a sheet twice in
  a way the library cannot see, now gets one line in its log saying so.
- **A drag that starts on another segment or destination ticks as the marker
  jumps to it.** Press a segmented control's third segment, or a nav bar's
  third destination, while the first is chosen, and drag on: the thumb or
  marker goes to the third with a tick, then on. That first jump was silent,
  because the detent was armed on the item under the finger rather than the one
  the marker was on. A segmented control's thumb also ran one pointer move ahead
  of the finger for the whole of a drag, so a first move that stopped just short
  of a boundary crossed it.
- **A rating's stars sit together on a touch screen.** Each 24dp star reserved
  a 48dp target of its own, so on a phone there was 28dp between one star and
  the next, more gap than star. The row takes every press itself, so it is the
  target now: at least a target tall, with the stars `RatingDefaults.Gap` apart,
  as a read-only rating's always were.
- **The month-change arrow in a range drag is easier to catch.** Dragging a
  range's end past the month's first or last day starts the month turning only
  once the finger has passed the little arrow there, and that meant the arrow's
  own row: a finger a little below the last week, or coming at it from just
  above, went past without starting it. It now catches half a row outside the
  grid and a third of a row toward the next week in. The blank beside the day,
  where a finger arriving from the week above lands, still does not.
- **A range dragged over days it may not have stops at the last it may.**
  Paged into a month that `isDateSelectable` closes part of, a range kept the
  last month's end and the whole new month was painted as in it, until the
  finger found a day it could have. Over a day it may not have, a dragged range
  now reaches as far toward the finger as it can, in any month, and paints
  nothing past that.
- **A sheet dragged to the top by its content climbs with the finger.** Pulled
  up from a lower detent by a scroll in its content, under a status bar, a sheet
  that reaches the top leapt the last status bar's height in one frame. Its
  content waits below the bar while its edge goes on to the top, and the peek
  detent, measured against the edge, changed with every pixel of that climb;
  each change rebuilt the anchors, and with nothing holding the drag a rebuild
  snaps to the nearest one. The peek is now measured in the content's own
  frame.
- **A bottom toast stands above the navigation bar.** A toast raised on a page
  with a `NavBar` came up over the bar, on top of the destinations. The bar now
  tells the overlay host where it is, and a bottom `ToastHost` stands a little
  above it, or above the window's insets, whichever is higher. A page that covers
  the bar brings the toasts back down with it; a bar partway down a page is left
  alone.
- **A quick back swipe on Android no longer stalls as it is let go.** The
  release read the finger's speed from the last 40ms of the platform's events,
  and a quick swipe can leave fewer than two there: measured as nothing, the
  page stopped on the lift and then picked up. It now takes the swipe's speed
  over its whole length where that is quicker, and never leaves slower than
  half its pace. A two-pane detail let go at the end, as the relay hands it
  over, pops at once rather than waiting out a fixed 200ms.
- **A toast has an edge over anything of its own colour.** The toast in front
  takes the rim the ones waiting behind it already had, a hairline of its own
  content colour: over a FAB, near-black on near-black, the two used to run
  together, and a black shadow shows on nothing that dark.
- **A list comes to rest once after a back gesture.** Back from a page over a
  `listPane()` entry, carried all the way by the gesture as a swipe's release
  does, finished, then the list jumped back three quarters of the way and came
  in again. An app that reads its own back stack builds its entries afresh on
  every pop, and the list's role was equal only to itself, so the scene the pop
  settled on was not the one the gesture had brought in. Every `listPane()` is
  now equal to every other.
- **A text area's placeholder starts at the top, where its text does.** It was
  centred in the area, a line or more below where the first character goes, and
  cut to one line however long it was. It now sits at the top start (the top
  right, right to left) and wraps over the lines the empty area has. In a
  single-line field the caret of an empty field sat a pixel below the
  placeholder and jumped up a pixel at the first character; it now starts on
  the placeholder's line.
- **An open search keeps clear of every edge.** `NavSearch` and
  `NavExpandingSlot` stood their panel on the keyboard and cleared only the
  bottom and the sides, so results tall enough to reach the top of the window
  ran under the status bar; hung from the top, long results ran under the
  keyboard. The panel now clears the window's insets and the keyboard on every
  side, wherever it stands, and both take a `windowInsets` for a caller that
  clears an edge itself.
- **A command palette keeps clear of the window's sides.** It took its width
  cap and nothing else, so on a phone it ran from one edge of the glass to the
  other. It now keeps `Theme.spacing.md` from either side, as a `Dialog` and a
  bar's expanding panel already kept a margin of their own.
- **A command palette's results fade at an end there are more of them.** They
  were cut off square at the panel's foot, and a row cut off at the edge reads
  as the last one. They now take `fadingEdges`, which stands down at high
  contrast as it does everywhere.

---

## 0.3.0 — 30 September 2026

Sheets that pin a part and keep one inset, a nav bar you can drag across, three
new components, and one break: `SettingRow`. [Migrating](migrating.md#02-to-03)
has everything an upgrade from 0.2 has to change, as code.

### Breaking

**`SettingRow` is removed. Use `SelectionRow(onClick = …)`**, which takes the
same content: `+"Theme"`, `leading {}`, and the current value in
`trailing {}`. Like `SettingRow`, it is a silent `Role.Button`, and
`onClick = null` shows a value that cannot be changed there. One default
differs:

- `SettingRow` defaulted to `position = GroupPosition.Only`, which drew the
  sunken card.
- `SelectionRow`'s `position` defaults to `null`, which draws a bare row.
- To keep the card, pass `position = GroupPosition.Only`.

A `SettingRow` that held a switch should be
`SelectionRow(selected, onSelectedChange, role = Role.Switch)`.
`ListGroupScope.setting(…)` in a `ListSection` is unchanged.
See [`SelectionRow`](components/selection-row.md#a-row-that-opens-a-choice).

### Changed

- **A swipe row's actions keep a lone row's shape in a group.** Before, they
  took the row's `shape`. In a group, a middle row's actions came out nearly
  square, and a first row's were rounded only on top. `SwipeActions` and
  `SwipeToDismiss` now draw their actions in `actionShape`, which defaults to
  `ListItemDefaults.Shape`. If your rows have their own corners, pass the same
  shape as `actionShape`.
- **A `NavBar`'s destinations share it in equal cells, and its marker is round
  the whole current destination**, glyph and label, in the bar's own shape: a
  capsule in a floating bar. Before, each destination was a circle and the marker
  a circle behind its glyph. For that look, pass
  `itemWidth = NavBarItemWidth.Natural` and
  `marker = NavBarDefaults.glyphMarker()` (on `NavigationSuiteScaffold`,
  `barItemWidth` and `barMarker`).
- **A `NavBar`'s marker can be dragged** from destination to destination, with
  a snap at each. The destination under the finger when it lifts has its
  `onClick` run once, and a tap is still a tap. `markerDraggable = false` turns
  it off, and on `NavigationSuiteScaffold` it's `barMarkerDraggable = false`.
- **A tap on a `NavBar`'s surface between destinations stays on the bar.**
  Before, it reached the page beneath.
- **A floating `NavBar`'s ends match the gaps between its destinations.**
  Before, the ends had about a third more room. A bar given its own
  `arrangement` keeps the old padding.
- **`NavigationSuiteScaffold`'s `action` sits beside a floating bar**, not
  inside it. A docked bar keeps it at the end.
- **A `Large` `TopBar` collapses by carrying its title into the bar, and the bar
  shrinks.** Before, the title faded between two sizes at a fixed height.
  Its subtitle travels with it, and the collapsed bar is a `Small` bar with
  the subtitle under its title, where before the subtitle faded out.
  Because the bar now shrinks, a list under it moves too, so driving
  `collapseProgress` from the list's scroll now moves the rows twice as far as
  the finger. Pass `collapseState = rememberTopBarCollapseState()` instead, and
  give the list its `nestedScrollConnection`. The bar then takes the scroll it
  gives up, and the list takes the rest.
- **On Android, a back gesture finishes at the speed the finger let go at**, in
  pages, panes and overlays alike. Before, it finished at a fixed pace, so a
  flick slowed down when it was released. A back button press goes through at
  once.
- **On iOS, swiping back takes no more effort than in a native app.** Letting go
  is decided by where the page would come to rest if it coasted on, by UIKit's
  own deceleration, so a swipe at an ordinary pace a third of the way across
  goes back. Before, it had to pass half the width or go faster than 1000dp a
  second. A swipe more sideways than up or down counts from anywhere on the
  page, not only one twice as sideways, and the page stays under the finger
  rather than a touch slop behind it.
- **A trackpad or a mouse can make a full swipe on a `SwipeActions` row.**
  Carried past the point of no return by a sideways scroll or a two-finger pan,
  the row runs its outermost action when the gesture ends there, as it does
  under a finger. Before, a scroll could open the actions but never run one.
- **A bottom sheet's header has no forehead.** The drag handle's 24dp band is
  the content's top padding, and a `SheetHeader` under it adds nothing more: it
  measures to what it draws, so its close button's empty top reaches into the
  band, and its title is 16dp higher than it was. It has
  `BottomSheetDefaults.ContentPadding`, 16dp, beside it and below it, and so do
  pinned parts. Before, a collapsed header had 44dp above its title and 16dp
  beside it.
- **A bottom sheet is at most 480dp wide on a wide window**, a side sheet's
  width, where it was 640dp: half of a modest desktop. Set
  `ComponentDefaults.sheetMaxWidth` to keep the old width.
- **A `RangeSlider` takes a finger on each thumb**, and both thumbs can be
  dragged at the same time. Before, a second finger did nothing.

### Added

- [`WheelSelect`](components/wheel-select.md): a field that opens into a wheel
  when tapped, and turns the wheel when dragged up or down while closed.
- [`DisclosureListItem`](components/disclosure-list-item.md): a row that opens
  onto more of itself, inside the same card.
- [`Modifier.pressable`](components/modifier-pressable.md): makes a component of
  your own pressable the way the library's are. It brings the press, hover,
  focus ring, cursor and 48dp target, and takes a long press, felt once, and a
  double click.
- `pinned { }` in a sheet's content: a part pinned to the bottom of what the
  sheet shows, or with `SheetPin.Top` under its handle, drawn over the content,
  which scrolls behind it. On the sheet's colour, or with
  `containerColour = Color.Transparent` the content goes under it and shows
  round it. `SheetDetent.pinned()` is the size that shows the pinned parts and
  nothing else: a sheet that collapses to its search field. See
  [`BottomSheet`](components/bottom-sheet.md#header-and-footer--the-sheets-top-and-foot-with-the-content-passing-behind).
- `SelectionRow(onClick)`, and `position` on both `SelectionRow` overloads, so
  a group can mix toggles, rows that open a choice, and plain rows.
- [`NavBar`](components/nav-bar.md): `shape`; `itemWidth`
  (`NavBarItemWidth.Natural` or `Equal`); `marker`
  (`NavBarDefaults.glyphMarker()` or `destinationMarker()`); `markerDraggable`;
  and `action`.
- `BottomSheetDefaults.ContentPadding`, the one inset a sheet keeps round its
  content.
- `NavigationSuiteScaffold` passes those on as `barShape`, `barItemWidth`,
  `barMarker` and `barMarkerDraggable`.
- `PageIndicator(orientation = Orientation.Vertical)`.
- `TopBarCollapseState`, `rememberTopBarCollapseState()` and
  `TopBar(collapseState)`.
- `SwipeActions(actionShape)` and `SwipeToDismiss(actionShape)`.
- `SystemIcons.ChevronUpDown`.

### Fixed

- **A `Pagination` with `allowJump` spread out.** Each tappable ellipsis
  reserved a whole touch target, 44dp on iOS and the web and 48 on Android, so
  the row grew and a phone showed fewer pages. A gap is now 24dp wide whether it
  can be tapped or not, so turning `allowJump` on moves nothing. A plain gap is
  a few dp wider than it was.
- **A long press lost after switching input.** A component's long press
  stopped working after the input changed between mouse, keyboard and touch,
  for example after the first tap on a laptop with a touchscreen.
  `Modifier.pointerCursor` removed its node from the modifier chain when the
  input changed. It now keeps it there.
- **A tap on a bar or a panel reached what was underneath.** A tap on a
  `TopBar` between its title and its actions pressed the row scrolled under it,
  and the same went for any blank part of a `Surface`: a card over a map, a
  toolbar over a canvas, a tooltip, coach mark or popover with no scrim over the
  page. `NavBar` had been fixed for this on its own. Every surface now keeps
  the taps that land on it from what is beneath it, as Material's does. The
  bars — `TopBar`, `NavBar`, `NavRail`, `TabBar` and `Toolbar` — also keep a
  press on nothing from a clickable container round them, so a toolbar inside
  a drawing canvas no longer draws. A drag from a bar still scrolls the page.
- **The page jumped when one overlay opened over another.** With a modal sheet
  open, a dialog brought the pushed-back page forward to full size in one frame,
  and pushed it back once the dialog had gone. A sheet over a sheet did the same,
  and the page lightened and darkened again round any second dimmed overlay. The
  host took the backdrop and the dim from the topmost dimmed overlay alone. Every
  open overlay now keeps doing what it asked to, so the page stays where it was.
  The overlay underneath dims, and blurs under a dialog, with the one above it.
  See [several overlays at once](overlays.md#several-overlays-at-once).

---

## 0.2.2 — 29 September 2026

The first release installed from a tag that still exists. The library code is
the same as 0.2.1. Only the release's CI and its install instructions changed.

**0.2.0 and 0.2.1** were tagged on 28 and 29 September. Their release runs failed
while building the documentation site, and their tags were later removed. 0.2.2
contains everything in both, so install 0.2.2 rather than either of them.

### Changed, since 0.2.0

- **A swiped toast shrinks away where it was let go.** Before, it slid off the
  way a toast that times out does, and its fade clipped it at its resting edge,
  so most of it vanished at once. The cards behind now ease back into place
  instead of snapping there as it goes.
- **A `NavBar` search, or any weighted `slot`,** stops at a maximum width
  rather than stretching across a wide window.
- **In a browser, a sideways scroll moves a `SwipeActions` row** a pixel at a
  time. It read each pixel as a wheel notch, so one trackpad event threw the row
  past every action it had.
- **`Modifier.revealOnScroll`:** a card's shadow fades in with the card.
  Before, the fade cut the shadow off, and it came back in one frame as the fade
  ended.

### Added, since 0.2.0

- New `richText` verbs: `underline`, `style(typography.titleLarge, …)` for a
  role from the type scale, `highlight`, `colour` and `gradient`. See
  [Text](components/text.md).

---

## 0.2.0 — 28 September 2026

The first release anyone used, and where these pages begin.

0.1.0 was published by hand on 16 August 2026 and never tagged. Nobody built on
it, so what moved between the two is not listed here. The migrating guide has
the full list of renames, reorders and changed types. It stood until 0.2.2 and
is kept in the repository's history:
[Migrating, 0.1.0 to 0.2.0](https://github.com/kontour-labs/ui/blob/7717dec541b87a794769a49af4c0e14d9bff2c88/ui-docs/content/migrating.md).

What 0.2.0 is, in outline:

- **Three modules at one version:** `:ui`, the components, tokens and theme;
  `:ui-nav3`, the pane scaffolds and page motion for Navigation 3; and
  `:haptics`, one haptics player for Android, iOS, the web and macOS.
- **Components,** across actions, selection, text editing, date and time,
  display, collections, overlays, sheets, navigation and adaptive layout. See the
  [map](components.md).
- **Back handled for you:** Android's predictive back and iOS's swipe back, in
  pages, panes, sheets and dialogs. A modal that may not be dismissed keeps back
  to itself. See [Overlays](overlays.md) and [Navigation 3](navigation3.md).
- **High contrast and reduced motion,** answered by every component. Each
  component's page has an Accessibility section that says how. See
  [Accessibility](accessibility.md).
- **Localisation,** with no bundled translations: a locale, with number, date
  and plural formats; `Strings` built for word order and plurals; pseudo-locales
  for testing; and right-to-left layout throughout. See
  [Localisation](localisation.md).
- **Haptics** by intent rather than by vibration, with a policy for what buzzes
  and what doesn't. See [Haptics](haptics.md) and [Theming](theming.md).
- **Text:** `richText` and inline Markdown, and `SelectableText` for a region
  whose text can be copied.
- **The Kontour Labs violet** as the default accent.
