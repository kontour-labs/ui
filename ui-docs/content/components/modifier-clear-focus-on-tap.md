# `Modifier.clearFocusOnTap`

<!--sample:ClearFocusOnTapBasics-->
```kotlin
val origin = rememberTextFieldState()

Box(Modifier.fillMaxSize().clearFocusOnTap()) {
    TextField(state = origin, label = "Origin")
}
```

Puts the keyboard away when a press lands somewhere nothing wanted. Tapping the
page beside a field is the gesture every platform treats as "I am done typing",
and a form where it does nothing is the report this exists to answer.

**It is already on if your root is a `Scaffold` or an `OverlayHost`**, which is
most apps. Reach for it directly when your root is neither, or when the screen
that takes presses is not the one those two cover — the rule is bounded by the
node it sits on, so a press outside that node's box never reaches it. That is
the one thing to get right, and it is why this belongs at the root rather than
wrapped around a form.

It observes two pointer passes instead of adding a click, which is what keeps it
out of everything else's way. The press is read on the **Initial** pass, before
children see it, so nothing can hide a press from it. The release is read on the
ordinary **Main** pass, by which point every child has had its turn to consume —
so a release still unconsumed there is one nothing on the page wanted, which is
the definition of "outside". Nothing is consumed, so no other gesture changes.

That gets four things right without special-casing any of them. A button still
runs, because its `clickable` consumes the release. Another field takes focus
normally, because it consumes too. **Scrolling does not drop the keyboard**,
because a drag that claims the pointer cancels the wait — a list that dismissed
the keyboard on every flick would be its own bug report. And a press on an
overlay's empty space counts, because the rule sits above both the content and
the overlay stack.

It calls `clearFocus()` rather than `clearFocus(force = true)`. A field that has
deliberately captured focus — a validation refusing to let go — is making a
statement this should not overrule.

---

## Accessibility

**It adds no click action**, which is the reason it is not a `clickable` on the
root. A root that is a button announces the whole page as one, and a screen
reader user then has a tappable element wrapping every element on the screen.
This observes the gesture instead of competing for it, so the semantics tree is
untouched.

Focus is only ever *released*, never moved somewhere else, so keyboard traversal
order is unaffected: the next Tab starts from the top rather than from wherever
a synthetic focus target happened to be.
