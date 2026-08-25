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

← [Display and content](display.md) · [All components](../components.md)
