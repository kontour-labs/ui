# `SwipeActions` / `SwipeToDismiss`

Actions revealed by a sideways drag. `SwipeActions` reveals buttons;
`SwipeToDismiss` removes the row.

At rest it is a row and nothing else, so nothing about it announces that the
gesture exists. `rememberSwipeActionsState(initialValue = SwipeValue.End)` starts
a row already open, and `state.animateTo(SwipeValue.End)` moves an already-drawn
one — which is how you would hint at the gesture on first run.

**Order runs from the screen edge in toward the row**, on both sides. So the
*first* action of a list is the one furthest from the row, and the last is the
panel that appears against its edge as the swipe opens. The reason is the full
swipe: carrying a row all the way runs the first action of the side, and what a
full swipe looks like is that action growing from the edge until it has the whole
row. The other way up, the action a full swipe commits to was the one hard
against the row, and the one at the edge was the one it would never run.

All of that mirrors, and the mirroring is the framework's rather than the
component's: `anchoredDraggable` reverses a horizontal drag under RTL and
`Modifier.offset {}` mirrors the placement it is given, so the offset everything
here works in is a *logical* one — positive is toward the trailing edge in both
directions. Flipping it a second time by hand is what made a swiped row in Arabic
vacate a strip of bare page, with the anchors naming one side and the drawing
looking for the other.

The panels travel with the row rather than waiting at the edge of the screen for
it to arrive, so the set slides in behind it and the ground under the row is the
colour of the action it is about to reach. Pinned to the container instead, a
half-open swipe uncovered the container's edge first — so the panel on screen for
most of the gesture was the one furthest from the row, whatever colour the strip
behind it was.

An action shows its label only where there is room for one. A single-line row is
48dp and an icon above a label wants 59, so on short rows the icon stands alone —
the label still reaches the screen reader through the row's custom action either
way.

The action's colour is at full strength from the first pixel. It used to darken
while letting go would do nothing and lighten as the row crossed its threshold —
an answer to a real question, delivered as a colour change on a box that is also
sliding, growing and being tracked by a finger, which reads as a flicker partway
through the swipe. The answer arrives better as the tick below.

**One buzz, at the one moment that has a consequence.** `DragThreshold`, as the
row passes the point where letting go commits the full swipe. It used to be four
— a tick per action width uncovered, the threshold, a confirmation when the
action ran, and a settle when the row came back — and the report was a row that
"goes way too crazy", which is what a pattern reads as when it is not describing
anything. An `actionWidth` is not a detent: nothing snaps there and nothing rests
there.

The threshold the buzz marks is **derived from where the commit actually
happens** rather than set beside it. It used to be six tenths of the row's width,
a constant of its own, while the commit is decided by the drag settling onto the
committed anchor — so the two drifted apart as the action count and the row width
changed, and the buzz stopped meaning "past here, letting go deletes it".

**Three actions a side, and the fourth is refused.** One target is 88dp, so three
is 264dp of travel and already most of a phone's width; a fourth is a target
nobody can reach, and a row that silently hides its last action is worse than one
that says so.

**Revealing the actions asks for a deliberate distance.** A release carries on to
the next anchor from just past halfway between them, up from two fifths — which is
what made the reveal fiddly: a row showed its actions on a gesture that was half a
mind to.

**Running one asks for more than that.** A slow drag has to carry the row four
fifths of the way from the actions to the commit, and a firm flick is judged by
where the throw was aimed rather than by how far it got. Two rules, because the
gesture is two different gestures.

Raising the threshold used to be the only lever, and it was not even the right one.
The fling this is built on resolves through a `computeTarget` that takes a velocity
threshold as well as a positional one, and above that threshold — 125dp/s, slower
than any swipe anybody makes on purpose — direction decides and distance stops
mattering. Once the row had passed its actions, the next anchor in the direction of
travel was the committed one, so an ordinary flick past the reveal ran the action
whatever the threshold said. That is "I sometimes end up triggering the action",
and it is why this component now settles itself.

**A full swipe runs the outermost action**, which is the first one declared —
the one at the screen edge, and the one the row is sliding onto.
`isFullSwipeAction` says whether the *side* has a full swipe at all; it does not
choose which action, because there is only one action a full swipe can mean. Past
the reveal that action grows into the others and the ground behind the row crosses
to its colour, so what a committing swipe looks like is one action arriving from
the edge and taking the row.

`SwipeToDismiss` **needs an undo**. A dismissal with no way back is a data-loss
bug wearing a gesture; pair it with a [`Toast`](../overlays.md) carrying the
undo.

<!--sample:SwipeToDismissBasics-->
```kotlin
SwipeToDismiss(
    onDismissRequest = { remove(stop.name) },
    label = "Remove",
    icon = Tabler.Outline.Trash,
) {
    ListItem {
        +stop.name
        supporting { +"${stop.routes} routes" }
    }
}
```

---

## Accessibility

Every action is exposed as a `CustomAccessibilityAction` on the row, so a swipe
is never the only way to reach one. That is the rule the component was built
around, and it is the one most often broken by rolling your own.

`label` is what that action announces, and it is also the visible label — one
string, so the two cannot disagree. That matters more than it sounds: split into
an icon plus a separate description, a caller can write a background that says
*Delete* and announces *Archive*.

The background icons are decorative and cleared; the row's own content is
unchanged by the swipe.
