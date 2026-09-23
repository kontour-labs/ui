# Module kontour-ui-nav3

Navigation 3 scene strategies that lay a back stack out with Kontour UI's pane
scaffolds: a list beside its detail, and a main pane with a supporting one that
becomes a sheet on a narrow window.

A module of its own so that the library takes no navigation dependency: the
scaffolds work for anyone, and this is the part that reads a back stack. The
ready-made adaptive strategy for Navigation 3 is Material's; this one is built on
Foundation and the library alone, and its build fails if Material ever reaches
its classpath.

**This is the reference, not the documentation.** How to set it up, and why the
scenes are keyed the way they are, is in the Navigation 3 guide in
`ui-docs/content/navigation3.md`.

# Package io.kontour.ui.nav3

`rememberListDetailSceneStrategy` with `listPane` and `detailPane`, and
`rememberSupportingPaneSceneStrategy` with `mainPane` and `supportingPane`.
