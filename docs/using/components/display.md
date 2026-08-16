# Display and content

Things that show rather than take input.

| | For | Instead of |
|---|---|---|
| [`Card`](#card) | A bounded block of related content | A `Surface`, when there is no grouping to express |
| [`Tag`](#tag) | A status label, or a colour out of a feed | A `Chip`, which is interactive |
| [`Badge`](#badge--badgedbox) | A count or a dot over something | — |
| [`Avatar`](#avatar--avatargroup) | A person or a place | — |
| [`LinearProgress`](#progress) | A known fraction | `Spinner`, when you do not know it |
| [`ProgressRing`](#progress) | A known fraction, in a small space | — |
| [`StepProgress`](#progress) | A known number of steps | `LinearProgress`, for a continuous fraction |
| [`Skeleton`](#skeletons) | The shape of content that is loading | `Spinner`, when the shape is knowable |
| [`EmptyState`](#emptystate--errorstate) | Nothing here, and that is fine | `ErrorState` — see below |
| [`ErrorState`](#emptystate--errorstate) | Something went wrong | `EmptyState` — see below |
| [`Banner`](#banner--animatedbanner) | A message about the screen you are on | `Toast`, for something you just did |
| [`Callout`](#callout) | The markdown blockquote treatment | `Banner`, for anything dismissible |
| [`Timeline`](#timeline) | A vertical sequence — the itinerary | A plain list, when there is no progression |
| [`Accordion`](#accordion) | Disclosure, with hoisted state | — |
| [`Stat`](#stat) | One figure, said loudly | `KeyValueList`, when none of them is the headline |
| [`KeyValueList`](#keyvaluelist) | Label-and-value facts about one thing | `SettingRow`, only if the rows are tappable |
| [`Kbd`](#kbd) | A keyboard shortcut, rendered as a key | — |
| [`RelativeTimeText`](date-time.md#relativetimetext) | A self-updating "in 4 min" | — |

---

## `Card`

`Elevated`, `Outlined` or `Filled`, optionally clickable as a whole.

`Elevated` and `Filled` take a `contrastEdge()` at the high-contrast tier. An
elevated card is white on white with a shadow for an edge, and a shadow does not
change between tiers — `surfaceSunken` on `background` measures **1.14:1** at
the high-contrast light tier, against the 3:1 WCAG 1.4.11 asks of a control's
boundary.

## `Tag`

**Takes an arbitrary background and derives its own text colour.** Transit feeds
supply route colours that are not drawn from any palette — a route can be pale
yellow or near-black. Passing `color` resolves the label with
`contentColorFor()`, so it stays legible.

That is the whole reason the component exists rather than callers styling a
`Surface` themselves: the case they would get wrong is the one where the feed
hands them a colour nobody designed for.

**Reach for a [`Chip`](selection.md#chip-filterchip-inputchip) instead** when the
thing is pressable. A tag is a label; a chip is a control.

## `Badge` / `BadgedBox`

A count or a dot, positioned over what it annotates. `BadgedBox` places it;
`Badge` is the mark itself.

The navigation surfaces do **not** use `BadgedBox` — they place the badge
against the glyph rather than the touch target, because a 48dp circle around a
24dp icon would otherwise leave the dot floating in empty space.

## `Avatar` / `AvatarGroup`

Image, initials or icon.

**Colours are derived from the name**, so the same person is the same colour
everywhere without anyone storing one. The derivation deliberately avoids
`hashCode()` — Kotlin's String hash is not guaranteed identical across
Kotlin/Native and Kotlin/JS, so the same person could be a different colour on
iOS than on Android.

---

## Progress

| | |
|---|---|
| `LinearProgress` | Determinate or indeterminate |
| `ProgressRing` | Circular, determinate |
| `StepProgress` | Segmented, for a known number of steps |
| [`Spinner`](actions.md#spinner) | Indeterminate activity |

Prefer a determinate one wherever the fraction is known. People wait longer when
they can see the end.

## Skeletons

`Skeleton`, `SkeletonText` and `SkeletonListItem` — placeholders in the shape of
what is coming.

**They are hidden from the accessibility tree.** There is nothing to announce,
and a screen reader walking a dozen unlabelled boxes is noise — the container
carries the loading announcement instead. The shimmer stops under reduced
motion.

**Reach for a skeleton over a spinner** when you know the shape of what is
loading. A list of five rows that appears as five grey rows does not reflow when
it arrives.

## `EmptyState` / `ErrorState`

**`EmptyState` is not `ErrorState`.** Empty means the request succeeded and there
is genuinely nothing, often by the user's own doing, and it needs no apology —
showing an error face for an empty list makes people think they broke something.

The message should say how to *leave* the empty state, not restate the title.

---

## `Banner` / `AnimatedBanner`

![AnimatedBanner](../../../../../app/ui-catalog/screenshots/components/animatedbanner-light.png)

An inline message, four severities. `AnimatedBanner` is the same thing that
animates its own appearance and dismissal, for a banner whose presence is driven
by state.

**`Banner` vs `Toast`.** A banner is about the screen you are on; a toast is
about something you just did. A banner that appears in response to a tap is easy
to miss, because the user is looking at their finger.

`Danger` banners announce assertively and everything else politely —
interrupting for a routine notice trains people to ignore the interruption.

## `Callout`

The markdown blockquote treatment, for prose. Not dismissible and not a status —
if it can go away, it is a `Banner`.

## `Timeline`

`Timeline` and `TimelineItem` — a vertical sequence, which in this app is the
journey itinerary.

**The connector is drawn to the full height of its row**, using
`IntrinsicSize.Min`. A fixed-height connector leaves gaps against tall rows and
overshoots short ones, which is what makes most hand-rolled timelines look
assembled rather than built.

## `Accordion`

![Accordion](../../../../../app/ui-catalog/screenshots/components/accordion-light.png)

Disclosure with hoisted state, so the caller decides what is open — including
opening the section containing whatever the user searched for.

## `Stat`

![Stat](../../../../../app/ui-catalog/screenshots/components/stat-light.png)

```kotlin
Stat {
    value("4 min")
    +"Next departure"
    supporting("Platform 2")
    trend(StatTrend.Positive, "2 min earlier than usual")
}
```

The label goes **under** the value. A dashboard is scanned by its numbers — the
reader finds the big thing first and then asks what it is, and a label on top
makes them read every caption to find the figure they came for.

**Spoken, that order is backwards.** So the block merges into one announcement
and reverses it: "Next departure. 4 min. Platform 2" — the label first, because
"4 min" before anything has said what is four minutes away is a number with no
subject. Pass `announcement(…)` for a figure a reader would mangle: "1.2k" is
said as "one point two kay".

`StatTrend` is `Positive`, `Negative` or `Neutral` — **sentiment, not
direction**. A departure two minutes earlier is good news and points down; a
fare two dollars higher is bad news and points up. Nothing in the component can
tell which.

**Reach for a `KeyValueList` instead** when there are several figures and none is
the headline. A screen with six stats has no headline, which is the same as
having none.

## `KeyValueList`

![KeyValueList](../../../../../app/ui-catalog/screenshots/components/keyvaluelist-light.png)

```kotlin
KeyValueList {
    row("Operator", "Transperth")
    row("Platform", "2")
    row("Fare", "$3.20")
    row("Accessible", announcement = "yes") { +Tabler.Outline.Check }
}
```

**Not a [`SettingRow`](collections.md#settingrow).** They draw almost the same
row, and the difference is the whole reason both exist:

| | `KeyValueList` row | `SettingRow` |
|---|---|---|
| What it is | text | a control |
| Role | none | `Role.Button` |
| Touch target | none | yes |
| Pressing it | nothing | opens something |

Using a setting row for facts gives a screen-reader user a list of buttons that
do nothing, which is worse than the visual duplication it saves. If one row needs
to become tappable, it is not one of these — move it out and leave the rest.

**Each row announces as a pair**, "Platform, 2". Separate nodes make the reader
hold the label while waiting for the value, and the pairing is the entire
content. A row whose value is not text needs `announcement` — a tick icon in an
"Accessible" row otherwise announces as "Accessible" and stops, which reads as a
row with its value missing.

`labelWidth` is a fixed minimum rather than intrinsic, so the values line up down
the column. A ragged value column is what makes a details panel look untidy, and
an intrinsic width re-rags it every time the content changes.

## `Kbd`

![Kbd](../../../../../app/ui-catalog/screenshots/components/kbd-light.png)

A keyboard shortcut rendered as a key. Used by `MenuScope.item(shortcut = …)`
and available directly.

It has no role, no disabled state and no touch target, which is why it is in the
registry as a render-only specimen — see
[`building/testing.md`](../../building/testing.md#one-list-two-consumers).
