# `Combobox`

A select the user can type into to narrow a long list. Above roughly a dozen
options, this rather than `Select`.

**A combobox is a select with search, not an autocomplete.** The value is always
one of the options, and typing something unmatched leaves the previous value
alone. For free text with suggestions, use a [`SearchField`](search-field.md) and
render results yourself — conflating the two gives a control where it is unclear
whether what you typed counts as an answer.

A command palette — a search field over *actions* rather than values — is
[not yet built](../components.md#not-yet-built), and is deliberately not this.

---

← [Text editing](text-editing.md) · [All components](../components.md)
