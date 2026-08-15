# Collections

Rows, and the things that happen to them.

| | For | Instead of |
|---|---|---|
| [`ListItem`](#listitem) | One row of anything | — |
| [`SettingRow`](#settingrow) | The settings shape: icon, label, value | `SelectionRow`, when the row *is* the toggle |
| [`ListSection`](#listsection--sectionheader) | A titled group of rows | — |
| [`SwipeActions`](#swipeactions--swipetodismiss) | Actions behind a sideways drag | A menu, which you owe anyway |
| [`SwipeToDismiss`](#swipeactions--swipetodismiss) | Removing a row by dragging it away | — |
| [`ReorderableItem`](#reorderableitem) | Drag to reorder, live | — |
| [`PullToRefresh`](#pulltorefresh) | Pull at the top to reload | A toolbar action, which you owe anyway |
| [`LoadMore`](#loadmore) | The paging row at the end | — |
| [`Modifier.fadingEdges`](#modifierfadingedges) | Fading content at a scrollable edge | — |
| [`Scrollbar`](#scrollbar) | Where you are in a long list | — |

---

## `ListItem`

![ListItem](../../../../../app/ui-catalog/screenshots/components/listitem-light.png)

```kotlin
ListItem(onClick = { open(stop) }, position = ListItemPosition.of(index, stops.size)) {
    leading { +Tabler.Outline.Bus }
    +stop.name
    supporting { +"${stop.routes.size} routes" }
    trailing { +Tabler.Outline.ChevronRight }
}
```

The builder is `ListItemScope`, and the bare `+` is the row's headline — see
[`dsls.md`](../dsls.md) for the whole vocabulary. `overline`, `supporting`,
`leading` and `trailing` are the other slots.

**A group of rows is one object, not a stack of cards.** Only the outside corners
of a group round; the ones facing a neighbour get a hairline. `ListItemPosition`
carries that, and `of(index, count)` gets the one-item case right — which is the
case a three-item example in a catalog never exercises and every settings screen
with a single row hits immediately.

**Rows default to a *sunken* ground.** In this scheme `surface` and `background`
are the same white, so a row drawn on `surface` is invisible on a page and a
group of them reads as loose text.

`role` defaults to `Role.Button` and is a parameter, because a row can be a
button, a radio option or a plain block of text depending on what it is for.

> A disabled `ListItem` used to drop its `clickable` entirely. The callback could
> not fire, so it looked right — but the node then had no role and no disabled
> flag, and a disabled row announced as plain text with no way to tell it was
> unavailable rather than broken. It keeps the modifier and passes
> `enabled = false` now.

## `SettingRow`

![SettingRow](../../../../../app/ui-catalog/screenshots/components/settingrow-light.png)

The settings-screen shape: an icon, a label, and the current value on the right.
`onClick` is optional — a row that only displays a value does not need one.

**`SettingRow` is `clickable`, not `toggleable`.** It is a row you tap to open
something, which may happen to carry a switch. That is why a control inside it
must publish its own state — see
[`Checkbox`](selection.md#checkbox).

**Reach for [`SelectionRow`](selection.md#selectionrow) instead** when tapping
the row *is* the toggle. `SettingRow` opens a screen; `SelectionRow` flips a
value.

## `ListSection` / `SectionHeader`

A titled group. `SectionHeader` carries `heading()` semantics, so a screen reader
can jump between sections rather than walking every row.

---

## `SwipeActions` / `SwipeToDismiss`

![SwipeToDismiss](../../../../../app/ui-catalog/screenshots/components/swipetodismiss-light.png)

Actions revealed by a sideways drag. `SwipeActions` reveals buttons;
`SwipeToDismiss` removes the row.

`SwipeToDismiss` **needs an undo**. A dismissal with no way back is a data-loss
bug wearing a gesture; pair it with a [`Toast`](../overlays.md) carrying the
undo.

## `ReorderableItem`

![ReorderableItem](../../../../../app/ui-catalog/screenshots/components/reorderableitem-light.png)

Drag to reorder, with `rememberReorderableState`.

**Reordering happens live, under the finger.** `onMove` fires every time the
dragged row passes another, so the caller's list stays the source of truth
throughout and there is no pending order to reconcile on release.

## `PullToRefresh`

![PullToRefresh](../../../../../app/ui-catalog/screenshots/components/pulltorefresh-light.png)

Pull at the top of a list to reload. The indicator appears past a threshold, so
a list that is merely over-scrolled does not fire a request.

## Gestures are shortcuts, never routes

Swiping, pulling and dragging are invisible, have no keyboard or pointer
equivalent, and are unreachable for anyone who cannot make a sustained drag. Each
of the three above carries its actions a second way and the caller still owes a
third:

- `SwipeActions` puts every action on the row as a **custom accessibility
  action**, so a screen reader can reach it. That covers assistive tech, not a
  sighted mouse user — put the same actions in a menu.
- `ReorderableItem` exposes **move up** and **move down** the same way, since a
  drag is not a gesture a screen reader can perform and reordering with no
  alternative makes a whole feature unreachable.
- `PullToRefresh` needs a refresh action in the toolbar as well.

---

## `LoadMore`

The paging row at the end of a list — idle, loading, failed with a retry, or
exhausted. Four states rather than a spinner, because "there is no more" and
"loading more" look identical if you only draw the second.

## `Modifier.fadingEdges`

**It erases rather than painting over.** `BlendMode.DstOut` in an offscreen
layer, not a gradient of the background colour — the shortcut version fails the
moment anything is behind the list, which over a map is always.

## `Scrollbar`

![Scrollbar](../../../../../app/ui-catalog/screenshots/components/scrollbar-light.png)

Purely an indicator, and hidden from the accessibility tree since it conveys
nothing the list does not already.

**Its visibility follows input modality, not platform.** Under an input that can
hover it is drawn; under touch it is **not drawn at all**, and the component
returns before laying anything out. A permanent scrollbar on a touchscreen is
wrong twice over — not draggable with a finger at any sensible width, and taking
space from the screens with least of it. On desktop and web the opposite holds:
a long list with no scrollbar reads as broken. Pass `alwaysVisible = true` to
override.

Hovering it thickens the thumb and takes it to full opacity, so it is a target
before you have to aim at it.

The full modality table is in
[`accessibility.md`](../accessibility.md#input-modality).
