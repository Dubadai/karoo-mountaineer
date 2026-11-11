# Settings on the shared settings kit

Mountaineer's Settings page built from the shared settings kit (`SettingsKit.kt`, from karoo-starter;
its design and rules live in the karoo-starter repo, `docs/design/settings-kit/`):

- every heading has its own icon (one tile, two tiles, half screen, Time to top, Strava);
- the half screen's four lines are switch rows with icons (climb, PR, time vs PR, time to top);
- Wind and Improve over time are switch rows; the privacy line and the Strava note are info rows;
- the page ends with the shared rows: Update notices, then the app's name, version and GitHub repo.

Pictures (Settings pages are light only):

- `settings-light.png`: three screens in the Karoo 3 on the Sky background (top, Time to top, end).
- `01-top-light.png`, `02-time-to-top-light.png`, `03-end-light.png`: the same screens from the
  Android 12L emulator (its own status bar, not the Karoo's).
- `04-on-the-karoo-dark.png`: on our test Karoo (in dark mode there), the half-screen switch rows (kit update 3).
- Kit update 4: bike weight as the kit's number row, the light-bike note as its OK note, one text size per control, the three-line version row.
