package com.focuslock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private var mode = -1            // 0 = not device owner, 1 = idle, 2 = locked
    private var exitAt = 0L
    private var minutesBox: EditText? = null
    private var timerView: TextView? = null
    private var exitBtn: Button? = null

    private val tick = object : Runnable {
        override fun run() {
            render()
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }

    @Deprecated("Back is ignored while locked")
    override fun onBackPressed() {
        if (mode != 2) super.onBackPressed()
    }

    private fun inLockTask(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun render() {
        val owner = Locker.isOwner(this)
        val active = owner && Locker.isActive(this)

        // Session finished (or left over): leave lock mode and clean up.
        if (owner && !active && (Locker.endAt(this) != 0L || inLockTask())) {
            try { stopLockTask() } catch (_: Exception) {}
            Locker.release(this)
        }
        // Session running but not locked yet (first start, or after a reboot): lock now.
        if (active && !inLockTask()) {
            try {
                Locker.applyPolicy(this)
                startLockTask()
            } catch (e: Exception) {
                Toast.makeText(this, "Lock failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        val newMode = if (!owner) 0 else if (active) 2 else 1
        if (newMode != mode) {
            mode = newMode
            build()
        }
        if (mode == 2) updateLocked()
    }

    private fun tv(s: String, size: Float = 18f) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, 16, 0, 16)
    }

    private fun btn(s: String, f: () -> Unit) = Button(this).apply {
        text = s
        setOnClickListener { f() }
    }

    private fun open(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            Toast.makeText(this, "Can't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun build() {
        root.removeAllViews()
        minutesBox = null; timerView = null; exitBtn = null
        root.addView(tv("Focus Lock", 28f))
        when (mode) {
            0 -> root.addView(tv("Not set up yet. This app must be made device owner once (see SETUP.md), then reopen it."))
            1 -> {
                root.addView(tv("Minutes to lock (max ${Locker.MAX_MINUTES}):"))
                minutesBox = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText("2")
                    setTextColor(Color.WHITE)
                }
                root.addView(minutesBox)
                root.addView(btn("Start lock") {
                    val m = minutesBox?.text?.toString()?.toIntOrNull() ?: 2
                    exitAt = 0L
                    Locker.start(this, m)
                    mode = -1
                    render()
                })
                root.addView(tv(" "))
                root.addView(btn("Release device owner (before uninstalling)") {
                    AlertDialog.Builder(this)
                        .setMessage("This removes the lock powers from the app. Continue?")
                        .setPositiveButton("Release") { _, _ ->
                            try { Locker.releaseOwner(this) } catch (_: Exception) {}
                            mode = -1
                            render()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                })
            }
            2 -> {
                timerView = tv("", 48f)
                root.addView(timerView)
                root.addView(btn("Phone") { open(Locker.dialIntent()) })
                root.addView(btn("Messages") { open(Locker.smsIntent()) })
                root.addView(btn("Calendar") { open(Locker.calendarIntent()) })
                root.addView(btn("Clock") { open(Locker.clockIntent()) })
                root.addView(tv(" "))
                exitBtn = btn("Emergency exit (${Locker.EXIT_DELAY_SEC}s wait)") {
                    exitAt = if (exitAt == 0L) System.currentTimeMillis() + Locker.EXIT_DELAY_SEC * 1000L else 0L
                }
                root.addView(exitBtn)
            }
        }
    }

    private fun updateLocked() {
        val ms = Locker.remainingMs(this)
        val s = ms / 1000
        timerView?.text = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)

        if (exitAt > 0L) {
            val left = (exitAt - System.currentTimeMillis()) / 1000
            if (left <= 0L) {
                exitAt = 0L
                try { stopLockTask() } catch (_: Exception) {}
                Locker.release(this)
                mode = -1
                render()
                return
            }
            exitBtn?.text = "Exiting in ${left}s - tap to cancel"
        } else {
            exitBtn?.text = "Emergency exit (${Locker.EXIT_DELAY_SEC}s wait)"
        }
    }
}
