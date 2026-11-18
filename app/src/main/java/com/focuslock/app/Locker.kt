package com.focuslock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

object Locker {
    private const val PREFS = "focuslock"
    private const val KEY_END = "end_at"

    const val MAX_MINUTES = 240      // hard cap on any session
    const val EXIT_DELAY_SEC = 60    // emergency exit wait (test build; raise later)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)
    fun endAt(c: Context): Long = prefs(c).getLong(KEY_END, 0L)
    fun isActive(c: Context) = endAt(c) > System.currentTimeMillis()
    fun remainingMs(c: Context) = (endAt(c) - System.currentTimeMillis()).coerceAtLeast(0L)

    // The end time is stored as an absolute timestamp, so a reboot cannot reset it.
    fun start(c: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MAX_MINUTES)
        prefs(c).edit().putLong(KEY_END, System.currentTimeMillis() + m * 60_000L).commit()
    }

    fun dialIntent() = Intent(Intent.ACTION_DIAL)
    fun smsIntent(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
    fun calendarIntent(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
    fun clockIntent() = Intent(AlarmClock.ACTION_SHOW_ALARMS)

    private fun allowedPackages(c: Context): Set<String> {
        val pm = c.packageManager
        val out = mutableSetOf<String>()
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(AlarmClock.ACTION_SHOW_ALARMS)
        )
        for (p in probes) {
            pm.queryIntentActivities(p, 0).forEach { out.add(it.activityInfo.packageName) }
        }
        try {
            (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
                .defaultDialerPackage?.let { out.add(it) }
        } catch (_: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(c)?.let { out.add(it) } } catch (_: Exception) {}
        out.add("com.android.server.telecom")
        out.add("com.android.phone")
        out.remove("android")
        return out
    }

    fun applyPolicy(c: Context) {
        val d = dpm(c)
        val a = admin(c)
        val pkgs = (allowedPackages(c) + c.packageName).toTypedArray()
        d.setLockTaskPackages(a, pkgs)
        d.setLockTaskFeatures(
            a,
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
        )
        // Make this app the home screen so after a reboot the phone comes back to it.
        val f = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        d.addPersistentPreferredActivity(a, f, ComponentName(c, MainActivity::class.java))
        // Stop clock tampering to end the lock early.
        try { d.setGlobalSetting(a, Settings.Global.AUTO_TIME, "1") } catch (_: Exception) {}
        try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME) } catch (_: Exception) {}
    }

    fun release(c: Context) {
        val d = dpm(c)
        val a = admin(c)
        prefs(c).edit().remove(KEY_END).commit()
        try { d.clearPackagePersistentPreferredActivities(a, c.packageName) } catch (_: Exception) {}
        try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME) } catch (_: Exception) {}
        try { d.setLockTaskPackages(a, arrayOf()) } catch (_: Exception) {}
    }

    // Needed before the app can be uninstalled (once uninstall blocking is added later).
    fun releaseOwner(c: Context) {
        dpm(c).clearDeviceOwnerApp(c.packageName)
    }
}
