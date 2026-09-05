# `ListSection` / `SectionHeader`

A titled group. `SectionHeader` carries `heading()` semantics, so a screen reader
can jump between sections rather than walking every row.

<!--sample:ListSectionBasics-->
```kotlin
// The section owns the rounding: `position` tells each row whether it is the
// first, the last, both or neither, so a group reads as one card rather than
// as a stack of separate ones.
ListSection(
    title = { +"Appearance" },
    description = { +"How the app looks on this device" },
) {
    SettingRow(position = ListItemPosition.First, onClick = { save() }) {
        +"Theme"
        supporting { +"Match system" }
    }
    SettingRow(position = ListItemPosition.Last, onClick = { save() }) {
        +"Text size"
        supporting { +"Default" }
    }
}
```

---

## Accessibility

`title` is marked `heading()`, which is what makes a settings screen navigable:
a screen reader can jump between sections instead of walking every row.

`spacing` and `position` are visual grouping. The heading is the semantic
grouping, and they are not the same thing — a run of rounded rows with no heading
looks like a group and is not one.

`footer` is announced after the rows, in reading order. Put the caveat there
rather than in the last row's `supporting`, where it reads as being about that
row.
