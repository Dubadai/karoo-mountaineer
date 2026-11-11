# Settings: what a setting does, under its heading

The settings rule from 2026-11-11: the text that says what a setting does goes right under its
heading (or switch row), before its choices, never under them. The kit's `settingsIntro`
(karoo-common 1.8.0) has no gap above and 12 dp below; `settingsSegmented` gets 14 dp under it.

Both mocks use the 1.8.0 kit, so only the order and the texts differ. Light only (settings).

| Screen | Before | After |
| --- | --- | --- |
| One tile, two tiles, half screen | `shots/01-top-before-light.png` | `shots/02-top-after-light.png` |
| Time to top, One for all | `shots/03-time-before-light.png` | `shots/04-time-after-light.png` |
| Time to top, Per ride profile | `shots/05-profiles-before-light.png` | `shots/06-profiles-after-light.png` |

What changes:
- One tile / Two tiles / Half screen: the help moves above the choices and starts with "Picks …".
- Bike weight: one intro above One for all / Per ride profile covers both modes; the help under
  the weight rows goes. "No ride profiles yet" stays where the list would be (it's the list's empty state).
- Wind and Improve over time: already right under their switch rows; they become `settingsIntro`
  (spacing) and start with what they do ("Adds …", "Learns …").
- Strava: an info row, unchanged.

Status: approved (2026-11-11, "Yes, as mocked"); built on branch `settings-intro` with karoo-common 1.8.2,
for the next release. One fix to the text after the OK: an empty ride profile uses the one-for-all weight
(grey), not always 8.0 kg (that's how the code works).
Source: `mockup.src.html` → `python3 design-assets/tools/mock/build.py <folder>`.
