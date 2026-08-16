# Components

The map. Every component in `io.kontour.ui`, what family it is in, and where its
page is.

Each family page says what each component is for, **what to reach for instead
and when**, its API, its states, and the accessibility notes specific to it —
with a render of the component on its own, light and dark, taken from the same
registry the [contract suite](../building/testing.md#the-contract-suite) runs
over.

| Family | | |
|---|---|---|
| [**Actions**](components/actions.md) | Things you press | `Button` `IconButton` `IconToggleButton` `FloatingActionButton` `Spinner` |
| [**Selection**](components/selection.md) | Recording a choice | `Checkbox` `RadioGroup` `Switch` `SelectionRow` `Chip` `SegmentedControl` `Slider` `RangeSlider` `Stepper` |
| [**Text editing**](components/text-editing.md) | Fields and pickers | `TextField` `TextArea` `SearchField` `PasswordField` `NumberField` `PhoneField` `EmailField` `Select` `MultiSelect` `Combobox` |
| [**Date and time**](components/date-time.md) | Calendars and clocks | `CalendarMonth` `DatePicker` `DateRangePicker` `TimePicker` `TimeField` `WheelPicker` `RelativeTimeText` |
| [**Display**](components/display.md) | Showing rather than taking | `Card` `Tag` `Badge` `Avatar` `LinearProgress` `Skeleton` `EmptyState` `Banner` `Callout` `Timeline` `Accordion` `Stat` `KeyValueList` `Kbd` |
| [**Collections**](components/collections.md) | Rows, and what happens to them | `ListItem` `SettingRow` `ListSection` `SwipeActions` `ReorderableItem` `PullToRefresh` `LoadMore` `Scrollbar` |
| [**Navigation**](components/navigation.md) | Getting between screens | `NavigationSuiteScaffold` `NavItem` `NavBar` `NavRail` `NavDrawer` `TopBar` `TabBar` `Breadcrumbs` `Pagination` |
| [**Overlays**](overlays.md) | Things drawn over everything | `Dialog` `AlertDialog` `DropdownMenu` `SubMenu` `ContextMenuArea` `Popover` `Tooltip` `Toast` `LoadingOverlay` |
| [**Sheets**](sheets.md) | Bottom and side panels | `BottomSheet` `ModalBottomSheet` `SideSheet` `SheetHeader` `DragHandle` |
| [**Adaptive**](components/adaptive.md) | Layout and motion by window | `Scaffold` `ListDetailPaneScaffold` `WindowSizeClass` `GlassSurface` `Motion.*` |
| [**Foundation**](components/foundation.md) | What the rest is built from | `Text` `Icon` `Surface` `Divider` `Scrim` |

Cross-cutting reading:
[tokens](tokens.md) ·
[theming](theming.md) ·
[the `+` vocabulary](dsls.md) ·
[accessibility](accessibility.md)

---

## Picking between the close calls

The comparisons that get made wrongly, and where each is argued:

| | |
|---|---|
| `ListItem` vs `SettingRow` vs `SelectionRow` | [collections](components/collections.md#settingrow) |
| `Select` vs `Combobox` vs `MultiSelect` vs `RadioGroup` | [text editing](components/text-editing.md#select) |
| `SegmentedControl` vs `TabBar` | [selection](components/selection.md#segmentedcontrol) |
| `Banner` vs `Toast` | [display](components/display.md#banner--animatedbanner) |
| `Dialog` vs `ModalBottomSheet` | [overlays](overlays.md) |
| `Chip` vs `Tag` | [display](components/display.md#tag) |
| `Skeleton` vs `Spinner` | [display](components/display.md#skeletons) |
| `Stat` vs `KeyValueList` vs `SettingRow` | [display](components/display.md#keyvaluelist) |
| `EmptyState` vs `ErrorState` | [display](components/display.md#emptystate--errorstate) |
| `Pagination` vs `LoadMore` | [navigation](components/navigation.md#pagination) |
| The five sheet entry points | [sheets](sheets.md) |

---

## Status

Per phase — see [README](../README.md#status). Everything above is built.

## Not yet built

Listed so the shape of the finished system is visible.

**Selection, remaining** — `Rating`, `FilePicker`

**Date and time, remaining** — `DurationPicker`, and a multi-month scrolling
calendar for range selection across month boundaries

**Text editing, remaining** — `OtpField`, `TagInput`, `CurrencyField`, and a
`CommandPalette` — a search field over *actions* rather than values. The pieces
exist (`SearchField`, `trapFocus`, `MenuScope.item(shortcut =)`, `Kbd`) and
nothing composes them; `Combobox` explicitly declines the case.

**Display, remaining** — `Carousel` + `PageIndicator`, `Marquee`

**Actions, remaining** — `Toolbar` / `ButtonGroup`, a row of related actions.
Distinct from `SegmentedControl`, which is single-select with radio semantics,
and from `TopBar`'s `actions` slot, which is a fixed pair of icon buttons.

### Deliberately not being built

`CodeBlock`, `Gauge`, `DataTable` and `TreeList` are admin-web patterns with no
usage in the mobile app, and are not being built on spec.
