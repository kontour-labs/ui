# `SwipeActions` / `SwipeToDismiss`

Actions revealed by a sideways drag. `SwipeActions` reveals buttons;
`SwipeToDismiss` removes the row.

At rest it is a row and nothing else, so nothing about it announces that the
gesture exists. `rememberSwipeActionsState(initialValue = SwipeValue.End)` starts
a row already open, and `state.animateTo(SwipeValue.End)` moves an already-drawn
one — which is how you would hint at the gesture on first run.

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

**The swipe asks for a deliberate distance.** A release carries on to the next
anchor from just past halfway between them, up from two fifths — which is what
made it fiddly: a row revealed its actions on a gesture that was half a mind to,
and a full swipe committed from a little over a third of the way across. Raising
that is the only lever available, because the fling this is built on takes a
positional threshold and nothing else — there is no velocity to ask for
separately, so a fast flick cannot be treated differently from a slow push.

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
