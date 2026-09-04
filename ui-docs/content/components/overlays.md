# Overlays

Everything that draws over the screen: menus, popovers, dialogs, tooltips,
toasts and the whole-screen blocker. They share one host, one stack and one
dismissal model — [`../overlays.md`](../overlays.md) is the guide to that
mechanism, and the pages below are the components themselves.

| | For | Instead of |
|---|---|---|
| [`DropdownMenu`](dropdown-menu.md) | A list of actions off a control | A `Popover` full of buttons |
| [`ContextMenuArea`](context-menu-area.md) | The same list, on right-click or long press | A visible menu button, when you need both |
| [`Popover`](popover.md) | Arbitrary content attached to a control | A dialog, when the rest of the screen is still usable |
| [`Dialog`](dialog.md) | A decision that must be made before anything else | A `Popover`, when the answer is required |
| [`AlertDialog`](alert-dialog.md) | The common two- or three-button case | Building the buttons yourself |
| [`Tooltip`](tooltip.md) | The name of a control being pointed at | A label, when there is no room for one |
| [`Toast`](toast.md) | Confirmation of something just done | A `Banner`, which is about the screen |
| [`LoadingOverlay`](loading-overlay.md) | Whole-screen, must-not-interrupt work | A `Skeleton` or an inline `Spinner`, nearly always |
| [`CommandPalette`](command-palette.md) | Every action, by name, from the keyboard | A menu bar |
| [`OverlayHost`](overlay-host.md) | Installing the layer all of the above render into | Nothing — a screen without one throws |

The rules that are easy to get wrong — a toast is not a banner, a popover is not
a small dialog, `LoadingOverlay` should be rare — are set out in
[the overlay guide](../overlays.md#picking-a-component).
