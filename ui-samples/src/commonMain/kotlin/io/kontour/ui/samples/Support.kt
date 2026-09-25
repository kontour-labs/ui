package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
internal fun save() = Unit
internal fun nearby() = Unit
internal fun saveAndClose() = Unit
internal fun saveCopy() = Unit
internal fun showTour() = Unit
internal fun forgot() = Unit

/** A colour the app is holding for something the reader named. */
internal var label: Color = Color(0xFF43A047)
internal fun explain() = Unit
internal fun replacements() = Unit

/** A handful of them, where a sample needs a list to iterate. */
internal val stops = listOf(Stop("Perth Underground", 4), Stop("Elizabeth Quay", 3))
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

/** The two things a rider reports about a stop, where a menu needs entries. */
internal fun report() = Unit
internal fun suggest() = Unit

/** The body of a sheet, where the sheet rather than its contents is the subject. */
@Composable
internal fun Departures() = Unit

/** A departure that has not arrived yet, for the redaction example. */
internal class Departure(val name: String, val detail: String)

/** Null while it loads, which is the state the example is about. */
internal val departures: List<Departure>? = null

/** A commit, for the history example: newest first, each naming its parents. */
internal class Commit(val sha: String, val message: String, val author: String, val parents: List<String>)

internal val commits = listOf(
    Commit("e41", "Merge feature/maps", "Sam", listOf("c32", "b17")),
    Commit("b17", "Map tiles", "Kai", listOf("a09")),
    Commit("c32", "Fix stop search", "Ari", listOf("a09")),
    Commit("a09", "Add journey planner", "Sam", emptyList()),
)

internal fun openCommit(commit: Commit) = Unit

/** A year of trips a day, for the activity calendar example. */
internal val tripsByDay: Map<kotlinx.datetime.LocalDate, Int> = emptyMap()
