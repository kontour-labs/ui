# `Kbd`

A keyboard shortcut rendered as a key. Used by `MenuScope.item(shortcut = …)`
and available directly.

<!--sample:KbdBasics-->
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
    // The icon rather than the "⌘" character. A character lands wherever
    // its font draws it inside the em — measurably a point high, for this
    // one — and an icon lands in the middle of the cap.
    Kbd { +KbdIcons.Command }
    Kbd { +"K" }
    Text("opens the command palette", style = Theme.typography.bodySmall)
}
```

**The modifier keys are available as icons**, and for ⌘ in particular that is
worth taking. A character lands wherever the font that supplies it draws it
inside the em — measured against its own 20dp cap, ⌘ sits a point high and ⇧ half
a point low, which is a point and a half of difference between two caps sitting
side by side. An icon's bounds are its drawing, so it lands in the middle.

`KbdIcons` covers nine: ⌘, ⇧, ⏎, ⌫, ⇥, ⇪, ⇞, ⇟ and ␣. **⎋ and ⌦ stay
characters** because there is no drawn counterpart for either under any name, and
a hand-made one would only be there to complete a table. **⌥ and ⌃ stay
characters for the opposite reason**: the bundled mono subset carries them, so
they already render in the library's own type.

Pick one style and keep to it inside a single shortcut: a stroke icon is lighter
than the type beside it, so mixing the two in one row shows. The demo above
flips between them, which is the only way to see the point and a half.

It has no role, no disabled state and no touch target, which is why it is in the
registry as a render-only specimen — see
[`building/testing.md`](../../../docs/building/testing.md#one-list-two-consumers).

---

## Accessibility

`Kbd` is presentational — it clears its own semantics, so a screen reader does
not spell out "command K" in the middle of a sentence.

That means a shortcut shown with it is **not announced**. Where the shortcut is
the only way to reach something, say so in the surrounding prose, and where it is
attached to a menu item use `item(shortcut = …)`, which puts it in the item's own
description rather than beside it.
