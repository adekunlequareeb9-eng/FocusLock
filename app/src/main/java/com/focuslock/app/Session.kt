package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

object Session {
    private const val PREFS = "focuslock"
    private const val KEY_END = "end_at"
    private const val KEY_LAST_PKG = "last_pkg"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_BOUNCES = "bounces"

    const val MAX_MINUTES = 240      // hard cap on any session
    const val EXIT_DELAY_SEC = 60    // emergency exit wait (test build; raise later)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun endAt(c: Context): Long = prefs(c).getLong(KEY_END, 0L)
    fun isActive(c: Context): Boolean = endAt(c) > System.currentTimeMillis()
    fun remainingMs(c: Context): Long = (endAt(c) - System.currentTimeMillis()).coerceAtLeast(0L)

    // The end time is an absolute timestamp, so a restart cannot reset it.
    fun start(c: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MAX_MINUTES)
        prefs(c).edit()
            .putLong(KEY_END, System.currentTimeMillis() + m * 60_000L)
            .putInt(KEY_BOUNCES, 0)
            .remove(KEY_LAST_PKG)
            .remove(KEY_LAST_AT)
            .commit()
    }

    fun stop(c: Context) {
        prefs(c).edit().remove(KEY_END).commit()
    }

    // Live diagnostics, shown on the locked screen.
    fun noteEvent(c: Context, pkg: String) {
        prefs(c).edit()
            .putString(KEY_LAST_PKG, pkg)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun noteBounce(c: Context) {
        val p = prefs(c)
        p.edit().putInt(KEY_BOUNCES, p.getInt(KEY_BOUNCES, 0) + 1).apply()
    }

    fun lastPkg(c: Context): String = prefs(c).getString(KEY_LAST_PKG, null) ?: "-"

    fun lastAgeSec(c: Context): Long {
        val t = prefs(c).getLong(KEY_LAST_AT, 0L)
        return if (t == 0L) -1L else (System.currentTimeMillis() - t) / 1000L
    }

    fun bounces(c: Context): Int = prefs(c).getInt(KEY_BOUNCES, 0)

    fun dialIntent(): Intent = Intent(Intent.ACTION_DIAL)
    fun smsIntent(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
    fun calendarIntent(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
    fun clockIntent(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)

    // Packages that stay usable during a lock: this app, system UI, calls, messages, calendar, clock, keyboards.
    fun allowedPackages(c: Context): Set<String> {
        val pm = c.packageManager
        val out = mutableSetOf(
            c.packageName,
            "android",
            "com.android.systemui",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.emergency"
        )
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(AlarmClock.ACTION_SHOW_ALARMS)
        )
        for (p in probes) {
            try {
                pm.queryIntentActivities(p, 0).forEach { out.add(it.activityInfo.packageName) }
            } catch (_: Exception) {}
        }
        try {
            (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
                .defaultDialerPackage?.let { out.add(it) }
        } catch (_: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(c)?.let { out.add(it) } } catch (_: Exception) {}
        try {
            (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.forEach { out.add(it.packageName) }
        } catch (_: Exception) {}
        return out
    }

    // Extra name checks so call screens and contacts never get blocked by mistake.
    fun isAllowed(pkg: String, allowed: Set<String>): Boolean {
        if (pkg in allowed) return true
        return pkg.contains("incallui") || pkg.contains("dialer") ||
            pkg.contains("telecom") || pkg.contains("contacts")
    }
}
