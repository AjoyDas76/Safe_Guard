# SafeGuard v1.3 — Liquid Glass

A full visual redesign with light and dark themes, a floating glass navigation dock, and a smoother 120 Hz experience. Telemetry, alerts, maps, reports and the AI tools work exactly as before.

## What's new

### New "Liquid Glass" design
- Frosted-glass cards, soft shadows and rounded corners across every screen.
- **Light theme**: soft off-white page with white glass cards and a blue accent.
- **Dark theme**: near-black page with smoky glass cards and a brighter blue accent.
- Switch between light and dark at any time: one tap on the sun/moon button in the top bar, from the More sheet, or in Settings. The choice is remembered.
- Redesigned Splash screen that follows the selected theme.

### Floating navigation dock
- New bottom dock: **Dashboard · Live · Map · Alerts · More**, with a liquid pill that glides to the selected tab.
- The dock floats over the content with nothing solid behind it; lists scroll underneath and the last item always scrolls clear of the dock.
- Live alert counter badge on the Alerts tab.
- **More** opens a sheet with Worker Profiles, Safety Rules, Report, AI Analyze and Settings, plus the monitored worker status and a theme switch.
- Pressing **Back** on a screen opened from More returns you to the screen you came from.
- The old side drawer and sci-fi HUD decorations were removed for a cleaner look.

### Smoother scrolling (120 Hz)
- The app now requests the display's highest refresh rate on launch and when returning from the background.
- Card shadows and glows are drawn once and cached instead of being recomputed every frame.
- The "Ask SafeGuard" button uses a cached glow instead of a per-frame shadow.
- The dock indicator animates in the draw phase only, so it never recomposes the screen.
- For best results use a **release** build and set the phone's refresh rate to High/120 Hz (battery saver can force 60 Hz).

## Unchanged
- Sensor colours: Temperature red, Humidity cyan, Pressure purple.
- No battery information is shown anywhere in the UI.
- All data, alerts, geofencing, siren, AI analysis, PDF/Excel export and Firebase behaviour.

## Version
- `versionName` 1.3, `versionCode` 4.
