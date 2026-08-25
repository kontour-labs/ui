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

← [Collections](collections.md) · [All components](../components.md)
