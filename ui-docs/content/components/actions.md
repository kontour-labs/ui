# Actions

Things the user presses to make something happen.

| | For | Instead of |
|---|---|---|
| [`Button`](button.md) | An action with a name | An `IconButton`, when there is room for the word |
| [`IconButton`](icon-button.md) | An action with no room for a name | A `Button`, whenever there is room |
| [`IconToggleButton`](icon-toggle-button.md) | An icon that is on or off — favourite, mute | A `Switch`, when the state deserves a label |
| [`FloatingActionButton`](fab.md) | The one action a whole screen exists for | A `Button`, for anything else |
| [`ExtendedFloatingActionButton`](extended-fab.md) | A FAB whose icon needs a word beside it | `FloatingActionButton`, when the icon carries it |
| [`FabMenu`](fab-menu.md) | Several actions behind the one FAB | A `Toolbar`, when they belong on the chrome |
| [`SplitButton`](split-button.md) | One usual action, with variants a tap away | A `Button` + menu, when there is no *usual* one |
| [`ButtonGroup`](button-group.md) | Related actions that read as one control | `SegmentedControl`, when one is *selected* |
| [`Toolbar`](toolbar.md) | A floating surface of actions over other content | `TopBar`, when it is the screen's own chrome |

A button that is working shows a `Spinner` in place of its label. The spinner
itself is filed under Display, beside `LinearProgress` and `Skeleton` — the rest
of the vocabulary for "something is happening". It is deliberately not linked
from here: an index page that links a component *claims* it, so a helpful
cross-reference between families would move the page into the wrong one.
