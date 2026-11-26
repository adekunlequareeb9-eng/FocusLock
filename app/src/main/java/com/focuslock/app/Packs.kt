package com.focuslock.app

import android.content.Context

object Packs {
    data class Card(val q: String, val a: String, val explanation: String)
    data class Pack(val name: String, val cards: List<Card>)
    data class Question(
        val q: String,
        val options: List<String>,
        val correct: Int,
        val explanation: String
    )

    private var cache: List<Pack>? = null

    // Reads assets/packs.txt: each card is Q:, A:, and optional E: (explanation).
    fun load(c: Context): List<Pack> {
        val cached = cache
        if (cached != null) return cached
        val packs = mutableListOf<Pack>()
        try {
            val rawText = c.assets.open("packs.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val text = rawText.removePrefix("\uFEFF")
            var name: String? = null
            var cards = mutableListOf<Card>()
            var q: String? = null
            var a: String? = null
            var e = ""
            fun saveCard() {
                val question = q
                val answer = a
                if (question != null && answer != null && name != null) {
                    val explanation = e.ifBlank {
                        "Remember the answer, then try to explain it in your own words. Look for where this idea is used in a real program."
                    }
                    cards.add(Card(question, answer, explanation))
                }
                q = null
                a = null
                e = ""
            }
            for (raw in text.lines()) {
                val line = raw.trim()
                when {
                    line.startsWith("## ") -> {
                        saveCard()
                        val prev = name
                        if (prev != null && cards.isNotEmpty()) packs.add(Pack(prev, cards))
                        name = line.removePrefix("## ").trim()
                        cards = mutableListOf()
                    }
                    line.startsWith("Q:") -> {
                        saveCard()
                        q = line.removePrefix("Q:").trim()
                    }
                    line.startsWith("A:") -> a = line.removePrefix("A:").trim()
                    line.startsWith("E:") -> e = line.removePrefix("E:").trim()
                }
            }
            saveCard()
            val last = name
            if (last != null && cards.isNotEmpty()) packs.add(Pack(last, cards))
        } catch (_: Exception) {
        }
        cache = packs
        return packs
    }

    // Multiple choice: the right answer plus up to 3 different answers from the same pack.
    fun makeQuiz(pack: Pack, n: Int): List<Question> {
        val picked = pack.cards.shuffled().take(n)
        return picked.map { card ->
            val wrong = pack.cards.map { it.a }.filter { it != card.a }.distinct().shuffled().take(3)
            val opts = (wrong + card.a).shuffled()
            Question(card.q, opts, opts.indexOf(card.a), card.explanation)
        }
    }
}
