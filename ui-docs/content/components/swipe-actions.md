# `SwipeActions` / `SwipeToDismiss`

Actions revealed by a sideways drag. `SwipeActions` reveals buttons;
`SwipeToDismiss` removes the row.

At rest it is a row and nothing else, so nothing about it announces that the
gesture exists. `rememberSwipeActionsState(initialValue = SwipeValue.End)` starts
a row already open, and `state.animateTo(SwipeValue.End)` moves an already-drawn
one — which is how you would hint at the gesture on first run.

**Up to three squircles grow out of the side, one after another.** As the row
slides, each action is its own button in the row's shape and its own colour, with
page showing between them. The one at the screen edge grows first; the next starts
only when the row has moved far enough to make room for it, and so on — *"can you
make it so they grow one after the other, as the item moves out of the way?"*. Each
is small and round when it appears, because a button is never taller than it is
wide, and a full-height button by the time the row has uncovered it. Asked for in
those words: *"an Apple-like animation where (up to) three squircle shapes expand
out of the side when you start swiping, and then continuing to swipe across will
select the rightmost one"*. They used to be panels laid over one strip painted in
the nearest action's colour, and then buttons that all grew at once.

**The icon and label grow in with their button.** An icon fades and scales up as
its button arrives, and the label unfolds under it — fading in while the space for
it opens — once the button is tall enough for both. The label used to switch on
the frame there was room for it, which read as a jump: *"it's a little bit janky
when the text first appears when you swipe"*. Under reduced motion both only fade.

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

**In a row that can be reordered, it waits out the hold.** Inside a
`ReorderableItem` on a touchscreen, where holding the row lifts it, a swipe is
claimed only once the finger has gone 24dp sideways — the wander a hold allows —
so a fingertip rolling as it presses does not swipe the row and stop it lifting.
The row catches up with the finger as it claims. Once the row has lifted, its
carry is its own: the swipe cannot take it.

**Whose gesture it is, early and by direction.** A drag within 45° of sideways is
the row's from a few pixels in; anything steeper is the list's. It used to wait for
a full touch slop sideways while the list waited for one downwards, and whichever
crossed first took the gesture — so a thumb's arc, which always has some drop in
it, handed the drag to the list. That was *"on iOS the swipe is way too hard to
do"*. The pixels spent deciding are not lost: the row moves by all of them once it
has the gesture.

**Past the point of no return, letting go commits — at any speed.** The point is
48dp past the actions and at least 55% of the row (and never within 24dp of its far
edge, so it can always be reached). On the way there the outermost action — the
one a full swipe runs — takes every bit of width the finger adds and pushes the
others smaller, until at the line it has the whole strip and they are gone; one
`DragThreshold` buzz marks it. Back off it by 16dp and a second buzz says so, since
backing off has a consequence too. The actions used to share the extra width
evenly and then give it all to the outermost at the line, which hid which of them
the swipe was heading for until it got there: *"the action that takes the full
swipe should keep expanding and slowly push the other 1 or 2 action/s to zero"*.
A side where no action takes a full swipe still shares it evenly.

**Short of it, a flick opens or closes the actions** by the way it was thrown —
400dp/s and at least 12dp of travel. A slow release opens several actions once
the first is out in full — where its soft tick plays — and a single action a third
of the way in (`swipePositionalThreshold`, 0.35); it closes an open row a third of
the way out. A flick never commits on its own. It used to: judged by where the throw was aimed,
a flick meant for the actions and thrown a little hard ran the action. That was
*"on android, the swiping is still too fiddly"*.

**The whole row drags, its buttons included.** A drag that starts on a revealed
action moves the row as one on the row does — it used to do nothing, because the
drag lived on the row's content and the buttons sit beside it. A tap on a button
is still the button's: the drag takes the gesture only once it has moved.

**Past the last place it can rest, the row gives.** Towards a side with no
actions, or beyond a side's actions where there is no full swipe, the row follows
the finger a little way, less for every pixel, and comes back when let go — from
rest. It used to sit still until the finger lifted and then jump that way and
back, which was the settle spring starting from the finger's speed at a place the
row had never left. Under reduced motion it does not give.

**A tap on a row that is showing its actions closes it**, and does not also reach
the row's own click. A tap on one of the buttons runs that action and closes the
row.

**A press anywhere else closes it too**, as a SwiftUI list's row does: another
row, a button, the list scrolled. That press still does what it does — the other
row's click, the button's action — and the open row slides home out of its way.
Presses are seen at the app's [`OverlayHost`](../overlays.md); without one round
the app, a press on another swipe row still closes it, and one on anything else
does not.

**One buzz at the one moment that has a consequence, and a soft tick as each
action arrives.** It used to be four — a tick per action width uncovered, the
threshold, a confirmation when the action ran, and a settle when the row came back
— and the report was a row that "goes way too crazy". What is left is the
threshold, and a soft tick as each action is dealt out to its full size: once per
action, only while it grows, so a finger resting where one fills is one tick. A
row opened or closed in code, with `animateTo`, is felt not at all.

**Three actions a side, and the fourth is refused.** One target is 88dp, so three
is 264dp of travel and already most of a phone's width; a fourth is a target
nobody can reach, and a row that silently hides its last action is worse than one
that says so.

**A full swipe ends on a tick.** Let go past the point of no return and the row
goes to the far edge, where the outermost action holds the whole strip — the others
stay folded away the whole time, not only while the finger is past the line. There
the action's icon gives way to a check mark, drawn in a stroke, and the row holds a
moment to show it before the action runs and the row comes home. The row always
spent a moment at the edge; it read as a pause, *"it stays at the end for just
that little bit too long"*, so the tick puts the moment to use and makes it a
little longer on purpose. The tick sets off when you let go, not when the row has
finished settling: the icon leaves while the row is still travelling and the
stroke begins about as it arrives, a tenth of a second after release rather than a
third. Only the action waits for the row to settle. It is optional: `fullSwipeConfirmation = false` runs the
action the moment the row arrives, for a list where the row disappearing is
confirmation enough. Under reduced motion the tick appears rather than draws.

**A tapped action ticks too.** The button tapped turns its icon into the same
check mark, drawn, holds it the same moment, and then the action runs and the row
closes — where it used to run and fold away in the same instant, with nothing to
say it had. While it ticks the row stays put. `tapConfirmation = false` runs a
tapped action at once. An action run from a screen reader's actions menu runs at
once either way.

**Each action can say for itself.** `fullSwipeConfirmation` and
`tapConfirmation` on a `SwipeAction` win over the row's, either way: a removal
that a toast with an undo already confirms can do without the tick, while the
pin beside it keeps one; an action that opens a menu or a sheet has that
for its answer. Left null, an action does what the row does.

<!--sample:SwipeActionOwnTick-->
```kotlin
SwipeActions(
    end = listOf(
        // Undone from the toast it raises, so it runs as the row arrives.
        SwipeAction(
            label = "Remove",
            icon = Tabler.Outline.Trash,
            onAction = { remove(stop.name) },
            background = Theme.colours.danger.solid,
            fullSwipeConfirmation = false,
            tapConfirmation = false,
        ),
        SwipeAction(
            label = "Pin",
            icon = Tabler.Outline.Pin,
            onAction = { pin(stop.name) },
            background = Theme.colours.accent.solid,
        ),
    ),
) {
    ListItem {
        +stop.name
        supporting { +"${stop.routes} routes" }
    }
}
```

**A full swipe runs the outermost action**, which is the first one declared —
the one at the screen edge, and the one that takes the strip at the point of no
return. `fullSwipe` says whether the *side* has a full swipe at all; it
does not choose which action, because there is only one action a full swipe can
mean. A side where nothing opted in has no point of no return: carried as far as it
goes, it opens.

On a desktop a sideways scroll moves the row as a drag would. A trackpad's
two-finger push follows the fingers — on a Mac it arrives as a pan, in pixels — and
settles when they lift; a mouse's sideways wheel moves the row 24dp a notch — two
clicks open a single action, four the first of several — and settles shortly after
the last. In a browser, where a scroll arrives in pixels rather than notches, a
trackpad follows the fingers a pixel at a time and one event never moves the row
more than a notch would. Either one is the
row's only if it begins mostly sideways, and a vertical scroll with a little drift
in it stays the list's.

**A scroll can make a full swipe**, the way a drag does. Carried past the point of
no return, the outermost action takes the strip with the same buzz, and it runs
when the gesture ends there: a trackpad's pan when the fingers lift, a wheel's
scroll once it has been quiet. Backing off before then undoes it, as it does under
a finger. It used to stop short of committing, on the argument that a scroll has
no point of no return to feel, and the report was that a full swipe could not be
done from a trackpad or a mouse at all.

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

### In a group of rows

A row joined to its neighbours takes their shape — `shape =
position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner,
Orientation.Vertical)`, the same the `ListItem` inside it is given — so it clips
square where it meets the next row. **Its actions do not.** Each is always the
shape of a row on its own, `actionShape`, which is `ListItemDefaults.Shape`: a
button squared off on the side that faces another row, when on that side it
faces nothing at all, reads as a broken piece of one. Pass `actionShape` only
when your rows are not the default shape.

### On a tall row

**An action is never taller than a two-line row, 64dp**, however tall the row it
comes out from behind: on a row with a picture or a paragraph in it, it sits
centred, and a full swipe's takeover grows across the strip and not down it.
It used to be as tall as the row, *"ridiculously tall"* for an icon and a word.
`maxActionHeight` moves the cap. It never goes below what the icon and label
need, so at large type it grows with them, and `Dp.Infinity` gives the old
full-height buttons back.

---

## Accessibility

Every action is exposed as a `CustomAccessibilityAction` on the row, so a swipe
is never the only way to reach one. That is the rule the component was built
around, and it is the one most often broken by rolling your own. A row with
`enabled = false` offers none of them, as it offers no swipe.

`label` is what that action announces, and it is also the visible label — one
string, so the two cannot disagree. That matters more than it sounds: split into
an icon plus a separate description, a caller can write a background that says
*Delete* and announces *Archive*.

The buttons' icons are decorative and cleared; each button's click carries its
action's `label`. The row's own content is unchanged by the swipe.

At high contrast the action buttons keep the grounds you gave them, with their
icon and label in whichever ink reads on each; the tick a full swipe draws and a
button's focus ring thicken with every other line. The row is your content, and
answers the tier itself.

Under [reduced motion](../tokens.md#motion) a row the app opens or puts back
arrives at once, the outermost action holds the strip at once on a full swipe, and a full swipe's or a tapped action's tick appears whole
instead of drawing itself; icons and labels fade without growing. The give past
the last resting place is off. A drag is untouched — the row
follows the finger at every setting — and it settles after the finger lifts on a
short tween rather than a spring.
