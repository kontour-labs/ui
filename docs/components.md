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

---

## Not yet built

Listed so the shape of the finished system is visible. See
[README](README.md#status) for phase ordering.

**Selection** — `Checkbox`, `TriStateCheckbox`, `RadioGroup`, `Switch`,
`Slider`, `RangeSlider`, `Chip`, `ChipGroup`, `SegmentedControl`, `Stepper`,
`Rating`, `Select`, `Combobox`, `MultiSelect`, `ColorSwatchPicker`, `FilePicker`

**Date and time** — `DatePicker`, `DateRangePicker`, `TimePicker`,
`CalendarMonth`, `WheelPicker`, `DurationPicker`, `RelativeTimeText`

**Text editing** — `TextField`, `OutlinedTextField`, `TextArea`, `SearchField`,
`PasswordField`, `NumberField`, `OtpField`, `PhoneField`, `TagInput`

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
