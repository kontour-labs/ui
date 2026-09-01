# Display and content

Things that show rather than take input.

| | For | Instead of |
|---|---|---|
| [`Card`](card.md) | A bounded block of related content | A `Surface`, when there is no grouping to express |
| [`Tag`](tag.md) | A status label, or a colour out of a feed | A `Chip`, which is interactive |
| [`Badge`](badge.md) | A count or a dot over something | — |
| [`Avatar`](avatar.md) | A person or a place | — |
| [`LinearProgress`](progress.md) | A known fraction | `Spinner`, when you do not know it |
| [`CircularProgress`](progress.md) | A known fraction, in a small space | — |
| [`StepProgress`](progress.md) | A known number of steps | `LinearProgress`, for a continuous fraction |
| [`Skeleton`](skeleton.md) | The shape of content that is loading | `Spinner`, when the shape is knowable |
| [`EmptyState`](empty-state.md) | Nothing here, and that is fine | `ErrorState` — see below |
| [`ErrorState`](empty-state.md) | Something went wrong | `EmptyState` — see below |
| [`Banner`](banner.md) | A message about the screen you are on | `Toast`, for something you just did |
| [`Callout`](callout.md) | The markdown blockquote treatment | `Banner`, for anything dismissible |
| [`Timeline`](timeline.md) | A vertical sequence — the itinerary | A plain list, when there is no progression |
| [`Accordion`](accordion.md) | Disclosure, with hoisted state | — |
| [`Stat`](stat.md) | One figure, said loudly | `KeyValueList`, when none of them is the headline |
| [`KeyValueList`](key-value-list.md) | Label-and-value facts about one thing | `SettingRow`, only if the rows are tappable |
| [`Carousel`](carousel.md) | Pages one at a time, that snap | A scrolling `Row`, when they are not pages |
| [`PageIndicator`](page-indicator.md) | Which page of how many | — |
| [`AnimatedCounter`](animated-counter.md) | A number that changes while you watch | A `Text`, when it changes off-screen |
| [`Modifier.marquee`](modifier-marquee.md) | A label that is occasionally too long | Truncation, when the tail does not matter |
| [`Kbd`](kbd.md) | A keyboard shortcut, rendered as a key | — |
| [`RelativeTimeText`](relative-time-text.md) | A self-updating "in 4 min" | — |
