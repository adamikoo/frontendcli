package com.antigravity.pocketgravity.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object AnsiColorParser {
    // Regex for ANSI CSI escape codes: e.g. \u001B[31m
    private val ANSI_REGEX = Regex("\u001B\\[([0-9;]*)m")

    fun parse(text: String): CharSequence {
        val ssb = SpannableStringBuilder()
        var lastIdx = 0

        var currentColor = Color.parseColor("#CCCCCC")
        var isBold = false

        for (match in ANSI_REGEX.findAll(text)) {
            val start = match.range.first
            if (start > lastIdx) {
                val spanStart = ssb.length
                ssb.append(text.substring(lastIdx, start))
                val spanEnd = ssb.length
                ssb.setSpan(ForegroundColorSpan(currentColor), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isBold) {
                    ssb.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            val codeStr = match.groupValues[1]
            val codes = if (codeStr.isEmpty()) listOf(0) else codeStr.split(";").mapNotNull { it.toIntOrNull() }

            for (c in codes) {
                when (c) {
                    0 -> { currentColor = Color.parseColor("#CCCCCC"); isBold = false }
                    1 -> isBold = true
                    30 -> currentColor = Color.parseColor("#4B5563")
                    31 -> currentColor = Color.parseColor("#EF4444") // Red
                    32 -> currentColor = Color.parseColor("#10B981") // Green
                    33 -> currentColor = Color.parseColor("#F59E0B") // Yellow
                    34 -> currentColor = Color.parseColor("#3B82F6") // Blue
                    35 -> currentColor = Color.parseColor("#A855F7") // Magenta
                    36 -> currentColor = Color.parseColor("#06B6D4") // Cyan
                    37 -> currentColor = Color.parseColor("#F3F4F6") // White
                    90 -> currentColor = Color.parseColor("#6B7280")
                    91 -> currentColor = Color.parseColor("#F87171")
                    92 -> currentColor = Color.parseColor("#34D399")
                    93 -> currentColor = Color.parseColor("#FBBF24")
                    94 -> currentColor = Color.parseColor("#60A5FA")
                    95 -> currentColor = Color.parseColor("#C084FC")
                    96 -> currentColor = Color.parseColor("#22D3EE")
                    97 -> currentColor = Color.parseColor("#FFFFFF")
                }
            }
            lastIdx = match.range.last + 1
        }

        if (lastIdx < text.length) {
            val spanStart = ssb.length
            ssb.append(text.substring(lastIdx))
            val spanEnd = ssb.length
            ssb.setSpan(ForegroundColorSpan(currentColor), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (isBold) {
                ssb.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return ssb
    }
}

object DiffColorizer {
    fun colorize(diffText: String): CharSequence {
        val ssb = SpannableStringBuilder()
        val lines = diffText.lines()
        for ((idx, line) in lines.withIndex()) {
            val start = ssb.length
            ssb.append(line)
            if (idx < lines.size - 1) ssb.append("\n")
            val end = ssb.length

            if (line.startsWith("+") && !line.startsWith("+++")) {
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#34D399")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(BackgroundColorSpan(Color.parseColor("#152D21")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#F87171")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(BackgroundColorSpan(Color.parseColor("#2E181B")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (line.startsWith("@@")) {
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#818CF8")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (line.startsWith("diff --git") || line.startsWith("index ")) {
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#94A3B8")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#CBD5E1")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return ssb
    }
}
