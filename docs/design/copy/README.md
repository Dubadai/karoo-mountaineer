# Copy: "climb" once (round 53)

Feedback (2026-11-10): "Copy is still confusing since now we have 2 things that say Climb, both CLIMB IN
and Climb 1 / 30." Three options, drawn with the field's code (`copy<A|B|C>-<far|short>-<size>-light.png`):

- **A:** the ClimbER mountain says "climb": name line "⛰ 1 of 30" (a named climb: ⛰ + its name); label CLIMB IN.
- **B:** STARTS IN where the name line gives context (two tiles, half: "Climb 1 of 30"); one tile CLIMB IN.
- **C:** CLIMB IN; no name line before the climb (it shows once you're on it).

Also open: the half screen's CLIMB column (height gain) is a third "climb"; GAIN would avoid it.

- **D** (feedback: "DIST TO CLIMB or something more clear"): DIST TO CLIMB (like the Karoo's own DIST TO
  TOP), A's name line (⛰ 1 of 30), the half screen's height column GAIN. It fits on one line on the
  smallest tile and the half screen's bar; on one tile (168 high) and two tiles it wraps onto two lines
  like the Karoo's own long labels, and the number gets smaller there.

Two tiles, D one line (feedback: "Can you not fit DIST TO CLIMB here without wrapping?"): the label needs
182 px (141 px rows) / 209 px (taller rows) at the Karoo's label size; the graph side is now 250 px
(was 278) and the label may use the column's full width (213 px), so it fits on one line at full size.

Distances (feedback: "Yes, under 1 km … as long as we are consistent, let's apply a common rule"): every
distance, big number, half screen bar and small line alike, in metres to 10 m under 1 km (800 m), in km
with one decimal from 1 km (22.9 km) — `Fmt.distance`.

Decision (2026-11-10): D, built: DIST TO CLIMB, the mountain + "1 of 30", GAIN, one distance rule.
