# `FileField`

*Also on this page: `rememberFilePicker`, `PickedFile`, `FileTypes`.*

A field that holds files the reader chose. It has the same frame, label and
messages as any other field, opens the platform's own picker when pressed, and
says what it holds.

<!--sample:FileFieldBasics-->
```kotlin
var files by remember { mutableStateOf(emptyList<PickedFile>()) }

// One control: pressed, it opens the platform's picker; filled, it says
// what it holds, and clears with its own button.
FileField(
    files = files,
    onFilesChange = { files = it },
    label = "Timetable",
    types = FileTypes.of(".pdf", ".csv"),
    supporting = "A PDF or a spreadsheet export",
)
```

**One control, not two.** A "Choose file…" button beside a text area that
echoes the name is two things that are one choice, and the larger of them does
nothing when pressed. The field is the button.

**One file reads as its name and size**, with a button that clears it:
"timetable.pdf  2.4 MB". The name is cut in the middle when it does not fit, so
its extension stays visible. The size is written by `sizeLabel`, which by default
is `FileFieldDefaults.sizeLabel()`. That counts in thousands, as Finder, Files and
Android's storage settings now do, in the reader's digits.

**Several read as chips**, each with its own remove:

<!--sample:FileFieldMultiple-->
```kotlin
var photos by remember { mutableStateOf(emptyList<PickedFile>()) }

// Several add up, a chip each; a file already there is not added twice.
FileField(
    files = photos,
    onFilesChange = { photos = it },
    label = "Photos",
    types = FileTypes.of("image/*"),
    multiple = true,
)
```

With `multiple`, a pick adds to what the field holds and skips a file that is
already there (same name and size). Without it, a pick replaces the file. A
picker closed without a choice changes nothing.

**`types` is what the picker offers.** Pass media types, with a star for a whole
family (`image/*`), or extensions (`.pdf`). A file that matches any of them is
taken. Not every platform's picker can be told what to show: a desktop's file
dialog shows everything on macOS and Windows. So what comes back is checked again,
and anything else is dropped before the field sees it.

**Files can be dropped on it**, dragged in from outside the app: the desktop,
another window, the Files app beside yours on an iPad. A drop is taken as a pick
is, and only files of `types` are offered the field at all. While one is over it,
the frame lights as it does for focus and the empty field says "Drop file here".
`acceptsDrops = false` turns this off. Where a drag from outside can happen
depends on the platform: see [drag and drop](../drag-and-drop.md#from-outside-the-app).

**The field reads nothing.** It holds `PickedFile`s: a name, a size and a media
type where the platform reports them, and `readBytes()` and `readText()` to read
the contents on demand. Uploading, or parsing, is the caller's, when it is ready.

## The picker on its own

`rememberFilePicker` is the picker without the field: for an "Add photos" button,
a drop-down item, or a toolbar.

<!--sample:FilePickerButton-->
```kotlin
val scope = rememberCoroutineScope()
val picker = rememberFilePicker { files ->
    // Read when you are ready, not when it is picked.
    for (file in files) scope.launch { upload(file.name, file.readBytes()) }
}

Column {
    // Launched straight from the press: a browser opens a picker only
    // while the press that asked for it is being handled.
    Button(onClick = { picker.launch(FileTypes.of("image/*"), multiple = true) }) { +"Add photos" }
}
```

**`launch` does not suspend, and that matters.** A browser opens a picker only
while it is still handling the press that asked for it. A coroutine resumes after
that, and the browser refuses. So call `launch` straight from an `onClick`, and
read the files in a coroutine afterwards.

| Platform | The picker | What it needs |
|---|---|---|
| Android | The system document picker (`OpenDocument`) | An activity: it goes through the activity's result registry. Composed with none, as in a preview, it picks nothing. |
| iOS | `UIDocumentPickerViewController`, presented over the Compose view controller | Nothing. Picked files are copies the app owns, so they can be read after the picker has gone. |
| Desktop | The platform's file dialog, over the focused window | A screen. Headless, as on a CI runner, it picks nothing rather than throwing. |
| Web | The page's own file input | The press. Closed without a choice, current browsers report it and the picker hands back nothing. An older browser reports nothing, and the callback does not come. |

**`LocalFileChooser` replaces the platform's picker.** Provide a `FileChooser`,
a single function, for a test, a preview, or an app that picks from its own
storage. Every `FileField` and `rememberFilePicker` below it then uses yours:

```kotlin
val fake = FileChooser { _, _, onResult ->
    onResult(listOf(PickedFile("timetable.pdf", 2_400_000, "application/pdf") { ByteArray(0) }))
}
CompositionLocalProvider(LocalFileChooser provides fake) { Form() }
```

`rememberPlatformFileChooser()` is the platform's own, for a chooser of yours
that wraps it rather than replaces it: one that records what was asked for, or
offers the app's own files first and the platform's after.

---

## Accessibility

The field is one button, named by its label, with what it holds as its value:
"Timetable, timetable.pdf, 2.4 MB, button". Empty, it says
`Theme.strings.noFileChosen` ("No file chosen") instead of reading out the
placeholder. The clear button and each chip's remove are separate controls, each
named for its file: "Remove timetable.pdf".

At high contrast the frame has the edge every field has, and each chip its own;
nothing else changes, as nothing else is drawn on a ground of its own. Nothing in
the field moves, so reduced motion changes nothing.

The words are `Theme.strings`: `chooseFile`, `chooseFiles`, `noFileChosen`,
`fileCount`, `fileBytes`, `fileSize`, `kilobytes`, `megabytes`, `gigabytes`,
`removeFile`, `dropFileHere` and `dropFilesHere`. They translate with the rest.

A drop is for a pointer only, and nothing is announced while one is over the
field: pressing it does the same with the picker.
