# Gradient blocks, not stripes (round 57)

Feedback (2026-11-10): "Should we show the gradient scale in a less harsh way when it's zoomed out? Just so
it's not so stripy?" — picked: blocks (not blended colours).

How the Karoo does it (ride app, read 2026-11-10): `ElevationAndGradeView` gets the climb's own elevation
points (`ClimbDetail.getElevation().decodeElevations()`) for both the profile and the colours, one block
per pair of points, nothing merged by zoom. It only ever draws a single climb, never a long road to one.

Ours: the gradient blocks grow with the zoom so none is narrower than 8 px: 100 m in a close-up, then
200 m, 500 m, 1 km … on the road to a long climb; each block the average grade over it, in the Karoo's
grade colours; a last bit under half a block joins the one before. `before-far2-*` are the stripes,
`far2-*` the blocks (9 km before a 4.5 km climb).

Decision (2026-11-10): "Yes, install it". Built.
