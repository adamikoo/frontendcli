package com.antigravity.pocketgravity.api

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class BridgeClient(private val context: Context? = null) {

    private val prefs = context?.getSharedPreferences("pocketgravity_prefs", Context.MODE_PRIVATE)

    companion object {
        fun getDefaultUrl(): String = "http://127.0.0.1:8765"
    }

    var baseUrl: String
        get() = prefs?.getString("bridge_url", null) ?: getDefaultUrl()
        set(value) {
            prefs?.edit()?.putString("bridge_url", value)?.apply()
        }

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun <T> runOnMain(callback: (T) -> Unit, data: T) {
        mainHandler.post { callback(data) }
    }

    fun getHealth(callback: (Result<HealthResponse>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/health")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Connection", "close")
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val body = reader.readText()
                    reader.close()
                    conn.disconnect()
                    val json = JSONObject(body)
                    val resp = HealthResponse.fromJson(json)
                    runOnMain(callback, Result.success(resp))
                } else {
                    conn.disconnect()
                    runOnMain(callback, Result.failure(Exception("HTTP $code")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun getModels(callback: (Result<List<ModelItem>>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/models")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("models") ?: JSONArray()
                    val list = mutableListOf<ModelItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            ModelItem(
                                id = item.optString("id"),
                                name = item.optString("name"),
                                selected = item.optBoolean("selected", false)
                            )
                        )
                    }
                    runOnMain(callback, Result.success(list))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun setModel(modelId: String, callback: (Boolean) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/model")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("model", modelId).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                val success = (conn.responseCode == 200)
                runOnMain(callback, success)
            } catch (e: Exception) {
                runOnMain(callback, false)
            }
        }
    }

    fun setEffort(effort: String, callback: (Boolean) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/effort")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("effort", effort).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                val success = (conn.responseCode == 200)
                runOnMain(callback, success)
            } catch (e: Exception) {
                runOnMain(callback, false)
            }
        }
    }

    fun getFiles(path: String, includeHidden: Boolean = false, callback: (Result<List<FileItem>>) -> Unit) {
        executor.execute {
            try {
                val enc = java.net.URLEncoder.encode(path, "UTF-8")
                val url = URL("$baseUrl/api/fs/tree?path=$enc&include_hidden=$includeHidden")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("items") ?: JSONArray()
                    val list = mutableListOf<FileItem>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            FileItem(
                                name = o.optString("name"),
                                path = o.optString("path"),
                                isDir = o.optBoolean("is_dir"),
                                size = o.optLong("size"),
                                modified = o.optDouble("modified")
                            )
                        )
                    }
                    runOnMain(callback, Result.success(list))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun getFile(path: String, callback: (Result<String>) -> Unit) {
        executor.execute {
            try {
                val enc = java.net.URLEncoder.encode(path, "UTF-8")
                val url = URL("$baseUrl/api/fs/file?path=$enc")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(body)
                    val content = json.optString("content", "")
                    runOnMain(callback, Result.success(content))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun saveFile(path: String, content: String, callback: (Result<Boolean>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/fs/file")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("path", path).put("content", content).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                if (conn.responseCode == 200) {
                    runOnMain(callback, Result.success(true))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun createItem(path: String, isDir: Boolean, callback: (Result<Boolean>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/fs/create")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("path", path).put("is_dir", isDir).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                runOnMain(callback, Result.success(conn.responseCode == 200))
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun deleteItem(path: String, callback: (Result<Boolean>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/fs/delete")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("path", path).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                runOnMain(callback, Result.success(conn.responseCode == 200))
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun renameItem(src: String, dest: String, callback: (Result<Boolean>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/fs/rename")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("src", src).put("dest", dest).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                runOnMain(callback, Result.success(conn.responseCode == 200))
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun getGitChanges(workspace: String, callback: (Result<List<GitChange>>) -> Unit) {
        executor.execute {
            try {
                val enc = java.net.URLEncoder.encode(workspace, "UTF-8")
                val url = URL("$baseUrl/api/git/changes?workspace=$enc")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("changes") ?: JSONArray()
                    val list = mutableListOf<GitChange>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(GitChange(status = o.optString("status"), path = o.optString("path")))
                    }
                    runOnMain(callback, Result.success(list))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun getGitDiff(workspace: String, path: String, callback: (Result<String>) -> Unit) {
        executor.execute {
            try {
                val wsEnc = java.net.URLEncoder.encode(workspace, "UTF-8")
                val pEnc = java.net.URLEncoder.encode(path, "UTF-8")
                val url = URL("$baseUrl/api/git/diff?workspace=$wsEnc&path=$pEnc")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(body)
                    val diff = json.optString("diff", "")
                    runOnMain(callback, Result.success(diff))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun execTerminal(command: String, cwd: String, callback: (Result<TerminalExecResponse>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/terminal/exec")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("command", command).put("cwd", cwd).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(body)
                    val resp = TerminalExecResponse(
                        stdout = json.optString("stdout"),
                        stderr = json.optString("stderr"),
                        exitCode = json.optInt("exit_code")
                    )
                    runOnMain(callback, Result.success(resp))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun stopAgent(callback: (Boolean) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/agent/stop")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                val ok = (conn.responseCode == 200)
                runOnMain(callback, ok)
            } catch (e: Exception) {
                runOnMain(callback, false)
            }
        }
    }

    fun streamAgent(
        prompt: String,
        model: String? = null,
        effort: String? = null,
        onEvent: (JSONObject) -> Unit,
        onComplete: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/agent/stream")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "text/event-stream")
                conn.readTimeout = 0 // Infinite stream timeout

                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    if (!model.isNullOrEmpty()) put("model", model)
                    if (!effort.isNullOrEmpty()) put("effort", effort)
                    put("dangerously_skip_permissions", true)
                }.toString()

                OutputStreamWriter(conn.outputStream).use { it.write(payload) }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line?.trim() ?: continue
                        if (l.startsWith("data: ")) {
                            val dataStr = l.substring(6).trim()
                            try {
                                val json = JSONObject(dataStr)
                                if (json.optString("event") == "exit") {
                                    val exitCode = json.optInt("exit_code", 0)
                                    mainHandler.post {
                                        try { onComplete(exitCode) } catch (e: Throwable) { DebugLogger.e("onComplete error", e) }
                                    }
                                } else {
                                    mainHandler.post {
                                        try { onEvent(json) } catch (e: Throwable) { DebugLogger.e("onEvent error", e) }
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip non-json lines
                            }
                        }
                    }
                    reader.close()
                } else {
                    mainHandler.post {
                        try { onError("Server returned HTTP ${conn.responseCode}") } catch (e: Throwable) { DebugLogger.e("onError error", e) }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    try { onError(e.message ?: "Connection error") } catch (t: Throwable) { DebugLogger.e("onError error", t) }
                }
            }
        }
    }

    fun startAuthLogin(callback: (Result<String>) -> Unit) {
        DebugLogger.i("Calling POST $baseUrl/api/auth/login")
        executor.execute {
            try {
                val url = URL("$baseUrl/api/auth/login")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val code = conn.responseCode
                if (code == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
                    DebugLogger.i("Auth login response: $body")
                    val json = JSONObject(body)
                    val loginUrl = json.optString("url", "")
                    runOnMain(callback, Result.success(loginUrl))
                } else {
                    val errStream = conn.errorStream
                    val errBody = if (errStream != null) BufferedReader(InputStreamReader(errStream, "UTF-8")).readText() else ""
                    DebugLogger.e("Auth login failed HTTP $code: $errBody")
                    val msg = try { JSONObject(errBody).optString("message", "HTTP $code") } catch (e: Exception) { "HTTP $code: $errBody" }
                    runOnMain(callback, Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                DebugLogger.e("startAuthLogin error", e)
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun saveToken(token: String, callback: (Boolean) -> Unit) {
        DebugLogger.i("Calling POST $baseUrl/api/auth/token")
        executor.execute {
            try {
                val url = URL("$baseUrl/api/auth/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("token", token).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                val success = (conn.responseCode == 200)
                DebugLogger.i("Save token status: $success (code: ${conn.responseCode})")
                runOnMain(callback, success)
            } catch (e: Exception) {
                DebugLogger.e("saveToken error", e)
                runOnMain(callback, false)
            }
        }
    }

    fun logout(callback: (Boolean) -> Unit) {
        DebugLogger.i("Calling POST $baseUrl/api/auth/logout")
        executor.execute {
            try {
                val url = URL("$baseUrl/api/auth/logout")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val success = (conn.responseCode == 200)
                DebugLogger.i("Logout status: $success (code: ${conn.responseCode})")
                runOnMain(callback, success)
            } catch (e: Exception) {
                DebugLogger.e("logout error", e)
                runOnMain(callback, false)
            }
        }
    }

    fun testCli(arg: String = "--version", callback: (Result<JSONObject>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/debug/test-cli")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("arg", arg).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = if (stream != null) BufferedReader(InputStreamReader(stream, "UTF-8")).readText() else "{}"
                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject().put("raw", body) }

                if (code == 200) {
                    runOnMain(callback, Result.success(json))
                } else {
                    val err = json.optString("error", "HTTP $code")
                    runOnMain(callback, Result.failure(Exception(err)))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }

    fun getDebugReport(callback: (Result<String>) -> Unit) {
        executor.execute {
            try {
                val url = URL("$baseUrl/api/debug/report")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
                    val json = JSONObject(body)
                    runOnMain(callback, Result.success(json.optString("report", "")))
                } else {
                    runOnMain(callback, Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                runOnMain(callback, Result.failure(e))
            }
        }
    }
}

