# `TextSelectionToolbar`

*Also on this page: `TextToolbarAction`, `textToolbarLabels`.*

Adds items to the toolbar shown when the user selects text.

<!--sample:TextToolbarBasics-->
```kotlin
// Wrap the app once, with the items this app wants on a text selection.
// With no actions this does nothing at all and every platform keeps its own
// toolbar — which is the right default, and why the list is what you pass.
TextSelectionToolbar(
    actions = listOf(
        TextToolbarAction("Plan a trip") { /* open the planner */ },
    ),
) {
    Screen()
}
```

## With no actions it does nothing at all, and that is the point

This used to replace the platform's selection popup with four buttons of our own
on desktop and web, on the grounds that Compose's default there is unstyled.
That was the wrong trade.

A selection toolbar is a **system** surface. On Android it carries "Look Up",
"Translate", "Share", the user's keyboard extensions and their configured text
replacements; on iOS the same plus the writing tools; on desktop it is what every
other application on that machine shows. Drawing our own removes functionality
the user expects and knows how to reach, in exchange for matching a design system
they did not ask the toolbar to match.

So `actions` empty — the default — installs nothing, and every platform shows its
own toolbar exactly as it would without this library.

## With actions it has to draw one, because no platform lets us add to theirs

`TextToolbar` is the whole of what Compose exposes in common code: a rectangle
and one nullable callback per built-in verb. There is no common way to append an
item to the platform's menu — Android would need an `ActionMode` with a custom
menu resource, iOS a `UIMenuController` we have no handle on, and web has no
system toolbar to append to.

An app that genuinely needs "Plan a trip" on its selection menu therefore has to
trade the system surface for one it controls. This makes that trade explicit
rather than making it for you: pass actions and you get a drawn toolbar carrying
the built-in verbs the framework offered *plus* yours, with `textToolbarLabels`
supplying the four verbs so an app that has localised the rest of
`Theme.strings` localises these too.

A verb the framework did not offer is **absent rather than disabled**. A
greyed-out "Paste" on an empty clipboard tells the user nothing they can act on,
and four permanent buttons make the two that apply harder to hit.

## What an action cannot have

The selected text. Compose hands `showMenu` a rectangle and four callbacks and
nothing else, so an action here fires against whatever the app already knows — a
screen's current field, a view model — rather than against a string passed in.
Anything that needs the text itself belongs on that screen, where the selection
lives.

---

## Accessibility

Passing no actions is the accessible default, and it is the default: the
platform's own toolbar carries the user's assistive settings, their configured
text replacements and the system actions a screen reader announces.

When you do pass actions, the drawn toolbar is built from `Button`, so each item
is a real focus target with a real touch target and the ring every other control
in the library has. Keep the list short — it sits between the user and the text
they just selected, and every item added is one more to read past.
