# Patterns

**How the pieces are meant to go together, and the mistakes that are easy to
make.** Each section is something apps built on the library have got wrong, what
it looks like when they do, and what to write instead. None of them is caught by
the compiler: the wrong version builds, runs, and looks almost right.

## One theme and one host, at the root

<!--sample:PatternRoot-->
```kotlin
// Once, at the root of the app: the theme every component reads, and the
// host every dialog, sheet, menu and toast draws in. Not per screen.
KontourTheme {
    OverlayHost {
        Screen()
    }
}
```

**Not a `KontourTheme` per screen**, and not one round a single component to
change its colours. Each reads the system's settings afresh, so whatever the app
decides at the root — a dark mode of its own, a contrast level, a reskin — does
not reach inside it. Reskin the one at the root: [theming](theming.md) has how.

**Not an `OverlayHost` per screen.** A dialog draws in the nearest host. One
inside a screen draws inside that screen's bounds, under its top bar and its
navigation, and two hosts each dim their own half of the window. Write a
`Dialog` or a `BottomSheet` wherever its state is, deep in a screen: it still
draws in the root's host, over everything. [Overlays](overlays.md) has the rest.

## A screen that scrolls: padding inside, scrollbar on the edge

The commonest mistake in apps on the library, and the easiest to see: a
scrollbar floating a page's margin in from the edge of the screen, and rows that
stop short of the top bar instead of scrolling under it.

```kotlin
// Not this: the margin and the bars' padding are on the list itself, so the
// list is narrower than the screen — its scrollbar sits inside the margin, and
// its rows are clipped at the top bar instead of passing under it.
LazyColumn(
    Modifier.padding(bars).padding(horizontal = 16.dp).scrollbar(list),
    state = list,
) { … }
```

<!--sample:PatternScrollingScreen-->
```kotlin
val list = rememberScrollbarListState()
val direction = LocalLayoutDirection.current
Scaffold(topBar = { TopBar { +"Stops" } }) { bars ->
    // The list fills the screen, edge to edge, so the bar runs down the
    // screen's own edge. The bars' height and the page's margin are the
    // list's *content* padding: the rows start clear of the top bar and the
    // margin, and scroll under the bar and into the margin as they go.
    LazyColumn(
        modifier = Modifier.fillMaxSize().scrollbar(list),
        state = list,
        contentPadding = PaddingValues(
            start = bars.calculateStartPadding(direction) + Theme.spacing.md,
            top = bars.calculateTopPadding(),
            end = bars.calculateEndPadding(direction) + Theme.spacing.md,
            bottom = bars.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
    ) {
        listGroup(stops, key = { it.name }) { stop ->
            item(stop.name, supporting = "${stop.routes} routes", icon = Tabler.Outline.Bus) {
                openStop(stop.name)
            }
        }
    }
}
```

The rules:

- **The scroller is as wide as the screen.** Anything that pads it — the
  `Scaffold`'s padding, a page margin — goes in its `contentPadding`, or for a
  `Column` with `verticalScroll`, in a `padding` *after* the scroll in the chain.
- **`Modifier.scrollbar(state)` goes on the scroller itself**, before its
  `verticalScroll`. Not on a `Box` round it, and not as an item inside it.
- **For a lazy list, `rememberScrollbarListState()`** in place of
  `rememberLazyListState()`, so the bar knows the list's length rather than
  guessing it. [Scrollbar](components/scrollbar.md) has why.
- **`Modifier.fadingEdges` comes after `scrollbar`**, or the fade takes the bar
  with it.

The same holds inside a sheet and a pane: they hand their content the padding
for the same reason the `Scaffold` does.

## Rows come in groups, and the group counts the corners

A group of rows is one object: its first row rounds its top, its last its
bottom, and the rows between meet square. By hand that is index arithmetic, and
getting it wrong draws two rounded rows in the middle of a group, which reads as
a rendering fault.

<!--sample:PatternRows-->
```kotlin
// A titled group: the section lines its title up with the rows' words and
// counts their corners. Each row is pressable as a row — its own press, its
// own role — rather than a row wrapped in `Modifier.clickable`.
ListSection(title = { +"Notifications" }) {
    ListGroup {
        item("Service alerts", supporting = "Delays and diversions", icon = Tabler.Outline.Bell) {
            open("Service alerts")
        }
        item("Favourites", icon = Tabler.Outline.Star) { open("Favourites") }
        split(ListItemSplit.Halves) {
            start(onClick = { open("Trains") }) { +"Trains" }
            end(onClick = { open("Buses") }) { +"Buses" }
        }
    }
}
```

- **`ListGroup`, or `listGroup` in a `LazyColumn`**, rather than passing each
  `ListItem` a `position` worked out from its index. `item { position -> … }`
  takes any row of your own that has a `position`.
- **A row's `onClick`, not `Modifier.clickable` on the row.** The row's own
  press is its press feedback, its focus ring, its role for a screen reader and
  its disabled state; `clickable` round it gives it a second, unstyled press
  and a role nobody chose.
- **No `Card` round a group, and no `Divider` between its rows.** The rows are
  already surfaces, and a group's hairline is the gap between them.
- **A setting that toggles is a `SelectionRow`**, with the control inside it
  showing the state rather than taking it:

<!--sample:PatternRowControl-->
```kotlin
var alerts by remember { mutableStateOf(true) }
// The whole row is the switch: the row toggles it, and the switch only
// shows its state, so it takes no callback of its own.
SelectionRow(selected = alerts, onSelectedChange = { alerts = it }, role = Role.Switch) {
    +"Service alerts"
    supporting { +"Delays and diversions" }
    trailing { Switch(alerts, onCheckedChange = null) }
}
```

A switch with its own callback inside a pressable row is two controls in one
place: the row and the switch each take presses, a screen reader stops on both,
and a press on the switch's edge goes to whichever it lands on.

### A swipe row in a group

`SwipeActions` clips its row, so in a group it needs the row's shape — or the
row slides out with rounded corners where it should meet its neighbours square.

<!--sample:PatternSwipeInGroup-->
```kotlin
// In a group, the swipe clips the row as the group draws it: square where
// it meets a neighbour, round at the group's ends.
stops.forEachIndexed { index, stop ->
    val position = GroupPosition.of(index, stops.size)
    SwipeActions(
        end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, { remove(stop.name) }, Theme.colours.danger.solid)),
        shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
    ) {
        ListItem(onClick = { openStop(stop.name) }, position = position) { +stop.name }
    }
}
```

A swipe is a shortcut for people who know it is there. Every action it offers
should be somewhere else too — a menu, the detail screen.

### Pull to refresh holds the list's state

<!--sample:PatternPullToRefresh-->
```kotlin
// The list's own state: the pull starts only when the list is at its top.
val rows = rememberLazyListState()
PullToRefresh(refreshing = refreshing, onRefresh = { refresh() }, scrollState = rows) {
    LazyColumn(state = rows) {
        items(stops, key = { it.name }) { stop -> ListItem { +stop.name } }
    }
}
```

The same state instance on both: a `rememberLazyListState()` passed to the
refresh and a second one made for the list means the pull cannot tell whether
the list is at its top.

## Numbers come from the theme

```kotlin
// Not this
Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Departures", fontSize = 20.sp, colour = Color(0xFF1B1B1F))
}

// This
Column(Modifier.padding(Theme.spacing.md), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
    Text("Departures", style = Theme.typography.titleMedium)
}
```

A literal is right in one theme and wrong in every other: a colour that reads on
white disappears in dark mode, fails at high contrast, and ignores a reskin.
`Theme.colours`, `Theme.spacing`, `Theme.typography`, `Theme.shapes` and
`Theme.motion` follow all of them. A component's own colour defaults to the
right one for where it sits; pass a colour only to change what it means — a
destructive button, a tone on a tag — and pass the token for that meaning.
[Tokens](tokens.md) lists them.

The same for `Text` and `Icon`: the library's, from `io.kontour.ui.foundation`,
which take the colour of the surface they are on. Compose's `BasicText` does
not, and draws black on a dark surface.

## An icon on its own says what it does

<!--sample:PatternIconOnly-->
```kotlin
// An icon with no words beside it says what it does to a screen reader.
IconButton(onClick = { delete() }, icon = Tabler.Outline.Trash, contentDescription = "Remove favourite")
```

`contentDescription` is required on `IconButton` for that reason. In a slot
beside words — `leading { +Tabler.Outline.Bus }` on a row that says "Perth
Underground" — an icon is decoration, and the row is announced by its words.
Name what it does, "Remove favourite", rather than what it shows, "Trash".

## An overlay is state, not composition

<!--sample:PatternDialog-->
```kotlin
var open by remember { mutableStateOf(false) }
Button(onClick = { open = true }) { +"Rename" }

// Always composed, shown by `visible`: it animates in and out, and takes
// the back gesture while it is open. It draws in the root's `OverlayHost`,
// wherever in the tree it is written.
val name = rememberTextFieldState("Perth Underground")
Dialog(
    visible = open,
    onDismissRequest = { open = false },
    title = { +"Rename favourite" },
    actions = {
        cancel()
        confirm("Save") {
            rename(name.text.toString())
            open = false
        }
    },
) {
    // The label is the field's own: it is what a screen reader reads for it,
    // and what stays when the field has text in it.
    TextField(name, label = "Name")
}
```

- **`visible = open`, not `if (open) Dialog(…)`.** Composed only while open, a
  dialog has no time to leave: it vanishes on the frame it is closed, and its
  state goes with it.
- **`onDismissRequest` closes it.** The back gesture, Escape and a tap on the
  scrim all ask through it; ignore it only for a dialog that must be answered.
- **A field's label is its `label`,** not a `Text` above it. Above it, a screen
  reader reads the field as unnamed, and the words do not move when the field
  has focus.
- **The right overlay for the job.** A toast says something happened; a banner
  says something is true until it is fixed; a dialog asks a question; a sheet
  holds a task. [Overlays](overlays.md) has the table, and the reasons.

## Smooth: what makes an app feel slow

Most of what makes an app on Compose feel sluggish is not a component. It is
the build being judged, or a value that changes every frame being read in the
wrong place.

### Judge the build you ship

A debug build is slow in ways a release build is not, and two of them are easy
to mistake for the library:

- **Android: R8 on, and compiled.** A release build with `isMinifyEnabled =
  false` keeps the source information and trace calls Compose leaves in every
  composable for R8 to strip, and loses the inlining it relies on. And until the
  phone has compiled the app — after its first idle night, or a baseline
  profile — much of it runs interpreted. To judge it as it will settle:

  ```kotlin
  // build.gradle.kts, the app's
  buildTypes {
      release {
          isMinifyEnabled = true
          isShrinkResources = true
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      }
  }
  ```

  then `adb shell cmd package compile -m speed -f <your.package>` after
  installing it.
- **iOS: Release, and not under the debugger.** The Kotlin framework is built
  from Xcode's configuration, so a Debug run is unoptimised Kotlin/Native; and an
  app started from Xcode runs with its debugger and Metal's validation attached.
  Run Release, then open the app from the home screen. On a ProMotion phone,
  `CADisableMinimumFrameDurationOnPhone` in `Info.plist` is what lets it past
  60Hz.

### Read what moves where it is used

A value that changes every frame — a scroll position, a drag, an animation —
recomposes whatever reads it in composition, every frame. Two ways not to.

**Ask the scroll a question, and derive the answer:**

```kotlin
// Not this, in a screen's composable: the read is the screen's, so the whole
// screen recomposes each time the list scrolls past a row.
val atTop = list.firstVisibleItemIndex == 0
```

<!--sample:PatternScrollAnswer-->
```kotlin
val list = rememberScrollbarListState()
// A question about the scroll, answered in a derived state: the screen
// recomposes when the answer changes, not on every pixel the list moves.
val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }
Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().scrollbar(list), state = list) {
        items(stops, key = { it.name }) { stop ->
            ListItem(onClick = { openStop(stop.name) }) { +stop.name }
        }
    }
    ExtendedFloatingActionButton(
        onClick = { plan() },
        icon = Tabler.Outline.Route,
        contentDescription = "Plan a trip",
        expanded = atTop,
        modifier = Modifier.align(Alignment.BottomEnd).padding(Theme.spacing.md),
    ) {
        +"Plan a trip"
    }
}
```

**Or read it in the layout or the draw**, which Compose re-runs on its own
without composing anything — `graphicsLayer { }`, `offset { }`,
`drawBehind { }`, the lambda forms rather than the value forms:

```kotlin
// Not this: a new alpha is a new modifier, so the header recomposes every frame.
HeroHeader(Modifier.alpha(1f - scroll.value / 300f))
```

<!--sample:PatternFadeOnScroll-->
```kotlin
val scroll = rememberScrollState()
Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
    // The value is read inside the layer's block, which Compose re-runs on
    // its own, without composing or measuring anything: the header fades
    // as the page scrolls, and nothing else is asked to change.
    HeroHeader(
        Modifier.graphicsLayer {
            alpha = 1f - (scroll.value / FadeDistancePx).coerceIn(0f, 1f)
        },
    )
    Departures()
}
```

The library's own components take their moving values this way — a top bar's
collapse, a sheet's offset, a pull's distance — so this is about your code
around them.

### A colour that follows state: animate the state, mix the theme's colours

A colour animated towards one of the theme's lags a theme switch. The theme's
new colour is a new target, so the colour sets off after it over its own
150ms, behind a page that has already changed, and recomposes on each frame of
the way:

```kotlin
// Not this: a theme switch changes the target, and the dot fades on its own
// after the page around it has changed.
val fill by animateColorAsState(if (live) Theme.colours.accent.container else Theme.colours.surfaceRaised)
```

Animate the state instead, from 0 to 1, and mix the theme's two colours by it
where it is drawn:

<!--sample:PatternStateColour-->
```kotlin
// The state is what animates, not the colour. The two ends are the theme's
// on this frame, so a theme switch moves them at once and only a change of
// `live` fades; mixed in the draw, so the fade composes nothing.
val lit by animateFloatAsState(if (live) 1f else 0f, Theme.motion.tweenFast())
val off = Theme.colours.surfaceRaised
val on = Theme.colours.accent.container
Box(Modifier.size(12.dp).drawBehind { drawCircle(lerp(off, on, lit)) })
```

A theme switch moves both ends at once, with the theme, on its frames; a change
of `live` fades as before. The library's own controls do this from 0.5.1: a
button, a switch, a tab or a chip changes with the theme, and fades only for a
change of its own.

### An offscreen layer costs its whole area, every frame

`graphicsLayer` with an alpha below 1, a `compositingStrategy` of `Offscreen`, or
a blend mode that erases each draws the content into a buffer of its own first,
then onto the screen. Round a full-height list that is a second full-screen pass
on every frame it scrolls, which a slow phone feels:

```kotlin
// Not this: the whole list drawn offscreen on every frame, to fade 24dp at
// each end.
LazyColumn(
    Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black), blendMode = BlendMode.DstIn)
        },
    state = list,
) { … }
```

<!--sample:PatternFadedList-->
```kotlin
val list = rememberLazyListState()
// The list's own fade: a layer on each 24dp edge, and the rows between
// them drawn straight on, as on a list with no fade at all.
LazyColumn(Modifier.fillMaxSize().fadingEdges(list), state = list) {
    items(stops, key = { it.name }) { stop ->
        ListItem(onClick = { openStop(stop.name) }) { +stop.name }
    }
}
```

`Modifier.fadingEdges` puts a layer on each fade strip, only while there is
something past that edge, and draws the rows between them straight on: a
scrolling frame of a full-height list took 78ms in the software renderer with
the whole list in a layer, and takes 15ms. Keep any layer of your own to what
needs it in the same way, and on only while it does.

### A gesture's pointer block holds no coroutine of its own

Kotlin/Native compiles a gesture's `awaitPointerEventScope` block differently
from the JVM, and the difference has crashed this library on iOS: a launched
coroutine's `Job` kept in a local of the block, across its suspensions, killed
the app on the next move of the finger — `Unexpected receiver type:
StandaloneCoroutine`, on iOS only. Android and the desktop ran it, and every
test on the JVM passed, because the JVM does not check what Kotlin/Native did.

```kotlin
// Not this: the job is a local of the restricted block, alive across
// `awaitPointerEvent`. Fine on the JVM; on iOS, a crash on a later move.
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        var following: Job? = null
        while (true) {
            awaitPointerEvent()
            if (following?.isActive != true) following = scope.launch { withFrameNanos { } }
        }
    }
}
```

Keep anything launched from a gesture on an object made outside the block —
the gesture's state, a `remember`ed holder — and have the block call it:

```kotlin
class FrameFollower(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun follow() {
        if (job?.isActive == true) return
        job = scope.launch { withFrameNanos { } }
    }
}

val frames = remember { FrameFollower(scope) }
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
            frames.follow()
        }
    }
}
```

And test a gesture on an iPhone, not only on the JVM: this is the class of
fault no JVM test can see.

### A lazy list knows its rows

Give `items` a `key`, so a row that moves keeps its state and its animations
rather than being rebuilt in its new place; `listGroup` takes one too. Where
rows are of different kinds — a header, a stop, an advert — give each kind a
`contentType`, so the list reuses a stop for a stop and never a header for one.

## Material stays out

The library is built on Compose Foundation, without Material, and an app on it
should not add Material components beside it. `MaterialTheme` is not provided,
so a Material button draws in Material's default palette, its own type scale and
its own ripple, beside components that follow the app's theme. Look for the
component in [the map](components.md) first, which lists them by what they are
for. Where there is none, build on the library's `Surface`, `Text` and
`Modifier.pressable`, which carry the theme, the press feedback and the
accessibility the rest of the app has.
