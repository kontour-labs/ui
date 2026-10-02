# `IndexScroller`

*Also on this page: `IndexAlphabet`.*

A rail of letters beside a long list, as a contacts list has: press a letter,
or run a finger down them, and the list jumps to the section that starts there.

<!--sample:IndexScrollerBasics-->
```kotlin
val formats = Theme.formats
val alphabet = IndexAlphabet.forLocale(Theme.locale)
// Grouped under the reader's alphabet, with where each group starts in
// the list: one header item and its rows.
val indexed = remember(stops, alphabet, formats) { stops.indexedBy(alphabet, formats) { it.name } }
val list = rememberLazyListState()

Box {
    // Room at the end for the letters, so no row runs under them.
    LazyColumn(state = list, contentPadding = PaddingValues(end = IndexScrollerDefaults.Width)) {
        indexed.groups.forEach { group ->
            item { SectionHeader { +group.label } }
            items(group.items) { stop -> ListItem { +stop.name } }
        }
    }
    IndexScroller(list, indexed.sections, Modifier.align(Alignment.CenterEnd), alphabet = alphabet)
}
```

**`indexedBy` does the grouping.** It files each item under a label of an
`IndexAlphabet`, puts the groups in the alphabet's order, and says where each
starts in a `LazyColumn` laid out from them: `sections`, which is what the rail
jumps to. It counts a header item at the start of each group, as above; pass
`headers = false` for a list without them, and `leadingItems` for a search field
or a pinned row above the first group.

**Every label is on the rail**, the ones with nothing under them dimmed. A
press on an empty one goes to the next section that has something. The rail is
a map of the whole alphabet, and one whose letters came and went as names were
added and deleted would move every letter below the change.

**On a short window the rail folds**, a dot standing for each run of letters it
has no room for, with the first and last letters always shown. The finger still
reaches every letter: running down a dot passes through the letters it stands
for, with a tick for each, and the bubble beside the finger says which one it is
on.

**The list jumps rather than scrolls.** A jump of a hundred rows is not motion
anybody needs to watch, and the finger is still moving.

The rail is a touch target wide, for a finger, with the letters drawn against
the end edge in `IndexScrollerDefaults.Width`. Put it over the list with
`Modifier.align(Alignment.CenterEnd)` in a `Box`, and pad the list's end by that
width, as above, so no row's end runs under the letters. Add `fillMaxHeight()`
to the rail to spread the letters over the whole height.
The bubble is an overlay, so an app needs an `OverlayHost`, as it does for a
tooltip; the rail itself draws without one until it is touched.

### Alphabets

`IndexAlphabet.forLocale(Theme.locale)`, the default, is the reader's script,
then Latin for the names a list in any language has some of, then `#` for
whatever no letter takes: a digit, an emoji, a Chinese character.

| | Files a word under |
|---|---|
| `Latin` | Its first letter, without its accent or case: `Émile` under E |
| `Cyrillic` | Russian's letters; Ё under Е. Ukrainian gets its own, with Ґ, Є, І and Ї |
| `Greek` | Its first letter, an accented vowel under its own |
| `Hebrew` | Its first letter, a final form under its letter |
| `Arabic` | Its first letter, an alif with hamza under alif |
| `Kana` | The head of its kana row: が and カ under か |
| `Hangul` | Its first consonant, a doubled one under its single: 꽃 under ㄱ |

An alphabet of your own wins over the folding, because an exact match is tried
first:

```kotlin
val swedish = IndexAlphabet.Latin + IndexAlphabet(listOf("Å", "Ä", "Ö"))
```

files `Ödla` under Ö, where the fold alone would put it under O. Chinese has no
alphabet here, because it is indexed by something other than its characters,
such as pinyin or stroke count. Index by that, through `indexedBy`'s `text`, with
an alphabet of its labels.

---

## Accessibility

The rail is **one adjustable control** to a screen reader, named "Section
index", with the current section as its value. `#` is said as "Other". Swiping
up or down on it, or the volume keys on Android, moves the list a section at a
time, and it carries "Next section" and "Previous section" as actions as well.
The letters are not separate stops: twenty-seven of them would be twenty-seven
swipes to get past.

It takes keyboard focus, with a ring. The up and down arrows move a section at a
time, and Home and End go to the first and last.

The current section's letter is drawn in the accent and in bold, so it is not
told apart by colour alone. A letter with nothing under it is in the disabled
grey; at high contrast it keeps the muted ink of the others instead, because it
can still be pressed and has to be read. Disabled, every letter is grey and the
rail does nothing, and its actions are withheld rather than just marked.

A tick is felt as the finger crosses from one letter to the next.

Under reduced motion nothing changes, because nothing here moves on its own:
the list already jumps without animating, and the bubble is the tooltip's.
