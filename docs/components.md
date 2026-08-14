# Components

The inventory. Each entry says what the component is for and, where it matters,
what to reach for instead.

Status is per phase — see [README](README.md#status). Components not yet built
are listed so the shape of the finished system is visible.

---

## Actions — built

### `Button`

```kotlin
Button("Plan a trip", onClick = ::plan)

Button(onClick = ::delete, variant = ButtonVariant.Destructive, size = ButtonSize.Small) {
    Icon(Icons.Trash, contentDescription = null)
    Text("Delete")
}
```

Six variants, chosen by *importance* rather than appearance:

| Variant | For |
|---|---|
| `Primary` | The one action the screen exists for. At most one per screen |
| `Secondary` | A real alternative to the primary action |
| `Tertiary` | Supporting actions that should not compete |
| `Ghost` | Lowest weight — toolbar actions, inline "edit" |
| `Destructive` | Deletes, cancels, ends a trip |
| `DestructiveGhost` | A destructive action inside a menu or row |

Five sizes, `XSmall` to `XLarge`, drawn from `Theme.sizing.controlHeight*` so a
row of mixed buttons, inputs and selects lines up without per-call-site padding.
`Medium` is the default; `Large`/`XLarge` are for a screen's single main call to
action.

**Loading** swaps the label for a spinner *without changing the button's width*,
so a row of buttons does not reflow when one is pressed. The button also blocks
input and announces itself as busy while loading — a screen-reader user is not
left tapping a control that already took their input.

Disabled styling is shared across variants on purpose: a disabled outlined
button and a disabled solid one both mean "not available right now", and should
not look like two different controls.

### `IconButton` / `IconToggleButton`

```kotlin
IconButton(Icons.Close, contentDescription = "Close", onClick = ::dismiss)
IconButton(Icons.Chevron, "Expand", ::toggle, rotation = if (expanded) 90f else 0f)
IconToggleButton(icon, "Favourite", checked = isFavourite, onCheckedChange = ::setFavourite)
```

`contentDescription` is **required and non-null**. There is no visible text to
fall back on, so an icon button without one is a control a screen-reader user
cannot identify. If the icon is genuinely decorative, it is not a button.

Visual bounds stay small — icon plus padding — while the touch target expands to
the platform minimum around it. That is why a toolbar of 20dp icons is still
usable with a thumb.

Prefer `rotation` over swapping between two icons for disclosure chevrons and
menu/close morphs; a swap reads as a flicker, a rotation reads as a flip.

### `FloatingActionButton` / `ExtendedFloatingActionButton`

```kotlin
FloatingActionButton(Icons.Plus, "Add favourite", ::add)

ExtendedFloatingActionButton(
    icon = Icons.Navigation,
    text = "Start trip",
    contentDescription = "Start trip",
    expanded = !listState.isScrollingDown,
    onClick = ::start,
)
```

One per screen. A second FAB is two competing "the" actions.

The extended variant animates its *width* when collapsing rather than
cross-fading between two components, so the icon stays put and the label slides
out from behind it. Cross-fading makes the icon appear to jump sideways.

`contentDescription` is separate from `text` because the label may be terse
where the announcement should not be — "Start" on screen, "Start trip to Perth
Station" for a screen reader.

### `Spinner`

An indeterminate activity indicator. The arc sweeps *and* breathes — its length
grows and shrinks as it rotates, so the tail chases the head. Under reduced
motion the breathing stops and the arc holds a constant length.

Pass `contentDescription = null` when it sits inside something that already
announces itself as busy; a loading `Button` does, so its spinner is silent.

---

## Selection — built

### `Checkbox` / `TriStateCheckbox`

The tick is drawn on a `Canvas` and *strokes itself on* along its path rather
than fading in, with the box springing up to meet it. Two frames of personality
on a control people tap dozens of times a session.

`ToggleableState.Indeterminate` draws a dash, for a parent whose children are
partly selected. Clicking an indeterminate checkbox should select everything,
not clear it — that is the caller's decision, and the common wrong answer.

### `RadioButton` / `RadioGroup`

Use `RadioGroup` rather than loose buttons. Owning the selection there is what
lets the group apply `selectableGroup()`, which is what makes a screen reader
announce "option 2 of 5". It also makes the invalid states — two selected, or
none — unrepresentable.

### `Switch`

Use a switch for a setting that takes effect **immediately**, and a `Checkbox`
for one that is part of a form and takes effect on submit. A user who flips a
switch expects the thing to have happened; a user who ticks a box expects to
press Save.

The thumb stretches as it travels — wider mid-flight, round at rest. The off
track is bordered and unfilled rather than grey-filled: as the marketing site's
own notes observe, a grey track sits too close in tone to the surfaces it is
toggled on top of to read as a distinct control.

### `SelectionRow`

```kotlin
SelectionRow(
    label = "Notify me about delays",
    supporting = "Only for favourited routes",
    selected = notifyOnDelay,
    onClick = { viewModel.setNotifyOnDelay(!notifyOnDelay) },
    role = Role.Checkbox,
    control = { Checkbox(notifyOnDelay, onCheckedChange = null) },
)
```

**This is the form almost every checkbox, radio and switch should take.** A bare
control with a `Text` beside it gives the user a small target and gives a screen
reader two nodes for one choice. The nested control takes `onClick = null` — the
row owns the interaction, the control is there to show state.

### `Chip`, `FilterChip`, `InputChip`, `ChipGroup`

Chips are for things that come in *sets*. A single chip on a screen is usually a
small button wearing the wrong clothes.

A selected `FilterChip` grows a tick in front of its label; the tick expands in
and shoves the label across, which is what makes a filter bar feel responsive
when you rattle through several. `InputChip`'s remove button is a separate
target with its own description, so a screen reader offers "Perth Station" and
"Remove Perth Station" as distinct actions.

`ChipGroup` wraps onto new lines rather than scrolling horizontally — a scrolling
row hides options off the edge of the screen.

### `SegmentedControl`

Two to four short options the user switches between often. Beyond four, or with
long labels, use a `RadioGroup` or a `Select`; segments get too narrow to read
and too narrow to hit.

The indicator is a single surface that **slides** between positions rather than
each segment fading its own background — that is what makes it read as one
physical thing with a moving part.

### `Slider`

The thumb grows while dragged and settles back with a bounce. Each step crossed
on a stepped slider fires a tick haptic, so a user changing a value without
looking can feel the detents — which is most of the point of having steps.

Pass `stateDescription` to turn the raw value into something a screen reader can
say. Without it the announcement is a bare percentage, which is rarely what the
number means.


---

## Text editing — built

Built on foundation's state-based `BasicTextField`, so the caller owns a
`TextFieldState` rather than a `String` plus a callback:

```kotlin
val query = rememberTextFieldState()
TextField(state = query, label = "Where to?", placeholder = "Station, stop or address")
```

`TextFieldState` is the right default because it makes the two classic bugs
unrepresentable: the caret jumping to the end when text is edited
programmatically, and characters dropping under fast typing because state
hoisting round-tripped through a recomposition.

| | |
|---|---|
| `TextField` | Label, placeholder, supporting text, error, leading/trailing slots |
| `TextArea` | Grows between `minLines` and `maxLines`, then scrolls internally |
| `SearchField` | Debounced query callback, animated clear button |
| `PasswordField` | Reveal toggle, autofill content type |
| `NumberField` | Digits or decimals, rejected at the keystroke |
| `PhoneField` | Live mask, digits stored clean |
| `EmailField` | Email keyboard, autofill, no capitalisation |

### Validation

`errorMessage` sets `error` semantics *and* colours the border. Colour alone
would fail WCAG 1.4.1, so the message is not optional decoration — it is how a
screen-reader user learns there is a problem.

**Error outranks focus.** A focused invalid field keeps its error border,
because an accent ring would hide the thing the user needs to fix.

Helper and error text share one slot and animate in place, so a form does not
jump by a line height every time validation flips.

### Transformations

`InputTransformation` filters keystrokes *as they arrive* — the rejected
character never reaches the state, so the field cannot flicker through an
invalid value. The best error message is the one that never has to appear.

```kotlin
InputTransformation.digitsOnly()
InputTransformation.decimal(allowNegative = true)
InputTransformation.limit(10)
```

`OutputTransformation` changes what is *displayed* without changing what is
stored:

```kotlin
phoneMask()   // stored "0412345678"  displayed "0412 345 678"
cardMask()
timeMask()
```

That separation is the point: the caller reads clean digits out of the state and
never has to strip formatting back out, which is where mask implementations
usually go wrong.

`InputTransformation.decimal` is deliberately permissive about intermediate
states — `-`, `1.` and `-0.` all pass, because a user typing `-0.5` passes
through every one of them. Rejecting them makes the field impossible to type
into.


---

## Date and time — built

| | |
|---|---|
| `CalendarMonth` | The reusable grid. No opinion about how selection works |
| `DatePicker` | Single date, with month paging |
| `DateRangePicker` | Start and end, with a continuous run between them |
| `TimePicker` | Hour/minute wheels, plus AM/PM on a 12-hour clock |
| `TimeField` | The tappable field that opens one |
| `WheelPicker` | The scrolling drum, for any list of values |
| `RelativeTimeText` | A self-updating "in 4 min" |
| `DateTimeFormats` | 12/24-hour, day-first, first-day-of-week |

`CalendarMonth` expresses selection as *predicates* rather than a value, because
that is the only shape that serves single, range and multi-select without the
grid knowing which mode it is in. Range endpoints get a rounded cap and the
interior stays square, so a run reads as continuous rather than as a row of
separate pills.

`DateRangePicker` follows the rule users expect without being told: the first
tap sets the start and clears any end, the second sets the end, and tapping
before the current start *restarts* the range there rather than producing a
backwards one.

`RelativeTimeText` re-renders at the resolution it is displaying — every second
under a minute, every twenty above — rather than on a fixed timer that is either
wasteful or stale. It rounds **down**: telling someone their bus is 2 minutes
away when it is 90 seconds away is the error that makes them miss it. It is a
polite live region, so a screen reader announces the change.

`DateTimeFormats` carries the two preferences users actually notice — 12/24-hour
and day-first — because "05/06" is two different days depending on the answer.
Provided once at the root through `LocalDateTimeFormats`.

`TimePicker` uses wheels rather than a clock dial. In a transit app the value is
almost always being *adjusted* ("leave at 8:15 instead of 8:00"), and a wheel
gets there in one flick where a dial needs two precise drags.


---

## Foundation — built

| | |
|---|---|
| `Text` | Resolves style and colour from the theme. `String` and `AnnotatedString` overloads |
| `Icon` | Tinted to the surrounding content colour. Takes an `ImageVector` or `Painter` |
| `Surface` | Background, shape, border, shadow — and sets `LocalContentColor` |
| `HorizontalDivider` / `VerticalDivider` | Decorative rules |
| `Scrim` | Dims and blocks input behind a modal |

**The design system ships no icon set.** Components take an `ImageVector` or
`Painter`, so the choice of icon library stays an application decision and `:ui`
does not drag a few hundred kilobytes of glyphs into every consumer.

The app and catalog use **Tabler** (`icons-tabler-outline-cmp`,
`icons-tabler-filled-cmp`). Tabler draws every glyph on a uniform 24×24 grid,
which matters more than it sounds: FontAwesome uses a 512-tall grid of varying
width while declaring every glyph as square, so its icons both distort and
occupy visibly different widths within the same slot. `IconMetricsDiagnostic`
asserts Tabler's uniformity so a future version cannot regress it silently.

`Icon` still corrects for a non-square viewport, so a set that does have one
renders undistorted. With Tabler that correction is a no-op.

---

## Not yet built

Listed so the shape of the finished system is visible. See
[README](README.md#status) for phase ordering.

**Selection, remaining** — `RangeSlider`, `Stepper`, `Rating`, `Select`,
`Combobox`, `MultiSelect`, `ColorSwatchPicker`, `FilePicker`

**Date and time, remaining** — `DurationPicker`, and a multi-month scrolling
calendar for range selection across month boundaries

**Text editing, remaining** — `OtpField`, `TagInput`, `CurrencyField`, plus the
custom desktop/web text toolbar and IME focus-chaining

**Display** — `Card`, `Badge`, `Tag`, `Avatar`, `LinearProgress`,
`CircularProgress`, `ProgressRing`, `StepProgress`, `Skeleton`, `EmptyState`,
`ErrorState`, `Banner`, `Callout`, `Stat`, `KeyValueList`, `Timeline`,
`Accordion`, `Carousel`, `CodeBlock`, `Gauge`, `Marquee`

**Collections** — `ListItem`, `ListSection`, `SwipeActions`, `ReorderableList`,
`PullToRefresh`, `LoadMore`, `Scrollbar`, `DataTable`, `TreeList`

**Overlays** — `Dialog`, `AlertDialog`, `ConfirmDialog`, `DropdownMenu`,
`ContextMenu`, `Tooltip`, `Toast`, `Popover`, `LoadingOverlay`

**Sheets** — `BottomSheet`, `ModalBottomSheet`, `SideSheet`, `SheetHeader`

**Navigation** — `NavBar`, `NavRail`, `NavDrawer`, `TopBar`, `TabBar`,
`Breadcrumbs`, `Pagination`, `Toolbar`, `NavigationSuiteScaffold`
