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

On `main`, and in the next release.

### Changed

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
- **A modal opening takes the focus off whatever had it, in another overlay
  too.** A text field in a sheet lets go, and its keyboard goes, when a dialog
  opens over the sheet. Before, only the page lost its focus. A menu or a
  combobox's list leaves the field it opened from focused, as before.

### Added

- [`CircularStepProgress`](components/progress.md): `StepProgress` round a
  ring, one arc a step from twelve o'clock, through the same animation.
  `gap = 0.dp` makes it one continuous ring.

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
  [`BottomSheet`](components/bottom-sheet.md#pinned--a-part-that-stays-at-an-edge-with-the-content-passing-behind).
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
