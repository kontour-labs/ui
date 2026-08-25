# `Icon`

A glyph, tinted to the surrounding content colour and sized from the theme.
Takes an `ImageVector` or a `Painter`.

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

← [Foundation](foundation.md) · [All components](../components.md)
