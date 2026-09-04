# `TextSelectionToolbar`

*Also on this page: `TextToolbarAction`, `textToolbarLabels`, `TextToolbarDefaults`.*

The toolbar shown when the user selects text.

<!--sample:TextToolbarBasics-->
```kotlin
// Wrap the app once, with the items this app wants on a text selection.
// With no actions, Android and iOS keep their own toolbar and this installs
// nothing; desktop and the web have none to keep, so it draws its own.
TextSelectionToolbar(
    actions = listOf(
        TextToolbarAction("Plan a trip") { /* open the planner */ },
    ),
) {
    Screen()
}
```

## It defers to the platform where there is a platform to defer to

A selection toolbar is a **system** surface on a phone. On Android it carries
"Look Up", "Translate", "Share", the user's keyboard extensions and their
configured text replacements; on iOS the same plus the writing tools. Drawing
our own there removes functionality the user expects and knows how to reach, in
exchange for matching a design system they did not ask the toolbar to match.

So on Android and iOS, with no actions to add, this installs nothing at all and
the system's own menu comes up exactly as it would without this library.

**Desktop and the web have no such surface.** Compose falls back to a bare
unstyled popup on the desktop and to nothing recognisable in a browser, so
leaving the platform alone there leaves the user with *less* rather than more —
the opposite of the reason for deferring. On those platforms this draws its own.

One rule read two ways: show the richest selection toolbar available, which is
the system's where there is one and this one where there is not.

## With actions it draws one everywhere, because no platform lets us add to theirs

`TextToolbar` is the whole of what Compose exposes in common code: a rectangle
and one nullable callback per built-in verb. There is no common way to append an
item to the platform's menu — Android would need an `ActionMode` with a custom
menu resource, and iOS a `UIMenuController` we have no handle on.

An app that genuinely needs "Plan a trip" on its selection menu therefore has to
trade the system surface for one it controls. This makes that trade explicit
rather than making it for you: pass actions and you get a drawn toolbar carrying
the built-in verbs the framework offered *plus* yours, with `textToolbarLabels`
supplying the words so an app that has localised the rest of `Theme.strings`
localises these too.

A verb the framework did not offer is **absent rather than disabled**. A
greyed-out "Paste" on an empty clipboard tells the user nothing they can act on,
and four permanent buttons make the two that apply harder to hit.

## Past four items, the rest go in a menu

A toolbar wider than the selection it points at has stopped pointing at
anything. `TextToolbarDefaults.MaxInline` is four — what fits across a phone at
the largest type size, and what every platform's own toolbar shows before its
own overflow — and the remainder go behind "More" as a
[`DropdownMenu`](dropdown-menu.md).

The overflow control costs a slot of its own, so a list one item over the limit
puts *two* into the menu rather than one. Otherwise adding the button to fit the
fifth item is what pushes it back out again.

## It is a `Toolbar`

The drawn bar is the library's own [`Toolbar`](toolbar.md) — "a floating surface
holding actions, over content it does not belong to", which is a selection
toolbar exactly. It used to be a row of ghost buttons inside a menu panel: a
menu doing a toolbar's job, with a menu's shape and a menu's padding and a
second set of numbers to keep in step with the first.

## What an action cannot have

The selected text. Compose hands `showMenu` a rectangle and four callbacks and
nothing else, so an action here fires against whatever the app already knows — a
screen's current field, a view model — rather than against a string passed in.
Anything that needs the text itself belongs on that screen, where the selection
lives.

---

## Accessibility

On a phone, passing no actions is the accessible default and it is the default:
the system's own toolbar carries the user's assistive settings, their configured
text replacements and the system actions a screen reader announces.

The drawn toolbar is built from `Button`, so each item is a real focus target
with a real touch target and the ring every other control in the library has,
and the bar is one traversal group rather than a scatter of buttons.

Keep the list short. It sits between the user and the text they just selected,
and every item added is one more to read past — which is also why the fifth and
beyond move into a menu rather than making the bar wider.
