# `SelectableText`

<!--sample:SelectableTextBasics-->
```kotlin
SelectableText {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text("Replacement buses", style = Theme.typography.titleMedium)
        Text("Midland line, Saturday 14 June, 6am to 10pm. Buses leave from stand 4.")
        Button(onClick = { plan() }) { +"Plan around it" }
    }
}
```

Lets the reader select and copy the text inside it. **Nothing in the library
is selectable until it is inside one.** A screen of controls whose labels light
up under a stray drag feels broken, so selection is opt-in and by region: wrap
what is *read* — an article, a notice, an address, an error someone will want
to paste into a message — not the app.

**One region for a piece of reading, not one per paragraph.** A selection runs
across everything in its region and stops at the region's edge, so a region per
paragraph is a drag that stops at every paragraph. Around a lazy list, wrap the
list: a selection then reaches as far as the list has composed, which is as far
as the window goes.

**Controls inside a region stay unselectable.** A drag across the notice above
picks up the title and the sentence and not "Plan around it". The same holds
for every control's label — buttons, chips, tabs, navigation destinations, a
segmented control's options, menu items, a stepper's value — and for any row
with an `onClick`. A row *without* one is content, and is selectable: a
`KeyValueList` of a stop's details is exactly what somebody copies.

**Redacted text is never selectable.** A placeholder bar keeps its string so it
can measure like the real thing, and must not hand it over on copy.

To keep something else out — a live demo, a canvas that takes drags — wrap it
in Compose's own `DisableSelection`, which is what the library does itself.

### Colours

The highlight is the accent at 35% and the handles are the accent, which
`KontourTheme` provides to every selection beneath it — so a selection in a
paragraph and one in a `TextField` beside it are the same colour, and a subtree
under `ProvideTokens` with another scheme gets that scheme's accent.

### Copying on the web

⌘C and Ctrl+C copy through the browser's own copy event. Copy from the
selection's toolbar, or a right-click menu, goes through Compose's web
clipboard instead, which does not reach the browser at the time of writing —
[measured, and why](../../../docs/building/testing.md#three-fixes-no-number-moved-all-three-reverted).
On Android, iOS and the desktop both go to the platform clipboard.

---

## Accessibility

A region changes nothing a screen reader reads: each `Text` in it is announced
as it was, and a control inside keeps its role. Selection is a pointer and
keyboard affordance — screen readers have their own ways to read and copy text.

On touch, a long press starts a selection and shows the platform's handles and
toolbar; with a mouse, a drag selects and a double-click selects a word. Both
work with the keyboard's copy shortcut once something is selected.

At high contrast the highlight is the enhanced scheme's accent at the same 35%
wash, with its handles in the solid accent; nothing else changes. The library
adds no animation to a selection, so reduced motion changes nothing.
