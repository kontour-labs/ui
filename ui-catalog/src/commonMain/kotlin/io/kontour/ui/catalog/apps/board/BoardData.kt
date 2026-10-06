package io.kontour.ui.catalog.apps.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropEvent
import io.kontour.ui.files.PickedFile

/** A card on the board: a piece of work, and what is attached to it. */
internal data class Task(val id: Int, val title: String, val attachments: List<PickedFile> = emptyList())

/**
 * A column of the board. [limit] is how much work it holds at once — Doing's
 * work-in-progress limit — and a full column turns more cards away.
 */
@Stable
internal class Lane(val id: String, val title: String, val limit: Int? = null, tasks: List<Task> = emptyList()) {
    val tasks = mutableStateListOf<Task>().apply { addAll(tasks) }

    val full: Boolean get() = limit != null && tasks.size >= limit

    /** Whether [task] can come here: there is room, or it is already here and only moving. */
    fun takes(task: Task): Boolean = !full || tasks.any { it.id == task.id }
}

/** The cards, carried between columns and to the archive. */
internal val TaskCard = DragType<Task>("board-task")

/**
 * Everything on the board. Held in memory for as long as the app is open, as
 * the other apps' state is.
 */
@Stable
internal class BoardState(lanes: List<Lane>) {
    val lanes = mutableStateListOf<Lane>().apply { addAll(lanes) }
    val archived = mutableStateListOf<Task>()
    private var nextId = lanes.sumOf { it.tasks.size } + 1

    /** The card [id], in a column or archived: a sheet open on a card it archives keeps it while it closes. */
    fun task(id: Int): Task? =
        lanes.firstNotNullOfOrNull { lane -> lane.tasks.firstOrNull { it.id == id } }
            ?: archived.firstOrNull { it.id == id }

    fun laneOf(id: Int): Lane? = lanes.firstOrNull { lane -> lane.tasks.any { it.id == id } }

    /** A card let go in [to]: out of wherever it was, into its place. */
    fun moveTask(drop: DropEvent<Task>, to: Lane) {
        val from = laneOf(drop.payload.id) ?: return
        val task = from.tasks.removeAt(from.tasks.indexOfFirst { it.id == drop.payload.id })
        to.tasks.add(drop.index.coerceIn(0, to.tasks.size), task)
    }

    /** Files dropped on a column from outside the app: one card, named for the first, holding them all. */
    fun addFromFiles(lane: Lane, files: List<PickedFile>, index: Int?) {
        if (files.isEmpty() || lane.full) return
        val task = Task(nextId++, files.first().name.substringBeforeLast('.'), files)
        lane.tasks.add((index ?: lane.tasks.size).coerceIn(0, lane.tasks.size), task)
    }

    fun archive(task: Task) {
        val lane = laneOf(task.id) ?: return
        lane.tasks.removeAll { it.id == task.id }
        archived.add(task)
    }

    /** Everything archived, back to the first column. */
    fun restoreAll() {
        lanes.firstOrNull()?.tasks?.addAll(archived)
        archived.clear()
    }

    fun update(task: Task) {
        val lane = laneOf(task.id) ?: return
        lane.tasks[lane.tasks.indexOfFirst { it.id == task.id }] = task
    }

    companion object {
        fun sample() = BoardState(
            listOf(
                Lane(
                    "todo", "To do",
                    tasks = listOf(
                        Task(1, "Refresh the 960 timetable"),
                        Task(2, "Fares page for the new zones"),
                        Task(3, "Signage at Elizabeth Quay"),
                    ),
                ),
                Lane(
                    "doing", "Doing", limit = 3,
                    tasks = listOf(
                        Task(4, "Wi-Fi on the Joondalup line"),
                        Task(5, "Step-free map of Perth Underground"),
                    ),
                ),
                Lane("done", "Done", tasks = listOf(Task(6, "Night bus survey"))),
            ),
        )
    }
}
