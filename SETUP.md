# Focus Lock build 2 (accessibility version)

No adb, no computer, no system-setting changes. Nothing here can break your phone: if the app or service stops, the lock simply stops.

## Install and enable
1. Install the APK from Releases (Latest build).
2. Open Focus Lock. Tap **Open accessibility settings**, find **Focus Lock** (under Downloaded apps / Installed services), and switch it on.
3. If the switch is greyed out: Settings > Apps > Focus Lock > three dots (top right) > **Allow restricted settings**, then try again.
4. Back in the app you should see the minutes box and **Start lock**.

## Test (leave the minutes at 2)
- Tap Start lock. Try Phone, Messages, Calendar, Clock: they should open.
- Press Home, or open any other app (even Settings): you should get thrown back to Focus Lock.
- Restart the phone mid-lock: the lock should still be running afterwards.
- Emergency exit: tap it, wait 60 seconds, and the lock ends.

## Safety
- Hard cap of 240 minutes per session. Keep tests at 2 minutes until you trust it.
- Settings and the app installer are blocked during a lock, so you can't switch the service off mid-session. That is the point; use the emergency exit if you truly must.
- To remove the app: when no lock is running, switch the service off in accessibility settings, then uninstall normally.

## Not in this build
Study topic picker, quiz and penalty. A slower, harder-to-abuse emergency exit. A delayed uninstall key.
