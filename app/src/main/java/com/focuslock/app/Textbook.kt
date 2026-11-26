package com.focuslock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

// Reads assets/books/topics.txt (lesson list) and shows the lesson's pages from the PDFs in assets/books.
object Textbook {
    const val PREFIX = "Textbook: "
    // Until lesson-specific cards exist, textbook sessions are quizzed from this pack.
    const val QUIZ_PACK = "Arabic grammar basics"

    data class Lesson(val n: Int, val file: String, val first: Int, val last: Int, val title: String)

    private const val PREFS = "focuslock_tb"
    private const val KEY_NEXT = "next"

    private var cache: List<Lesson>? = null
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var openName = ""

    fun load(c: Context): List<Lesson> {
        val cached = cache
        if (cached != null) return cached
        val out = mutableListOf<Lesson>()
        try {
            val raw = c.assets.open("books/topics.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val text = raw.removePrefix("﻿")
            for (r in text.lines()) {
                val line = r.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split("|")
                if (p.size < 5) continue
                val n = p[0].trim().toIntOrNull() ?: continue
                val a = p[2].trim().toIntOrNull() ?: continue
                val b = p[3].trim().toIntOrNull() ?: continue
                out.add(Lesson(n, p[1].trim(), a, b, p.drop(4).joinToString("|").trim()))
            }
        } catch (_: Exception) {
        }
        cache = out
        return out
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nextIndex(c: Context): Int = prefs(c).getInt(KEY_NEXT, 0)

    fun current(c: Context): Lesson? {
        val l = load(c)
        if (l.isEmpty()) return null
        return l[nextIndex(c).coerceIn(0, l.size - 1)]
    }

    // Called when a textbook session's quiz is passed. Wraps to the first lesson after the last one.
    fun advance(c: Context) {
        val size = load(c).size
        if (size == 0) return
        val n = nextIndex(c) + 1
        prefs(c).edit().putInt(KEY_NEXT, if (n >= size) 0 else n).commit()
    }

    fun close() {
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        renderer = null
        pfd = null
        openName = ""
    }

    // Copies the PDF out of assets once (PdfRenderer needs a real file), then opens it.
    fun open(c: Context, file: String): Boolean {
        if (openName == file && renderer != null) return true
        close()
        return try {
            val dest = File(c.cacheDir, "tb_" + file.replace(' ', '_'))
            if (!dest.exists() || dest.length() == 0L) {
                val tmp = File(c.cacheDir, dest.name + ".tmp")
                c.assets.open("books/$file").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(dest)
            }
            val fd = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = fd
            renderer = PdfRenderer(fd)
            openName = file
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    // page1 is a 1-based PDF page number.
    fun render(page1: Int, width: Int): Bitmap? {
        val r = renderer ?: return null
        val idx = page1 - 1
        if (idx < 0 || idx >= r.pageCount || width <= 0) return null
        return try {
            val pg = r.openPage(idx)
            val h = (width.toFloat() * pg.height / pg.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pg.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
