# `Icon`

A glyph, tinted to the surrounding content colour and sized from the theme.
Takes an `ImageVector` or a `Painter`.

<!--sample:IconBasics-->
```kotlin
// A decorative icon beside a label that already says the same thing takes
// `null`, so a screen reader announces the label once rather than twice.
Icon(Tabler.Outline.Star, contentDescription = null)

// One that carries the meaning on its own describes itself.
Icon(Tabler.Outline.Star, contentDescription = "Favourite", size = Theme.sizing.iconLarge)
```

**Components take icons from the caller.** A button's leading icon, a menu
item's icon, an empty state's illustration — all parameters, so the choice of
icon library stays an application decision. The exception is `SystemIcons`: the
handful of glyphs a component draws on its own behalf, because they are structure
rather than content. A submenu with no chevron gives no sign it opens anything.

`Icon` corrects for a non-square viewport, so an icon set whose glyphs are not
drawn on a uniform grid still renders undistorted — see
[the foundation page](foundation.md#icons) for why the library's own set is
Tabler and what `IconMetricsDiagnostic` asserts about it.

`contentDescription` is nullable and the default in the `+` vocabulary is
decorative: `+icon` inside a slot draws an icon with no name, because the slot
around it is already labelled. Use `icon(image, contentDescription)` when the
glyph is the only label there is.

---

## Accessibility

`contentDescription` is **required rather than nullable-by-default**, and the
choice between a string and `null` is the whole of what this component asks.

Pass `null` when the icon sits beside a label that already says the same thing —
otherwise a screen reader announces it twice. Pass a description when the icon
carries the meaning on its own, which for an icon-only button means the
description is the button's name.

`tint` defaults to `LocalContentColour`, so an icon inside a `Surface` is legible
against whatever that surface painted without anyone choosing a colour. Overriding
it with a literal is how an icon ends up invisible in the high-contrast scheme.
