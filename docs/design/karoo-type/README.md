# Karoo type (before / after)

From the workspace's shared `KarooType.kt` (workspace fdeab4b, notes in
`design-assets/karoo-type/README.md`): the Karoo's own field typography.

After: numbers in the Karoo's value font **Relative** (system font, loaded with
`Typeface.create("relative")`, never bundled), letter spacing −0.04, no narrowing; headers sized and
placed by the Karoo's header rules (caps 23 / 20 px, caps top, 7 px right inset, icon box); the
one-tile time right under the header (8 px gap).

`before/` and `after/`: the field gallery (real code, Karoo 3 emulator with the Karoo's fonts).
`compare.html` (`python3 make_compare.py`): side by side, light and dark.

Decision: pending (build onto the Karoos only after our OK).

## Update (workspace 93a9014)

Re-copied `KarooType.kt`. Values drawn in Relative go through `valueText` (no "−" in Relative, so
ahead of PR reads "-0:24"). The calibrated `valueSize` / `valueBaseline` (tileH − 35, 14 px above the
bottom) are for a tile that holds only a value; Mountaineer's tiles share the height with the climb
graph, so its own placement stays. The gallery has an "ahead" variant now.
