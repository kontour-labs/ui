# `Rating`

<!--sample:RatingBasics-->
```kotlin
var rating by remember { mutableStateOf(0f) }

Rating(value = rating, onValueChange = { rating = it }, contentDescription = "Your rating")

// A null callback means read-only, which means not a control at all: no role,
// no touch target, one node saying "Average rating, 4.3 out of 5".
Rating(value = 4.3f, onValueChange = null, contentDescription = "Average rating")
```

**`onValueChange = null` makes it read-only, and read-only means it is not a
control at all** — no role, no touch target, no click action, one node saying
"Average rating, 4.3 out of 5".

That is the case that gets built wrong. Most ratings on any screen are
*averages*, and shipping those as five silent radio buttons gives a
screen-reader user five things to activate that do nothing. Pass
`onValueChange = null` and it becomes one node saying "Average rating, 4.3 out
of 5", which is what an average is.

Interactive, it is a `selectableGroup` of `Role.RadioButton` marks, because
picking one of five is what that is. Each announces its own value, so "3 out of
5" is a thing to navigate to and choose rather than a slider to drag blind.

A fractional `value` draws a partly-filled mark and only makes sense read-only —
a tap cannot mean 3.4. The fill is a hard clip over the empty glyph rather than
a gradient: a star is not a rectangle, and a gradient across one fills the
points before the body and reads as a smudge.

`icon` and `filledIcon` default to a star outline and a filled star, and are
parameters because a rating of hearts is a reasonable thing to want.

**Drag across the marks to set the score.** Each mark crossed ticks, so it can be
set without looking, and the taps still work. `allowHalf = true` lets a drag stop
on a half mark — the left half of a mark is `.5`, the right half is whole. Off by
default, because a rating that starts emitting `3.5` to callers who expected `4`
is a change of contract rather than a nicety.

**A score can be taken back to zero.** Tap the score already given and it clears —
decided when the finger lifts, so a drag that starts there still drags — or carry
a drag off the start of the row. `allowZero = false` keeps the lowest score at one
mark, for a rating that has to be given. A quick double tap on a new score now
sets it and then clears it, which is the one habit this changes.

---

## Accessibility

Interactive, it is a `selectableGroup()` of `Role.RadioButton` marks — a rating
is one of five, and that is what a screen reader should hear. Activating the
selected mark again clears the score, which is how the keyboard and a screen
reader reach zero; the score is then announced as "0 out of 5".

Read-only, it is one node with a `stateDescription`: "4.3 out of 5". A read-only
rating rendered as five separate stars would be five nodes announcing nothing
each.

`contentDescription` names the control and it matters here more than usual —
"Your rating" and "Average rating" are the same five stars and completely
different facts.

At high contrast it takes the enhanced scheme's colours and each interactive
mark's focus ring is thicker; nothing else about it changes. The default marks
already differ in shape as well as colour: a filled star and an outlined one.

Under reduced motion a mark fills over a short tween and settles at full size
without the overshoot. A drag across the marks sets the score as the finger
moves, at every setting.
