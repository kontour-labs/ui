# :ui — Kontour UI

The design system. A component library built directly on Compose Foundation,
with no Material dependency.

**Documentation lives in
[`docs/app/design-system/`](../../docs/app/design-system/README.md).** Start
there — this file is only a pointer.

- [Tokens](../../docs/app/design-system/tokens.md) — colour, type, spacing, shape, elevation, motion, sizing
- [Theming](../../docs/app/design-system/theming.md) — building a theme
- [Accessibility](../../docs/app/design-system/accessibility.md) — the contract, and how it is enforced
- [Contributing](../../docs/app/design-system/contributing.md) — adding a component

## Quick start

```kotlin
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.foundation.Text

KontourTheme {
    Text("Departures", style = Theme.typography.titleMedium)
}
```

## Checks

```sh
./gradlew :ui:jvmTest          # contract, behaviour and contrast tests
./gradlew :ui:checkNoMaterial  # fails if Material reaches the classpath
./gradlew :ui-catalog:jvmTest  # regenerates the screenshot goldens
```

## Layout

```
theme/        tokens and KontourTheme
foundation/   Text, Icon, Surface, Divider
interaction/  indication, haptics
input/        input modality, focus rings
a11y/         contrast, touch targets, semantics helpers
components/   the component library
nav/ overlay/ sheet/ layout/ motion/
```

Third-party font: Outfit, SIL OFL — see [`licenses/`](licenses/).
