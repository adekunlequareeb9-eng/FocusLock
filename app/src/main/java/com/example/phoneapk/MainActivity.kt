package com.example.phoneapk

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// A tiny starter screen built in code (no layout files, no extra libraries),
// so there is as little as possible that can break the build.
// Replace everything in onCreate with your own app.
class MainActivity : Activity() {

    private var taps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "It works!"
            textSize = 30f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Built on GitHub, with no computer."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        val counter = TextView(this).apply {
            text = "Taps: 0"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }

        val button = Button(this).apply {
            text = "Tap me"
            setOnClickListener {
                taps++
                counter.text = "Taps: $taps"
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(subtitle)
            addView(counter)
            addView(button)
        }

        setContentView(root)
    }
}
