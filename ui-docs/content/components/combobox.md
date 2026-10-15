# `Combobox`

A select the user can type into to narrow a long list. Above roughly a dozen
options, this rather than `Select`.

<!--sample:ComboboxBasics-->
```kotlin
var stop by remember { mutableStateOf<String?>(null) }
val names = remember { stops.map { it.name } }

// A `Select` you can type into, for a list too long to scroll. `matches` is
// where a fuzzier rule goes — matching on a stop code as well as its name.
Combobox(
    value = stop,
    options = names,
    onValueChange = { stop = it },
    label = "From",
)
```

**A combobox is a select with search, not an autocomplete.** The value is always
one of the options, and typing something unmatched leaves the previous value
alone. For free text with suggestions, use a [`SearchField`](search-field.md) and
render results yourself — conflating the two gives a control where it is unclear
whether what you typed counts as an answer.

**Open, the field is a text box like any other.** A click or a tap in what has
been typed moves the caret, a double-click or a double-tap selects a word, a
drag selects a run, and the right-click menu offers to copy and cut it — all
without closing the list. A press on the field around the text puts the caret
in it; the chevron, a pick or a press anywhere else closes the list. A drag on
the field scrolls the page, as a drag on any field does, and the list goes with
it; a drag on the list scrolls the list, and nothing under it.

**Options drawn rather than named.** `optionContent` and `valueContent` draw
the options and the value as a [`Select`](select.md)'s do.
What is typed is matched against `optionLabel` whatever they draw, and open, the
field shows the value as words — the field keeps the height it had closed, so it
does not drop to one line as it opens.

A command palette — a search field over *actions* rather than values — is
[not yet built](../components.md#not-yet-built), and is deliberately not this.

---

## Accessibility

Everything on [`Select`](select.md), plus a field the user types into.

`matches` decides what filtering does, and what it does is change the list under
the user's fingers. Keep it predictable: a fuzzy match that reorders results as
each character arrives is very hard to follow without sight.

`emptyLabel` is what the menu says when nothing matches. It is announced, so make
it say what to do next rather than "No matches".

At high contrast the field's border is drawn thicker in the enhanced scheme's
colours, and a `Filled` or `Plain` field gains one it has at no other tier. The menu's
border becomes the near-ink `outline`, and both it and the tick beside the
chosen option are drawn thicker.

Under reduced motion the menu fades in beside the field rather than scaling out
of it, and the chevron turns without its overshoot. The field's colour changes
are the same short fade at every setting.
