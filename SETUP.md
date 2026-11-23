# Focus Lock build 4

No adb, no computer, no system-setting changes. If the app or service stops, the lock simply stops.

## How a session works now
1. **Start** a lock (default 5 minutes).
2. You get a short window (2 minutes, or half the session if shorter) to **pick a study topic**. If you don't, +10 minutes is added, and a random topic is assigned for the quiz.
3. While locked you can open the **study cards** for your topic, plus Phone, Messages, Calendar and Clock.
4. When the timer hits zero the lock **does not end yet**: you must **pass a 5-question quiz** (80% needed). Fail and +10 minutes is added, then you get another quiz.
5. The emergency exit now waits **5 minutes** and works in every phase, so you can never be trapped.

All the numbers live at the top of `Session.kt` (`COMMIT_WINDOW_SEC`, `NO_TOPIC_PENALTY_MIN`, `QUIZ_FAIL_PENALTY_MIN`, `QUIZ_QUESTIONS`, `EXIT_DELAY_SEC`).

## Your own study material
Edit `app/src/main/assets/packs.txt` on GitHub:

    ## Pack name
    Q: question on one line
    A: short answer on one line

Give each pack at least 4 cards with different answers (the quiz uses other answers from the same pack as wrong options). The file ships with two starter packs, Arabic grammar basics and Coding basics. Replace them with your own.

## Test (use 3 minutes)
- Start, pick a topic, open the cards, check Phone/Messages/Calendar/Clock still open.
- Leave the topic unpicked in one run to see the +10 min penalty.
- Let the timer end, fail the quiz on purpose once, then pass it.
- Try the emergency exit once (5 minute wait).

## Safety
- Hard cap 240 minutes on the starting duration; failures and missed topics add 10 minutes each.
- To remove the app: with no lock running, switch the service off in accessibility settings, then uninstall.
