# `TopBar`

*Also on this page: `HeroImages`.*

Title and actions. Four styles from `TopBarStyle`: `Small`, `Centred`, `Large`
and `Hero`. `Centred` centres the title on the bar where it fits — the title alone
decides, and a subtitle under it is fitted to the same width with an ellipsis —
and where the controls leave it too little room it is laid out exactly as
`Small`, against the leading controls — never centred in what was left over.

<!--sample:TopBarCollapsing-->
```kotlin
val collapse = rememberTopBarCollapseState()

// The bar collapses as the list scrolls: its collapse goes on something
// that holds both, so the bar takes the scroll until it has collapsed and
// the rows move with the finger the whole way.
Column(Modifier.topBarCollapse(collapse)) {
    TopBar(
        style = TopBarStyle.Large,
        onBack = { back() },
        collapseState = collapse,
    ) {
        +"Perth Underground"
        supporting { +"Platform 2" }
    }
    LazyColumn {
        stopRows()
    }
}
```

### `Large`, and how it collapses

`Large` puts the actions on a first line and a large title on a second, and
collapses into a `Small` bar as the content scrolls. **The title travels**: it
rises into the first line, shrinking from the large type to the small as it
goes, and lands where a `Small` bar's title sits — beside the back button when
there is one, at the margin when there is not, so without one it rises straight
up. The bar grows shorter in step, from both lines to one. Over the last part of
the way the small title takes over from the large one, drawn at the same size on
the same baseline, so what shows is the weight easing rather than one title
fading into another; a title the large type wraps onto two lines lets its second
line go early, as its words arrive beside the first. **The subtitle travels
too**, under the title the whole way, and lands under the small title as a
`Small` bar has it: collapsed, the bar is a `Small` bar with its subtitle. It
used to fade out early, and the collapsed bar lost it.

The bar does not own the scroll. `rememberTopBarCollapseState()` is the usual
way to drive it: put `Modifier.topBarCollapse(state)` on something that holds
both the bar and the list, as above, and hand the state to the bar. The bar then
takes the scroll until it has collapsed, and the rows move exactly with the
finger the whole way — a bar that shrank while the list scrolled under it would
carry the rows twice as far as the finger. Scrolling back, the list returns to
its top first and the bar opens after it, and a collapse the finger leaves
part-way finishes to whichever end is nearer.

**A mouse wheel opens it all the way too.** A list at its top declines a wheel's
notch before it becomes a scroll, so nothing listening to the scroll hears it.
`topBarCollapse` hears the notches the content declined, and finishes the bar
the way the wheel turned. The state's `nestedScrollConnection` is still there
for wiring the scroll yourself with `Modifier.nestedScroll`, without that.

To drive it from anything else, pass `collapseProgress` directly: 0 open, 1
collapsed. `collapseProgress(listState)` computes one from a `LazyListState`,
for a bar drawn over its list rather than above it, and
`collapseProgress(scrolled, distance)` from any two numbers.

### `Hero`: a large bar over a picture

<!--sample:TopBarHeroCollapsing-->
```kotlin
val list = rememberLazyListState()
val collapse = rememberTopBarCollapseState(list)

Box {
    // Under the bar, starting where the open bar ends: the list is the
    // only thing that scrolls, and the bar follows it.
    LazyColumn(state = list, contentPadding = PaddingValues(top = collapse.openHeight)) {
        stopRows()
    }
    // The picture runs under the status bar and fades into the bar's colour
    // under the title, and the bar collapses as the rows go up under it.
    TopBar(
        style = TopBarStyle.Hero,
        onBack = { back() },
        image = { StopPhoto(Modifier.fillMaxSize()) },
        collapseState = collapse,
    ) {
        +"Kings Park"
        supporting { +"Fraser Avenue" }
    }
}
```

A `Large` bar with a picture behind it, for the page of a place, a person or an
album: `style = TopBarStyle.Hero` and the picture as `image`. The picture runs to
the window's top, under the status bar, and is `expandedHeight` tall —
`TopBarDefaults.HeroHeight`, 360dp, about half a phone, unless you say — or
taller if the title needs it. The controls stay below the status bar, and the
title rests at the foot, as a `Large` bar's does.

**The picture runs under the title and fades into the bar's colour.** It is
left alone down to a little above the title, then eases into the bar's
background — most of the way by the title's top, and the bar's colour alone by
the subtitle — so the title and subtitle stand over the fade in the bar's own
ink, and the page below starts where the picture ends with no line between
them. A scrim that darkened the picture under the title was tried first, and
read as a dark band that the picture stopped short of.

**The controls carry their own ground.** Over the picture, the back button and
each action sit on a disc of the bar's colour, so a dark glyph is not lost on a
dark photograph. The discs go as the bar collapses.

**It is drawn over its list, and follows the list's scroll.**
`rememberTopBarCollapseState(list)` — or `(scrollState)` for a `verticalScroll`
— reads how far the list has gone, and the list pads its top by the state's
`openHeight`, the bar's height open, status bar included. The list is the only
thing that scrolls: as the rows go up, the bar's foot goes with them, the
picture going as the title comes in, and wherever the finger leaves the list
the bar stays — there is no finishing to the nearer end over a tall picture.
Laid out above its list with `topBarCollapse`, a hero shared each drag with
the list, and on an iPhone the system's stretch, still relaxing from a pull,
sometimes took the start of the next drag before the bar was asked for it —
reported as *"sometimes when i start scrolling up, it doesn't and just does the
overscroll animation"*. That wiring still works, and a `Large` bar keeps it.

Collapsed it is a `Small` bar to the pixel: the title travels into the first
line, the picture rises behind the bar at half the speed and fades out over the
second half of the way.

**Several pictures are a `HeroImages`**, swiped between:

<!--sample:TopBarHeroImages-->
```kotlin
val pictures = rememberCarouselState { photos.size }

// Several pictures, swiped between, with the carousel's dots over the
// fade just above the title.
TopBar(
    style = TopBarStyle.Hero,
    onBack = { back() },
    image = {
        HeroImages(pictures) { page -> StopPhoto(photos[page], Modifier.fillMaxSize()) }
    },
) {
    +"Kings Park"
    supporting { +"Fraser Avenue" }
}
```

It is a [`Carousel`](carousel.md) filling the picture, one page at a time, and
the carousel's [`PageIndicator`](page-indicator.md) at the bottom end of the bar,
over the fade, on the last line of the title block — the subtitle, or the title
without one — which stops short of the dots. A finger swipes anywhere on the picture that is not a button, and a pointer
clicks or drags along the dots. One picture shows no dots. As the bar collapses
the dots go up with the title and are gone a third of the way in, before the
title nears the first line. Like a single picture, it is decoration and hidden
from screen readers, dots and all: a set of photos someone needs to page through
is a `Carousel` in the page, where each page is announced.

**Without an `image`** it is a `Large` bar that opens to the hero's height, so a
page whose picture is sometimes missing keeps its shape. `expandedHeight` works
on `Large` too, as the least it opens to, with the title resting at the foot of
the extra; `Small` and `Centred` ignore it, and only `Hero` draws an `image`.

### Content fading under the bar

`fadeUnder = true` lets content pass *under* the bar fading out, rather than
stopping square at its foot: the bar's ground thins out over its last 12dp, so
content shows faintly through there, and is gone 4dp past the foot — about
where a divider would be. Any style; a collapsing bar's fade follows its foot.
Off by default.

The content has to be under the bar for it to fade there, so put the bar over
it rather than above it in a `Column`. A [`Scaffold`](scaffold.md) does, and
hands the content the bar's height to pad by:

```kotlin
Scaffold(
    topBar = { TopBar(fadeUnder = true) { +"Departures" } },
) { padding ->
    LazyColumn(contentPadding = padding) { … }
}
```

The fade is only drawn: a tap on a row under it reaches the row. A
`showDivider` line at the foot is where the fade starts, so the two rarely want
to be together.

### The back button

`onBack = null` draws no back button. In a page that a navigation host shows,
pass `pageBack()`, which is null exactly when back would not leave the page:

```kotlin
TopBar(onBack = pageBack()) { +"Perth Busport" }
```

Not `backStack.size > 1`. During a back gesture the back stack still holds the
page being swiped away, and the page under it is already on screen, so a button
shown by the stack's size appears on the root page while the gesture runs. See
[Navigation 3](../navigation3.md#a-back-button-from-the-page).

---

## Accessibility

The title is marked `heading()`, so a screen reader can jump straight to it —
which is how a user finds out what screen they are on after navigating.

The back button carries `backLabel`. Action icons need their own
`contentDescription`; the bar cannot invent one.

`windowInsets` defaults to the top edge, so the bar is not under the status bar
on an iOS PWA. A `Large` bar's collapse moves the title and changes nothing about
what is announced: the title is heard once, from the large one, however far the
bar has collapsed.

At high contrast the bar takes the enhanced scheme's colours and adds no edge of
its own — it is the page's ground by default — and the rule `showDivider` draws
under it thickens with the tier. `fadeUnder` draws no fade there: content stops
square at the bar's foot, rather than passing under text through a wash of the
bar's colour. The back button is a ghost
[`IconButton`](icon-button.md), so it gains a ring there.

A `Hero` bar's picture is hidden from screen readers: the title says what the
page is about, and a picture behind a bar is decoration. Put a description of it
in the page if it says something the title does not. At the title's top the
fade leaves 40% of the picture, which gives the title's ink about 5:1 over the
worst of pictures — large type needs 3:1 — and the subtitle is on the bar's
colour alone. At high contrast the fade is a solid band of the bar's colour
under the words, with nothing of the picture behind them, and the discs behind
the controls take an edge. Under reduced motion the
picture does not rise as the bar collapses; it fades where it is.

`windowInsets` is for a bar at the top of the window. **A bar anywhere else — in
a card, a sheet, a demo on a page — passes `WindowInsets(0)`**, or it pads itself
by the status bar's height wherever it is; over a `Hero` picture that put the
buttons well down it.

Reduced motion leaves the large title's collapse as it is: it is driven by the
scroll position rather than by a clock, so the title moves only as the content
under it does. A collapse left part-way lands at once rather than travelling the
rest of the way. The back button keeps its press wash and loses its shrink.
