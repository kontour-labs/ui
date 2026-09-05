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
