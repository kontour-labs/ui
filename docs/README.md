# Kontour UI

The design system in [`ui/`](../ui). A custom component library built directly
on Compose Foundation, with no Material dependency.

## Why it exists

Material is a complete, opinionated design language. Adopting it means adopting
its colour roles, its type scale, its motion, its component shapes — and then
fighting all of them to look like Kontour. Commit `34862a9` decided not to.

What replaces it has to earn that decision: a component library good enough that
nobody misses `androidx.compose.material3`, themed so it can express what
`kontour.io` looks like today, and disciplined enough that the next person to add
a component does not have to invent the conventions themselves.

## The mental model

Four layers. Each one only knows about the layers below it.

```
  components/  nav/  overlay/  sheet/     what you call
  ─────────────────────────────────────
  foundation/                            Text, Icon, Surface, Divider
  ─────────────────────────────────────
  interaction/  input/  a11y/  motion/   how it responds and who can use it
  ─────────────────────────────────────
  theme/                                 what it looks like
```

Three rules follow from that shape, and they are the whole system:

**1. Components read tokens, never literals.** No component contains a `Color`,
a `Dp` radius, or a duration. It reads `Theme.colours.surface`,
`Theme.shapes.medium`, `Theme.motion.default`. That indirection is what makes a
theme swappable, a contrast tier possible, and reduced motion automatic.

**2. Components describe intent, not mechanism.** A button asks for
`FeedbackIntent.Confirm`, not a haptic constant. A sheet asks for
`Theme.motion.springGentle`, not a stiffness of 300. The platform-specific and
preference-specific translation happens once, in one place.

**3. Accessibility is structural, not a review checklist.** Contrast is asserted
by a test over every token pairing. Touch targets are enforced by a modifier
every interactive component applies. Focus rings appear based on tracked input
modality rather than a guess. None of these are things a contributor has to
remember — they are things the code does.

## Using it

```kotlin
import io.kontour.ui.theme.KontourTheme

KontourTheme {
    // everything in io.kontour.ui works in here, and only in here
}
```

Once, at the root. It follows the operating system's dark mode, contrast tier
and reduced-motion settings, and components outside it throw rather than falling
back to a default palette — [`theming.md`](../ui-docs/content/theming.md#installing-the-theme)
says why, and how to override any of it.

## Where the documentation is

Two halves, split by who is reading.

### Using it — you are building a screen

**[kontour-labs.github.io/ui](https://kontour-labs.github.io/ui/)** — every
component with a demo you can operate, a compiled example, a generated parameter
table, and the guides: installing, tokens, theming, accessibility, the `+`
vocabulary, overlays and sheets.

The source is [`ui-docs/content/`](../ui-docs/content), and it is markdown that
reads on GitHub too. It lives inside `:ui-docs` rather than here because that is
what it is for — the site is built from it on every push, and a published page
belongs with the thing publishing it.

The **API reference** is generated from the KDoc and lists every public symbol,
which is the half those pages do not attempt:

```sh
./gradlew :ui:dokkaGenerateHtml     # ui/build/dokka/html
```

It knows every signature and no reasons; the pages carry the comparisons, the
"reach for this instead", and the bug histories. Read the page first.

### Building it — you are adding to the system

That is what the rest of this directory is.

| | |
|---|---|
| [`building/contributing.md`](building/contributing.md) | The shape of a component, the naming rules, the checklist |
| [`building/testing.md`](building/testing.md) | Every gate, what each one asks, and what they have caught |
| [`check-links.py`](check-links.py) | Every link in the repository resolves |
| [`check-components.py`](check-components.py) | Eight rules over the pages: a page per component, an example, a demo, an index entry |
| [`check-api-links.py`](check-api-links.py) | Every "API reference" link points at a page the reference published |
| [`sync-samples.py`](sync-samples.py) | The examples on the pages are the code `:ui-samples` compiles |
| [`generate-doc-pages.py`](generate-doc-pages.py) | The content, turned into the Kotlin the site renders |
| [`doctree.py`](doctree.py) | Where the pages live and what families there are — imported by the three above |

Every example on a documentation page is compiled: they live in
[`ui-samples/`](../ui-samples) and the pages hold checked copies, so one that no
longer works fails the build.

## Status

| Phase | | Status |
|---|---|---|
| 0 | Module scaffolding, all six targets | done |
| 1 | Tokens and theme | done |
| 2 | Foundation primitives and mechanisms | done |
| 3 | Actions — buttons, FABs, links | done |
| 4 | Selection controls | core built |
| 5 | Date and time | core built |
| 6 | Text editing | core built |
| 7 | Display and content | core built |
| 8 | Collections | done — `DataTable`/`TreeList` deliberately skipped |
| 9 | Overlays | done, with `Select`, `Combobox`, `MultiSelect` and the text toolbar |
| 10 | Sheets | done |
| 11 | Navigation | done |
| 12 | Adaptive layout and motion | done |
| 13 | Contract suite, catalog, screenshot goldens, CI | done |
| 14 | Selection indicator, navigation rework | done |
| 15 | API consistency sweep | done |
| 16 | Slot APIs and the `+` vocabulary | done |
| 17 | Interaction defects, one nav bar, every catalog control live | done |
| 18 | Per-component renders, documentation split by audience | done |
| 19 | API consistency, the move to its own repository, neutral defaults | done |
| 20 | Render states, compiled examples, the API reference | in progress |
| 21 | New components — see [`components.md`](../ui-docs/content/components.md#not-yet-built) | not started |

## Verifying

```sh
./gradlew :ui:jvmTest :ui:checkNoMaterial :ui:checkApiConventions \
          :ui:checkKdocSamples :ui-catalog:jvmTest :ui-docs:jvmTest \
          :ui-samples:compileKotlinJvm :ui-samples:checkDocSamples \
          :ui:dokkaGenerateHtml
python3 docs/check-links.py
python3 docs/check-components.py
python3 docs/check-api-links.py   # after the two builds above, and only then
```

All on the JVM without an emulator or a simulator — the contract suite,
`checkNoMaterial`, `checkApiConventions`, `EverythingRespondsTest`, the
screenshot goldens, the documentation's compiled examples, every page of the
site drawn at four window widths, every link in the repository, every
cross-reference in the KDoc, and a page for every component. What each one asks,
and what each has caught, is in [`building/testing.md`](building/testing.md).

`:ui-docs:jvmTest` writes a contact sheet per width class to
`ui-docs/build/site-shots/`, and looking through it is the review step no
assertion replaces.

## The catalog

`Catalog()` in `:ui-catalog` is every component, in every state, running on all
five targets from one source. It opens on **About** — what the library is, how
to add it, a theming primer and where the writing lives — because a gallery
whose first page is a colour ramp tells someone who has just been handed the
library none of that, and reads as a test fixture rather than as documentation.
That page is also written entirely with shipped components, which makes it the
one place the system has to work as a document rather than as a form. It carries the switches that are hardest to check
by eye and easiest to get wrong — dark, high contrast, text size to 200%,
right-to-left, reduced motion, and a forced input modality — so a component can
be seen under each without changing a system setting.

The modality switch matters more than it looks: focus rings, hover, scrollbar
visibility and tooltip triggers all branch on it, and on a desktop host you
would otherwise only ever see the pointer branch.
