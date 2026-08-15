# Overlays

Everything that draws over the screen: dialogs, menus, tooltips, toasts, sheets.

There are **two mechanisms** here and they solve different problems. Reaching for
the wrong one is the mistake this document exists to prevent.

| | [`OverlayHostState`](#the-stack) | [`OverlayQueue`](#the-queue) |
|---|---|---|
| Shape | A layered **stack** | Priority-ordered **mutual exclusion** |
| For | Overlays that legitimately coexist | Overlays that must never coexist |
| Example | A menu open over a sheet | Force-update *or* onboarding *or* a review prompt |
| How many showing | As many as are pushed | Exactly one |

---

## Setup

Install one host at the root of the app. `KontourTheme` does not install one,
because a host has to render somewhere specific in the layout and guessing where
would be worse than asking.

```kotlin
KontourTheme {
    OverlayHost {
        AppRoot()
    }
}
```

Everything else finds it through `LocalOverlayHost`. That local **throws** rather
than defaulting: an overlay silently rendering nowhere is a bug that presents as
a missing feature, and those take a long time to find.

---

## The stack

`OverlayHost` renders its content, then every active overlay above it, ordered by
`OverlayLayer`:

```kotlin
enum class OverlayLayer { Sheet, Dialog, Menu, Tooltip, Toast, Critical }
```

Ordering is by ordinal, not by push order. That is the point: without a declared
ordering, z-order is "whichever happened to be pushed last", which is correct
right up until someone opens a menu from inside a sheet.

Within a layer, push order decides — `sortedBy` is stable.

### Why in-composition rather than platform windows

Material renders each dialog into its own platform window. That is fine on
Android and awkward on the other four targets: window ordering, animation and
dismissal all behave differently per platform, and a menu opened from inside a
sheet has to reason about two windows.

Rendering the stack in-composition means:

- z-ordering is a sort on an enum,
- dimming is decided once for the whole stack,
- and all six targets behave identically.

The cost is that overlays are clipped to the host's bounds, which is why the host
belongs at the root. A platform `Popup` remains available for the cases that
genuinely need a real window — Android IME interaction, chiefly. It is just not
the default.

### Scrims

Each entry declares one:

```kotlin
enum class ScrimStyle {
    None,         // no dimming, pointer events pass through — tooltips, toasts
    Transparent,  // no dimming, pointer events blocked — menus
    Dimmed,       // dimmed and blocking — dialogs, modal sheets
}
```

Every entry that asks for a scrim **gets** one, directly beneath itself, so an
outside tap always dismisses the thing it is under. But only the topmost
`Dimmed` entry actually draws colour.

Both halves of that matter. One shared scrim would mean a menu opened over a
sheet dismisses the *sheet* when tapped outside. Dimming every scrim would mean a
dialog over a sheet darkens the background twice, and a third overlay would sit
on near-black.

### Back and dismissal

```kotlin
if (!overlayHost.dismissTop()) {
    navController.popBackStack()
}
```

`dismissTop()` takes the topmost entry with `dismissOnBack = true` and reports
whether it dismissed anything. Returning `false` lets the caller pass the event
on to navigation rather than swallowing it — a back press that silently does
nothing is worse than one that leaves the screen.

Toasts and tooltips set `dismissOnBack = false`, so a back press while a toast is
up leaves the screen rather than dismissing a confirmation the user was not
interacting with.

### Focus and reading order

While any focus-trapping entry is showing, the content beneath is removed from
the accessibility traversal order and cannot take focus. A screen reader that can
walk into content behind a modal is a modal in name only.

Each overlay gets a `traversalIndex` above the content, in stack order.

---

## The queue

The stack is wrong for a specific, common situation: several unrelated overlays
that all want to appear on launch, of which exactly one may.

The Anyways Android app has six — force-update, onboarding, legal-update,
what's-new, review-prompt, paywall — encoded today as an `ActiveOverlay` enum and
a `when` in `GlobalOverlays.kt`. `OverlayQueue` generalises that, so adding a
seventh does not mean editing a conditional:

```kotlin
val queue = rememberOverlayQueue(sessionCount = prefs.launches)

queue.request(OverlayRequest("force-update", priority = 100)) { updateRequired }
queue.request(OverlayRequest("onboarding", priority = 90)) { !prefs.onboarded }
queue.request(
    OverlayRequest(
        id = "review",
        priority = 10,
        prerequisites = setOf("onboarding"),
        minSessions = 5,
    ),
) { !prefs.reviewed }

when (queue.current?.id) {
    "force-update" -> ForceUpdateSheet(onDismiss = { queue.dismiss("force-update") })
    "onboarding" -> OnboardingSheet(onDismiss = { queue.dismiss("onboarding") })
    "review" -> ReviewPrompt(onDismiss = { queue.dismiss("review") })
}
```

`current` is the highest-priority request whose condition holds, whose
prerequisites have all been **dismissed**, and which has met its session
threshold.

Prerequisites requiring dismissal rather than merely display is deliberate:
otherwise a review prompt could appear over the onboarding it was meant to
follow.

`request()` is safe to call on every composition — re-registering an id replaces
its condition rather than duplicating it.

### Suppression

While any *modal* layer is showing — sheet, dialog, menu, critical — the queue is
suppressed. So a coach mark cannot appear over a dialog, and onboarding cannot
fire while the user is mid-way through a sheet. That is the generalisation of the
Android app's `tooltipBlocker`.

It watches `OverlayHostState.isBusy`, **not** `isEmpty`, and the difference is
load-bearing. Coach marks render into the tooltip layer *through this queue*. A
queue that stopped for anything at all would show a coach mark, immediately count
its own overlay as a reason to suppress, and hide it again on the next frame —
which is exactly the bug the first version had, caught by looking at the rendered
golden.

### Coach marks

`TooltipManager` in the Android app is this class with different field names, so
`Modifier.coachMark` uses the queue rather than carrying its own scheduler:

```kotlin
IconButton(
    icon = Tabler.Outline.Bookmark,
    contentDescription = "Save this trip",
    onClick = ::save,
    modifier = Modifier.coachMark(
        id = "save-trip",
        title = "Save this trip",
        text = "Trips you save show up on the home screen.",
        priority = 40,
        minSessions = 3,
    ),
)
```

Requires an `OverlayQueue` in `LocalOverlayQueue`. Without one the modifier is
inert rather than throwing, because a coach mark is by definition optional — an
app that has not opted into scheduling them should not crash for it.

---

## Anchoring

Menus, popovers and tooltips attach to something. `positionAnchored()` is the one
function that decides where, and it is pure:

```kotlin
internal fun positionAnchored(
    anchor: Rect,
    contentSize: IntSize,
    containerSize: IntSize,
    side: OverlaySide,
    alignment: OverlayAlignment,
    gap: Int,
    margin: Int,
    isRtl: Boolean,
): AnchoredPlacement
```

Two corrections, in order:

1. **Flip.** If the preferred side has no room and the opposite side does, use
   the opposite side. If neither fits, keep whichever has more room.
2. **Shift.** Slide along the other axis until the whole thing is inside the
   container, keeping `margin` clear of the edges.

Shifting is not constrained by the anchor. A menu aligned to the start of a
button in the far corner slides until it fits and ends up no longer aligned with
that button — which is correct. Alignment is a preference; being on screen is
not.

`OverlaySide.Start` and `End` follow the layout direction, so a submenu opens
leftward in an RTL locale without the caller doing anything.

**It is tested directly rather than through a rendered component.** The cases
that break anchored overlays are geometric — a menu in the far corner, an overlay
taller than the window, an RTL submenu, a container smaller than its content —
and each is one line to assert but a fiddly interaction to reproduce on a screen.
`AnchoringTest` covers all of them.

### Getting an anchor

Two modifiers, both reporting `null` once the node detaches — which is what stops
a tooltip pointing at a list item that has scrolled away.

| | |
|---|---|
| `Modifier.anchorBounds { }` | This composable's own bounds |
| `Modifier.parentBounds { }` | Its parent's bounds — how `DropdownMenu` anchors to the control it is declared beside |

`parentBounds` is what makes the familiar API work:

```kotlin
Box {
    IconButton(Tabler.Outline.Dots, "More", onClick = { expanded = true })
    DropdownMenu(expanded, onDismissRequest = { expanded = false }) { … }
}
```

### Arrows

`ArrowSpec` draws a pointer against the resolved side, in the surface's own
colour. It is a sibling of the surface rather than part of it, so it does not
pick up the surface's shadow *or its border* — which is why an arrow-bearing
panel passes `border = false` to `OverlaySurface`. A border would run straight
across the arrow's base and sever it from the panel, turning a speech bubble into
a panel with a torn corner. Arrow-bearing panels lean on the shadow instead.

---

## Picking a component

| If it is… | Use |
|---|---|
| A list of actions | `DropdownMenu` |
| A list of actions, on secondary click or long press | `ContextMenuArea` |
| Arbitrary content attached to a control | `Popover` |
| A decision that must be made before anything else | `Dialog` / `AlertDialog` |
| A decision, awaited from a coroutine | `ConfirmationController.confirm()` |
| The name of a control the user is pointing at | `Modifier.tooltip` |
| A feature the user has not discovered | `Modifier.coachMark` |
| Confirmation of something the user just did | `Toast` |
| Something about the screen the user is on | `Banner` — not a toast |
| Whole-screen, must-not-interrupt work | `LoadingOverlay` |

A few of these are worth stating as rules rather than a table row.

**A toast is for feedback on an action, a banner is about the screen.** A banner
that appears in response to a tap is easy to miss, because the user is looking at
their finger. A toast that carries important information is missed by anyone who
looked away.

**Never put the only copy of something important in a toast**, and never put a
control in one that is not also available elsewhere. An action that vanishes
after four seconds is unusable for anyone who reads slowly.

**Toasts show one at a time.** A stack of them covers the interface they are
reporting on, and by the third nobody is reading. Queueing means each is actually
seen.

**A popover is not a small dialog.** It points at the thing it is about and
leaves the rest of the screen alone. If the content is a decision that must be
made before anything else can happen, it is a dialog.

**`LoadingOverlay` should be rare.** Blocking the whole screen for something that
usually takes 200ms trades a brief wait for a flash of grey, and the flash is
worse. Prefer a `Skeleton` where the result will fill a known shape, an inline
`Spinner` where one region is loading, and a button's own `loading` state where
the user pressed a button.

**Nested menus are worth being sparing with.** Two levels is a category; three is
a filing system, and by then a flat list with section headers is easier to scan.

**A context menu must never be the only route to an action.** Anything in one
needs a visible path too.

---

## Input modality

Overlays are where the differences between a finger, a mouse and a keyboard stop
being cosmetic. See [`accessibility.md`](accessibility.md) for the general
mechanism; the overlay-specific behaviour is:

| | Touch / stylus | Mouse | Keyboard |
|---|---|---|---|
| Tooltip | Long press, times out | Hover after 500ms, until the pointer leaves | On focus, until focus leaves |
| Context menu | Long press | Secondary click | — |
| Submenu | Tap | Hover | Arrow keys |
| Menu focus | Not taken | Not taken | Moved into the menu |

A hover-only tooltip is unreachable on a phone. A long-press-only tooltip never
fires for a mouse. Both are wired, and neither fires for the other's modality.

**A tooltip is not a label.** `Modifier.tooltip` sets no semantics at all,
because anything the user needs in order to understand a control belongs in its
`contentDescription`. The tooltip repeats that for sighted pointer users; a
screen reader that announced both would say it twice. A control whose only
explanation is its tooltip is a control that is unlabelled.

A submenu does **not** close when the pointer leaves it. The pointer has to travel
across the gap to reach the submenu, and closing en route is the classic
nested-menu frustration; the outside-tap scrim closes it instead.

---

## Feedback and motion

Every overlay animates in, and every animation respects `reduceMotion`.

| | |
|---|---|
| Dialog | Scales in from 0.92 — it has no direction to come from; it is not somewhere else on the screen, it is *on top of* the screen |
| Menu | Scales from 0.9 with its transform origin at the corner nearest the anchor, so it reads as unfolding out of the control |
| Popover | Scales from 0.94 |
| Tooltip | Scales from 0.8 on a bouncy spring — one of the few places a flourish costs nothing, since it is transient and nobody is waiting on it |
| Coach mark | Scales from 0.85, same spring |
| Toast | Slides up half its height and fades |

Menus scale from 0.9 rather than from nothing on purpose: a menu springing out of
a point is a lot of movement for something the user opens dozens of times a day.
