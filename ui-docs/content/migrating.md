# Migrating

What moved in the API consistency pass, old name to new. The library is
pre-1.0 and this was a clean break: nothing old is kept as a deprecated alias,
so each line here is a compile error until it is made.

Most of it is mechanical, and the compiler finds every site. Three kinds of
change are worth reading rather than fixing one error at a time:

- **Parameters that moved position.** A call that names its arguments is
  untouched; a call that passes them by position either stops compiling or, in
  one case below, compiles to something else. The icon buttons are the big one.
- **A behaviour that moved with a name.** `CarouselState.scrollToPage` animated;
  it is now `animateScrollToPage`, and `scrollToPage` moves at once.
- **A type that changed.** Time is a `Duration` everywhere, and a toast is
  pinned with `Duration.INFINITE`.

Every public API is now checked against a dump under each module's `api/`
directory, so a change like any of these shows up in review from here on — see
[`checkKotlinAbi`](../../docs/building/testing.md#checkkotlinabi).

---

## Behaviour first

The icon buttons take their action first, as `Button` and a
`FloatingActionButton` with a slot always have. **A call that passed the icon by
position no longer compiles**; one that named it is unaffected.

| Before | After |
|---|---|
| `IconButton(icon, contentDescription, onClick)` | `IconButton(onClick, icon, contentDescription)` |
| `IconToggleButton(icon, contentDescription, checked, onCheckedChange)` | `IconToggleButton(checked, onCheckedChange, icon, contentDescription)` |
| `FloatingActionButton(icon, contentDescription, onClick)` | `FloatingActionButton(onClick, icon, contentDescription)` |
| `ExtendedFloatingActionButton(icon, contentDescription, onClick)` | `ExtendedFloatingActionButton(onClick, icon, contentDescription)` |
| `TextIconButton(icon, contentDescription, onClick)` | `TextIconButton(onClick, icon, contentDescription)` |
| `Rating(value, contentDescription, …, onValueChange = null)` | `Rating(value, onValueChange, contentDescription, …)` — required and nullable, as `Checkbox`'s is; `null` is a read-only score |

A builder shorthand's action trails, so it can be written as a trailing lambda:

| Before | After |
|---|---|
| `ButtonGroupScope.item(onClick, contentDescription, icon, …)` | `item(contentDescription, icon, …, onClick)` |
| `ListGroupScope.item`, `NavDrawerScope.item`, `TimelineRowScope.item` | `enabled` is the first optional parameter |

**The one that compiles to something else:** `Modifier.minimumTouchTarget(false)`
used to switch the target off; with `enabled` last on every modifier it now
means `fill = false`. Name it: `minimumTouchTarget(enabled = false)`.

| Before | After |
|---|---|
| `Modifier.minimumTouchTarget(enabled, fill)` | `Modifier.minimumTouchTarget(fill, enabled)` |
| `Modifier.redacted(enabled, shape)` | `Modifier.redacted(shape, enabled)` |

---

## Picking one of many

By value, `value` and `onValueChange`; by position, `selectedIndex` and
`onSelectedIndexChange`.

| Before | After |
|---|---|
| `SegmentedControl(selected, onSelectedChange)` | `SegmentedControl(selectedIndex, onSelectedIndexChange)` |
| `WheelPicker(selected, onSelectedChange)` | `WheelPicker(selectedIndex, onSelectedIndexChange)` |
| `Modifier.tabSwipe(selected, count, onSelectedChange)` | `Modifier.tabSwipe(selectedIndex, count, onSelectedIndexChange)` |
| `RadioGroup(selected, onSelectedChange)` | `RadioGroup(value, onValueChange)` |
| `DatePicker(selected, onSelectedChange)` | `DatePicker(value, onValueChange)` |
| `DateRangePicker(onRangeSelected)` | `DateRangePicker(onRangeChange)` |
| `FilterChip(selected, onClick: () -> Unit)` | `FilterChip(selected, onSelectedChange: ((Boolean) -> Unit)?)` — handed the new state; `null` is an inert chip |

---

## One `Tone`, one `GroupPosition`

| Before | After |
|---|---|
| `BannerTone` — `Banner`, `AnimatedBanner`, `Callout` | `Tone` (`io.kontour.ui.theme`) |
| `TagTone` — `Tag` | `Tone` |
| `ToastTone` — `ToastHostState.show` | `Tone` |
| `ListItemPosition` — `ListItem`, `ExpandingListItem` | `GroupPosition` (`io.kontour.ui.foundation`) |
| `ButtonGroupPosition` — `ButtonGroup` | `GroupPosition` |
| `BubblePosition` — `ChatBubble` | `GroupPosition` |
| `BubblePosition.of(items, index, sender)` | `GroupPosition.of(items, index, runOf)` |
| `ListItemPosition.shape(shape, square)` | `GroupPosition.shape(shape, square, orientation)` |

Every tone is on every component now: a banner can be `Neutral`, and a toast
`Info`.

---

## Surfaces and colours

A surface takes `containerColour` and `contentColour`; `colour` is for the one
ink of a mark — `Text`, a divider, a spinner.

| Before | After |
|---|---|
| `Surface(colour)` | `Surface(containerColour)` |
| `Card(colour)` | `Card(containerColour)` |
| `ChatBubble(colour)` | `ChatBubble(containerColour)` |
| `ChatBubbleDefaults.colour(side)` | `ChatBubbleDefaults.containerColour(side)` |
| `Badge(colour)` | `Badge(containerColour)` |
| `Tag(colour)` | `Tag(containerColour)` |
| `ButtonColours.disabledContainer`, `disabledContent`, `disabledBorder` | `containerDisabled`, `contentDisabled`, `borderDisabled` |
| `TextFieldColours.helper` | `TextFieldColours.supporting` |
| `Rating(filledColour, emptyColour)` | `Rating(colours = RatingDefaults.colours(filled, empty))` |
| `KnobDefaults.colours(face, faceRing, notch)` | `KnobDefaults.colours(thumb, thumbRing, needle)` — the `DialColours` fields they fill |

---

## The default accent is violet

The default schemes' accent moved from blue to Kontour Labs' violet, `#BB86FC`,
and the `Palette` ramp behind it was renamed with it. A scheme that sets its own
`accent`, `brand` and `focusRing` looks exactly as it did; one that relied on the
defaults is violet now wherever it was blue — selection, links, focus, the
accent button. `primary` is unchanged, so controls stay ink.

| Before | After |
|---|---|
| `Palette.BlueReadable`, `BlueDeep`, `BlueDeeper`, `BlueStrong` | `Palette.VioletReadable`, `VioletDeep`, `VioletDeeper`, `VioletStrong` |
| `Palette.BlueTintLight`, `BlueTintLightHc`, `BlueTintDark`, `BlueTintDarkHc` | `Palette.VioletTintLight`, `VioletTintLightHc`, `VioletTintDark`, `VioletTintDarkHc` |
| `Palette.BlueLight`, `BlueLightHc`, `BlueOnLight`, `BlueHcOnLight` | `Palette.VioletLight` (`#BB86FC`), `VioletLightHc`, `VioletOnLight`, `VioletHcOnLight` |
| `Palette.BluePale`, `BluePaleHc`, `BlueBorderLight`, `BlueBorderDark` | `Palette.VioletPale`, `VioletPaleHc`, `VioletBorderLight`, `VioletBorderDark` |

In the light scheme `brand` is `#BB86FC` and `accent.solid` is `#7C37BE`, the
same hue at a lightness that carries white text; the two are no longer the same
colour there. See [tokens.md](tokens.md#actions).`contrastFailures` also asks one more question now: whether `accent.solid` can be
read as text, at 4.5:1 against every ground, because links, text buttons and a
focused field's label are drawn in it. A scheme of your own whose accent was a
fill dark enough to carry white may report a failure it always had — the demo
GTurbo theme did, and moved its accent to a lighter red with a dark label.

---

## Back is handled for you

Nothing in the library answered back before: an Android back press with a sheet
open left the screen, and `ListDetailPaneScaffold.onBack` was never called. Now:

- **Overlays take back.** The `OverlayHost` gives it to the topmost entry that
  takes it. On Android it closes by the overlay's own exit animation, following
  the gesture; on iOS back closes only a side sheet, swiped towards its own
  edge, and otherwise only stops at the overlay. A handler of your own that
  called `overlayHost.dismissTop()` on back still works, but never sees an
  overlay now — remove it.
- **On iOS the outermost `OverlayHost` takes the back swipe from the platform.**
  It gives everything under it a back dispatcher of its own and tracks the edge
  swipe itself, so a released swipe carries on at the finger's speed rather
  than pausing and finishing at the transition's. A handler registered on the
  platform's `NavigationEventDispatcher` *outside* the host no longer hears the
  swipe; register it inside, through `LocalNavigationEventDispatcherOwner`.
- **A dimmed modal that may not be dismissed swallows back.** `Dialog`,
  `ModalBottomSheet`, `ModalSideSheet` and `CommandPalette` with
  `dismissible = false` take back and do nothing with it, rather than letting it
  pop the screen behind them. `dismissible = false` used to leave
  `dismissOnBack` on; an entry of your own with `ScrimStyle.Dimmed` does the
  same.
- **A side sheet is dragged closed on every platform,** towards its own edge,
  not only on iOS. The drag is the sheet's own: a stack inside the sheet with
  somewhere to go back to no longer takes it, though it still takes the
  platform's back gesture first.
- **`ListDetailPaneScaffold` calls `onBack`** for back on one pane, and follows
  the gesture.
- **How back looks is per platform:** `LocalBackStyle` is `Predictive` on Android
  and `Swipe` on iOS, and can be provided to preview one on the other. Android
  13 to 15 shows the predictive animation only with
  `android:enableOnBackInvokedCallback="true"` on the application in the
  manifest; Android 16 has it on for apps that target it.

With Navigation 3, `rememberPageTransitionStrategy()` in `:ui-nav3` gives every
page the push, pop and back gesture of its platform in one line; see
[Navigation 3](navigation3.md).

---

## Switches are `show<Part>`

| Before | After |
|---|---|
| `SkeletonListItem(supportingLine)` | `showSupportingLine` |
| `KeyValueList(dividers)` | `showDividers` |
| `ActivityCalendar(monthLabels, weekdayLabels, legend)` | `showMonthLabels`, `showWeekdayLabels`, `showLegend` |
| `ColourPicker(alphaSlider, valueField)` | `showAlphaSlider`, `showValueField` |
| `ChatBubble(tail)` | `showTail` |
| `NavBar(backdrop)` | `showBackdrop` |
| `NavBarItem(shadow)` | `showShadow` |
| `Gauge(contentBackground)`, `Meter(contentBackground)` | `showContentBackground` — the `Color` of the same name on their `colours()` is unchanged |
| `Gauge(animated)`, `Meter(animated)` | `animateValue` |
| `PasswordField(isNewPassword)` | `newPassword` |
| `SwipeAction(isFullSwipeAction)` | `fullSwipe` |

---

## One word per idea

| Before | After |
|---|---|
| `ListSection(description)`, `SectionHeader(description)` | `supporting` |
| `supporting { }` in an `EmptyState`, `ErrorState` or `AlertDialog` | `message { }`, as a `Banner`'s is |
| `Modifier.coachmark(text)`, `Modifier.coachmarkStep(text)` | `message` |
| `LoadMore(errorLabel)` | `errorMessage` |
| `MenuSectionHeader(content)` | `title` |
| `NavDrawerGroup(label)` | `header`, as an `Accordion`'s |
| `MenuScope.item(icon)`, `MenuScope.submenu(icon)` | `leadingIcon` |
| `NumberField(prefix)` | `leadingIcon` |
| `PageIndicator(onPageSelect, label)` | `onPageClick`, `pageDescription` |
| `ActivityCalendar(markFor)` | `markerFor` |
| `WheelPicker(label)` | `itemLabel` |
| `CommandPalette(filter: (String, Command) -> Boolean)` | `matches: (Command, String) -> Boolean`, in `Combobox`'s order |
| `commandMatches(query, command)` | `commandMatches(command, query)` |
| `Command(onRun)` | `onAction`, as a `SwipeAction`'s |
| `BranchTimeline(id)`, `LazyListScope.branchTimeline(id)` | `key` |
| `Stepper(range)` | `valueRange`, as a `Slider`'s |
| `ToastHost(showClose, closeLabel)` | `showDismiss`, `dismissLabel`, as a `Banner`'s |
| `Scaffold(contentWindowInsets)` | `windowInsets` |
| `TextArea(minLines, maxLines)` | `TextArea(lineLimits = TextFieldLineLimits.MultiLine(…))`, as a `TextField` takes |
| `Modifier.coachMark` | `Modifier.coachmark` |

`NavigationSuiteScaffold` hands its content `PaddingValues` now, as `Scaffold`
does: `Modifier.padding(bottom = it)` becomes `Modifier.padding(it)`.

---

## Time is a `Duration`

| Before | After |
|---|---|
| `SearchField(debounceMillis: Long)` | `SearchField(debounce: Duration)` |
| `ToastHostState.show(durationMillis: Long)` | `show(duration: Duration)` — `Duration.INFINITE` pins a toast, as `0` did |
| `ToastDefaults.Duration`, `DurationWithAction` | `ToastDefaults.DisplayDuration`, `DisplayDurationWithAction` — which no longer hide `kotlin.time.Duration` inside the object |
| `TooltipDefaults.HoverDelayMillis`, `TouchDurationMillis` | `HoverDelay`, `TouchDuration` |
| `MenuDefaults.SubmenuCloseDelay: Long` | `SubmenuCloseDelay: Duration` |
| `SwipeActionsDefaults.SettleAfterScroll: Long` | `SettleAfterScroll: Duration` |
| `FabMenuDefaults.StaggerMillis` | `FabMenuDefaults.Stagger` |
| `SpinnerDefaults.RotationMillis`, `BreatheMillis` | `RotationDuration`, `BreatheDuration` |
| `MarqueeDefaults.PauseMillis`, `Modifier.marquee(pauseMillis)` | `MarqueeDefaults.Pause`, `marquee(pause)` |
| `Modifier.revealOnScroll(delayMillis)` | `revealOnScroll(delay)` |
| `Modifier.shimmer(durationMillis)` | `shimmer(duration)` |

---

## State holders and hosts

| Before | After |
|---|---|
| `CarouselState.scrollToPage` (it animated) | `animateScrollToPage`; `scrollToPage` now moves at once |
| `CarouselState.count` | `pageCount` |
| `ConfirmationController`, `rememberConfirmationController()` | `ConfirmHostState`, `rememberConfirmHostState()` — dismissed with `dismiss()`, like the other hosts |
| `ConfirmHost(controller)` | `ConfirmHost(state, modifier)` |
| `LoadMoreState`, `LoadMore(state)` | `LoadMoreStatus`, `LoadMore(status)` |
| `rememberSelectState(initiallyExpanded)` | `initialExpanded`; `SelectState.expanded` is read-only — `expand()` and `collapse()` change it |
| `rememberCalendarNavigationState(initial)` | `initialDate` |
| `rememberInputModalityState(initial)` | `initialModality` |

---

## Haptics

| Before | After |
|---|---|
| `KontourTheme(haptics)` | `KontourTheme(hapticsLevel)` — `Haptics` is the player |
| `HapticCapability.level: Level` | `HapticCapability.richness: Richness` |
| `FeedbackDispatcher.perform(intent, strength)` | `perform(intent, scale)`, the factor `HapticEffect.scaled` takes |
| `HoldFeedback.progress(fraction)` | `HoldFeedback.update(fraction)` |
| `rememberTapFeedback(): () -> Unit` | `TapFeedback`, called the same way |
| `rememberToggleFeedback(): (Boolean) -> Unit` | `ToggleFeedback`, called the same way |
| `NoFeedback` | `FeedbackDispatcher.None`, as `Haptics.None` |
| `HapticPatternBuilder` | `HapticPatternScope`, with a DSL marker |
| `HapticEffect.Threshold()` | `HapticEffect.Threshold(activate = true)` — required, as `Toggle(on)` is |

---

## Names and places

| Before | After |
|---|---|
| `NavExpandDefaults` | `NavExpandingSlotDefaults` |
| `navSlotContainerColour()`, `navSlotShadow()` | `NavExpandingSlotDefaults.containerColour()`, `shadow()` |
| `FabDefaults` | `FloatingActionButtonDefaults` |
| `SheetDefaults` | `BottomSheetDefaults` |
| `DefaultSheetDetents` | `BottomSheetDefaults.Detents` |
| `KbdDefaults.Command`, `Option`, `Shift`, `Control`, `Return`, `Backspace`, `Delete`, `Escape`, `Tab`, `CapsLock`, `ArrowUp`, `ArrowDown`, `ArrowLeft`, `ArrowRight`, `PageUp`, `PageDown`, `Space` | the same names on `KbdGlyphs`; `KbdDefaults` keeps the sizes |
| `ContrastThreshold.BODY_TEXT`, `LARGE_TEXT`, `NON_TEXT`, `BODY_TEXT_ENHANCED`, `LARGE_TEXT_ENHANCED` | `BodyText`, `LargeText`, `NonText`, `BodyTextEnhanced`, `LargeTextEnhanced` |
| `CheckboxVisualSize` | `CheckboxDefaults.VisualSize` |
| `SliderVisualHeight` | `SliderDefaults.VisualHeight` |
| `pointerMinTouchTarget` | `TouchTargetDefaults.PointerMinimum` |
| `DefaultPressScale`, `PressFloor` | `IndicationDefaults.PressScale`, `PressFloor` |
| `CapsuleCap` | `Shapes.CapsuleCap` |
| `TimelineListDefaults.NodeSize`, `GutterWidth` | `TimelineDefaults.NodeSize`, `GutterWidth` |
| `BranchTimelineDefaults.NodeSize`, `GutterWidth` | `TimelineDefaults.NodeSize`, `GutterWidth` |
| `textToolbarLabels(…)` | `TextToolbarDefaults.labels(…)` |
| `FabPosition.Center`, `OverlayAlignment.Center`, `TableAlign.Center` | `.Centre` |
| `linkedText { }`, `LinkedTextScope` | `richText { }`, `RichTextScope` — the same builder, which now does bold, italic, code, tone and Markdown as well as links; `link(text) { }` is unchanged |

Six types that nothing public could reach are `internal` now: `SlotGap`,
`ArrowSpec`, `ScrollFade`, `ScrollbarGeometry`, `ConcentricContainer`, and the
toast host's `Toast`.

---

## Navigation, merged

The bar, the rail and the drawer had three content models between them — a
`NavItem` list with a `selectedIndex`, per-row `selected` flags, and strings keyed
by their own label. They share one now, [`NavScope`](components/nav-scope.md),
written once and drawn by whichever surface it is in; and the rail and the drawer
are one component at two widths.

| Before | After |
|---|---|
| `NavBar(items, selectedIndex, search = …, searchIndex = …, action = …)` | `NavBar { item(…); slot(weight = 1f) { NavSearch(…) } }` |
| `NavItem("Home", icon, onClick, badge = 3)` in a list | `item(selected = here == Home, onClick = …, key = Home, icon = icon, badge = { Badge(count = 3) }) { +"Home" }` |
| `selectedIndex` | `selected` on each `item` |
| `NavBarStyle.Free` | Gone: `Floating`, the new default, or `Docked`; `glass = true` for either frosted |
| `NavBar(showBackdrop, containerColour, contentColour, indicatorColour, indicatorSize, labelGap)` | `colours = NavDefaults.colours(…)`; the backdrop went with `Free` |
| `NavRail(items, selectedIndex, showLabels, action)` | `NavRail(…) { … }`, labels whenever it is wide enough, `footer` for the action |
| `NavDrawer(header, footer) { … }` | `NavRail(expanded = true, header, footer) { … }` |
| `ModalNavDrawer(…)` | `ModalNavRail(…)`, which closes itself a frame after a choice (`dismissOnItemClick`) |
| `NavDrawerItem`, `NavDrawerGroup`, `NavDrawerSection`, `NavBarItem`, `NavRailItem` | `item`, `group`, `section` in the scope |
| drawer DSL `item("Home", selected = …) { … }`, keyed by its label | `item(…, key = …) { +"Home" }` — a key of yours; a duplicate throws |
| `NavigationSuiteScaffold(items, selectedIndex, drawerHeader = …)` | `NavigationSuiteScaffold(destinations = { … }, header = …)`; `NavigationSuiteType.Drawer` is `ExpandedRail`, and `ModalRail` is new |
| `NavExpandingSlot { Icon(…); Text(…) }` in a `RowScope` | `NavExpandingSlot { +icon; +"Filters" }` in a `RowContentScope` |
| `NavItemDefaults`, `NavDrawerDefaults` | `NavBarDefaults.CircleSize`, `NavRailDefaults.GlyphSize` and `.NestIndent`, `NavRailDefaults.ExpandedWidth` |
| `ComponentDefaults.navDrawerNestIndent` | `navNestIndent`; `navIndicatorWidth`, `navIndicatorHeight`, `navGlyphHeight` and `navDrawerWidth` are gone |

Also changed: a group in a bar or a narrow rail opens a menu of its destinations,
and carries the marker while the selected one is inside it; unselected
destinations are muted everywhere; the marker casts no shadow; a bar's slot is on
a surface, so a `NavSearch` pill there is a recess; a rail fixed at
`expanded = true` with no toggle is honoured; an icon-less destination shows its
label's initial where its label is hidden.

---

## `+` where a component holds one kind of content

The `+` vocabulary reached more of the components whose content is nearly
always the same kind of thing in the same style, and the slots that already
took it now style what they hold the same way everywhere.

- **`ChatBubble`'s content and `meta`, and `Callout`'s content, are
  `ContentScope` slots** in the style they already provided:
  `ChatBubble(side, meta = { +"9:41" }) { +"On my way" }`, `Callout { +"…" }`.
  `Text(…)` inside them still works; a stored `@Composable () -> Unit` passed
  as the content no longer compiles — wrap it.
- **`TimelineItem` takes a list row's regions**, collected, instead of a
  `ColumnScope` body: `+"Perth Busport"` is the stop in `titleSmall`,
  `supporting { +"08:29" }` the muted detail, `trailing {}` a value. A body of
  `Text(…)` calls becomes `+"…"` and `supporting { +"…" }`; anything else goes
  in `label { … }`.
- **`Dialog(title)` and `Popover(title)`** are new: a heading region above the
  body, in `titleLarge` and `titleSmall`. A hand-written first
  `Text(…, style = titleMedium)` moves there. `AlertDialog`'s title is drawn by
  the same region, and is now announced as a heading.
- **`Gauge`, `Meter` and `Knob` take a `ReadoutScope`** instead of a `BoxScope`:
  `+"8.5k"` is the figure, sized by the component, and `label("RPM")` the
  caption. A body that hand-set both becomes those two lines; anything else goes
  in `value { … }` or `label { … }`.
- **A list row's `trailing` is styled.** `ListItem`, `SettingRow`,
  `SelectionRow` and the timeline rows give `trailing { +"4 min" }` the muted
  body style, dimming with a disabled row. `settingValue(…)` existed only to do
  that and is gone: write `trailing { +"Kilometres" }`. A control in the slot —
  a switch, a badge — sets its own colours and does not change.
- **`ListGroupScope.item(trailing)`** is a `ContentScope` lambda, so
  `trailing = { +"4 min" }` compiles. A stored `@Composable () -> Unit` passed
  there no longer does; wrap it: `trailing = { mine() }`.
- **`SubMenu(leadingIcon)` is gone.** Put the icon in the label, as on a
  `MenuItem`: `SubMenu(label = { +"Sort by"; leading { +icon } })`. The
  `MenuScope.submenu(leadingIcon)` shorthand is unchanged.
- **`StatScope`** takes `+AnnotatedString` and `label { … }`, like every other
  collecting scope.
- **Fixed:** a `MenuScope.item` given both `trailingIcon` and `shortcut` drew
  only the shortcut. It draws both, icon first.
- **`SegmentedControl` honours `uppercaseLabels`,** as `Button`, `Chip` and
  `Tab` do.

---

## Moved, and unaffected if named

These changed position only. A call that names the argument — which is how
every example in these pages writes them — compiles as it did.

- `BottomSheet(dismissible)` comes before `paneTitle`, as on the other sheets.
- `FabMenu(key)` and `AnchoredDropdownMenu(key)` come straight after `modifier`
  and `enabled`, as a `Dialog`'s and a `CommandPalette`'s do.

---

## Added, alongside

Not breaks, but new in the same pass, and several replace something you may
have been writing by hand:

- **Defaults you can read back** — `CardDefaults`, `SurfaceDefaults.contentColour`,
  `CalloutDefaults.icon`, `DatePickerDefaults.currentDate`, `WheelPickerDefaults`,
  `ProgressDefaults`, `PopoverDefaults`, `ColourSwatchPickerDefaults`,
  `ScrollEffectDefaults`, `TextAreaDefaults`, `TimelineDefaults.ConnectorWidth`.
- **Colour classes** — `SliderColours` on `Slider` and `RangeSlider`,
  `RatingColours` on `Rating`; `ButtonDefaults.colours` and
  `TextFieldDefaults.colours` take any colour to replace.
- **`enabled`** on `WheelPicker`, `TimePicker`, `CalendarMonth`, `DatePicker`,
  `DateRangePicker`, `Pagination`, `LoadMore` and `NavDrawerGroup`;
  **`interactionSource`** on `NavDrawerGroup`, `SubMenu` and `FabMenu`.
- **`onValueChangeFinished`** on `Rating`, and `onColourChangeFinished` on
  `ColourPicker`, for saving once rather than on every step.
- `ModalBottomSheet(scrim)`, `SupportingPaneScaffold(resizable)`,
  `WheelPicker(contentDescription)`, `DateRangePicker(markerFor)`,
  `AnimatedBanner(dismissLabel)`, and `shape`, `colours` and `metrics` on every
  specialised text field.
- `InputChip` and `Banner` draw their remove and dismiss buttons by default now;
  pass `null` to leave one out.
- **Back:** `BackStyle` and `LocalBackStyle`; `PageMotion`, `rememberPageMotion`
  and `Modifier.pageEffects`, for an app that runs its own `AnimatedContent`
  between pages and wants them to move the way the platform's do; and
  `Modifier.contentSwipeBack`, iOS 26's back from anywhere on the page, for a
  page of that kind.
- **`LocalContainerColour`**, the ground content sits on, provided by every
  opaque `Surface`. A page in a navigation stack paints it, so a stack inside a
  sheet is the sheet's colour, and **`TopBar` now defaults to it** rather than to
  `Theme.colours.background` — the same colour on the window, the sheet's inside
  a sheet. Pass `containerColour` to keep the old one. Inside an overlay,
  `Modifier.pageEffects` fades the page beneath towards that colour instead of
  dimming it, so nothing marks where the stack ends.
- **Icons in `richText`:** `+Icons.Clock`, or `icon(icon, contentDescription)`,
  put an icon in the line; `Text` draws it, with no `inlineContent` to write.
