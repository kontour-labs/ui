package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.Stat
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * What this is, before the gallery of what it contains.
 *
 * The catalog opened on a page of colour swatches, which tells someone who has
 * just been handed the library nothing about what it is, how to add it, or where
 * the writing lives. A gallery with no front door reads as a test fixture, and
 * for a while that is all this was.
 *
 * ### Written with the library, on purpose
 *
 * Every element here is a shipped component — `Card`, `Stat`, `KeyValueList`,
 * `Surface`, `Text` — and no page-local layout beyond a `Column`. It is the one
 * page whose content is prose rather than specimens, which makes it the one
 * place the system has to work as a *document* rather than as a form. That is a
 * real test: the first draft wanted a code block, and the library has no
 * component for one, which is a genuine gap rather than an oversight in this
 * page.
 */
@Composable
fun AboutShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Masthead()
            Numbers()
            Install()
            Theming()
            Reading()
        }
    }
}

@Composable
private fun Masthead() {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text("Kontour UI", style = Theme.typography.displaySmall)
        Text(
            "A Compose Multiplatform design system built directly on Foundation, " +
                "with no Material dependency. Android, iOS, desktop, JS and Wasm " +
                "from one source.",
            style = Theme.typography.bodyLarge,
            color = Theme.colors.contentMuted,
        )
    }
}

/**
 * Three numbers, because they are the three questions asked first.
 *
 * `Stat` rather than a sentence: it is the component for a figure someone scans
 * for, this page is scanned, and using it here is one more place the component
 * has to hold up outside its own showcase.
 */
@Composable
private fun Numbers() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Stat(modifier = Modifier.weight(1f)) {
            value("5")
            +"Targets"
            supporting("One source")
        }
        Stat(modifier = Modifier.weight(1f)) {
            value("0")
            +"Material"
            supporting("Gated by a build task")
        }
        Stat(modifier = Modifier.weight(1f)) {
            value("AA")
            +"Contrast"
            supporting("Asserted per pairing")
        }
    }
}

@Composable
private fun Install() {
    Panel("Adding it") {
        KeyValueList {
            item("Group", "io.kontour")
            item("Artifact", "ui")
            item("Repository", "maven.pkg.github.com/kontour-labs/ui")
        }
        Text(
            "It is published privately through GitHub Packages, so the repository " +
                "needs credentials — a personal access token with read:packages, or " +
                "the Actions token in CI. Versions are tags: v0.2.0 publishes 0.2.0, " +
                "and anything untagged is a snapshot.",
            style = Theme.typography.bodyMedium,
            color = Theme.colors.contentMuted,
        )
    }
}

@Composable
private fun Theming() {
    Panel("Theming it") {
        Text(
            "Install KontourTheme once at the root. Everything in io.kontour.ui " +
                "reads its tokens from there and throws outside it — a component " +
                "silently drawing in the wrong palette is a worse bug than one " +
                "that refuses to draw.",
            style = Theme.typography.bodyMedium,
        )
        Text(
            "The default palette is monochrome with one blue accent, and that is " +
                "deliberate: a library that shipped somebody's brand would make " +
                "every app using it look like that somebody. Every token group is " +
                "a parameter, so a product overrides accent, brand and focus ring " +
                "and inherits the rest. Kontour's own theme is about a hundred " +
                "lines, most of them colour values.",
            style = Theme.typography.bodyMedium,
            color = Theme.colors.contentMuted,
        )
        Text(
            "Dark mode, contrast tier and reduced motion resolve from the " +
                "operating system and follow it live. The switches in this app's " +
                "settings sheet override all three, which is how to see a " +
                "component under a setting you are not running.",
            style = Theme.typography.bodyMedium,
            color = Theme.colors.contentMuted,
        )
    }
}

@Composable
private fun Reading() {
    Panel("Reading about it") {
        // Paths rather than links: this app has no browser to hand off to on
        // three of its five targets, and a link that does nothing on iOS is
        // worse than a path someone can find in the repository.
        KeyValueList {
            item("Components", "docs/using/components.md")
            item("Tokens", "docs/using/tokens.md")
            item("Theming", "docs/using/theming.md")
            item("Accessibility", "docs/using/accessibility.md")
            item("Slots and +", "docs/using/dsls.md")
            item("Adding one", "docs/building/contributing.md")
        }
        Text(
            "Every example on those pages is compiled — they live in ui-samples " +
                "and the pages hold checked copies, so an example that no longer " +
                "works fails the build rather than being found by whoever pastes it.",
            style = Theme.typography.bodyMedium,
            color = Theme.colors.contentMuted,
        )
        Text(
            "The generated API reference lists every public symbol, which the " +
                "pages above do not attempt: ./gradlew :ui:dokkaGenerateHtml.",
            style = Theme.typography.bodyMedium,
            color = Theme.colors.contentMuted,
        )
    }
}

/**
 * The shared [Section] heading, with this page's content in a card under it.
 *
 * Calls the one every other showcase uses rather than declaring a second — a
 * page-local copy would drift from the rest of the gallery, and the compiler
 * caught the first draft doing exactly that.
 */
@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Section(title) {
        Card(variant = CardVariant.Outlined, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                content()
            }
        }
    }
}
