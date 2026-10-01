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
| Android: predictive back | the shared axis, a third of the width and a fade | the page follows the finger one to one, from whichever edge, casting a shadow onto the one sliding in beneath it |
| iOS: swipe back | the new page slides over the old, which moves a third of the way and dims | the page follows the finger one to one, casting a shadow onto the one sliding in beneath it |

A page under a gesture only moves: it does not shrink, fade or round its
corners, on either platform.

On iOS a sideways pan from **anywhere on the page** goes back too, as it does
from iOS 26 — not only one from the leading edge. It yields: anything on the
page that pans sideways first, a carousel, a row's swipe actions, a slider, a
horizontal list, keeps its pan, and only a pan that goes mostly sideways towards
the trailing edge becomes back. `rememberPageTransitionStrategy(contentSwipe =
false)` leaves only the edge. `Modifier.contentSwipeBack` is the same pan for a
page of your own, outside `NavDisplay`.

**From the very edge, back wins; from anywhere else, the control does.** The
edge swipe decides as early as a swipe row does, and before it, so a swipe that
starts in the 20dp band at the leading edge goes back even when it starts on a
row with actions on both sides — the row is left where it was. A control that
takes the press itself keeps it, edge or not: a slider pressed at its very start
still slides. Away from the edge a swipe row owns both directions, including the
side it has no actions on, where it gives under the finger and comes back rather
than letting the page go. The catalog's Back page has a two-sided row, a
one-sided row, a slider and a carousel on its second page to try this on.

**On iOS the library tracks the swipe itself** — the edge swipe as well as the
pan from the content — through the outermost `OverlayHost`, so that letting go
carries on at the finger's speed. The page goes the rest of the way on a spring
that starts at the speed the finger left it, with no pause at the moment it
lifts. The platform's own swipe reports only that the finger lifted, and a
`NavDisplay` finishes from there at the transition's pace.

**It asks for no more effort than UIKit's.** Letting go is decided by where the
page would come to rest if it coasted on at the finger's speed, by UIKit's own
deceleration: past half the width, it goes back. So a swipe at an ordinary pace
a third of the way across goes back, a slow one springs home, and a flick back
stays. Any swipe more sideways than up or down counts, from the content as from
the edge, and the page stays under the finger from the moment it touches down.

**A stack inside a sheet is the sheet's colour.** Each page paints
`LocalContainerColour` — the ground of the `Surface` it is in — and inside an
overlay the page going under fades towards it rather than dimming, so a stack
that does not reach the sheet's edges, with the sheet's grabber above it and its
safe-area padding below, draws no edge against them as it goes back. On the
window's own ground the page beneath dims, as iOS's does.

A page's own `NavDisplay.transitionSpec { … }` in its entry's metadata still
wins. A `transitionSpec` passed to `NavDisplay` itself does not, for decorated
pages, so pass one or the other.

**Back goes to the innermost thing that can take it**, and it is the same order
on every platform — the Android back gesture, iOS's edge swipe, Escape on the
desktop and the web:

1. the topmost overlay that takes back — a dialog, a sheet. On Android it closes
   by its own exit animation; on iOS back closes only a side sheet, swiped
   towards its edge, and otherwise stops there (see
   [Overlays](overlays.md#back-and-dismissal));
2. a handler inside it — a stack in a sheet, on both;
3. a pane: the detail beside its list, or the supporting pane beside its main
   one, which the scene closes itself, following the hand, because the scene
   does not change and `NavDisplay` would not animate it;
4. `NavDisplay`, which pops the page;
5. the platform.

**Without Navigation 3.** The same motion is there for an app that runs its own
`AnimatedContent` between pages. `rememberPageMotion()` gives a `PageMotion` for
the platform's back style, whose `push()`, `pop()` and `predictivePop(edge)` are
the three `ContentTransform`s, and `Modifier.pageEffects(motion, this, isPop)` on
each page draws what a transform cannot: the dim beneath and the shadow above
while one page slides over another. Paint the page's ground before it, in
`LocalContainerColour` — inside a sheet, the dim fades towards that colour rather
than to black, so it never draws an edge where the stack stops. Under reduced
motion the transforms are a crossfade and the modifier draws nothing.

```kotlin
val motion = rememberPageMotion()
AnimatedContent(
    targetState = page,
    transitionSpec = { if (targetState.depth > initialState.depth) motion.push() else motion.pop() },
) { shown ->
    Box(
        Modifier
            .background(LocalContainerColour.current.takeOrElse { Theme.colours.background })
            .pageEffects(motion, this, isPop = { shown.depth < page.depth }),
    ) { Page(shown) }
}
```

**On Android**, the predictive animation needs
`android:enableOnBackInvokedCallback="true"` on the application in the manifest
on Android 13 to 15; an app targeting Android 16 has it already.

**Let go, a back gesture finishes at the finger's speed, on both platforms.** On
iOS the swipe is the library's own. On Android it is the system's, which reports
the gesture as it goes and then only that it was let go — so the outermost
`OverlayHost` takes it through a relay, measures its speed from the system's
frame times, and carries the page the rest of the way at that speed, or briskly
if it was let go slowly, before telling `NavDisplay` it is done. Whether it goes
back is still the system's decision. The relay takes the system's back only while
something inside would answer it, so at the root of an app the system keeps its
own animation home.

---

## What is not here

A third pane — Material's list-detail has an "extra" one — because the library
has no three-pane scaffold to lay it out. Shared-element transitions between
panes, which are `NavDisplay`'s to add and not a layout's.

In two panes, a detail being dragged away uncovers the pane's ground rather
than the detail before it, which comes up once the gesture is let go.
