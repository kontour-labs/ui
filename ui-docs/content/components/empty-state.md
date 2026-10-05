# `EmptyState` / `ErrorState` / `LoadingState`

*Also on this page: `StateSwitcher`.*

**`EmptyState` is not `ErrorState`.** Empty means the request succeeded and there
is genuinely nothing, often by the user's own doing, and it needs no apology —
showing an error face for an empty list makes people think they broke something.

<!--sample:EmptyStateBasics-->
```kotlin
// The action is the part that matters: an empty screen that only says it is
// empty leaves the reader where they already were.
EmptyState(Modifier.fillMaxWidth()) {
    +"No favourites yet"
    message { +"Star a stop or route and it will appear here." }
    leading { +Tabler.Outline.Star }
    action {
        Button(onClick = { nearby() }, variant = ButtonVariant.Secondary) { +"Browse routes" }
    }
}
```

The message should say how to *leave* the empty state, not restate the title.

### Loading, and the move between them

**`LoadingState` is the third**, drawn as the other two are, with a spinner where
their icon is: `LoadingState { +"Loading your favourites" }`. Without a title it
is announced as "Loading" (`loadingLabel`); with one, the title is what is read,
so it should say what is loading.

**`StateSwitcher` puts them in one place** and moves between them when its
`target` changes — loading, failed, empty and the content itself:

<!--sample:StateSwitcherBasics-->
```kotlin
var status by remember { mutableStateOf(Loaded.Loading) }

// One place for all four. The one leaving fades through the one arriving,
// and the space between them changes size smoothly rather than in a frame.
StateSwitcher(target = status, modifier = Modifier.fillMaxWidth()) { now ->
    when (now) {
        Loaded.Loading -> LoadingState { +"Loading your favourites" }
        Loaded.Failed -> ErrorState(onRetry = { status = Loaded.Loading }) {
            +"Couldn't load your favourites"
            message { +"Check your connection and try again." }
        }
        Loaded.Empty -> EmptyState { +"No favourites yet" }
        Loaded.Ready -> Column { stops.forEach { Text(it.name) } }
    }
}
```

A `when` on its own swaps one state for the next in a frame, and a loading state
a fast network replaces after 80ms flashes. Here the state leaving fades through
the one arriving, as content replaces content with no relationship between
them, and the space they take changes size smoothly. `target` is anything with
`equals`: an enum, a sealed class.

---

## Accessibility

All three states are **live regions** — `EmptyState` and `LoadingState` polite,
`ErrorState` assertive —
and merge their content, so the whole block is announced as one thing when it
appears rather than as an icon, a heading and a button in sequence.

That is why the `action` slot matters more here than anywhere else: an empty
screen that only says it is empty leaves a screen-reader user exactly where they
were, with nothing to move to. Give it somewhere to go.

The `leading` icon is decorative and cleared. Whatever it depicts should be in
the words. `LoadingState`'s spinner is the exception when it has no title: then
it is the only thing there, and it is announced as `loadingLabel`, "Loading".
With a title, the title says it and the spinner is quiet.

At high contrast all three states take the enhanced scheme's colours and draw no
line of their own. `ErrorState`'s retry is a secondary button, whose border
thickens with the tier, and `LoadingState`'s spinner is drawn as every spinner is
at that tier.

In a `StateSwitcher` the state leaving is cleared from the semantics while it
fades, so a screen reader hears the one arriving and not both.

None of the states animates its own arrival. A `StateSwitcher`'s change is a
cross-fade under reduced motion, without the slight scale, and its size is the
new one at once rather than travelling. `LoadingState`'s spinner keeps turning
and stops breathing, as every spinner does. A button in the `action` slot drops
its press-shrink, as every button does.
