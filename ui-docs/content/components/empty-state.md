# `EmptyState` / `ErrorState`

**`EmptyState` is not `ErrorState`.** Empty means the request succeeded and there
is genuinely nothing, often by the user's own doing, and it needs no apology —
showing an error face for an empty list makes people think they broke something.

<!--sample:EmptyStateBasics-->
```kotlin
// The action is the part that matters: an empty screen that only says it is
// empty leaves the reader where they already were.
EmptyState(Modifier.fillMaxWidth()) {
    +"No favourites yet"
    supporting { +"Star a stop or route and it will appear here." }
    leading { +Tabler.Outline.Star }
    action {
        Button(onClick = { nearby() }, variant = ButtonVariant.Secondary) { +"Browse routes" }
    }
}
```

The message should say how to *leave* the empty state, not restate the title.

---

## Accessibility

Both states are **live regions** — `EmptyState` polite, `ErrorState` assertive —
and merge their content, so the whole block is announced as one thing when it
appears rather than as an icon, a heading and a button in sequence.

That is why the `action` slot matters more here than anywhere else: an empty
screen that only says it is empty leaves a screen-reader user exactly where they
were, with nothing to move to. Give it somewhere to go.

The `leading` icon is decorative and cleared. Whatever it depicts should be in
the words.

---

← [Display and content](display.md) · [All components](../components.md)
