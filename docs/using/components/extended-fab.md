# `ExtendedFloatingActionButton`

![ExtendedFloatingActionButton](../../../ui-catalog/screenshots/components/extendedfloatingactionbutton-light.png)

<!--sample:ExtendedFloatingActionButtonCollapsing-->
```kotlin
ExtendedFloatingActionButton(
    icon = Tabler.Outline.Navigation,
    contentDescription = "Start trip to Perth Station",
    expanded = listState.firstVisibleItemIndex == 0,
    onClick = { start() },
) {
    +"Start"
}
```

It animates its *width* when collapsing rather than cross-fading between two
components, so the icon stays put and the label slides out from behind it.
Cross-fading makes the icon appear to jump sideways. `NavRail` uses the same
treatment when it expands.

`contentDescription` is separate from `label` because the label may be terse
where the announcement should not be — "Start" on screen, "Start trip to Perth
Station" for a screen reader.

---

← [Actions](actions.md) · [All components](../components.md)
