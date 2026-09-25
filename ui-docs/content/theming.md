# Theming

*Also on this page: `KontourTheme`, `ProvideTokens`.*

How to change what the system looks like without touching a component.

## The default has no product in it

Monochrome — ink, white and a grey ramp — plus **one blue** for the accent, and
the four conventional status hues. That is the whole palette, and it is
deliberate: a library that shipped somebody's brand would make every app using
it look like that somebody, and the app that owned the brand would be the only
one not fighting the defaults.

So the default scheme is not a design; it is a *starting point that offends
nobody*. `brand` resolves to the accent until you set one, which is the library
saying it has no opinion rather than pretending to have none.

**The worked example is in this repository.** `GTurbo`, in
`ui-catalog/src/commonMain/kotlin/io/kontour/ui/demo/theme/GTurbo.kt`, is a
near-black, red-accented, small-cornered design that shares no value with the
default — a fair test of whether this is a token *system* or a dark-mode switch.
Every colour in it was sampled from the design's own pixels, its contrast is
walked against WCAG on every build, and it is photographed beside the built-in
schemes in `ui-catalog/screenshots/theme-gturbo-dark.png`. It is the
shape to copy, and unlike a description in prose it cannot drift: it compiles.

This page used to point at an app in another repository for that. A worked
example nothing builds is a worked example nobody can check.

---

## Installing the theme

```kotlin
KontourTheme {
    AppRoot()
}
```

Once, at the root. Everything in `io.kontour.ui` reads its tokens from here and
throws outside it — a component silently rendering in the wrong palette is a
worse bug than one that refuses to render.

By default it resolves dark mode, contrast tier and reduced motion from the
operating system, and follows all three live. Which platform setting drives
which is in [`accessibility.md`](accessibility.md#contrast-tiers).

**It also tells the host which way round the app is drawing.** Every platform
has chrome the canvas cannot reach and that picks its own colours from a flag
the host owns — Android's status and navigation bar icons, iOS's status bar, a
browser's scrollbars and mobile address bar. All of them default to guessing
from the *system's* appearance, which is the wrong guess for an app with a dark
switch in it: force dark under a light phone and the clock and the battery stay
dark on a dark bar. The outermost `KontourTheme` sets the flag from the scheme
it resolved, so there is nothing to do at the host but let it draw edge to edge.

A nested `KontourTheme` — one screen forcing dark over a light app — does not.
It re-provides tokens for its subtree; the window belongs to the app.

On web the flag is the document's `color-scheme`, and it follows the app's own
switch in both directions — measured in a browser on the built site, turning Dark
on under a light system and off under a dark one.

---

## Overriding

Every token group is a parameter, so you override one and inherit the rest.

**Force a mode for one screen:**

```kotlin
ProvideTokens(colours = kontourColourScheme(dark = true)) { MapScreen() }
```

> **Call `KontourTheme` once, at the root, and `ProvideTokens` after it.**
>
> A nested `KontourTheme` does **not** inherit. Every parameter it is not given
> re-runs its *default*, and those defaults read the platform rather than the
> theme around them — so `KontourTheme(strings = german) { KontourTheme(darkTheme
> = true) { … } }` puts all 47 strings back into English, resets
> `HapticsLevel.Off` to `Full`, and discards a custom `spacing`, `sizing` or
> `motion` on the way. Nothing errors. Nothing looks wrong until somebody reads
> the German build.
>
> `ProvideTokens` defaults every parameter to the value already in scope, so an
> argument you do not pass is an argument that does not change. This page used to
> recommend the nested form; both behaviours are now asserted on every build, so
> the difference stays a fact rather than a memory.
>
> Do not hand-roll it either: `CompositionLocalProvider(LocalColourScheme
> provides scheme)` installs the scheme and leaves every `Text` and `Icon` below
> it drawing in the *old* palette's content colour, because `LocalContentColour`
> is derived where `KontourTheme` provides it and is not re-derived by providing
> the scheme again.

**Change the words the library puts on screen:**

```kotlin
KontourTheme(
    strings = Strings(
        dismiss = "Schließen",
        back = "Zurück",
        pullToRefresh = "Zum Aktualisieren ziehen",
    ),
) { AppRoot() }
```

`Strings` is a token group like the rest, and it holds every word the library
draws that you did not supply — 47 of them. Each component still takes its own
parameter, defaulted from here, so a one-off at a call site keeps working and an
app-wide change is one argument rather than a sweep through every call site.

One field per idea, not per parameter: `SheetHeader`, `SideSheet` and
`ModalBottomSheet` all say "Close" and all three read `strings.close`. It is not
a localisation system — no plurals, no locale lookup, no resource bundle — and
it is not trying to be. What it guarantees is that no English is welded into a
component, so an app can feed it from whatever it already uses.

**Let an in-app setting win over the OS:**

```kotlin
KontourTheme(
    darkTheme = when (prefs.theme) {
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
        ThemePreference.System -> isSystemInDarkTheme()
    },
    reduceMotion = prefs.reduceMotion ?: platformPrefersReducedMotion(),
) { AppRoot() }
```

**Change the accent:**

```kotlin
KontourTheme(
    colours = lightColourScheme(
        // `accent` is a `StatusColours`, not a `Color` — it is a whole tone, the
        // same shape as `success` and `danger`, because a component that takes a
        // tone has to be able to take this one.
        accent = StatusColours(
            solid = Color(0xFF0B6E99),
            onSolid = Color.White,
            container = Color(0xFFE3F2FA),
            onContainer = Color(0xFF083D55),
            border = Color(0xFFBEE0EF),
        ),
        focusRing = Color(0xFF0B6E99),
    ),
) { AppRoot() }
```

Keeping the whole tone together is what makes the five values consistent: pick
`solid` on its own and the first `ButtonVariant.Accent` you draw has a label
nobody can read on it.

`lightColourScheme()` and `darkColourScheme()` default every parameter, so this
keeps the rest of the palette intact.

**Change the typeface:**

```kotlin
KontourTheme(typography = kontourTypography(family = myBrandFamily)) { … }
```

The scale — sizes, weights, line heights, tracking — is preserved; only the
family changes.

**Change a component's geometry, everywhere:**

```kotlin
KontourTheme(
    componentDefaults = ComponentDefaults(
        uppercaseLabels = true,
        buttonPaddingMedium = 16.dp,
        navRailExpandedWidth = 240.dp,
    ),
) { … }
```

`ComponentDefaults` is the youngest of the families and the one with the
narrowest remit. Colour and type in this library have always come from the
theme; geometry did not — it sat as constants in forty-odd per-component
`*Defaults` objects, each overridable at a single call site and none of them
app-wide, so a design that wanted tighter buttons everywhere edited every call
or gave up.

A constant earns a place on it when **a different design system would plausibly
change it** and **no existing family can carry it**. Most do not: a menu's
minimum width and a rating's five stars are facts about the component, and a
control's corner is `Theme.shapes.control`, its border weight
`Theme.sizing.borderWidth` and its spring `Theme.motion` already. What is left
is button and row density, navigation geometry, the widths of overlays and
sheets, and the glass and backdrop dials.

`uppercaseLabels` is the one field that is not a measurement, and it is here
because there is nowhere else: casing is not something a `TextStyle` can carry.
It applies where the library turns a `String` you passed into text **on a
control** — a button, an extended FAB, a tag, a chip, a tab and a field's label
— and not to dialog titles, banner messages, list rows or menu items, which are
prose. Writing `Text("Save")` inside a slot instead of `+"Save"` opts out, the
same way it opts out of the slot's line limit and text style.

Overriding tokens mid-tree is cheap in the way that matters — `ProvideTokens`
provides locals and nothing else, and a nested `KontourTheme` at least declines
to install a second input-modality tracker. What it is *not* is free of meaning:
see the note under [Overriding](#overriding) for what a nested theme silently
discards, and prefer `ProvideTokens` for every override below the root.

---

## Physical feedback

Components declare an **intent** — what just happened, from the user's point of
view — and a single `FeedbackDispatcher` decides what that feels like. So the
whole app's haptics retune, or mute, in one place:

```kotlin
KontourTheme(haptics = HapticsLevel.Reduced) { AppRoot() }

// or replace the mapping outright
KontourTheme(feedback = FeedbackDispatcher { intent -> myEngine.play(intent) }) { AppRoot() }
```

Four levels, and **`Standard` is the default**:

| | Allows |
|---|---|
| `Off` | Nothing. A kiosk, a test, or a reader who has asked for silence. |
| `Reduced` | Outcomes only. Drops the ones that report progress — `Tap`, `Tick`, `Selection`, `KeyPress` — so a drag still reports arriving somewhere without buzzing the whole way there. |
| `Standard` | Everything a control does under a finger: taps, detents, thresholds, outcomes. |
| `Full` | Every intent, `KeyPress` included. |

`KeyPress` is what separates the top two, and it earns the split by being the one
intent **nothing in the library performs**: it is the only decorative one, which
is what a level above the default is for. Everything else a reader can feel is on
at `Standard`.

No call site needs a platform check — where a platform cannot vibrate, its
handler is already a no-op. Nor does one need a rate limit: every intent that
arrives in a stream shares one, so a slider's ticks and a chip's tap forty
milliseconds later are one rattle rather than two.

### How hard, and what each weight actually does per platform

An intent says what *happened*. A **feel** says how much of the hand that is
worth, and it is the second half of the vocabulary — added because it was missing
and the absence was reported from a phone: *"all the haptics feel heavy, there
doesn't seem to be the concept of a soft interaction for anything"*.

Five feels. Three of them are one pulse of increasing weight and are declared
lightest first; two are rhythms and are not on that scale, which is why the type
is `FeedbackFeel` and not `FeedbackWeight` — asking whether `Danger` is heavier
than `Heavy` has no answer.

| Feel | Which intents | What it is |
|---|---|---|
| `Light` | `Tick`, `GestureEnd`, `KeyPress` | A texture going past. Arrives in streams — a flung wheel crosses a row every 8ms — so it is also the tier the shared rate floor thins. |
| `Medium` | `Tap`, `Selection`, `DragThreshold` | A control answering, or what letting go will do changing. One event. |
| `Heavy` | `LongPress` | A threshold held long enough to mean something. |
| `Success` | `Confirm` | It worked. |
| `Danger` | `Reject`, `Warn` | It was refused, or it is about to be irreversible. |

`FeedbackIntent.feel` is public, and it is the whole of the policy: one `when`, in
common, the same on every platform. What differs per platform is only **how light
that platform can go**, and that is a capability table:

| Feel | Web | iOS | Android |
|---|---|---|---|
| `Light` | 0, 20ms | `selectionChanged()` — the picker tick | `SegmentTick` (**34**), else `TextHandleMove` (27) |
| `Medium` | 0, 20ms | light impact | `VirtualKey` (5) |
| `Heavy` | 0, 30ms | medium impact | `LongPress` (3) |
| `Success` | 18, 32, 36ms | notification, success | `Confirm` (30), else `VirtualKey` |
| `Danger` | 18, 28, 18, 28, 18ms | notification, error | `Reject` (30), else `LongPress` |

**A vibration motor needs roughly 10–20ms to spin up far enough to be felt.** That
number is why `Tick` is not on a 6ms pattern. It used to be `SegmentFrequentTick`,
and measured on the built site with `docs/measure-web.mjs --vibration` a stepped
slider dragged across its whole range produced `3 x [6]` — eighteen milliseconds
of motor time for an entire gesture. The same drag now produces `3 x [0,20]`.

**What that measurement did not settle is where 20ms sits.** It was a web
measurement, and it was applied to Android wholesale. On Android `VirtualKey` is
`EFFECT_CLICK` — a full key click, the weight a button press wants — so putting a
stream of detents on it meant a key click per row of a drum. Two intents had
already earned a platform seam each to work around the consequence, and below
Android 14 both of them resolved to `VirtualKey` anyway: a two-tier vocabulary
with a one-tier result, which is the report.

The lighter constant was there the whole time and unmentioned. `TEXT_HANDLE_MOVE`
has existed since **API 27**, one release below this library's own `minSdk`, so no
device it runs on lacks a light tier; it resolves to a tick rather than to a
click; and Compose exposes it as `HapticFeedbackType.TextHandleMove`. Nothing
needed `Vibrator`, `VibrationEffect` or the `VIBRATE` manifest permission — which
would also have bypassed the reader's own touch-feedback setting, and a UI library
must not do that for a tick.

**iOS had the same defect from the other direction.** `SegmentTick` and
`SegmentFrequentTick` are the *same* `selectionChanged()` generator there, and a
press claimed to be "the lightest thing the device can do", so a checkbox and a
row of a drum felt identical. A press is `Medium` now and routes to a light
impact, with `LongPress`'s medium impact above it: three rungs, on the platform
that has the most to say.

**On the web there are two felt weights, not three.** Below 20ms the single
pulses are 12ms and 6ms; 6ms is the silence the measurement was about and **12ms
has never been measured either way**, so `Light` and `Medium` are both the 20ms
pulse and this table says so rather than implying a scale the platform has not
been shown to have. `docs/measure-web.mjs --vibration` is what would settle it.

The **rate** limit is a separate question from weight and always was. A component
that wants a *finer* tick — the wheel picker, spinning past a row every few
milliseconds — now does get a lighter feel, and it still gets the same rate floor
on top: `DetentTicker` will not fire twice inside 80ms, which is a quarter duty
cycle rather than the continuous buzz that was reported. Note that `Tap` is
`Medium` and is thinned all the same, because three chips answering inside eighty
milliseconds are one rattle to the hand whatever each press weighs — the floor
asks about rate, and this section asks about weight.

**Where haptics do not happen at all**, written down so it is not re-reported as
a bug:

- **iOS Safari** has no Vibration API. On an iPhone in mobile web there are no
  haptics whatever the mapping says. Native iOS is unaffected.
- **Desktop**, all of it. There is no motor, and the handler returns immediately.
- **Android 13 and below** for a destructive alert's `Warn` was the last gap of
  this kind, and the fallbacks in the table above close it: `Confirm` and `Reject`
  are API-30 constants against a `minSdk` of 29, so on that one release they fall
  back to the nearest weight rather than to silence. `DragThreshold` used to be
  the named gap here — it was on an API-34 constant, so a pull-to-refresh
  threshold was silent on Android 13 and below. It is `Medium` now and answers on
  every supported release.

### What the library buzzes for

Two tiers. **An outcome reports something the user could not otherwise tell** —
and survives every level above `Off`. **A tap acknowledges a press** on a control
whose job is to answer one, says nothing the eye is not also getting, and is
therefore the tier a level can drop. Everything not on a row below is silent
whatever the level:

| Fires | Where | Why |
|---|---|---|
| A **detent crossed under a finger** | `Slider`, `RangeSlider`, `Knob`, `WheelPicker`, `SegmentedControl`, `TabBar` swipe, `ReorderableItem`, `BottomSheet`, `Carousel`, `ColourPicker`'s palette, `CalendarMonth` dragged, `ActivityCalendar` scrubbed | The finger is between two values and the eye is on something else. This is the case haptics exist for. All of them go through `DetentTicker` now, which is where the once-per-crossing guard and the rate limit both live. |
| A **threshold passed** | `PullToRefresh`, `SwipeActions`, `Switch` dragged, `Toast` swiped, `CalendarMonth` paged mid-drag | What letting go will do has just changed, and nothing on screen said so first. |
| A **slider run into its end** | `Slider`, `RangeSlider`, `Knob`, `ColourPicker`'s hue and opacity tracks | `DragThreshold`, once per wall a drag runs into — holding against the stop is one report, and backing off and pushing again is another. On the finger's position rather than the drawn squash, so it reports under reduced motion too. |
| A **long press becoming a gesture** | `Menu`, `Tooltip`, `ReorderableItem` | The press has been held long enough to mean something. Nothing has visibly happened yet, which is exactly why it needs reporting. |
| A **destructive question arriving** | `AlertDialog(destructive = true)` | The only one that fires *before* the thing it is about. Optional — see `hapticWarning`. |
| A **control answering a press** | `Checkbox`, `RadioButton`, `Chip`, `Switch` tapped, `SegmentedControl`, `Stepper`, `Rating`, `ColourSwatchPicker`, `CalendarMonth`, `Accordion`, `ExpandingListItem`, `AnimatedCounter` counting **down** | `Medium` — a step above the texture of a detent going past, on the controls whose whole job is to answer a press. Every one of them goes through `rememberTapFeedback`, which is one call site and one shared rate limit rather than a dozen of each. |

Nothing else does. A `Button` press, a tab, a menu item, a page control, a
navigation destination, a stepped slider *tapped* rather than dragged: all
silent, at every `HapticsLevel`, because they perform no intent at all for a
level to let through.

**A slider's end stop was removed once and has come back.** It went on the
grounds that a finger pushing against the end of a range is looking at a thumb that
has stopped and is visibly squashing under the push. It came back when that turned
out to be wrong in use: the thumb is under the finger doing the pushing, and the
value the finger is chasing is somewhere else on the screen. Asked for as "a haptic
in standard mode to all sliders that fires when you hit the end stop", and it is on
the sliders only — the carousel's first and last page and the wheel's ends stay
silent. The drum is still the strongest case for one, and it still loses: a hand
turning a wheel is already being told about every row it crosses, so the row it
stops on is the one report in the sequence that adds nothing.

**A button is the deliberate omission.** It is the control most likely to appear
twenty times on a screen, and a press that is already answered by a state change,
a navigation or a dialog does not need a second answer. The tap row above is for
controls that change a *value* under your finger and show it in a mark small
enough to miss.

The switch is on two rows, and the split is the policy in miniature: tapping it
is a decision you made, and dragging it past its midpoint is a threshold you
cannot see coming. Both report now — the first as a tap, the second as the
heavier threshold — where the tap used to be silent and read as the control
below it working and this one not.

### Why it used to do much more

The library previously fired on **57** call sites. Every `clickable` and every
`toggleable` in it buzzed, so a form with a dozen fields was a dozen
vibrations. A stepped slider fired on the press *and* on the release for a
gesture that crossed nothing. A swipe row fired four different intents across
one swipe — a tick per action width uncovered, the commit threshold, a
confirmation when the action ran, and a settle when the row came back. A
`WheelPicker` fired the moment it was composed, before anything touched it, so
opening a `TimePicker` was three buzzes for arriving at a screen.

**Nine sites are left, and the light tier that has just been added took none of
them.** That is the part worth understanding, because the obvious reading of
"twelve components now acknowledge a press" is that the audit has been undone.

Two shared seams do the work. `DetentTicker` is one call site for every snapping
component in the library — the sheet's detents, the carousel's pages, the
toast's threshold, the switch's midpoint — so a component joining it costs
nothing. `rememberTapFeedback` is one more, for every control
that answers a press. Between them the twenty-eight components on the table above
share two `perform` calls, which is also what makes the shared rate floor true by
construction rather than by each caller remembering it.

The switch's own site went with the move: it performed `DragThreshold` directly
and now goes through the ticker, which is what left room for the tap helper
without the ceiling rising. Net zero at the time; the count the build checks has
since gone to nine, when `SwipeActions`' point of no return moved onto the ticker
as well — a threshold is a detent with two sides.

The rule that removed the original forty-seven is the two-tier one above, and what
holds the line is a count rather than a review: the build fails if the number goes
up without the table going with it. Components are held to it by intent rather
than by haptic, which is the stronger claim — one that performs no intent is
silent under every level *and* under a replacement dispatcher, because there is
nothing for either to let through.

### Every site, before and after

The count is what the build checks; this is what the count is made of. Read it
when the answer to "should this buzz?" is not obvious — the argument for each
removal is the row it is on.

| Component | Fired | Now | Why |
|---|---|---|---|
| `Slider` | `Tick` on press, `Tick` per step dragged, `GestureEnd` on release | `Tick` per step **dragged**, `DragThreshold` on running into either end | A tap sets a value without travelling, so it crosses no detent. The release crosses nothing either. The end stop was added, removed and added back — see above. |
| `RangeSlider` | The same three | `Tick` per step dragged, `DragThreshold` at either end of the track | Same rule, same component, two handles. Running into the *other* thumb is not reported: the shove is already visible in the reach the thumbs deform by, and the other thumb is not the end of anything. |
| `Knob` | — | `Tick` per step turned **or spun past**, `DragThreshold` on running into either end | New, and built to the slider's rule from the start. A spin after a flick is still the user's gesture carrying on, so its steps tick; keys and assistive actions set a value without travelling and are silent. |
| `WheelPicker` | `Tick` on composition, `Tick` per row, `Tick` through a caller's spring | `Tick` per row | `snapshotFlow` emits its current value first, so every wheel buzzed on arrival — three for a `TimePicker`, before the screen had finished appearing. A `Reject` at the first or last value came and went: it was the best argued of the end stops, because a drum really is turned without being looked at, and it was still one report at the end of a sequence that had been reporting every row on the way. |
| `SegmentedControl` | `Selection` on tap, `GestureEnd` on release | `Tick` per segment **crossed**, `Tap` on a change | A thumb sliding past a segment is a detent, and both tiers are honestly present here: the drag reports crossings and a press reports that the control took it. What went is the `GestureEnd` — the thumb arriving is a thing the eye is on. |
| `TabBar` | `Selection` on tap ×2 | `Tick` per tab crossed by a **swipe** | Same distinction. Tapping a tab is watched; swiping past one is not. |
| `ReorderableItem` | `LongPress`, `GestureEnd` on drop | `LongPress` (touch only), `Selection` per position change, `Tick` on drop | The position changes are the news, once per gap crossed. The drop is lighter than they are, and the long press no longer fires on the mouse-and-handle path, where there is no threshold to announce. |
| `PullToRefresh` | `DragThreshold` | `DragThreshold` | Kept whole: it is the one moment that says letting go will do something. |
| `SwipeActions` | `Tick` per action width, `DragThreshold`, `Confirm` on run, `GestureEnd` on settle | `DragThreshold` at the point of no return, and again backing off it | Four intents across one swipe. `actionWidth` is arithmetic, not an anchor. What is left is the line past which letting go runs the action, reported through `DetentTicker` as a two-sided threshold, so a row moved in code with `animateTo` is silent. |
| `Switch` | `Selection` on tap, and on the crossing, and on release | `Tap` on a tap, `DragThreshold` on the **crossing** of a drag | Two gestures, two different reports, and the switch is the only component that gives both. The drag's is the interesting one: it commits as the thumb goes over the midpoint, so what letting go will do changes under the finger with nothing on screen having said so. The tap's came back after the silence was reported as the control reading dead next to a checkbox that answers — and it goes through the ticker now rather than performing directly, which is a midpoint being a two-sided threshold and is what paid for the tap helper's site. |
| `BottomSheet` | — | `Tick` per detent **dragged** across | The one the audit left open on purpose. `targetDetent` is the right signal and changes the instant a drag passes the threshold, but nothing told that apart from the same field changing because code called `animateTo` — and a sheet that buzzes when it is opened programmatically is worse than one that is silent. `SheetState.draggedByHand` is that distinction, taken from the drag's own interaction source. Costs nothing against the ceiling: it goes through `DetentTicker` like every other detent. |
| `Carousel` | — | `Tick` per page crossed **under a finger** | A carousel's pages are detents in the strictest sense: the card snaps to one and rests there, and the eye is on the card rather than on a counter. What it must not report is a page reached any other way — the accessibility actions, the indicator's dots and an autoplay all call `scrollToPage`, and a carousel that buzzes when a dot is clicked is buzzing for something the reader is already watching. The drag signal comes from the list's own interaction source, plus the pointer drag's, which does not go through the list at all. |
| `Toast` | — | `DragThreshold` as the swipe passes its dismiss point | One report, at the one moment in the gesture that has a consequence. The threshold is *derived from the release's own condition* rather than set beside it — one expression decides the buzz and the dismissal — which is the mistake `SwipeActions` shipped and then fixed. Also free against the ceiling: a threshold is a detent with two sides, so it goes through the same ticker with an index of 0 or 1. |
| `AlertDialog` | — | `Warn`, for a destructive alert | The one addition, and the only haptic that fires for something that has **not** happened yet. Opt-out; inert on a non-destructive alert however it is set. |
| `Menu`, `Tooltip` | `LongPress` | `LongPress` | The press has been held long enough to mean something and nothing visible has happened yet. |
| `Menu` item | `Selection` | — | A menu item is a button. |
| `Rating` | `Selection` per star, `GestureEnd`, one more on tap | `Tap` per mark **taken** | Five marks on one continuous track. Nothing rests between them, so this is not a detent and never was — the report's words were "there's no real detents here". What the silence missed is that a drag is how the value gets *chosen*: every mark the thumb passes is a value taken, and a control you set without looking is one whose mark is small enough to miss. Once per value, not once per frame, which is the guard the drag already had for the callback. |
| `Stepper` | `Tick` ×2 | `Tap` on each button | Not `Tick`: nothing snaps, and `FeedbackIntent.Tick`'s own doc naming a stepper as its case was the doc being wrong rather than the component. A `Tap` is the right tier — it acknowledges the press and drops at `Reduced` — and it is what a stepper held down to run a number up wants, since the shared floor thins a held repeat to a rate a hand can tell apart. |
| `Checkbox`, `RadioButton`, `Chip`, `ColourSwatchPicker` | `Selection`, part of 11 sites | `Tap` | The controls the tap tier is *for*: a value that changes in a mark a few millimetres across, which is exactly the thing a reader can look away from and miss. Eleven direct `Selection` calls became four `tap()` calls behind one site. |
| `SelectionRow`, `Select` | `Selection`, the rest of those 11 | — | A row and a field. Both answer with a layout change big enough to see without being told. |
| `IconButton`, `FloatingActionButton` | `Selection` ×2, `Confirm` ×3 | — | A button press is the least surprising thing a screen does. |
| `Accordion`, `CalendarMonth` | `Selection`, part of 5 sites | `Tap` | Disclosure and a date cell. A cell in a month grid is the smallest target the library has, and a panel opening beneath the fold is a press whose answer is off screen. |
| `ListItem`, `TimePicker` | `Selection`, the rest of those 5 | — | A row navigates, and a `TimePicker` is three `WheelPicker`s that report their own detents and their own ends. A fourth report over the top would be the same news twice. |
| `Breadcrumbs`, `NavDrawer`, `NavExpansion`, `NavItemContent`, `Pagination` | `Selection`, 6 sites | — | Navigation. The screen changing is the feedback. |
| `ExpandingListItem` | `Selection` | `Tap` | Same case as the accordion, in a list. |
| `ColourPicker` | — | `Tick` per palette cell, one at a spectrum edge or a track end | Three surfaces and two kinds of report. The palette's cells are detents in the strictest sense — the colour visibly steps — and the spectrum and the hue and opacity tracks have edges a finger can lean on with nothing to show it has. The spectrum's two axes give four walls, packed into one index so that a corner reached from an edge reports the second wall as well as the first. |
| `AnimatedCounter` | — | `Tap` on a value going **down** | One direction only. A number that rises is news a reader can take at their leisure; one that falls is a seat count or a time remaining they may be about to act on, and it is the case `warnBefore` exists for. Fires whether or not there is a warning to hold it and whether or not motion is reduced — a reader who asked for less movement is the one the drop is quietest for. |
| `PaneScaffold` | `DragThreshold`, `GestureEnd` | — | A pane divider dragged with a mouse on a wide screen, which is the one input that cannot feel a haptic at all. |

Three judgement calls inside that, written down because they are the ones most
likely to be argued with:

- **A detent is a place something snaps to, and a tap is not a detent.** A
  `Rating`'s five marks are five drawings on one continuous track — nothing rests
  between them — so a drag across it reports a `Tap` per mark it *takes* rather
  than a `Tick` per mark it crosses. The distinction is not pedantry: a level
  that keeps detents and drops taps is a real setting, and putting the rating on
  the wrong side of it would mean either losing a slider's ticks to keep the
  rating or the reverse. `SwipeActions` had the opposite mistake: it ticked per
  `actionWidth`, which is arithmetic, not an anchor.
- **A tap onto a detent has not crossed one.** Pressing a stepped slider sets a
  value without travelling, so the tick is gated on the drag. The step index is
  still recorded, or the first pixel of a drag would tick for the step the thumb
  is already on.
- **A drop is lighter than the reorders it follows.** `ReorderableItem` reports
  each position change with `Selection` and the drop with `Tick` —
  `SegmentTick` against `SegmentFrequentTick`. The news already happened, once
  per gap the row crossed.

### Adding your own

Everything above is reachable, and nothing here is a private mechanism. Two
entry points, and which one you want depends on whether the thing you are
reporting happens once or keeps happening.

**A one-shot.** `LocalFeedback.current` is a `FeedbackDispatcher` and
`perform(intent)` is all of it. The [`HapticsLevel`](#physical-feedback) gate is
applied inside, so a reader who has turned haptics down is respected without the
call site knowing.

```kotlin
val feedback = LocalFeedback.current

Button(onClick = { feedback.perform(FeedbackIntent.Warn); confirm() }) {
    +"Delete"
}
```

**Anything continuous.** Do *not* call `perform` from inside a drag. A gesture
sits between two values for many frames and reports the same number on each of
them, so a `perform` on "the value is on a detent" fires sixty times a second —
and a vibration motor has no mass to stop it, so sixty ticks a second is one
continuous buzz. `rememberDetentTicker()` is the guard, and it is public for
exactly this:

```kotlin
val ticker = rememberDetentTicker()

// in the drag:
ticker.at(nearestNotch)
// when it ends:
ticker.reset()
```

It fires on the index *changing*, not on where it is, and it holds a floor of
80ms between reports — the number of an infinite wheel picker flung fast enough
to cross a row every 8ms, which is where "less punchy" was reported from.

**A threshold is a detent with two sides.** Pass `FeedbackIntent.DragThreshold`
and an index of 0 or 1, and you get one report as the drag passes the point
where letting go would do something, and one more if it comes back:

```kotlin
val latch = rememberDetentTicker(FeedbackIntent.DragThreshold)

latch.at(if (past) 1 else 0)
```

That is the same mechanism rather than an analogy for it, and it is what `Toast`
uses. Deriving `past` from the *same expression* the release acts on is the part
worth copying: written twice, the buzz and the commit drift apart.

**Replacing the dispatcher entirely** is the other direction, and
`FeedbackDispatcher` is a `fun interface` — provide your own through
`LocalFeedback` to route the library's intents somewhere else, or to silence a
subset without touching `HapticsLevel`.

---

## Writing a whole theme

A theme is four values. Build them with the factory functions rather than the
constructors, so you inherit defaults for anything you do not care about:

```kotlin
object OceanTheme {
    fun colours(dark: Boolean) = if (dark) {
        darkColourScheme(
            accent = StatusColours(
                solid = Color(0xFF4FC3F7),
                onSolid = Color(0xFF002E3F),
                container = Color(0xFF0A2A38),
                onContainer = Color(0xFFB3E5FC),
                border = Color(0xFF17495E),
            ),
            brand = Color(0xFF4FC3F7),
            focusRing = Color(0xFF4FC3F7),
        )
    } else {
        lightColourScheme(
            accent = StatusColours(
                solid = Color(0xFF01579B),
                onSolid = Color(0xFFFFFFFF),
                container = Color(0xFFE1F5FE),
                onContainer = Color(0xFF01426A),
                border = Color(0xFFB6E2F7),
            ),
            brand = Color(0xFF4FC3F7),
            focusRing = Color(0xFF01579B),
        )
    }

    // The whole ladder, from one rung and one step. Writing this as
    // `Shapes(small = …, medium = …)` — which this page recommended for a
    // while — leaves `extraSmall` at the default 10dp, so the scale runs
    // 10, 2, 4, 28, 34 and the *extra small* corner is the second largest
    // in it. A ladder has to be set as a ladder.
    val shapes = kontourShapes(extraSmall = 2.dp, step = 2.dp, smoothing = 0f)
}

@Composable
fun OceanApp(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    KontourTheme(
        darkTheme = dark,
        colours = OceanTheme.colours(dark),
        shapes = OceanTheme.shapes,
        content = content,
    )
}
```

**Verify its contrast**, with the check the built-in schemes are held to:

```kotlin
@Test
fun theOceanSchemeIsReadable() {
    for (dark in listOf(false, true)) {
        val failures = contrastFailures(OceanTheme.colours(dark), ContrastLevel.Standard)
        assertTrue(failures.isEmpty(), "Ocean, dark=$dark: $failures")
    }
}
```

`contrastFailures` walks every foreground/background pairing a component is
allowed to produce and returns the ones that miss, each naming the token and the
number it needed. Contrast is not something you can eyeball — three of the values
originally proposed for the built-in schemes looked fine and failed by a tenth of
a point.

The built-in schemes run this on every build. A scheme of your own does not,
until you write the four lines above.

One thing it cannot promise: **a role added to `ColourScheme` owes it nothing
automatically.** The pairings are written by hand, because nothing can infer
which ground a new token is drawn on or whether it is text.

Remember the `brand` / `accent` split when authoring: `brand` may be any brand
colour at all, including one that fails contrast, because it is only ever
decoration. `accent` must be readable. If your brand colour happens to be dark
enough, set both to it.

---

## Contrast tiers

`kontourColourScheme(dark, contrast)` picks between the four built-in schemes.
A custom theme that wants a high-contrast tier authors a second scheme and
selects on `ContrastLevel` the same way — and can read
`platformPrefersHighContrast()` to know which tier to build, the same function
`KontourTheme` uses for its own default.

If you do not author one, pass your single scheme regardless of tier — users who
asked for high contrast will get standard contrast, which is a downgrade you
should make knowingly rather than by omission.

**Authoring one is a starting point plus a check.** `highContrastLightColourScheme`
and `highContrastDarkColourScheme` take every token, each defaulted to the value
the AAA ratio demands, so a brand overrides what it owns and inherits forty tuned
greys, washes and status tones:

```kotlin
val enhanced = highContrastDarkColourScheme(
    background = Color(0xFF0A0A0B),
    accent = myEnhancedAccent,
)
```

Overriding a ground makes the ratio yours, and there is one call that tells you
whether you kept it:

```kotlin
@Test
fun myEnhancedTierIsEnhanced() {
    assertEquals(emptyList(), contrastFailures(enhanced, ContrastLevel.High))
}
```

The factory does not run that for you. A palette that lands at 6.9:1 instead of
7 should not crash the app, and least of all should it crash only for the people
who have the high-contrast setting switched on.

---

## The generator, later

`ColourScheme` is a plain `@Immutable data class` built by *factory functions*
rather than only by its constructor. That shape is deliberate.

Today the factories are hand-authored:

```kotlin
fun lightColourScheme(…): ColourScheme              // every token
fun darkColourScheme(…): ColourScheme               // every token
fun highContrastLightColourScheme(…): ColourScheme  // every token, AAA defaults
fun highContrastDarkColourScheme(…): ColourScheme   // every token, AAA defaults
```

The high-contrast pair used to take three parameters — accent, brand, focus ring
— on the argument that the rest is not a design choice: at AAA the grounds are
pure white or pure black, the content is its opposite, and the greys are the
lightest values that still clear 7:1. That argument is right about the *defaults*
and was wrong as a restriction. It meant a product whose ground is neither pure
white nor pure black had no enhanced tier available at all, because there was no
way to keep its ground and take the forty tuned values with it.

Withholding a parameter defends a guarantee by blocking every legitimate use
along with the illegitimate ones. The guarantee is a ratio, so the way to keep it
is to check the ratio — see `contrastFailures` above.

Deriving a full palette from a single seed colour — for user-selectable accents,
or Android's wallpaper-derived colours — means adding one more factory:

```kotlin
fun generatedColorScheme(seed: Color, dark: Boolean, contrast: ContrastLevel): ColourScheme
```

and nothing else. No component changes, because no component knows where its
`ColourScheme` came from. That is the entire reason components are forbidden from
touching `Palette` directly — the ban is on *components* reading it, not on you:
`Palette` is public, so an app that wants to change one colour can keep the
other twenty-eight instead of starting from raw hex.

The generator is not built yet. When it is, it will need to satisfy the same
contrast suite as the static schemes — which is the hard part of that work, and
the reason it was deferred rather than rushed.
