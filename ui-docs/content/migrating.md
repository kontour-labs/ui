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
  takes it, and each overlay follows a back gesture while it runs. A handler of
  your own that called `overlayHost.dismissTop()` on back still works, but never
  sees an overlay now — remove it.
- **A dimmed modal that may not be dismissed swallows back.** `Dialog`,
  `ModalBottomSheet`, `ModalSideSheet` and `CommandPalette` with
  `dismissible = false` take back and refuse it, rather than letting it pop the
  screen behind them. `dismissible = false` used to leave `dismissOnBack` on; an
  entry of your own with `ScrimStyle.Dimmed` does the same.
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
  between pages and wants them to move the way the platform's do.
