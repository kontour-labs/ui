# `ChatBubble`

<!--sample:ChatBubbleBasics-->
```kotlin
val messages = listOf(
    "sam" to "Is the 950 running tonight?",
    "sam" to "The app says it's delayed",
    "me" to "Every 15 minutes until 11pm",
)
Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    messages.forEachIndexed { index, (sender, text) ->
        ChatBubble(
            side = if (sender == "me") BubbleSide.Outgoing else BubbleSide.Incoming,
            // Consecutive messages from one sender are a run; the last has the tail.
            position = GroupPosition.of(messages, index) { it.first },
        ) {
            Text(text)
        }
    }
}
```

One message in a conversation. `BubbleSide.Outgoing` sits at the end of the line in
the theme's `primary`, `Incoming` at the start on a quiet ground — the arrangement every
messaging app has taught its users, mirrored right to left.

**A run reads as one.** Consecutive messages from one sender are a run, and
`position` is where a bubble sits in it: the corners on the sender's side tighten
where two bubbles of a run meet, and **only the last of a run has a tail**, so a run
reads as one turn in the conversation and the tail marks where it ends.
`GroupPosition.of(items, index) { it.sender }` works the positions out from a list.
Put a couple of dp between the bubbles of a run and more between runs.

Every bubble keeps the tail's width free on its sender's side, tail or not, so a
run lines up edge to edge. `showTail = false` gives a quieter thread.

A bubble grows to `maxWidthFraction` of the width it is given — 80% — so a short
reply is short and a long one still leaves the other side of the conversation
visibly the other side. `meta` goes under the message at its end, smaller and
fainter: a time, "Read", a tick.

---

## Accessibility

Each bubble is one merged node, so its message and its meta are read together
rather than as two fragments.

**Name the sender where it is not obvious.** In a conversation between two people
the side says who spoke, and a screen reader cannot hear sides: put the name in the
bubble's `contentDescription` through a `Modifier.semantics` of your own, or in a
visible label above the run, in a group chat especially.

Colour is not the only thing that separates the two sides — their position does —
but check the fill against its text colour if you pass your own.
