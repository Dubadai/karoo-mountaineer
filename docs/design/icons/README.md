# Fewer icons, the Strava rosette in colour (round 51)

Feedback (2026-11-10): "can we have the colored Strava rosette? Not just white? Also, we need to clean up
all the icons, so many icons..." Before: a mountain in the one-tile header, another light-blue mountain
before the climb's name, a mountain with a flag for SUMMIT REACHED, a white PR rosette.

Rendered with the field's own code (branch blue-tiles, on top of rounds 48–50):

- ~~The PR rosette in Strava orange~~: round 51b, feedback: "white ribbon was better": white, centred on the
  name's capitals and 3 px from it; only with a PR.
- **One mountain:** the Karoo's ClimbER glyph only in the one-tile header (the Karoo's field header has
  an icon); none before the climb's name.
- **One summit symbol:** the checkered flag, as on the graph's summit line, also in the half screen's
  SUMMIT REACHED bar and the next-500 m boxes.
- Unchanged: the half screen's TO TOP bar keeps the Karoo's own green distance and elevation glyphs.

Round 51b (feedback): no PR (not a Strava segment): the half screen leaves the PR (and TIME VS PR) column
empty, so LENGTH and CLIMB (and TIME TO TOP) keep their places; two tiles show the distance and height
line instead of "-- VS PR". The next-500 m boxes merge a last bit under 50 m into the box before it (no
red sliver).

Round 51c (feedback: "Align texts"): the climb name starts 12 px from the left, under the bar's STARTS IN / TO TOP.

Round 51d (feedback): the climb name in the label's type and size (two tiles: on STARTS IN's baseline; half: as the bar's STARTS IN, 31 px); the small line under a two-tile number 20 px (was 18).

Decision: built (2026-11-10).
