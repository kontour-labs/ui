package io.kontour.ui.docs.icons

/**
 * The icons that answer [query], closest first; every icon, in name order, for
 * an empty one.
 *
 * **Every word has to answer.** `arrow up` is the arrows that go up, not every
 * arrow and every up. Each word is scored by the best thing it matches, and an
 * icon's score is its words' sum:
 *
 * - 10, the name is the word: `home` for *home*;
 * - 9, the name starts with it: `home-2`;
 * - 8, a synonym names it or its family: `settings` for *gear*;
 * - 7, a word of the name starts with it: `user-check` for *check*;
 * - 6, it is the tag Tabler leads with: `home` for *house*;
 * - 5, it is one of Tabler's tags;
 * - 4, a word of a tag starts with it;
 * - 3, a word of the category starts with it: *build* for `Buildings`;
 * - 2, it is inside a word of the name;
 * - 1, it is one letter off a word of the name or a tag: *hosue*.
 *
 * Ties go to the plainer icon — fewer words, so `home` before `home-check` —
 * and then to the name. A plural is tried singular too, so *houses* finds what
 * *house* does.
 *
 * Tabler's tags carry most of it: `home` is tagged *house*, `search`
 * *magnifier*. [IconSynonyms] adds the common words the tags miss.
 *
 * @param fold Case and accent folding for the query, in the reader's language;
 *   Tabler's own names and tags are lower-case ASCII already.
 */
internal fun searchIcons(
    entries: List<IconEntry>,
    query: String,
    fold: (String) -> String = { it.lowercase() },
): List<IconEntry> {
    val words = words(fold(query.trim()))
    if (words.isEmpty()) return entries
    val scored = ArrayList<Pair<IconEntry, Int>>()
    for (entry in entries) {
        var total = 0
        for (word in words) {
            val score = wordScore(entry, word)
            if (score == 0) {
                total = 0
                break
            }
            total += score
        }
        if (total > 0) scored += entry to total
    }
    return scored
        .sortedWith(
            compareByDescending<Pair<IconEntry, Int>> { it.second }
                .thenBy { it.first.parts.size }
                .thenBy { it.first.name },
        )
        .map { it.first }
}

private fun words(text: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    for (c in text) {
        if (c.isLetterOrDigit()) {
            current.append(c)
        } else if (current.isNotEmpty()) {
            out += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) out += current.toString()
    return out
}

/** The word, and its singular if it reads as a plural: *houses*, *batteries*. */
private fun forms(word: String): List<String> {
    if (word.length <= 3) return listOf(word)
    val out = ArrayList<String>(3)
    out += word
    if (word.endsWith("ies")) out += word.dropLast(3) + "y"
    if (word.endsWith("es")) out += word.dropLast(2)
    if (word.endsWith("s") && !word.endsWith("ss")) out += word.dropLast(1)
    return out
}

private fun wordScore(entry: IconEntry, word: String): Int {
    var best = 0
    for (form in forms(word)) {
        val score = when {
            entry.name == form -> 10
            entry.name.startsWith(form) -> 9
            IconSynonyms[form]?.any { entry.name == it || entry.name.startsWith("$it-") } == true -> 8
            entry.parts.any { it.startsWith(form) } -> 7
            entry.tags.firstOrNull() == form -> 6
            form in entry.tags -> 5
            entry.tagWords.any { it.startsWith(form) } -> 4
            entry.categoryWords.any { it.startsWith(form) } -> 3
            form.length >= 3 && entry.parts.any { form in it } -> 2
            else -> 0
        }
        if (score > best) best = score
    }
    if (best == 0 && word.length >= 5 &&
        (entry.parts.any { nearly(it, word) } || entry.tagWords.any { nearly(it, word) })
    ) {
        best = 1
    }
    return best
}

/** Whether [a] and [b] are one edit apart: a letter changed, added, dropped, or two swapped. */
internal fun nearly(a: String, b: String): Boolean {
    if (a.length < 4 || kotlin.math.abs(a.length - b.length) > 1) return false
    if (a.length == b.length) {
        var first = -1
        var second = -1
        for (i in a.indices) {
            if (a[i] != b[i]) {
                if (first < 0) first = i else if (second < 0) second = i else return false
            }
        }
        if (first < 0 || second < 0) return true
        return second == first + 1 && a[first] == b[second] && a[second] == b[first]
    }
    val (short, long) = if (a.length < b.length) a to b else b to a
    var i = 0
    while (i < short.length && short[i] == long[i]) i++
    return short.regionMatches(i, long, i + 1, short.length - i)
}

/**
 * Words people search icons by that Tabler's tags do not lead to, each with
 * the icons, or families of icons, it means: *gear* is `settings`, and
 * `settings-2` and the rest of that family.
 *
 * Each was kept because it changed what a search shows first, measured on
 * Tabler 3.48's tags; one that changed nothing is not here. British spellings
 * are among them, since Tabler's are American.
 */
internal val IconSynonyms: Map<String, List<String>> = mapOf(
    "add" to listOf("plus"),
    "attach" to listOf("paperclip"),
    "avatar" to listOf("user"),
    "cancel" to listOf("x"),
    "cart" to listOf("shopping-cart"),
    "chat" to listOf("message"),
    "close" to listOf("x"),
    "cog" to listOf("settings"),
    "colour" to listOf("color"),
    "comment" to listOf("message"),
    "cross" to listOf("x"),
    "dark" to listOf("moon"),
    "delete" to listOf("trash"),
    "directory" to listOf("folder"),
    "document" to listOf("file"),
    "duplicate" to listOf("copy"),
    "earth" to listOf("world"),
    "ellipsis" to listOf("dots"),
    "email" to listOf("mail"),
    "exit" to listOf("logout", "door-exit"),
    "expand" to listOf("chevron-down", "arrows-maximize"),
    "favourite" to listOf("star", "heart"),
    "find" to listOf("search"),
    "gear" to listOf("settings"),
    "globe" to listOf("world"),
    "group" to listOf("users", "users-group"),
    "hamburger" to listOf("menu-2"),
    "hidden" to listOf("eye-off"),
    "hide" to listOf("eye-off"),
    "image" to listOf("photo"),
    "internet" to listOf("world"),
    "kebab" to listOf("dots-vertical"),
    "light" to listOf("sun"),
    "like" to listOf("heart", "thumb-up"),
    "magnifier" to listOf("search"),
    "more" to listOf("dots"),
    "mute" to listOf("volume-off", "volume-3"),
    "new" to listOf("plus"),
    "next" to listOf("chevron-right", "arrow-right"),
    "night" to listOf("moon"),
    "notification" to listOf("bell"),
    "password" to listOf("lock", "key"),
    "paste" to listOf("clipboard"),
    "pause" to listOf("player-pause"),
    "people" to listOf("users"),
    "person" to listOf("user"),
    "picture" to listOf("photo"),
    "place" to listOf("map-pin"),
    "previous" to listOf("chevron-left", "arrow-left"),
    "profile" to listOf("user"),
    "reload" to listOf("refresh"),
    "remove" to listOf("trash", "minus"),
    "save" to listOf("device-floppy"),
    "security" to listOf("shield", "lock"),
    "show" to listOf("eye"),
    "sound" to listOf("volume"),
    "stop" to listOf("player-stop"),
    "success" to listOf("circle-check", "check"),
    "sync" to listOf("refresh"),
    "team" to listOf("users", "users-group"),
    "visible" to listOf("eye"),
    "web" to listOf("world"),
    "write" to listOf("pencil"),
)
