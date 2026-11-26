package com.focuslock.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout

    private var phase = -1            // 0 setup, 1 idle, 2 locked (timer), 3 quiz
    private var sig = ""
    private var exitAt = 0L
    private var notice: String? = null

    private var studyIdx = -1
    private var studyShowAnswer = false

    private var tbPage = -1           // current textbook PDF page, -1 = not reading
    private var tbBmp: Bitmap? = null

    private var quiz: List<Packs.Question> = emptyList()
    private var quizTopic = ""
    private var quizIsTextbook = false
    private var quizIdx = 0
    private var quizScore = 0
    private var quizAnswered = false
    private var lastChoice = -1

    private var minutesBox: EditText? = null
    private var timerView: TextView? = null
    private var commitView: TextView? = null
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
        if (phase < 2) super.onBackPressed()
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

    // ---------------------------------------------------------------- state

    private fun render() {
        val svc = serviceEnabled()
        val ovl = overlayOk()
        val newPhase = when {
            Session.isLocked(this) -> if (Session.inTimePhase(this)) 2 else 3
            svc && ovl -> 1
            else -> 0
        }

        if (newPhase == 2 && Session.checkCommitPenalty(this)) {
            notice = "No topic chosen in time: +${Session.NO_TOPIC_PENALTY_MIN} minutes added."
        }
        if (newPhase != 2) {
            studyIdx = -1
            studyShowAnswer = false
            tbPage = -1
        }
        if (newPhase == 3 && quiz.isEmpty()) {
            prepareQuiz()
            if (!Session.isLocked(this)) {
                sig = ""
                render()
                return
            }
        }
        if (newPhase != 3 && quiz.isNotEmpty()) resetQuiz()

        val newSig = "$newPhase|$svc|$ovl|${Session.topic(this).isBlank()}|$studyIdx|" +
            "$studyShowAnswer|$tbPage|$quizIdx|$quizAnswered|${notice ?: ""}"
        if (newSig != sig) {
            sig = newSig
            phase = newPhase
            build(svc, ovl)
        }
        tickViews()
    }

    private fun rebuild() {
        sig = ""
        render()
    }

    private fun tickViews() {
        if (phase == 2) {
            val s = Session.remainingMs(this) / 1000
            timerView?.text = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
            val left = Session.commitLeftSec(this)
            commitView?.text = if (left >= 0) {
                "Pick what you'll study. ${left}s left, or +${Session.NO_TOPIC_PENALTY_MIN} min is added."
            } else {
                "No topic chosen: a random topic will be used for the quiz."
            }
        }
        if (phase == 2 || phase == 3) {
            val age = Session.lastAgeSec(this)
            val ageText = if (age < 0) "never" else "${age}s ago"
            diagView?.text = "Service last saw: ${Session.lastPkg(this)} ($ageText)\n" +
                "Blocks so far: ${Session.bounces(this)}   Overlay permission: ${if (overlayOk()) "yes" else "no"}"
            updateExit()
        }
    }

    private fun updateExit() {
        if (exitAt > 0L) {
            val left = (exitAt - System.currentTimeMillis()) / 1000
            if (left <= 0L) {
                exitAt = 0L
                Session.finish(this)
                resetQuiz()
                studyIdx = -1
                tbPage = -1
                notice = "Emergency exit used."
                rebuild()
                return
            }
            exitBtn?.text = "Exiting in ${left}s - tap to cancel"
        } else {
            exitBtn?.text = "Emergency exit (${Session.EXIT_DELAY_SEC / 60} min wait)"
        }
    }

    // ---------------------------------------------------------------- quiz

    private fun resetQuiz() {
        quiz = emptyList()
        quizTopic = ""
        quizIsTextbook = false
        quizIdx = 0
        quizScore = 0
        quizAnswered = false
        lastChoice = -1
    }

    private fun prepareQuiz() {
        val packs = Packs.load(this)
        val wanted = Session.topic(this)
        val isTb = wanted.startsWith(Textbook.PREFIX)
        var pack = if (isTb) {
            packs.firstOrNull { it.name == Textbook.QUIZ_PACK }
        } else {
            packs.firstOrNull { it.name == wanted }
        }
        if (pack == null && packs.isNotEmpty()) {
            pack = packs.random()
            if (!isTb) Session.setTopic(this, pack.name)
        }
        if (pack == null || pack.cards.isEmpty()) {
            Session.finish(this)
            notice = "No study cards found, so there is nothing to ask. Lock released."
            return
        }
        quizTopic = pack.name
        quizIsTextbook = isTb
        quiz = Packs.makeQuiz(pack, Session.QUIZ_QUESTIONS)
        quizIdx = 0
        quizScore = 0
        quizAnswered = false
        lastChoice = -1
    }

    private fun onAnswer(i: Int) {
        if (quizAnswered || quizIdx >= quiz.size) return
        lastChoice = i
        quizAnswered = true
        if (i == quiz[quizIdx].correct) quizScore++
        rebuild()
    }

    private fun nextQuestion() {
        if (quizIdx + 1 < quiz.size) {
            quizIdx++
            quizAnswered = false
            lastChoice = -1
            rebuild()
        } else {
            finishQuiz()
        }
    }

    private fun finishQuiz() {
        val total = quiz.size
        val need = (total * 4 + 4) / 5      // 80%, rounded up
        val score = quizScore
        val wasTextbook = quizIsTextbook
        resetQuiz()
        if (score >= need) {
            Session.finish(this)
            notice = "Quiz passed ($score/$total). You're free."
            if (wasTextbook) {
                Textbook.advance(this)
                notice = notice + " Next textbook lesson is ready."
            }
        } else {
            Session.failQuiz(this)
            notice = "Quiz failed ($score/$total, needed $need). +${Session.QUIZ_FAIL_PENALTY_MIN} minutes. Study the cards and try again."
        }
        studyIdx = -1
        tbPage = -1
        rebuild()
    }

    // ---------------------------------------------------------------- views

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

    private fun addExit() {
        root.addView(tv(" ", 8f))
        exitBtn = btn("Emergency exit") {
            exitAt = if (exitAt == 0L) System.currentTimeMillis() + Session.EXIT_DELAY_SEC * 1000L else 0L
        }
        root.addView(exitBtn)
    }

    private fun addDiag() {
        diagView = tv("", 12f)
        diagView?.setTextColor(Color.GRAY)
        root.addView(diagView)
    }

    private fun build(svc: Boolean, ovl: Boolean) {
        root.removeAllViews()
        tbBmp?.recycle()
        tbBmp = null
        minutesBox = null; timerView = null; commitView = null; diagView = null; exitBtn = null
        root.addView(tv("Focus Lock", 28f))
        when (phase) {
            0 -> buildSetup(svc, ovl)
            1 -> buildIdle()
            2 -> buildLocked()
            3 -> buildQuiz()
        }
    }

    private fun buildSetup(svc: Boolean, ovl: Boolean) {
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

    private fun buildIdle() {
        notice?.let { root.addView(tv(it, 16f)) }
        root.addView(tv("Minutes to lock (max ${Session.MAX_MINUTES}):"))
        minutesBox = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("5")
            setTextColor(Color.WHITE)
        }
        root.addView(minutesBox)
        root.addView(tv(
            "After you start, pick a study topic within ${Session.COMMIT_WINDOW_SEC / 60} minutes or " +
                "+${Session.NO_TOPIC_PENALTY_MIN} minutes is added. When the timer ends, you must pass a quiz to unlock. " +
                "Fail it and +${Session.QUIZ_FAIL_PENALTY_MIN} minutes is added.", 14f
        ))
        root.addView(btn("Start lock") {
            val m = minutesBox?.text?.toString()?.toIntOrNull() ?: 5
            exitAt = 0L
            notice = null
            studyIdx = -1
            tbPage = -1
            resetQuiz()
            Session.start(this, m)
            rebuild()
        })
        root.addView(tv(" "))
        root.addView(btn("Open battery settings") {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
    }

    private fun buildLocked() {
        timerView = tv("", 40f)
        root.addView(timerView)
        notice?.let { root.addView(tv(it, 15f)) }

        val topic = Session.topic(this)
        val packs = Packs.load(this)
        val pack = packs.firstOrNull { it.name == topic }
        val lesson = Textbook.current(this)

        if (tbPage >= 0 && lesson != null && Textbook.open(this, lesson.file)) {
            val total = lesson.last - lesson.first + 1
            root.addView(tv("${lesson.title}   page ${tbPage - lesson.first + 1}/$total", 14f))
            val bmp = Textbook.render(tbPage, resources.displayMetrics.widthPixels - 96)
            if (bmp != null) {
                tbBmp = bmp
                root.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                })
            } else {
                root.addView(tv("Can't show this page.", 16f))
            }
            if (tbPage < lesson.last) {
                root.addView(btn("Next page") {
                    tbPage++
                    rebuild()
                })
            }
            if (tbPage > lesson.first) {
                root.addView(btn("Previous page") {
                    tbPage--
                    rebuild()
                })
            }
            root.addView(btn("Done reading") {
                tbPage = -1
                rebuild()
            })
        } else if (studyIdx >= 0 && pack != null && pack.cards.isNotEmpty()) {
            val i = studyIdx % pack.cards.size
            val card = pack.cards[i]
            root.addView(tv("${pack.name}   ${i + 1}/${pack.cards.size}", 14f))
            root.addView(tv(card.q, 22f))
            if (studyShowAnswer) {
                root.addView(tv(card.a, 22f))
            } else {
                root.addView(btn("Show answer") {
                    studyShowAnswer = true
                    rebuild()
                })
            }
            root.addView(btn("Next card") {
                studyIdx = (i + 1) % pack.cards.size
                studyShowAnswer = false
                rebuild()
            })
            root.addView(btn("Back") {
                studyIdx = -1
                studyShowAnswer = false
                rebuild()
            })
        } else {
            studyIdx = -1
            tbPage = -1
            if (topic.isBlank()) {
                commitView = tv("", 16f)
                root.addView(commitView)
                if (lesson != null) {
                    root.addView(btn("Study next textbook lesson: ${lesson.title}") {
                        Session.setTopic(this, Textbook.PREFIX + lesson.title)
                        tbPage = lesson.first
                        rebuild()
                    })
                }
                for (p in packs) {
                    root.addView(btn("Study: ${p.name}") {
                        Session.setTopic(this, p.name)
                        rebuild()
                    })
                }
            } else if (topic.startsWith(Textbook.PREFIX) && lesson != null) {
                root.addView(tv("Studying: ${lesson.title}. A quiz unlocks the phone when the timer ends.", 16f))
                root.addView(btn("Open lesson pages") {
                    tbPage = lesson.first
                    rebuild()
                })
            } else {
                root.addView(tv("Studying: $topic. A quiz unlocks the phone when the timer ends.", 16f))
                root.addView(btn("Open study cards") {
                    studyIdx = 0
                    studyShowAnswer = false
                    rebuild()
                })
            }
            root.addView(tv(" ", 8f))
            root.addView(btn("Phone") { open(Session.dialIntent()) })
            root.addView(btn("Messages") { open(Session.smsIntent()) })
            root.addView(btn("Calendar") { open(Session.calendarIntent()) })
            root.addView(btn("Clock") { open(Session.clockIntent()) })
        }
        addExit()
        addDiag()
    }

    private fun buildQuiz() {
        root.addView(tv("Time's up. Pass the quiz to unlock your phone.", 16f))
        notice?.let { root.addView(tv(it, 15f)) }
        if (quiz.isNotEmpty() && quizIdx < quiz.size) {
            val qn = quiz[quizIdx]
            root.addView(tv("$quizTopic   ${quizIdx + 1}/${quiz.size}", 14f))
            root.addView(tv(qn.q, 22f))
            for (i in qn.options.indices) {
                val b = btn(qn.options[i]) { onAnswer(i) }
                b.isEnabled = !quizAnswered
                root.addView(b)
            }
            if (quizAnswered) {
                val ok = lastChoice == qn.correct
                root.addView(tv(if (ok) "Correct" else "Wrong. Answer: ${qn.options[qn.correct]}", 18f))
                root.addView(btn(if (quizIdx + 1 < quiz.size) "Next" else "See result") { nextQuestion() })
            }
        }
        addExit()
        addDiag()
    }
}
