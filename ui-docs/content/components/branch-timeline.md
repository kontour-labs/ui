# `BranchTimeline`

*Also on this page: `branchTimeline`, `BranchTimelineScope`, `BranchProgress`, `BranchTimelineColours`.*

<!--sample:BranchTimelineBasics-->
```kotlin
// Only which commit comes from which: the lanes are laid out from that. The
// merge's second parent opens a lane of its own, which closes back into the
// commit it forked from.
BranchTimeline(
    items = commits,
    id = { it.sha },
    parents = { it.parents },
) { commit ->
    // Each commit is a row declared like a TimelineList stop.
    item(onClick = { openCommit(commit) }) {
        +commit.message
        supporting { +"${commit.author} · ${commit.sha}" }
    }
}
```
`BranchTimeline` draws a history that branches and merges, the way
`git log --graph` does. Each commit is a list row, and the lanes run beside the
rows for the branches. Use it for a repository's commits, a document's versions,
or a plan's revisions: any sequence where things fork off and come back together.

**You say only which items come from which; the lanes are laid out for you.**
`id` names each item, and `parents` names the items it comes from, first parent
first:

- **A commit continues the lane of its first parent.** A straight history is one
  lane, top to bottom.
- **Each further parent is a merge.** The branch it brings in gets a lane of its
  own to the right, in the palette's next colour. That lane runs down until the
  branch closes into the commit it forked from.
- **Tips that share a parent run side by side** and meet at it.
- **A lane that closes is free again.** The next branch to open takes the
  leftmost free lane, so the graph stays as narrow as the history allows.

Lanes closing into a commit bend toward it across the row above and arrive at it
straight down, so the curves have a whole row's height to swing across.

## Each commit is a timeline stop

The builder declares each commit's row with `item`, **the same `item` a
`TimelineList` stop takes**, so a commit gets everything a timeline's stop does:

- **`onClick`, `selected`, `enabled`, `role`**: the row is a `ListItem`, and
  presses, selects and reads like one.
- **`nodeColour`, `filled`, `loading`**: the node's colour, a ring rather than
  a dot, and a spinner in its place while the commit is being built or checked.
- **`connector`, `connectorColour`, `connectorWidth`**: how the commit's lines
  down to its parents are drawn, in every row they pass. A dashed line for a
  branch that only exists locally, a dotted one for work nobody has committed
  to, or a release branch's commits all in one colour.

**A merge is a ring unless it says otherwise.** `filled` defaults to the
scope's `filledByDefault`, which is false for a commit with more than one
parent, so a merge reads as a join rather than as work. Pass `filled = true` for
a solid one. The nodes are the size and weight of a `Timeline`'s, and one lane
is laid out exactly like a `TimelineList`: the text starts the same distance
past the last lane as a list's does past its rail.

Two commits can share a line: when a merge brings in a branch another lane is
already waiting on, both run down that lane to the parent. The bend across is
the merge's own; below it, the shared stretch takes the style of the commit that
opened the lane.

## The order it needs

**Newest first, and every commit above its parents.** This is the order
`git log` prints. A parent missing from the list keeps its lane running to the
bottom. That is right for a history cut off at a page boundary, and wrong for a
list that is out of order.

## Colour

Lanes take the colours of `colours.lanes` in turn as they open: the accent
first, then the status colours, from `BranchTimelineDefaults.palette()`. A
commit's `connectorColour` gives its own lines a colour of their own. Give every
commit on a release branch the same one to keep that branch green however many
branches opened before it.

## Progress

`progress = BranchProgress(reached = "c3")` marks how far a history has got:
the commit a deploy has reached, the last one reviewed. **The commit reached
pulses, and everything it descends from keeps its colours. The commits it has
not reached, and their lines, are muted.** Name `towards`, one of its children,
and the line from one to the other turns faint while a band sweeps up it, for
the deploy that is on its way. An explicit `nodeColour` or `connectorColour`
keeps its colour whatever the progress, and the band shows on it too, since the
line under the band is faint. Under reduced motion the pulse stands still and
there is no band.

## In a `LazyColumn`

`branchTimeline(items, id, parents)` draws the same history inside a
`LazyColumn`, keyed by `id`. The lanes are laid out for the whole list up front,
because a row cannot know which lanes pass it without every commit above it. The
rows themselves stay lazy. **Leave the column's `verticalArrangement` alone**:
the lanes are unbroken because the rows touch.

The graph is only as wide as the most lanes any one row uses, and every row
leaves that much room, so the rows' text lines up. A history that needs many
lanes at once gets wide. That is not a limit, but it is past what a phone can
show legibly.

---

## Accessibility

Every commit is a `ListItem`: one merged node carrying its row's words. With
`onClick` it is a button. With `selected` true it is tinted, and it announces as
selected when it is also a button.

**The graph is drawn, not announced.** The lanes, the colours, the rings for
merges, the muting and the pulse are pictures of the history, and a screen
reader hears none of them. Put what they show in the words: "Merge feature/maps
into main", "Branched from Fix stop search", "Release 2.1". Colour is never the
only thing that says which branch a commit is on, because a branch's name
belongs in its commits' text, and a commit that is `loading` or reached should
say so: "Building", "Deployed".
