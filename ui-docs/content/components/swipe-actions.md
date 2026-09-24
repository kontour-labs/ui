# `SwipeActions` / `SwipeToDismiss`

Actions revealed by a sideways drag. `SwipeActions` reveals buttons;
`SwipeToDismiss` removes the row.

At rest it is a row and nothing else, so nothing about it announces that the
gesture exists. `rememberSwipeActionsState(initialValue = SwipeValue.End)` starts
a row already open, and `state.animateTo(SwipeValue.End)` moves an already-drawn
one — which is how you would hint at the gesture on first run.

**Up to three squircles grow out of the side.** As the row slides, each action is
its own button in the row's shape and its own colour, with page showing between
them — small and round at the first pixel of the swipe, because a button is never
taller than it is wide, and full-height buttons by the time the row has uncovered
them. Their icons and labels arrive from the row outward, a little after one
another. Asked for in those words: *"an Apple-like animation where (up to) three
squircle shapes expand out of the side when you start swiping, and then continuing
to swipe across will select the rightmost one"*. They used to be panels laid over
one strip painted in the nearest action's colour.

**Order runs from the screen edge in toward the row**, on both sides. So the
*first* action of a list is the one furthest from the row, and the last is the
button that appears against its edge as the swipe opens. The reason is the full
swipe: carrying a row all the way runs the first action of the side, and what a
full swipe looks like is that action taking the strip from the edge.

All of that mirrors, and the offset everything works in is a *logical* one —
positive is toward the trailing edge in both directions. The drag turns the
finger's physical movement into it once, and `Modifier.offset {}` mirrors it back
on the way out. Flipping it a second time by hand is what made a swiped row in
Arabic vacate a strip of bare page.

An action shows its label only where there is room for one. A single-line row is
48dp and an icon above a label wants 59, so on short rows the icon stands alone —
the label still reaches the screen reader through the row's custom action either
way.

## How it decides

**Whose gesture it is, early and by direction.** A drag within 45° of sideways is
the row's from a few pixels in; anything steeper is the list's. It used to wait for
a full touch slop sideways while the list waited for one downwards, and whichever
crossed first took the gesture — so a thumb's arc, which always has some drop in
it, handed the drag to the list. That was *"on iOS the swipe is way too hard to
do"*. The pixels spent deciding are not lost: the row moves by all of them once it
has the gesture.

**Past the point of no return, letting go commits — at any speed.** The point is
48dp past the actions and at least 55% of the row (and never within 24dp of its far
edge, so it can always be reached). There the outermost action widens over the
whole strip while the others fold into it, and one `DragThreshold` buzz marks the
line. Back off it by 16dp and the takeover undoes itself with a second buzz, since
backing off has a consequence too.

**Short of it, a flick opens or closes the actions** by the way it was thrown —
400dp/s and at least 12dp of travel — and a slow release opens them a third of the
way in (`swipePositionalThreshold`, 0.35) and closes them a third of the way out. A
flick never commits on its own. It used to: judged by where the throw was aimed,
a flick meant for the actions and thrown a little hard ran the action. That was
*"on android, the swiping is still too fiddly"*.

**A tap on a row that is showing its actions closes it**, and does not also reach
the row's own click. A tap on one of the buttons runs that action and closes the
row.

**One buzz, at the one moment that has a consequence.** It used to be four — a
tick per action width uncovered, the threshold, a confirmation when the action ran,
and a settle when the row came back — and the report was a row that "goes way too
crazy". A row opened or closed in code, with `animateTo`, is felt not at all.

**Three actions a side, and the fourth is refused.** One target is 88dp, so three
is 264dp of travel and already most of a phone's width; a fourth is a target
nobody can reach, and a row that silently hides its last action is worse than one
that says so.

**A full swipe runs the outermost action**, which is the first one declared —
the one at the screen edge, and the one that takes the strip at the point of no
return. `isFullSwipeAction` says whether the *side* has a full swipe at all; it
does not choose which action, because there is only one action a full swipe can
mean. A side where nothing opted in has no point of no return: carried as far as it
goes, it opens.

On a desktop a sideways scroll — a trackpad's two-finger push — moves the row as a
drag would, and settles it shortly after the last one. It never commits.

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

The buttons' icons are decorative and cleared; each button's click carries its
action's `label`. The row's own content is unchanged by the swipe.
