# Tokens

Everything a component is allowed to look like. Read through `Theme` inside a
composable:

```kotlin
Theme.colors.surface
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

Defined in [`theme/ColorScheme.kt`](../../ui/src/commonMain/kotlin/io/kontour/ui/theme/ColorScheme.kt);
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
| `accent` | a `StatusColors`, below | | The brand, as a tone |
| `brand` | `#BB86FC` | `#BB86FC` | Purple as **decoration only** |
| `focusRing` | `#6D28D9` | `#BB86FC` | The keyboard focus indicator |

**`accent` is a tone, not four loose fields.** It is a `StatusColors` exactly
like `success` and the rest, so there is one tone type and six tones:

| Member | Light | Dark | Was |
|---|---|---|---|
| `accent.solid` | `#6D28D9` | `#BB86FC` | `accent` |
| `accent.onSolid` | `#FFFFFF` | `#1A1024` | `onAccent` |
| `accent.container` | `#F3ECFE` | `#2A1F3D` | `accentContainer` |
| `accent.onContainer` | `#4C1D95` | `#D9BBFD` | `onAccentContainer` |
| `accent.border` | `#DCC9FB` | `#453259` | *new* |

The point is not tidiness. A component that takes a tone can now take *this*
one, which is what `ButtonVariant.Accent`, `BannerTone.Accent` and
`ToastTone.Accent` are made of — and `TagTone.Accent`, which already existed,
had to reach past the group and assemble itself from three separate fields.

It also means a custom scheme has to supply the whole tone rather than one
colour. That is the point too: the old `lightColorScheme(accent = Color(...))`
left `accentContainer` at the default purple, so a blue accent came with a
purple selected-state and the signature said nothing about it.

**On `brand` versus `accent`.** `#BB86FC` is the Kontour purple, and on white it
reaches only 2.1:1 — it cannot carry text, a fill label, or a focus ring in light
mode. (The marketing site's `.button.primary:hover` does exactly this today and
fails.) So the palette splits the job: `brand` is the literal brand colour for
decoration, and `accent.solid` is a purple dark enough to be read. `BrandIsDecorativeOnlyTest`
pins that contract so nobody quietly promotes `brand` to a text colour.

### Status

Each of `success`, `warning`, `danger` and `info` is a `StatusColors` with five
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

---

## Type

Outfit, shipped as five static instances cut from the upstream variable font, so
weights render identically on every target. SIL OFL; licence at
[`app/ui/licenses/Outfit-OFL.txt`](../../ui/licenses/Outfit-OFL.txt).

| Role | Large | Medium | Small | Weight | Line height |
|---|---|---|---|---|---|
| `display` | 48 | 40 | 32 | 800 | 1.10–1.20 |
| `headline` | 28 | 24 | 20 | 700 / 600 | 1.25–1.35 |
| `title` | 18 | 16 | 14 | 600 | 1.40 |
| `body` | 17 | 15 | 13 | 400 | 1.60 / 1.50 |
| `label` | 16 | 14 | 12 | 600 | 1.20 |

Plus `monoLabel` — 13sp, weight 700, `+0.14em` tracking, meant to be set in
upper case. It is the eyebrow above a section heading (`.mono-label` on the
marketing site).

Sizes are in `sp` and scale with the OS text-size setting. Every style trims
half-leading at the top and bottom of a block, so visual bounds match layout
bounds and vertical centring behaves.

Which one to reach for:

- **display** — hero moments. At most one per screen.
- **headline** — screen and section titles.
- **title** — card headers, list headlines, dialog titles.
- **body** — everything the user actually reads.
- **label** — text inside a control: buttons, chips, tabs, form labels.

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
| `pill` | 50% | Avatars, scrollbars, indicators |
| `sheet` | 34dp top only | Bottom sheets |
| `sideSheet` | 34dp leading only | Side sheets |

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

### Ask for what a thing *is*

Components do not pick a rung. They ask for one of four names, and that is why
two buttons cannot disagree — there is one place that says what a button's corner
is, and every button reads it.

| Token | Resolves to | For |
|---|---|---|
| `control` | half its height | `Button`, `IconButton`, `SplitButton`, `ButtonGroup`, `FloatingActionButton`, `FabMenu`, `Chip`, `Tag`, `Toolbar`, `TabBarScope.Tab`, `Breadcrumbs`, `Pagination` |
| `field` | half its height, up to 26dp | `TextField`, `SearchField`, `Select`, `SegmentedControl`, `TimePicker` |
| `container` | `medium` | `Card`, `ListItem`, `SelectionRow`, `Accordion`, `SwipeActions`, `DropdownMenu`, `Popover`, `Tooltip`, `NavDrawer` |
| `panel` | `large` | `Dialog`, `CommandPalette`, `NavSearch` |

**A control is a capsule at every height**, which is the thing a fixed radius
cannot do: at 14dp an `XSmall` button was nearly a pill already and an `XLarge`
was nearly square, so one component disagreed with itself across its own size
scale. And a `Button` sat at 14dp next to a circular `IconButton` in the same
toolbar. Now every action is the same shape whatever size it happens to be.

**A field is a capsule too, up to a point.** It used to be a fixed 14dp, on the
argument that a capsule reads as something to press rather than something to fill
in. Half right: a single-line field *is* a control by every other measure — same
height, same row, same press target — and giving it a different corner from the
button beside it was the inconsistency rather than the fix.

What that argument was really protecting is the multi-line case, and a text area
shaped like a lozenge is nobody's idea of a text area. So the rule is capped at
26dp — half the height a text field's `minHeight` resolves to — which puts the
default single-line field exactly on a capsule and stops everything taller right
there.

Reaching past these four to a rung of the size scale is for genuine one-offs — an
avatar, a scrollbar, a skeleton line, a drag handle — where the shape belongs to
that one thing rather than to a family. A component that reaches for `small`
because it is the right number today stops tracking the family it belongs to, and
that is exactly how a design system drifts.

They are also the seam a consumer wants. Overriding `pill` to square off buttons
would square off the avatars and the scrollbar too; overriding `control` moves
the buttons and nothing else.

### Two kinds of corner

From `medium` up the corners are **squircles** — curvature eased in and out
rather than a quarter circle bolted between two straight edges. Both corners
share the same arc, so at forty-five degrees they are the same point; what
differs is that a squircle starts bending at `1.6 × radius` from the corner and
arrives gradually, where an arc holds the straight edge until `radius` and then
turns all at once. That earlier, gentler departure is the whole of the effect,
and it is why a large surface reads as drawn rather than clipped.

It is not free: a squircle is a generic path to clip, to border and to shadow.
Below about 12dp the smoothing is invisible, so `extraSmall` and `small` stay
circular and pay nothing. `pill` is a true capsule, where the corner is a
semicircle and there is no curvature discontinuity to remove in the first place.

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
library. Both are fixed; `PressScaleTest` measures the ink rather than trusting
the claim.

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
