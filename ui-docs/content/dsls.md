# Slots, and the `+` that keeps them short

Every component in this library takes its **content** as slots rather than as
strings. That is the whole API; there is no `label: String` to fall back to.

The rule is about **content**, and three kinds of `String` are not content. Each
survives on purpose, and each is a different reason:

| | Example | Why |
|---|---|---|
| **Chrome** | `TextField.label`, `Select.placeholder` | Animates between two positions, becomes the control's accessible name, and has nowhere to put a composable |
| **A projection** | `Avatar.name`, `Select.optionLabel` | Not drawn as given — it derives the initials and the stable colour index, or turns one of *your* objects into a row |
| **An announcement** | `SwipeToDismiss.label`, `LoadingOverlay.label` | Reaches an API that accepts only a `String` — `contentDescription`, `onClickLabel`, `CustomAccessibilityAction`, `stateDescription` |

The third is the one worth stating carefully, because it looks like a miss and
is not. Where text is both **drawn and announced**, one `String` makes the two
agree by construction. Split it into a slot plus a name and a caller can write a
swipe background that says *Delete* and announces *Archive* — a class of
accessibility bug the single parameter makes impossible. `InputChip.removeLabel`
argues the same case from the other direction.

Everything else is a slot.

```kotlin
Button(onClick = ::save) { +"Save" }

ListItem(onClick = { open(stop) }) {
    +stop.name
    supporting { +stop.detail }
    leading { +Tabler.Outline.Bus }
    trailing { Switch(saved, onCheckedChange = ::save) }
}
```

## Why slots at all

A `String` cannot carry a second colour, an inline tag, an `AnnotatedString`, or
whatever the design wants next. So a component that takes one grows a parallel
slot beside it the first time anybody needs any of those, and then has two
vocabularies in one signature. `ListItem` had reached three strings and two
slots. `SettingRow` had a `value: String?` whose entire job was to build a
default for its `trailing` slot.

Slots cost brevity, which is why Material call sites run long. The `+` is what
buys it back.

---

## The `+` vocabulary

Inside any slot, `+` takes four things and nothing else:

| | becomes |
|---|---|
| `String` | `Text`, in the slot's style |
| `AnnotatedString` | `Text`, its spans merged over the slot's style |
| `ImageVector` | `Icon`, decorative, at the slot's size |
| `Painter` | `Icon`, decorative, at the slot's size |

A fifth overload would be the point at which the call site should be writing a
composable — and it can, because a slot is a slot:

```kotlin
Button(onClick = ::save) {
    +Tabler.Outline.Check
    +"Save"
    if (count > 0) Badge(count)          // ordinary composable, same block
}
```

### Style comes from the slot, not the call site

`+"…"` never says how to draw itself. Each slot wraps its content in
`ProvideTextStyle` and `ProvideContentColour`, so the same character is
`bodySmall` and muted under a row's supporting line, and the button's own label
style inside a button. That is the whole reason it can be one character: there is
nothing left for the call site to decide.

It follows that **a component's own text styling lives in the component**, not in
its `Text` calls. When you add a region, provide its style around the slot rather
than passing `style =` to something inside it.

### `+icon` is decorative

It takes no content description, because the text beside it is the accessible
name and a screen reader announcing both says everything twice. For an icon that
is the only thing saying what something is, use `icon(image, contentDescription)`
— and if you reach for that often, the component probably wants a real
`contentDescription` parameter instead.

### One sharp edge

`+` binds tighter than `+`. A long string split across lines does not compile:

```kotlin
supporting { +"Perth Underground will be taken off " +      // ✗ unaryPlus(…).plus(…)
    "your home screen." }

supporting { +("Perth Underground will be taken off " +     // ✓
    "your home screen.") }
```

---

## Two kinds of scope, one spelling

Which one a component uses is invisible at the call site, and the difference is
worth understanding only when you are writing a component.

**Emitting.** `Button`, `Chip`, `Tag` — anything whose content is a single run of
things. The slot is a normal `@Composable` lambda and `+` emits where it is
written. The scope extends the layout scope the lambda already had
(`RowContentScope : RowScope`), so `Modifier.weight` still means what it did.

**Collecting.** `ListItem`, `Banner`, `TopBar`, `MenuItem`, `SheetHeader` —
anything with more than one region. Content cannot run where it is written: a
row's label belongs inside a `Column` inside a `Row`, next to a leading slot
declared after it. So the builder is a plain lambda that *records* what goes
where, and the component composes the regions in the order the layout wants.

`TopBar` is the clearest case for why. `TopBarStyle.Large` renders the title
**twice** — small and fading in, large and sliding out — at two type scales. A
slot that emitted where it was written could only ever be in one of them.

A collecting builder is not composable, so you cannot call a composable or read
`Theme` in the builder itself — only inside the slots it hands you. This is the
same shape as Compose's own `LazyListScope`.

**A bare `+` fills the component's primary text** — the region a one-line call
means. A row's label, a banner's message, a state screen's title, a stat's label.
Those are four different *names* because they are four different things, and the
[word table](../../docs/building/contributing.md#the-words) keeps them apart: a `label`
names a control, a `title` heads a region, a `message` is prose. What is uniform
is which one `+` reaches, not what it is called.

---

## The shorthands

On top of the slots, a few scopes carry a handful of small functions for the
shape you write nine times out of ten.

```kotlin
DropdownMenu(visible = open, onDismissRequest = { open = false }) {
    section("This stop")
    item("Share", icon = Tabler.Outline.Share, shortcut = "⌘S") { share(stop) }
    item("Copy stop ID", icon = Tabler.Outline.Copy) { copy(stop.id) }
    divider()
    item("Remove favourite", icon = Tabler.Outline.Trash, destructive = true) {
        remove(stop)
    }
}
```

These still take strings, and that is deliberate: a shorthand exists for the
common shape, and the common shape is text. They build through the slot API
underneath.

So does `Modifier.tooltip`, which is a shorthand wearing a different hat — the
brief form of the `Tooltip` component, taking a `String` where the component
takes a slot. A modifier has no composition position to hang a slot on, and the
relationship is the same one every scope here has with the component beside it.

### The scopes that *do* offer both

`StatScope` and `KeyValueScope` take a `String` **and** a slot for the same
region, which reads like drift beside `ListItemScope`. It is the announcement
rule again, one level down.

Both components merge their whole block into a single announcement — `Stat`
draws `semantics(mergeDescendants = true) { contentDescription = … }` — so the
drawn nodes stop speaking for themselves and the scope has to collect the words
in speaking order. `value("12")` contributes "12"; `value { +"12" }` cannot, and
that is exactly what `announcement()` is for:

```kotlin
Stat {
    value("12")            // drawn and spoken
    +"Stops away"
}

Stat {
    value { Countdown(arrivesAt) }   // drawn only…
    +"Until departure"
    announcement("4 minutes until departure")   // …so say it explicitly
}
```

`ListItemScope`, `BannerScope` and `StateScope` merge nothing, so their drawn
text speaks for itself and a `String` overload would buy nothing. Whether a
scope offers one is decided by whether its component merges, not by taste.

### They do not replace anything

Each scope **extends the receiver the content lambda already had**, so every
component still works inside it, unchanged:

| Scope | Extends | Used by |
|---|---|---|
| `MenuScope` | `ColumnScope` | `DropdownMenu`, `AnchoredDropdownMenu`, `SubMenu` |
| `NavDrawerScope` | `ColumnScope` | `NavDrawer`, `ModalNavDrawer`, `NavDrawerSection`, `NavDrawerGroup` |
| `ListGroupScope` | — | `ListGroup`, `LazyListScope.listGroup` |

```kotlin
DropdownMenu(visible = open, onDismissRequest = { open = false }) {
    item("Share") { share(stop) }

    // Not in the shorthand — nothing here takes a modifier. Use the component.
    MenuItem(onClick = ::pin, modifier = Modifier.testTag("pin")) { +"Pin" }
}
```

**No shorthand takes a `modifier`.** That is the rule, and it is what keeps them
small: the moment a row needs one it has outgrown the shorthand, and the
component is right there in the same scope.

---

## What each shorthand is actually for

Not brevity. Each one deletes a specific mistake.

### `MenuScope` — the menu that stays open

`MenuItem` cannot close the menu it is in; it has never been told how. Written
by hand, every row is:

```kotlin
MenuItem(onClick = { open = false; share(stop) }) { +"Share" }
```

and the one that forgets leaves a menu hanging over the screen it just navigated
away from. `item` does the dismiss for you, and closes *first* — an action that
navigates takes the composition with it, and a dismiss queued behind it never
runs.

`closeOnClick = false` for a row the user is likely to press again, like a filter
toggle. `submenu` closes the whole stack rather than just itself, for the same
reason one level up.

### `ListGroupScope` — the corners

A group of rows is one object with rows in it: the first rounds its top, the last
rounds its bottom, the rest are square. By hand that is index arithmetic at every
call site.

```kotlin
val positions = listPositions(stops.size)          // the bit people forget
stops.forEachIndexed { index, stop ->
    ListItem(position = positions[index], …) { +stop.name }
}
```

```kotlin
ListGroup {                                        // the same thing, counted for you
    stops.forEach { item(it.name, supporting = it.detail) { open(it) } }
}
```

Get it wrong and you get a group with two rounded rows in the middle of it, which
reads as a rendering fault rather than as a mistake.

Inside a `LazyColumn`, use `listGroup`:

```kotlin
LazyColumn(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
    item { SectionHeader { +"Saved places" } }
    listGroup(savedPlaces, key = { it.id }) { place ->
        item(place.name, supporting = place.detail, icon = Tabler.Outline.Bus) {
            open(place)
        }
    }
}
```

The row *content* stays lazy — a row scrolled off screen is not composed — but
the builder runs for every element up front, because the first row cannot know it
is first until something has counted the rest. That is the right trade for the
lists this is for and the wrong one for a list of thousands; a group of thousands
of rows with rounded ends is not a thing anyone wants to look at, so the
constraint and the design agree.

### `NavDrawerScope` — the indent

A destination inside a `group` inside a `section` is still a destination. It
keeps its full touch target and reports its selection to the travelling pill
through a composition local rather than through its parent, so the only thing
nesting changes is the indent — and by hand that means threading `nestLevel`
down every branch.

```kotlin
NavDrawer(header = { AppMark() }) {
    item("Home", Tabler.Outline.Home, selected = tab == Tab.Home) { go(Tab.Home) }

    section("Saved") {
        item("Favourites", Tabler.Outline.Star, badge = 3) { go(Tab.Favourites) }
        group("Routes", expanded = open, onExpandedChange = { open = it }) {
            item("950", selected = route == "950") { go("950") }
        }
    }
}
```

---

## One verb: `item`

Every builder scope calls its child `item`, whatever the component draws — a menu
row, a drawer destination, a button in a group, a key-and-value pair. There were
four names for this once (`item`, `row`, `action`, `destination`), which meant
knowing which component you were inside before you could write a line of it.

`action` was the worst of them, because `action` already means something else
here: a *content region* holding a button, in `BannerScope` and `StateScope`. The
same word for a child and for a region is how a vocabulary stops being one.

`ListGroupScope` takes an overloaded `item` that hands you the position, so a row
that has outgrown the shorthand does not have to leave the group:

```kotlin
ListGroup {
    item("Notifications") { … }
    item { position ->
        ListItem(position = position, onClick = ::signOut) { +"Sign out" }
    }
}
```

That overload is the reason the other shorthands can stay small — and it is an
overload rather than a fifth verb because a group holds items either way.

---

## What this cost, and what pays for it

`ListItem(label = "…")` could not produce a row without an accessible name.
`ListItem { leading { +icon } }` can. That is a real regression in what the type
system guarantees, and it was the known price of moving to slots — paid for by a
contract assertion that every component declaring a role must announce a
non-empty name.

The trade, and how the DSLs are tested, are in
[`building/testing.md`](../../docs/building/testing.md#what-the-slot-conversion-cost-and-what-pays-for-it).
