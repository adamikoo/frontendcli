package com.antigravity.pocketgravity.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

object DebugLogger {
    const val TAG = "PocketGravity"
    private const val MAX_LOGS = 1500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val logs = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile var lastCommand: String = ""
    @Volatile var lastExitCode: Int = 0
    @Volatile var lastStderr: String = ""
    @Volatile var lastStdout: String = ""
    @Volatile var lastRunTimestamp: Long = 0L

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

    fun logBanner(title: String, details: Map<String, Any?> = emptyMap()) {
        val border = "================================================================================"
        Log.e(TAG, border)
        Log.e(TAG, " [POCKETGRAVITY DEBUG] $title")
        Log.e(TAG, border)
        for ((k, v) in details) {
            Log.e(TAG, "  * $k: $v")
        }
        Log.e(TAG, border)
        append("==== $title ====")
        for ((k, v) in details) {
            append("  $k: $v")
        }
    }

    fun recordProcessRun(cmd: String, exitCode: Int, stdout: String, stderr: String) {
        lastCommand = cmd
        lastExitCode = exitCode
        lastStdout = stdout
        lastStderr = stderr
        lastRunTimestamp = System.currentTimeMillis()

        val summary = mapOf(
            "Command" to cmd,
            "Exit Code" to exitCode,
            "Stdout Length" to stdout.length,
            "Stderr Length" to stderr.length,
            "Stderr Snippet" to (stderr.trim().take(300).ifEmpty { "(empty)" }),
            "Stdout Snippet" to (stdout.trim().take(300).ifEmpty { "(empty)" })
        )
        logBanner("PROCESS RUN COMPLETE (exitCode=$exitCode)", summary)
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

    fun copyToClipboard(context: Context, text: String, label: String = "PocketGravity Debug Info") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "📋 Debug info copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateFullReport(context: Context? = null): String {
        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("POCKETGRAVITY / CLIFRONTEND COMPREHENSIVE DIAGNOSTIC REPORT")
        sb.appendLine("Generated at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
        sb.appendLine("================================================================================")
        sb.appendLine()

        // 1. Device Info
        sb.appendLine("### 1. Device & Android System")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Brand: ${Build.BRAND}")
        sb.appendLine("Android Release: ${Build.VERSION.RELEASE}")
        sb.appendLine("SDK Version: ${Build.VERSION.SDK_INT}")
        sb.appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine()

        // 2. Runtime & Directories
        sb.appendLine("### 2. Runtime Directories & Storage")
        if (RuntimeManager.isInitialized) {
            val fDir = RuntimeManager.filesDir
            val rDir = RuntimeManager.runtimeDir
            val rootfs = RuntimeManager.rootfsDir
            val home = RuntimeManager.homeDir
            val ws = RuntimeManager.workspaceDir
            sb.appendLine("filesDir:     ${fDir.absolutePath} (exists=${fDir.exists()}, free=${fDir.freeSpace / (1024 * 1024)} MB)")
            sb.appendLine("runtimeDir:   ${rDir.absolutePath} (exists=${rDir.exists()})")
            sb.appendLine("rootfsDir:    ${rootfs.absolutePath} (exists=${rootfs.exists()})")
            sb.appendLine("homeDir:      ${home.absolutePath} (exists=${home.exists()})")
            sb.appendLine("workspaceDir: ${ws.absolutePath} (exists=${ws.exists()})")
            sb.appendLine("glibcDir:     ${RuntimeManager.glibcDir.absolutePath} (exists=${RuntimeManager.glibcDir.exists()}, files=${RuntimeManager.glibcDir.list()?.size ?: 0})")
        } else {
            sb.appendLine("RuntimeManager: NOT INITIALIZED")
        }
        sb.appendLine()

        // 3. Engine & PRoot Emulation
        sb.appendLine("### 3. Binaries, PRoot & Linkers")
        val proot = RuntimeManager.findProotBinary()
        val agy = RuntimeManager.findAgyBinary()
        val ld = RuntimeManager.getLdLinux()
        sb.appendLine("PRoot Binary:   ${proot?.absolutePath ?: "NOT FOUND"} (exists=${proot?.exists()}, size=${proot?.length() ?: 0} B, canExec=${proot?.canExecute()})")
        val prootDir = File(RuntimeManager.runtimeDir, "proot")
        val loader = File(prootDir, "loader")
        val loader32 = File(prootDir, "loader32")
        val talloc = File(prootDir, "libtalloc.so.2")
        sb.appendLine("PRoot Loader:   ${loader.absolutePath} (exists=${loader.exists()}, size=${loader.length()} B)")
        sb.appendLine("PRoot Loader32: ${loader32.absolutePath} (exists=${loader32.exists()})")
        sb.appendLine("libtalloc.so.2: ${talloc.absolutePath} (exists=${talloc.exists()}, size=${talloc.length()} B)")
        sb.appendLine("Antigravity:    ${agy?.absolutePath ?: "NOT FOUND"} (exists=${agy?.exists()}, size=${agy?.length() ?: 0} B, canExec=${agy?.canExecute()})")
        sb.appendLine("ld-linux:       ${ld?.absolutePath ?: "NOT FOUND"} (exists=${ld?.exists()}, size=${ld?.length() ?: 0} B)")
        sb.appendLine("Standalone Ready: ${RuntimeManager.isStandaloneRuntimeReady()}")
        sb.appendLine()

        // 4. Credential & Auth Files Inspection
        sb.appendLine("### 4. Credentials & Token Inspection")
        val credFiles = listOf(
            "RuntimeManager.agyTokenFile" to RuntimeManager.agyTokenFile,
            "agySettingsFile" to RuntimeManager.agySettingsFile,
            "oauth_credentials.json (agyConfigDir)" to File(RuntimeManager.agyConfigDir, "oauth_credentials.json"),
            "oauth_creds.json (~/.gemini)" to File(RuntimeManager.homeDir, ".gemini/oauth_creds.json"),
            "ADC (~/.config/gcloud/application_default_credentials.json)" to File(RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json"),
            "ADC (agyConfigDir/application_default_credentials.json)" to File(RuntimeManager.agyConfigDir, "application_default_credentials.json"),
            "ADC (~/.gemini/application_default_credentials.json)" to File(RuntimeManager.homeDir, ".gemini/application_default_credentials.json"),
            "google_accounts.json" to File(RuntimeManager.homeDir, ".gemini/google_accounts.json"),
            "Termux token" to File("/data/data/com.termux/files/home/.gemini/antigravity-cli/antigravity-oauth-token"),
            "Termux ADC" to File("/data/data/com.termux/files/home/.config/gcloud/application_default_credentials.json"),
            "Rootfs /root token" to File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/antigravity-oauth-token")
        )
        for ((label, f) in credFiles) {
            if (f.exists()) {
                val len = f.length()
                val snippet = try {
                    val txt = f.readText().trim()
                    if (txt.startsWith("{")) {
                        val obj = org.json.JSONObject(txt)
                        val keys = obj.keys().asSequence().toList()
                        val type = obj.optString("type", "")
                        val hasRefresh = obj.optString("refresh_token", "").isNotEmpty()
                        val hasAccess = obj.optString("access_token", "").isNotEmpty()
                        "JSON type='$type', keys=$keys, hasRefresh=$hasRefresh, hasAccess=$hasAccess"
                    } else {
                        val prefix = txt.take(8)
                        val suffix = txt.takeLast(4)
                        "Raw token len=${txt.length} ($prefix...$suffix)"
                    }
                } catch (e: Exception) {
                    "Read error: ${e.message}"
                }
                sb.appendLine("  ✓ $label: EXISTS (${len} B) -> $snippet")
            } else {
                sb.appendLine("  ✗ $label: NOT FOUND (${f.absolutePath})")
            }
        }
        sb.appendLine()

        // 5. Last Process Execution Details
        sb.appendLine("### 5. Last Process Execution Record")
        if (lastCommand.isNotEmpty()) {
            sb.appendLine("Executed at: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(lastRunTimestamp))}")
            sb.appendLine("Command:     $lastCommand")
            sb.appendLine("Exit Code:   $lastExitCode")
            sb.appendLine("--- Stdout (${lastStdout.length} chars) ---")
            sb.appendLine(lastStdout.ifEmpty { "(empty stdout)" })
            sb.appendLine("--- Stderr (${lastStderr.length} chars) ---")
            sb.appendLine(lastStderr.ifEmpty { "(empty stderr)" })
        } else {
            sb.appendLine("No process execution recorded yet since app start.")
        }
        sb.appendLine()

        // 6. Recent Logcat / Console Buffer
        sb.appendLine("### 6. Recent Console / Logcat Buffer (last 200 entries)")
        val recentLogs = logs.takeLast(200)
        if (recentLogs.isNotEmpty()) {
            for (l in recentLogs) {
                sb.appendLine(l)
            }
        } else {
            sb.appendLine("(No log buffer entries)")
        }
        sb.appendLine()
        sb.appendLine("================================================================================")
        sb.appendLine("END OF DIAGNOSTIC REPORT")
        sb.appendLine("================================================================================")
        return sb.toString()
    }
}

