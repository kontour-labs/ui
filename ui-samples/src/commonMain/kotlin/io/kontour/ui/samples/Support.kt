package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import io.kontour.ui.components.list.ListItem

/**
 * Stand-ins for the caller's own code.
 *
 * Every example on the documentation pages compiles, so every name in one has to
 * resolve — including the things that are obviously not the library's: the
 * action a button runs, the rows a list holds, the screen a route opens.
 *
 * Inventing a plausible view model per page would be more code than the samples
 * and would be the first thing a reader tried to understand. So they are these
 * instead: named for what they do, and never shown.
 *
 * ### Everything here is `internal`, and that is what keeps it out of the docs
 *
 * `sync-samples.py` reads only **public top-level functions** as samples. That
 * is the whole rule separating an example from its scaffolding, and it also
 * powers the check that fires when a sample is compiled but no page shows it —
 * which would otherwise report every helper in this file.
 */

internal fun plan() = Unit
internal fun delete() = Unit
internal fun dismiss() = Unit
internal fun add() = Unit
internal fun start() = Unit
internal fun zoomIn() = Unit
internal fun zoomOut() = Unit
internal fun recentre() = Unit
internal fun openLayers() = Unit
internal fun refresh() = Unit
internal fun openStop(name: String) = Unit
internal fun remove(name: String) = Unit

/** Rows, where a sample needs a list to be about something other than the list. */
internal fun LazyListScope.stopRows() {
    items(3) { index ->
        ListItem { +"Stop ${index + 1}" }
    }
}

/** The screen behind an overlay, where a sample needs one to sit over. */
@Composable
internal fun Screen() = Unit
