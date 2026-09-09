# Tokens

*Also on this page: `ProvideConcentric`.*

Everything a component is allowed to look like. Read through `Theme` inside a
composable:

```kotlin
Theme.colours.surface
Theme.typography.titleMedium
Theme.spacing.md
Theme.shapes.medium
Theme.elevation.low
Theme.motion.default
Theme.sizing.iconMedium
```

A component that hardcodes a value instead cannot be re-themed, will not respond
to the contrast setting, and will not honour reduced motion. There is no
exception to this — if a value you need is missing, add a token.

---

## Colour

Defined in [`theme/ColourScheme.kt`](../../ui/src/commonMain/kotlin/io/kontour/ui/theme/ColourScheme.kt);
raw values in [`theme/Palette.kt`](../../ui/src/commonMain/kotlin/io/kontour/ui/theme/Palette.kt).

Four built-in schemes: light, dark, and a high-contrast variant of each.

### Grounds

| Token | Light | Dark | For |
|---|---|---|---|
| `background` | `#FFFFFF` | `#121212` | The page |
| `surface` | `#FFFFFF` | `#221E29` | Cards, sheets, menus |
| `surfaceSunken` | `#F6F6F6` | `#1A1820` | Wells: input fills, code blocks, table stripes |
| `surfaceRaised` | `#FFFFFF` | `#2A2633` | Above `surface` — menus over cards |
| `surfaceInverse` | `#121212` | `#F4F1F8` | Toasts, tooltips |
| `onSurfaceInverse` | `#FFFFFF` | `#121212` | Content on `surfaceInverse` |

### Content

| Token | Light | Dark | For |
|---|---|---|---|
| `content` | `#121212` | `#F4F1F8` | Anything the user reads to understand the screen |
| `contentMuted` | `#545454` | `#A79FB0` | Captions, timestamps, secondary labels |
| `contentSubtle` | `#6B6B6B` | `#9A93A2` | Placeholders, tertiary hints. Still real text, still 4.5:1 |
| `contentDisabled` | `#A3A3A3` | `#5C5566` | Genuinely disabled controls only (WCAG-exempt) |

### Lines

| Token | Light | Dark | For |
|---|---|---|---|
| `outline` | `#E5E5E5` | `#3C3547` | Dividers and decorative rules |
| `outlineStrong` | `#8A8A8A` | `#7C7484` | The boundary of anything interactive |
| `outlineSubtle` | `#EFEFEF` | `#2C2735` | The faintest rule, for dense lists |

`outline` is too light to bound a control — it does not meet the 3:1 that
WCAG 1.4.11 asks of a UI component boundary. Inputs, checkboxes and switches use
`outlineStrong`. This is the single most commonly got-wrong pair.

### Actions

| Token | Light | Dark | For |
|---|---|---|---|
| `primary` / `onPrimary` | `#121212` / `#FFFFFF` | `#F4F1F8` / `#121212` | The solid call to action |
| `accent` | a `StatusColours`, below | | The brand, as a tone |
| `brand` | `#1D4ED8` | `#93C5FD` | The literal brand colour, decoration first |
| `focusRing` | `#1D4ED8` | `#93C5FD` | The keyboard focus indicator |

**`accent` is a tone, not four loose fields.** It is a `StatusColours` exactly
like `success` and the rest, so there is one tone type and six tones:

| Member | Light | Dark | Was |
|---|---|---|---|
| `accent.solid` | `#1D4ED8` | `#93C5FD` | `accent` |
| `accent.onSolid` | `#FFFFFF` | `#0D1B2E` | `onAccent` |
| `accent.container` | `#EFF6FF` | `#1B2739` | `accentContainer` |
| `accent.onContainer` | `#1E3A8A` | `#BFDBFE` | `onAccentContainer` |
| `accent.border` | `#C7DCFD` | `#2C3E5C` | *new* |

The point is not tidiness. A component that takes a tone can now take *this*
one, which is what `ButtonVariant.Accent`, `BannerTone.Accent` and
`ToastTone.Accent` are made of — and `TagTone.Accent`, which already existed,
had to reach past the group and assemble itself from three separate fields.

It also means a custom scheme has to supply the whole tone rather than one
colour. That is the point too: the old `lightColourScheme(accent = Color(...))`
left `accentContainer` at the default, so a green accent came with a blue
selected-state and the signature said nothing about it.

**On `brand` versus `accent`.** They are separate roles because a brand colour is
chosen to be recognised and an accent has to be *read*, and nothing guarantees
one colour can do both. `brand` is the literal mark — a logo, a decorative rule,
a splash — and is the one role the contrast suite does not walk; `accent.solid`
is the tone components are built from, and every pairing of it is checked.

Today's default happens to satisfy both: `#1D4ED8` is 6.7:1 on white and
`#93C5FD` is 10.39:1 on `#121212`, so the shipped `brand` would carry text
perfectly well. That is a fact about this palette and not about the role. A brand
that hands over a colour which cannot — GTurbo's `#E11F26` is 4.17:1 on its own
near-black ground and unreadable on anything lighter — puts it in `brand`,
supplies a readable tone in `accent`, and the split does its job. Because the
suite skips `brand`, that is also the one place a scheme can put a colour it has
decided nobody has to read.

### Status

Each of `success`, `warning`, `danger` and `info` is a `StatusColours` with five
fields, matching how the web properties already use them:

| Field | For |
|---|---|
| `solid` / `onSolid` | Filled backgrounds — badges, solid buttons, progress fills |
| `container` / `onContainer` | Soft tints — banners, chips, callouts. `onContainer` doubles as the tone's standalone text colour |
| `border` | Hairline around `container`. Decorative — no contrast requirement |

| Tone | Light `solid` | Dark `solid` |
|---|---|---|
| `success` | `#2E7D32` | `#7BE08A` |
| `warning` | `#B45309` | `#FDBA74` |
| `danger` | `#B91C1C` | `#FCA5A5` |
| `info` | `#6D28D9` | `#C4B5FD` |

### Overlays

| Token | For |
|---|---|
| `scrim` | Dims content behind a modal, which also [blurs it](overlays.md#the-backdrop) |
| `overlayHover` / `overlayPressed` / `overlayDragged` | The tonal washes `KontourIndication` composites over a control |

### Source code

`code` is a `CodeColours` with four fields, and this documentation site is what
draws them — a fenced block on any page is coloured from these.

| Field | For |
|---|---|
| `plain` | Identifiers, punctuation, everything with no special meaning |
| `keyword` | `fun`, `val`, `when`, and annotations |
| `literal` | Strings, characters and numbers |
| `comment` | `//` and `/* */` |

| Field | Light | Dark |
|---|---|---|
| `keyword` | `#1E3A8A` | `#93C5FD` |
| `literal` | `#1B5E20` | `#7BE08A` |

`plain` and `comment` follow `content` and `contentMuted` unless a theme moves
them.

All four are drawn on `surfaceSunken` and all four are checked against it at the
scheme's own tier — 4.5:1 standard, 7:1 high contrast. **Highlighting is
decorative**: the code says the same thing in one colour, and nothing here is
the only way to know anything. That is why the four are held to being *readable*
and not to being far apart from one another — on the high-contrast light scheme
they are all necessarily close to black, which is what 7:1 on a near-white
ground means.

---

## Type

Outfit, shipped as five static instances cut from the upstream variable font, so
weights render identically on every target. SIL OFL; licence at
[`app/ui/licenses/Outfit-OFL.txt`](../../ui/licenses/Outfit-OFL.txt).

Beside it, two cuts of JetBrains Mono for code, keyboard keys and figures — also
SIL OFL, licence at
[`app/ui/licenses/JetBrainsMono-OFL.txt`](../../ui/licenses/JetBrainsMono-OFL.txt).
It is bundled for the same reason Outfit is: `FontFamily.Monospace` is Menlo on
one machine, Consolas on another and whatever a browser was configured with on
the web, and a library that ships five static weights so they "render identically
on every target" should not then ask the platform what it thinks a monospaced
face looks like.

| Role | Large | Medium | Small | Weight | Line height |
|---|---|---|---|---|---|
| `display` | 48 | 40 | 32 | 800 | 1.10–1.20 |
| `headline` | 28 | 24 | 20 | 700 / 600 | 1.25–1.35 |
| `title` | 18 | 16 | 14 | 600 | 1.40 |
| `body` | 17 | 15 | 13 | 400 | 1.60 / 1.50 |
| `label` | 16 | 14 | 12 | 600 | 1.20 |

Plus two that are not rungs on that ladder:

- **`eyebrow`** — 13sp, weight 700, `+0.14em` tracking, meant to be set in upper
  case. The label above a section heading (`.mono-label` on the marketing site).
  It was called `monoLabel` and is Outfit: nothing about it is monospaced, and
  the name cost the library six call sites that reached past it for
  `FontFamily.Monospace` because it plainly was not what they wanted.
- **`mono`** — `bodyMedium`'s metrics in the monospaced face, with `tnum` on.
  Figures line up whatever they are, which is what a readout redrawn in place
  needs; and because Outfit ships `tnum`, that holds even under a theme that
  supplied no monospaced family at all.

For a different size in that face, take the family and keep the metrics:

```kotlin
Theme.typography.bodySmall.copy(fontFamily = Theme.typography.mono.fontFamily)
```

That is what the code blocks on this site do, and it is why `mono` is one style
rather than a second scale of nine.

Sizes are in `sp` and scale with the OS text-size setting. Every style trims
half-leading at the top and bottom of a block, so visual bounds match layout
bounds and vertical centring behaves.

Which one to reach for:

- **display** — hero moments. At most one per screen.
- **headline** — screen and section titles.
- **title** — card headers, list headlines, dialog titles.
- **body** — everything the user actually reads.
- **label** — text inside a control: buttons, chips, tabs, form labels.
- **mono** — anything a reader is meant to compare column-wise or copy verbatim:
  telemetry, prices, identifiers, code.

A brand that supplies its own faces passes both:

```kotlin
KontourTheme(typography = kontourTypography(myBrandFace, myMonoFace)) { … }
```

The second argument defaults to the first rather than to the bundled mono — a
brand that named one face and said nothing about a second has asked for *its*
face, not for its face beside somebody else's.

---

## Spacing

A 4dp grid, named to match `--space-*` in the admin site so values port across
without arithmetic.

| Token | | Typical use |
|---|---|---|
| `xxs` | 4dp | Between an icon and its label |
| `xs` | 8dp | Inside a chip or badge |
| `sm` | 12dp | Between related rows |
| `md` | 16dp | Default padding inside a card; screen gutter |
| `lg` | 24dp | Between groups |
| `xl` | 32dp | Between sections |
| `xxl` | 40dp | Around a screen's hero |

`Theme.spacing.of(n)` gives `n × 4dp` for the rare one-off. Repeated use of the
same `of(n)` is a sign that value wants a name.

---

## Shape

| Token | Radius | Used by |
|---|---|---|
| `extraSmall` | 10dp | Badges, tags, inline code |
| `small` | 16dp | Small containers, swatches |
| `medium` | 22dp | Cards, list groups, menus |
| `large` | 28dp | Dialogs, large cards |
| `extraLarge` | 34dp | Sheets, hero panels |
| `pill` | 50% | Avatars, scrollbars, indicators, both FABs, `FabMenu`, a floating `NavBar` |
| `sheet` | 34dp top only | Bottom sheets |
| `sideSheet` | 34dp leading only | Side sheets |

**Set the ladder as a ladder.** `kontourShapes(extraSmall = 6.dp, step = 2.dp)`
is the whole scale from one rung and one step, with `container`, `panel`,
`sheet` and `sideSheet` following it down. Reaching for `Shapes(small = …,
medium = …)` instead leaves the rungs you did not name at the defaults, so a
scale meant to be small runs 10, 2, 4, 28, 34 and its *extra small* corner is
the second largest in it. `smoothing = 0f` turns the continuous corners off
across the whole scale in the same call, which is the only place it can be done
consistently — see below.

`kontourShapes()` with no arguments is exactly `Shapes()`, asserted on every
build. A brand that disagrees about which rung a *pressable* thing lands on is
replacing a mapping rather than adjusting a scale, and says so with `copy`:

```kotlin
kontourShapes(extraSmall = 6.dp, step = 2.dp, capsuleCap = 10.dp)
    .let { it.copy(control = it.small, field = it.small) }
```

**One step, all the way up.** Every rung is 6dp above the one below it, and that
regularity is the point rather than tidiness. Two rounded shapes nested inside
one another look right when the inner radius is the outer radius minus the gap
between them, and wrong otherwise — the corners stop being parallel and the gap
pinches. That only works if the scale steps evenly.

**And the numbers are chosen, not inherited.** `22` is the medium rung because
the medium control height is 44dp, so a medium button's corner is exactly half
its height and the button is a capsule. The ladder is built around that number
rather than the other way round, which is what lets a card sit beside a button
and read as the same family.

**Every rung is a squircle** — curvature eased in and out rather than a quarter
circle bolted between two straight edges. The two small rungs used to be circular
on the grounds that the smoothing is invisible below about 12dp and a generic
path costs more to clip. Both true, and still the wrong trade: a scale whose
continuity stops halfway up is a discontinuity in the *scale*, and a badge with a
corner from a different design system to the card it sits on is more visible than
the thing that was being avoided.

Do not step through the scale by eye. `Theme.shapes.medium.inset(6.dp)` gives the
radius something 6dp inside a `medium` container should use, floors at zero, and
keeps the kind of corner it was called on.

**`inset` and `outset` both resolve against the box the shape they are given is
drawn on, not against the box they are handed.** For a fixed rung the two are the
same and nothing shows. For a *proportional* corner — a capsule, a pill — they
are not, because such a corner is smaller on a smaller box before any gap is
subtracted. `inset` used to skip that reconstruction and take the gap twice: a
segmented control's thumb came out at 10dp inside a 22dp track, six too square on
a six dp gap, which is why the thing inside looked like it came from a squarer
scale than the thing around it.

### Ask for what a thing *is*

Components do not pick a rung. They ask for one of four names, and that is why
two buttons cannot disagree — there is one place that says what a button's corner
is, and every button reads it.

| Token | Resolves to | For |
|---|---|---|
| `control` | half its height, up to 18dp | `Button`, `SplitButton`, `ButtonGroup`, `Chip`, `Tag`, `Toolbar`, `TabBarScope.Tab`, `Breadcrumbs`, `Pagination` |
| `field` | half its height, up to 18dp | `TextField`, `SearchField`, `Select`, `SegmentedControl`, `TimePicker` |
| `container` | `medium` | `Card`, `ListItem`, `SelectionRow`, `Accordion`, `SwipeActions`, `DropdownMenu`, `Popover`, `Tooltip`, `NavDrawer` |
| `panel` | `large` | `Dialog`, `CommandPalette`, `NavSearch` |

**A control is a pill up to `small`, and squarer above it.** Half its own height
is the thing a fixed radius cannot do: at 14dp an `XSmall` button was nearly a
pill already and an `XLarge` was nearly square, so one component disagreed with
itself across its own size scale. But that rule taken all the way up has the
opposite failure — a 60dp button at 30dp is not a considered radius, it is a
stadium. So it stops at **18dp**.

18 is not tuned. It is half `controlHeightSmall`, which is what makes this one
rule rather than two competing ones:

| Height | Corner | Reads as |
|---|---|---|
| 28dp (`XSmall`) | 14dp | a pill — half the height, under the cap |
| 36dp (`Small`) | 18dp | a pill *and* the cap, meeting exactly |
| 44dp (`Medium`) | 18dp | capped |
| 52dp (`Large`) | 18dp | capped |
| 60dp (`XLarge`) | 18dp | capped |

At `small` and below the corner is under the cap, so it is exactly half the
height and the control is a pill. At 36dp precisely the two readings agree, so
there is no step at the join. Above it the corner stops and each size reads a
little squarer than the last.

It also lands the ladder somewhere useful: **22 minus 18 is 4**, which is
`spacing.xxs`. A standard control inside a standard `container` with one unit of
padding round it is concentric by construction, without either of them naming a
radius — and `panel` at 28 holds a `container` at 22 with 6dp of ring the same
way.

**A field takes the same cap.** It used to be a fixed 14dp, on the argument that a
capsule reads as something to press rather than something to fill in. Half right:
a single-line field *is* a control by every other measure — same height, same row,
same press target — and giving it a different corner from the button beside it was
the inconsistency rather than the fix.

What that argument was really protecting is the multi-line case, and a text area
shaped like a lozenge is nobody's idea of a text area. That used to need a cap of
its own at 26dp; it now shares the one every height-derived corner takes, so a
field and the button beside it agree at every height rather than only below 52dp,
and the text area it was protecting is an 18dp box instead of a 26dp lozenge.

**A container that wraps controls can no longer share their token.** It used to
be able to: two uncapped capsules were concentric for free, because a child inset
by the padding top and bottom is shorter by exactly twice it, so the two radii
differed by exactly the padding whatever the numbers were. Once both sides hit
the cap they land on the same 18 with a gap between them, and a ring that is even
along the straight edges and closes to nothing at the corners is the pinch this
whole scale exists to avoid. `Toolbar` derives its corner from its children's
with `outset` now, and the `TabBar` indicator derives its from its tab's with
`inset`.

Reaching past these four to a rung of the size scale is for genuine one-offs — an
avatar, a scrollbar, a skeleton line, a drag handle — where the shape belongs to
that one thing rather than to a family. A component that reaches for `small`
because it is the right number today stops tracking the family it belongs to, and
that is exactly how a design system drifts.

They are also the seam a consumer wants. Overriding `pill` to square off buttons
would not even reach them — a button reads `control` — and it would square off
the avatars and the scrollbar instead; overriding `control` moves the buttons and
nothing else.

### Nesting one shape inside another

Two rounded rectangles are concentric when **the inner radius is the outer
radius minus the space between them**. Get it wrong and the ring visibly widens
or pinches around the corner even though it is even along every straight edge —
which is the one thing about a nested shape people notice without being able to
say what they are looking at.

At the standard sizes this now falls out of the scale on its own: `container` is
22, a control caps at 18, and 22 − 18 is 4, which is `spacing.xxs`. So a button
in a card with one unit of padding round it is already concentric, and so is a
`container` in a `panel` with `spacing.xs`.

The moment you change the padding, it stops being free. `inset` and `outset` are
the two directions of the arithmetic, and both defer: a proportional corner has
no value until there is a box to take it of, so they resolve against the box the
*base* is drawn on rather than the one they are handed.

```kotlin
// A thumb 6dp inside its track.
val track = Theme.shapes.field
val thumb = track.inset(6.dp)

// A bar wrapped 6dp around its buttons.
val bar = Theme.shapes.control.outset(6.dp)
```

**`Modifier.concentric()` does it without you naming either number.** A
container publishes its corner and its ring; anything inside can ask for the
shape that matches.

```kotlin
Card {
    // Takes a shape: read it.
    Button(onClick = {}, shape = Theme.shapes.concentric()) { Text("Save") }

    // Clips its own background: use the modifier.
    Box(Modifier.fillMaxWidth().height(120.dp).concentric().background(cover))
}
```

It is **opt-in**, and stays out of the way when it cannot help:

- Outside any container `Theme.shapes.concentric()` is the component's normal
  default and `Modifier.concentric()` adds nothing at all — so the same call
  site works wherever it ends up.
- It **nests**: a card inside a dialog publishes the card, so a button two
  levels down measures against the thing actually around it.
- It does not fight an explicit `shape`, because nothing is automatic. A call
  site passes this or passes something else.
- It **declines rather than guesses**. A container whose shape is a path has no
  radius to subtract from, and one with 16dp at the sides and 8dp top and bottom
  has no single inner radius that keeps the ring even — the ring is genuinely
  uneven and no corner fixes it. Both publish nothing and the fallback stands.

`Card` and `Toolbar` publish for you. For a container of your own, call
`ProvideConcentric(shape, contentPadding) { … }` around its content. `Dialog` and
`DropdownMenu` do not publish, because neither takes a content padding to derive
from and inventing one would be guessing at a number their callers own.

### Two kinds of corner

From `medium` up the corners are **squircles** — curvature eased in and out
rather than a quarter circle bolted between two straight edges. Both corners
share the same arc, so at forty-five degrees they are the same point; what
differs is that a squircle starts bending at `1.6 × radius` from the corner and
arrives gradually, where an arc holds the straight edge until `radius` and then
turns all at once. That earlier, gentler departure is the whole of the effect,
and it is why a large surface reads as drawn rather than clipped.

It is not free: a squircle is a generic path to clip, to border and to shadow.
Every rung pays it, the two small ones included — see above for why a scale that
stops being continuous partway up is worse than the cost it saves.

**A capsule is a squircle too, and for a long time it silently was not.**
`control` used to be half the shorter side on every control, so on any button,
chip, tag, toolbar or tab the two corners at one end met in the middle of that
end with nothing between them: the short edge was *saturated*, exactly and
always. (The cap means that is now only true at `small` and below — above it
there is straight edge at both ends. The fix below is what makes both cases come
out right.) Smoothing needs room
past the radius to put its blend in, and the rule used to take the tighter of a
corner's two edges and apply it to both — so one full edge dropped the smoothing
on the other, and every control in the library drew a plain circular arc while
naming a squircle and paying a generic path for it.

Each edge is asked separately now. The end keeps its full arc where it meets its
neighbour and eases into the long edge where there is room, so a control is
exactly as round at its ends as it was and no longer steps from arc to straight
line. Measured against a plain arc, a 200×52 button deviates by up to 1.32px at
its capped 18dp — the same order as a `Card`, which is not saturated and has
always smoothed freely.

Squaring the family off did not flatten it, which is worth a number because the
intuition runs the other way. The uncapped 26dp capsule deviated by 1.9px and the
capped 18dp one deviates by 1.32 — but as a *fraction of the radius* those agree
to three decimal places. A control is exactly as much of a squircle as it was, at
a smaller radius.

The exceptions fall out of the same rule rather than a list. A square box at
capsule radius is saturated on *both* edges, so it has nothing to ease onto in
either direction and stays a true circle: an `IconButton`, an `Avatar`, a status
dot, the ring round a `RadioButton`, a colour swatch, a day cell. `pill` remains
for those, and `capsule` — the squircle of the same silhouette — is what a
lozenge asks for: a chip, a toast, a nav indicator, a skeleton line.

Those components **name** `pill` rather than inheriting it from `control`, and
that is a deliberate change: on a square box the two draw the same picture, so
the name buys nothing you can see today. What it buys is an exemption from the
cap below. A capped `capsule` on a 50dp box is an 18dp rounded square; a `pill`
on the same box is still a circle. A day cell is the case that makes it
concrete — its fill, its "today" ring and its range caps only agree with each
other if none of them is capped.

**What is still drawn as a plain rounded rect, and why.** Seventeen places paint
a corner with `drawRoundRect` rather than clipping to a shape, and a
`CornerRadius` on a `RoundRect` cannot carry smoothing at all. Two reasons, both
measurable:

- Fifteen of them are **3–8dp in the short dimension** — a progress track, a
  slider track, the `Callout` rule, a page-indicator dot. The blend scales with
  the radius, so where an 18dp button deviates from a plain arc by 1.32px a 4dp
  track deviates by 0.29px. Below half a pixel there is nothing to see and a
  generic path to pay for.
- The other two are the **slider thumb and the switch thumb**, which change size
  on every frame of a gesture — the thumb stretches to 1.25× and leans toward the
  finger. A shape caches its path on the size it was last built at, so an element
  whose size is different every frame misses that cache every frame, and building
  one is four corners of trigonometry and twelve cubic segments. Sixty times a
  second, under a finger, is the one place in this library where the generic path
  is the wrong trade.

`sheet` and `sideSheet` are `extraLarge` with two corners squared off, derived
rather than restated — a panel against the edge of the window should be square
where it meets that edge, and the same radius as a hero panel everywhere else.

---

## Elevation

Shadows are described the way CSS describes them — colour, offset, blur, spread
— not as an Android elevation in dp, because a single dp number cannot express a
two-layer shadow and Android's elevation model does not exist on the other four
targets.

| Token | For |
|---|---|
| `flat` | Flush with the page. Most content |
| `low` | Cards and list groups at rest |
| `medium` | Nav bars, raised cards |
| `high` | Menus, popovers, tooltips |
| `overlay` | Dialogs and sheets |

Each level stacks two `ShadowSpec` layers: a tight, darker *contact* shadow that
gives the shape a crisp edge against a busy background, and a wide, soft
*ambient* layer that carries height. One layer alone gives you either a hard
edge or a vague smudge, never both.

Alphas roughly double in dark mode. A soft black shadow on a near-black ground is
invisible, which is why dark themes so often look flat.

The top two tiers are lighter than they were. `overlay` used to be a 20dp offset
with a 50dp blur at 22%, which bled about 70dp past a dialog's edge and was doing
the whole job of separating it from the page. That job is now shared — what is
behind a modal is [blurred as well as dimmed](overlays.md#the-backdrop) — and two
mechanisms both pushed to their limit read as one heavy-handed one.

Elevation is a **rank**, not a decoration: pick the level that matches where the
element sits in the overlay stack.

---

## Motion

| Token | | For |
|---|---|---|
| `instant` | 0ms | No transition |
| `fast` | 150ms | State changes within a control |
| `default` | 220ms | Most transitions |
| `slow` | 400ms | Large surfaces entering or leaving |
| `deliberate` | 600ms | Scroll reveals; anything meant to be noticed |

Easings: `standard` is `cubic-bezier(0.16, 1, 0.30, 1)` — the marketing site's
verbatim, so a transition in the app and the same transition on the web feel like
one product. Plus `enter`, `exit` and `emphasized` (which overshoots).

Springs are `SpringToken(dampingRatio, stiffness)`: `springSnappy` for toggles
and thumbs, `springDefault` for size and position, `springGentle` for sheets,
and `springBouncy` — deliberately under-damped, the system's one bit of play.

**On `springBouncy`.** Use it on the *return* leg of an interaction: a button
springing back after a press, a chevron settling after a flip, a chip popping
in. Never on the way *down* into a press. Overshoot on the outbound leg reads as
mushy and slow; overshoot on the way back reads as alive. That asymmetry is the
whole trick, and `KontourIndication` applies it.

**Two things about that were untrue for a long time, so they are worth stating
plainly.** The indication has to sit *ahead of* the container in the modifier
chain — a draw modifier only draws what comes after it, so an indication handed
to `clickable`, which is past `.background()`, scaled the label inside a button
whose silhouette never moved. And ghost variants opted out of the scale
altogether, which is right for a ghost *text* button (a shrinking label reads as
the text jumping) and wrong for the icon buttons that make up most of the
library. Both are fixed, and the check measures the ink rather than trusting the
claim.

**How far it moves** now follows the control's size, because one constant
serving a 28dp icon button and a full-width one is invisible on the first and a
collapse on the second: 7% at XSmall and Small, 5% at Medium, 3% at Large and
XLarge. `ButtonDefaults.pressScale` owns it. A control with a moving indicator —
a tab, a nav destination, a segment — deliberately keeps `pressScale = 1f`,
because the indicator travelling to what you pressed is already the answer and a
second one fights it.

It is suppressed entirely under reduced motion. An unrequested overshoot is
exactly the kind of movement that preference exists to stop.

**Reduced motion** does not mean *no* animation. A cross-fade is not what causes
vestibular discomfort — large translation, parallax and spinning are. When
`Theme.motion.reduceMotion` is set:

- durations collapse toward `fast`
- transition presets swap movement for opacity
- springs degrade to tweens, so nothing overshoots or bounces
- `KontourIndication` drops the press-shrink and keeps the tonal wash
- continuous looping motion — marquee, indeterminate spinners — stops

Which OS setting turns it on is in
[`accessibility.md`](accessibility.md#reduced-motion).

Use the helpers rather than reading durations directly — they already account
for the preference:

```kotlin
animateFloatAsState(target, Theme.motion.tweenDefault())
animateDpAsState(target, Theme.motion.springOrTween())
```

---

## Sizing

| Token | | |
|---|---|---|
| `minTouchTarget` | The platform minimum, narrowing when the input is a mouse | [Touch targets](accessibility.md#touch-targets) has the four numbers and their sources |
| `iconSmall` / `iconMedium` / `iconLarge` | 16 / 20 / 24dp | Three sizes; anything else needs justifying |
| `controlHeight*` | 28 / 36 / 44 / 52 / 60dp | Shared by buttons, inputs and selects so mixed rows align |
| `borderWidth` / `borderWidthStrong` | 1 / 2dp | |
| `focusRingWidth` / `focusRingOffset` | 2 / 2dp | 2dp is the thinnest that stays visible at 200% zoom |
