package com.focuslock.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
    private var mode = -1            // 0 = service off, 1 = idle, 2 = locked
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

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, FocusService::class.java)
        val long = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(long, true) || it.equals(short, true) }
    }

    private fun render() {
        val active = Session.isActive(this)
        val newMode = if (active) 2 else if (serviceEnabled()) 1 else 0
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
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun build() {
        root.removeAllViews()
        minutesBox = null; timerView = null; exitBtn = null
        root.addView(tv("Focus Lock", 28f))
        when (mode) {
            0 -> {
                root.addView(tv("One-time setup: turn on the Focus Lock accessibility service. It only checks which app is on screen."))
                root.addView(btn("1. Open accessibility settings") {
                    open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                root.addView(tv("If the switch is greyed out, Android is blocking sideloaded apps. Open App info, tap the three dots at the top right, and choose Allow restricted settings. Then try again."))
                root.addView(btn("Open App info") {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                })
            }
            1 -> {
                root.addView(tv("Minutes to lock (max ${Session.MAX_MINUTES}):"))
                minutesBox = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText("2")
                    setTextColor(Color.WHITE)
                }
                root.addView(minutesBox)
                root.addView(btn("Start lock") {
                    val m = minutesBox?.text?.toString()?.toIntOrNull() ?: 2
                    exitAt = 0L
                    Session.start(this, m)
                    mode = -1
                    render()
                })
            }
            2 -> {
                timerView = tv("", 48f)
                root.addView(timerView)
                root.addView(btn("Phone") { open(Session.dialIntent()) })
                root.addView(btn("Messages") { open(Session.smsIntent()) })
                root.addView(btn("Calendar") { open(Session.calendarIntent()) })
                root.addView(btn("Clock") { open(Session.clockIntent()) })
                root.addView(tv(" "))
                exitBtn = btn("Emergency exit (${Session.EXIT_DELAY_SEC}s wait)") {
                    exitAt = if (exitAt == 0L) System.currentTimeMillis() + Session.EXIT_DELAY_SEC * 1000L else 0L
                }
                root.addView(exitBtn)
            }
        }
    }

    private fun updateLocked() {
        val s = Session.remainingMs(this) / 1000
        timerView?.text = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)

        if (exitAt > 0L) {
            val left = (exitAt - System.currentTimeMillis()) / 1000
            if (left <= 0L) {
                exitAt = 0L
                Session.stop(this)
                mode = -1
                render()
                return
            }
            exitBtn?.text = "Exiting in ${left}s - tap to cancel"
        } else {
            exitBtn?.text = "Emergency exit (${Session.EXIT_DELAY_SEC}s wait)"
        }
    }
}
