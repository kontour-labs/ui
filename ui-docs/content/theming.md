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
KontourTheme(haptics = HapticsLevel.Essential) { AppRoot() }

// or replace the mapping outright
KontourTheme(feedback = FeedbackDispatcher { intent -> myEngine.play(intent) }) { AppRoot() }
```

`HapticsLevel.Full` allows every intent, `Essential` drops the continuous ones
(`Tick`, `Selection`, `KeyPress`) and keeps the ones that report an outcome, and
`Off` is silence. No call site needs a platform check — where a platform cannot
vibrate, its handler is already a no-op.

### What an intent actually does, per platform

Worth having in front of you, because the constant names are Android's and what
they *do* is not. The web column is the `navigator.vibrate` pattern in
milliseconds; iOS is the generator; Android is the API level the constant was
added in.

| Intent | Constant | Web | iOS | Android |
|---|---|---|---|---|
| `Tick` | `VirtualKey` | 0, 20ms | light impact | 5 |
| `Selection` | `ContextClick` | 12ms | medium impact | 23 |
| `DragThreshold` | `GestureThresholdActivate` | 12ms | light impact | **34** |
| `LongPress` | `LongPress` | 0, 30ms | medium impact | 3 |
| `GestureEnd` | `GestureEnd` | 12ms | light impact | 30 |
| `Confirm` | `Confirm` | 18, 32, 36ms | notification, success | 30 |
| `Reject`, `Warn` | `Reject` | 18, 28, 18, 28, 18ms | notification, error | 30 |
| `KeyPress` | `KeyboardTap` | 6ms | **nothing** | 8 |

**A vibration motor needs roughly 10–20ms to spin up far enough to be felt.**
That number is the whole reason this table exists. `Tick` used to be
`SegmentFrequentTick`, which is 6ms on the web — so every detent in the library
issued a pulse and nothing arrived. Measured on the built site with
`docs/measure-web.mjs --vibration`, a stepped slider dragged across its range
produced `3 x [6]`: eighteen milliseconds of motor time for an entire gesture.
The same drag now produces `3 x [0,20]`.

It was no better elsewhere. `SegmentFrequentTick` and `SegmentTick` are the
*same* `selectionChanged()` generator on iOS, so `Tick` and `Selection` were
indistinguishable there; and both are Android 14 constants, so below that they
did nothing at all.

**There is no lighter tier that is still felt.** Below `Tick`'s 20ms the web
patterns are 12ms and 6ms, and 6ms is the silence above. So a component that
wants a *finer* tick — the wheel picker, spinning past a row every few
milliseconds — does not get a lighter intent. It gets the same one, less often:
`DetentTicker` will not fire twice inside 80ms, which is a quarter duty cycle
against a 20ms pulse rather than the continuous buzz that was reported.

**Where haptics do not happen at all**, written down so it is not re-reported as
a bug:

- **iOS Safari** has no Vibration API. On an iPhone in mobile web there are no
  haptics whatever the mapping says. Native iOS is unaffected.
- **Desktop**, all of it. There is no motor, and the handler returns immediately.
- **Android below 14** for `DragThreshold` alone, which is still on an API-34
  constant, so a pull-to-refresh threshold is silent there. Named rather than
  fixed.

### What the library buzzes for

**A haptic reports something the user could not otherwise tell.** There are four
of those, and everything else is silent:

| Fires | Where | Why |
|---|---|---|
| A **detent crossed under a finger** | `Slider`, `RangeSlider`, `WheelPicker`, `SegmentedControl`, `TabBar` swipe, `ReorderableItem` | The finger is between two values and the eye is on something else. This is the case haptics exist for. All of them go through `DetentTicker` now, which is where the once-per-crossing guard and the rate limit both live. |
| A **threshold passed** | `PullToRefresh`, `SwipeActions` | What letting go will do has just changed, and nothing on screen said so first. |
| A **long press becoming a gesture** | `Menu`, `Tooltip`, `ReorderableItem` | The press has been held long enough to mean something. Nothing has visibly happened yet, which is exactly why it needs reporting. |
| A **destructive question arriving** | `AlertDialog(destructive = true)` | The only one that fires *before* the thing it is about. Optional — see `hapticWarning`. |

Nothing else does. A `Button` press, a `Switch` flip, a `Checkbox`, a `Chip`, a
date cell, a tab, a menu item, a page control, a `Rating` drag, a stepped slider
*tapped* rather than dragged: all silent, at every `HapticsLevel`, because they
perform no intent at all for a level to let through.

### Why it used to do much more

The library previously fired on **57** call sites. Every `clickable` and every
`toggleable` in it buzzed, so a form with a dozen fields was a dozen
vibrations. A stepped slider fired on the press *and* on the release for a
gesture that crossed nothing. A swipe row fired four different intents across
one swipe — a tick per action width uncovered, the commit threshold, a
confirmation when the action ran, and a settle when the row came back. A
`WheelPicker` fired the moment it was composed, before anything touched it, so
opening a `TimePicker` was three buzzes for arriving at a screen.

Eleven sites are left. The rule that removed the other forty-six is the one in
bold above, and what holds the line is a count rather than a review: the build
fails if that number goes up. Components are held to it by intent rather than by
haptic, which is the stronger claim — one that performs no intent is silent
under every level *and* under a replacement dispatcher, because there is nothing
for either to let through.

### Every site, before and after

The count is what the build checks; this is what the count is made of. Read it
when the answer to "should this buzz?" is not obvious — the argument for each
removal is the row it is on.

| Component | Fired | Now | Why |
|---|---|---|---|
| `Slider` | `Tick` on press, `Tick` per step dragged, `GestureEnd` on release | `Tick` per step **dragged** | A tap sets a value without travelling, so it crosses no detent. The release crosses nothing either. |
| `RangeSlider` | The same three | `Tick` per step dragged | Same rule, same component, two handles. |
| `WheelPicker` | `Tick` on composition, `Tick` per row, `Tick` through a caller's spring | `Tick` per row, through `DetentTicker` | `snapshotFlow` emits its current value first, so every wheel buzzed on arrival — three for a `TimePicker`, before the screen had finished appearing. |
| `SegmentedControl` | `Selection` on tap, `GestureEnd` on release | `Tick` per segment **crossed** | A thumb sliding past a segment is a detent; pressing one is a button press. |
| `TabBar` | `Selection` on tap ×2 | `Tick` per tab crossed by a **swipe** | Same distinction. Tapping a tab is watched; swiping past one is not. |
| `ReorderableItem` | `LongPress`, `GestureEnd` on drop | `LongPress` (touch only), `Selection` per position change, `Tick` on drop | The position changes are the news, once per gap crossed. The drop is lighter than they are, and the long press no longer fires on the mouse-and-handle path, where there is no threshold to announce. |
| `PullToRefresh` | `DragThreshold` | `DragThreshold` | Kept whole: it is the one moment that says letting go will do something. |
| `SwipeActions` | `Tick` per action width, `DragThreshold`, `Confirm` on run, `GestureEnd` on settle | `DragThreshold` | Four intents across one swipe. `actionWidth` is arithmetic, not an anchor. |
| `AlertDialog` | — | `Warn`, for a destructive alert | The one addition, and the only haptic that fires for something that has **not** happened yet. Opt-out; inert on a non-destructive alert however it is set. |
| `Menu`, `Tooltip` | `LongPress` | `LongPress` | The press has been held long enough to mean something and nothing visible has happened yet. |
| `Menu` item | `Selection` | — | A menu item is a button. |
| `Rating` | `Selection` per star, `GestureEnd`, one more on tap | — | Five marks on one continuous track. Nothing rests between them, so a drag across it crosses no detent — the report's words were "there's no real detents here". |
| `Stepper` | `Tick` ×2 | — | Two buttons and a number that changes where you are looking. `FeedbackIntent.Tick`'s own doc used to name a stepper as its case; the doc was what was wrong, and it has been corrected rather than the component. |
| `Switch`, `Checkbox`, `RadioButton`, `SelectionRow`, `Chip`, `ColourSwatchPicker`, `Select` | `Selection`, 11 sites between them | — | Every one of these is a control whose whole job is to change visibly under the finger. |
| `IconButton`, `FloatingActionButton` | `Selection` ×2, `Confirm` ×3 | — | A button press is the least surprising thing a screen does. |
| `ListItem`, `Accordion`, `CalendarMonth`, `TimePicker` | `Selection`, 5 sites | — | Rows, disclosure, a date cell, an hour. All watched. |
| `Breadcrumbs`, `NavDrawer`, `NavExpansion`, `NavItemContent`, `Pagination` | `Selection`, 6 sites | — | Navigation. The screen changing is the feedback. |
| `PaneScaffold` | `DragThreshold`, `GestureEnd` | — | A pane divider dragged with a mouse on a wide screen, which is the one input that cannot feel a haptic at all. |

Three judgement calls inside that, written down because they are the ones most
likely to be argued with:

- **A detent is a place something snaps to.** A `Rating`'s five marks are five
  drawings on one continuous track — nothing rests between them — so dragging
  across it is silent. `SwipeActions` had the same mistake in reverse: it ticked
  per `actionWidth`, which is arithmetic, not an anchor.
- **A tap onto a detent has not crossed one.** Pressing a stepped slider sets a
  value without travelling, so the tick is gated on the drag. The step index is
  still recorded, or the first pixel of a drag would tick for the step the thumb
  is already on.
- **A drop is lighter than the reorders it follows.** `ReorderableItem` reports
  each position change with `Selection` and the drop with `Tick` —
  `SegmentTick` against `SegmentFrequentTick`. The news already happened, once
  per gap the row crossed.

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
other twenty-seven instead of starting from raw hex.

The generator is not built yet. When it is, it will need to satisfy the same
contrast suite as the static schemes — which is the hard part of that work, and
the reason it was deferred rather than rushed.
