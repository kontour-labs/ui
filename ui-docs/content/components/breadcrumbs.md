# `Breadcrumbs`

Where you are in a hierarchy, and the way back up. No caller in Anyways today —
it is here for the admin panel.

<!--sample:BreadcrumbsBasics-->
```kotlin
// The last crumb has no `onClick`, and that is what makes it the current
// page rather than a link back to itself.
Breadcrumbs(
    listOf(
        Crumb("Routes", onClick = { nearby() }),
        Crumb("Route 950", onClick = { nearby() }),
        Crumb("Stops"),
    ),
)
```

---

← [Navigation](navigation.md) · [All components](../components.md)
