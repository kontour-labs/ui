# `Accordion`

Disclosure with hoisted state, so the caller decides what is open — including
opening the section containing whatever the user searched for.

<!--sample:AccordionBasics-->
```kotlin
Accordion(
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    header = { +"Accessibility" },
) {
    Text("Step-free access at all platforms.")
}
```

---

## Accessibility

The header is `Role.Button` with a `stateDescription` of `expandedLabel` /
`collapsedLabel`, so a screen reader hears whether the section is open before
deciding whether to move into it. The chevron is decorative and cleared.

Collapsed content is **not composed**, so it is not in the accessibility tree at
all. That is why a collapsed accordion must never hold the only route to
something, and why the header text has to say what is inside rather than
labelling it "Details".

Use it for content, not for a form: a required field the user cannot see is a
required field they cannot fill in.

At high contrast the header takes the enhanced scheme's colours and a thicker
focus ring. It draws no border or divider of its own, so nothing else about it
changes.

Under reduced motion the body takes its room at once and fades in, and fades out
before the room closes, rather than pushing what is below it down and back; the
chevron turns without its overshoot.
The header never shrinks on a press, at any setting.
