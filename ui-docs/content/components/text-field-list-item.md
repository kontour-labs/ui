# `TextFieldListItem`

*Also on this page: `ListGroupScope.field`*

**A list row you type into.** A [`ListItem`](list-item.md)'s ground, corners
and place in a group, holding a one-line text field where its words would go:
for a form that lives in a list, a contact's details or a stop's name and note,
where a [`TextField`](text-field.md) would stand out of the rows round it as a
box of its own.

<!--sample:TextFieldListItemBasics-->
```kotlin
val name = rememberTextFieldState("Perth Underground")
val note = rememberTextFieldState()

// A favourite's details, as rows of a group rather than boxes on a page:
// the label at the start and the value at the end, or with no label the
// field from the start, its placeholder saying what goes there.
ListGroup {
    field(name, label = "Name", icon = Tabler.Outline.Star)
    field(note, placeholder = "Add a note", supporting = "Only you see this")
}
```

**With a `label`, the label is at the start and the value at the end**, as a
settings row's value is. **Without one, the field starts where a row's words
start**, and its `placeholder` says what goes in it: the row is the field.

**A press anywhere on the row puts the caret in the field**, the label and the
padding included, as a press on a field's frame does. Focused, read-only or not,
the row takes a field's focused edge round it; with an `errorMessage`, the error
edge, and the message under the row in place of `supporting`.

### In a group

`field` on a [`ListGroup`](list-item.md), a `listGroup` in a `LazyColumn` or an
[`ExpandingListItem`](expanding-list-item.md)'s rows draws one and gives it its
`position`, as `item` does a `ListItem`. Make the `TextFieldState` before the
group: a group's builder in a `LazyColumn` runs outside composition, and a
field whose text lived there would lose it as the list scrolled.

On its own, or in a [`ListSection`](list-section.md), pass `position` yourself:

```kotlin
ListSection(title = { +"Contact" }) {
    TextFieldListItem(state = email, label = "Email", keyboardType = KeyboardType.Email, position = GroupPosition.First)
    TextFieldListItem(state = phone, label = "Phone", keyboardType = KeyboardType.Phone, position = GroupPosition.Last)
}
```

In [`SwipeActions`](swipe-actions.md) and a `ReorderableItem` it is a row like
any other. A `trailing` slot after the field takes a unit, a clear button or a
reveal toggle, and `imeChain` walks the keyboard's action key down the rows, as
it does down a form of `TextField`s.

## Accessibility

The field is the one node, named by its `label`, or by its `placeholder` where
there is no label; the label's own words are not said a second time. An
`errorMessage` is said as the field's error. The row is not a control of its
own: a screen reader reaches the field, and a press on the row's padding is a
pointer's convenience, not a second way in.

At high contrast the row takes the enhanced tier's edge round it at rest, as a
`ListItem` does, and the focused and error edges are the field family's own,
drawn over it. Nothing moves when it is focused or in error, so reduced motion
changes nothing here: the edge and the message appear where they will stay.
