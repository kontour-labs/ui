# Navigation

| | For | Instead of |
|---|---|---|
| [`NavigationSuiteScaffold`](navigation-suite-scaffold.md) | **Start here.** Destinations, placed by window size | Picking a surface by hand |
| [`NavItem`](nav-item.md) | One destination, declared once | Three copies, one per surface |
| [`NavBar`](nav-surfaces.md) | Compact windows — bottom of the screen | — |
| [`NavRail`](nav-surfaces.md) | Medium windows — leading edge | — |
| [`NavDrawer`](nav-surfaces.md) | Expanded windows, and nested groups | `NavRail`, for a flat set |
| [`TopBar`](top-bar.md) | A title and its actions | Anything holding destinations |
| [`TabBar`](tab-bar.md) | Views of *one* screen | `SegmentedControl`, when switching a value |
| [`Breadcrumbs`](breadcrumbs.md) | Where you are in a hierarchy | — |
| [`Pagination`](pagination.md) | Numbered pages | `LoadMore`, in an app |

Routes and the back stack are not in `:ui` — they are in `:core:navigation`.
These are the surfaces that draw them.
