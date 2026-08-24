# Components

The map. Every component in `io.kontour.ui`, and what family it is in.

**Each component has a page of its own.** It says what the component is for,
**what to reach for instead and when**, its API, its states, and the
accessibility notes specific to it — with a render of it alone, light and dark,
taken from the same registry the
[contract suite](../building/testing.md#the-contract-suite) runs over.

A family page is the index of its components: the "which one" table, and the
prose that is about the family rather than any one of them.

| Family | | |
|---|---|---|
| [**Actions**](components/actions.md) | Things you press | `Button` `IconButton` `IconToggleButton` `FloatingActionButton` `FabMenu` `SplitButton` `ButtonGroup` `Toolbar` `Spinner` |
| [**Selection**](components/selection.md) | Recording a choice | `Checkbox` `RadioGroup` `Switch` `SelectionRow` `Chip` `SegmentedControl` `Slider` `RangeSlider` `Stepper` `Rating` |
| [**Text editing**](components/text-editing.md) | Fields and pickers | `TextField` `TextArea` `SearchField` `PasswordField` `NumberField` `PhoneField` `EmailField` `Select` `MultiSelect` `Combobox` |
| [**Date and time**](components/date-time.md) | Calendars and clocks | `CalendarMonth` `DatePicker` `DateRangePicker` `TimePicker` `TimeField` `WheelPicker` `RelativeTimeText` |
| [**Display**](components/display.md) | Showing rather than taking | `Card` `Tag` `Badge` `Avatar` `LinearProgress` `Skeleton` `EmptyState` `Banner` `Callout` `Timeline` `Accordion` `AnimatedCounter` `Modifier.marquee` `Stat` `KeyValueList` `Carousel` `Kbd` |
| [**Collections**](components/collections.md) | Rows, and what happens to them | `ListItem` `ExpandingListItem` `SettingRow` `ListSection` `SwipeActions` `ReorderableItem` `PullToRefresh` `LoadMore` `Scrollbar` |
| [**Navigation**](components/navigation.md) | Getting between screens | `NavigationSuiteScaffold` `NavItem` `NavBar` `NavRail` `NavDrawer` `TopBar` `TabBar` `Breadcrumbs` `Pagination` |
| [**Overlays**](overlays.md) | Things drawn over everything | `Dialog` `AlertDialog` `DropdownMenu` `SubMenu` `ContextMenuArea` `Popover` `Tooltip` `Toast` `LoadingOverlay` `CommandPalette` |
| [**Sheets**](sheets.md) | Bottom and side panels | `BottomSheet` `ModalBottomSheet` `SideSheet` `SheetHeader` `DragHandle` |
| [**Adaptive**](components/adaptive.md) | Layout and motion by window | `Scaffold` `ListDetailPaneScaffold` `WindowSizeClass` `GlassSurface` `PageTransition` `Motion.*` |
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
| `ListItem` vs `SettingRow` vs `SelectionRow` | [`SettingRow`](components/setting-row.md) |
| `Select` vs `Combobox` vs `MultiSelect` vs `RadioGroup` | [`Select`](components/select.md) |
| `SegmentedControl` vs `TabBar` vs `ButtonGroup` | [`ButtonGroup`](components/button-group.md#not-a-segmentedcontrol) |
| `Toolbar` vs `TopBar` | [`Toolbar`](components/toolbar.md) |
| `Banner` vs `Toast` | [`Banner`](components/banner.md) |
| `Dialog` vs `ModalBottomSheet` | [overlays](overlays.md) |
| `Chip` vs `Tag` | [`Tag`](components/tag.md) |
| `Skeleton` vs `Spinner` | [`Skeleton`](components/skeleton.md) |
| `CommandPalette` vs `Combobox` | [overlays](overlays.md#commandpalette) |
| `Stat` vs `KeyValueList` vs `SettingRow` | [`KeyValueList`](components/key-value-list.md) |
| `EmptyState` vs `ErrorState` | [`EmptyState`](components/empty-state.md) |
| `Pagination` vs `LoadMore` | [navigation](components/pagination.md) |
| The five sheet entry points | [sheets](sheets.md) |

---

## Status

Per phase — see [README](../README.md#status). Everything above is built.

## Not yet built

Listed so the shape of the finished system is visible.

**Selection, remaining** — `FilePicker`

**Date and time, remaining** — `DurationPicker`, and a multi-month scrolling
calendar for range selection across month boundaries

**Text editing, remaining** — `OtpField`, `TagInput`, `CurrencyField`

**Display, remaining** — `Marquee`

### Deliberately not being built

`CodeBlock`, `Gauge`, `DataTable` and `TreeList` are admin-web patterns with no
usage in the mobile app, and are not being built on spec.
