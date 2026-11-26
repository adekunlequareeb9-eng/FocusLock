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
    private var delayBox: EditText? = null
    private var lenBox: EditText? = null
    private var autoView: TextView? = null
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
        // Notices a fresh boot and starts the auto lock when its delay is over.
        if (Session.autoTick(this)) {
            exitAt = 0L
            resetQuiz()
            tbPage = -1
            notice = "Auto lock started."
        }

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

        val newSig = "$newPhase|$svc|$ovl|${Session.topic(this).isBlank()}|$tbPage|" +
            "$quizIdx|$quizAnswered|${notice ?: ""}"
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
        if (phase == 1) {
            val left = Session.autoDueSec(this)
            autoView?.text = if (left < 0) {
                "No auto lock is waiting. One is scheduled every time the phone is turned on."
            } else {
                String.format("Auto lock starts in %02d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)
            }
        }
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
            notice = "Quiz failed ($score/$total, needed $need). +${Session.QUIZ_FAIL_PENALTY_MIN} minutes. Re-read the lesson and try again."
        }
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

    private fun numberBox(initial: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(initial.toString())
        setTextColor(Color.WHITE)
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
        minutesBox = null; delayBox = null; lenBox = null; autoView = null
        timerView = null; commitView = null; diagView = null; exitBtn = null
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
        minutesBox = numberBox(5)
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
            tbPage = -1
            resetQuiz()
            Session.start(this, m)
            rebuild()
        })

        root.addView(tv(" "))
        root.addView(tv("Auto lock after every boot", 20f))
        root.addView(tv("Starts this many minutes after the phone is turned on (1-${Session.AUTO_DELAY_MAX_MIN}):", 14f))
        delayBox = numberBox(Session.autoDelayMin(this))
        root.addView(delayBox)
        root.addView(tv("Stays locked for this many minutes (${Session.AUTO_LEN_MIN_MIN}-${Session.MAX_MINUTES}):", 14f))
        lenBox = numberBox(Session.autoLenMin(this))
        root.addView(lenBox)
        root.addView(btn("Save auto lock (used from the next boot)") {
            val d = delayBox?.text?.toString()?.toIntOrNull() ?: Session.AUTO_DELAY_DEFAULT_MIN
            val l = lenBox?.text?.toString()?.toIntOrNull() ?: Session.AUTO_LEN_MIN_MIN
            Session.setAuto(this, d, l)
            notice = "Auto lock saved: ${Session.autoDelayMin(this)} min after the phone starts, locked for ${Session.autoLenMin(this)} min."
            rebuild()
        })
        autoView = tv("", 14f)
        root.addView(autoView)

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
        } else {
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

                if (packs.isNotEmpty()) {
                    root.addView(tv("Or choose a flashcard pack", 18f))
                    for (p in packs) {
                        root.addView(btn("Study pack: ${p.name}") {
                            Session.setTopic(this, p.name)
                            rebuild()
                        })
                    }
                }
            } else if (topic.startsWith(Textbook.PREFIX) && lesson != null) {
                root.addView(tv("Studying: ${lesson.title}. A quiz unlocks the phone when the timer ends.", 16f))
                root.addView(btn("Open lesson pages") {
                    tbPage = lesson.first
                    rebuild()
                })
            } else {
                root.addView(tv("Quiz topic: $topic. A quiz unlocks the phone when the timer ends.", 16f))
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
