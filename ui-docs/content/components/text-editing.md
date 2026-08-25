# Text editing

Every field is built on foundation's state-based `BasicTextField`, so the caller
owns a `TextFieldState` rather than a `String` plus a callback:

<!--sample:TextFieldBasics-->
```kotlin
val query = rememberTextFieldState()

TextField(state = query, label = "Where to?", placeholder = "Station, stop or address")
```

`TextFieldState` is the right default because it makes the two classic bugs
unrepresentable: the caret jumping to the end when text is edited
programmatically, and characters dropping under fast typing because state
hoisting round-tripped through a recomposition.

| | For | Instead of |
|---|---|---|
| [`TextField`](text-field.md) | One line of anything | `TextArea`, when it can run long |
| [`TextArea`](text-area.md) | Several lines | `TextField`, for a name or a code |
| [`SearchField`](search-field.md) | A query that filters as you type | `TextField`, when submission is explicit |
| [`PasswordField`](specialised-fields.md) | A secret, with a reveal toggle | — |
| [`NumberField`](specialised-fields.md) | Digits or decimals | `Slider`, for "about this much" |
| [`PhoneField`](specialised-fields.md) | A number with a live mask | — |
| [`EmailField`](specialised-fields.md) | An address | — |
| [`Select`](select.md) | One of a fixed set | `RadioGroup`, at three or four options |
| [`MultiSelect`](multi-select.md) | Any number of a fixed set | `ChipGroup` of `FilterChip`, when they fit on screen |
| [`Combobox`](combobox.md) | One of a *long* fixed set | `SearchField`, for free text |
| [`rememberImeChain`](ime-chain.md) | Wiring a form's Next and Done | — |
| [`KontourTextToolbar`](text-toolbar.md) | The selection popup on desktop and web | — |

---

## Keyboard action chaining
<!--sample:ImeChainForm-->
```kotlin
val from = rememberTextFieldState()
val to = rememberTextFieldState()
val note = rememberTextFieldState()

val chain = rememberImeChain("from", "to", "note", onSubmit = { plan() })

TextField(state = from, label = "From", imeChain = chain["from"])
TextField(state = to, label = "To", imeChain = chain["to"])
TextField(state = note, label = "Note", imeChain = chain["note"])
```

Every field but the last shows **Next** and moves to the one after; the last
shows **Done** and submits. Without it a soft keyboard's action key does nothing,
and filling a three-field form means dismissing the keyboard and tapping the next
field between every entry — with the keyboard covering the field being tapped.

The order lives in one place, at the `rememberImeChain` call, rather than being
implied by three separate `imeAction` arguments that go wrong the first time
someone reorders the form. A chain overrides both `imeAction` and a specialised
field's own default: `PasswordField` defaults to Done, which is wrong for a
password halfway down a form.

---

## Text selection toolbar
`KontourTextToolbar` replaces Compose's selection popup with one drawn in the
design system — but **only on desktop and web**, where the default is a bare
unstyled row. On Android and iOS it is a deliberate no-op: the platform toolbar
there is a real system surface carrying "Look Up", "Translate", "Share", the
user's keyboard extensions and their configured text replacements, none of which
four buttons of our own can reproduce. Install it unconditionally at the root and
it does the right thing per platform.

---

## Validation
`errorMessage` sets `error` semantics *and* colours the border. Colour alone
would fail WCAG 1.4.1, so the message is not optional decoration — it is how a
screen-reader user learns there is a problem.

**Error outranks focus.** A focused invalid field keeps its error border,
because an accent ring would hide the thing the user needs to fix.

Helper and error text share one slot and animate in place, so a form does not
jump by a line height every time validation flips.

Every field carries its label as its accessible name. Compose has no
`labelledBy`, so a label drawn above a field is an unrelated node however close
it is on screen — `FieldScaffold` draws the label with `clearAndSetSemantics {}`
and puts the name on the control. Before the contract suite, **no text field or
select carried its label**: the user heard "Origin", moved on, and landed in an
unnamed edit box.

---

## Transformations
`InputTransformation` filters keystrokes *as they arrive* — the rejected
character never reaches the state, so the field cannot flicker through an
invalid value. The best error message is the one that never has to appear.

```kotlin
InputTransformation.digitsOnly()
InputTransformation.decimal(allowNegative = true)
InputTransformation.limit(10)
```

`OutputTransformation` changes what is *displayed* without changing what is
stored:

```kotlin
phoneMask()   // stored "0412345678"  displayed "0412 345 678"
cardMask()
timeMask()
```

That separation is the point: the caller reads clean digits out of the state and
never has to strip formatting back out, which is where mask implementations
usually go wrong.

`InputTransformation.decimal` is deliberately permissive about intermediate
states — `-`, `1.` and `-0.` all pass, because a user typing `-0.5` passes
through every one of them. Rejecting them makes the field impossible to type
into.
