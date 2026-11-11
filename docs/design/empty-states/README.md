# A route loaded always shows the map (rounds 59–60)

Feedback (2026-11-10): "it says no climbs on the route, with Mallorca 312 loaded" (`00-before-light.png`);
"don't have the subtitle unless needed"; "if I am done climbing, show the full elevation map still";
"only show the empty state if there is no route loaded … with a route loaded and zero climbs, show the
full elevation map, and just show distance left"; "we do show reroutes, so off route feels weird to show".

Rendered with the field's code:

- **No route loaded:** the only empty screen: No Route · "Load a route with climbs" (24 px, darker grey).
- **A route but no climb to show** (all climbs done, none on the route, or off route before the Karoo
  lists any): the whole route, done climbs faded, you on it, **DIST TO FINISH** with the distance left
  ("--" when the Karoo has none). Two tiles' small line: "All 10 climbs done"; the half screen's bottom
  row: CLIMBS DONE 10/10 (only when there were climbs). (`done-*`, `noclimbs-*`, `offempty-*`)
- **Off route:** no OFF ROUTE words anywhere; the red way back says it (`offnear-*`).

Decision (2026-11-10): "Yes, install it". Built.
