# Shortened rows (map page with a route)

On a map page with a route the Karoo makes a row shorter than it reported (141 → 123 px) without
telling the app, so the field's single picture (fitXY) was squashed, header and number included.
Feedback: "Don't squash, make things smaller instead, like the time." Now nothing squashes:

- **One tile:** header and number at true size (karoo-common `KarooField`, `Body.STRETCH`); the
  climb graph below them stretches.
- **Two tiles:** split in two (`field_wide.xml`): the climb graph on the left stretches; the text
  column on the right keeps its header at true size and makes the time and its small line smaller,
  in proportion, at the bottom right (`KarooField`, `Body.END`).
- **Half screen:** the blue bar at the top and the grade boxes and PR row at the bottom keep their
  size (`footerPx`, karoo-common 1.3.0); the graph between them stretches.
- **Empty states:** the kit's header band, the rest in proportion (`Body.CENTER`).

Pictures from the debug check screen (`SquashCheckActivity`, Android 12L emulator). In each, top to
bottom: the normal height, the old squashed picture, the new version. Light, then dark:
`01-one-tile-*`, `02-two-tiles-*`, `03-half-*`. Decision: "Yes, build it".
