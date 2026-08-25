# `rememberImeChain`

*Also on this page: `ImeChain`.*

Wires a form's fields together so the keyboard's action key moves to the next
one and submits on the last.

<!--sample:ImeChainBasics-->
```kotlin
val from = rememberTextFieldState()
val to = rememberTextFieldState()
// Declared in order, once. The first field's action key says Next and moves
// focus; the last one says Done and runs `onSubmit`.
val chain = rememberImeChain("from", "to", onSubmit = { plan() })

TextField(state = from, label = "From", imeChain = chain["from"])
TextField(state = to, label = "To", imeChain = chain["to"])
```

```
val chain = rememberImeChain("from", "to")

TextField(state = from, label = "From", imeChain = chain["from"])
TextField(state = to, label = "To", imeChain = chain["to"])
```

The chain is declared once, in order, and each field takes its own link. That is
what makes the *last* field know it is last — a field cannot work out on its own
whether the action key should say Next or Done, and a form that says Next on its
final field sends people looking for a field that does not exist.

Reordering the fields on screen without reordering the chain is the mistake this
shape makes visible: the chain is the reading order, written down.

---

← [Text editing](text-editing.md) · [All components](../components.md)
