# Changelog

What changed in each release, newest first. `io.kontour:ui`, `:ui-nav3` and
`:haptics` are released together at one version, so one entry covers all three.

The library is before 1.0, and a release can break its API. When one does, the
break is listed under **Breaking** with what to write instead. There are no
deprecated aliases in between, so the compiler finds every call site. Changes
that still compile but look or behave differently are under **Changed**. Read
those rather than finding them on a device.

**Upgrading an app, as a person or an agent:**

1. Read every entry after the version the app is on, up to and including the one
   it is moving to.
2. Make each **Breaking** change. [Migrating](migrating.md) has each one as
   code, before and after.
3. Check each **Changed** line against the screens that use it.

The version an app is on is the `kontour` entry in its version catalog. See
[Installing](installing.md).

---

## Unreleased

On `main`, and in the next release.

### Added

- **A second axis up on a `Chart`.** `endYAxis` puts one down the end edge,
  and what the content draws in `onEndAxis { }` is measured against it: two
  series in different units, each on its own scale, the end axis's labels on
  the start axis's gridlines. `ChartDataTable` takes it too. See
  [Chart](components/chart.md#two-axes-up).
- **A `Sparkline` moves to new values.** When its values change, the line
  moves from the shape it had to the new one, as a `Chart` does, rather than
  being replaced. Not under reduced motion or `animateData = false`.
- **Steps of different lengths.** `StepProgress` and `CircularStepProgress`
  take `weights`, one a step, for a short step before a long one. Even by
  default, as before.
- **Progress within a step.** `StepProgress` and `CircularStepProgress` take
  `stepProgress`, from 0 to 1 as `LinearProgress`'s `progress` is: the steps
  before the current one full, and the current one filled as far as it has
  got, in place of `working`'s bars. It defaults to `null`, which draws a step
  reached full as before. See [Progress](components/progress.md).
- **[`TextFieldListItem`](components/text-field-list-item.md)**: a list row
  you type into, with a `ListItem`'s ground, corners and `position`. A label at
  the start and the value at the end, or with no label the field from the start
  and its placeholder. A leading icon, a supporting or error line, a trailing
  slot, and `imeChain`. `field` on `ListGroup` and `listGroup` draws one in
  place.
- **`ModalBottomSheet(recedes = false)`**, and `ModalSideSheet`'s: the page
  behind stays where it is, still dimmed, for a screen with a map, a video or
  another platform view behind the sheet, which Android and iOS move on every
  frame of the shrink. See
  [`ModalBottomSheet`](components/modal-bottom-sheet.md#recedes--false-over-a-map-or-a-video).
- **`Table(narrowStickyColumns = true)`**: pinned columns narrow to about two
  thirds of their width over the first stretch of a scroll across, cut and
  faded at their ends, giving the scrolling columns the room, and widen again as
  the table returns to its start.
- **Charts**, in their own module, `io.kontour:ui-charts`. A
  [`Chart`](components/chart.md) is made of marks, `line`, `area`, `bar`,
  `point`, `rectangle` and `rule`, as many and as mixed as it needs, over number,
  date or category axes written by `Theme.formats`. Bars and areas stack, group
  or normalise. Series take their colours from a palette chosen to stay apart
  under colour blindness, and with `showPatterns`, on at the high contrast tier
  and where the reader asks, a dash, a symbol and a hatching each as well. A
  change of data moves from the old chart to the new. A screen reader hears a
  summary and then each place along the chart, and a selection moves by touch,
  pointer, keys or screen reader. `SectorChart` draws a pie or a donut, and
  `Sparkline` a trend the size of a word.
- **`ChartDataTable`**: a chart's numbers as a table, from the same axes and
  content, a row for each place and a column for each series, for the readers
  a picture does not serve. **`SectorChart` takes a selection**, chosen by tap,
  pointer, arrow keys or a screen reader on each sector's own node.
  **`ChartAxis.instant()`** plots moments, ticked on a time zone's clock.
- **`FillPattern` for any surface**: the hatching, dots and cross-hatch a
  chart's series take as a second cue beside colour are in `:ui` now, with
  `Modifier.fillPattern` to draw one over any background and `PatternBrushes`
  for a canvas of an app's own. A `CalendarEvent` takes one as its `pattern`,
  drawn over its block and its bar. Beside the named ones (`Diagonal`, `Dots`,
  `CrossHatch`, `Grid` and the rest), `FillPattern.lines` takes a direction,
  a spacing and a weight, `FillPattern.dots` a spacing, a size and staggered
  rows, and `FillPattern.custom` repeats a tile you draw.
- **`seriesVisibility`** on `Chart` and `SectorChart`, from
  `rememberChartSeriesVisibility()`: each entry in the legend becomes a switch
  that hides its series and shows it again, the axes and stacks closing in on
  what is left.
- **`Modifier.onPressOutside`**: told of a press anywhere but on its node,
  without taking it, for a selection that lets go when the reader taps
  elsewhere. A chart's selection does.
- **`Modifier.tooltip { … }`**: a tooltip's slots, an icon or a painter beside
  its words, from the modifier as well as the component.
- **A range marked on a `Chart`.** `rangeSelection`, from
  `rememberChartRangeSelectionState()`, lets a reader mark a stretch across
  the chart, or with `ChartRangeMode.Box` a box across and up for a scatter
  plot's points: a pointer drags, a finger holds and then drags, and Shift and
  the arrows stretch one from the selected place. A tap or Escape lets go, a
  screen reader hears it with the place, and its words are
  `ChartStrings.selectedRange` and `selectedBox`. Optional, and separate from
  `selection`. See [Chart](components/chart.md#marking-a-range).
- **`Tooltip(style = TooltipStyle.Card)`**: a tooltip drawn as a small card,
  on the raised surface a menu takes, edged and shadowed, standing off what it
  is about with no arrow, for a few lines with figures in them. It opens on the
  other side where it does not fit whole on its own. A chart's tooltip is one.
  See [Tooltip](components/tooltip.md#a-card-to-read-off).
- **A chart's references beside its data**: `region` shades and names a span,
  a launch's fortnight or everything after today; `band` fills a forecast's or
  a day's range between two values at each place; and a `rule` up the plot is
  an event, its label in a pin above the plot, stacked where pins would touch.
  Each is said to a screen reader by its label and where it is. Bars, rectangles
  and sectors are cut with the library's squircle corners.
- **More kinds of chart**: a `bar` from `yStart` to `yEnd` for a range;
  `stem` for dumbbells, lollipops and error bars; `box` for box plots, from
  samples or from five numbers; `ChartStacking.Centred` for a streamgraph; and
  `cell` for a heat map, shaded along `ChartColours.ramp`, with a scale and,
  with patterns on, its numbers written in its cells. Each is said, tabled and
  animated as the others are.
- **[`DocumentTabs`](components/document-tabs.md)**: open documents as a
  browser's or an editor's tabs. The selected one is raised and joined to its
  page by feet that curve into it; the others sit flat, each with a ×, and a
  "+" opens another. Tabs grow in as they open and shrink away as they close,
  share the width down to a minimum and then scroll, and drag: along the strip
  with a detent at each place, or, with `movesBetweenStrips`, pulled off it into
  another strip, as a split view's panes exchange them. Middle click, Delete, the arrow keys and a screen
  reader's actions all work.
- **`KeyValueList(compact = true)`**, for a small container of its own: the
  rows take the style around them and the list is as wide as its rows.
- **`FillPatternDefaults`**, the spacing and weight a pattern is drawn with and
  the order a chart's series take them in, and
  **`bottomCornersOnly()`** and **`trailingCornersOnly()`** beside
  `topCornersOnly()` and `leadingCornersOnly()`, which the charts' bars use.
- **`Theme.icons`**: every glyph the library draws on its own behalf, Tabler's
  unless `KontourTheme(icons = SystemIcons(…))` replaces some. A select's
  chevron, a toast's close, a stepper's minus and plus, a sorted column's arrow
  and a calendar's today were Tabler's and nothing else. The components that
  draw them also take them: `Select`, `MultiSelect` and `Combobox` a `chevron`,
  `ToastHost` a `dismissIcon`, `Pagination` and `EventCalendarDefaults.Header`
  a `previousIcon` and `nextIcon`, `Stepper` a `decrementIcon` and
  `incrementIcon`, `Table` its two sort arrows, `FileField` an `attachIcon` and
  `removeIcon`, and `TopBar`, `SheetHeader` and both side sheets a `backIcon`.
- **`differentiateWithoutColour()`**, from iOS's *Differentiate Without
  Colour* and Android's colour correction, with `LocalDifferentiateWithoutColour`
  for an app's own answer.
- **[Installing](installing.md#the-size-of-an-app) says what an app ships.**
  On the web, iOS and Android, a component or an icon the app never uses is
  not in it at all; on the desktop that needs ProGuard, and the section gives
  the one rule the library needs there.

### Breaking

- **`ComponentDefaults` has `modalSheetMaxWidth`**, 560dp, after
  `sheetMaxWidth`, so a theme built against 0.4.5 recompiles; one that names its
  arguments needs nothing.
- **`ModalBottomSheet` and `ModalSideSheet` have a new parameter**,
  `recedes`, defaulted, after `scrim`, so anything built against 0.4.5
  recompiles. A call that passed `dismissible` by position still compiles and
  sets `recedes` instead, so it names it. See
  [Migrating](migrating.md#modalbottomsheet-has-recedes-after-scrim).
- **`SystemIcons` is a class, read as `Theme.icons`.** `SystemIcons.Close` is
  `Theme.icons.close`, every glyph in lower camel case, and
  `DatePickerDefaults.TodayIcon` is `Theme.icons.today`. `KontourTheme` and
  `ProvideTokens` take `icons`. See
  [Migrating](migrating.md#systemiconsclose-is-themeiconsclose).
- **Glyphs by default where there were none.** `SearchField`'s search and clear
  icons, `NavSearch`'s search icon, the password and secret fields' reveal, and
  `Accordion`'s and `ExpandingListItem`'s chevron default to `Theme.icons`
  rather than to `null`, so each has its control without being given a glyph.
  A side sheet's back is the chevron a top bar's is.
- **`Strings` has new fields**, `labelledValue`, `selectAllRows`,
  `sortAscending`, `sortDescending`, `notSorted`, `showDay`, `moveLeft`,
  `moveRight`, `newTab` and `closeTab`, at the end,
  so an app that builds its own with named arguments needs nothing; one that
  passes them by position, or overrides `Strings.from`'s keys, recompiles. See
  [Migrating](migrating.md#045-to-050).
- **`CalendarEvent` has a new parameter**, `pattern`, defaulted and last, so
  anything built against 0.4.5 recompiles; every call site still compiles as it
  is.
- **`Tooltip` has a new parameter**, `style`, defaulted, before `content`, so
  anything built against 0.4.5 recompiles. A call that passes its content as
  the trailing lambda, as every one in these docs does, needs nothing.
- **`Table` has a new parameter**, `narrowStickyColumns`, defaulted, after
  `stickyColumns`, so anything built against 0.4.5 recompiles. A call that
  names its arguments, as every one in these docs does, needs nothing; one that
  passed `striped` by position still compiles, and narrows the pinned columns
  instead, so it names it. See
  [Migrating](migrating.md#table-has-narrowstickycolumns-after-stickycolumns).

### Fixed

- **A [toast](components/toast.md) waiting behind the front one is never bigger
  than it.** A card pushed back by a newer toast turned into a pill by
  animating its size, so for the length of it a three-line card stood out
  round a one-line "Saved" on every side; and a pill is 72dp wide whatever is
  in front of it, so behind an "OK" it stood out at the sides for as long as
  the two were stacked. The ones behind are measured no bigger than the front
  card now, in the same pass as it, so a card pushed back takes the new one's
  size on the frame it arrives and only its peek shows. Brought back to the
  front, it grows back to its own size.
- **A swiped row stays inside its row.** A
  [`SwipeActions`](components/swipe-actions.md) row slid open drew whatever it
  carried past its own edge, cut off only by what stood round it: the screen on
  a phone, and nothing in a list pane, where a chat swiped open on Pin drew its
  avatar, name and time over the conversation beside the list. The slide is
  clipped to the row's sides now.
- **A sheet opening with the keyboard rises with it.** A field focused as a
  sheet opened made the sheet stand still for the whole of the keyboard's
  slide, then come up after it, while the page behind receded, came forward
  and receded again. The keyboard is the sheet's bottom padding, so the sheet's
  resting place moved on every frame of it, and each move started the sheet's
  slide again from rest. The slide now carries on towards wherever its detent
  has got to, and the page recedes once.
- **The keyboard goes as a sheet or dialog starts to leave.** It went when the
  overlay had finished leaving and its field had gone with it, the length of a
  sheet's slide late. Only when the focus is in the overlay.
- **Dragging a scrollbar collapses a large top bar as a scroll does.** The
  thumb moved its list past the scroll chain a collapsing bar listens on, so
  the bar stayed open over a list dragged to its middle. A drag of the thumb
  now goes through the chain, as a swipe does, and the bar settles when it is
  let go.
- **A submenu opened in place closes as it opened.** It opened on the theme's
  spring and closed on its fastest tween, so a long one collapsed its whole
  height in a fraction of the time it took to grow. It closes on the same
  spring now, and the menu beneath takes taps again as soon as it starts to.
- **A press on a field off its text puts the caret at the nearer end.** A
  press on a text field's leading icon or padding, or on a
  `TextFieldListItem`'s label, focused it with the caret wherever it was last
  left. It now goes to the end of the text nearer the press, and in a
  `TextArea` to the nearer end of the line level with it.
- **A chart legend whose entries hide series is laid out as one whose
  entries do not.** Each entry reserved a finger's height of room, so the
  legend's rows stood twice as far apart once it could hide a series. A touch
  just off an entry still reaches it.
- **A rule's label stays put while its chart's data moves.** Its place, the
  first end of its line with no data under it, was worked out on every frame
  of a move, and it hopped between them; and the rule itself was at its new
  value on axes still easing from the old, so a label could leave the plot
  for the first frames. The place is now chosen from where the data is going,
  and rules and regions move with their axes. A region's label likewise.
- **A sector at 100% is drawn as the whole circle.** The last series shown in
  a `SectorChart` vanished: a whole turn was one arc of 360 degrees, which
  Skia takes as no turn at all. It also has no dividing line across it now,
  since it has no neighbour.
- **A drop list that refuses what is carried over it makes no room for it.**
  A full column moved its items aside for a card it was about to send home.
  It now shows the refusal and keeps its items where they are.
- **A reorderable row carried by its grip is no longer put down on the way.**
  On a touchscreen, a slow carry by the grip dropped the row after a crossing
  or two. The row moves with the finger, so its own long press saw a finger
  held still and came true half a second in, then took the carry from the
  grip. The row's hold now stands aside while the grip has the row.
- **A `ListSection` in a lazy list no longer crashes a row's layout.** To
  inset its header and footer beside rows, a section asked its body for an
  alignment line while measuring, and Compose laid the body out to answer,
  placing its rows inside the section's measure. In a lazy list on Android
  that placed a node Compose had not filed: "LayoutNode … not found in
  RectList". The section now reads the line where it places the body.
- **A list on `rememberScrollbarListState()` no longer crashes an app as it
  leaves.** Leaving a page, a tab or a sheet while its list was still measuring
  its rows for the scrollbar could crash with "LayoutNode should be attached
  to an owner": the rows it held were let go of after the list had gone. Now
  they are forgotten with it, since the list's leaving disposes them, and a
  row of a list that has gone is never let go of.
- **A sheet or dialog opening with the keyboard no longer pushes the app up.**
  A field focused as its overlay arrived asked to be seen while it was still
  low on the screen, nothing in the overlay answered, and on Android the
  window panned everything, sheet and page, to show it. A request from inside
  an overlay now ends at the overlay. A field in a sheet the keyboard covers
  waits until the sheet has stopped, and is then scrolled clear by the
  content's own scroller, into the room the keyboard's padding makes. The rest
  scrolls behind the keyboard.
- **A sheet drag the system takes away is not a throw.** Android sends a
  cancel to each part of a sheet in turn, and the drag could hear it before the
  part that tells a cancel from a lift: a pull the phone took for its own
  gesture was thrown on at the pull's speed, as much as a detent further than
  the finger had taken it. It settles where it is now.
- **An overlay shows content that changed as it opened.** A dialog, sheet,
  popover, menu or tooltip whose content changed in the frame it appeared could
  keep the content it opened with, from a caller in a `Scaffold`'s body or a
  lazy list, which is most screens: a rollback dialog stayed on "Comparing…"
  with its button disabled about one time in three. The host draws an
  overlay's content once more, a frame after it appears, from what it has been
  given since.
- **A colour picker's swatch is named by its hex code as a unit.** In a
  right-to-left language the `#` took the line's direction and the code read
  "E53935#"; it is isolated now, as a badge's count is.
- **What the library says is joined the reader's way.** A key-value row was
  read "label, value" with a comma written into the component, and a stat's
  supporting line and trend, and an activity day's meaning, were joined with an
  English ". ". The row is now `Strings.labelledValue`, and the rest go through
  `Strings.sentences`, as the parts around them already did. A table's
  select-all checkbox has its own `selectAllRows` rather than borrowing the
  text editor's `selectAll`, which some languages word differently.
- **Numbers in other scripts animate.** `RelativeTimeText` looked for its
  number in the sentence as `toString()` writes it, which is not how it is
  written in Arabic-Indic digits or with a thousands separator, so it fell back
  to fading the whole sentence. `AnimatedCounter`'s cells were measured from
  Western digits and clipped wider ones.
- **A calendar's weekdays are whole at large type and in long-name languages.**
  A month and a week went to a weekday's letter only below a fixed column
  width, which held English at 100% and nothing else, so a longer name was cut
  at its column's edge; they measure the widest name now. A year's rows and the
  hours of a day grow with the type too. At 100% in English nothing moves.
- **A schedule's day is named by its date.** Its button had no name, and said
  the date as what pressing it does; it is the date now, and pressing it is
  `Strings.showDay`. Its column and today's ring grow with the type, where at
  200% the number was cut.
- **A sortable column says so.** Its header said nothing until it was the sort,
  so a reader could not tell it from one that sorts nothing, and its tap had no
  label. It is "Not sorted" until it is, and the tap is said as what it will do:
  "sort ascending", or "sort descending" on a column already sorted the other way.
- **A drop zone turning a drag away says so with more than colour.** Taking it
  and refusing it were the same solid edge on a tinted ground, accent or
  danger; refusing now shows `Theme.icons.danger` in place of the zone's icon.
- **An activity calendar says the day its arrows reach.** The grid is one stop
  for the keyboard, and the cursor moving inside it was drawn and never said;
  the day it is on, with its count and its mark, is now the grid's state.
- **A command palette says where its arrows are.** The highlighted command was
  published as selected, so every arrow told a screen reader a choice had been
  made, and nothing said which command the arrows had reached. It is drawn as
  before and said politely from the field, "Saved trips, 2 of 3", or "No
  matching commands".
- **A coachmark is read once.** Its bubble said its title and message, and then
  each again from the texts inside it, and its place in the tour as "2 / 5",
  which a screen reader reads as "two slash five". It is one sentence now,
  ending "2 of 5".
- **A toast waits for a reader who needs it to.** Its time follows Android's
  *Time to take action*, which someone using TalkBack or switch access sets so
  that anything with a control in it waits for them, and its clock stops while
  keyboard focus is on its action or its close, as it does under a finger or a
  pointer.
- **An arrow at the end of its bubble meets it.** A tooltip's or a popover's
  arrow was kept one arrow's width from its panel's ends, and the panel's
  squircle corner curves away for further than that, so an arrow pushed to the
  end — a chart's tooltip at the edge of its plot — floated off its bubble. It
  is kept past the corner's whole curve now.
- **A tooltip never takes a tap.** Its bubble scrolls, for text too long for
  its side at a large type size, and the scroller took every tap on the bubble
  whether it had anywhere to scroll or not, so a tap on whatever was under a
  [`Tooltip`](components/tooltip.md) went nowhere. It scrolls, and takes the
  pointer, only while its text overflows.
- **A side sheet comes in from its own side right to left.** An end sheet in a
  right-to-left layout came in at the start, across the content it was meant
  to sit beside, rounded on the wrong edge and sliding the wrong way: its edge
  was worked out for the direction and then flipped again by the alignment
  and the slide. [`SideSheet`](components/side-sheet.md) and
  [`ModalSideSheet`](components/modal-side-sheet.md) are placed by the
  physical edge now.
- **A dialog's buttons stack rather than break a word.** At large type in a
  narrow window, "Cancel" came out "Cance / l" and "Remove" "Remo / ve" in
  their halves of the row. Where one of them cannot fit its longest word, the
  buttons are a column, the answer first and the way out last; a label that
  wraps between its words stays in the row as before.
- **A selection is drawn over code and highlights.** Dragged across a sentence
  with `code` in it, a selection lit up the words either side and left the code
  looking unselected, though it was selected and copied. A span's background
  is now painted behind the selection rather than over it, for
  [`richText`](components/text.md)'s `code` and `highlight` and for any
  background a caller gives a span.

### Changed

- **An [event calendar](components/event-calendar.md)'s three days swipe a day
  at a time.** Asked for: *"snap to any starting day, not just days that land on
  a multiple of 3"*. Let go, they come to rest on the day nearest where the
  finger left them, where a swipe used to turn a page of three or nothing; a
  flick carries them on as a fling would, at least a day and at most three. The
  title, `visibleDates` and the state's `date` go with them, and the arrows
  still step three from there. The week and the day page as they did.
- **A chart's tooltip is a card beside the selected place.** The place,
  quietly, over a row for each series with its value at the card's end, in
  figures of one width so that they line up, and a stack's total under a rule.
  It stands to the end of the place, or to its start where it does not fit,
  rather than over the bars and the line it reads out. Among bars the selected
  place is washed behind them, its whole width, where a line through them cut
  them in two. A `SectorChart`'s stands over the pointer or the finger that
  chose its sector, and moves with a pointer, rather than over the sector's
  middle, and fades as the pointer leaves the circle rather than its box. A
  touch's tooltip stays while the finger is down and goes four seconds after it
  lifts, where it went four seconds into a long drag; and a pointer that lands
  on the tooltip chooses the place under it, where the chart let go and the
  tooltip blinked out and back. `tooltip` still replaces what it says; the
  energy app's hours show one of their own.
- **A chart moving between ways of drawing its data keeps its marks
  together.** A stack rises as one from its foot, where each part rose from
  its own and stood in the air over the part under it. Bars leaving a stack
  for side by side narrow and move apart as they come down, where they took
  their new width at once; a stack turned to shares takes its percent axis at
  once and each part moves to its share, where its labels sat heaped at the
  foot of the plot for most of the move; and bars turned to a line or an area
  fade into it, the line going from their tops, where they sank to the floor
  as it rose. Into a stack, bars side by side rise to their places first and
  then close up, and out of one they part first and then come down, so no part
  hangs over the one it lands on. A bar's end is rounded by how much of it
  another bar covers in each frame, so corners ease square as parts meet and
  round as they part, rather than snapping when the move stops. Along a date or number axis, a day that has gone goes
  down as the axis closes in, rather than vanishing.
- **Side sheets take the panel's corner, 28dp**, not a sheet's 34. Facing the
  content in the middle of the window, a side sheet meets no device corner, and
  at 34dp its smoothed top corner crowded the title and the back button in it.
  `Theme.shapes.sideSheet` is `large.leadingCornersOnly()`; pass `shape` for the
  old corner.
- **A top bar opens as far as a wheel or a trackpad went** at a list's top,
  rather than all the way at the first piece of scroll the list declined, which
  made a slow trackpad scroll jump.
- **A modal bottom sheet holds the page back the whole way once it is open**,
  at any detent, as a side sheet does. Resting at `Half` or a peek it pushed the
  page back and dimmed it only as far as it showed, with a corner part of the
  way between the screen's and the card's.
- **A modal bottom sheet is wider on a wide window**: it stops at
  `ComponentDefaults.modalSheetMaxWidth`, 560dp, rather than the 480 a
  `BottomSheet` beside the page keeps, at which it stood in the middle of a
  tablet as a narrow strip. Set it to `sheetMaxWidth`'s for the old width.
- **A receding page costs a modal sheet much less to draw.** The black band
  round the page and its rounded corners were one screen-sized path, cut and
  filled afresh on every frame the sheet arrived, left or was dragged. They are
  four rectangles and four corner pieces now, cut once per step of the corner's
  ramp and drawn under each frame's scale. The picture is the same.
- **Swipe actions let go of a trackpad's swipe as it ends**: carried past the
  point of no return by a scroll, the row runs its action at once, and the
  scroll a trackpad coasts on after the fingers lift no longer holds it there or
  cancels the settle under way. And a sideways scroll keeps the whole of
  itself: its ups and downs no longer scroll the list out from under the row
  being swiped, until it has been let go of.
- **A mouse on a reorderable row decides before it carries**: it goes a few dp
  first, and mostly up or down, as a swipe decides. At a mouse's own slop a
  pixel of jitter in a click picked the row up and lost the click, and a drag
  sideways lifted the row before the swipe inside it could take it. A grip still
  takes the mouse at once, and a mouse no longer waits for a hold because a
  touch was the last input used.
- **A press on a destination or a tab no longer leans the marker toward it.**
  A nav bar's, a nav rail's and a tab bar's marker waits for the tap and then
  goes; switched between all day, its twitch toward each before it went looked
  eager. A drag still leans it as it goes, and a segmented control's thumb
  still leans toward a segment pressed.
- **A mouse drags a nav rail's or a tab bar's marker when the row scrolls**,
  as it does when the row fits. A mouse scrolls the row with its wheel, so the
  drag has nothing to take from; a finger's drag along a row that scrolls
  still scrolls it.
- **A chart's tooltip goes as the mouse leaves the chart**, and the place it
  rested on with it, rather than staying until a scroll moved the chart.
- **An index scroller shows its bubble for the letter a mouse rests on**,
  before it is clicked, and moves nothing until it is.
- **Charts' first data rises into place**: a `Chart` that had no data, loading
  say, raises its marks from their baseline as its first data arrives, as one
  with data from the start does; they used to appear at once and fall from above
  the plot as its axes opened. A series added later rises too, a `Sparkline`
  draws itself in when its first values come, and under reduced motion each
  fades in instead.
- **A drop list laid out as a row says "Move left" and "Move right"** to a
  screen reader, the other way round right to left, rather than a column's
  "Move up" and "Move down".
- **A mouse picks things up with its own button only.** A right press is a
  context menu's and a middle one closes a tab; either dragged a row or a card
  away with it.
- **A lone coach mark is a tour's bubble.** `Modifier.coachmark` and a tour's
  steps drew their bubbles apart, and only the tour's way on was filled in the
  bubble's ink, where a ghost button on the accent read as words. "Got it" is
  filled now, and a tour's step scrolls when its words run past the room beside
  what it points at, as a lone mark's did.
- **An app on the web is about 18 KB smaller.** Six small regular expressions
  in the theme's formats, plurals and keyboard handling brought a
  regular-expression engine into every web app. Each is a loop now, with the
  same answers. `KontourTheme` alone costs 18,533 gzip bytes less, measured by
  the [`theme` probe](https://github.com/kontour-labs/ui/tree/main/probes).

---

## 0.4.5 — 16 October 2026

Smoother on iOS: clips and shadows on Skia's fast path, a scaffold that does
not recompose its screen as the keyboard or a top bar moves, and a floating
sheet that no longer masks itself again on every frame it is dragged. A sheet
caught on its way back holds where it was caught; a floating sheet keeps the
same margin at its foot as at its sides; a large or hero top bar no longer has
its page slide up under it on iOS. Selects draw their options with a slot, a
combobox's text can be selected, list sections line up with what they hold,
the default schemes' selected tint stands further off the page, a phone shows
a date-time picker's date and time together, and an event calendar's month
scrolls freely under a mouse's wheel.

And a round of fixes from an audit of the whole library: a modal sheet that may
not be hidden no longer hangs the app as it closes, a list beside a detail and
the screen inside a navigation suite keep their state, a control keeps its
press feedback through a change of input, sliders and ratings land where they
should, the text menu respects a field's rules, and a good deal less is
composed, measured or animated on every frame — a list in a floating sheet
above all, which was measured again on every frame the sheet moved.

Then a round from testing on phones: an event calendar's carried events keep
their shape and bring the rest of themselves along, a title runs on across a
swipe once rather than twice, the year flashes today, and an evening that ends
the next morning reads "22:30 – 06:15⁺¹". Long selects compose only what they
show, a drag on an open list stays in it, a rail's or a tab bar's marker drags
wherever its row fits, a pull to refresh knows where its list is, a dialog's
blur is cheaper to draw, and Android's back gesture keeps the screen's own
frame rate.

And after that: a [`SplitListItem`](components/split-list-item.md), two rows
side by side as one, with `split` in a `ListGroup`; wheels turned from the
keyboard and by a screen reader; menus that take the arrows and typing; a swipe
row's full-swipe action growing as the others give way; a dropped card landing
as it settles; and smoother curved edges and crisper grid lines on the web, the
desktop and iOS.

Last, from measuring the gallery on two phones: a busy page is no longer
composed whole while it arrives, a pull to refresh comes down with the finger,
an iOS fling stops where it stops being seen and a slow release does not
fling, and the patterns guide says what makes an app feel slow.

**It is the first release on Kontour's own Maven repository,
`https://maven.kontour.io`, which needs no token** — the library was on GitHub
Packages, which needed one even to read — and the documentation is public, with
a new [patterns](patterns.md) guide to the mistakes apps on the library tend to
make.

The repository moves, `PullToRefresh` takes its list's scroll state,
`NavBarMarkerDrag` is `NavMarkerDrag`, and `Select`, `MultiSelect`, `Combobox`,
`ListSection` and `Strings` have new parameters — see **Breaking** and
[Migrating](migrating.md). A scrollbar's list state measures its rows only
once the list is still, and on iOS a fling ends sooner and a slow release does
not fling — see **Changed**.

### Breaking

- **The library and its icons are on `https://maven.kontour.io`**, Kontour's own
  Maven repository, and no longer on GitHub Packages. It is public: no token, no
  `gpr.user`/`gpr.key`, nothing to set up in CI. An app swaps the
  `maven.pkg.github.com` repository and its credentials for
  `maven("https://maven.kontour.io")`, as the only source of `io.kontour` —
  [Installing](installing.md#the-repositories) and [Migrating](migrating.md#044-to-045)
  have the lines. Versions up to 0.4.4 stay on GitHub Packages only.
- **`Select`, `MultiSelect`, `Combobox` and `ListSection` have new
  parameters** — `valueContent` and `optionContent` on the three selects,
  `textInset` on the section — so anything built against 0.4.4 has to be
  recompiled. They are defaulted and come last, so every call site still
  compiles as it is.
- **`Strings` has new fields**, each defaulted: `inRange`, which a date range
  picker says of a day inside its range; `time`, a date-time picker's time row;
  and `endDayOffset`, `endDaysLater` and `untilTime`, for an event that ends on
  a later day than it starts. A `Strings(…)` written with its arguments by
  position, rather than by name, has to name them.
- **[`PullToRefresh`](components/pull-to-refresh.md) takes the content's
  `scrollState`**, third, after `onRefresh`: the `LazyListState`,
  `LazyGridState` or `ScrollState` of the list it holds, which says whether the
  list is at its top. Required, and `null` for content that does not scroll or
  whose state cannot be had, so leaving it out is a choice rather than an
  omission. Without it a mouse drag halfway down a list moved by its scrollbar,
  a key, `animateScrollToItem` or a restored position pulled to refresh —
  nothing Compose makes reachable says where a child is scrolled to.
- **`NavBarMarkerDrag` is `NavMarkerDrag`**, now taken by
  [`NavRail`](components/nav-rail.md), `ModalNavRail` and
  [`TabBar`](components/tab-bar.md) as well as `NavBar`; and
  [`NavigationSuiteScaffold`](components/navigation-suite-scaffold.md)'s
  `barMarkerDrag` is `markerDrag`, and reaches the rail and the modal rail too.

### Changed

- **`rememberScrollbarListState` measures its rows once the list is still.**
  It composed every row ahead from the first frame, each one whole, so a page
  of heavy rows — the gallery's date and time page — was composed in full while
  it arrived, and dropped frames on both phones. It now waits until the list
  has been still for 300ms, stops while it scrolls, and takes a row a frame.
  The thumb is exact a moment after the list comes to rest rather than within
  its first frames; until then it is estimated, as on a plain list.
- **On iOS, a slow release is not a fling, and a fling ends where it stops
  being seen.** The library's iOS fling flung every release however slow, and
  ran until 1px/s, two seconds of sub-pixel creep after a list looked still. A
  list's overscroll relaxes only when its fling returns, and a fling on an
  inner list at its top goes on moving the page round it, so a list pulled at
  its top and let go stayed stretched — for 4.2s on the gallery's Frames page,
  and for good if a touch cancelled the fling first. A release under 500px/s
  is handed back at once, as Compose's own iOS fling does, and a fling ends at
  30px/s, which gives up 15px of a throw.
- **The icons are Tabler 3.49.0.1**, from 3.49.0: the same icons,
  `io.kontour:tabler-icons-outline` and `-filled`, re-released by
  `kontour-labs/tabler` to `maven.kontour.io`.
- **Curved edges are smoother, and grid lines crisp.** Two causes of the rough
  look reported on the web, the desktop and iOS, where nothing about a
  component's shape or size changes:
  - Rows, buttons, chips, fields, menu rows, tabs and the rest clipped their
    content by the squircle's path and then filled the same path, so each edge
    pixel was smoothed twice and came out thinner and harder than one fill.
    They clip by the rounded rectangle a `Surface` already used, which sits
    within a fraction of a pixel of the squircle, and the squircle is drawn as
    the edge — once, smoothly, and cheaper to clip.
  - The calendar's day and hour lines and a table's column lines were centred
    on a pixel boundary, which draws a 1dp line as two pale pixels; and at a
    density of 1.25 or 1.5 a 1dp line is a fraction of a pixel too. They are
    whole pixels now, on whole pixels.
- **A [dropped](drag-and-drop.md) card lands as it settles into its place**, not
  a fraction of a second after. The drop waited for the card's flight to finish
  on a spring that ran on to a hundredth of a pixel, and for its shadow to come
  down: on a short flight, `onDrop` ran about a sixth of a second after the eye
  had seen the card arrive. The flight now ends within half a pixel of its
  place and the drop happens there, the shadow finishing beside it.
- **A [bottom sheet](components/bottom-sheet.md) let go and caught again on its
  way back is held where the finger caught it**, and follows the finger from
  there. A full-height sheet pulled down, let go and pulled again before it was
  back went to the top anyway: a throw of the sheet's surface was not counted as
  the sheet moving, so the new finger had to travel the drag's slop first, and
  the throw carried the sheet home meanwhile. A finger laid on a moving sheet
  now holds it at once, and once the finger moves, the rest of that gesture is
  the sheet's, not a list's inside it; a press that does not move is left to
  whatever it is on.
  `SheetState.isMoving` is true through a throw of the sheet itself too.
- **A floating [bottom sheet](components/bottom-sheet.md) keeps the same margin
  at its foot as at its sides**, 12dp by default: from the window's edge under
  iOS's home indicator or Android's gesture handle, which are drawn over the
  app, and above Android's three buttons or the keyboard rather than on them. It
  kept the larger of its margin and the window's inset, so on an iPhone it stood
  34dp off the bottom against 12 at the sides, and over three buttons it sat on
  the bar with no margin at all. Its content is handed the part of the bottom
  safe area it now stands in — 22dp on an iPhone, 12 over a gesture handle,
  none over three buttons or the keyboard — where it was handed none.
- **A floating [bottom sheet](components/bottom-sheet.md) is cheaper to drag and
  to expand to the edges, on iOS most of all.** Its surface clipped its content
  to the squircle's path, and a sheet's height changes on every frame it is
  dragged, so the clip's mask was made again on every frame of a drag, over most
  of the window. It clips to a rounded rectangle with the same corners now, which
  Skia works out rather than masks; the ground and the edge are still the
  squircle, and the two differ by under half a dp at the corner.
- **A large or hero [top bar](components/top-bar.md) no longer has its page
  slide up under it on iOS.** On a page only just long enough to scroll, a push
  past its end went to iOS's rubber band, which moves the whole content: up
  under a bar that had not finished collapsing, or, for a hero following its
  list, under a bar left where the scroll had stopped. With `topBarCollapse` on
  the content, the bar takes that push itself, through a band as stiff as the
  system's, and the two come back together when the finger lifts. A hero that
  follows its list gets this with `Modifier.topBarCollapse(collapse)` on the
  list itself, ahead of its `verticalScroll`; without it the bar follows the
  list as before. A large bar over a page that cannot scroll at all opens again
  when the finger lifts, where it could be left collapsed over nothing. Android
  and the desktop are unchanged.
- **An open [combobox](components/combobox.md)'s field is a text box like any
  other.** A click or a tap in what has been typed moves the caret, a
  double-click or a double-tap selects a word, and a drag selects a run, without
  closing the list. The first press used to land on the list's scrim, which
  closed the list and threw away what had been typed. A press on the field
  around the text puts the caret in it; the chevron closes the list. A selection
  there is in the field's selection colour, and a right-click offers Copy and
  Cut. Its hint sits over the empty input, where the caret is, rather than at
  the far end of the field against the chevron.
- **A [list section](components/list-section.md)'s title and footer line up with
  what they sit beside.** Over rows they start 16dp in, where a row's content
  starts, as before. Over anything else — a paragraph, a field, a control, a
  card — they start at its edge. They were 16dp in whatever the section held, so
  over a paragraph the title hung in from a column that was not there. The title
  follows whatever is first in the section and the footer whatever is last; a
  row in `SwipeActions`, an open `DisclosureListItem` and an `ExpandingListItem`
  count as rows, and a row inside a padded card does not.
- **The default schemes' selected tint stands further off the page.**
  `accent.container` is `#E9D7FD` in light and `#3D2E50` in dark: 1.35:1 and
  1.52:1 from the page where it was 1.21 and 1.35, and 1.25 and 1.43 from the
  rows a selected row sits among, where it was 1.12 and 1.26. Selected chips,
  rows, pages, table rows and navigation items, and a focused filled field, all
  show it. The demo GTurbo scheme's is 1.40:1 from its page, from 1.11.
- **Dark's secondary text and control edges are lighter**, which is what made
  room for the tint. `contentMuted` is `#B2AABB` and `contentSubtle` `#A49CAC`,
  5.17:1 on a raised ground where it was 4.61; `outlineStrong`, and with it
  `inactive`, is `#847C8D`, 3.42:1 on a raised ground where it was 3.06. A
  switch's off track is 3.58:1 from its on track, where it was 4.01. In light,
  `outlineStrong` and `inactive` are `#7C7C7C` and `contentSubtle` `#616161`.
- **A [`DateTimePicker`](components/date-time-picker.md) shows the date and
  the time together at every width.** Side by side wherever a calendar of 40dp
  days, 8dp and the wheels as wide as they measure fit across — 390dp on a
  24-hour clock at the default type size, 453dp on a 12-hour one — and
  narrower, as UIKit's inline picker does: the calendar, and under it a row
  that says **Time** and the chosen time in a pill. Pressed, the pill opens the
  wheels, with **Now**, in a popover anchored to it. It replaces the segmented
  control that showed one half at a time below 560dp, which on a 12-hour clock
  was every phone. Only the width given decides, never the height. The pill is
  a button named "Time" that says its time; opened from the keyboard, the focus
  goes to the hour wheel and back to the pill as it closes. The wheels open into an
  `OverlayHost`, as a menu does.
- **A `DateTimePicker`'s wheels are narrower**: the hour and minute wheels as
  wide as their widest number with 12dp either side, about 48dp from 72, and
  the am/pm wheel as wide as its word. A `TimePicker`'s are as they were.
- **A date picker is 8dp shorter**: its header has no padding of its own past
  its arrows' touch targets, on `DatePicker`, `DateRangePicker` and
  `DateTimePicker` — 310dp on a phone, from 318. A `DateTimePicker`'s calendar
  and wheels are 8dp apart side by side, from 24.
- **An [event calendar](components/event-calendar.md)'s month scrolls freely
  under a mouse's wheel or a trackpad, and stays where it stops**, as a
  desktop's calendars do. Each turn of the wheel was slid on to the nearest week
  150ms after it stopped, which for a turn under half a week was back where it
  had been, so a slowly turned wheel went nowhere and a quick one lurched. A
  finger's swipe or fling still settles with a week at the top.
- **A [`Scaffold`](components/scaffold.md)'s padding is read in its layout, not
  its composition**, so the keyboard sliding up, or a large top bar collapsing
  over the list, re-measures the content rather than recomposing the whole
  screen on every frame. The padding handed to `content` is one object for the
  scaffold's life whose sides change; content that reads them in composition is
  recomposed a frame after they do.
- **`KontourIndication`'s `hoverEnabled` means "may draw a hover"**, and the
  hover still waits for an input that can hover. It used to draw a hover under
  any input; `kontourIndication()` behaves as before.
- **`WindowSizeClass.widthDp` and `heightDp` are documented as what they always
  were**: the window's size when its class last changed, not as it is now.
- **`dismissible` is read when the reader tries to dismiss, not when the overlay
  opened**, on [`ModalBottomSheet`](components/modal-bottom-sheet.md),
  [`Dialog`](components/dialog.md), [`ModalSideSheet`](components/modal-side-sheet.md)
  and [`CommandPalette`](components/command-palette.md). A sheet given
  `dismissible = !form.dirty` could still be tapped or backed away after typing,
  and the typing went with it; the other way round, it never became dismissible.
- **A sheet's [drag handle](components/sheets.md) offers only the actions that
  would do something**: expand where there is a taller detent, collapse where
  there is a shorter one, and dismiss where the user may put the sheet away. The
  default modal sheet offered two that did nothing, and no way to dismiss it.
- **A timed event that runs into a later day reads as a flight or a shift
  does.** A [schedule](components/event-calendar.md) shows it on each day it is
  on: "09:00 – 17:00⁺²" on its first, the end raised by the days it is on;
  "All day" between; and "Until 17:00" on its last. It said "All day" on every
  day, and an overnight event under a day long said its times on both. An end
  at the midnight that closes the first day is that day's: "18:00 – 00:00". A
  block in the hours shows "22:30 – 06:15⁺¹" too, and a screen reader hears
  "09:00 – 17:00, 2 days later", never "plus two".
- **An [event calendar](components/event-calendar.md)'s year flashes today**
  when the header's today, or `state.showToday()`, is pressed, as its other
  views do: today's month is brought in first, even from elsewhere in the same
  year, which it was not, and flashes once it is there.
- **A carried event keeps its shape.** The copy a carry draws is square and out
  to the edge where the event runs on into another day, week or past midnight,
  and rounded only where it really ends, as it was before it was picked up; it
  was a capsule drawn in from both edges. Carried sideways in a day, three days,
  a week or a month, the rest of the event comes with it, its far end sliding
  into view, rather than the copy pulling away from the edge.
- **An all-day bar that runs on into the next page shows its title once as the
  pages slide.** The title stays where it is on the screen, and goes with the
  bar once the bar's end reaches it; both pages drew one each.
- **An event calendar's bar is on the same line on every day, three days and
  week it is in**, so swiped mid-way its two pieces meet. Each page packed its
  own bars, so a bar under one that had started before it was a line lower on
  one page than the next. The bars are packed across the whole calendar now,
  earliest first, each on the first line free on its first day — so now and
  then a page has an empty line above a bar run on from a busier day. The same
  holds in a month's weeks and a condensed list, and a bar past what a day has
  room for goes up to a free line rather than into the "+N".
- **An event calendar's all-day strip goes from one page's height to the next
  as far as a swipe has gone**, where it jumped to the taller page as a finger
  set off and back as the page came to rest; and a condensed page swiped in
  opens its list where the page beside it has scrolled to, so their lines are
  level, where it always opened at the top.
- **A [rail](components/nav-rail.md)'s marker can be dragged while the rail has
  nothing to scroll, and a [tab bar](components/tab-bar.md)'s while its tabs
  fit** — always, for a fixed bar — as a nav bar's could. A row that overflows
  scrolls as before. Asked as each drag begins, so a window resized or a tab
  added changes it. `markerDrag = NavMarkerDrag.Off` turns it off; a modal rail
  closes once a drag has chosen.
- **A [select](components/select.md), [multi-select](components/multi-select.md)
  or [combobox](components/combobox.md) of more than thirty options composes the
  ones it shows.** A select of 300 composed and measured every row as it opened
  — about 125ms on the desktop, where ten take 20 — and now takes about 30. It
  is as wide as before, settled as it opens from its options' words and not
  changed by scrolling; one whose options `optionContent` draws opens as wide as
  a menu goes. A screen reader is told how many options there are and where each
  is. Thirty or fewer are drawn as they were.
- **A wheel turns from the keyboard and from a screen reader.** A
  [`WheelPicker`](components/wheel-picker.md), and each wheel of a
  [`TimePicker`](components/time-picker.md) and a date-time picker, is a stop
  for Tab, where Tab went past every one: Up and Down turn it a row, round the
  ends of an endless one; Page Up and Page Down a face of rows; Home and End to
  its ends; and typing to the row that begins with what was typed — "17" on an
  hour wheel. A wheel clicked takes the keys, with a ring inside its band only
  when the focus came from the keyboard. A screen reader can turn it as an
  adjustable control, where it could only hear its value. A
  [`WheelSelect`](components/wheel-select.md)'s field turns its value from the
  keys, open or closed, and Escape folds its wheel.
- **Menus are walked from the keyboard.** In a
  [dropdown menu](components/dropdown-menu.md), a context menu and the lists of
  a [select](components/select.md), [multi-select](components/multi-select.md)
  and [combobox](components/combobox.md), Up and Down move between the rows that
  can be chosen and go round at the ends; Home and End go to the first and
  last; Page Up and Page Down a list's height; and typing goes to the row whose
  words begin with what was typed — "ma" to *Madagascar*, the same letter again
  to the next. Only Tab moved between rows before, which the docs did not say.
  A long list scrolls to the row the keys go to; in a combobox, typing in the
  list goes back to the field. A menu opened with a mouse holds the focus
  without a ring, so the next key moves about it, and Escape in a submenu puts
  the focus back on its row.
- **A select opens at its value.** A select, multi-select or combobox opens
  with the option it holds in the middle of its list, from its first frame,
  where it opened at the top; opened from the keyboard, the focus is on it.
  Escape, or an option picked with Enter, gives the focus back to the field.
- **A dialog's blurred backdrop is cheaper to draw on iOS, the desktop and the
  web.** A dialog that sits open costs nothing, but every frame drawn while it
  is open — its arrival, a caret blinking in it, a spinner, a clock on the page
  behind — blurred the whole app again at full size, since Skia keeps no
  blurred layer between frames. The app is now drawn at a quarter of its size
  for the blur and scaled back up: the same picture to within a few levels in
  255, at under half the cost on a software rasteriser. Android, and
  `BackdropStyle.BlurAndScale`, blur as before.
- **Carried past its actions, a [swipe row](components/swipe-actions.md)'s full
  swipe action grows and pushes the others aside.** Between the reveal and the
  point of no return the outermost action — the one letting go there will run —
  takes all the width the finger adds, and the others shrink to nothing as it
  does, so the strip is the outermost's by the time the line is crossed. They
  used to grow evenly up to the line and then fold away at once, which hid which
  action the swipe was heading for until it got there. A side where no action
  takes a full swipe still shares the width evenly.

### Added

- **[Patterns](patterns.md): how the pieces go together, and the mistakes that
  are easy to make.** A scroller padded instead of its content, which puts the
  scrollbar a page's margin in from the screen's edge; a theme or an overlay host
  per screen; rows grouped by hand, made pressable with `Modifier.clickable`, or
  wrapped in a card; a swipe row's shape in a group; pull to refresh and its
  list's state; literal colours and sizes; icons with no name; dialogs composed
  rather than shown; and what makes an app feel slow — the build being judged,
  and a moving value read in composition — and a coroutine kept in a gesture's
  pointer block, which crashes on iOS alone. Each with an example. The `Scaffold` page's
  own example applied the padding it said it handed over, and is a list's
  `contentPadding` now.
- **The documentation is public.** `docs.kontour.io/ui` and its Markdown for
  agents need no sign-in and no headers, and search engines may index them. The
  [Agents](agents.md) guide and its `CLAUDE.md` snippet drop the service token.
- **[Drag and drop](drag-and-drop.md) has a demo to press.** Two lanes whose
  cards reorder within one and carry across to the other, and a bin that takes a
  card from either: on the guide's own page, and on the gallery's Collections
  page. Knobs turn the drag handles off, so a card is held anywhere on it, and
  cap the second lane at two, so a third is seen turned away. The guide drew
  nothing before, and the gallery showed drag and drop only inside the board app.
- **[`SplitListItem`](components/split-list-item.md): two rows of a list side
  by side, as one row.** Each half is a `ListItem` of its own — its own press,
  selection, focus and screen-reader stop — joined as a group's rows are, with
  the hairline between them and the facing corners cut square, and it takes its
  outside corners from `position` like any row. `ListItemSplit` shares the
  width: `Halves`, `TwoToOne` and `OneToTwo`, `AutoSquare` and `SquareAuto` for a
  tile as wide as the row is tall beside the rest, or `ratio(start, end)`. Both
  halves are as tall as the taller, and a square grows with its row. Each half
  takes its own `containerColour` and `contentColour`, a row's by default. In a
  `ListGroup`, `split(ListItemSplit.Halves) { start { … }; end { … } }` adds one
  with its corners worked out like any other row's.
- **A [select](components/select.md)'s options can be drawn rather than named.**
  `optionContent`, the trailing lambda of `Select`,
  [`MultiSelect`](components/multi-select.md) and
  [`Combobox`](components/combobox.md), draws each option in the menu, and a
  select's value in its closed field, with `+"…"` and `+icon` and anything else:
  a face beside a name, a swatch beside a line. `valueContent` draws the closed
  field alone, for one that shows less than a row does; a `MultiSelect`'s is
  given the ticked set. Either is in the field's colour, and grey when it is
  disabled. `optionLabel` is still what a screen reader says for an option, and
  what a combobox matches typing against, whatever the slot draws.
- **`ListSection(textInset = …)`** sets where the title, supporting line and
  footer start, for a section whose content the automatic reading gets wrong;
  `0.dp` is flush whatever is inside. **`ListItemDefaults.ContentInset`** is how
  far in a row's content starts.

### Fixed

- **Scrolling and animating are cheaper on iOS throughout.** A squircle that is
  exactly a rounded rectangle — a circle: an avatar, a status dot, a radio ring
  — is handed to Skia as one, and every surface clips, and casts its shadow,
  with a rounded rectangle of its corners, where each was a path Skia masks; the
  ground and the edge are still drawn as the squircle. A pointer's hover shape,
  inert on iOS, is no longer a node on every control there. An opening
  `ExpandingListItem` or `DisclosureListItem` no longer composes its rows again
  on every frame; a swipe action's button no longer composes again for every
  width it passes through; a chat bubble makes its outline once per size rather
  than three times; a large title is drawn offscreen only while its second line
  is being cut away; and a sheet works out how collapsed it is without making a
  list on every frame.
- **[Pull to refresh](components/pull-to-refresh.md) comes down with the
  finger.** The content chased the pull on a spring, restarted on every move,
  so it trailed a steady pull by 27px and caught up only when the finger
  stopped — and setting the spring's target recomposed the container on every
  pointer event. It follows the finger exactly now, and springs only on the way
  home.
- **Lighter in places that are drawn every frame.** A
  [wheel select](components/wheel-select.md)'s drum renders offscreen only while
  it has a sliver to fade, as fading edges already did; a
  [text field](components/text-field.md) no longer resolves seven font
  resources on every composition for an answer only the web uses; a
  `Modifier.concentric` clip goes through the same rounded-rectangle stand-in
  as every other clip; and a menu under an opening submenu works out whether
  it is covered once rather than on every frame.

**Sheets and overlays**

- **Closing a [modal bottom sheet](components/modal-bottom-sheet.md) that may not
  be hidden no longer hangs the app.** A sheet with no hidden detent, or one
  refusing it through `confirmDetentChange` once open, turned down every `hide()`
  without moving, and the close went round again without yielding the main
  thread. It now leaves without a slide.
- **A list in a floating [bottom sheet](components/bottom-sheet.md) is no longer
  measured again on every frame the sheet moves.** The padding the sheet hands
  its content was read from the sheet's position, so every frame of every drag,
  settle, open and close re-measured a `LazyColumn` given it as `contentPadding`,
  though it only changes across the morph to an edge sheet.
- **A button in a moving sheet answers a tap.** Every open is a moving sheet, so
  a tap as one opened, or settled, did nothing.
- **A [modal bottom sheet](components/modal-bottom-sheet.md) opened again comes
  back as it came the first time.** Reopened under reduced motion during its
  fade out, it and its scrim stayed translucent; reopened after the window had
  grown while it was closed, it slid up from the middle of the screen.
- **A request made before a sheet has been laid out arrives as asked.**
  `expand()` on a sheet whose `Expanded` is its peek left it hidden while it
  reported itself open — for a modal, a scrim over nothing — and `snapTo` before
  the first layout slid there instead of snapping.
- **A [dialog](components/dialog.md) reports a scrim tap or back to the
  `onDismissRequest` it has now**, where it reported to the one it opened with.
- **Closed [tooltips](components/tooltip.md), [popovers](components/popover.md),
  dropdown menus and coach marks no longer recompose on every frame their page
  scrolls**; an open anchored overlay no longer recomposes as its anchor moves; a
  pending [coach mark](components/coachmark.md) no longer recomposes its screen
  on every frame, which kept an app from ever going idle; and an open menu
  follows a field that changes width.
- **The app's own `ToastHostState.dismiss(id)` no longer buys the other
  [toasts](components/toast.md) time.** Only a toast the reader sends away tops
  the rest up by `ToastDefaults.ClearedGrace`.
- **A drag on an open menu's list no longer scrolls the page under it.** Where
  an `OverlayHost` sits in a page that scrolls — a card, a pane, a stage with a
  host of its own — a drag on a [combobox](components/combobox.md)'s or a
  [select](components/select.md)'s list handed the page whatever the list did
  not use: all of it, for a list that fits, and the rest of a drag past its end.
  The drag is the list's now, with its stretch or bounce at the ends, and a
  wheel's turn stops at the list too. A drag on a combobox's field still scrolls
  the page, as on any field.
- **An arrow meets its bubble along the bubble's own edge.** The
  [index scroller](components/index-scroller.md)'s bubble, one letter in a
  squircle, is very nearly round, and its arrow's base sat on the bubble's
  bounding box: its corners stood out past the side, which had already curved
  away, as a small ledge either side. A tooltip's, a popover's or a coach
  mark's arrow is now cut along its panel's outline, so on a curve it runs into
  the curve; on a straight side it is drawn exactly as before.

**Navigation and layout**

- **A list beside a detail keeps its place on one pane.** On a phone, opening an
  item in a `ListDetailPaneScaffold` and coming back put the list at its top, and
  so did a window narrowing to one pane with an item open. What the list keeps in
  `rememberSaveable` — a `rememberLazyListState`'s position, a search being typed
  — now survives its time away; what it keeps in plain `remember` still starts
  again.
- **The screen inside a [`NavigationSuiteScaffold`](components/navigation-suite-scaffold.md)
  carries on when the bar becomes a rail.** A phone turned on its side, a
  foldable opened or a window dragged past 600dp started the whole content again,
  its saved state and scroll positions with it. Its bar's padding is also right
  from the first frame, where it was a 44–48dp guess for one frame — under a
  floating bar of about 96dp on an iPhone.
- **A [`Scaffold`](components/scaffold.md)'s content no longer trails a moving
  bar by a frame on Android.** The padding written as a top bar collapsed or the
  keyboard rose was announced only after the frame, so a list not otherwise
  dirty was laid out against the previous frame's padding.
- **A pane split's seam follows a drag after the app changes
  `supportingWeight`**, where it stood still under the finger, and **one set from
  a screen reader goes where it was asked**: `setProgress` moved it as if the
  window were 1000px wide, under a published range the drag did not use.
- **`Transitions.sharedAxis` goes forward the way the line reads.** In right to
  left, a step forward along X came in from the right.
- **A large or hero [top bar](components/top-bar.md)'s push on iOS is let go of
  however the gesture ends.** Content swapped away mid-push left the push held
  and the next press misread; a bar saved mid-push came back part-way; a list
  merely at its end under an open bar was taken for one that cannot scroll; and a
  release back towards the start stopped the list dead rather than flinging it
  back. `topBarCollapse` round a box holding both the bar and its list now
  leaves the bar where it is.
- **An in-app language written the other way from the device's no longer resets
  the app.** Switching `KontourTheme(locale = …)` between English and Arabic
  disposed the whole application and built it again.

**Controls**

- **A control no longer loses its press and hover feedback after something
  changes under a finger.** The first touch after using a mouse, or a theme or
  contrast change with a pointer over a control, left it unable to show a press
  or a hover again. Each press is now matched to its own release, and a control
  taken back from a lazy list's pool starts unpressed. Moving between the mouse,
  the keyboard and the screen also no longer recomposes every control on screen.
- **A slider or range slider over a range with no width no longer throws**, on a
  tap, a key or a screen reader's `setProgress`. **A slider's keys and
  `setProgress` land on a step**, as a drag does: a stepped slider at 0.33 went to
  0.58 on Right, and seven presses up a ten-notch slider arrived at 0.70000005.
  **The end of a slider's track is the end of its range, exactly**: a drag to the
  end of `0.3f..1.9f` reported 1.8999999, and one of `-273.15f..0.7f` a value
  outside the range. Sliders also no longer subcompose on every frame of a drag.
- **A range slider's keys build on the range the app holds**, where the next key
  built on the range the last one reported and the other thumb jumped back; and a
  `minDistance` that is a whole number of steps is no longer rounded up by one.
- **A right-to-left [`Rating`](components/rating.md) scores the star tapped**: a
  tap on the first star cleared the score, and the last scored one. **Only the
  scored star says it is selected**, as one radio button of a group; every
  filled star did. Given an `interactionSource`, it rings only the focused star.
- **A switch in a disabled `SelectionRow` can no longer be dragged.**
- **The text menu respects the field.** Paste and Cut go through the field's
  input filter, so pasting `12ab` into a `NumberField` does nothing, as typing it
  would; and a masked `PasswordField` or `SecretField` no longer offers Copy or
  Cut.
- **A [combobox](components/combobox.md) works from the keyboard and a screen
  reader**: opened from the keyboard, the focus stays in its field so typing
  narrows the list, and Down moves into it; a screen reader can close it from the
  field. Closed with `SelectState.collapse()` it forgets its search, and its rows
  stay with their options as the list narrows.
- **A select's "Other" box keeps what was typed when the app is slow to echo
  it.** A late `otherText` of `a` deleted the `b` typed after it.
- **A focus ring no longer starts a collector on a control that cannot be
  focused**, and a closed `WheelSelect` no longer recomposes on every frame its
  page scrolls.
- **A wheel turned by a tap under reduced motion says it has come to rest.** The
  turn is a jump there, which the wheel never saw move, so
  `onSelectedIndexChangeFinished` — and a `TimePicker`'s or a `WheelSelect`'s
  `onValueChangeFinished` — was never called for it.

**Lists and display**

- **A [carousel](components/carousel.md) follows a page count taken from a
  parameter**, where it kept the first lambda it was given — a list that grew
  showed only its first pages, and one that shrank threw. **A
  [thumbnail strip](components/thumbnail-strip.md) follows its carousel's page
  count too**, and no longer places each thumbnail as its neighbour for a frame
  when its width changes.
- **A full [swipe](components/swipe-actions.md) whose row is tapped on its way
  home still lets the next one run.** The tick stayed drawn, and every full swipe
  after it went to the edge and ran nothing. A row composed open shows its
  actions' icons and labels at once, where they faded in.
- **A [reorderable row](components/reorderable-item.md) carried over a header
  or footer stays where it was**, where the last row over a footer reported a
  move past the end and the documented `add(to, removeAt(from))` threw.
- **A disabled reorderable row or swipe row offers a screen reader none of its
  moves or actions**, where it offered and ran them.
- **An [expanding list item](components/expanding-list-item.md)'s rows follow a
  state its builder reads** — `if (signedIn) item("Sign out")` — which this
  release's own change had frozen.
- **A row added to an open [FAB menu](components/fab-menu.md) is shown**, where
  the menu threw.
- **A drop's `fromIndex` is where the carried item is when it lands**, found by
  its key: a list that changed during the carry had `removeAt(fromIndex)` take out
  the row beside it.
- **A [list section](components/list-section.md) of a fixed height gives a
  `weight` what its header and footer leave**, where the footer was drawn below
  the section, and its intrinsic height counts a title at the rows' inset.
- **A [scrollbar](components/scrollbar.md) on a long lazy list costs the same at
  the foot as at the top**, where it summed every row above on each look, several
  times a frame.
- **A `Card` keeps its content when its padding changes between even and uneven,
  or when its `onClick` comes and goes.**
- **Everything in a `Redacted` block shimmers as one**, from one animation where
  each text and icon ran its own, and a row arriving later no longer sweeps out of
  step.
- **A surface's clip matches its squircle when a corner is too big for its box**,
  and a `SquircleShape` with a negative smoothing is drawn as one curve
  everywhere.

**Dates and the calendar**

- **A [time picker](components/time-picker.md) given a time past its last
  minute step keeps its hour.** At 9:58 by fives it opened on :00 and reported
  9:00 before anything was touched; it opens on 9:55.
- **Now on a [date and time picker](components/date-time-picker.md) that cannot
  take tomorrow stops at the end of today**, where at 23:58 by fives it gave 00:00
  today, a day before now.
- **[`RelativeTimeText`](components/relative-time-text.md) reads the time left
  off the clock.** It added up the delays it had slept, so an app back from the
  background showed a countdown that had counted none of that time, and above a
  minute it ticked every twenty seconds, so "in 2 min" stayed up with fewer than
  two left.
- **A [date picker](components/date-picker.md) keeps the month it was paged to**
  through rotation, process death and being scrolled out of a list.
- **A [date range picker](components/date-range-picker.md)'s days say where in
  the range they are**: "Range start", "In range", "Range end".
- **An [activity calendar](components/activity-calendar.md) read right to left
  keeps the keyboard's day in view**, where it scrolled to the oldest weeks.
- **An [event calendar](components/event-calendar.md)'s line or bar picked up
  from the keyboard is carried by days, as a pointer carries it**, where up and
  down moved its time unseen; in a month they now move it a week. A move or a
  resize of an all-day or several-day event is read out with its days, where it
  said "00:00 – 00:00" and never its new end.
- **An event calendar no longer composes every day and event again each
  minute**, nor writes every day's and event's place again on every frame it
  scrolls, and a wheel's turn on its month no longer hands what is left of its
  speed to the page round it.

**Platforms**

- **A swipe back let go on a 120Hz screen no longer jumps on its first frame**,
  on iOS, and **Android's back gesture finishes at the screen's own frame
  rate**: the relayed release was timed as if every screen ran at 60Hz, so on a
  90Hz or 120Hz phone the page jumped about two frames' worth as it was let go.
  The frame is measured during the gesture, and the release's first frame, its
  shortest length and the shortest swipe whose speed is read all use it.
- **A rumble in the browser no longer starts again on an update after it has
  ended**, and ended rumbles are cleared away.
- **A desktop app no longer keeps a growing list of every appearance its theme
  reported**, and a theme cross-fade no longer recomposes `KontourTheme` itself
  on every frame.

---

## 0.4.4 — 11 October 2026

An event calendar that scrolls through its months and years, zooms its month
under two fingers, and shows three days, several weeks at once or a schedule,
with icons on its events and a header that fits a phone. A filmstrip for a
carousel, a countdown on a toast, icons and folded stops on a timeline, and a
back button in a sheet header. Sheets that move only on a flick that is meant,
let a carousel inside them have its swipe, always report being put away, and
drag a floating sheet without stutter.

A date picker's days are decorated with one `DayDecoration`, `CalendarView` has
two more views, a few arguments passed by position need naming, and anything
built against 0.4.3 has to be recompiled — see **Breaking**, and
[Migrating](migrating.md#043-to-044). Several things look or behave differently
without any change: an event calendar offers six views, in a menu where its
header is narrow; its month and year scroll up and down rather than paging; its
overlapping events lie over each other; a bottom sheet moves only on a
deliberate flick, one detent unless it is thrown hard, and lets a swipe across
something inside it be that thing's; and swipe actions in a reorderable row wait
for the finger to go 24dp across.

### Breaking

- **`CalendarView` has two more views, `ThreeDays` and `Schedule`**, so a `when`
  that names every view needs a branch for each — see
  [Migrating](migrating.md#a-when-over-calendarview-has-two-more-branches).
  `EventCalendar`, `EventCalendarDefaults.Header`, `CalendarEvent`,
  `rememberEventCalendarState`, `ToastHost`, `SheetHeader`, `TimelineItem`, a
  timeline's `item` and `Strings` have new parameters, so anything built against
  0.4.3 has to be recompiled.
- **A date picker's days are decorated with one
  [`DayDecoration`](components/calendar-month.md#daydecoration)**, by
  `decorationFor`, in place of `markerFor`, `containerColourFor` and
  `annotationFor` and its `DayAnnotation`, on `DatePicker`, `DateRangePicker`,
  `DateTimePicker` and `CalendarMonth` — see
  [Migrating](migrating.md#a-date-pickers-days-are-decorated-with-one-daydecoration).
  It has more than one dot, an icon beside the text, and `keepColours`, which
  keeps them on a chosen day, where they took its ink. `showDecorationLine =
  false` keeps the weeks their height for a calendar that only draws dots.
- **A few arguments passed by position need naming**, where a new parameter now
  comes before them: a `TimelineItem`'s `nodeSize` and those after it, a
  timeline `item`'s `connector` and after, `ToastHost`'s `windowInsets`,
  `SheetHeader`'s `actions`, `EventCalendar`'s `formats` and after, and the
  arguments after `isDateSelectable` on the date pickers and `CalendarMonth` —
  see [Migrating](migrating.md#a-few-arguments-passed-by-position-need-naming).
  Named arguments, which every example uses, are unaffected.

### Changed

- **Kontour UI is open source, under the [Apache License
  2.0](https://www.apache.org/licenses/LICENSE-2.0).** It was all rights
  reserved. The packages are public; GitHub Packages still asks for a token with
  `read:packages` to download them, now any GitHub account's. The fonts `:ui`
  bundles and the CLDR plural rules keep their own licences, listed in `NOTICE`.
- **An [event calendar](components/event-calendar.md) offers six views**:
  `EventCalendarDefaults.Views` gains three days and the schedule, so an
  existing calendar's switcher has them too, and a header too narrow for every
  view side by side offers them in a menu. Pass `views` to keep the four it had.
- **An [event calendar](components/event-calendar.md)'s year scrolls up and down
  through the years**, each year's months under its number, instead of paging
  sideways a year at a time; the year most on screen is the one the title and
  the state are about. Its months are drawn whole, so every month on screen is
  there at once — they came in one after another. A screen reader reaches each
  month as one stop, read with how many of its days are busy, and its days in
  the month it opens; before, each day was a stop.
- **An [event calendar](components/event-calendar.md)'s month scrolls up and
  down by the week**, as Apple's calendar does, instead of paging sideways a
  month at a time. Every row is the same height — six weeks to the box — so the
  weeks sit under each other from one month into the next, and a scroll or a
  fling settles with a week at the top. The month most on screen is the one the
  title, the state's `date` and the quieter days are about; the header's arrows,
  Page Up and Page Down and `state.step` scroll to that month's first week. An
  event carried near the top or the foot scrolls the weeks with it, staying
  under the finger, as the hours do, in place of the arrow at the side.
  `visibleDates` in the month also covers any weeks either side scrolled into
  view.
- **An [event calendar](components/event-calendar.md)'s overlapping events are
  laid over each other** in the day and week views when one starts below the
  other's title, as Google's calendar does: a little in from its start edge,
  ringed, and most of the day wide. Before, each took a lane of its own, and
  three in a week's narrow day were a letter wide apiece. Events that start
  together still go side by side.
- **An [event calendar](components/event-calendar.md)'s titles show more of
  themselves.** A month's line is in a slightly smaller type, and in a narrow
  day gives up its time, then its dot, before its title. A title cut short fades
  out instead of ending in "…". In the hours a title wraps over every line its
  event is tall, not two. A busy day's "+N" is a row the size of its events, in
  line with them, rather than text under them; in the week's all-day strip too.
- **An [event calendar](components/event-calendar.md)'s event that runs on past
  what a view shows is square on that side, and runs through the line there**,
  so a bar across two weeks, or a block across midnight, reads as one event in
  two pieces. It is rounded, and stops short of the line, only where it really
  ends.
- **An [event calendar](components/event-calendar.md)'s arrow at the side of the
  days is level with a mouse's pointer** while an event is carried. It was 40dp
  above it, which is for a fingertip; under a finger it still is.
- **A [bottom sheet](components/sheets.md) moves only on a flick that is
  meant.** A release goes to the detent nearest where its throw would carry the
  sheet, projected a fifth of a second, and past the next detent only at
  5000dp/s or more — so an ordinary flick down from the top stops at the middle,
  and a hard one still closes it in one. It counts as a throw only once the
  finger has run about seven and a half millimetres (48dp) one way, at 300dp/s
  or more; a throw of the sheet itself, by its handle or its surface, counted
  from 125dp/s. Any flick over the floor used to move a whole detent, projected
  half a second, so a sheet went away on a finger moved by accident and an
  ordinary flick from the top went straight past the middle.
- **A floating [bottom sheet](components/bottom-sheet.md)'s content widens with
  it as it fills to the sides**, to the whole width of the edge sheet it becomes
  at the top. It was held at its floating width, so the surface grew past it and
  an expanded sheet kept a floating margin inside its sides.
- **A [bottom sheet](components/bottom-sheet.md)'s content fades out a little
  higher above a part pinned to its foot**, 24dp rather than 16dp, so the
  buttons read as standing over what scrolls behind them.
- **A [toast](components/toast.md)'s clock stops while a mouse's pointer rests
  on it**, as it already did while a finger held the stack, so one being read is
  not taken away.

### Added

- **[`ThumbnailStrip`](components/thumbnail-strip.md)**: a carousel's pages as a
  photo app's filmstrip, the current one wide in the middle — its photo's own
  shape, with `thumbnailAspectRatio`, between a thumbnail's width and a
  panorama's, and a photo narrower than a thumbnail shown whole between black
  bars. Dragged, it scrubs through the pages with the carousel following; a
  thumbnail pressed, an arrow key or a screen reader's action goes to its page.
  It shares the carousel's `CarouselState`, and composes only the thumbnails on
  screen.
- **A [toast](components/toast.md) can show the time it has left**:
  `ToastHost(countdown = ToastCountdown.Wipe)` washes its background and draws
  it back as the time runs out, and `ToastCountdown.Bar` draws a bar along its
  foot that shortens. Both stand still while the toast's clock does.
- **An [event calendar](components/event-calendar.md) shows three days**,
  `CalendarView.ThreeDays`: their hours side by side, from the day it was
  switched to, stepped and swiped three days at a time. The header's switcher
  offers it between the day and the week; `EventCalendarDefaults.Views` is the
  switcher's order. New strings: `threeDays`, `previousDays`, `nextDays`.
- **An [event calendar](components/event-calendar.md) has a schedule**,
  `CalendarView.Schedule`: the days with something on, one under another with
  their events, under each month's name, scrolled through, today always among
  them and saying "Nothing planned" when it has nothing on. It is last in the
  switcher. New strings: `schedule`, `nothingPlanned`.
- **An [event calendar](components/event-calendar.md)'s three-day and week views
  can do without their hours**, `daysStyle = CalendarDaysStyle.Condensed`: each
  day's events stacked in a list down the days, a line at each day's edge.
  `CalendarDaysStyle.Hours`, the default, is as before, and the day view always
  has its hours.
- **An [event calendar](components/event-calendar.md)'s week view shows several
  weeks at once**: `state.showWeeks(count)`, 1 to 6, or
  `rememberEventCalendarState(initialWeekCount = …)`, from the week the date is
  in. Steps and swipes go by as many weeks. `state.weekCount` says how many; it
  is kept with the state.
- **An [event calendar](components/event-calendar.md)'s month zooms**: two
  fingers spread show fewer weeks, each taller, down to three to the box;
  pinched, more, up to six. The day between the fingers stays under them and the
  events grow and shrink with their weeks as the fingers move. Ctrl or Cmd with
  the wheel, or with plus and minus, steps a week, and a screen reader has Zoom
  in and Zoom out; under reduced motion it settles at once. Zoomed in, an event
  shows its time and where it is on lines of their own. The zoom is kept with
  the state. New strings: `zoomIn`, `zoomOut`.
- **An [event calendar](components/event-calendar.md)'s events can have an
  icon**: `CalendarEvent(icon = …)`, before the title in a block or a bar and in
  place of the dot in a line, given up before the title where a day is narrow.
- **An [event calendar](components/event-calendar.md) can show only all-day and
  multi-day events**: `showTimedEvents = false`. Its days are stacked with no
  hours, the day view's too.
- **An [event calendar](components/event-calendar.md) can leave views out**:
  `EventCalendar(views = …)`. The switcher offers only those, and a day opened
  without a day view opens the nearest view there is. **Its header can offer the
  views in a menu**: `EventCalendarDefaults.Header(state, switcher =
  CalendarSwitcher.Dropdown)`, which it does by itself where it is too narrow
  for them side by side. New string: `chooseView`.
- **An event in a busy day's list, opened from an [event
  calendar](components/event-calendar.md) month's "+N", can be carried out of
  it** onto any day of the month, by a finger or a mouse. The list follows the
  carry and closes once the event is put down.
- **A timeline can fold several stops into one row**:
  [`TimelineNodeGroup(count)`](components/timeline.md), and `nodeGroup(count)`
  in a [`TimelineList`](components/timeline-list.md) or a
  [`BranchTimeline`](components/branch-timeline.md) — "Show 10 past stops", its
  node up to three nodes bunched along the rail. It is one stop to the rail and
  to `progress`, whose halo goes round the whole stack.
- **A timeline's node can hold an icon**: `icon = …` on a
  [`TimelineItem`](components/timeline.md), and on a
  [`TimelineList`](components/timeline-list.md) stop or a
  [`BranchTimeline`](components/branch-timeline.md) commit. The node grows to
  `TimelineDefaults.IconNodeSize` to hold it, and the icon reads on it — light
  or dark on a solid node, the ring's colour inside a hollow one.
- **A [sheet header](components/sheet-header.md) can lead with a back button**:
  `SheetHeader(onBack = …)`, with `backLabel`, or `navigation` for something
  else in its place, as a [top bar](components/top-bar.md) takes them.

### Fixed

- **A floating [bottom sheet](components/bottom-sheet.md) no longer stutters as
  it is dragged**, through its morph into an edge sheet most of all. Its two
  blurred shadow layers were rasterised again on every frame its size or its
  corners changed — measured on an Android phone at 25% janky frames and a 150ms
  p99, against 5% for an edge sheet. The shadow is now blurred once, for a small
  tile, and drawn at any size from that, and looks the same; the surface no
  longer recomposes for each step of its corners; and a squircle works its
  corners out once per radius rather than at every size.
- **A [modal bottom sheet](components/bottom-sheet.md) flicked or dragged away
  is always reported as dismissed**, so it opens again next time. It was
  reported only when a settle completed, and a sheet caught by a touch as it
  opened or settled could be put away without one: the caller still had it open,
  and could not open it again. A sheet the caller closes while a finger is on it
  goes when the finger lifts, where it stayed up; and a finger on a sheet thrown
  away holds the page again.
- **A [modal bottom sheet](components/bottom-sheet.md) with a part pinned to its
  top is drawn.** It opened, with the scrim over the page, and showed nothing:
  its surface was measured once, before the sheet had settled its detents, at no
  height, and never again.
- **A swipe across a carousel, a row of chips or a pager inside a [bottom
  sheet](components/sheets.md) is theirs up to about sixty degrees from
  across**, where the sheet took any swipe over thirty. The sheet's drag is its
  own now, not Compose's `anchoredDraggable`: a Compose draggable round a
  horizontal one takes every swipe over thirty degrees from it, however long its
  slop. It waits for 1.75 times the touch slop up or down, so a swipe mostly up
  or down still moves the sheet from over a carousel.
- **A [bottom sheet](components/sheets.md) nudged a notch and then taken away by
  the system — the notification shade pulled down over it, focus gone elsewhere
  — stays where it was.** The cancel was read as a release, at the speed of the
  nudge, and could close a sheet moved a millimetre. So could a finger held
  still a moment before lifting: the throw was read from the moves alone, not
  from when the finger let go.
- **[Swipe actions](components/swipe-actions.md) in a
  [reorderable](components/reorderable-item.md) row no longer stop it being held
  and carried.** A fingertip rolling a few pixels sideways as it held the row
  was taken for a swipe, and the row never lifted. Inside a reorderable row a
  swipe waits for the finger to go 24dp across, and a row once lifted is carried
  alone.
- **Something picked up with a long press and let go where it was is not also
  tapped.** A [reorderable](components/reorderable-item.md) row or a [drag
  source](drag-and-drop.md) with its own click, held until it lifted and let go
  without moving, also clicked; moved first, it did not, so it happened only
  sometimes.
- **On iOS, a swipe out of the app from the home indicator no longer scrolls
  what is under it.** iOS hands the app the start of the swipe before it takes
  it over; a finger that lands on the home indicator now moves nothing in the
  app past touch slop, and a tap there still lands. A trackpad, mouse or pencil
  is left alone.
- **A decoration on a day in the band of a [date
  range](components/date-range-picker.md) keeps its colours.** It took the
  band's ink, which read as grey.
- **Today's ring stays a circle inside a [date
  range](components/date-range-picker.md).** It was the outline of the band's
  square day, and drew a pill the width of it.
- **The discs behind a hero [top bar](components/top-bar.md)'s actions follow
  the collapse after the screen is come back to.** A screen left part-way down
  its scroll and navigated back to kept the discs as faint as they were when it
  was left, or gone, however far back up it was scrolled.

---

## 0.4.3 — 8 October 2026

An event calendar; coach mark tours that cross the app; a Now on the time
pickers, and one way for every date and time field to open its picker. A
scrollbar that can measure its list and never jumps, a sheet's pinned content as
parts — at its foot, on its top, or the head of its list that is all that is
left collapsed — days in a date picker that carry a colour and a few characters,
and a large top bar that follows the finger. On the mobile web, the keyboard on
the first tap of a text field on an iPhone, a focused field's selection and menu
that are the browser's own, and tabs, checkboxes and switches named as
themselves to a screen reader. On Compose Multiplatform 1.12.1, with Java 11
bytecode on the JVM.

A sheet's `pinned { }` is now a pinned `part`, a few arguments passed by
position need naming, and anything built against 0.4.2 has to be recompiled —
see **Breaking**, and [Migrating](migrating.md#042-to-043). Several things look
or behave differently without any change: a badged button is the size of what
it badges, a coach mark tour is not ended by a tap outside it and moves between
steps as a morph, a `KeyValueList`'s values are in one column, a swipe row
closes when anything else is pressed, a time picker is taller by its Now
button, a large top bar finishes the way the finger was going, and a
`StateSwitcher` lays a state out as a column.

### Breaking

- **A [bottom sheet](components/bottom-sheet.md#partpin----what-stays-put-while-the-sheet-moves)'s
  `pinned { }` is a `part` with a `pin`.** Rethought around three uses: a
  search bar locked at the bottom of a sheet like a navigation bar, a search
  bar on top of the sheet that the content scrolls under, and a search bar at
  the head of a list that becomes the only thing showing as the sheet
  collapses. `part(pin = SheetPartPin.Bottom)` is `pinned { }`,
  `SheetPartPin.Top` is `pinned(to = SheetPin.Top)`, and
  `SheetPartPin.Collapsed` is new. `SheetPin` is gone, `SheetDetent.pinned()`
  is `SheetDetent.parts()`, and `pinned(containerColour = …)` is gone:
  `fadeUnder = false` on the sheet lets the content show round the parts. What
  changed on screen is under **Changed**.
- **Recompile against Compose Multiplatform 1.12.1.** The library moved from
  1.12.0-rc01 to the stable release.
- **A few arguments passed by position no longer compile**, where a parameter
  arrived in the middle of a list, each of a different type from what was there
  so nothing silently means something else. Name the argument; the new
  parameters, and what they come after:

| Where | Takes, new | After |
|---|---|---|
| `DatePicker`, `DateRangePicker`, `DateTimePicker`, `CalendarMonth` | `containerColourFor`, `annotationFor` | `markerFor` |
| `TimePicker`, `DateTimePicker` | `now` | `minuteStep` |
| `DateField` | `supporting`, `errorMessage` | `placeholder` |
| `TimeField`, `DateTimeField` | `supporting`, `errorMessage` | `label` |
| `BottomSheet`, `ModalBottomSheet` | `fadeUnder` | `draggable` |
| `Select`, `MultiSelect` | `otherPlaceholder` | `otherLabel` |
| `Modifier.coachmark` | `tone` | `alignment` |
| `Modifier.coachmarkStep` | `tone` | `shape` |

- **Recompile rather than swap the jar.** `SwipeAction` has a new constructor;
  `Scrollbar` and `Modifier.scrollbar`, `PullToRefresh`, `TimePicker`,
  `DateTimePicker`, `DatePicker`, `DateRangePicker`, `CalendarMonth`,
  `DateField`, `TimeField`, `DateTimeField`, `Select`, `MultiSelect`,
  `BottomSheet`, `ModalBottomSheet`, `Modifier.coachmark`,
  `Modifier.coachmarkStep`, `rememberCoachmarkTour`, `SheetContentScope`,
  `Strings` and `ComponentDefaults` gained members or parameters, so a library
  built against 0.4.2 that calls one fails until it is rebuilt.

### Changed

- **A badged button lines up with the buttons beside it.** A
  [`BadgedBox`](components/badge.md) is now exactly the size of what it badges,
  with its baselines, and the badge hangs over the corner outside it. It used to
  grow by half the badge on the top and trailing side, so a badged button in a
  top bar's actions sat half a badge lower than its neighbours, with uneven gaps
  either side. A parent that clips needs room for half a badge.
- **A [time picker](components/time-picker.md) and a
  [date-and-time picker](components/date-time-picker.md) have a Now button**
  under their wheels, so the time and date-time fields' pickers do too, and are
  taller by it. It scrolls the wheels through to the time now, rounded to
  `minuteStep`; the date-and-time picker's takes today's date with it, where the
  calendar allows that day. `now = null` hides it.
- **A [coach mark tour](components/coachmark.md#coachmarktour) is not
  dismissed by a tap on the dim, Back or Escape** unless
  `rememberCoachmarkTour(dismissible = true)`. Its way out is its own buttons.
- **A coach mark tour moves between steps as a morph by default**: the mark
  shrinks into a ball, travels to the next step and grows into its mark, where
  the lit area used to glide between them.
  `transition = CoachmarkTransition.Fade` crossfades instead. Under reduced
  motion it always fades.
- **A `KeyValueList`'s values line up in one column whatever the labels.**
  Every row shares a label column as wide as the widest label, from
  `labelWidth` up to what leaves the values their room. `labelWidth` was a floor
  each row took for itself, so a label longer than it pushed its own value out
  of line with the rest. In a row that scrolls sideways it takes its natural
  width.
- **A swipe row showing its actions closes when anything else is pressed**:
  another row, a button, the list scrolled, as a SwiftUI list's row does — from
  the moment it sets off open. The press still does what it does. It stayed
  open, its actions out, while the reader went on to something else.
- **A `FabMenu` item's label can be tapped**, and does what its button does.
  A tap on it fell through to the scrim and closed the menu with nothing
  chosen.
- **A scrollbar's thumb never jumps.** It moves only as the content moves,
  reaching the ends of its track exactly as the content reaches its own, and
  eases to a new length as a lazy list's rows come into view. A thumb that
  started too small grew in one frame at the end of the list, and one on a page
  of uneven cards stepped back and forth as it was scrolled back up.
- **A [scrollbar](components/scrollbar.md) keeps its length on a lazy list of
  uneven rows.** A lazy list's length was the average row on screen times the
  number of rows, so the thumb grew over short rows and shrank over tall ones.
  It is now from every row the list has measured, and is held still while the
  thumb is.
- **A list with an [index](components/index-scroller.md) has no scrollbar.** The
  index is its position control, so a scrollbar given the same list draws
  nothing while the index is there.
- **An undismissable [modal sheet](components/modal-bottom-sheet.md) with one
  size has no drag handle.** With `dismissible = false` and nothing to be dragged
  between, a drag only stretched it and sprang it back. A sheet with more than one
  size keeps its handle.
- **A [page indicator](components/page-indicator.md)'s drag pill is `outline`
  in the light too**, as it was already in the dark: on a hero's light fade the
  well it was drawn in could hardly be seen. In a hero the dots are given room
  for the pill, which was cut off on an iPad.
- **Collapsed to its parts, a [bottom sheet](components/bottom-sheet.md) shows
  nothing of its content round them.** The content fades into the sheet's
  colour on the way down to `SheetDetent.parts()`, whatever the parts are
  pinned to. A list scrolled behind a bottom-pinned search bar showed through
  the bar's fade with the sheet collapsed.
- **A part pinned to a sheet's bottom is a navigation bar to it**: at the foot
  of what the sheet shows through every frame of a drag, with the content
  appearing above it as the sheet grows.
- **Content fades out under a sheet's pinned parts** — under a bottom part as
  under a floating navigation bar, under a top part as under a `TopBar` —
  where a pinned part stood on a solid band of the sheet's colour.
  `fadeUnder = false` lets the content show round them, as
  `containerColour = Color.Transparent` did.
- **A part pinned to a sheet's top has no inset of its own.** It was padded by
  16dp at the sides and below; it is laid out as the content is now, so a
  `SheetHeader` there looks as it does in the content, and anything else is as
  wide as the sheet.
- **A slider waits for the finger to come back.** Dragged off the end of a
  [`Slider`](components/slider.md) or a `RangeSlider` and back, the thumb
  stays at the end until the finger or pointer is level with it again — for a
  range slider's thumb held off by `minDistance` too. It used to move as soon
  as the finger turned round, however far past the end it was, because only the
  stretch past the end was remembered and that stops growing.
- **A dot under a day no longer moves its number**, in
  [`CalendarMonth`](components/calendar-month.md) and the date and range
  pickers. A day with a `markerFor` dot had its number nudged up out of line
  with the rest of its week; the number now sits where it would with nothing
  under it.
- **On an iPhone or iPad, the first tap on a text field opens the keyboard.**
  0.4.2's fix did not, and it took a second tap. Compose focuses its input
  while the finger is still down, a frame after it lands, which WebKit does not
  count as the reader's; 0.4.2's primer then took the focus from it as the
  finger lifted, and Compose took it back a frame later, outside the touch
  again. The primer now focuses Compose's own input again as the finger lifts,
  which is the focus WebKit opens the keyboard for.
- **On a phone or a tablet, in any browser, a focused text field's selection,
  handles and menu are the browser's own**, not Compose's. Compose's input is
  laid over the field's text while it has the focus, in the same face at the
  same size, and takes the touches on it; Compose's own caret, selection,
  handles and toolbar stand aside for the field. Paste, Cut and drops from the
  browser's menu reach the field. A field whose text is drawn as something else
  — a phone number grouped as it is typed — keeps Compose's. See
  [installing](installing.md#per-platform).
- **In a browser, tabs, checkboxes, switches and radio buttons are named as
  themselves**, with their checked or selected state, where they were all
  buttons in the browser's accessibility tree. Compose 1.12's web layer names
  any clickable node a button and writes no state; the library's own controls
  put theirs back. See [In the browser](accessibility.md#in-the-browser).
- **A page sliding towards a [`NavRail`](components/nav-rail.md) goes under
  it.** It went over the rail briefly and was then cut off. The rail draws over
  what is beside it now, in a `NavigationSuiteScaffold` or anywhere else.
- **A large or hero [`TopBar`](components/top-bar.md) follows the finger**,
  where it could stop half collapsed or short of open. Pulled open at the top
  of a list, it opens over its own height, so the list comes down with the
  finger, where a bar with less height than its title's journey opened slower
  than the pull. Let go part-way, it finishes the way the finger was going
  rather than to the nearer end, and a fling that carries the list back to its
  top opens it.
- **A large or hero bar lands where a flick sends it**, on a screen only just
  long enough to scroll as much as on any other. A list that can barely scroll
  spends none of a fling, so the spring that finishes the bar was handed all of
  it, carried the bar past open or past collapsed and brought it back, with the
  content under it going there and back too. The bar now stops at its end.
- **[`StateSwitcher`](components/empty-state.md) lays each state out as a
  column**, where a state with several sections drew them on top of each
  other. A state that wants a box writes one.
- **The JVM artifacts are Java 11 bytecode.** `:ui` and `:ui-nav3` were the
  toolchain's Java 25, and with the builders inline, an app using them had to
  compile for Java 25 too.

- **The icons are Tabler 3.49.0**, from 3.48.0: `io.kontour:tabler-icons-outline`
  and `-filled`, which the library's own icons are drawn from.

### Added

- **An [`EventCalendar`](components/event-calendar.md)**: events in a day's or
  a week's hours, a month of day boxes, or a year of small months — a calendar
  of events rather than a date picker — with `rememberEventCalendarState`,
  `EventCalendarState` and `EventCalendarDefaults`. `CalendarEvent` is
  wall-clock and half-open. The app keeps the events; the calendar reports
  presses on an event, a day and an empty time, and a day pressed anywhere in a
  month is `onDateClick` where there is one and its day view where there is
  not. The header steps, goes to today and switches the view; a swipe pages as
  the date picker's months do; going back to today pulses today's date. The
  year opens at the month it is on and fills its months in a frame apart.
- **An event calendar's hours**: each event as tall as its time — an hour is
  64dp by default, `ComponentDefaults.eventCalendarHourHeight`, so a
  quarter-hour is 16dp with its title on one line — and overlapping events
  sharing the day in lanes, running on across free ones. The hours are
  labelled `9 am`, on one line.
- **An event calendar's edits.** Events are drawn out, carried and stretched —
  by finger, mouse, keyboard or a screen reader's actions — in steps of `snap`,
  and reported through `onCreateRequest`, `onEventMove` and `onEventResize`;
  deleted through `onEventDelete`. A carry keeps to its day; an edge keeps to
  its own day, and a bar's ends move a day at a time. What is carried is shown
  as a lifted copy on its step that leans toward the pointer and hops to the
  next step, day or week as the pointer crosses into it. Held at either side
  of the days, the date range picker's arrow fills, with its rumble, and the
  carry pages on to the next week or month and goes on following the pointer.
- **Holding an event in an event calendar.** A finger held still opens a menu,
  and moving on instead drags; a right-click opens the same menus. From the
  moment it is held, an event in the hours is widened to its whole day over a
  fifth of a second, its title laid out once, and the other events fade back.
  On a touchscreen, **Adjust time** gives its edges handles, taken from 20dp
  inside the event or 16dp outside it; on an event under 40dp tall the way the
  finger sets off picks the edge. The press that takes the handles away does
  nothing else.
- **An event calendar's menus are the app's to build**: `eventMenu` and
  `emptyMenu`, as any menu is, with the calendar's own actions as calls in them
  — `adjustTime()`, `delete()` and `newEvent()` — each drawn only where it
  applies. By default **New event** on empty time, **Adjust time** and
  **Delete** on an event. Null turns a menu off.
- **An event calendar from the keyboard.** The grid is one stop with a cursor
  the arrows move — a day in a month, a slot of time in a day or a week —
  paging past the period's ends; Enter opens the day or the time, and Tab goes
  on to the events of the cursor's day. Space picks an event up and the arrows
  move it.
- **Coach mark tours across the app, a step that points at nothing, a way
  between steps, and any colour** — [coach marks](components/coachmark.md).
  `rememberCoachmarkTour` takes a `navigate`, told each step as the tour reaches
  it, to take the reader to the screen it is on; the dim stays up while the
  screen changes, and a step whose control never turns up is passed over.
  `CoachmarkStep(tour, id, title, message)` is a step with no control, a card in
  the middle of the screen, for a welcome. `transition` is
  `CoachmarkTransition.Morph` — the mark shrinks into a ball that keeps its
  corner, travels to the next step clearing a little of the dim round itself,
  and grows into its mark — or `Fade`, the next mark fading in as the last
  fades out, under the one dim. `dismissible = true` lets a tap outside, Back
  or Escape end it; `showSkip = true` adds a Skip to every bubble but the last,
  and `tour.skip()` does the same. `tone` on `Modifier.coachmarkStep`,
  `CoachmarkStep` and `Modifier.coachmark` draws a mark in any `Tone`.
- **[`FieldPicker`](components/date-field.md)**: where a date, time or
  date-and-time field opens its picker — a sheet on a phone, a dialog on
  anything wider — so all three open the same way.
  `FieldPicker(visible, onDismissRequest, title) { picker }`.
- **`now` on a [`TimePicker`](components/time-picker.md) and a
  `DateTimePicker`**, for the Now button: `TimePickerDefaults.currentTime` and
  `DateTimePickerDefaults.currentDateTime` read the system clock, and are the
  defaults.
- **`rememberScrollbarListState()`**: a lazy list state whose list measures
  its rows ahead of showing them, up to 200, so a
  [scrollbar](components/scrollbar.md) on it is the list's true length from the
  first few frames and reaches the end of its track exactly with the list. On a
  plain `rememberLazyListState()` the bar still estimates the rows it has not
  seen.
- **A [scrollbar](components/scrollbar.md) keeps clear of bars over its
  scroller**: `contentPadding` on `Modifier.scrollbar` and `Scrollbar`, usually
  the padding the list's own content keeps clear of, so the track stops short
  of a top bar or a floating tab or nav bar.
- **A [hero bar](components/top-bar.md#hero-a-large-bar-over-a-picture) that
  follows its list.** `rememberTopBarCollapseState(list)` — or `(scrollState)`,
  each with an `initialOpenHeight` — makes a collapse read from the list's own
  scroll, for a bar drawn over the list, which pads its top by the state's new
  `openHeight`. The list is the only thing that scrolls, so the two never share
  a drag: a hero laid out above its list could spend the start of a drag on
  the system's stretch instead of collapsing. A collapse left part-way also
  stays where the list is.
- **`PullToRefresh(indicatorPadding = …)`** puts the indicator below a bar
  drawn over the list, in the gap a pull opens, for a hero that follows its
  list. Transit's departures are wired this way.
- **A day's own colour and a few characters under its number**, in a
  [`DatePicker`](components/date-picker.md#what-the-app-has-to-say-about-a-day),
  `DateRangePicker`, `DateTimePicker` and `CalendarMonth`.
  `containerColourFor` fills a day, under the selection and the range;
  `annotationFor` gives a `DayAnnotation`, its text, colour and what a screen
  reader says after the date. Given annotations, every week is a line taller,
  and every day's number sits at one height.
- **`SheetPartPin.Collapsed`**: a sheet's part at the head of its list that is
  all that is left as the sheet collapses to its parts, carried to the top as
  the rest fades. **`fadeUnder`** on `BottomSheet` and `ModalBottomSheet`.
- **`otherPlaceholder` on a [`Select`](components/select.md) and a
  `MultiSelect`**, for the hint in the "Other" box. `otherLabel` already named
  the option in the app's own words; the example now shows it.
- **`supporting` and `errorMessage` on a [`DateField`](components/date-field.md)**,
  `DateTimeField` and `TimeField`, as a text field has.
- **A [swipe action](components/swipe-actions.md) can turn its own tick
  off, or on**: `fullSwipeConfirmation` and `tapConfirmation` on a
  `SwipeAction`, which win over the row's, where only a whole row could. Null,
  the default, leaves it to the row.

---

## 0.4.2 — 4 October 2026

Feedback from building an app on 0.4.1. A loading state and a way to move
between states, a scrollbar that shows under touch and goes on any scroller, a
row of buttons for a form dialog, date fields, money in the theme's formats,
builders that read the theme where they are written, and a "More" for a bar with
more destinations than room. Fixes for overlays left up when their caller went,
a select's options and a search field as a screen reader meets them, a table
column fitted to more than its first rows, swipe back over a moving list,
`LoadMore` in a plain column, helpers in a navigation group and a POSIX browser
locale. The docs gain recipes for images from the network and for a question
that needs more than an answer.

A lambda literal compiles everywhere as it did, but a builder held in a variable
and a few arguments passed by position need a change, and anything built
against 0.4.1 has to be recompiled — see **Breaking**, and
[Migrating](migrating.md#041-to-042) for the code before and after. Four things
look different without any change: an app with no `WindowSizeClassProvider` now
gets a rail and two panes on a wide window rather than a phone's layout, a
floating `NavBar` sits lower over a home indicator, with the page fading out
under it (`fadeUnder = false` keeps the page as it was), a date picker given
no icons has arrows either side of its month, and a bottom sheet's top corners
on a round phone stop at 40dp.

### Breaking

- **[`ListGroup`](components/list-item.md), [`KeyValueList`](components/key-value-list.md),
  [`Stat`](components/stat.md) and the readouts of [`Gauge`](components/gauge.md),
  [`Meter`](components/meter.md) and [`Knob`](components/knob.md) take
  composable builders**, so `Theme.formats`, `Theme.strings` and a string
  resource are read where a row or a figure is written —
  `item("Departs", supporting = Theme.formats.time(departure))`,
  `value(Theme.formats.currency(total, "AUD"))` — rather than hoisted above it.
  All six are `inline`, as `Column` is, so the builder runs in the caller's
  composition and a state it reads recomposes what draws it. **A lambda literal
  compiles as before; a builder held in a variable of the old, non-composable
  type, or a plain function passed for one, no longer does** — mark it
  `@Composable`. `listGroup` in a `LazyColumn` is unchanged.
- **A selection control's `interactionSource`, or a button's or a text field's
  `variant`, passed by position no longer compiles.** `Checkbox`,
  `TriStateCheckbox`, `Switch`, `RadioButton`, `Button`, `TextButton` and
  `SplitButton` take a `contentDescription` after `enabled`; `TextField` and
  `TextArea` take `code` before `variant`; `NavBar` takes `fadeUnder` before
  `windowInsets`, and `NavigationSuiteScaffold` `barFadeUnder` after
  `railExpandedWidth`. Each is a different type from what was in its place, so a
  call that passed those arguments by position fails to compile rather than
  meaning something else. Name the argument.
- **Recompile rather than swap the jar.** The six builders above are `inline`
  now, and `Dialog`, `NavBar`, `NavigationSuiteScaffold`, the selection
  controls, the buttons, the text fields, `ComponentDefaults`, `LocaleFormats`
  and `Strings` gained members,
  so a library built against 0.4.1 fails when it calls one until it is rebuilt.

### Changed

- **A [bottom sheet](components/bottom-sheet.md)'s top corners stop at 40dp** on
  a round display. They followed the display's corners all the way — an iPhone's
  55pt, a newer Pixel's 51dp — at any height, and at half height that curve sat
  close to the header's text. A
  display rounder than the theme's 34dp still rounds them, to 40dp at most; at
  full height the display's own corner covers them either way. A theme whose
  sheet corner is rounder than 40dp keeps it.
- **A [`PageIndicator`](components/page-indicator.md#dragged-along-page-by-page)'s
  scrub pill fades in as it grows** and is its colour from half its thickness
  on, and fades as it shrinks back. The line through the dots it starts as was
  drawn at full colour, a hard thin bar for a frame or two. The fade rides the
  same spring as the size.
- **Under touch, a [scrollbar](components/scrollbar.md) shows while the content
  moves**, thinner, and fades 600ms after it stops, where it used to draw
  nothing at all. While it is drawn a finger on its thumb takes hold of it and
  drags the content, as a mouse does. Only the thumb: the
  rest of the strip at a list's edge is the list's, which it was not under an
  `alwaysVisible` bar. With a keyboard it is drawn at rest, as with a mouse and
  as the docs always said.
- **Without a `WindowSizeClassProvider`, the [window size class](components/window-size-class.md)
  is the window's**, read by `KontourTheme`, where it used to be a phone. Apps
  that leave the provider out — the root setup has none — now get a rail and two
  panes on a tablet or a desktop window rather than the bottom bar. A provider
  still measures a region, and wins inside it; a class provided outright is
  kept.
- **A floating [`NavBar`](components/nav-bar.md#where-a-floating-bar-sits-and-what-is-under-it)
  sits just clear of iOS's home indicator and Android's gesture handle**, about
  28dp up from the window's foot, rather than above the whole strip the system
  keeps for them: 14dp lower on an iPhone, 4dp lower over a gesture handle.
  Android's three buttons, and a docked bar, keep the whole inset, as does a
  `windowInsets` taller than the handle's, which is kept as given. There is no
  switch for this: it is where the bar belongs. The scaffold's padding comes
  down with it.
- **The page fades out under a floating bar**, which stands off it on a shadow
  a little deeper than the theme's `high`. Over the rows of a list, which are
  `surface` as the bar is, it was a shadow apart from them. `fadeUnder = false`
  (`barFadeUnder` on `NavigationSuiteScaffold`) leaves the page as it is and
  gives the capsule a rim, a hairline of its own ink, instead. The fade is the
  colour the bar sits on, `LocalContainerColour`.
- **An [`AlertDialog`](components/alert-dialog.md)'s buttons stay on screen
  under a long message**, which scrolls, rather than scrolling away below it.
  A dialog's content fades at the edge it scrolls under while there is more
  past it.
- **A [`DatePicker`](components/date-picker.md#the-header-is-the-way-out-of-paging),
  `DateRangePicker` and `DateTimePicker` have their paging arrows by default**:
  the library's chevrons, back and on in the reading direction. Given no icons
  they had no buttons either side of the month, and paged only by a swipe,
  which in a dialog left no visible way to change month. `previousIcon = null`
  and `nextIcon = null` leave them out.
- **A [toast](components/toast.md) stands 8dp off its edge**, the window's top
  or foot inside its insets, and 8dp above a navigation bar, where it stood 16dp
  off the window and 28dp above the bar.

### Added

- **Several pictures behind a `Hero` [`TopBar`](components/top-bar.md)**, from
  the library's own carousel and page indicator. Give `image` a
  `HeroImages(state) { page -> … }` and the picture is a
  [`Carousel`](components/carousel.md), one page at a time, swiped anywhere
  that is not a button, with its [`PageIndicator`](components/page-indicator.md)
  at the bottom end of the bar, over the fade, on the last line of the title
  block, which stops short of it.
  The dots go up with the title as the bar collapses and are gone a third of
  the way in. Decorative like a single hero picture, so hidden from screen
  readers, dots and all; outside a hero bar it is a carousel announced as
  `Theme.strings.images`, with its dots at the foot.
- **"Other" on a [`Select`](components/select.md) and a
  [`MultiSelect`](components/multi-select.md)**, for an answer none of the
  options give. Pass
  `onOtherTextChange` and the menu ends with "Other"; picked, a filled text
  field opens inside the outline, under the value, inset for concentric
  corners, and takes the keyboard. `otherText` holds what is
  typed, and picking an option instead clears it, so the answer is
  `otherText ?: value`. The words are `Theme.strings.selectOther` and
  `selectOtherHint`, or `otherLabel`. Nothing changes for a select not given it.
- **[Drag and drop](drag-and-drop.md) across the app**, so a card moves
  between a board's columns, which `ReorderableItem` could not do.
  `Modifier.dragSource` carries a typed payload, `Modifier.dropTarget` with
  `rememberDropTargetState` takes it, and `rememberDropListState`,
  `Modifier.dropList` and `dragItems` make a lazy list both. Each drop is one
  `onDrop`, with the index it lands at and where it came from, and nothing in
  your lists moves before then. The list opens a gap where the card would land,
  and scrolls as the card nears either end; anything else that scrolls takes
  `Modifier.dragAutoScroll`. The nearest `OverlayHost` draws the carried card
  above everything, so it can leave a sheet. Every move also works without a
  pointer: *Move to Done*, *Move up* and *Move down* for a screen reader, Space,
  Enter and the arrows for a keyboard, and each step is said aloud through
  `Theme.strings`.

- **[`FileField`](components/file-field.md)**, and **`rememberFilePicker`**
  under it, which opens each platform's own picker: Android's document picker, iOS's,
  a desktop file dialog and a browser's file input. Each file is a chip with its
  name and size, in one row that scrolls sideways so the field keeps its height;
  a single file clears with the button where the attach glyph was. `FileTypes` says what to
  offer (`image/png`, a whole family, `.pdf`), and what comes back is checked
  against it again. What is picked arrives as `PickedFile`s, read on demand.
  `LocalFileChooser` swaps the picker for a test's or an app's own. Files
  dragged in from outside the app can be dropped on the field too. Before, it
  took a button and a text area. On Android, `:ui` now depends on
  `androidx.activity:activity-compose` for the picker's result registry.

- **Drops from outside the app**: files from the desktop, the Files app or
  another window, and text. A drop target or a drop list takes an
  `ExternalDrops` — `ExternalDrops.files(types)` for files of some types — and
  hears each drop once, as `PickedFile`s read on demand and, on a list, the
  position it was let go at where the platform says (a desktop and Android; a
  browser and iOS do not). A list shows a line where it would land. On Android
  the drop's read permission is asked for through the activity, and lasts as
  long as the activity does.

- **[`DropZone`](components/drop-zone.md)**, a drop target that shows where a
  drag stands with it — a dashed place at rest, the accent while something it
  would take is carried, the accent's ground over it, the danger colour's when
  it would turn the thing away — laid out as an `EmptyState`, with room for a
  picker beside the drop. `rememberDropTargetState(label, external)` makes a
  target that takes only drags from outside the app, for an upload area.

- **[`Button`](components/button.md), [`TextButton`](components/text-button.md)
  and [`SplitButton`](components/split-button.md) take a `contentDescription`**,
  read in place of the label where the label alone does not say enough — "Save"
  on screen, "Save this trip to Perth" read aloud. It took a `semantics`
  modifier of your own. An [`ExtendedFloatingActionButton`](components/fab.md)'s
  description is now its name open as well as collapsed, as its documentation
  said; open, the label was.

- **[`SecretField`](components/specialised-fields.md#a-key-is-not-a-password)**
  for an API key, a token or a recovery code: masked, revealed and showing the
  character just typed as `PasswordField` does, and not a password to the
  platform — no autofill hint, an ASCII keyboard, the mono face — so nothing
  offers to save a key as the reader's login. On iOS the keyboard may learn from
  it, as it does from any field that is not secure entry; the page says what
  each platform still does. Its toggle reads `Theme.strings.show` and `hide`.

- **[`TextArea`](components/text-area.md#code) and
  [`TextField`](components/text-field.md) take `code = true`** for a snippet, a
  config file or an identifier: the text and placeholder in the theme's mono
  face, at the field's own size, and a keyboard asked for no autocorrect and no
  capitalisation. The fields take no text style, so there was no asking for one.

- **`Theme.formats.currency(amount, code)`** writes an amount of money as the
  reader writes it — `€3.20`, `3,20 €`, `￥320` — with the currency's own places,
  from the platform's CLDR. See [`LocaleFormats`](components/locale-formats.md#money).

- **[`LoadingState`](components/empty-state.md#loading-and-the-move-between-them)**,
  the third of the states beside `EmptyState` and `ErrorState`, drawn as they
  are with a spinner where their icon is.
- **[`StateSwitcher`](components/empty-state.md#loading-and-the-move-between-them)**
  puts loading, empty, failed and the content in one place and moves between
  them when its `target` changes: the state leaving fades through the one
  arriving, and the space they take changes size smoothly. Under reduced motion
  it cross-fades and takes the new size at once.

- **`contentDescription` on [`Checkbox`](components/checkbox.md),
  [`TriStateCheckbox`](components/tri-state-checkbox.md),
  [`Switch`](components/switch.md) and [`RadioButton`](components/radio-button.md)**,
  for one that stands alone with no row to name it — a select-all box over a
  table. It came out as an unnamed control. In a `SelectionRow` the row's label
  is still the name; leave it `null` there.

- **[`DateField`](components/date-field.md) and `DateTimeField`**, the tappable
  field a form shows for a `DatePicker` and a `DateTimePicker`, as `TimeField`
  is for a `TimePicker`. A `DateField` with no date yet shows its placeholder.

- **[`SegmentedControl`](components/segmented-control.md) takes values**:
  `value`, `options`, `onValueChange` and `optionLabel`, as `Select` and
  `RadioGroup` do, so nothing maps an index back to an option. The overload
  taking strings and an index stays.

- **[`Modifier.scrollbar(state)`](components/scrollbar.md)** puts a scrollbar
  on the scroller itself — a `Column` before its `verticalScroll`, a
  `LazyColumn`, a grid, a `LazyRow` with `Orientation.Horizontal` — with nothing
  to wrap it in and nothing to align. `Scrollbar` is the same bar as a component
  of its own.

- **[`Dialog`](components/dialog.md) takes `actions`**, the row of buttons an
  `AlertDialog` draws: `actions = { cancel(); confirm("Save", enabled = valid) { … } }`.
  The row sits under the content rather than in it, so a form taller than the
  window scrolls and its Save stays on screen. The builder is composable, so a
  label can come from `Theme.strings` or a string resource where it is written.
  It is also how to ask a question that needs more than an answer — type the
  account's email to delete it, give a reason — with a field above a confirm
  that waits for it; [the alert dialog's page](components/alert-dialog.md#a-question-that-needs-more-than-an-answer)
  has the recipe. A destructive `confirm` warns once as the dialog arrives, as
  a destructive `AlertDialog` does, and anything else written in the row — a
  button of your own — is drawn too, leading it. An `AlertDialog` keeps its
  fixed shape.

- **A [`NavBar`](components/nav-bar.md#dragging-the-marker)'s marker can be
  dragged to a group.** Let go over one and its menu opens, and the group keeps
  the marker while it is open; closed without a pick, the marker goes back to
  where it was. A drag across a group to the destination beyond opens nothing.

- **[`more { }`](components/nav-bar.md#more-than-a-bar-has-room-for)** on any
  `NavScope`: the destinations a bar has no room for, behind a circle of three
  dots at its end, named `Theme.strings.more`, that opens a menu of them. A
  group with no icon, name or key to choose. A rail has room, and draws them in
  line, so one block reads right in a `NavigationSuiteScaffold`.

### Fixed

- **[`SegmentedControl`](components/segmented-control.md#when-the-labels-do-not-fit)'s
  labels keep apart at phone width.** A segment keeps
  `SegmentedControlDefaults.LabelPadding`, 4dp, either side of its label, and the
  control stacks its options rather than let two labels closer than 8dp. A label
  could fill its share to the last dp, and "Standard" met "Keyboard" with nothing
  between them. A control that fitted with a few dp to spare now stacks: the
  gallery's settings at 130% text, for one.
- **`PasswordField(newPassword = true)` tells autofill it is a new password.**
  Its hint was on the field's frame, which no autofill service reads; the input
  said `Password`, from the password keyboard, so a password manager offered a
  saved password where it should have offered to make one. The specialised
  fields' hints are now on the input itself.
- **On an iPhone or iPad, the keyboard opens on the tap that focuses a text
  field in the browser.** WebKit opens it only for an input focused during the
  tap, and Compose focuses its own a frame later — later still for a combobox,
  the nav bar's search and a knob's typed value, which focus from an effect.
  `KontourTheme` installs a primer that holds the keyboard open for them. See
  [installing](installing.md#per-platform); untested on a device here.
- **An [`Avatar`](components/avatar.md) or a [`Badge`](components/badge.md)
  inside a row no longer names it.** A clickable `Card`, a `ListItem`, a
  `SelectionRow` and a button each merge into one node, and a description in it
  is read instead of the row's text — so a chat row was announced as its person,
  or as "3 unread", and nothing else. Inside one, an avatar says no name and a
  badge says itself as text, read with the rest of the row; an `AvatarGroup` is
  "3 people" rather than "3 people, Ada, Grace, Katherine". An avatar given a
  `contentDescription` still says it.
- **An edge [bottom sheet](components/bottom-sheet.md#partpin----what-stays-put-while-the-sheet-moves)
  collapsed to a part pinned to its top clears the safe area.** The pinned
  detent counted the safe area only under a part pinned to the bottom, so with
  the part at the top the sheet came to rest with its foot behind the home
  indicator. Not new in this release — the
  arithmetic is 0.3.0's.
- **A [coachmark tour](components/coachmark.md#coachmarktour) dims the page
  once, from one step to the next.** Each step had a dim of its own, and the
  next arrived while the last was leaving, so the page went twice as dark
  between steps and came back. The dim stays, and the hole and the bubble
  travel to the next control.
- **A menu's dividers can be seen on the menu.** They were `outlineSubtle`, the
  faintest rule, chosen for a list on `surface`; on a menu's raised panel that
  was 1.15:1 in light and, in dark, 1.06:1 and darker than the panel. A
  [`MenuDivider`](components/dropdown-menu.md), and every menu's `divider()`, is
  the menu's text colour at 12% now — 1.28:1 and 1.39:1, what a list's divider
  stands off a list — and the tier's `outline` at high contrast, as before.
- **A [`Table`](components/table.md)'s fitted column is measured against rows
  spread through the data** — the first twenty, the last ten and twenty between —
  rather than the first fifty alone, and again, widening only, when `items` is a
  new list. A wider date past the first rows was cut off as it scrolled into
  view, and data loaded later never refitted the column.
- **A [`SearchField`](components/search-field.md) is named by its placeholder**,
  and "Search" without one, as are the fields in `NavSearch` and the command
  palette. It had no name at all — a screen reader announced an edit box — and
  nothing from outside could give it one.
- **A [`Select`](components/select.md)'s and a
  [`Combobox`](components/combobox.md)'s options are each a radio button, chosen
  or not, in one group.** Only the chosen one was; the rest announced as
  buttons, so nothing said they were choices. A
  [`MultiSelect`](components/multi-select.md)'s checkboxes now say whether they
  are ticked. A menu of actions is unchanged.
- **A `Dialog`, `AlertDialog` or `CommandPalette` that leaves composition while
  it is up goes with it**, as a popover, a menu and a sheet already did. It
  stayed on screen when its caller went — a screen navigated away from, an `if`
  round it turning false — with nothing left to close it. A dialog whose `key`
  changed while it was up was shown twice. The toast stack's host is the same.
- **A scrollbar on a grid, a staggered grid or a pager is drawn.** It read only
  `ScrollState`s and lazy lists, and drew nothing for anything else; it reads
  the position every scroller in foundation reports now.
- **A browser reporting a POSIX locale no longer blanks the page.** Headless
  Chromium's `navigator.language` is `en-US@posix`, and Compose's `Locale(tag)`
  on the web is `new Intl.Locale(tag)`, which throws on it, through the first
  composition. The browser's languages are cleaned — the `@` modifier and the
  charset dropped, `_` made `-` — and the first `Intl` accepts is used, with
  `en-AU`, the library's own default, when none is. Compose's own
  `Locale.current` reads the same list through the same constructor if anything
  inside Compose asks for it; that half is Compose's.
- **[`LoadMore`](components/load-more.md) asks for the next page when it is on
  screen, not when it is composed.** At the foot of a plain scrolling `Column`,
  which composes everything at once, it chained through every page to the end
  before anyone scrolled. It now waits until some of it is in the window; a
  lazy list's prefetch no longer asks early either.
- **A destination written through a helper draws where the helper is called.**
  A helper written in a navigation block — `fun stop(id: String) = item(…)` —
  closes over that block's scope, and called inside a `group { }` it drew as
  the outer scope's: over a shut group in a rail, unindented in an open one,
  as a circle of its own in a bar, and opening the bar's menu claimed its key
  twice and threw. Every destination call now draws through the scope composing
  the place it is called from. `NavScope` already carried a DSL marker; that
  refuses an implicit outer receiver, but cannot see a captured one.
- **On iOS, swiping back works over a list that is still moving.** A list moving
  from a throw takes the press that lands on it, to stop, and the swipe back
  read that as a control taking the press and left it alone — from the very
  edge as well, and for seconds after a throw, which keeps moving long after it
  stops looking like it. A press that catches a list moving up or down no longer
  counts: a sideways swipe goes back, and one up or down is still the list's.

---

## 0.4.1 — 4 October 2026

A `Hero` bar a mouse wheel could not open again, a segmented control's thumb
that jiggled on a click, and a page indicator you can drag along. Nothing
breaks: an app on 0.4.0 moves to 0.4.1 by changing the version. To get the
wheel fix, put `Modifier.topBarCollapse(state)` where
`Modifier.nestedScroll(state.nestedScrollConnection)` was.

### Added

- **`Modifier.topBarCollapse(state)`** wires a
  [collapsing top bar](components/top-bar.md#large-and-how-it-collapses) to the
  content's scroll. It takes the place of
  `Modifier.nestedScroll(state.nestedScrollConnection)`, and answers a mouse
  wheel as well, below. The connection is still there for wiring the scroll
  yourself.
- **A [`PageIndicator`](components/page-indicator.md#dragged-along-page-by-page)
  can be dragged along.** With `onPageClick`, a press that moves along the dots
  goes to each page it passes, with a `Snap` for each. A pill in the theme's
  `pill` shape grows out of the dots while it is held, and back into them on
  release: `surfaceSunken` in the light, `outline` in the dark. Across or down,
  as the strip runs. A drag used to be a tap wherever it ended.

### Fixed

- **A `Hero` bar scrolled away with a mouse wheel opens all the way when it is
  scrolled back.** It stopped part-way: a list at its top declines a wheel's
  notch before it becomes a scroll, so the bar opened by what was left of the
  notch that brought the list home, and no further. A `Large` bar's collapse is
  short enough for that remainder to cover, and a `Hero` bar's is not. With
  `topBarCollapse` on the content, a notch the list declines finishes the bar the
  way the wheel turned, open or collapsed. Put it where
  `Modifier.nestedScroll(state.nestedScrollConnection)` was to get this.
- **A clicked segment's thumb goes straight across.** A press leans the thumb
  toward the segment, and on release the lean sprang back while the slide began
  from rest, so the thumb stepped back before it went: a jiggle on every mouse
  click, and on a held tap. The slide now starts from where the lean left it.
  The same goes for a `NavBar`, `NavRail` or `TabBar` marker.

---

## 0.4.0 — 3 October 2026

Pages that cover the nav bar on one back stack, a back button that knows its
page, two new components, a top bar over a picture and a tag inside a chip, a
sheet that reaches the top
of the window and clears the keyboard once, and one break: `markerDraggable`.
[Migrating](migrating.md#03-to-04) has everything an upgrade from 0.3 has to
change, as code.

### Breaking

**`NavBar(markerDraggable)` is now `markerDrag: NavBarMarkerDrag`**, and on
`NavigationSuiteScaffold` `barMarkerDraggable` is `barMarkerDrag`.
`markerDraggable = false` is `NavBarMarkerDrag.Off`, and the default is
`NavBarMarkerDrag.SelectOnRelease`, as it was. The new
`NavBarMarkerDrag.SelectWhileDragging` chooses each destination as the marker
reaches it. See [Migrating](migrating.md#markerdraggable-is-now-markerdrag).

### Changed

- **A [`WheelSelect`](components/wheel-select.md#when-to-use-it) keeps its value
  on the field near the top or foot of the window.** It opens about the field
  where there is room above and below, as before. Where there is not, it opens
  away from the edge: the rest of the wheel runs to the side with room, as a
  `Select`'s menu picks its side, and half of the next row fades out on the
  other. Before, it was pushed along to fit, and its value was no longer on the
  field.
- **`Modifier.parallax` is subtler, and stops when the page does.** A spring
  chased its offset and was started over on almost every frame of a scroll, so
  the content moved rigidly with the page and then glided on by itself for half
  a second after it stopped, by up to 30dp near the window's edges. The offset
  now follows the page directly, eases off towards 16dp, and the default
  `ScrollEffectDefaults.ParallaxStrength` is `0.05`, down from `0.08`.
- **Icons come from Kontour Labs' own build of Tabler**,
  `io.kontour:tabler-icons-outline` and `-filled`, in place of composables'
  `icons-tabler-*-cmp`: every icon of Tabler 3.48.0, where composables' stopped
  at an older release. The library's few system icons are drawn from it, so it
  comes with the library, from the same GitHub Packages repository. The API is
  the same under `io.kontour.icons.tabler`; a few glyphs Tabler has redrawn
  since, the star among them, are a pixel or two different.
- **A coach-mark tour's last step says Done**, beside Back, and the way on is
  filled in the bubble's ink so it reads as a button. It said "Got it", and
  next to Back as two ghost buttons on the accent the pair read as words. A
  tour of one step still says "Got it". `Strings.done` is new.
- **A swipe row's actions are at most 64dp tall**, a two-line row's height,
  however tall the row. On a taller row they sit centred, and a full swipe's
  takeover grows across the strip and not down it. Before, they were as tall
  as the row. `SwipeActions(maxActionHeight = Dp.Infinity)` keeps the old
  height.
- **`StepProgress(gap = 0.dp)` is one continuous bar**, with no seams and a fill
  that stops round at the end of its step. Before, its segments met at their
  round ends and the bar pinched at every step.
- **A disabled control is quieter than an enabled one, everywhere on it.** A
  switch's thumb, a slider's and a range slider's thumbs and a knob's face are
  the page's colour, where the switch's was near-black in light and white in
  dark. A slider's thumb is ringed in the disabled grey. A disabled knob is
  drawn in that grey rather than faded to 38%, which kept a coloured scale
  coloured. A disabled rating's empty marks are fainter than its filled ones. A
  stepper's value, and a field's placeholder, are in the disabled grey. See
  [Accessibility](accessibility.md#disabled-is-quieter-everywhere-on-the-control).
  `SliderDefaults.colours(thumbDisabled)` now defaults to `surface`.
- **A checkbox, radio button or switch in a disabled `SelectionRow` or
  `ListItem` draws itself disabled.** Before, it looked as if it could still be
  changed unless it was given `enabled = false` as well.

- **A `NavBar` with labels is the height of one without.** A labelled
  destination's icon is 20dp, two points above its label, and the two are
  centred in the touch target: a floating bar is 64dp with labels or without,
  where it was 82 with them. Its label and badge stay inside the marker round
  the current destination. A label is ellipsised where the marker's rounded
  bottom runs out, and a badge is moved in off the marker's edge. A bar with
  `NavBarDefaults.glyphMarker()` keeps its 40dp circle and the label under it.
- **A sheet at full height reaches the top of the window**, under the status
  bar, with its top corners the display's own, and its drag handle and header
  padded below the bar. So does one at `Expanded` whose content is taller than
  the window. Both used to stop 12dp short, and a tall `Expanded` sheet a status
  bar short. A floating sheet without `edgeMorph` keeps its margin.
- **A sheet's header keeps clear of a display's rounder corners.** On an iPhone,
  whose corners are about 55pt, or a newer Pixel's 51dp, against the sheet's
  34dp, the header, the drag handle's band and a part pinned to the top move in
  by what the rounder corner cuts into its diagonal: 6dp at 55. The title's
  corner had come within 5dp of the curve. A sheet with the theme's own corners
  is unchanged.
- **A modal opening takes the focus off whatever had it, in another overlay
  too.** A text field in a sheet lets go, and its keyboard goes, when a dialog
  opens over the sheet. Before, only the page lost its focus. A menu or a
  combobox's list leaves the field it opened from focused, as before.
- **Date pickers are a phone's size wherever they are, and shorter.** A day
  cell stops at 44dp, so a calendar stops at 308dp (it was 511dp, with digits
  half as large again); a week is at most 40dp tall where it was as tall as a
  day is wide; and the header and the weekday initials sit closer. At a
  phone's width a `DatePicker` is 307dp tall rather than 379dp. A chosen
  day is a circle as tall as its week, and the arrow a range's dragged end
  shows on a month's first or last day is sized to the day, beside its number
  rather than against it.
- **A command palette puts the closest commands first.** With something typed,
  the label that is the query comes first, then one that starts with it, then a
  word in a label, then a keyword, then the query inside a word; within each,
  the one it covers more of. It kept the caller's order, so the highlight, and
  Enter, could sit on a command that only had the query in the middle of a word.
  With nothing typed the order is the caller's, as before. The ranking is
  `commandSimilarity`; a `similarity` parameter beside `matches` takes another.

### Added

- **A selection marker reaches for the item being pressed.** In a `NavBar`, a
  `NavRail`, a `TabBar` and a `SegmentedControl`, a press on another item leans
  the marker a little toward it, stretching from the edge it is leaving, before
  the finger lifts and the marker goes there. A press that slides off lets it
  settle back. A dragged nav-bar marker now stretches as it leans toward the
  finger, as a segmented control's thumb does. None of it under reduced motion.
  See [`NavBar`](components/nav-bar.md#the-marker-reaches-for-a-press).
- [`CircularStepProgress`](components/progress.md): `StepProgress` round a
  ring, one arc a step from twelve o'clock, through the same animation.
  `gap = 0.dp` makes it one continuous ring.

- **`pageBack()`**, for a back button: going back from the page it is read in,
  or null when back would not leave it. `TopBar(onBack = pageBack())`. A button
  shown while `backStack.size > 1` appeared on the root page during a back
  gesture, because the stack holds the page leaving until the gesture ends. Set
  by `:ui-nav3`'s pages and panes and a one-pane `ListDetailPaneScaffold`'s
  detail; `LocalPageBack` and `rememberPageBack` for a host of your own. See
  [Navigation 3](navigation3.md#a-back-button-from-the-page).

- **Pages over the nav bar, with one back stack:** `:ui-nav3`'s
  `rememberNavigationSuiteSceneStrategy` puts a `NavigationSuiteScaffold`, or
  any frame, round the pages of a `NavDisplay`, so the bar stays put as they
  move, and an entry marked `coversNavigation()` slides in over the bar and the
  page under it. `navigationSuitePadding()` is the bar's height, for a page
  inside the scaffold that is not written in its `content`. See
  [Navigation 3](navigation3.md#pages-inside-the-nav-bar-and-pages-over-it).

- **A tag at the end of a chip:** `FilterChip { +"Buses"; tag { +"3" } }`. A
  count or a "New" inside the chip's pill, read with the chip as one, "Buses,
  3"; the chip's end comes in so the tag sits concentric with it. `Chip`,
  `FilterChip` and `InputChip` take their content as a `ChipScope`, which is a
  `RowContentScope` with `tag` added, so every existing chip compiles as it
  was. See [`Chip`](components/chip.md#a-count-at-the-end).

- [`TopBarStyle.Hero`](components/top-bar.md#hero-a-large-bar-over-a-picture):
  `TopBar(style = TopBarStyle.Hero, image = { … })` is a `Large` bar over a
  picture that runs under the status bar, collapsing into a `Small` bar as
  `Large` does. The picture runs under the title and fades into the bar's
  colour there, the title standing over the fade in the bar's ink, with a solid
  band at high contrast; the back button and actions sit on discs of the bar's
  colour until the picture has gone.
  `expandedHeight` sets how tall it opens: `TopBarDefaults.HeroHeight`, 360dp,
  from `ComponentDefaults.topBarHeroHeight`, unless you say. On `Large` it is
  the least the bar opens to.

- [`TopBar(fadeUnder)`](components/top-bar.md#content-fading-under-the-bar):
  content passing under a bar fades out beneath it rather than stopping square
  at its foot, the bar's ground thinning out over its last 12dp and gone 4dp
  past it. Any style, off
  by default, and none at high contrast. The bar has to be over the content, as
  a `Scaffold` puts it.

- [`Avatar(border)`](components/avatar.md#a-border): a ring round the avatar,
  any `BorderStroke` — a colour, the accent, a gradient — over the picture's
  edge and under its decorations.

- [`DateTimePicker`](components/date-time-picker.md): a `DatePicker` and a
  `TimePicker` as one `LocalDateTime`, side by side from 560dp and one at a
  time under a segmented control narrower. A day tapped keeps the time and a
  wheel turned keeps the day; `onValueChangeFinished` comes once a day is
  tapped or a wheel has come to rest.

- [`TextFieldVariant.Plain`](components/select.md): a field with no ground and
  no edge at rest, for a select at the end of a row or in a sentence — every
  select takes it, `Select`, `MultiSelect`, `Combobox` and `WheelSelect`, and
  stays outlined by default. Focus brings back the tint and the accent edge, an
  error its edge, and at high contrast the edge stays.

- **Wheels say when they have stopped.** `WheelPicker(onSelectedIndexChangeFinished)`,
  `WheelSelect(onValueChangeFinished)` and `TimePicker(onValueChangeFinished)`
  are called once a wheel comes to rest after the reader turned it, as a slider's
  and a rating's already were once the finger lifted. `onValueChange` still
  follows every row passed; a save, a request or a toast belongs in the new one.
  Not called when the caller sets the value.

- [`listSection`](components/list-section.md#in-a-lazy-list-listsection): a
  titled group of rows inside a `LazyColumn`, as `ListSection` is outside one.
  It draws the heading, each row's `position`, the hairline between rows, the
  footer and the space before the next section, which a lazy list's single
  `verticalArrangement` could not, and its rows slide when the list changes
  (fading in place under reduced motion). A section per `indexedBy` group is
  where an `IndexScroller`'s letters land.

- [`IndexScroller`](components/index-scroller.md): an A–Z rail beside a long
  list. Press a letter or run a finger down them and the list jumps to its
  section, with a tick a letter and the letter shown large beside the finger.
  Every letter stays on the rail, the empty ones dimmed and going to the next
  section; a short rail folds runs of letters into dots and still reaches each.
  `IndexAlphabet` files a word under its label in Latin, Cyrillic, Greek,
  Hebrew, Arabic, kana rows and Hangul initials, by the reader's locale, and
  `indexedBy` groups a list and says where each section starts. Four new
  `Strings`: `sectionIndex`, `otherSection`, `nextSection`, `previousSection`.

### Fixed

- **A card carried by `sharedBounds` keeps its shadow while it travels.** Its
  contents cross-faded in a layer as big as the card, and a layer at part
  opacity is drawn offscreen at its own size, so the shadow outside the card was
  cut off for the whole of a `PageTransition` and came back the frame it ended.
  The cross-fade now fades each thing as it is drawn, with no layer to be cut
  by.
- **A sheet over a text field is lifted clear of the keyboard once.** A bottom
  sheet, a side sheet, a dialog and a toast count only the part of each inset
  that reaches their container. In an app whose root had already made room for
  the keyboard, with `imePadding()` or a padded scaffold, a sheet was lifted by
  the whole keyboard again and stood a keyboard's height above it. On Android, a
  debuggable app whose window pans for the keyboard, which lifts a sheet twice in
  a way the library cannot see, now gets one line in its log saying so.
- **A drag that starts on another segment or destination ticks as the marker
  jumps to it.** Press a segmented control's third segment, or a nav bar's
  third destination, while the first is chosen, and drag on: the thumb or
  marker goes to the third with a tick, then on. That first jump was silent,
  because the detent was armed on the item under the finger rather than the one
  the marker was on. A segmented control's thumb also ran one pointer move ahead
  of the finger for the whole of a drag, so a first move that stopped just short
  of a boundary crossed it.
- **A rating's stars sit together on a touch screen.** Each 24dp star reserved
  a 48dp target of its own, so on a phone there was 28dp between one star and
  the next, more gap than star. The row takes every press itself, so it is the
  target now: at least a target tall, with the stars `RatingDefaults.Gap` apart,
  as a read-only rating's always were.
- **The month-change arrow in a range drag is easier to catch.** Dragging a
  range's end past the month's first or last day starts the month turning only
  once the finger has passed the little arrow there, and that meant the arrow's
  own row: a finger a little below the last week, or coming at it from just
  above, went past without starting it. It now catches half a row outside the
  grid and a third of a row toward the next week in. The blank beside the day,
  where a finger arriving from the week above lands, still does not.
- **A range dragged over days it may not have stops at the last it may.**
  Paged into a month that `isDateSelectable` closes part of, a range kept the
  last month's end and the whole new month was painted as in it, until the
  finger found a day it could have. Over a day it may not have, a dragged range
  now reaches as far toward the finger as it can, in any month, and paints
  nothing past that.
- **A sheet dragged to the top by its content climbs with the finger.** Pulled
  up from a lower detent by a scroll in its content, under a status bar, a sheet
  that reaches the top leapt the last status bar's height in one frame. Its
  content waits below the bar while its edge goes on to the top, and the peek
  detent, measured against the edge, changed with every pixel of that climb;
  each change rebuilt the anchors, and with nothing holding the drag a rebuild
  snaps to the nearest one. The peek is now measured in the content's own
  frame.
- **A bottom toast stands above the navigation bar.** A toast raised on a page
  with a `NavBar` came up over the bar, on top of the destinations. The bar now
  tells the overlay host where it is, and a bottom `ToastHost` stands a little
  above it, or above the window's insets, whichever is higher. A page that covers
  the bar brings the toasts back down with it; a bar partway down a page is left
  alone.
- **A quick back swipe on Android no longer stalls as it is let go.** The
  release read the finger's speed from the last 40ms of the platform's events,
  and a quick swipe can leave fewer than two there: measured as nothing, the
  page stopped on the lift and then picked up. It now takes the swipe's speed
  over its whole length where that is quicker, and never leaves slower than
  half its pace. A two-pane detail let go at the end, as the relay hands it
  over, pops at once rather than waiting out a fixed 200ms.
- **A toast has an edge over anything of its own colour.** The toast in front
  takes the rim the ones waiting behind it already had, a hairline of its own
  content colour: over a FAB, near-black on near-black, the two used to run
  together, and a black shadow shows on nothing that dark.
- **A list comes to rest once after a back gesture.** Back from a page over a
  `listPane()` entry, carried all the way by the gesture as a swipe's release
  does, finished, then the list jumped back three quarters of the way and came
  in again. An app that reads its own back stack builds its entries afresh on
  every pop, and the list's role was equal only to itself, so the scene the pop
  settled on was not the one the gesture had brought in. Every `listPane()` is
  now equal to every other.
- **A text area's placeholder starts at the top, where its text does.** It was
  centred in the area, a line or more below where the first character goes, and
  cut to one line however long it was. It now sits at the top start (the top
  right, right to left) and wraps over the lines the empty area has. In a
  single-line field the caret of an empty field sat a pixel below the
  placeholder and jumped up a pixel at the first character; it now starts on
  the placeholder's line.
- **An open search keeps clear of every edge.** `NavSearch` and
  `NavExpandingSlot` stood their panel on the keyboard and cleared only the
  bottom and the sides, so results tall enough to reach the top of the window
  ran under the status bar; hung from the top, long results ran under the
  keyboard. The panel now clears the window's insets and the keyboard on every
  side, wherever it stands, and both take a `windowInsets` for a caller that
  clears an edge itself.
- **A command palette keeps clear of the window's sides.** It took its width
  cap and nothing else, so on a phone it ran from one edge of the glass to the
  other. It now keeps `Theme.spacing.md` from either side, as a `Dialog` and a
  bar's expanding panel already kept a margin of their own.
- **A command palette's results fade at an end there are more of them.** They
  were cut off square at the panel's foot, and a row cut off at the edge reads
  as the last one. They now take `fadingEdges`, which stands down at high
  contrast as it does everywhere.

---

## 0.3.0 — 30 September 2026

Sheets that pin a part and keep one inset, a nav bar you can drag across, three
new components, and one break: `SettingRow`. [Migrating](migrating.md#02-to-03)
has everything an upgrade from 0.2 has to change, as code.

### Breaking

**`SettingRow` is removed. Use `SelectionRow(onClick = …)`**, which takes the
same content: `+"Theme"`, `leading {}`, and the current value in
`trailing {}`. Like `SettingRow`, it is a silent `Role.Button`, and
`onClick = null` shows a value that cannot be changed there. One default
differs:

- `SettingRow` defaulted to `position = GroupPosition.Only`, which drew the
  sunken card.
- `SelectionRow`'s `position` defaults to `null`, which draws a bare row.
- To keep the card, pass `position = GroupPosition.Only`.

A `SettingRow` that held a switch should be
`SelectionRow(selected, onSelectedChange, role = Role.Switch)`.
`ListGroupScope.setting(…)` in a `ListSection` is unchanged.
See [`SelectionRow`](components/selection-row.md#a-row-that-opens-a-choice).

### Changed

- **A swipe row's actions keep a lone row's shape in a group.** Before, they
  took the row's `shape`. In a group, a middle row's actions came out nearly
  square, and a first row's were rounded only on top. `SwipeActions` and
  `SwipeToDismiss` now draw their actions in `actionShape`, which defaults to
  `ListItemDefaults.Shape`. If your rows have their own corners, pass the same
  shape as `actionShape`.
- **A `NavBar`'s destinations share it in equal cells, and its marker is round
  the whole current destination**, glyph and label, in the bar's own shape: a
  capsule in a floating bar. Before, each destination was a circle and the marker
  a circle behind its glyph. For that look, pass
  `itemWidth = NavBarItemWidth.Natural` and
  `marker = NavBarDefaults.glyphMarker()` (on `NavigationSuiteScaffold`,
  `barItemWidth` and `barMarker`).
- **A `NavBar`'s marker can be dragged** from destination to destination, with
  a snap at each. The destination under the finger when it lifts has its
  `onClick` run once, and a tap is still a tap. `markerDraggable = false` turns
  it off, and on `NavigationSuiteScaffold` it's `barMarkerDraggable = false`.
- **A tap on a `NavBar`'s surface between destinations stays on the bar.**
  Before, it reached the page beneath.
- **A floating `NavBar`'s ends match the gaps between its destinations.**
  Before, the ends had about a third more room. A bar given its own
  `arrangement` keeps the old padding.
- **`NavigationSuiteScaffold`'s `action` sits beside a floating bar**, not
  inside it. A docked bar keeps it at the end.
- **A `Large` `TopBar` collapses by carrying its title into the bar, and the bar
  shrinks.** Before, the title faded between two sizes at a fixed height.
  Its subtitle travels with it, and the collapsed bar is a `Small` bar with
  the subtitle under its title, where before the subtitle faded out.
  Because the bar now shrinks, a list under it moves too, so driving
  `collapseProgress` from the list's scroll now moves the rows twice as far as
  the finger. Pass `collapseState = rememberTopBarCollapseState()` instead, and
  give the list its `nestedScrollConnection`. The bar then takes the scroll it
  gives up, and the list takes the rest.
- **On Android, a back gesture finishes at the speed the finger let go at**, in
  pages, panes and overlays alike. Before, it finished at a fixed pace, so a
  flick slowed down when it was released. A back button press goes through at
  once.
- **On iOS, swiping back takes no more effort than in a native app.** Letting go
  is decided by where the page would come to rest if it coasted on, by UIKit's
  own deceleration, so a swipe at an ordinary pace a third of the way across
  goes back. Before, it had to pass half the width or go faster than 1000dp a
  second. A swipe more sideways than up or down counts from anywhere on the
  page, not only one twice as sideways, and the page stays under the finger
  rather than a touch slop behind it.
- **A trackpad or a mouse can make a full swipe on a `SwipeActions` row.**
  Carried past the point of no return by a sideways scroll or a two-finger pan,
  the row runs its outermost action when the gesture ends there, as it does
  under a finger. Before, a scroll could open the actions but never run one.
- **A bottom sheet's header has no forehead.** The drag handle's 24dp band is
  the content's top padding, and a `SheetHeader` under it adds nothing more: it
  measures to what it draws, so its close button's empty top reaches into the
  band, and its title is 16dp higher than it was. It has
  `BottomSheetDefaults.ContentPadding`, 16dp, beside it and below it, and so do
  pinned parts. Before, a collapsed header had 44dp above its title and 16dp
  beside it.
- **A bottom sheet is at most 480dp wide on a wide window**, a side sheet's
  width, where it was 640dp: half of a modest desktop. Set
  `ComponentDefaults.sheetMaxWidth` to keep the old width.
- **A `RangeSlider` takes a finger on each thumb**, and both thumbs can be
  dragged at the same time. Before, a second finger did nothing.

### Added

- [`WheelSelect`](components/wheel-select.md): a field that opens into a wheel
  when tapped, and turns the wheel when dragged up or down while closed.
- [`DisclosureListItem`](components/disclosure-list-item.md): a row that opens
  onto more of itself, inside the same card.
- [`Modifier.pressable`](components/modifier-pressable.md): makes a component of
  your own pressable the way the library's are. It brings the press, hover,
  focus ring, cursor and 48dp target, and takes a long press, felt once, and a
  double click.
- `pinned { }` in a sheet's content: a part pinned to the bottom of what the
  sheet shows, or with `SheetPin.Top` under its handle, drawn over the content,
  which scrolls behind it. On the sheet's colour, or with
  `containerColour = Color.Transparent` the content goes under it and shows
  round it. `SheetDetent.pinned()` is the size that shows the pinned parts and
  nothing else: a sheet that collapses to its search field. See
  [`BottomSheet`](components/bottom-sheet.md#partpin----what-stays-put-while-the-sheet-moves).
- `SelectionRow(onClick)`, and `position` on both `SelectionRow` overloads, so
  a group can mix toggles, rows that open a choice, and plain rows.
- [`NavBar`](components/nav-bar.md): `shape`; `itemWidth`
  (`NavBarItemWidth.Natural` or `Equal`); `marker`
  (`NavBarDefaults.glyphMarker()` or `destinationMarker()`); `markerDraggable`;
  and `action`.
- `BottomSheetDefaults.ContentPadding`, the one inset a sheet keeps round its
  content.
- `NavigationSuiteScaffold` passes those on as `barShape`, `barItemWidth`,
  `barMarker` and `barMarkerDraggable`.
- `PageIndicator(orientation = Orientation.Vertical)`.
- `TopBarCollapseState`, `rememberTopBarCollapseState()` and
  `TopBar(collapseState)`.
- `SwipeActions(actionShape)` and `SwipeToDismiss(actionShape)`.
- `SystemIcons.ChevronUpDown`.

### Fixed

- **A `Pagination` with `allowJump` spread out.** Each tappable ellipsis
  reserved a whole touch target, 44dp on iOS and the web and 48 on Android, so
  the row grew and a phone showed fewer pages. A gap is now 24dp wide whether it
  can be tapped or not, so turning `allowJump` on moves nothing. A plain gap is
  a few dp wider than it was.
- **A long press lost after switching input.** A component's long press
  stopped working after the input changed between mouse, keyboard and touch,
  for example after the first tap on a laptop with a touchscreen.
  `Modifier.pointerCursor` removed its node from the modifier chain when the
  input changed. It now keeps it there.
- **A tap on a bar or a panel reached what was underneath.** A tap on a
  `TopBar` between its title and its actions pressed the row scrolled under it,
  and the same went for any blank part of a `Surface`: a card over a map, a
  toolbar over a canvas, a tooltip, coach mark or popover with no scrim over the
  page. `NavBar` had been fixed for this on its own. Every surface now keeps
  the taps that land on it from what is beneath it, as Material's does. The
  bars — `TopBar`, `NavBar`, `NavRail`, `TabBar` and `Toolbar` — also keep a
  press on nothing from a clickable container round them, so a toolbar inside
  a drawing canvas no longer draws. A drag from a bar still scrolls the page.
- **The page jumped when one overlay opened over another.** With a modal sheet
  open, a dialog brought the pushed-back page forward to full size in one frame,
  and pushed it back once the dialog had gone. A sheet over a sheet did the same,
  and the page lightened and darkened again round any second dimmed overlay. The
  host took the backdrop and the dim from the topmost dimmed overlay alone. Every
  open overlay now keeps doing what it asked to, so the page stays where it was.
  The overlay underneath dims, and blurs under a dialog, with the one above it.
  See [several overlays at once](overlays.md#several-overlays-at-once).

---

## 0.2.2 — 29 September 2026

The first release installed from a tag that still exists. The library code is
the same as 0.2.1. Only the release's CI and its install instructions changed.

**0.2.0 and 0.2.1** were tagged on 28 and 29 September. Their release runs failed
while building the documentation site, and their tags were later removed. 0.2.2
contains everything in both, so install 0.2.2 rather than either of them.

### Changed, since 0.2.0

- **A swiped toast shrinks away where it was let go.** Before, it slid off the
  way a toast that times out does, and its fade clipped it at its resting edge,
  so most of it vanished at once. The cards behind now ease back into place
  instead of snapping there as it goes.
- **A `NavBar` search, or any weighted `slot`,** stops at a maximum width
  rather than stretching across a wide window.
- **In a browser, a sideways scroll moves a `SwipeActions` row** a pixel at a
  time. It read each pixel as a wheel notch, so one trackpad event threw the row
  past every action it had.
- **`Modifier.revealOnScroll`:** a card's shadow fades in with the card.
  Before, the fade cut the shadow off, and it came back in one frame as the fade
  ended.

### Added, since 0.2.0

- New `richText` verbs: `underline`, `style(typography.titleLarge, …)` for a
  role from the type scale, `highlight`, `colour` and `gradient`. See
  [Text](components/text.md).

---

## 0.2.0 — 28 September 2026

The first release anyone used, and where these pages begin.

0.1.0 was published by hand on 16 August 2026 and never tagged. Nobody built on
it, so what moved between the two is not listed here. The migrating guide has
the full list of renames, reorders and changed types. It stood until 0.2.2 and
is kept in the repository's history:
[Migrating, 0.1.0 to 0.2.0](https://github.com/kontour-labs/ui/blob/7717dec541b87a794769a49af4c0e14d9bff2c88/ui-docs/content/migrating.md).

What 0.2.0 is, in outline:

- **Three modules at one version:** `:ui`, the components, tokens and theme;
  `:ui-nav3`, the pane scaffolds and page motion for Navigation 3; and
  `:haptics`, one haptics player for Android, iOS, the web and macOS.
- **Components,** across actions, selection, text editing, date and time,
  display, collections, overlays, sheets, navigation and adaptive layout. See the
  [map](components.md).
- **Back handled for you:** Android's predictive back and iOS's swipe back, in
  pages, panes, sheets and dialogs. A modal that may not be dismissed keeps back
  to itself. See [Overlays](overlays.md) and [Navigation 3](navigation3.md).
- **High contrast and reduced motion,** answered by every component. Each
  component's page has an Accessibility section that says how. See
  [Accessibility](accessibility.md).
- **Localisation,** with no bundled translations: a locale, with number, date
  and plural formats; `Strings` built for word order and plurals; pseudo-locales
  for testing; and right-to-left layout throughout. See
  [Localisation](localisation.md).
- **Haptics** by intent rather than by vibration, with a policy for what buzzes
  and what doesn't. See [Haptics](haptics.md) and [Theming](theming.md).
- **Text:** `richText` and inline Markdown, and `SelectableText` for a region
  whose text can be copied.
- **The Kontour Labs violet** as the default accent.
