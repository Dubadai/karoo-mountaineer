# Whole route, close-up near a climb (round 48)

After the 2026-11-10 test ride, where a 510 m climb filled nearly the whole tile.

Proposal (`mockup.html`, built from `mockup.src.html` with `design-assets/tools/mock/build.py`):

- **Standard view: the whole route** with the Karoo's climbs coloured on it (by the grade of each 100 m),
  your dot, a flag on the next climb's start. Ridden road a quiet tint; each climb at least 3 px wide.
  Text as today (STARTS IN, the next climb's length and gradient); the half screen numbers the climbs.
- **Close-up from 1 km before a climb**, at least **2 km** wide, so a short climb looks short; a climb
  longer than 2 km fills it as today. Back to the whole route a minute after the top.
- **Smallest tiles (236 × 141):** A, the whole route, or B, from you to the top of the next climb.
- Long climbs (Sa Calobra) unchanged: `images/today-eta-*` are today's build from the gallery.

Text (round 48b, after the ride): the grey line under the number 18 px (was 21) and darker (#333;
night #D0D0D0); a number 2 px from its unit (was 4–6); a thin space in "510 m". Metres under 1 km for a
climb's length and what's left in the grey line; the big countdowns stay in km (0.80 km) like the Karoo.

To pick: the 1 km, the 2 km, and A or B for the smallest tiles.

Decision (2026-11-10): "Yes, build it": close-up from 1 km, at least 2 km wide, A (whole route on the
smallest tiles too). Built: `built/` has the real field from the gallery (test route: a 510 m climb at
2.5 km, Sa Calobra from 7 km): `next` (1.2 km before Sa Calobra, whole route), `near` (800 m before the
short climb, close-up), `short` (200 m up it).
