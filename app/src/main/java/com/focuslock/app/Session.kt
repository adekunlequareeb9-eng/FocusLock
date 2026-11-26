package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

object Session {
    private const val PREFS = "focuslock"
    private const val KEY_END = "end_at"
    private const val KEY_TOPIC = "topic"
    private const val KEY_DEADLINE = "commit_deadline"
    private const val KEY_LAST_PKG = "last_pkg"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_BOUNCES = "bounces"
    private const val KEY_LAST_ELAPSED = "last_elapsed"   // last seen SystemClock.elapsedRealtime (drops after a reboot)
    private const val KEY_AUTO_AT = "auto_at_elapsed"      // elapsedRealtime when the auto lock is due, 0 = none
    private const val KEY_AUTO_DELAY = "auto_delay_min"
    private const val KEY_AUTO_LEN = "auto_len_min"

    // ---- Tune these ----
    const val MAX_MINUTES = 240            // hard cap on the starting duration
    const val EXIT_DELAY_SEC = 900         // emergency exit wait (15 min)
    const val COMMIT_WINDOW_SEC = 120      // time allowed to pick a study topic
    const val NO_TOPIC_PENALTY_MIN = 10    // added if no topic is picked in time
    const val QUIZ_FAIL_PENALTY_MIN = 10   // added if the quiz is failed
    const val QUIZ_QUESTIONS = 5           // questions per quiz (needs 80% to pass)

    // Auto lock after every boot
    const val AUTO_DELAY_DEFAULT_MIN = 60  // default: 1 hour after the phone is turned on
    const val AUTO_DELAY_MAX_MIN = 180     // starts at most 3 hours after the phone is turned on
    const val AUTO_LEN_MIN_MIN = 180       // stays locked at least 3 hours (upper cap is MAX_MINUTES)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun endAt(c: Context): Long = prefs(c).getLong(KEY_END, 0L)

    // A session exists from Start until the quiz is passed (or the emergency exit finishes).
    fun isLocked(c: Context): Boolean = endAt(c) != 0L
    fun inTimePhase(c: Context): Boolean = endAt(c) > System.currentTimeMillis()
    fun remainingMs(c: Context): Long = (endAt(c) - System.currentTimeMillis()).coerceAtLeast(0L)

    // The end time is an absolute timestamp, so a restart cannot reset it.
    fun start(c: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MAX_MINUTES)
        val windowSec = minOf(COMMIT_WINDOW_SEC, m * 30)
        val now = System.currentTimeMillis()
        prefs(c).edit()
            .putLong(KEY_END, now + m * 60_000L)
            .putString(KEY_TOPIC, "")
            .putLong(KEY_DEADLINE, now + windowSec * 1000L)
            .putInt(KEY_BOUNCES, 0)
            .remove(KEY_LAST_PKG)
            .remove(KEY_LAST_AT)
            .commit()
    }

    fun finish(c: Context) {
        prefs(c).edit().remove(KEY_END).remove(KEY_TOPIC).remove(KEY_DEADLINE).commit()
    }

    fun failQuiz(c: Context) {
        prefs(c).edit()
            .putLong(KEY_END, System.currentTimeMillis() + QUIZ_FAIL_PENALTY_MIN * 60_000L)
            .commit()
    }

    fun topic(c: Context): String = prefs(c).getString(KEY_TOPIC, "") ?: ""

    fun setTopic(c: Context, t: String) {
        prefs(c).edit().putString(KEY_TOPIC, t).putLong(KEY_DEADLINE, 0L).commit()
    }

    // Seconds left to pick a topic, or -1 if no choice is pending.
    fun commitLeftSec(c: Context): Long {
        val p = prefs(c)
        val deadline = p.getLong(KEY_DEADLINE, 0L)
        val t = p.getString(KEY_TOPIC, "") ?: ""
        if (t.isNotBlank() || deadline == 0L) return -1L
        return ((deadline - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    // If the topic window ran out with no topic, add the penalty once. Returns true when applied.
    fun checkCommitPenalty(c: Context): Boolean {
        val p = prefs(c)
        val deadline = p.getLong(KEY_DEADLINE, 0L)
        val t = p.getString(KEY_TOPIC, "") ?: ""
        if (t.isNotBlank() || deadline == 0L || System.currentTimeMillis() <= deadline) return false
        val end = p.getLong(KEY_END, 0L)
        p.edit()
            .putLong(KEY_END, end + NO_TOPIC_PENALTY_MIN * 60_000L)
            .putLong(KEY_DEADLINE, 0L)
            .commit()
        return true
    }

    // ---------------------------------------------------------------- auto lock after boot

    fun autoDelayMin(c: Context): Int =
        prefs(c).getInt(KEY_AUTO_DELAY, AUTO_DELAY_DEFAULT_MIN).coerceIn(1, AUTO_DELAY_MAX_MIN)

    fun autoLenMin(c: Context): Int =
        prefs(c).getInt(KEY_AUTO_LEN, AUTO_LEN_MIN_MIN).coerceIn(AUTO_LEN_MIN_MIN, MAX_MINUTES)

    fun setAuto(c: Context, delayMin: Int, lenMin: Int) {
        prefs(c).edit()
            .putInt(KEY_AUTO_DELAY, delayMin.coerceIn(1, AUTO_DELAY_MAX_MIN))
            .putInt(KEY_AUTO_LEN, lenMin.coerceIn(AUTO_LEN_MIN_MIN, MAX_MINUTES))
            .commit()
    }

    // Seconds until the auto lock starts, or -1 if none is waiting.
    fun autoDueSec(c: Context): Long {
        val at = prefs(c).getLong(KEY_AUTO_AT, 0L)
        if (at == 0L) return -1L
        return ((at - SystemClock.elapsedRealtime()) / 1000L).coerceAtLeast(0L)
    }

    // Call often (the accessibility service does every few seconds, the screen every second).
    // elapsedRealtime only goes backwards when the phone was restarted, so a lower value than the
    // last one seen means a fresh boot: schedule the auto lock. When it is due, start the lock.
    // Returns true when a lock was just started.
    fun autoTick(c: Context): Boolean {
        val p = prefs(c)
        val now = SystemClock.elapsedRealtime()
        val last = p.getLong(KEY_LAST_ELAPSED, 0L)
        var at = p.getLong(KEY_AUTO_AT, 0L)
        val ed = p.edit().putLong(KEY_LAST_ELAPSED, now)
        var important = false
        if (last > 0L && now < last) {
            at = now + autoDelayMin(c) * 60_000L
            ed.putLong(KEY_AUTO_AT, at)
            important = true
        }
        var startNow = false
        if (at != 0L && now >= at) {
            ed.putLong(KEY_AUTO_AT, 0L)
            important = true
            // If a lock is already running, it simply continues.
            startNow = !isLocked(c)
        }
        if (important) ed.commit() else ed.apply()
        if (startNow) start(c, autoLenMin(c))
        return startNow
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
