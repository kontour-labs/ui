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
    label = "Start trip",
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
    selected = notifyOnDelay,
    onSelectedChange = viewModel::setNotifyOnDelay,
    role = Role.Checkbox,
) {
    +"Notify me about delays"
    supporting { +"Only for favourited routes" }
    trailing { Checkbox(notifyOnDelay, onCheckedChange = null) }
}
```

**This is the form almost every checkbox, radio and switch should take.** A bare
control with a `Text` beside it gives the user a small target and gives a screen
reader two nodes for one choice. The nested control takes `onClick = null` — the
row owns the interaction, the control is there to show state.

It takes `ListItem`'s builder rather than one of its own, because a selection row
is a list row that happens to toggle. Which slot the control goes in *is* its
position — there is no `controlPosition`, and `leading` suits a list of options
being picked from where `trailing` suits a settings list.

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

### `ColorSwatchPicker`

A grid of swatches rather than a dropdown of colour names, because the choice
being made is visual: "which of these do I like" is answered by looking, and a
list that shows one colour at a time makes the user open it six times.

**The tick is drawn in whatever colour is legible on the swatch**, resolved
through `contentColorFor()`. A fixed white tick vanishes on pale yellow and a
fixed black one vanishes on navy, and a picker whose selection is invisible on
two of its own options has a bug in it.

Every swatch carries a label as its content description. A colour with no name is
unusable to anyone who cannot see it — and to anyone who can, describing it over
the phone.

Options whose colour is `null` render as an outlined swatch with an icon, for a
"match the system" entry.

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
| `Select` | Picks one of a fixed set. Menu matches the field's width |
| `MultiSelect` | Picks any number; the menu stays open while toggling |
| `Combobox` | A select the user can type into to narrow a long list |
| `rememberImeChain` | Wires a form's fields so Next walks through them and Done submits |
| `KontourTextToolbar` | Replaces the selection popup on desktop and web |

**A select is a field, not a button.** `Select` shares `FieldScaffold` with
`TextField` rather than resembling it by hand — same frame, same label, same
helper and error slot — because in a form it *is* one of the fields, and a select
styled as a button in a column of text inputs reads as a different kind of thing.

Its menu anchors to the field frame, using `Modifier.anchorBounds` and
`AnchoredDropdownMenu` rather than the parent-anchoring `DropdownMenu`: "the
parent layout" is the wrong node when the menu is declared in one slot of a row
and has to line up with the whole row.

**Reach for a `RadioGroup` above a `Select`** when there are three or four
options and room to show them. A select hides its options behind a tap, a cost
worth paying only when showing them would crowd the screen. Above roughly a
dozen, use `Combobox` so the user can type rather than scroll.

**A combobox is a select with search, not an autocomplete.** The value is always
one of the options, and typing something unmatched leaves the previous value
alone. For free text with suggestions, use a `SearchField` and render results
yourself — conflating the two gives a control where it is unclear whether what
you typed counts as an answer.

### Keyboard action chaining

```kotlin
val chain = rememberImeChain("from", "to", "note", onSubmit = viewModel::plan)

TextField(state = from, label = "From", imeChain = chain["from"])
TextField(state = to, label = "To", imeChain = chain["to"])
TextField(state = note, label = "Note", imeChain = chain["note"])
```

Every field but the last shows **Next** and moves to the one after; the last
shows **Done** and submits. Without it a soft keyboard's action key does nothing,
and filling a three-field form means dismissing the keyboard and tapping the next
field between every entry — with the keyboard covering the field being tapped.

The order lives in one place, at the `rememberImeChain` call, rather than being
implied by three separate `imeAction` arguments that go wrong the first time
someone reorders the form. A chain overrides both `imeAction` and a specialised
field's own default: `PasswordField` defaults to Done, which is wrong for a
password halfway down a form.

### Text selection toolbar

`KontourTextToolbar` replaces Compose's selection popup with one drawn in the
design system — but **only on desktop and web**, where the default is a bare
unstyled row. On Android and iOS it is a deliberate no-op: the platform toolbar
there is a real system surface carrying "Look Up", "Translate", "Share", the
user's keyboard extensions and their configured text replacements, none of which
four buttons of our own can reproduce. Install it unconditionally at the root and
it does the right thing per platform.

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

## Display and content — built

| | |
|---|---|
| `Card` | `Elevated` / `Outlined` / `Filled`, optionally clickable as a whole |
| `Tag` | Status label. Takes an arbitrary background and derives its own text colour |
| `Badge` / `BadgedBox` | Count or dot, positioned over what it annotates |
| `Avatar` / `AvatarGroup` | Image, initials or icon; colour derived from the name |
| `LinearProgress` | Determinate or indeterminate |
| `ProgressRing` | Circular, determinate |
| `StepProgress` | Segmented, for a known number of steps |
| `Spinner` | Indeterminate activity |
| `Skeleton` / `SkeletonText` / `SkeletonListItem` | Loading placeholders |
| `EmptyState` / `ErrorState` | Nothing here vs. something went wrong |
| `Banner` / `AnimatedBanner` | Inline message, four severities |
| `Callout` | The markdown blockquote treatment |
| `Timeline` / `TimelineItem` | Vertical sequence — the journey itinerary |
| `Accordion` | Disclosure with hoisted state |

**`Tag` and arbitrary colours.** Transit feeds supply route colours that are not
drawn from any palette — a route can be pale yellow or near-black. Passing
`color` resolves the label with `contentColorFor()`, so it stays legible. That
is the whole reason the component exists rather than callers styling a `Surface`
themselves: the case they would get wrong is the one where the feed hands them a
colour nobody designed for.

**`Avatar` colours are derived from the name**, so the same person is the same
colour everywhere without anyone storing one. The derivation deliberately avoids
`hashCode()` — Kotlin's String hash is not guaranteed identical across
Kotlin/Native and Kotlin/JS, so the same person could be a different colour on
iOS than on Android.

**`EmptyState` is not `ErrorState`.** Empty means the request succeeded and there
is genuinely nothing, often by the user's own doing, and it needs no apology —
showing an error face for an empty list makes people think they broke something.
The message should say how to *leave* the empty state, not restate the title.

**`Banner` vs `Toast`.** A banner is about the screen you are on; a toast is
about something you just did. A banner that appears in response to a tap is easy
to miss, because the user is looking at their finger. `Danger` banners announce
assertively and everything else politely — interrupting for a routine notice
trains people to ignore the interruption.

**`Timeline`'s connector is drawn to the full height of its row**, using
`IntrinsicSize.Min`. A fixed-height connector leaves gaps against tall rows and
overshoots short ones, which is what makes most hand-rolled timelines look
assembled rather than built.

**Skeletons are hidden from the accessibility tree.** There is nothing to
announce, and a screen reader walking a dozen unlabelled boxes is noise — the
container carries the loading announcement instead. The shimmer stops under
reduced motion.


---

## Overlays — built

Two mechanisms, and telling them apart is the whole design. See
[`overlays.md`](overlays.md) for the architecture; this is the inventory.

| | |
|---|---|
| `OverlayHost` / `OverlayHostState` | The layered stack every overlay renders into |
| `OverlayQueue` | Priority-ordered mutual exclusion, for overlays that must not coexist |
| `Dialog` | Modal panel, arbitrary content |
| `AlertDialog` | Title, message, up to two actions |
| `ConfirmationController` / `ConfirmHost` | `suspend fun confirm(): Boolean` |
| `DropdownMenu` | Anchored to its parent, flips and shifts to stay on screen |
| `MenuItem` / `MenuDivider` / `MenuSectionHeader` | Menu contents |
| `SubMenu` | Nested menu — hover on pointer, tap on touch |
| `ContextMenuArea` | Right-click on pointer, long-press on touch, opens at the pointer |
| `Popover` | Arbitrary content with an arrow, for things a menu is wrong for |
| `Modifier.tooltip` | Hover / focus / long-press by modality |
| `Tooltip` | The same bubble, with visibility the caller controls |
| `Modifier.coachMark` | A tip the app decides to show, scheduled through `OverlayQueue` |
| `Toast` / `ToastHost` | Transient confirmations, one at a time |
| `LoadingOverlay` | Blocking, undismissable, assertive live region |

**Positioning is one pure function.** `positionAnchored()` takes an anchor, a
content size and a container, and returns a placement — flip to the opposite side
when the preferred one has no room, then shift along the other axis until it
fits. It is tested directly rather than through a rendered component, because the
cases that break anchored overlays are geometric (a menu in the far corner, an
overlay taller than the window, an RTL submenu) and each is one line to state as
an assertion and a fiddly interaction to reproduce on a screen.

Shifting gives up alignment rather than going off-screen. A menu aligned to the
start of a button in the corner slides until it fits and ends up no longer
aligned with that button; alignment is a preference, being visible is not.

**Menus render in-composition, not in platform windows.** Material puts each
dialog in its own window, which is fine on Android and awkward on the other four
targets. Rendering the stack inside the app root means z-ordering is a sort on
`OverlayLayer`, dimming is decided once for the whole stack, and every target
behaves identically. The cost is that overlays are clipped to the host's bounds,
so the host belongs at the root.

**One scrim per overlay, but only one of them dims.** Each scrim-requesting entry
gets a scrim directly beneath it, so an outside tap always dismisses the thing it
is under. Only the topmost *dimming* entry draws colour — otherwise a dialog over
a sheet would darken the background twice and the second overlay would sit on
near-black.

**The queue is not the stack.** The stack holds overlays that legitimately
coexist: a menu open over a sheet. The queue holds overlays that must never
coexist — force-update, onboarding, legal notice, what's-new, review prompt,
paywall — and elects one by priority, prerequisites and session count. The
Android app encodes that today as an `ActiveOverlay` enum and a `when`; adding a
seventh here does not mean editing a conditional.

**Coach marks go through the queue, not their own scheduler.** `TooltipManager`
in the Android app is `OverlayQueue` with different field names. The queue
suppresses itself while any *modal* layer is showing — sheet, dialog, menu,
critical — which generalises `tooltipBlocker`. Deliberately not "while anything
is showing": coach marks render into the tooltip layer through this queue, so a
queue that counted its own output would show a tip, suppress itself, and hide it
again on the next frame.

**A tooltip is not a label.** `Modifier.tooltip` sets no semantics, because
anything the user needs in order to understand a control belongs in its
`contentDescription`. The tooltip repeats it for sighted pointer users; a screen
reader that announced both would say it twice.

---

## Adaptive layout and motion — built

| | |
|---|---|
| `WindowSizeClass` / `WindowSizeClassProvider` | Compact / medium / expanded / large, from a measured window |
| `WindowAdaptiveInfo` | Size class *and* input modality, together |
| `Scaffold` | Top bar, bottom bar, FAB, and the padding for the content |
| `ListDetailPaneScaffold` | Two panes when there is room, one at a time when there is not |
| `SupportingPaneScaffold` | Content with a helper pane, or a sheet when narrow |
| `AspectRatioBox` | Reserves a media slot before its content loads |
| `Motion.fadeThrough` / `sharedAxis` / `containerTransform` | Transition presets |
| `Modifier.revealOnScroll` | Fades content in the first time it appears |
| `Modifier.parallax` | Scroll-linked drift |
| `Modifier.shimmer` | The travelling highlight behind a skeleton |
| `GlassSurface` | Translucent panel for a bar over content |
| `Modifier.atmosphere` | The radial glow both websites use behind a hero |
| `Modifier.edgeVignette` | Soft falloff at the edges of a scrolling page |

**`WindowAdaptiveInfo` bundles size with input modality** because the decisions
are rarely about one alone. A 900dp touchscreen held in the hands is not a 900dp
desktop window, and a resize handle is a very different proposition in each.

**`Scaffold` hands the content the whole area plus the padding to inset by**,
rather than squeezing it into the gap between the bars. That is what lets a list
scroll *under* a translucent bar. The padding is the **larger** of the bar and
the inset on each edge, never their sum — a bar has already padded itself for the
inset it sits under, and adding both insets the content twice. `ScaffoldGeometryTest`
renders a real scaffold and measures it, because a double-inset looks like a
slightly generous gap rather than a bug.

**A pane scaffold decides layout, not state.** The caller keeps the selection,
which is what makes back work: on one pane it clears the selection, on two panes
there is nothing to go back from and it does not appear.

### Motion presets

The choice between them says what the *relationship* between two states is:

| | For | Says |
|---|---|---|
| `fadeThrough` | Unrelated content in the same place | "a different thing" |
| `sharedAxis` | A step forward or back in a sequence | "further along" |
| `containerTransform` | One thing becoming a bigger view of itself | "the same thing" |

Picking the wrong one is not cosmetic. A shared-axis slide between two unrelated
tabs implies an order that is not there; a fade between a list row and its detail
throws away the one cue connecting them.

Every preset collapses to a plain cross-fade under reduced motion, and so do
`revealOnScroll`, `parallax` and `shimmer` — movement is the thing the preference
exists to remove.

### There is no portable backdrop blur

Compose's `Modifier.blur` blurs a layer's *own* content, not what is behind it,
and there is no common equivalent of CSS's `backdrop-filter` or iOS's
`UIVisualEffectView`. So `GlassSurface` draws a translucent tint and a hairline
edge — most of what reads as glass — and offers real frosting only via a
`backdrop` slot that composes the background **twice**. Fine for a static image;
not for the live map a floating bar actually sits over, which is why it is not
the default.

`Modifier.atmosphere` is opt-in and deliberately not baked into `Surface`. It is
a treatment for a hero or an onboarding screen; a gradient behind every list is
one nobody notices and everybody pays for.

---

## Navigation — built

| | |
|---|---|
| `NavigationSuiteScaffold` | Picks the surface from the window size and places it |
| `NavItem` | One destination, declared once and rendered by whichever surface fits |
| `NavBar` / `NavBarItem` | **Bottom of the screen.** Floating pill or docked |
| `NavRail` / `NavRailItem` | **Leading edge.** Expandable to a drawer's width, action at the bottom |
| `NavDrawer` / `ModalNavDrawer` | **Leading edge.** Nested groups, sections |
| `TopBar` | Title and actions. Small, centred, or large-collapsing |
| `TabBar` / `Tab` | Views of one screen |
| `SelectionIndicatorBox` | The travelling marker all four surfaces share |
| `Breadcrumbs` | Where you are in a hierarchy, and the way back up |
| `Pagination` | Numbered pages, collapsed around the current one |
| `WindowSizeClass` | Compact / medium / expanded / large, from a measured window |

### How selection is shown

A single marker **travels** to the current destination — an underline beneath it
in the bar and tab bar, a bar down the leading edge in the rail and drawer. The
movement is what carries the meaning, so the accent tint on the icon and label is
a *second* cue rather than the only one.

That matters beyond taste. Selection signalled by colour alone fails WCAG 1.4.1,
and it is what a colour-blind user has nothing to go on. Two independent cues
also serve two different people: the shape reads without colour vision, the tint
reads without sharp edges.

All four surfaces use `foundation/SelectionIndicator.kt` — one mechanism, so the
bar, the rail, the drawer, the tab bar and `SegmentedControl` cannot drift apart.
See [building/](contributing.md) for why it is built the way it is.

Under reduced motion the marker snaps and cross-fades rather than travelling: a
bar sliding the width of the screen is exactly the translation the preference
exists to stop.

### The rail expands

```kotlin
NavRail(
    items = destinations,
    selectedIndex = current,
    expanded = railOpen,
    onExpandedChange = { railOpen = it },
)
```

Pass `onExpandedChange` to get the toggle; leave it null for a rail fixed at
whatever `expanded` says. Expanding grows the rail to `NavDrawerDefaults.Width`
and moves the labels beside the icons — the two line up so the switch does not
read as a jump.

A collapsed *expandable* rail shows icons only. Stacking the label and then
moving it beside the icon would pop mid-animation; keeping the icon still and
sliding the label out from behind it is the same treatment
`ExtendedFloatingActionButton` uses.

### Where navigation goes

| Window | Surface | Placement |
|---|---|---|
| Compact (< 600dp) | `NavBar` | **Bottom of the screen**, over the content |
| Medium (< 840dp) | `NavRail` | **Leading edge**, beside the content |
| Expanded and up | `NavDrawer` | **Leading edge**, labels always shown |

**This is an app, not a website.** Destinations live at the bottom on a phone
because that is where a thumb reaches, and move to the leading edge on a wide
window because a horizontal bar there eats the dimension there is least of. A
`TopBar` in this system is a title and its actions — never a place to put
destinations.

`NavigationSuiteScaffold` makes that choice and does the placement, so a screen
declares its destinations once and never arranges them. The Android app today
writes the same three destinations into `MainToolbar` and `MainNavigationRail`
and picks by hand; `NavItem` is what removes the second copy.

**The bar overlays the content** rather than sitting below it, matching the
floating toolbar the app uses over its map. The scaffold hands your content the
padding to inset by, the same way a map insets its controls by a sheet's
`visibleHeight`.

**The selected destination grows and its pill springs in**, ported from the
app's `ToolbarButton`. It is a control people tap dozens of times a session.

**`TabBar` is not app navigation.** Tabs stay within one screen — the stop you
are looking at, seen three ways. A tab bar used for destinations leaves the user
with no back stack and no sense of where they are. Its indicator is one bar that
*slides*, for the same reason `SegmentedControl`'s does: the row reads as a
single control with a moving part.

**`Pagination` collapses only when collapsing saves room.** A range short enough
to list in full is listed in full — "1 2 … 5" is exactly as wide as "1 2 3 4 5"
and shows two fewer pages — and a gap standing in for a single page is replaced
by that page. `paginationSlots()` is pure and tested, because the failure mode is
a control that is right in the middle of a range and wrong at both ends, and
"page 1 of 40" is the first thing anyone sees.

**A drawer takes a slot, not a list**, unlike the bar and the rail. A drawer is
where destinations stop being a flat set of three: the admin panel's sidebar
nests, groups and separates, and a list model would be a tree wearing a list's
shape. `NavDrawerGroup`'s expansion is hoisted, so the app can open the group
containing the current page — which is nearly always right and not something the
component can know.

---

## Collections — built

| | |
|---|---|
| `ListItem` | One row. Leading, trailing, overline, supporting, segmented corners |
| `ListItemPosition` | `Only` / `First` / `Middle` / `Last`, from `of(index, count)` |
| `ListSection` / `SectionHeader` | A titled group |
| `SettingRow` | The settings-screen shape: icon, label, value |
| `SwipeActions` / `SwipeToDismiss` | Actions revealed by a sideways drag |
| `ReorderableItem` / `rememberReorderableState` | Drag to reorder, live |
| `PullToRefresh` | Pull at the top of a list to reload |
| `LoadMore` | The paging row at the end of a list |
| `Modifier.fadingEdges` | Fades content out at a scrollable edge |
| `Scrollbar` | Position indicator, pointers only |

**A group of rows is one object, not a stack of cards.** Only the outside corners
of a group round; the ones facing a neighbour get a hairline. `ListItemPosition`
carries that, and `of(index, count)` gets the one-item case right — which is the
case a three-item example in a catalog never exercises and every settings screen
with a single row hits immediately.

**Rows default to a *sunken* ground.** In this scheme `surface` and `background`
are the same white, so a row drawn on `surface` is invisible on a page and a
group of them reads as loose text.

**`Scrollbar` is drawn only under a hovering pointer.** A permanent scrollbar on
a touchscreen is wrong twice over — not draggable with a finger at any sensible
width, and taking space from the screens with least of it. On desktop and web the
opposite holds: a long list with no scrollbar reads as broken. It is purely an
indicator, and hidden from the accessibility tree, since it conveys nothing the
list does not already.

**`fadingEdges` erases rather than painting over.** `BlendMode.DstOut` in an
offscreen layer, not a gradient of the background colour — the shortcut version
fails the moment anything is behind the list, which over a map is always.

### Gestures are shortcuts, never routes

Swiping, pulling and dragging are invisible, have no keyboard or pointer
equivalent, and are unreachable for anyone who cannot make a sustained drag. Each
of these components carries its actions a second way and the caller still owes a
third:

- `SwipeActions` puts every action on the row as a **custom accessibility
  action**, so a screen reader can reach it. That covers assistive tech, not a
  sighted mouse user — put the same actions in a menu.
- `ReorderableItem` exposes **move up** and **move down** the same way, since a
  drag is not a gesture a screen reader can perform and reordering with no
  alternative makes a whole feature unreachable.
- `PullToRefresh` needs a refresh action in the toolbar as well.

`SwipeToDismiss` also needs an undo. A dismissal with no way back is a data-loss
bug wearing a gesture; pair it with a `Toast` carrying the undo.

**Reordering happens live, under the finger.** `onMove` fires every time the
dragged row passes another, so the caller's list stays the source of truth
throughout and there is no pending order to reconcile on release.

---

## Sheets — built

See [`sheets.md`](sheets.md) for the detent model; this is the inventory.

| | |
|---|---|
| `SheetState` / `rememberSheetState` | Position, detents, nested scroll |
| `SheetDetent` | `Hidden`, `Expanded`, `Half`, `peek(dp)`, `height(dp)`, `fraction(f)` |
| `BottomSheet` | Non-modal, in the layout, over the content behind it |
| `ModalBottomSheet` | Renders into the `OverlayHost`; dims and blocks |
| `SideSheet` | Slides in from an edge, for wide windows |
| `SheetHeader` | Title, supporting line, actions, close |
| `DragHandle` | The grab bar, and the sheet's accessibility actions |
| `Modifier.sheetPeekAnchor` | Marks what a `peek` detent shows |

**A detent is how much of the sheet is visible**, not where its top edge is —
"showing just the header", "showing all of it". They are values rather than an
enum so a screen can define its own: the map's stop sheet rests at the height of
its own header, which is not a case the library could have enumerated.

**`peek` measures rather than guesses.** `Modifier.sheetPeekAnchor()` on the
header makes the sheet rest exactly far enough to show it, at any font scale. A
fixed peek height cuts the title in half at 200% type.

**`BottomSheet` is non-modal, `ModalBottomSheet` is not.** The first is for a
sheet over a map that the user keeps working around; the second dims and blocks,
which is right for a decision and wrong for anything they need to keep looking
at.

**The drag handle is drawn, not draggable.** The whole sheet already is; a handle
that is the only draggable part makes a 4dp target. It carries the expand and
collapse *accessibility* actions instead, since a drag is not a gesture a screen
reader can perform.

---

## Foundation — built

| | |
|---|---|
| `Text` | Resolves style and colour from the theme. `String` and `AnnotatedString` overloads |
| `Icon` | Tinted to the surrounding content colour. Takes an `ImageVector` or `Painter` |
| `Surface` | Background, shape, border, shadow — and sets `LocalContentColor` |
| `HorizontalDivider` / `VerticalDivider` | Decorative rules |
| `Scrim` | Dims and blocks input behind a modal |

**Components take icons from the caller.** Anything a caller puts *in* a
component — a button's leading icon, a menu item's icon, an empty state's
illustration — is an `ImageVector` or `Painter` parameter, so the choice of icon
library stays an application decision.

The exception is `foundation/SystemIcons.kt`: the handful of glyphs a component
draws on its own behalf, because they are structure rather than content. A
submenu with no chevron gives no sign it opens anything, and a menu item with no
tick cannot show which option is current. Making the caller supply those means
every component ships looking broken until someone remembers to pass one in. They
are Tabler, `internal`, and each is a separate top-level declaration, so the ones
`:ui` never touches are stripped by R8 and by the JS/Wasm DCE.

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

**Selection, remaining** — `RangeSlider`, `Stepper`, `Rating`, `FilePicker`

**Date and time, remaining** — `DurationPicker`, and a multi-month scrolling
calendar for range selection across month boundaries

**Text editing, remaining** — `OtpField`, `TagInput`, `CurrencyField`

**Display, remaining** — `Carousel` + `PageIndicator`, `Stat`, `KeyValueList`,
`Marquee`. `CodeBlock` and `Gauge` are admin-web patterns with no usage in the
mobile app and are not being built on spec.

**Collections, remaining** — `DataTable` and `TreeList` are admin-web patterns
with no usage in the mobile app, and are not being built on spec.


