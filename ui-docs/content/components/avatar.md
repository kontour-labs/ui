# `Avatar` / `AvatarGroup`

Image, initials or icon.

<!--sample:AvatarBasics-->
```kotlin
// Initials from the name, so a missing photo is still a person rather than
// a grey circle. `image` wins when there is one.
Avatar(name = "Ada Lovelace", size = AvatarSize.Large)

// The overflow count is part of the accessible name, not a decoration —
// "+3" read out of context tells a screen-reader user nothing.
AvatarGroup(names = listOf("Ada Lovelace", "Grace Hopper", "Alan Turing", "Ken Thompson"))
```

**Colours are derived from the name**, so the same person is the same colour
everywhere without anyone storing one. The derivation deliberately avoids
`hashCode()` — Kotlin's String hash is not guaranteed identical across
Kotlin/Native and Kotlin/JS, so the same person could be a different colour on
iOS than on Android.

## Decorations

A status, a count or a glyph on the rim — asked for from a phone as "decorator
icons in corners":

```kotlin
Avatar(name = "Ada Lovelace") {
    status(Theme.colours.success.solid, "Online")
    count(3, at = AvatarCorner.TopEnd)
    icon(Tabler.Outline.Check, "Verified", at = AvatarCorner.BottomStart)
}
```

| Decoration | What it is | Default corner |
|---|---|---|
| `status(colour, description)` | A dot, a quarter of the avatar across, between 8dp and 16dp | Bottom end |
| `count(value, description, max = 99)` | A `Badge`'s count, "99+" past `max` | Top end |
| `icon(icon, description, containerColour)` | A glyph in a small disc, the accent by default | Bottom end |
| `custom(at, description) { }` | Anything, at its own size | — |

**Each sits on the circle's rim at 45°** in its `AvatarCorner`, not in the corner
of the square round the avatar, where a dot floats clear of a round picture. Each
is ringed in the colour of what the avatar is on — the surface it sits in, or
the page — so it reads as laid over the picture. They can overhang the avatar's
bounds by a few dp, as a `Badge` does; the avatar's size is unchanged. Start and
end corners mirror right to left.

---

## Accessibility

The announcement is `contentDescription` where you pass one and `name` otherwise,
set on the avatar itself with the initials inside cleared — so it is heard once,
as a person, rather than as two letters.

`name` is not a label drawn on screen; it is where the initials and the stable
colour come from. That is why it doubles as the announcement: an avatar with a
name has one whether or not anybody remembered `contentDescription`.

`AvatarGroup`'s overflow count is part of its description rather than a separate
node — "+3" read on its own tells a screen-reader user nothing.

**Decorations are said with the avatar**, after the name — "Ada Lovelace. Online.
3 new" — since a dot is drawn, not heard. A count with no `description` of its
own says what a `Badge` would. A decoration with a `null` description adds
nothing.

At high contrast an avatar with neither a name nor a picture, which is otherwise
a faint well on the page, takes an edge, and so does a group's overflow count. A
status dot and a glyph's disc take the edge too, and their ring thickens with the
tier.
The ring between a group's avatars is
drawn at `borderWidthStrong`, so it thickens with the tier; everything else is
the enhanced scheme's colours.

Nothing in an avatar moves, so reduced motion changes nothing.
