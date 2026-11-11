# One graph system, one set of words (round 52)

Found on the Mallorca 312 desk test (2026-11-10, `00-before-mallorca312-light.png`): on a 312 km route
with 30 climbs the whole-route view made climbs slivers (one tile) and stacked 30 numbers (half), the
two tiles showed a different stretch than the other sizes, and STARTS IN didn't say what starts.

## What the graph shows: decided by where you are, the same at every size

| Where you are | The stretch of road |
| --- | --- |
| More than 1 km before the next climb | From you to the top of the next climb |
| Within 1 km of a climb shorter than 2 km, and on it | The 2 km close-up: the road up to it and the climb |
| Within 1 km of a longer climb | From you to its top |
| On a climb of 2 km or more | The whole climb |
| The summit minute | The climb just done (as on it) |

Always: your dot near the left edge (5 %), the start flag where the climb starts, the graph ends at the
summit with the checkered flag. No climb numbers on the graph (the name line says "Climb 3 of 30").

## How it scales to a tile

The stretch is fitted to the graph's width, its height to the stretch's own rise (at least 30 m, so flat
road stays flat). Only the graph's space differs by size: one tile the strip under the number, two
tiles the left 278 px, half screen the middle (plus the next 500 m boxes on a climb). Marks (dot, flags)
scale with the graph's height. In a shortened map row only the graph gives way.

## The words, at every size

| Moment | One tile | Two tiles | Half screen bar |
| --- | --- | --- | --- |
| Before a climb | CLIMB IN 22.9 km | CLIMB IN 22.9 km · 18.5 km · 3.2% | CLIMB IN … 22.9 KM |
| On a climb | TIME TO TOP (or DIST TO TOP / VS PR) | the same + its line | TO TOP 0.31 KM + 14 M |
| Summit minute | SUMMIT 43:49 | SUMMIT 43:49 | SUMMIT |

Pictures: `far` (route start, 22.9 km before an 18.5 km climb), `far2` (9 km before a 4.5 km climb),
`near` / `short` (a 510 m climb), `eta` (on Sa Calobra), `summit`, each `-<size>-<light|dark>.png`.

Decision: pending.
