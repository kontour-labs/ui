# `TopBar`

Title and actions. Four styles from `TopBarStyle`: `Small`, `Centred`, `Large`
and `Hero`. `Centred` centres the title on the bar where it fits — the title alone
decides, and a subtitle under it is fitted to the same width with an ellipsis —
and where the controls leave it too little room it is laid out exactly as
`Small`, against the leading controls — never centred in what was left over.

<!--sample:TopBarCollapsing-->
```kotlin
val collapse = rememberTopBarCollapseState()

// The bar collapses as the list scrolls: its connection goes on something
// that holds both, so the bar takes the scroll until it has collapsed and
// the rows move with the finger the whole way.
Column(Modifier.nestedScroll(collapse.nestedScrollConnection)) {
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
way to drive it: put its `nestedScrollConnection` on something that holds both
the bar and the list, as above, and hand the state to the bar. The bar then
takes the scroll until it has collapsed, and the rows move exactly with the
finger the whole way — a bar that shrank while the list scrolled under it would
carry the rows twice as far as the finger. Scrolling back, the list returns to
its top first and the bar opens after it, and a collapse the finger leaves
part-way finishes to whichever end is nearer.

To drive it from anything else, pass `collapseProgress` directly: 0 open, 1
collapsed. `collapseProgress(listState)` computes one from a `LazyListState`,
for a bar drawn over its list rather than above it, and
`collapseProgress(scrolled, distance)` from any two numbers.

### `Hero`: a large bar over a picture

<!--sample:TopBarHeroCollapsing-->
```kotlin
val collapse = rememberTopBarCollapseState()

Column(Modifier.nestedScroll(collapse.nestedScrollConnection)) {
    // The picture runs under the status bar and fades into the bar's colour
    // under the title, and the bar collapses as a Large one does.
    TopBar(
        style = TopBarStyle.Hero,
        onBack = { back() },
        image = { StopPhoto(Modifier.fillMaxSize()) },
        collapseState = collapse,
    ) {
        +"Kings Park"
        supporting { +"Fraser Avenue" }
    }
    LazyColumn {
        stopRows()
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

**It collapses as `Large` does**, with the same `collapseState` or
`collapseProgress`, and collapsed it is a `Small` bar to the pixel: the title
travels into the first line, the picture rises behind the bar at half the speed
and fades out over the second half of the way.

**Without an `image`** it is a `Large` bar that opens to the hero's height, so a
page whose picture is sometimes missing keeps its shape. `expandedHeight` works
on `Large` too, as the least it opens to, with the title resting at the foot of
the extra; `Small` and `Centred` ignore it, and only `Hero` draws an `image`.

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
under it thickens with the tier. The back button is a ghost
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
