# Selection

Controls that record a choice.

| | For | Instead of |
|---|---|---|
| [`Checkbox`](checkbox.md) | An independent yes/no in a form | `Switch`, when it applies immediately |
| [`TriStateCheckbox`](tri-state-checkbox.md) | A parent whose children are partly selected | `Checkbox`, when there is no hierarchy |
| [`RadioButton`](radio-button.md) | One of a set — the control itself | `RadioGroup`, almost always |
| [`RadioGroup`](radio-group.md) | One of three or four visible options | `Select`, when there is no room to show them |
| [`Switch`](switch.md) | A setting that takes effect immediately | `Checkbox`, when it takes effect on submit |
| [`SelectionRow`](selection-row.md) | **Any of the four above, with a label** | A bare control beside a `Text` — never do this |
| [`Chip`](chip.md) | One of a *set* of small choices | `Button`, when there is only one |
| [`SegmentedControl`](segmented-control.md) | Two to four short options, switched often | `RadioGroup`, beyond four or with long labels |
| [`ColorSwatchPicker`](color-swatch-picker.md) | A choice made by looking | `Select`, for anything nameable |
| [`Slider`](slider.md) | A value in a continuous range | A `NumberField`, when the exact figure matters |
| [`RangeSlider`](range-slider.md) | A band — two values on one track | Two `Slider`s, which cannot stop each other crossing |
| [`Stepper`](stepper.md) | A small exact count | A `Slider`, when the number is approximate |
| [`Rating`](rating.md) | A score out of five | A `Slider`, when the scale is not a score |

**The single most important rule on this page:** every one of these belongs
inside a [`SelectionRow`](selection-row.md) unless something else is already
labelling it. A bare control with a `Text` beside it gives the user a small
target and gives a screen reader two nodes for one choice.
