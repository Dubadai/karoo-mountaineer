# Off route in red (round 55)

Feedback (2026-11-10): "how do we handle off route … can we add that in as red parts in the elevation? So
red means off-route elevation?"

What the Karoo gives apps: an on/off-route flag (in the distance-to-destination stream); with
turn-by-turn, the way back to the route (`rejoinPolyline`, points without heights) and where it meets
the route (`rejoinDistance`); with breadcrumb navigation, no way back. No heights for the way back.

Rendered with the field's code (`offfar`: 1.8 km over a hill back to a long route, `offnear`: 600 m back
to the test route, 300 m before a short climb):

- The way back drawn in red in front of the rejoin point, your dot at its start; the route continues
  in blue from there; route climbs behind you aren't drawn under the red.
- Its heights: a ground-height service (Open-Meteo, as for the wind) for the way back's points, once
  per new way back; without a connection, a straight line from your altitude to the route's.
- DIST TO CLIMB counts the way back plus the route from where you rejoin.
- Two tiles and the half screen: the name line says OFF ROUTE in red.
- Breadcrumb navigation (no way back): OFF ROUTE and "--" (not drawn here).

Open: the red (#E5322D) is close to the steepest grade colour (#D60404); far from the next climb the
way back is only a sliver, and one tile has no words for it.

Decision (2026-11-10): "Yes, build it". Built (blue-tiles).
