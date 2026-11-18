# Focus Lock - first test build

## Before the command
- Accounts: already removed.
- Settings > Users: no guest or extra users. Tecno phones: also remove XSpace / Second Space / Dual Apps if you have them (they count as another user and block the command).
- Settings > Developer options > turn on Wireless debugging. It needs Wi-Fi to be connected (a hotspot from another phone works).

## Run the command from Termux on the same phone
    pkg install android-tools
Open Wireless debugging > "Pair device with pairing code". Keep that popup visible (use split screen or a floating window, it closes if you leave).

    adb pair localhost:PAIRING_PORT
Enter the 6-digit code. Then use the IP and port shown on the main Wireless debugging screen (a different port):

    adb connect localhost:MAIN_PORT
    adb shell dpm set-device-owner com.focuslock.app/.AdminReceiver

You want: `Success: Device owner set to package ...`

## Test
Open the app. Leave it at 2 minutes and tap Start lock. Try Phone, Messages, Calendar, Clock. Try Home and Recents. Restart the phone mid-lock: it should return locked, with the timer still counting.

## If something goes wrong
- Emergency exit on the lock screen ends it after a 60 second wait.
- Test sessions are short on purpose. Don't raise the minutes until the escape works.

## Later (not in this build)
Block uninstall, safe mode, factory reset and USB debugging during locks. Study content, quiz and penalty. The uninstall key.
