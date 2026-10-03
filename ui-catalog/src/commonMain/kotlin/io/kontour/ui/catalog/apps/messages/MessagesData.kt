package io.kontour.ui.catalog.apps.messages

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * The day the app believes it is.
 *
 * Fixed, not read from the clock, so every run — a golden, a test, a screen
 * recording next week — shows the same "Today" and "Yesterday" over the same
 * messages. A Friday, so the week behind it has names to give its days.
 */
internal val Today = LocalDate(2026, 10, 2)

/** The day before [Today], spelled out because the app never does date arithmetic. */
private val Yesterday = LocalDate(2026, 10, 1)

/** A conversation with one person, or with several. The filters split the list on it. */
internal enum class ChatKind { Person, Group }

/**
 * One conversation in the list.
 *
 * Immutable, and replaced whole when it changes — pinned, read, archived — so
 * the list's rows recompose for the one chat that changed and no other.
 *
 * @param members Everyone in a group but you, in the order their names are
 *   listed. Empty for a chat with one person, whose name is the chat's.
 * @param about One line for the info popover: how you know them.
 */
@Immutable
internal data class Chat(
    val id: String,
    val name: String,
    val kind: ChatKind,
    val about: String,
    val members: List<String> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val unread: Int = 0,
)

/**
 * One message.
 *
 * @param sender Who wrote it: a name, or [Me]. Kept as the name rather than a
 *   flag, because a group's bubbles run by sender and the run breaks between
 *   two people as much as it does between them and you.
 */
@Immutable
internal data class Message(
    val id: Int,
    val sender: String,
    val text: String,
    val date: LocalDate,
    val time: LocalTime,
) {
    val fromMe: Boolean get() = sender == Me
}

/** The sender of every message you wrote. Not a name anybody in the data has. */
internal const val Me = "me"

/**
 * One note.
 *
 * @param body Inline markdown — `**bold**`, `*italic*`, `` `code` `` — which
 *   reading mode draws with `markdownText` and editing mode shows as typed.
 *   Paragraphs are separated by a blank line.
 */
@Immutable
internal data class Note(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val edited: String,
    val pinned: Boolean = false,
)

/**
 * Everything the app holds, and the things that change it.
 *
 * One object rather than a view model per screen, because the screens are
 * views of the same few lists: reading a conversation clears the badge on the
 * Chats tab, archiving from the command palette takes a row out of the list
 * beside it. Snapshot state throughout, so whichever screen is showing sees a
 * change the moment it is made.
 *
 * [scope] is the app's, not a screen's: a reply that was on its way when you
 * left the conversation still arrives, and is waiting there — unread — when you
 * come back.
 */
@Stable
internal class MessagesState(private val scope: CoroutineScope) {

    private val chatList = mutableStateListOf<Chat>().apply { addAll(SeedChats) }
    private val threads = mutableStateMapOf<String, List<Message>>().apply { putAll(SeedThreads) }

    /** Every note, pinned or not, in the order the pinned ones were arranged. */
    val notes = mutableStateListOf<Note>().apply { addAll(SeedNotes) }

    /** Who is typing in which chat, while a scripted reply is on its way. */
    val typing = mutableStateMapOf<String, String>()

    /**
     * The conversation on screen, set by the conversation page while it is
     * there. A reply that lands anywhere else is unread.
     */
    var openChatId: String? by mutableStateOf(null)

    /**
     * What the chat list is narrowed to. Here rather than in the list, so a
     * trip to Notes and back does not quietly put back the chats it hid.
     */
    var chatFilter: ChatFilter by mutableStateOf(ChatFilter())

    /**
     * "Now", a minute later each time anyone says anything.
     *
     * Never the real time: a message sent during a test reads "9:41 am" on
     * every machine, and two sent in a row still come out in order.
     */
    private var now by mutableStateOf(LocalTime(9, 41))
    private var nextMessageId = 1_000
    private var nextNoteId = 1
    private val repliesSent = mutableMapOf<String, Int>()

    /** The chats on show — not archived — pinned first, then newest first. */
    val chats: List<Chat>
        get() = chatList
            .filter { !it.archived }
            .sortedWith(compareByDescending<Chat> { it.pinned }.thenByDescending { stamp(last(it.id)) })

    /** Every unread message in every chat on show, for the badge on the Chats tab. */
    val unreadTotal: Int get() = chatList.filter { !it.archived }.sumOf { it.unread }

    /** The chat with [id], archived or not; null for one that never existed. */
    fun chat(id: String): Chat? = chatList.firstOrNull { it.id == id }

    /** [id]'s messages, oldest first. */
    fun thread(id: String): List<Message> = threads[id].orEmpty()

    /** The newest message in [id], which the list previews and sorts by. */
    fun last(id: String): Message? = threads[id]?.lastOrNull()

    /** Every day anybody said anything, for the dots under the date picker's days. */
    val activeDays: Set<LocalDate> get() = threads.values.flatMapTo(mutableSetOf()) { it.map(Message::date) }

    fun markRead(id: String) = update(id) { it.copy(unread = 0) }

    fun markAllRead() {
        for (index in chatList.indices) chatList[index] = chatList[index].copy(unread = 0)
    }

    fun togglePin(id: String) = update(id) { it.copy(pinned = !it.pinned) }

    fun archive(id: String) = update(id) { it.copy(archived = true) }

    fun unarchive(id: String) = update(id) { it.copy(archived = false) }

    /**
     * Sends [text] to [id] as you, and sets the other side typing.
     *
     * The reply is scripted — each chat has a few lines and goes round them —
     * and arrives after [ReplyDelay]. Shorter would read as an echo; longer, in
     * a demo, reads as nothing happening.
     */
    fun send(id: String, text: String) {
        val chat = chat(id) ?: return
        append(id, Me, text.trim())
        val replier = replierFor(chat)
        typing[id] = replier
        scope.launch {
            delay(ReplyDelay)
            typing.remove(id)
            val lines = Replies[id] ?: DefaultReplies
            val count = repliesSent.getOrElse(id) { 0 }
            repliesSent[id] = count + 1
            append(id, replier, lines[count % lines.size])
            if (openChatId != id) update(id) { it.copy(unread = it.unread + 1) }
        }
    }

    fun note(id: String): Note? = notes.firstOrNull { it.id == id }

    val pinnedNotes: List<Note> get() = notes.filter { it.pinned }
    val otherNotes: List<Note> get() = notes.filterNot { it.pinned }

    /** A blank note at the top of the unpinned ones, for "New note" to open straight into editing. */
    fun addNote(): String {
        val id = "new-${nextNoteId++}"
        val at = notes.indexOfFirst { !it.pinned }.takeIf { it >= 0 } ?: notes.size
        notes.add(at, Note(id = id, title = "Untitled note", body = "", tags = emptyList(), edited = "Just now"))
        return id
    }

    fun updateNote(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) notes[index] = note
    }

    /**
     * Moves a pinned note from [from] to [to], both counted among the pinned
     * notes only — the order the pinned section is dragged into.
     */
    fun movePinned(from: Int, to: Int) {
        val pinned = pinnedNotes
        if (from !in pinned.indices || to !in pinned.indices) return
        val moved = pinned[from]
        val target = pinned[to]
        notes.remove(moved)
        val at = notes.indexOf(target)
        notes.add(if (to > from) at + 1 else at, moved)
    }

    private fun append(id: String, sender: String, text: String) {
        now = LocalTime(now.hour + (now.minute + 1) / 60, (now.minute + 1) % 60)
        threads[id] = thread(id) + Message(nextMessageId++, sender, text, Today, now)
    }

    private fun update(id: String, change: (Chat) -> Chat) {
        val index = chatList.indexOfFirst { it.id == id }
        if (index >= 0) chatList[index] = change(chatList[index])
    }

    /** The person who answers: the chat's own, or the first of a group's. */
    private fun replierFor(chat: Chat): String = chat.members.firstOrNull() ?: chat.name
}

/** How long a scripted reply takes to arrive, typing indicator and all. */
private val ReplyDelay = 1_200.milliseconds

/** Sorts a message by when it was sent, oldest smallest. */
private fun stamp(message: Message?): Long =
    if (message == null) 0L else message.date.toEpochDays().toLong() * 24 * 60 + message.time.hour * 60 + message.time.minute

// --- formatting --------------------------------------------------------------
//
// English names written out here, rather than through the library's locale
// formats: the app's words are the catalog's own content, not the library's
// strings, and every one of them is in Australian English.

private val DayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** "8:31 am": the twelve-hour clock, lower case, the way people here write it. */
internal fun LocalTime.label(): String {
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val minutes = minute.toString().padStart(2, '0')
    return "$hour12:$minutes ${if (hour < 12) "am" else "pm"}"
}

/** "Today", "Yesterday", or "Wednesday 30 September" — the heading over a day of messages. */
internal fun LocalDate.dayHeading(): String = when (this) {
    Today -> "Today"
    Yesterday -> "Yesterday"
    else -> "${DayNames[dayOfWeek.ordinal]} $day ${MonthNames[month.ordinal]}"
}

/** "12 March": a date in a sentence, where the weekday would be noise. */
internal fun LocalDate.shortDate(): String = "$day ${MonthNames[month.ordinal]}"

/**
 * What a chat row says about when: the time for today, "Yesterday", the
 * weekday within the week, and the date before that.
 */
internal fun Message.whenLabel(): String = when {
    date == Today -> time.label()
    date == Yesterday -> "Yesterday"
    Today.toEpochDays() - date.toEpochDays() < 7 -> DayNames[date.dayOfWeek.ordinal].take(3)
    else -> "$day ${MonthNames[date.month.ordinal].take(3)}"
}

private val Message.day: Int get() = date.day

/** How many words [text] has, markdown marks and all stripped first. */
internal fun wordCount(text: String): Int =
    text.replace(Regex("[*`_#>\\[\\]()]"), " ").split(Regex("\\s+")).count { it.isNotBlank() }

// --- the data ----------------------------------------------------------------

private val SeedChats = listOf(
    Chat("ari", "Ari Ngata", ChatKind.Person, "Fremantle markets, most Saturdays", pinned = true, unread = 2),
    Chat(
        "crew", "Joondalup line crew", ChatKind.Group, "The 7:52 from Edith Cowan",
        members = listOf("Kai Tran", "Sam Whitlock", "Mia Fernandes", "Ari Ngata"), pinned = true, unread = 3,
    ),
    Chat("kai", "Kai Tran", ChatKind.Person, "Carriage two, every weekday", unread = 1),
    Chat("sam", "Sam Whitlock", ChatKind.Person, "Book club, and the last 950 home"),
    Chat(
        "markets", "Fremantle markets", ChatKind.Group, "Saturday mornings at the Roundhouse",
        members = listOf("Jo Okafor", "Mia Fernandes", "Ari Ngata"),
    ),
    Chat("mia", "Mia Fernandes", ChatKind.Person, "Biscuits, recipes and opinions about both"),
    Chat(
        "books", "Book club", ChatKind.Group, "Last Friday of the month",
        members = listOf("Jo Okafor", "Sam Whitlock", "Mia Fernandes"),
    ),
    Chat("jo", "Jo Okafor", ChatKind.Person, "Chooses the books nobody finishes"),
)

/** Builds a thread from (day, hour, minute, sender, text), numbering the messages as it goes. */
private fun thread(start: Int, vararg lines: Line): List<Message> =
    lines.mapIndexed { index, line -> Message(start + index, line.sender, line.text, line.date, line.time) }

private class Line(val date: LocalDate, hour: Int, minute: Int, val sender: String, val text: String) {
    val time = LocalTime(hour, minute)
}

private val Sep24 = LocalDate(2026, 9, 24)
private val Sep25 = LocalDate(2026, 9, 25)
private val Sep26 = LocalDate(2026, 9, 26)
private val Sep28 = LocalDate(2026, 9, 28)
private val Sep30 = LocalDate(2026, 9, 30)

private val SeedThreads: Map<String, List<Message>> = mapOf(
    "ari" to thread(
        100,
        Line(Sep30, 18, 2, "Ari Ngata", "Are you still keen for the markets on Saturday?"),
        Line(Sep30, 18, 10, Me, "Yes! Fremantle, then lunch somewhere by the water."),
        Line(Sep30, 18, 11, "Ari Ngata", "Perfect. I'll bring the good tote bags."),
        Line(Yesterday, 21, 15, Me, "What time's the train?"),
        Line(Yesterday, 21, 20, "Ari Ngata", "There's one at 8:22 from Perth Station, platform 7."),
        Line(Yesterday, 21, 20, "Ari Ngata", "Gets in just before nine."),
        Line(Today, 8, 31, "Ari Ngata", "Running ten minutes late, sorry!"),
        Line(Today, 8, 32, "Ari Ngata", "Grab us a coffee if you're at Elizabeth Quay first?"),
    ),
    "crew" to thread(
        200,
        Line(Yesterday, 17, 40, "Sam Whitlock", "Buses replace trains between Whitfords and Joondalup this weekend."),
        Line(Yesterday, 17, 41, "Sam Whitlock", "Track work, apparently."),
        Line(Yesterday, 18, 2, Me, "Good to know, thanks Sam."),
        Line(Today, 7, 44, "Kai Tran", "Anyone on the 7:52? I've saved seats in carriage two."),
        Line(Today, 7, 50, "Mia Fernandes", "Just made it, coming down the escalator now"),
        Line(Today, 7, 51, "Kai Tran", "Stand clear of the doors, Mia."),
        Line(Today, 8, 20, "Kai Tran", "Coffee at Edith Cowan after?"),
    ),
    "kai" to thread(
        300,
        Line(Yesterday, 22, 12, "Kai Tran", "Thanks for dinner. The laksa was unreal."),
        Line(Yesterday, 22, 20, Me, "Any time. Next one's at yours."),
        Line(Today, 7, 2, "Kai Tran", "Do you still have my umbrella?"),
    ),
    "sam" to thread(
        400,
        Line(Sep28, 7, 15, "Sam Whitlock", "Did you get home all right last night?"),
        Line(Sep28, 7, 30, Me, "Yes, caught the last 950 with about a minute to spare."),
        Line(Sep28, 7, 31, "Sam Whitlock", "Classic."),
        Line(Yesterday, 12, 10, Me, "Sending you the photos from Kings Park."),
        Line(Yesterday, 12, 44, "Sam Whitlock", "These are lovely. The one of the city from the lookout especially."),
    ),
    "markets" to thread(
        500,
        Line(Sep30, 12, 0, "Jo Okafor", "Who's in for Saturday?"),
        Line(Sep30, 12, 5, "Mia Fernandes", "Me!"),
        Line(Sep30, 12, 6, "Ari Ngata", "Me too."),
        Line(Sep30, 12, 30, Me, "Count me in."),
        Line(Sep30, 12, 31, "Jo Okafor", "Meet at the Roundhouse at ten."),
    ),
    "mia" to thread(
        600,
        Line(Sep26, 15, 30, "Mia Fernandes", "Here's the recipe for the lemon myrtle biscuits."),
        Line(Sep26, 15, 31, "Mia Fernandes", "170 degrees for twelve minutes, no more. I mean it."),
        Line(Sep26, 16, 2, Me, "Saving this. Thank you!"),
    ),
    "books" to thread(
        700,
        Line(Sep25, 9, 0, "Jo Okafor", "Next month: The Rabbits. Short, but it'll start an argument."),
        Line(Sep25, 9, 20, "Sam Whitlock", "Good choice."),
        Line(Sep25, 9, 31, "Mia Fernandes", "I'm organising snacks this time."),
    ),
    "jo" to thread(
        800,
        Line(Sep24, 20, 0, "Jo Okafor", "Have you started the book club pick yet?"),
        Line(Sep24, 20, 15, Me, "Halfway. No spoilers!"),
    ),
)

/** What each chat says back, in turn. */
private val Replies: Map<String, List<String>> = mapOf(
    "ari" to listOf("Legend, thank you.", "See you on the platform.", "Ha, fair enough."),
    "crew" to listOf("Count me in.", "Same carriage as usual?", "Noted!"),
    "kai" to listOf("No rush. It's not raining yet.", "You're the best.", "Ha!"),
    "sam" to listOf("Sounds good.", "Let's do it.", "Classic."),
    "markets" to listOf("See you at ten.", "I'll bring the bags.", "Perfect."),
    "mia" to listOf("Twelve minutes. Not thirteen.", "Tell me how they turn out.", "Yes!"),
    "books" to listOf("Agreed.", "Bring your copy, mine's lent out.", "Ha, we'll see."),
    "jo" to listOf("No promises.", "Chapter nine. You'll see.", "Good."),
)

private val DefaultReplies = listOf("Sounds good.")

private val SeedNotes = listOf(
    Note(
        "weekend", "Weekend plan",
        "**Saturday:** Fremantle markets with Ari, Mia and Jo. Meet at the *Roundhouse* at ten.\n\n" +
            "Train at `8:22` from Perth Station, platform 7. Bring the tote bags and a SmartRider with credit on it.\n\n" +
            "**Sunday:** nothing. Absolutely nothing.",
        tags = listOf("Plans"), edited = "Today, 7:40 am", pinned = true,
    ),
    Note(
        "books", "Book club picks",
        "October is *The Rabbits*. November is Jo's to choose, so probably something with a map in the front.\n\n" +
            "For my turn: **Cloudstreet** again, or whatever the library has new on the shelf by the door.",
        tags = listOf("Books"), edited = "Yesterday", pinned = true,
    ),
    Note(
        "gifts", "Gift ideas",
        "Mum: a lemon myrtle plant for the courtyard.\n\n" +
            "Kai: a *good* umbrella, given how often he leaves his at mine.",
        tags = listOf("Family", "Birthdays"), edited = "Wednesday", pinned = true,
    ),
    Note(
        "trains", "Joondalup line timings",
        "Weekdays the 7:52 from Edith Cowan gets in at **8:14**. Carriage two stops by the escalator.\n\n" +
            "Buses replace trains between *Whitfords* and *Joondalup* this weekend.",
        tags = listOf("Transit"), edited = "Yesterday",
    ),
    Note(
        "biscuits", "Lemon myrtle biscuits",
        "From Mia. **170 degrees for twelve minutes**, no more.\n\n" +
            "125 g butter, half a cup of sugar, one egg, two cups of flour and a tablespoon of ground lemon myrtle. " +
            "Cream, mix, roll, cut, bake.",
        tags = listOf("Recipes"), edited = "26 Sep",
    ),
    Note(
        "house", "Things to fix at home",
        "The back gate latch. The dripping tap in the laundry. The *colour* of the hallway, which nobody chose.",
        tags = listOf("Home"), edited = "21 Sep",
    ),
    Note(
        "margaret", "Margaret River packing",
        "Rain jacket, walking shoes and the good camera. Book the **cellar door** for Saturday afternoon, " +
            "and organise somebody to feed the cat.",
        tags = listOf("Travel", "Plans"), edited = "14 Sep",
    ),
)
