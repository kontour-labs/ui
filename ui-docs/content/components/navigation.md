# Navigation

| | For | Instead of |
|---|---|---|
| [`NavigationSuiteScaffold`](navigation-suite-scaffold.md) | **Start here.** Destinations, placed by window size | Picking a surface by hand |
| [`NavScope`](nav-scope.md) | The destinations, written once for every surface | A copy per surface |
| [`NavBar`](nav-bar.md) | Compact windows — bottom of the screen | — |
| [`NavRail`](nav-rail.md) | Medium windows and up — leading edge; collapsed, or a sidebar | — |
| [`ModalNavRail`](modal-nav-rail.md) | The sidebar over the content, behind a menu button | `NavBar`, when there are only three or four |
| [`NavSearch`](nav-search.md) | A search in a bar or a rail | A search screen for the bar's one field |
| [`TopBar`](top-bar.md) | A title and its actions | Anything holding destinations |
| [`TabBar`](tab-bar.md) | Views of *one* screen | `SegmentedControl`, when switching a value |
| [`DocumentTabs`](document-tabs.md) | Open documents, a browser's or an editor's, closed and dragged between panes | `TabBar`, whose views are fixed |
| [Navigation 3 scenes](../navigation3.md) | A list and its detail, or a supporting pane, from a back stack | Laying the panes out by hand in every destination |
| [`Breadcrumbs`](breadcrumbs.md) | Where you are in a hierarchy | — |
| [`Pagination`](pagination.md) | Numbered pages | `LoadMore`, in an app |

Routes and the back stack are not in `:ui` — they are in `:core:navigation`.
These are the surfaces that draw them.
