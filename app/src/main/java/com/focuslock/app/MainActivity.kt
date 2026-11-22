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
    private var mode = -1            // 0 = setup needed, 1 = idle, 2 = locked
    private var sig = ""
    private var exitAt = 0L
    private var minutesBox: EditText? = null
    private var timerView: TextView? = null
    private var diagView: TextView? = null
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

    private fun overlayOk(): Boolean = Settings.canDrawOverlays(this)

    private fun render() {
        val active = Session.isActive(this)
        val svc = serviceEnabled()
        val ovl = overlayOk()
        val newMode = if (active) 2 else if (svc && ovl) 1 else 0
        val newSig = "$newMode-$svc-$ovl"
        if (newSig != sig) {
            sig = newSig
            mode = newMode
            build(svc, ovl)
        }
        if (mode == 2) updateLocked()
    }

    private fun rebuild() {
        sig = ""
        render()
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
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
        }
    }

    private fun build(svc: Boolean, ovl: Boolean) {
        root.removeAllViews()
        minutesBox = null; timerView = null; diagView = null; exitBtn = null
        root.addView(tv("Focus Lock", 28f))
        when (mode) {
            0 -> {
                root.addView(tv("One-time setup"))
                root.addView(tv((if (svc) "✔ " else "✘ ") + "Accessibility service"))
                root.addView(tv((if (ovl) "✔ " else "✘ ") + "Display over other apps"))
                if (!svc) {
                    root.addView(btn("Turn on accessibility service") {
                        open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    })
                    root.addView(tv("If the switch is greyed out: App info, three dots at the top right, Allow restricted settings.", 14f))
                    root.addView(btn("Open App info") {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    })
                }
                if (!ovl) {
                    root.addView(btn("Allow display over other apps") {
                        open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    })
                }
                root.addView(tv("Also recommended so the phone never closes Focus Lock: App info > Battery > Unrestricted, allow Autostart, and lock the app in the Recents screen.", 14f))
                root.addView(btn("Open battery settings") {
                    open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
                    rebuild()
                })
                root.addView(tv(" "))
                root.addView(btn("Open battery settings") {
                    open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
                diagView = tv("", 12f)
                diagView?.setTextColor(Color.GRAY)
                root.addView(diagView)
            }
        }
    }

    private fun updateLocked() {
        val s = Session.remainingMs(this) / 1000
        timerView?.text = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)

        val age = Session.lastAgeSec(this)
        val ageText = if (age < 0) "never" else "${age}s ago"
        diagView?.text = "Service last saw: ${Session.lastPkg(this)} ($ageText)\n" +
            "Blocks so far: ${Session.bounces(this)}   Overlay permission: ${if (overlayOk()) "yes" else "no"}"

        if (exitAt > 0L) {
            val left = (exitAt - System.currentTimeMillis()) / 1000
            if (left <= 0L) {
                exitAt = 0L
                Session.stop(this)
                rebuild()
                return
            }
            exitBtn?.text = "Exiting in ${left}s - tap to cancel"
        } else {
            exitBtn?.text = "Emergency exit (${Session.EXIT_DELAY_SEC}s wait)"
        }
    }
}
