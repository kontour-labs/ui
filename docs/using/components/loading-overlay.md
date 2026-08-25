# `LoadingOverlay`

Blocks the whole screen while something must not be interrupted.

**It should be rare.** Blocking everything for something that usually takes 200ms
trades a brief wait for a flash of grey, and the flash is worse. Prefer a
[`Skeleton`](skeleton.md) where the result will fill a known shape, an inline
[`Spinner`](spinner.md) where one region is loading, and a
[`Button`](button.md)'s own `loading` state where the user pressed a button.

`label` is required and is read aloud, because a blocked screen with no
explanation is indistinguishable from a frozen one.

---

← [Overlays](overlays.md) · [All components](../components.md)
