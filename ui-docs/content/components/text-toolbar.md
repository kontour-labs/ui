# `KontourTextToolbar`

*Also on this page: `textToolbarLabels`.*

The cut / copy / paste / select-all toolbar that appears over selected text,
drawn by the library rather than by the platform.

<!--sample:TextToolbarBasics-->
```kotlin
// Wrap the app once. Cut, copy, paste and select-all are then drawn by the
// library rather than by the platform, so they look the same everywhere and
// read their labels from `Theme.strings`.
KontourTextToolbar {
    Screen()
}
```

Installed once, at the theme, so every text field in the app gets it. The
platform toolbars differ in shape, in colour and in what they call the actions,
and a design system that controls every other floating surface but not this one
has a hole in it exactly where a user is most likely to be looking.

`textToolbarLabels` is where the four verbs come from, so an app that has
localised the rest of `Theme.strings` localises this too.

---

## Accessibility

Install it unconditionally at the root, and it does the right thing per platform.

On desktop and web it replaces Compose's selection popup, which is otherwise a
bare unstyled row, with one drawn in the design system — real focus rings, real
touch targets, labels from `Theme.strings`.

On Android and iOS it is a **deliberate no-op**. The platform toolbar there is a
system surface carrying "Look Up", "Translate", "Share", the user's keyboard
extensions and their configured text replacements, none of which four buttons of
our own can reproduce. Replacing it would be taking capability away.

---

← [Text editing](text-editing.md) · [All components](../components.md)
