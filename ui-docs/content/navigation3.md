# Navigation 3

The library does not navigate. It lays out: `ListDetailPaneScaffold` puts a list
beside its detail when there is room, and `SupportingPaneScaffold` puts a helper
beside the main content or in a sheet over it. Which screen is showing is your
app's business, and on Compose Multiplatform that is increasingly Navigation 3's
back stack.

`io.kontour:ui-nav3` is the small piece in between. It turns a Navigation 3 back
stack into those two layouts, as scene strategies you hand to `NavDisplay`.

```kotlin
dependencies {
    implementation("io.kontour:ui:<version>")
    implementation("io.kontour:ui-nav3:<version>")
}
```

It is a module of its own so that the library takes no navigation dependency at
all. Navigation 3's own adaptive strategy lives in Material's adaptive library,
and this one exists because that cannot be used here: `:ui-nav3`'s build fails
if Material ever reaches its classpath.

**It needs an `OverlayHost` above the `NavDisplay`**, the one the library already
asks for at the root. The supporting pane becomes a `ModalBottomSheet` on a
narrow window, and a sheet draws in the host.

---

## A list and its detail

<!--sample:ListDetailSceneStrategyBasics-->
```kotlin
val backStack = remember { mutableStateListOf<Any>(StopList) }

NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategies = listOf(rememberListDetailSceneStrategy()),
    entryProvider = entryProvider {
        // The list, and what fills the detail pane before anything is
        // picked. On a phone the placeholder is never drawn.
        entry<StopList>(metadata = listPane { Text("Pick a stop") }) {
            ListGroup {
                for (name in listOf("Perth Underground", "Elizabeth Quay")) {
                    item(label = name, onClick = { backStack += StopDetail(name) })
                }
            }
        }
        // Beside the list when there is room; on top of it when not.
        entry<StopDetail>(metadata = detailPane()) { stop -> Text(stop.name) }
    },
)
```

Mark the list's entry with `listPane()` and each detail's with `detailPane()`.
On a window with room for two panes, the top detail sits beside the nearest list
below it; with only the list on top, the detail pane shows the placeholder you
gave `listPane`. On a narrow window the strategy steps aside, and Navigation 3's
own single pane shows the top entry alone — the whole of "one pane on a phone",
with no code of its own.

**Picking another detail is not a new scene.** The scene is keyed on the list's
entry, so moving from one stop to the next changes what is *inside* one scene,
and `NavDisplay` animates only the detail's content. Keyed on the detail, every
selection would slide the whole two-pane layout out and back in.

**It pairs only through details.** A settings screen pushed between the list and
a detail is a screen of its own, and a detail on top of it is shown alone rather
than beside a list the settings screen covers.

**The window is read where the strategy is remembered.** Navigation 3 calculates
scenes outside composition, so a strategy cannot read the window size itself;
`rememberListDetailSceneStrategy` reads it and passes it in, and a window that
crosses 840dp gets a new strategy on the next frame. Pass `twoPane` yourself to
decide by something else.

---

## A main pane and a supporting one

<!--sample:SupportingPaneSceneStrategyBasics-->
```kotlin
val backStack = remember { mutableStateListOf<Any>(RunSummary) }

// Inside the app's `OverlayHost`: on a narrow window the supporting pane
// is a sheet, and a sheet draws in the host.
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategies = listOf(rememberSupportingPaneSceneStrategy()),
    entryProvider = entryProvider {
        entry<RunSummary>(metadata = mainPane()) {
            ListGroup {
                item(label = "Conditions", onClick = { backStack += Conditions })
            }
        }
        entry<Conditions>(metadata = supportingPane()) { Text("24 °C, dry") }
    },
)
```

Mark the main entry with `mainPane()` and the helper with `supportingPane()`, and
push the helper to open it. On a wide window it arrives beside the main content,
in the same scene — the main pane does not move. On a narrow one it rises in a
sheet over the page, and the page underneath stays exactly as it was.

**Every way out pops exactly one entry.** Dragging the sheet down, tapping the
scrim, and back — which reaches the sheet before the page, through the overlay
host — all close the sheet, and the sheet asks the back stack to pop once it has.
Popping the entry yourself closes it too. Either way the sheet finishes leaving
before its content is taken away, so it slides out rather than vanishing.

---

## Using both

`NavDisplay` takes a list of strategies and asks each in turn, and the first
with an answer wins:

```kotlin
sceneStrategies = listOf(
    rememberSupportingPaneSceneStrategy(),
    rememberListDetailSceneStrategy(),
)
```

Each looks only at entries it has been told about, and hands anything else on —
to the next, and finally to Navigation 3's single pane.

---

## Back, and how pages move

<!--sample:PageTransitionStrategyBasics-->
```kotlin
val backStack = remember { mutableStateListOf<Any>(StopList) }

NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategies = listOf(rememberListDetailSceneStrategy()),
    // Every page pushes, pops and follows a back gesture the way its
    // platform's do: predictive back on Android, swipe back on iOS.
    sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
    entryProvider = entryProvider {
        entry<StopList>(metadata = listPane()) {
            ListGroup {
                item(label = "Perth Underground", onClick = { backStack += StopDetail("Perth Underground") })
            }
        }
        entry<StopDetail>(metadata = detailPane()) { stop -> Text(stop.name) }
    },
)
```

`rememberPageTransitionStrategy()` goes in `sceneDecoratorStrategies`, and every
page then moves the way its platform's pages do — `LocalBackStyle` says which:

| | Push and pop | A back gesture |
|---|---|---|
| Android: predictive back | the shared axis, a third of the width and a fade | the page shrinks and drifts with the finger, with the display's corners, and the one beneath fades in |
| iOS: swipe back | the new page slides over the old, which moves a third of the way and dims | the page follows the finger one to one, casting a shadow onto the one sliding in beneath it |

On iOS a sideways pan from **anywhere on the page** goes back too, as it does
from iOS 26 — not only one from the leading edge. It yields: anything on the
page that pans sideways first, a carousel, a row's swipe actions, a slider, a
horizontal list, keeps its pan, and only a pan that goes mostly sideways towards
the trailing edge becomes back. `rememberPageTransitionStrategy(contentSwipe =
false)` leaves only the edge.

A page's own `NavDisplay.transitionSpec { … }` in its entry's metadata still
wins. A `transitionSpec` passed to `NavDisplay` itself does not, for decorated
pages, so pass one or the other.

**Back goes to the innermost thing that can take it**, and it is the same order
on every platform — the Android back gesture, iOS's edge swipe, Escape on the
desktop and the web:

1. the topmost overlay that takes back — a dialog, a sheet;
2. a handler inside it — a stack in a sheet;
3. a pane: the detail beside its list, or the supporting pane beside its main
   one, which the scene closes itself, following the hand, because the scene
   does not change and `NavDisplay` would not animate it;
4. `NavDisplay`, which pops the page;
5. the platform.

**On Android**, the predictive animation needs
`android:enableOnBackInvokedCallback="true"` on the application in the manifest
on Android 13 to 15; an app targeting Android 16 has it already.

---

## What is not here

A third pane — Material's list-detail has an "extra" one — because the library
has no three-pane scaffold to lay it out. Shared-element transitions between
panes, which are `NavDisplay`'s to add and not a layout's.

A back gesture let go finishes on the transition's own remaining curve, so the
speed of a flick does not carry into it, and a predictive page does not follow
the finger up and down as Material's fullest version does. Both would need
`NavDisplay` itself to change. In two panes, a detail being dragged away
uncovers the pane's ground rather than the detail before it, which comes up
once the gesture is let go.
