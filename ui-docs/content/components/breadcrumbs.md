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

## Accessibility

The last crumb has no `onClick`, and that is what makes it the current page
rather than a link back to itself — announced as text among links.

The separators are drawn and not announced.

On a narrow window the trail is what tells a user where they are, and it is also
the first thing to overflow. Where it does, keep the last two: the current page
and its parent are the pair that answer "where am I and how do I get out".

---

← [Navigation](navigation.md) · [All components](../components.md)
