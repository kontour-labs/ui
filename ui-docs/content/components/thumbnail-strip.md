# `ThumbnailStrip`

*Also on this page: `ThumbnailStripDefaults`.*

A row of thumbnails under a [`Carousel`](carousel.md), one a page, as a photo
app's filmstrip is: the current page's in the middle and wider than the rest.
Drag the row to scrub through the pages, or press a thumbnail to go to it.

<!--sample:ThumbnailStripBasics-->
```kotlin
val pages = rememberCarouselState { photoCount }

Column {
    Carousel(pages, contentDescription = "Trip photos") { page -> photo(page, Modifier.fillMaxWidth()) }
    // Under the photos: dragged, it scrubs through them; a thumbnail pressed goes to it.
    ThumbnailStrip(pages) { page -> photo(page, Modifier.fillMaxSize()) }
}
```

**It shares the carousel's state.** Give it the `CarouselState` the carousel
has, and the two move together: swipe the photos and the strip slides with
them, a thumbnail widening as its page comes to the middle and narrowing as it
leaves; drag the strip and the photos follow the finger. Let go and it settles
on the nearest page, a little further on if it was let go moving, as a flick
does. Each page passed under the finger is felt as a detent where the platform
has haptics.

**`thumbnail` draws a page's thumbnail**, filling the box it is given — the
same picture as the page, cropped to fit. Each is cut to the theme's smallest
corner. Only the thumbnails on screen are composed, so a strip of thousands of
photos costs what a screenful does.

**`pageDescription`** says what a thumbnail is, "Page 2 of 40" by default, as a
[`PageIndicator`](page-indicator.md)'s dot is. Name the pages for what they are
— "Photo 2 of 40" — where the carousel's own `contentDescription` does not.

**`thumbnailAspectRatio` gives the current thumbnail its photo's own shape**:
each page's width over its height, so a portrait widens less than a landscape
does. A page it says nothing about, `null`, takes `CurrentWidth`. The width is
kept between `ThumbnailWidth` and 112dp, a little over a 7:3 panorama at the
strip's height, so one panorama does not take the strip; a photo narrower than a
thumbnail is shown whole at that width, with black bars at its sides. Moving from
one page to the next, the strip slides from one shape to the other without a
jump.

<!--sample:ThumbnailStripShapes-->
```kotlin
val pages = rememberCarouselState { photos.size }

Column {
    Carousel(pages, contentDescription = "Trip photos") { page -> photo(photos[page], Modifier.fillMaxWidth()) }
    // The current one widens to its photo's own shape.
    ThumbnailStrip(
        pages,
        thumbnailAspectRatio = { page -> photos[page].width.toFloat() / photos[page].height },
    ) { page -> photo(photos[page], Modifier.fillMaxSize()) }
}
```

`ThumbnailStripDefaults` has the sizes: `Height`, a fingertip's 48dp;
`ThumbnailWidth`, how wide each thumbnail beside the current one is;
`CurrentWidth`, how wide the current one grows; and `Gap`. `enabled = false`
shows the strip without letting it be pressed, dragged or stepped.

## Accessibility

A screen reader reaches each thumbnail as one of a group, read by
`pageDescription` and as selected when it is the current page; activating one
goes to its page. A keyboard reaches the strip as one stop, with a focus ring:
the left and right arrows step a page — mirrored for right-to-left reading —
and Home and End go to the first and last. A drag is not the only way to move
it: a press, a key and a screen reader's action each do it too.

Under reduced motion a thumbnail pressed, a key or a screen reader's action
goes to its page at once rather than sliding there, as a carousel's own arrows
do. A drag still follows the finger: the reader is moving it.

At high contrast nothing changes, because the strip draws no ink of its own:
the thumbnails are the app's pictures, the current page is told apart by its
width rather than by a colour, and the focus ring is the tier's.
