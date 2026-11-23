package com.focuslock.app

import android.content.Context

object Packs {
    data class Card(val q: String, val a: String)
    data class Pack(val name: String, val cards: List<Card>)
    data class Question(val q: String, val options: List<String>, val correct: Int)

    private var cache: List<Pack>? = null

    // Reads assets/packs.txt: "## Pack name" then Q:/A: line pairs.
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
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith("## ")) {
                    val prev = name
                    if (prev != null && cards.isNotEmpty()) packs.add(Pack(prev, cards))
                    name = line.removePrefix("## ").trim()
                    cards = mutableListOf()
                    q = null
                } else if (line.startsWith("Q:")) {
                    q = line.removePrefix("Q:").trim()
                } else if (line.startsWith("A:")) {
                    val question = q
                    if (question != null && name != null) {
                        cards.add(Card(question, line.removePrefix("A:").trim()))
                    }
                    q = null
                }
            }
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
            Question(card.q, opts, opts.indexOf(card.a))
        }
    }
}
