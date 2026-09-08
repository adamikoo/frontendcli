package com.antigravity.pocketgravity.api

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

object DebugLogger {
    private const val TAG = "PocketGravity"
    private const val MAX_LOGS = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val logs = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun i(msg: String) {
        Log.i(TAG, msg)
        append("ℹ $msg")
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        append("🔍 $msg")
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        append("⚠ $msg")
    }

    fun e(msg: String, tr: Throwable? = null) {
        Log.e(TAG, msg, tr)
        val errText = if (tr != null) "$msg: ${tr.message}" else msg
        append("✕ $errText")
    }

    private fun append(entry: String) {
        val line = "[${timeFormat.format(Date())}] $entry"
        logs.add(line)
        if (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        for (l in listeners) {
            try { l(line) } catch (ignored: Exception) {}
        }
    }

    fun getLogs(): List<String> = logs.toList()

    fun getAllText(): String = logs.joinToString("\n")

    fun clear() {
        logs.clear()
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
