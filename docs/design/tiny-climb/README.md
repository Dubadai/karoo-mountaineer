# Tiny Climb (round 1)

Mountaineer's first field. On a climb it shows where you are on the climb and how long until
the top, in the Karoo's own field style (mountain icon top left, capitals top right).

Sizes (light | dark, ride page with data field boundaries on):

- **One tile** (238×168): `TO TOP` 9:42 counting down, or `VS PR` +0:18 (red behind, green
  ahead); a tiny climb profile under it.
- **Two tiles** (478×168): climb name in the header, a longer profile, time to top big; under it
  distance and height left, or the gap to PR (`Both`).
- **Half screen** (478×348): climb name, length and average grade; Strava PR | VS PR | To top;
  the profile big with the grade where you are, km done and km to the top.
- **Not on a climb**: next climb on the route, how far to its start, its profile in grey.

Profile: one piece per 400 m in its grade colour, the Karoo's own climb colours (ride app
`climb1..7`: <3 % · 3–5 · 5–7 · 7–9 · 9–11 · 11–13 · 13+), climbed part grey, the rider as a
▼ with a line (BetterIntervals' "now" marker).

Settings (light): Example card; One tile shows (Time to top / Gap to PR); Two tiles show (Time to
top / Gap to PR / Both); Half screen shows (switches: Climb name, Strava PR, Gap to PR, Time to
top); Climbed part (Grey / Coloured); Strava Connect.

Open points: where climbs come from (the Karoo's ClimbER climbs on a loaded route, if karoo-ext
exposes them) and the Strava PR (Strava API sign-in, segment matched to the climb).
Demo climb "Col du Lac" is made up.

## Round 2

Feedback: label the time "ETA TO TOP", add "DIST TO TOP"; a small Strava symbol next to the
mountain on the PR views, and try other Strava-style symbols.

- Labels: `ETA TO TOP` (countdown), `DIST TO TOP` (km left), `VS PR`.
- One tile shows: ETA to top / Dist to top / Gap to PR. Two tiles show: ETA / Distance / Gap to
  PR / Both. Half screen: ETA TO TOP column.
- Strava symbol next to the mountain wherever a PR shows (one tile VS PR, two tiles Gap / Both,
  half screen by the climb name). Three candidates, light and dark: Chevron (Strava's own mark in
  Strava orange: its use needs a check against Strava's brand rules), PR medal, orange PR badge.

Files: `mockup.src.html` + `build.py` → `mockup.html` (rounds 1–2 in one page, round 2 current).
Decision: chevron dropped.

## Round 3

Feedback: let riders choose between the PR badge and the medal in Settings.

- Settings, at the end (Example sticky, one tile VS PR): **PR symbol** PR medal (default) / PR badge,
  each drawn in its segment; then Strava Connect (moved here from the first screen).
- The PR symbol grid shows only medal and badge.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 4: half screen options

Feedback: only the half screen needs work (typography, sizing, alignment); learn from the Karoo's
own climb view.

Native ClimbER, read from the Karoo ride app's resources (2026-11-08; 1 dp = 1.875 px):
- Climb header (`layout_climb_header`, 51 dp): climb number in a pill (18 sp, `#A3D5FF` on
  `#00294B`, radius 8 dp), then three numbers at 30 sp, centred, split by 1 dp lines.
- Detail bar (`layout_tab_climb`, 30 dp, `#005297`): "To Top" (17 sp condensed, white), distance
  and height left with 20 dp icons, 26 sp white (`ClimbDetailText`).
- Climb blue: 400 `#A3D5FF`, 500 `#2386D9` (lines), 600 `#005297` (bars), 700 `#00294B`.
- Grade colours: `climb1..7` (what we use already). Profile: `ElevationAndGradeView`, plus a
  grade strip `GradientDetailView` (28 dp half / 52 dp full).
- Numbers use the font "relative" (Relative12, the Karoo's `monospace` system font). Not in the
  mock (commercial font, kept out of git); the app could use it through the system monospace.
- Strings: "To Top", "Starting Climb %d", "Next Climb: %d of %d".

Options (one type scale each: labels PlexCond, numbers PlexReg at 0.82 width):
- **A · Karoo field grid**: header row like a Karoo field (name in label capitals), label over
  number right-aligned 9 px from each column edge, distances in small capitals under the profile.
- **B · Native ClimbER**: climb-blue header with the numbered pill, three centred numbers with
  thin blue lines, blue TO TOP bar (distance, height) at the bottom; blue in light and dark.
- **C · Profile first**: name, length, km left and a grade chip in its grade colour on top, the
  profile big, the three numbers along the bottom, all left-aligned on one edge.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 5: with the Karoo's own climb view

Feedback: two pictures of the Karoo's ClimbER (map drawer and climb page, kept out of this repo);
redo the mocks from them.

What ClimbER does (pictures + the ride app's code):
- Graph on navy `#00294B` in light and dark, zoomed to about a kilometre around you.
- 100 m columns in their grade colour; ahead they are flat-topped steps; the climbed part is
  hatched (stripes of the grade colour on navy).
- You: a white ring on the profile with a white line down.
- The next 500 m is lit in climb blue 600 `#005297` above the profile, for scale.
- Grade strip under the graph: each 100 m's grade to one decimal in its colour, white lines between.
- TO TOP bar in climb blue: distance and height left with their icons.
- Climb page: graph and strip on top, then navy field tiles (DIST TO TOP, ELEV TO TOP, CLIMB 1/2, GRADE).
- **Grade colours (ride app code, grade rounded to 0.1):** <2 % c1, <5 c2, <8 c3, <11 c4,
  <14 c5, <20 c6, else c7; text black on c1–c4, white on c5–c7. These replace our round 1 guess.

Options:
- **A · Climb drawer**: TO TOP bar on top, zoomed graph + strip, Strava PR / VS PR / ETA TO TOP on navy.
- **B · Climb page**: zoomed graph + strip, three navy tiles in the Karoo's field layout.
- **C · Fields + whole climb**: name and three numbers as black / white Karoo fields, the whole climb
  as the Karoo's graph (200 m bars), TO TOP bar at the bottom.
- Extra (not asked): one and two tiles with the same graph and the Karoo's zigzag climb icon.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 6: half screen is A (Climb drawer)

We picked A. It is now the half screen everywhere (and the Settings Example). States shown:
behind PR (red), ahead of PR (green), fewer lines (Settings › Half screen shows: ETA only;
Gap + ETA with the name off; the numbers left get the room, 52 / 60 / 66 px for 3 / 2 / 1),
not on a climb (bar: NEXT CLIMB + distance to its start; the whole climb in its colours, 200 m
bars; Strava PR, length, height). The round 3–5 half screens stay in git history.
Still open: the one / two tiles with the Karoo's graph (extra section) or the round 3 tiles.

Decision: half screen A; tiles pending.

## Round 7: tiles with the Karoo's graph

Feedback: yes, the Karoo's graph on the tiles too. One tile and two tiles now use the same navy graph
as the half screen (one tile 400 m bars, two tiles 200 m bars, climbed part hatched, ring, next
500 m lit), the Karoo's zigzag climb icon, every state: ETA, distance, VS PR behind / ahead,
Both, next climb (whole climb in its colours, no ring). Settings Examples and the PR symbol grid
follow. The round 1–3 white profile with the ▼ marker is in git history.

Decision: half screen A, tiles with the Karoo's graph. Whole design: pending the final OK.

## Built (v0.1, 2026-11-08)

Feedback: "Lets build it". Built as designed in round 7. Changes while building:
- Strava: no Connect row. The PR and gap come from the Karoo's own Strava Live Segments
  (karoo-ext `SEGMENT_PR`, `SEGMENT_TIME_TO_PR`), so no Strava sign-in in the app.
- Climb names: the Karoo doesn't name route climbs, so a real climb shows "Climb 2 of 5"; the
  Sa Calobra test ride shows its name.
- `device/01-settings-light.png`: Settings on our test Karoo.
- `device/02-beta-page-light.png`: the "Mountaineer" page on our BETA profile (Tiny Climb at
  the top, Power, Cadence), Sa Calobra test ride 5.2 km up, in the Karoo's page editor. One Tiny
  Climb per page; the Karoo sizes fields by how many a page has (3 fields: Tiny Climb gets the top
  ~455 px; 4: a 240 px row; 5: a 141 px row).
- Fix after a look on the device: the half screen painted over the Karoo's tile border and rounded
  corners. The renderer now clips everything to the inside of the tile (1 px border, 14 px radius).

## Round 8: four sizes, columns like the Karoo

Feedback (after the first ride view, `device/03-beta-sizes-light.png`, `device/04-beta-largest-light.png`):
name the lit 500 m; the columns jump (flat-topped steps); the grade strip doesn't work at nearly
half; there are four sizes and only the biggest should have the strip; look at how the Karoo does it.

How the Karoo draws its climb graph (ride app `ElevationAndGradeView`, read 2026-11-08): one
smooth path through the elevation points, scaled into the view; `clipPath` to it; then each grade
segment is a full-height `drawRect` in its grade colour (right edge `ceil`ed, so no gaps), so the
tops follow the road; the climbed part gets a striped bitmap on top (same clip); the "upcoming"
rect is drawn behind the profile; the marker is a line and two circles. Its climb-graph data
field has no grade strip; the strip (`GradientDetailView`) is only in the ClimbER drawer
(28 dp half / 52 dp full height).

Round 8:
- Columns: full-height colour cut out by the smooth profile (no steps), at every size.
- The lit band says **NEXT 500 M** (condensed capitals, climb blue 400) wherever it fits; in the
  small tiles it's too narrow, so no label.
- Four sizes: one tile, two tiles (any height under 250), **nearly half** (250–329: TO TOP bar,
  graph, PR row, no strip), **half and up** (330+: with the grade strip).
- The zoomed window starts on a 100 m mark (5 columns behind, 7 ahead) and only whole columns get
  a number (no cut-off "1.0" / "6" at the edges, as on the device).

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 9: the NEXT 500 M band

Feedback: maybe dark tonal, or not all the way to the top. Four looks at the biggest size and
nearly half: A climb blue to the top (round 8), B dark tonal (#0A3A66, label #7FB4E6) to the
top, C climb blue stopping above the road, D dark tonal stopping above the road (rises at least
26 px / 24 % of the graph above the highest point in those 500 m, rounded top corners, label on
it). D also shown on a page like our BETA page, light and dark.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 10

Feedback: "C, and hidden in the smallest two". NEXT 500 M is C (climb blue 600, stops above the road,
rounded top, label on it) on nearly half and up; one tile and two tiles show no band. Shown on a
page like ours and at the biggest size, light and dark.

Decision: C picked; build pending the go.

## Round 11: two tiles, typography and alignment

Feedback (round 10, two tiles): improve typography and alignment; the climb name big? should the
number be bottom aligned? One rule for all options: the big time sits on the graph's bottom edge
(bottom aligned, like the Karoo's fields), VS PR sits on the graph's top edge, the right column
ends 9 px from the tile edge, the time grows to fill the room between them (up to 110 px).
- **A · Karoo header**: name in the Karoo's label capitals (COL DU LAC), same size as ETA TO TOP.
- **B · Big name**: name in 40 px capitals as the title; ETA TO TOP smaller and grey.
- **C · Name on the graph**: like the half screen, the graph uses the full height with the name on
  its sky; the right side is a Karoo field (label, VS PR, time at the bottom).
Each on a page like ours (two tiles 212 px), plus a grid at 212 and 168 px, light and dark.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Field name

Feedback (round 11): the field is called **Mountaineer**, not Tiny Climb (one field: the app's name, as
the playbook says). Its typeId stays `tiny-climb` so ride pages keep it. The design folder keeps
its name.

## Round 12: two tiles = C, VS PR under the time

Feedback: "C, but have the vs PR below the eta, bottom right aligned". Two tiles: the graph uses the
full height with the name on its sky; right column: ETA TO TOP (label) at the top, the time under
it (as big as fits, up to 110 px), VS PR bottom right on the graph's bottom edge. Shown on a page
like ours and at 212 and 168 px, light and dark.

Decision: pending (with rounds 8–10, ready to build).

## Built (rounds 8–12, 2026-11-08)

Feedback: "Yes, build it". In `ClimbRenderer`: the graph drawn like the Karoo's (clip to the smooth
profile, full-height grade rectangles, navy stripes on the climbed part), NEXT 500 M as C on
nearly half and up only, the grade strip only from 330 px (design) tall, the zoomed window from a
100 m mark with whole-column numbers, two tiles as round 12. Field named Mountaineer.
- On our test Karoo (build b4d3098, page editor): `device/05-built-r12-next-light.png` (before the
  climb), `device/06-built-r12-climbing-light.png` (5.2 km up): two tiles, one tile, nearly half.

## Round 13: whole climb always; the strip is the next 500 m; heavier numbers

Feedback (with a ClimbER picture): the big size's gradients still aren't right; like the Karoo,
always show the whole climb; the next 500 m drives the boxes below, bigger, moving as you climb,
in the colours of that 500 m. Also: fonts look a bit thin on the Karoo.
- Half screen sizes: the graph always shows the whole climb (100 m columns), no zoom.
- Biggest size: the strip (52 px) is the next 500 m: five 100 m boxes in their grade colours,
  starting where you are, sliding left as you climb (the 100 m you're in leaves on the left, its
  number goes once under 60 % shows; the next comes in on the right). Nearly half: no strip.
- Numbers: Plex Regular at 90 % width with letters 0.05 em tighter, as the Karoo's fields (and
  BetterIntervals, matched on the device). Our 82 % width thinned the strokes.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 14: marking the next 500 m on the whole climb

Feedback: something creative to still show the next 500 m (the lit box is lost on the whole
climb), and more contrast. Four markers, at the biggest size and nearly half:
- A · Bracket: a white dimension line over the road for those 500 m, ticks at both ends, "500 M".
- B · Spotlight: the road beyond the 500 m dimmed (navy at 62 %), a white edge along the road for
  the 500 m; the next 500 m are the brightest thing on the graph and match the strip below.
- C · End line: a second, thinner white line where the 500 m end (the Karoo's own way), "500 M".
- D · Spotlight + bracket (also on the device, light and dark).

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 15

Feedback: "D but dont tone down the remaining climb". The next 500 m: a white edge (2.5 px) along
the road and the 500 M bracket over it; the rest of the climb keeps its full colours. Only on
nearly half and up (one tile / two tiles: none). Shown on a page like ours and at the biggest
size, light and dark. With round 13 (whole climb, next-500 m strip, heavier numbers).

Decision: picked; build pending the go.

## Round 16

Feedback: "Make the 500m text a tad bigger". The 500 M label: 20 px (was 16), 6 px above the bracket.

## Built (rounds 13–16)

Feedback: "Yes, build it". Whole climb on the half screens, the next-500 m strip (five sliding boxes, 52 px) on the biggest size, the white edge + 500 M bracket (20 px label), numbers at 90 % width with letters 0.05 em tighter.
- `device/07-built-r16-light.png`: build b9c66e5 on our test Karoo, test ride 5.2 km up (two tiles, one tile, nearly half).

## Round 17

Feedback: "500m text is too small" (built at 20 px). The label at 24 px (A) and 28 px (B), biggest size and nearly half; the bracket unchanged.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 18

Feedback: "28 and make the bracket same thickness as the vertical white line. Also we can remove this white line. Have the bracket almost meet the circle dot." 500 M label 28 px; bracket line as thick as the white line under you (0.44 x the ring); left end comes down to 3 px above the ring, right end to 3 px above the road at 500 m; no white edge along the road.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

Built (round 18): 28 px label, bracket 0.44 x the ring thick, ends just above the ring and the road, no road edge.
- `device/08-built-r18-light.png`: build cac54db on our test Karoo, test ride 5.2 km up.

## Round 19: summit and empty states

Feedback: at the top the strip turns white behind the moving boxes ("looks very angry"): make it dark blue like the PR row, plus an easter egg, SUMMIT REACHED, that moves with the boxes and ends in the middle. And better empty states.
- Strip background navy, a 2 px white line on top. Right behind the last 100 m: a summit flag + SUMMIT REACHED (condensed, white), sliding with the boxes, resting in the middle of the strip on top.
- Near the top the bracket shows what is left (300 M, 100 M); on top no bracket. Its label stays inside the graph.
- Empty states (today: centred light-blue text on navy; "--" numbers): no route = climb icon, "No climbs yet", "Load a route with climbs" (one tile: "Load a route"); after the last climb = summit flag, "That was the last climb", 3/3. Faint ridge in the graph area, no dashes.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 20

Feedback: a rider found the TO TOP icons unclear; the latest type? 3 of 3 small; climbs done too close to the border; why a white line and a light-blue line; no 500 M bracket where there is no strip.
- TO TOP bar: A, the units in label capitals after each number (2.6 KM, 196 M UP), or B, in words (2.6 km to go · 196 m to climb). Round 19 icons for comparison. Device pages show A.
- No white line above the strip; no light-blue lines between the PR columns.
- The 500 M bracket only with the strip (biggest size).
- Empty states: hint 21 px (was 18); "climbs done" 14 px off the bottom (the Karoo values' margin).
- Numbers in the preview use the Karoo's Relative (build.py PREVIEW_OUT, never in the committed mockup.html; the app already uses it from the system).

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 21

Feedback: "3/3 climbs done is fine copy, maybe remove this title blue if there is no climb right now?" With no climb (no route, after the last climb) the half screen has no blue bar: the empty state fills the field. Hint after the last climb: "3/3 climbs done".

Decision: pending (TO TOP bar A or B still to pick).

## Round 22

Feedback: "Remove the up from the A units." TO TOP bar A: 2.6 KM   196 M. Bar A picked.

Decision: picked; build pending.

## Build preview (rounds 19–22 + Karoo type)

Feedback: "Mocks before please". At the top of the mock, every state as it will be built, light and dark: on the climb (our page and the biggest size), 100 m to the top, on top, before the climb, no route, after the last climb. The bar and ETA follow the position; before the climb the two tiles use the round 12 layout and the bar says 3.2 KM. Numbers in Relative in the preview.

## Round 23: borrowing the Karoo climb page tiles

Feedback (with a picture of ClimbER's DIST TO TOP / ELEV TO TOP tiles): can we take something from this? Biggest size, light and dark:
- A: the Karoo's distance and elevation icons in its mint green (#5BD69C) before the numbers in the TO TOP bar.
- B: the bar as the Karoo's pair of fields: mint icon, DIST TO TOP 2.6 | ELEV TO TOP 196 (TO TOP goes).
- C: B, plus STRAVA PR / VS PR / ETA TO TOP as the climb page's navy tiles (blue edge, icon + label, number).

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 24: the Karoo's own icons

Feedback: "Use Karoo native icons, but the big TO TOP is better, just need to include something more to the elevation left." The big TO TOP stays; before each number the Karoo's own glyph from its ride app (ic_distance, ic_elevation) in its field-icon green (#32E09A on navy), units after. A: 196 M; B: +196 M. The climb icon everywhere is now the Karoo's ClimbER mountain (ic_climber). Findings shared in design-assets/karoo-type/README.md (workspace 12a1d1c).

Decision: pending (A or B).

## Round 25: the Karoo's own PR icon and names

From the Hammerhead legends (Strava Live Segments) and the ride app: its Personal Record rosette (ic_pr, now KarooType.Glyph.PR, workspace f4f58af) instead of our medal / badge; its names: PR (was STRAVA PR), TIME TO PR (was VS PR; "time against PR"), TIME TO TOP (was ETA TO TOP; the Karoo says Time to Destination on a route). The Karoo already has Distance / Elevation to Top, from Base, Climb Number, Climb Graph, grade, VAM: Mountaineer adds the time to the top, the PR on the climb and the graph with the next 500 m. Open: in two tiles "+0:18 TIME TO PR" is long.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

Feedback: "Yes, Karoo's". The rosette and the names (PR, TIME TO PR, TIME TO TOP) are the defaults in the mock and the build preview. With the rosette, the PR symbol setting (medal / badge) goes. Still open: height label 196 M or +196 M (round 24).

## Round 26

Feedback: two tiles, "+0:18 TIME TO PR" too long: "maybe have vs PR". The small line under the time in two tiles says VS PR; elsewhere the Karoo's names stay (PR, TIME TO PR, TIME TO TOP).

## Round 27: corners by the shared tile rules

Feedback: "Are we abiding to the tile rules here in the corner?" Checked against KarooType / karoo-type README: right edges were fine (9 px; native values 8-10, labels 7-8). Bottoms were not: two tiles' VS PR line sat 7 px up, the half screen's PR row 11 px. Both now on the Karoo's value baseline, 14 px above the bottom (valueBaseline), like the 280 next to it. In two tiles the line no longer sits on the graph's bottom edge (round 12); the shared rule wins.

## Built (rounds 19–27, +196 M)

Feedback: "Build, +196 M". Built as the build preview: navy strip with SUMMIT REACHED, the bracket counting down near the top (only with the strip), empty states (no route, all done), no blue bar without a climb, TO TOP bar with the Karoo's distance/elevation glyphs in GLYPH_GREEN_NIGHT and units (+196 M), the ClimbER mountain and PR rosette (KarooType.Glyph), the Karoo's names (PR, TIME TO PR, TIME TO TOP; two tiles: VS PR under the time), values on the 14 px baseline, no dividers. The summit view holds 60 s or 200 m after the top. Settings: no PR symbol choice. Half screens 330 px+ get more headroom so the bracket's label stays inside.
- `device/09-built-r27-editor-light.png`: build d23686e on our test Karoo, in the page editor (the preview's still example climb). After the reinstall the ride screen kept the old build's picture and started no views (the Karoo's known stale link; a restart clears it). The second Karoo was not connected, so not installed there.

## Round 28: empty states by the shared rule

Shared rule (via PartnerETA, workspace 8b8d944): empty states use the label font at 25 px caps, centred, ink bottom 39 px above the tile bottom, shrunk to fit (KarooType.emptyPaint / emptyBaseline). Options, no route and after the last climb, light and dark:
- A: one tile and two tiles as the Karoo's own empty field (header stays, "No Route" / "3/3 Climbs Done" in place of the value); the half screen keeps the navy art, words by the rule.
- B: the navy art on every size, words by the rule.
Round 21 (built) shown for comparison. Note: the 39 px is measured on 167 px tiles only.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

Feedback: "A". Round 28 A is the default in the build preview. Build pending.

## Round 29

Feedback: the half screen's navy "No Route" box "should be consistent as well". Every size is now the Karoo's own empty field: header (ClimbER mountain + TIME TO TOP), the empty text by the shared rule (label font, 25 px caps, centred, 39 px above the bottom); no navy box. After the last climb: All 3 Climbs Done (one tile: 3/3 Climbs Done). Note: 39 px is measured on 167 px tiles; the half screen still needs a check against a native empty tile.

## Round 30: say it's Mountaineer, and what to do

Feedback: a small text saying this is Mountaineer, and "Add a route with climbs" or similar. A: header MOUNTAINEER (ClimbER mountain), No Route by the shared rule, a muted hint under it on the 14 px baseline ("Load a route with climbs"; one tile "Load a route"; after the last climb "That was the last climb on this route"). B: header stays TIME TO TOP, a small grey "Mountaineer" above No Route, same hint.

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Round 31

Feedback: "Center it". A (MOUNTAINEER header) with "No Route" and its hint centred together in the space under the header, at every size; this moves the words off the shared rule's 39 px-from-bottom spot (worth telling the other sessions if we keep it).

Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.

## Fix after v0.1.0: the stretched last field

The Karoo stretches a page's last field to fill the page but reports its set grid size in
ActiveRidePage (reported (60, 20), view (60, 25); (60, 21) vs (60, 45)), so the exact grid-size
match left those half tiles blank. Now: same width, view at least as tall as reported.
- `device/10-stretched-fix-page-light.png`: the Mountaineer page, the half tile at the bottom drawn.
- `device/11-stretched-fix-half-light.png`: the stretched half tile on the other page.

## Round 32: NEXT CLIMB IN

Feedback: "Next climb" should say "Next climb in", so it's clear the number is the distance to the
climb. Mock from the real code (field gallery): one tile, two tiles, half screen, light and dark, in
`round32-next-climb-in/`. Fits at every size. Decision: OK, built.
- `device/12-built-r32-next-climb-in-light.png`: build 7171300 on our test Karoo, the Mountaineer page before Sa Calobra.

## Round 33: the road up to the climb

Feedback: before the climb, show the elevation up to it (the route has it), but the whole climb
stays the focus, and it must be clear where the road ends and the climb starts. In `mockup.html`
(Round 33), every size, light and dark: the climb keeps 80 % of the graph in its grade colours; the
road to it is squeezed into the first fifth with its real ups and downs, you at the left, a dashed
white line (START on the bigger sizes) where the climb begins.
- A: the road as a muted blue silhouette.
- B: the road as a light blue line, no fill.
Decision: A (silhouette), built. Gallery pictures from the code in `round33-road-to-climb/`.
- `device/13-built-r33-road-light.png`: build 1ffba96 on our test Karoo, 1.5 km before Sa Calobra (two tiles, one tile, nearly half).

## Round 34: START like the 500 M label

Feedback: START should have the same typography as the 500 M label. Now: the bracket label's type
(Plex Condensed, 28 px, white), centred over the line; the line as thick as the bracket, still
dashed so it isn't read as your position line. One tile: line only. Pictures from the code in
`round34-start-label/` (with the 500 M field for comparison). Decision: built, placement changed in rounds 36–37.

## Round 35: START on every size, the dot clear of the corner

Feedback: "even the smallest one can show start" and "should we move the dot up a bit?" (the
dot on the flat road was cut by the tile's rounded corner). One tile and two tiles: START in the
same type, scaled to the graph (14–20 px, smaller still if the road's space is narrow), left of
the line's top, over the clear navy above the road (right of it, it ran into the climb's colours
and the climb name). The dot stays on the road; the graph's floor lifts just enough for it to
clear the corner (ring + 7 px). Pictures from the code in `round35-start-small/`. Decision: built.

## Round 36: START beside the line

Feedback: START left or right of the dashed line, more like the 500 M label. Bigger sizes: 28 px
in the bracket label's type, caps level with the top of the line. A: right of the line (into the
climb). B: left of it (over the road, like the small sizes). Pictures from the code in
`round36-start-side/`. Decision: A (right).

## Round 37: START right of the line, never touching the climb

Feedback: "Right looks good, but needs to never touch the graph." START (28 px, the 500 M type)
right of the dashed line's top; the label and the line's top rise until START clears the climb's
highest point under it by 8 px. If that would reach the climb's name (a very steep start), START
goes left of the line, over the flat road. One tile and two tiles: unchanged (left, scaled).
Pictures from the code in `round37-start-clear/`. Decision: OK, built.
- `device/14-built-r37-start-light.png`, `device/15-built-r37-start-half-light.png`: build 3412d66 on our test Karoo, 1.5 km before Sa Calobra.

## Round 38: header at the Karoo's size, graph to the tile edges, ▶ at the start

Feedback: the one-tile header has more space above than the Karoo's tiles; a ▶ next to the start
line in the smallest tile instead of START; the graph could run to the tile's edges, like the half
screen. Measured on the test Karoo: NEXT CLIMB IN was shrunk to fit one line (20 px caps one tile,
19 px two tiles; the Karoo: 23). The Karoo never shrinks a label: it wraps onto two lines (native
LAP AVG / POWER: baselines 14 px above and 15 px below the one-line baseline, icons stay).
Options (all at full size, graph to the edges, ▶ on small graphs): A wrap like the Karoo (NEXT
CLIMB / IN), B balanced (NEXT / CLIMB IN), C one line "CLIMB IN". On short full-width rows both
lines move down until the first clears the top. Pictures from the code in `round38-header-edge/`
(today vs A/B/C, on the climb today vs edges). Decision: A (wrap like the Karoo), graph to the edges, ▶; built.
- `device/16-built-r38-header-light.png`: build 87f4725 on our test Karoo: NEXT CLIMB / IN at 23 px caps (baselines 14 px up, 15 px down, measured), graphs to the tile edges, ▶ on the one-tile start line.

## Round 39: STARTS IN

Feedback: "Starts in" instead of "Next climb in": shorter, and it says what the number is. At
every size (one tile, two tiles, the half screen's blue bar); it fits on one line at the Karoo's
full size, so the two-line wrap (round 38) isn't needed for it. Pictures from the code in
`round39-starts-in/`. Decision: OK, built.

## Round 40: the blue bar's numbers and units

Feedback: should the bar's typography (1.2 KM; 4.2 KM +318 M) match the label and the other data?
Today the bar writes units in light-blue capitals while the row under it and the tiles write them
lowercase in the number's colour (9.4 km, 667 m). B: units like the rest (lowercase, white), number
unchanged. C: the whole value in the label type (condensed capitals, white), like STARTS IN / TO TOP.
Pictures from the code in `round40-bar-type/`. Decision: C (label type), built.

## Round 41: the climb icon at the Karoo's icon height

Feedback: "still not aligned" (STARTS IN next to LAP TIME). Measured on the test Karoo: the labels
match to the pixel (caps 326–348, both); the icons end on the same baseline, but our ClimbER mountain
is wide and flat, so fitted into the 25 px icon box it is 16 px tall against the Karoo's 24 px icons
(CADENCE 324–348, ours 332–348). Tall: the mountain at 24 px (0.96 × box), wider than the box (36 px),
bottom on the baseline. The gallery's stand-in icons were also 9 px high (fixed in the gallery by
Karoo ALL). Pictures from the code in `round41-icon-height/`. Decision: tall, built.
- `device/17-built-r41-icon-light.png`: build 54a4b46 on our test Karoo: STARTS IN, the bar in the label type, the mountain 24 px tall (325–348, CADENCE 324–348; labels 326–348 both).

## Round 42: one tile without the navy box

Feedback: in the smallest tile, remove the graph's navy background. Mock: the graph straight on
the tile (white / black), the climb in its grade colours, the road before it muted blue, the
climbed part keeps its navy hatch; your ring, the start line and the ▶ in the text colour, the
ring's hole in the tile's. Pictures from the code in `round42-bare-graph/` (today vs no box).
Decision: "Looks great", built.

## Round 43: TIME VS PR

Feedback: "it should be Time VS PR" (was the Karoo's own name, TIME TO PR). Everywhere: one tile,
two tiles, the half screen's PR row, Settings (Time vs PR). Then: "In the smallest tiles, have it just be
VS PR": one tile says VS PR (one line next to the mountain and the PR rosette). Pictures from the
code in `round43-time-vs-pr/`. Decision: built.

## Round 44: a space after the sign

Feedback: a space between − / + and the numbers. The gap to the PR (+ 0:18, - 0:24) and the
height in the blue bar (+ 318 M). In Relative (monospaced) a normal space is a digit wide, so
"Narrower space": a thin space (U+2009, from the fallback font) after the sign; the bar's label
type keeps its own space. Pictures from the code in `round44-sign-space/`. Decision: "Perfect", built.
- `device/18-built-r44-vs-pr-light.png`: build 5652087 on our test Karoo, 5.2 km up Sa Calobra: TIME VS PR "+ 0:04" with the thin space, "+ 319 M" in the bar.

## Round 45: the small start line as thick as the others

Feedback: on the smallest tile the dotted start line should be as thick as on the others. Small
graphs now draw it 3 px with 8/6 px dashes (was 1.5 px, 3/3), like two tiles (0.44 × the 7 px
ring); the ▶ stays. Pictures from the code in `round45-start-line/`. Decision: replaced by round 46.

## Round 46: the start marker as a flag on the road

Feedback on round 45: "looks odd" (the thick dashed line broke into a few chunky dashes and ran
down through the road). A: a solid pole standing on the road, the ▶ as its flag. B: the same pole
dashed (5/3 px). Today's for comparison. Pictures from the code in `round46-start-pole/`.
Decision: A (solid pole), built.
- `device/19-built-r46-flag-light.png`: build 6d83ee5 on our test Karoo, 1.5 km before Sa Calobra: one tile without the navy box, the start as a ▶ on a solid pole.

## Round 47: Time to top settings (bike weight, wind)

Feedback: time to top needs to be fairly accurate; use the wind data we have, and ask for the bike
weight. A new Settings section "Time to top" above Strava. Bike weight: "use a base for standard 8kg, but
allow to enter a custom one", "or fetch from profile, but then we ask what that profile's bike
weight is": a choice One for all (default; standard 8.0 kg in grey, type your own) / Per ride
profile (each profile picked on the Karoo gets a row; empty rows use the one-for-all weight; the
Karoo reports the active profile, not the list, so profiles appear once used). Under 6.8 kg (UCI
minimum) a feather note: "Oh nice, a weight weenie build!". Fields like PartnerETA's input. Wind
on by default (Open-Meteo per part of the climb, no wind without a connection). Improve over time
(off by default): logs each climb (speed against power, gradient, wind) on the Karoo and carries
what it learned into the next climb; a lock and "The data stays on your Karoo and never leaves it."
In `mockup.html` (Round 47), light only. Built; emulator shots of the real screen in `round47-settings/`.
Decision: built (one for all / per profile, weight weenie, wind, improve over time).
