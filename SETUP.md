# Focus Lock build 3

No adb, no computer, no system-setting changes. If the app or service stops, the lock simply stops.

## What changed in build 3
- New one-time permission: **Display over other apps**. It lets Focus Lock pull itself back to the front even after the phone has had it in the background for a while.
- Setup screen with ticks for both permissions, plus a battery shortcut.
- A small grey status line on the locked screen: which app the service last saw, how long ago, how many blocks, and whether the overlay permission is on.

## Install and set up
1. Install over the old build (same signing key keeps your settings).
2. Open Focus Lock. Tick both items on the setup screen:
   - Accessibility service on (if greyed out: App info > three dots > Allow restricted settings).
   - Allow display over other apps.
3. Keep Focus Lock alive: App info > Battery > **Unrestricted**; allow **Autostart** if your phone has it; open Recents and **lock** the Focus Lock card (long-press or the lock icon on the card).

## Test (5 minutes)
- Start a lock. Try Phone, Messages, Calendar, Clock.
- Press Home, open another app, wait 30 seconds, press Home again, open something else. It should always come back.
- If it ever lets you out, open Focus Lock and read the grey status line, or send me a photo of it.
  - "Service last saw" stuck on an old time = the phone killed the service.
  - Blocks goes up but the app doesn't come back = Android refused the pull-back.

## Safety
- Hard cap 240 minutes. Emergency exit ends a lock after a 60-second wait.
- To remove the app: with no lock running, switch the service off in accessibility settings, then uninstall.
