# `Kbd`

A keyboard shortcut rendered as a key. Used by `MenuScope.item(shortcut = …)`
and available directly.

<!--sample:KbdBasics-->
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
    Kbd { +"⌘" }
    Kbd { +"K" }
    Text("opens the command palette", style = Theme.typography.bodySmall)
}
```

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
