package com.focuslock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class FocusService : AccessibilityService() {

    private var allowed: Set<String> = emptySet()
    private var allowedAt = 0L
    private var lastBounce = 0L

    private val handler = Handler(Looper.getMainLooper())

    // Android starts this service by itself after every boot, so this loop is what notices a
    // fresh boot, counts down the delay and starts the auto lock.
    private val autoLoop = object : Runnable {
        override fun run() {
            try {
                if (Session.autoTick(this@FocusService)) bounce()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshAllowed()
        handler.removeCallbacks(autoLoop)
        handler.post(autoLoop)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoLoop)
        super.onDestroy()
    }

    private fun refreshAllowed() {
        allowed = Session.allowedPackages(this)
        allowedAt = SystemClock.elapsedRealtime()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        try {
            // Locked from Start until the quiz is passed.
            if (!Session.isLocked(this)) return
            val pkg = event.packageName?.toString() ?: return
            Session.noteEvent(this, pkg)
            val now = SystemClock.elapsedRealtime()
            if (now - allowedAt > 60_000L) refreshAllowed()
            if (Session.isAllowed(pkg, allowed)) return
            if (now - lastBounce < 600L) return
            lastBounce = now
            Session.noteBounce(this)
            bounce()
        } catch (_: Exception) {
            // Fail open: if anything goes wrong, never trap the phone.
        }
    }

    private fun bounce() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
