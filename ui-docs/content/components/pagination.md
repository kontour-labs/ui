# `Pagination`

**It collapses only when collapsing saves room.** A range short enough to list
in full is listed in full — "1 2 … 5" is exactly as wide as "1 2 3 4 5" and
shows two fewer pages — and a gap standing in for a single page is replaced by
that page.

<!--sample:PaginationBasics-->
```kotlin
var page by remember { mutableStateOf(0) }

// `window` is how many numbers sit either side of the current one; the run
// collapses differently at each end so the control never changes width.
Pagination(value = page, pageCount = 40, onValueChange = { page = it })
```

`paginationSlots()` is pure and tested, because the failure mode is a control
that is right in the middle of a range and wrong at both ends, and "page 1 of
40" is the first thing anyone sees.

**`window` is a ceiling, not a promise.** `« 1 … 19 20 21 … 40 »` needs about
410dp once every button reserves Android's 48dp touch target, and a 360dp phone
offers roughly 310 — so the window narrows to what there is room for, down to
first-current-last, which always fits. Where there is room the `window` you ask
for is the `window` you get.

**Reach for [`LoadMore`](load-more.md) instead** in the app. Numbered
pages are a web pattern; a phone list pages by scrolling.

---

## Jumping to a page you cannot see

```kotlin
Pagination(value = page, pageCount = 40, onValueChange = { page = it }, allowJump = true)
```

`allowJump` makes the ellipsis a control: tapping it opens a box to type a page
number into. The gap is the only sensible place for it — it is the part of the
row that stands for the pages you cannot reach by tapping, which is exactly the
set you would be typing a number to get to.

Off by default, because it changes what the ellipsis *is*. With it on the gap
gets a touch target, a focus ring and a name a screen reader reads out; with it
off it is punctuation, announced as nothing. Both are right, for different rows:
a forty-page result set wants it, and a five-page one has no ellipsis to tap.

The number is typed **one-based** — "3" is the third page — because that is what
the row shows and what each button announces. Out of range is clamped rather
than rejected: someone typing 99 into a forty-page set means the end, and an
error message for that is a lecture. A long row has an ellipsis at each end, and
they are separate controls: opening one closes the other.

---

## Accessibility

Each page number is a button with its own name, and previous/next carry
`previousLabel` and `nextLabel` — they are icons and have no other source of one.

The run of numbers collapses at each end so the control never changes width. That
is a layout property with an accessibility consequence worth knowing: the number
of nodes changes as the user pages, so a screen reader's position in the row is
not stable. Give the surrounding page a heading or a live region that says which
page is now shown.

For a feed with no known end, [`LoadMore`](load-more.md) is the component —
pagination implies a countable set.

---

← [Navigation](navigation.md) · [All components](../components.md)
