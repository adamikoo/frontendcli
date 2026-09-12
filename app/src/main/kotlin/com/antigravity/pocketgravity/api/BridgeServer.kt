package com.antigravity.pocketgravity.api

import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class BridgeServer(val port: Int = 8765) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var isRunning = false

    var currentWorkspace: String = RuntimeManager.workspaceDir.absolutePath
    @Volatile private var activeAgentProcess: Process? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        executor.execute {
            try {
                // Check if external bridge (e.g. Termux server.py) is already serving port 8765
                try {
                    val probeSocket = Socket()
                    probeSocket.connect(java.net.InetSocketAddress("127.0.0.1", port), 400)
                    probeSocket.close()
                    DebugLogger.i("External bridge detected on port $port (Termux/Ubuntu). Deferring to external bridge.")
                    return@execute
                } catch (_: Exception) {
                    // Port is free
                }

                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress("0.0.0.0", port), 50)
                }
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            activeAgentProcess?.destroy()
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, "UTF-8"))

            val requestLine = reader.readLine() ?: run { socket.close(); return }
            val parts = requestLine.split(" ")
            if (parts.size < 2) { socket.close(); return }

            val method = parts[0].uppercase()
            val fullPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: break
                if (l.isEmpty()) break
                val colonIdx = l.indexOf(':')
                if (colonIdx > 0) {
                    val k = l.substring(0, colonIdx).trim().lowercase()
                    val v = l.substring(colonIdx + 1).trim()
                    headers[k] = v
                    if (k == "content-length") {
                        contentLength = v.toIntOrNull() ?: 0
                    }
                }
            }

            var body = ""
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = reader.read(bodyChars, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                body = String(bodyChars, 0, readTotal)
            }

            if (method == "OPTIONS") {
                sendResponse(output, 200, "OK", "text/plain", "".toByteArray())
                socket.close()
                return
            }

            val questionIdx = fullPath.indexOf('?')
            val path = if (questionIdx >= 0) fullPath.substring(0, questionIdx) else fullPath
            val queryString = if (questionIdx >= 0) fullPath.substring(questionIdx + 1) else ""
            val queryParams = parseQueryParams(queryString)

            val isStream = path == "/api/agent/stream"
            routeRequest(method, path, queryParams, body, output, socket)
            if (!isStream) {
                try {
                    output.flush()
                    socket.shutdownOutput()
                } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun routeRequest(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String,
        output: OutputStream,
        socket: Socket
    ) {
        val payload = try { if (body.isNotEmpty()) JSONObject(body) else JSONObject() } catch (e: Exception) { JSONObject() }

        when {
            method == "GET" && (path == "/" || path == "/api/health") -> handleHealth(output)
            method == "GET" && path == "/api/models" -> handleGetModels(output)
            method == "GET" && path == "/api/effort" -> handleGetEffort(output)
            method == "POST" && path == "/api/effort" -> handleSetEffort(payload, output)
            method == "POST" && path == "/api/model" -> handleSetModel(payload, output)
            method == "GET" && path == "/api/auth/status" -> handleAuthStatus(output)
            method == "POST" && path == "/api/auth/login" -> handleAuthLogin(output)
            method == "POST" && path == "/api/auth/logout" -> handleAuthLogout(output)
            method == "POST" && path == "/api/auth/token" -> handleAuthToken(payload, output)
            method == "GET" && (path == "/oauth/callback" || path == "/oauth2callback") -> handleOAuthCallback(query, output)
            method == "GET" && path == "/api/workspace" -> sendJson(output, JSONObject().put("workspace", currentWorkspace))
            method == "POST" && path == "/api/workspace" -> handleSetWorkspace(payload, output)
            method == "GET" && path == "/api/fs/tree" -> handleFsTree(query, output)
            method == "GET" && path == "/api/fs/file" -> handleFsFileGet(query, output)
            method == "POST" && path == "/api/fs/file" -> handleFsFileSave(payload, output)
            method == "POST" && path == "/api/fs/create" -> handleFsCreate(payload, output)
            method == "POST" && path == "/api/fs/delete" -> handleFsDelete(payload, output)
            method == "POST" && path == "/api/fs/rename" -> handleFsRename(payload, output)
            method == "GET" && path == "/api/git/changes" -> handleGitChanges(query, output)
            method == "GET" && path == "/api/git/diff" -> handleGitDiff(query, output)
            method == "GET" && path == "/api/sessions" -> handleSessions(output)
            method == "POST" && path == "/api/terminal/exec" -> handleTerminalExec(payload, output)
            method == "POST" && path == "/api/agent/stop" -> handleAgentStop(output)
            method == "POST" && path == "/api/agent/stream" -> handleAgentStream(payload, output, socket)
            method == "POST" && path == "/api/debug/test-cli" -> handleDebugCliTest(payload, output)
            method == "GET" && path == "/api/debug/report" -> handleDebugReport(output)
            method == "GET" && path == "/api/mcps" -> handleGetMcps(output)
            method == "POST" && path == "/api/mcps" -> handleSaveMcps(payload, output)
            method == "GET" && path == "/api/customizations" -> handleGetCustomizations(output)
            method == "POST" && path == "/api/customizations" -> handleSaveCustomization(payload, output)
            method == "GET" && path == "/api/limits" -> handleGetLimits(output)
            method == "POST" && path == "/api/limits" -> handleSetLimits(payload, output)
            method == "GET" && path == "/api/browser/settings" -> handleGetBrowserSettings(output)
            method == "POST" && path == "/api/browser/settings" -> handleSetBrowserSettings(payload, output)
            method == "POST" && path == "/api/upload/image" -> handleUploadImage(payload, output)
            else -> sendJson(output, JSONObject().put("error", "Endpoint not found"), 404)
        }
    }

    @Volatile
    private var lastPkceVerifier: String = ""

    private fun generatePkce(): Pair<String, String> {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        val verifier = android.util.Base64.encodeToString(randomBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = android.util.Base64.encodeToString(sha256, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        lastPkceVerifier = verifier
        return Pair(verifier, challenge)
    }

    private fun handleOAuthCallback(query: Map<String, String>, output: OutputStream) {
        val code = query["code"]
        val token = query["token"]
        if (!token.isNullOrEmpty()) {
            RuntimeManager.agyTokenFile.parentFile?.mkdirs()
            RuntimeManager.agyTokenFile.writeText(token)
            val html = "<html><body style='background:#101216;color:#fff;font-family:sans-serif;text-align:center;padding-top:60px;'><h2 style='color:#4ade80;'>✓ Google Sign-In Successful</h2><p>Pocket gravity is now authenticated. You can return to the app.</p></body></html>"
            sendResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
            return
        }

        if (!code.isNullOrEmpty()) {
            try {
                DebugLogger.i("Exchanging Google OAuth code for access token...")
                val tokenUrl = java.net.URL("https://oauth2.googleapis.com/token")
                val conn = tokenUrl.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                val postData = "code=" + java.net.URLEncoder.encode(code, "UTF-8") +
                        "&client_id=1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com" +
                        "&client_secret=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" +
                        "&code_verifier=" + java.net.URLEncoder.encode(lastPkceVerifier, "UTF-8") +
                        "&redirect_uri=" + java.net.URLEncoder.encode("http://127.0.0.1:8765/oauth2callback", "UTF-8") +
                        "&grant_type=authorization_code"

                conn.outputStream.use { os ->
                    os.write(postData.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val respCode = conn.responseCode
                if (respCode in 200..299) {
                    val respText = conn.inputStream.bufferedReader().readText()
                    val respJson = JSONObject(respText)
                    val accessToken = respJson.optString("access_token")
                    val expiresIn = respJson.optLong("expires_in", 3600)

                    respJson.put("expiry_date", System.currentTimeMillis() + (expiresIn - 300) * 1000)
                    persistOAuthCredentials(respJson, accessToken)

                    DebugLogger.i("Google OAuth access token successfully saved!")
                    val html = "<html><body style='background:#101216;color:#fff;font-family:sans-serif;text-align:center;padding-top:60px;'><h2 style='color:#4ade80;'>✓ Google Sign-In Successful</h2><p>Pocket gravity is authenticated! You can now return to the app.</p></body></html>"
                    sendResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                    return
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $respCode"
                    DebugLogger.e("OAuth code exchange failed: $err")
                    val html = "<html><body style='background:#101216;color:#fff;font-family:sans-serif;text-align:center;padding-top:60px;'><h2 style='color:#f87171;'>OAuth Exchange Failed</h2><p>$err</p></body></html>"
                    sendResponse(output, 400, "Bad Request", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                    return
                }
            } catch (e: Exception) {
                DebugLogger.e("OAuth exchange error", e)
                val html = "<html><body style='background:#101216;color:#fff;font-family:sans-serif;text-align:center;padding-top:60px;'><h2 style='color:#f87171;'>OAuth Error</h2><p>${e.message}</p></body></html>"
                sendResponse(output, 500, "Server Error", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                return
            }
        }

        val html = "<html><body style='background:#101216;color:#fff;font-family:sans-serif;text-align:center;padding-top:60px;'><h2>Google Sign-In</h2><p>No token or authorization code received.</p></body></html>"
        sendResponse(output, 400, "Bad Request", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
    }

    private fun handleHealth(output: OutputStream) {
        val agy = RuntimeManager.findAgyBinary()
        val token = getValidAccessToken()
        val settings = getSettings()

        val resp = JSONObject().apply {
            put("status", "ok")
            put("standalone", true)
            put("termux", File("/data/data/com.termux").exists())
            put("ubuntu", RuntimeManager.glibcDir.exists())
            val agyInstalled = (agy != null && agy.exists())
            put("antigravity", JSONObject().apply {
                put("installed", true)
                put("version", if (agyInstalled) RuntimeManager.getAgyVersion() else "2.0.0 (Standalone Native Engine)")
                put("path", if (agyInstalled) agy?.absolutePath else "embedded/standalone")
            })
            put("auth", JSONObject().apply {
                put("authenticated", token.isNotEmpty())
                put("token_file", RuntimeManager.agyTokenFile.absolutePath)
            })
            put("workspace", currentWorkspace)
            put("model", settings.optString("model", "Gemini 3.8 Flash (Medium)"))
            put("effort", settings.optString("effort", "medium"))
            put("timestamp", System.currentTimeMillis() / 1000.0)
        }
        sendJson(output, resp)
    }

    private fun handleGetModels(output: OutputStream) {
        val agy = RuntimeManager.findAgyBinary()
        val settings = getSettings()
        val currentModel = settings.optString("model", "Gemini 3.8 Flash (Medium)")
        val models = JSONArray()

        if (agy != null && agy.exists()) {
            try {
                val pb = RuntimeManager.buildProcess(listOf(agy.absolutePath, "models"), File(currentWorkspace))
                val p = pb.start()
                val reader = BufferedReader(InputStreamReader(p.inputStream, "UTF-8"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (l.isEmpty() || l.startsWith("⠋") || l.startsWith("⠙") || l.startsWith("⠹") || l.contains("Fetching")) continue
                    val parts = l.split(Regex("\\s+"), 2)
                    if (parts.size >= 2) {
                        models.put(JSONObject().apply {
                            put("id", parts[0])
                            put("name", parts[1])
                            put("selected", parts[0] == currentModel || parts[1] == currentModel)
                        })
                    } else if (parts.size == 1) {
                        models.put(JSONObject().apply {
                            put("id", parts[0])
                            put("name", parts[0])
                            put("selected", parts[0] == currentModel)
                        })
                    }
                }
                p.waitFor()
            } catch (e: Exception) {
                DebugLogger.e("Error querying agy models", e)
            }
        }

        if (models.length() == 0) {
            val defaults = listOf(
                "gemini-3.8-flash-high" to "Gemini 3.8 Flash (High)",
                "gemini-3.8-flash-medium" to "Gemini 3.8 Flash (Medium)",
                "gemini-3.8-flash-low" to "Gemini 3.8 Flash (Low)",
                "gemini-3.7-flash-high" to "Gemini 3.7 Flash (High)",
                "gemini-3.1-pro-high" to "Gemini 3.1 Pro (High)",
                "claude-sonnet-4-6" to "Claude Sonnet 4.6 (Thinking)",
                "gpt-oss-120b-medium" to "GPT-OSS 120B (Medium)"
            )
            for ((id, name) in defaults) {
                models.put(JSONObject().apply {
                    put("id", id)
                    put("name", name)
                    put("selected", id == currentModel || name == currentModel)
                })
            }
        }

        sendJson(output, JSONObject().put("models", models).put("current", currentModel))
    }

    private fun handleGetEffort(output: OutputStream) {
        val settings = getSettings()
        val resp = JSONObject().apply {
            put("current", settings.optString("effort", "medium"))
            put("options", JSONArray().put("low").put("medium").put("high"))
        }
        sendJson(output, resp)
    }

    private fun handleSetEffort(payload: JSONObject, output: OutputStream) {
        val effort = payload.optString("effort", "medium")
        val settings = getSettings()
        settings.put("effort", effort)
        saveSettings(settings)
        sendJson(output, JSONObject().put("status", "ok").put("effort", effort))
    }

    private fun handleSetModel(payload: JSONObject, output: OutputStream) {
        val model = payload.optString("model", "")
        val settings = getSettings()
        settings.put("model", model)
        saveSettings(settings)
        sendJson(output, JSONObject().put("status", "ok").put("model", model))
    }

    fun getValidAccessToken(forceRefresh: Boolean = false): String {
        val tokenFile = RuntimeManager.agyTokenFile
        var token = if (tokenFile.exists()) {
            val text = tokenFile.readText().trim()
            if (text.startsWith("{")) {
                try { JSONObject(text).optString("access_token", "") } catch (_: Exception) { text }
            } else {
                text
            }
        } else ""

        val candidates = listOf(
            File(RuntimeManager.agyConfigDir, "oauth_credentials.json"),
            File(RuntimeManager.homeDir, ".gemini/oauth_creds.json"),
            File(RuntimeManager.rootfsDir, "root/.gemini/oauth_creds.json"),
            File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/oauth_credentials.json"),
            File(RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json")
        )

        var refreshToken = ""
        var expiresAt = 0L
        var targetJson: JSONObject? = null

        for (f in candidates) {
            if (f.exists() && f.length() > 0) {
                try {
                    val j = JSONObject(f.readText().trim())
                    val r = j.optString("refresh_token", "")
                    if (r.isNotEmpty() && refreshToken.isEmpty()) {
                        refreshToken = r
                        targetJson = j
                        expiresAt = if (j.has("expiry_date")) j.optLong("expiry_date", 0L) else j.optLong("expires_at", 0L)
                    }
                } catch (_: Exception) {}
            }
        }

        // Bundled asset fallback if refresh token not on disk
        if (refreshToken.isEmpty()) {
            try {
                val assetStream = RuntimeManager.appContext.assets.open("default_credentials.json")
                val assetJson = JSONObject(assetStream.bufferedReader().readText())
                val r = assetJson.optString("refresh_token", "")
                if (r.isNotEmpty()) {
                    refreshToken = r
                    targetJson = assetJson
                    if (token.isEmpty()) {
                        token = assetJson.optString("access_token", "")
                    }
                }
            } catch (_: Exception) {}
        }

        val needsRefresh = forceRefresh || token.isEmpty() || token.length < 30 ||
                (refreshToken.isNotEmpty() && (expiresAt == 0L || System.currentTimeMillis() >= (expiresAt - 300_000L)))

        if (refreshToken.isNotEmpty() && needsRefresh) {
            DebugLogger.i("Refreshing Google OAuth token (force=$forceRefresh, tokenLen=${token.length}, expiresAt=$expiresAt)...")
            try {
                val conn = java.net.URL("https://oauth2.googleapis.com/token").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.doOutput = true
                val postData = "client_id=1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com" +
                        "&client_secret=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" +
                        "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, "UTF-8") +
                        "&grant_type=refresh_token"
                conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode in 200..299) {
                    val respJson = JSONObject(conn.inputStream.bufferedReader().readText())
                    val newAccessToken = respJson.optString("access_token")
                    if (newAccessToken.isNotEmpty()) {
                        token = newAccessToken
                        val expIn = respJson.optLong("expires_in", 3600)
                        val newExpiry = System.currentTimeMillis() + expIn * 1000L

                        val jsonToSave = (targetJson ?: JSONObject()).apply {
                            put("access_token", newAccessToken)
                            put("refresh_token", refreshToken)
                            put("token_type", "Bearer")
                            put("expires_in", expIn)
                            put("expiry_date", newExpiry)
                            put("expires_at", newExpiry)
                        }
                        val jsonStr = jsonToSave.toString(2)

                        // 1. Write raw access token to ALL token files
                        listOf(
                            tokenFile,
                            File(RuntimeManager.agyConfigDir, "antigravity-oauth-token"),
                            File(RuntimeManager.homeDir, ".gemini/antigravity-cli/antigravity-oauth-token"),
                            File(RuntimeManager.homeDir, ".gemini/antigravity-oauth-token"),
                            File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/antigravity-oauth-token"),
                            File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-oauth-token")
                        ).forEach { tf ->
                            try {
                                tf.parentFile?.mkdirs()
                                tf.writeText(newAccessToken)
                            } catch (_: Exception) {}
                        }

                        // 2. Write credential JSON to ALL credential stores
                        candidates.forEach { cf ->
                            try {
                                cf.parentFile?.mkdirs()
                                cf.writeText(jsonStr)
                            } catch (_: Exception) {}
                        }

                        DebugLogger.i("Google OAuth token refreshed successfully! (new expiry: $newExpiry)")
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
                    DebugLogger.e("Token refresh failed: $err")
                }
            } catch (e: Exception) {
                DebugLogger.e("Error refreshing token", e)
            }
        }
        return token
    }

    private fun handleAuthStatus(output: OutputStream) {
        val token = getValidAccessToken()
        val adcFile = File(RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json")
        val auth = token.isNotEmpty() || adcFile.exists()
        sendJson(output, JSONObject().put("authenticated", auth).put("token_file", RuntimeManager.agyTokenFile.absolutePath))
    }

    private fun handleAuthLogin(output: OutputStream) {
        val agy = RuntimeManager.findAgyBinary()
        var oauthUrl: String? = null

        if (agy != null && agy.exists()) {
            try {
                DebugLogger.i("Executing agy auth login at: ${agy.absolutePath}")
                val pb = RuntimeManager.buildProcess(listOf(agy.absolutePath, "auth", "login"), File(currentWorkspace), forceProot = true)
                pb.redirectErrorStream(true)
                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream, "UTF-8"))
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < 8000) {
                    val line = reader.readLine() ?: break
                    DebugLogger.d("agy auth output: $line")
                    val match = Regex("https://[^\\s\"'>]+").find(line)
                    if (match != null) {
                        oauthUrl = match.value
                        break
                    }
                }
                try { process.destroy() } catch (_: Exception) {}
            } catch (e: Exception) {
                DebugLogger.e("Failed to execute agy auth login", e)
            }
        }

        val (verifier, challenge) = generatePkce()
        val finalUrl = oauthUrl ?: ("https://accounts.google.com/o/oauth2/auth?" +
                "access_type=offline" +
                "&client_id=1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com" +
                "&code_challenge=" + java.net.URLEncoder.encode(challenge, "UTF-8") +
                "&code_challenge_method=S256" +
                "&prompt=consent" +
                "&redirect_uri=" + java.net.URLEncoder.encode("http://127.0.0.1:8765/oauth2callback", "UTF-8") +
                "&response_type=code" +
                "&scope=" + java.net.URLEncoder.encode("https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/cclog https://www.googleapis.com/auth/experimentsandconfigs https://www.googleapis.com/auth/aicode openid", "UTF-8"))

        DebugLogger.i("Returning login URL: $finalUrl")
        sendJson(output, JSONObject().put("status", "ok").put("url", finalUrl).put("source", if (oauthUrl != null) "agy_cli" else "standalone_oauth"))
    }

    private fun handleAuthLogout(output: OutputStream) {
        try {
            val filesToDelete = listOf(
                RuntimeManager.agyTokenFile,
                File(RuntimeManager.agyConfigDir, "antigravity-oauth-token"),
                File(RuntimeManager.homeDir, ".gemini/antigravity-cli/antigravity-oauth-token"),
                File(RuntimeManager.agyConfigDir, "oauth_credentials.json"),
                File(RuntimeManager.homeDir, ".gemini/oauth_creds.json"),
                File(RuntimeManager.homeDir, ".gemini/antigravity/mcp_oauth_tokens.json"),
                File(RuntimeManager.homeDir, ".config/agy/credentials.json"),
                File(RuntimeManager.homeDir, ".gemini/google_accounts.json"),
                File(RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json"),
                File(RuntimeManager.agyConfigDir, "application_default_credentials.json"),
                File(RuntimeManager.homeDir, ".gemini/application_default_credentials.json"),
                File("/data/data/com.termux/files/home/.gemini/antigravity-cli/antigravity-oauth-token"),
                File("/root/.gemini/antigravity-cli/antigravity-oauth-token")
            )
            for (f in filesToDelete) {
                try {
                    if (f.exists()) {
                        f.delete()
                    }
                } catch (_: Exception) {}
            }

            // Remove Gemini API key mode or modelProvider if present
            val settings = getSettings()
            if (settings.has("modelProvider")) {
                settings.remove("modelProvider")
                saveSettings(settings)
            }

            DebugLogger.i("User logged out, all token and ADC credential files deleted")
            sendJson(output, JSONObject().put("status", "ok").put("authenticated", false))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleAuthToken(payload: JSONObject, output: OutputStream) {
        val input = payload.optString("token", "").trim()
        if (input.isEmpty()) {
            sendJson(output, JSONObject().put("error", "Empty token"), 400)
            return
        }

        DebugLogger.logBanner("AUTH TOKEN RECEIVED", mapOf(
            "Input Length" to input.length,
            "Starts with {" to input.startsWith("{"),
            "Starts with 4/" to input.startsWith("4/"),
            "Starts with ya29" to input.startsWith("ya29"),
            "Starts with AIza" to input.startsWith("AIza")
        ))

        try {
            // Case 1: JSON payload pasted (e.g. gcloud ADC, oauth credentials json, etc.)
            if (input.startsWith("{")) {
                val json = JSONObject(input)
                val isAdc = json.optString("type") == "authorized_user" || json.has("refresh_token")
                val accessToken = json.optString("access_token", "")

                persistOAuthCredentials(json, accessToken.ifEmpty { "manual-token" })
                DebugLogger.i("JSON credentials successfully parsed and written to all credential locations!")
                sendJson(output, JSONObject().put("status", "ok").put("authenticated", true).put("type", "json_credentials"))
                return
            }

            // Case 2: Authorization code (starts with 4/ or standard Google code format)
            if (input.startsWith("4/") || (input.length > 40 && !input.startsWith("AIza") && !input.startsWith("ya29"))) {
                DebugLogger.i("Exchanging Google authorization code...")
                val redirectUris = listOf(
                    "http://127.0.0.1:8765/oauth2callback",
                    "https://antigravity.google/oauth-callback"
                )
                for (rUri in redirectUris) {
                    try {
                        val tokenUrl = java.net.URL("https://oauth2.googleapis.com/token")
                        val conn = tokenUrl.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        conn.doOutput = true
                        val postData = "code=" + java.net.URLEncoder.encode(input, "UTF-8") +
                                "&client_id=1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com" +
                                "&client_secret=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" +
                                "&code_verifier=" + java.net.URLEncoder.encode(lastPkceVerifier, "UTF-8") +
                                "&redirect_uri=" + java.net.URLEncoder.encode(rUri, "UTF-8") +
                                "&grant_type=authorization_code"

                        conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
                        val respCode = conn.responseCode
                        if (respCode in 200..299) {
                            val respText = conn.inputStream.bufferedReader().readText()
                            val respJson = JSONObject(respText)
                            val accessToken = respJson.optString("access_token")
                            val expiresIn = respJson.optLong("expires_in", 3600)
                            respJson.put("expires_at", System.currentTimeMillis() + (expiresIn - 300) * 1000)
                            persistOAuthCredentials(respJson, accessToken)

                            DebugLogger.i("Antigravity OAuth token exchanged and saved successfully via $rUri!")
                            sendJson(output, JSONObject().put("status", "ok").put("authenticated", true))
                            return
                        }
                    } catch (e: Exception) {
                        DebugLogger.e("Exchange attempt via $rUri failed", e)
                    }
                }
                sendJson(output, JSONObject().put("error", "Failed to exchange authorization code with Google"), 400)
                return
            }

            // Case 3: Raw Access Token (ya29...) or other raw token string
            val tokenJson = JSONObject().apply {
                put("access_token", input)
                put("token_type", "Bearer")
                put("expires_in", 3600)
                put("expiry_date", System.currentTimeMillis() + 3500 * 1000)
            }
            persistOAuthCredentials(tokenJson, input)

            // Also write raw text to agyTokenFile as fallback
            RuntimeManager.agyTokenFile.parentFile?.mkdirs()
            RuntimeManager.agyTokenFile.writeText(input)

            DebugLogger.i("Saved raw token to agyTokenFile (${RuntimeManager.agyTokenFile.absolutePath}) and credential stores")
            sendJson(output, JSONObject().put("status", "ok").put("authenticated", true))
        } catch (e: Exception) {
            DebugLogger.e("handleAuthToken error", e)
            sendJson(output, JSONObject().put("error", e.message ?: "Authentication error"), 500)
        }
    }

    private fun persistOAuthCredentials(respJson: JSONObject, accessToken: String) {
        val refreshToken = respJson.optString("refresh_token", "")

        // 1. Google Cloud Application Default Credentials (ADC) format
        if (refreshToken.isNotEmpty() || accessToken.isNotEmpty()) {
            val adcObj = JSONObject().apply {
                put("type", "authorized_user")
                put("client_id", "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com")
                put("client_secret", "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")
                if (refreshToken.isNotEmpty()) put("refresh_token", refreshToken)
                if (accessToken.isNotEmpty()) put("access_token", accessToken)
            }
            val adcTargets = listOf(
                File(RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json"),
                File(RuntimeManager.agyConfigDir, "application_default_credentials.json"),
                File(RuntimeManager.homeDir, ".gemini/application_default_credentials.json"),
                File(RuntimeManager.rootfsDir, "root/.config/gcloud/application_default_credentials.json"),
                File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/application_default_credentials.json")
            )
            for (at in adcTargets) {
                try {
                    at.parentFile?.mkdirs()
                    at.writeText(adcObj.toString(2))
                    DebugLogger.i("Wrote ADC credentials: ${at.absolutePath} (${at.length()} bytes)")
                } catch (_: Exception) {}
            }
        }

        // 2. Write raw accessToken to antigravity-oauth-token (legacy compatibility)
        val targets = listOf(
            RuntimeManager.agyTokenFile,
            File(RuntimeManager.agyConfigDir, "antigravity-oauth-token"),
            File(RuntimeManager.homeDir, ".gemini/antigravity-cli/antigravity-oauth-token"),
            File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/antigravity-oauth-token")
        )
        for (t in targets) {
            try {
                t.parentFile?.mkdirs()
                t.writeText(accessToken)
                DebugLogger.i("Wrote raw accessToken to: ${t.absolutePath} (${t.length()} bytes)")
            } catch (_: Exception) {}
        }

        // 3. Write structured JSON stored_token.json for keyring fallback
        val storedTokenObj = JSONObject().apply {
            put("auth_method", "oauth")
            put("user_tier", "FREE")
            put("project_id", "default-cli-project")
            put("access_token", accessToken)
            if (refreshToken.isNotEmpty()) put("refresh_token", refreshToken)
            put("token_type", respJson.optString("token_type", "Bearer"))
            put("expiry_date", respJson.optLong("expiry_date", System.currentTimeMillis() + 3300 * 1000))
        }
        val storedTokenStr = storedTokenObj.toString(2)
        val storedTargets = listOf(
            File(RuntimeManager.agyConfigDir, "stored_token.json"),
            File(RuntimeManager.homeDir, ".gemini/antigravity-cli/stored_token.json"),
            File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/stored_token.json")
        )
        for (st in storedTargets) {
            try {
                st.parentFile?.mkdirs()
                st.writeText(storedTokenStr)
            } catch (_: Exception) {}
        }

        // 4. Ensure settings.json exists with defaults
        val settingsTargets = listOf(
            RuntimeManager.agySettingsFile,
            File(RuntimeManager.homeDir, ".gemini/antigravity-cli/settings.json"),
            File(RuntimeManager.rootfsDir, "root/.gemini/antigravity-cli/settings.json")
        )
        val defaultSettings = JSONObject().apply {
            put("model", "gemini-3.8-flash")
            put("effort", "medium")
        }.toString(2)
        for (sf in settingsTargets) {
            try {
                if (!sf.exists() || sf.length() == 0L) {
                    sf.parentFile?.mkdirs()
                    sf.writeText(defaultSettings)
                }
            } catch (_: Exception) {}
        }

        val credsTargets = listOf(
            File(RuntimeManager.agyConfigDir, "oauth_credentials.json"),
            File(RuntimeManager.homeDir, ".gemini/oauth_creds.json"),
            File(RuntimeManager.homeDir, ".gemini/antigravity/mcp_oauth_tokens.json"),
            File(RuntimeManager.homeDir, ".config/agy/credentials.json"),
            File(RuntimeManager.rootfsDir, "root/.gemini/oauth_creds.json")
        )
        val jsonStr = respJson.toString(2)
        for (ct in credsTargets) {
            try {
                ct.parentFile?.mkdirs()
                ct.writeText(jsonStr)
                DebugLogger.i("Wrote oauth credentials to: ${ct.absolutePath} (${ct.length()} bytes)")
            } catch (_: Exception) {}
        }

        // Create google_accounts.json from id_token email
        try {
            val idToken = respJson.optString("id_token", "")
            if (idToken.isNotEmpty()) {
                val parts = idToken.split(".")
                if (parts.size >= 2) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
                    val claims = JSONObject(payload)
                    val email = claims.optString("email", "")
                    if (email.isNotEmpty()) {
                        val accountsJson = JSONObject().put("active", email)
                        val accountsFile = File(RuntimeManager.homeDir, ".gemini/google_accounts.json")
                        accountsFile.parentFile?.mkdirs()
                        accountsFile.writeText(accountsJson.toString(2))
                        DebugLogger.i("Created google_accounts.json for: $email")
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("Failed to create google_accounts.json", e)
        }
    }

    private fun handleSetWorkspace(payload: JSONObject, output: OutputStream) {
        val ws = payload.optString("workspace", "")
        val dir = File(ws)
        if (dir.exists() && dir.isDirectory) {
            currentWorkspace = dir.absolutePath
            sendJson(output, JSONObject().put("status", "ok").put("workspace", currentWorkspace))
        } else {
            sendJson(output, JSONObject().put("error", "Invalid workspace directory"), 400)
        }
    }

    private fun handleFsTree(query: Map<String, String>, output: OutputStream) {
        val targetPath = query["path"] ?: currentWorkspace
        val target = File(targetPath)
        val includeHidden = query["include_hidden"]?.toBoolean() ?: false

        if (!target.exists()) {
            sendJson(output, JSONObject().put("error", "Path not found"), 404)
            return
        }

        val items = JSONArray()
        val files = target.listFiles() ?: arrayOf()
        files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
            if (includeHidden || !f.name.startsWith(".")) {
                items.put(JSONObject().apply {
                    put("name", f.name)
                    put("path", f.absolutePath)
                    put("is_dir", f.isDirectory)
                    put("size", if (f.isDirectory) 0 else f.length())
                    put("modified", f.lastModified() / 1000.0)
                })
            }
        }
        sendJson(output, JSONObject().put("path", target.absolutePath).put("items", items))
    }

    private fun handleFsFileGet(query: Map<String, String>, output: OutputStream) {
        val targetPath = query["path"] ?: ""
        val target = File(targetPath)
        if (!target.exists() || target.isDirectory) {
            sendJson(output, JSONObject().put("error", "File not found"), 404)
            return
        }
        try {
            val content = target.readText(Charsets.UTF_8)
            val resp = JSONObject().apply {
                put("path", target.absolutePath)
                put("name", target.name)
                put("size", content.length)
                put("content", content)
            }
            sendJson(output, resp)
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleFsFileSave(payload: JSONObject, output: OutputStream) {
        val path = payload.optString("path", "")
        val content = payload.optString("content", "")
        if (path.isEmpty()) {
            sendJson(output, JSONObject().put("error", "Missing path"), 400)
            return
        }
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            sendJson(output, JSONObject().put("status", "ok").put("path", path).put("size", content.length))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleFsCreate(payload: JSONObject, output: OutputStream) {
        val path = payload.optString("path", "")
        val isDir = payload.optBoolean("is_dir", false)
        if (path.isEmpty()) {
            sendJson(output, JSONObject().put("error", "Missing path"), 400)
            return
        }
        try {
            val file = File(path)
            if (isDir) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            sendJson(output, JSONObject().put("status", "ok").put("path", path).put("is_dir", isDir))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleFsDelete(payload: JSONObject, output: OutputStream) {
        val path = payload.optString("path", "")
        val file = File(path)
        if (!file.exists()) {
            sendJson(output, JSONObject().put("error", "File not found"), 404)
            return
        }
        try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
            sendJson(output, JSONObject().put("status", "ok").put("path", path))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleFsRename(payload: JSONObject, output: OutputStream) {
        val src = payload.optString("src", payload.optString("oldPath", ""))
        val dest = payload.optString("dest", payload.optString("newPath", ""))
        val srcFile = File(src)
        val destFile = File(dest)
        if (!srcFile.exists()) {
            sendJson(output, JSONObject().put("error", "Source file not found"), 400)
            return
        }
        try {
            srcFile.renameTo(destFile)
            sendJson(output, JSONObject().put("status", "ok").put("src", src).put("dest", dest))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleGitChanges(query: Map<String, String>, output: OutputStream) {
        val ws = query["workspace"] ?: currentWorkspace
        val wsDir = File(ws)
        val gitDir = File(wsDir, ".git")

        if (!gitDir.exists()) {
            sendJson(output, JSONObject().put("is_git", false).put("workspace", ws).put("changes", JSONArray()))
            return
        }

        try {
            val pb = RuntimeManager.buildProcess(listOf("git", "status", "--porcelain"), wsDir)
            val p = pb.start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()

            val changes = JSONArray()
            text.lines().forEach { line ->
                if (line.length >= 4) {
                    val st = line.substring(0, 2).trim()
                    val path = line.substring(3).trim()
                    changes.put(JSONObject().put("status", st).put("path", path))
                }
            }
            sendJson(output, JSONObject().put("is_git", true).put("workspace", ws).put("changes", changes))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("is_git", false).put("workspace", ws).put("changes", JSONArray()).put("error", e.message))
        }
    }

    private fun handleGitDiff(query: Map<String, String>, output: OutputStream) {
        val ws = query["workspace"] ?: currentWorkspace
        val path = query["path"] ?: ""
        val wsDir = File(ws)
        try {
            val cmd = mutableListOf("git", "diff", "HEAD")
            if (path.isNotEmpty()) {
                cmd.add("--")
                cmd.add(path)
            }
            val pb = RuntimeManager.buildProcess(cmd, wsDir)
            val p = pb.start()
            val diff = p.inputStream.bufferedReader().readText()
            p.waitFor()
            sendJson(output, JSONObject().put("diff", diff).put("path", path))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleSessions(output: OutputStream) {
        val convDir = File(RuntimeManager.homeDir, ".gemini/antigravity-cli/conversations")
        val sessions = JSONArray()
        if (convDir.exists()) {
            convDir.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }?.forEach { f ->
                sessions.put(JSONObject().put("id", f.nameWithoutExtension).put("file", f.name))
            }
        }
        sendJson(output, JSONObject().put("sessions", sessions))
    }

    private fun handleTerminalExec(payload: JSONObject, output: OutputStream) {
        val command = payload.optString("command", "")
        val cwd = payload.optString("cwd", currentWorkspace)
        if (command.isEmpty()) {
            sendJson(output, JSONObject().put("error", "Empty command"), 400)
            return
        }
        try {
            val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
            val pb = RuntimeManager.buildProcess(listOf(shell, "-c", command), File(cwd))
            val p = pb.start()
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            val exitCode = p.waitFor()

            sendJson(output, JSONObject().apply {
                put("stdout", stdout)
                put("stderr", stderr)
                put("exit_code", exitCode)
            })
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleAgentStop(output: OutputStream) {
        activeAgentProcess?.let { p ->
            try {
                p.destroy()
                activeAgentProcess = null
                sendJson(output, JSONObject().put("status", "stopped"))
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sendJson(output, JSONObject().put("status", "no_active_agent"))
    }

    private fun handleAgentStream(payload: JSONObject, output: OutputStream, socket: Socket) {
        var prompt = payload.optString("prompt", "")
        val images = payload.optJSONArray("images")
        if (images != null && images.length() > 0) {
            val imgList = mutableListOf<String>()
            for (i in 0 until images.length()) {
                val img = images.getString(i)
                if (img.isNotEmpty()) imgList.add(img)
            }
            if (imgList.isNotEmpty()) {
                prompt = "[Attached Media: ${imgList.joinToString(", ")}]\n\n$prompt"
            }
        }
        val model = if (payload.has("model")) payload.optString("model", "") else ""
        val effort = if (payload.has("effort")) payload.optString("effort", "") else ""
        val conversationId = if (payload.has("conversation_id")) payload.optString("conversation_id", "") else ""
        val dangerouslySkip = payload.optBoolean("dangerously_skip_permissions", true)

        var agy = RuntimeManager.findAgyBinary()
        if (agy == null || !agy.exists() || agy.length() < 150000000L) {
            DebugLogger.i("Waiting for Antigravity engine extraction to complete...")
            if (RuntimeManager.waitForRuntimeReady(30000L)) {
                agy = RuntimeManager.findAgyBinary()
            }
        }

        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.flush()

        if (prompt.isEmpty()) {
            val errData = JSONObject().put("event", "error").put("error", "Missing prompt").toString()
            output.write("data: $errData\n\n".toByteArray(Charsets.UTF_8))
            output.write("data: {\"event\":\"exit\",\"exit_code\":1}\n\n".toByteArray(Charsets.UTF_8))
            output.flush()
            try { socket.close() } catch (_: Exception) {}
            return
        }

        if (agy == null || !agy.exists()) {
            val errMsg = "⚠️ Antigravity CLI binary not ready.\n\nThe embedded runtime is initializing or extracting assets (requires ~10s on cold boot). Please wait a few moments and try again."
            val errData = JSONObject().put("event", "error").put("error", errMsg).toString()
            output.write("data: $errData\n\n".toByteArray(Charsets.UTF_8))
            output.write("data: {\"event\":\"exit\",\"exit_code\":1}\n\n".toByteArray(Charsets.UTF_8))
            output.flush()
            try { socket.close() } catch (_: Exception) {}
            return
        }

        val cmd = mutableListOf(
            agy.absolutePath,
            "-p", prompt,
            "--output-format", "stream-json"
        )
        if (model.isNotEmpty()) { cmd.add("--model"); cmd.add(model) }
        if (effort.isNotEmpty()) { cmd.add("--effort"); cmd.add(effort) }
        if (conversationId.isNotEmpty()) { cmd.add("--conversation"); cmd.add(conversationId) }
        if (dangerouslySkip) { cmd.add("--dangerously-skip-permissions") }

        var processStarted = false
        val capturedStdout = StringBuilder()
        val capturedStderr = StringBuilder()

        try {
            val activeToken = getValidAccessToken()
            if (activeToken.isNotEmpty()) {
                DebugLogger.i("handleAgentStream: active token verified before spawn (len=${activeToken.length})")
            }

            val pb = RuntimeManager.buildProcess(cmd, File(currentWorkspace))
            val fullCmdStr = pb.command().joinToString(" ")
            DebugLogger.logBanner("SPAWNING AGY CLI AGENT STREAM", mapOf(
                "Command" to fullCmdStr,
                "Workspace" to currentWorkspace,
                "Model" to model.ifEmpty { "default" },
                "Effort" to effort.ifEmpty { "default" },
                "TokenFile Exists" to RuntimeManager.agyTokenFile.exists(),
                "TokenFile Length" to (if (RuntimeManager.agyTokenFile.exists()) RuntimeManager.agyTokenFile.length() else 0),
                "ADC Exists" to File(RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json").exists()
            ))

            pb.redirectErrorStream(false)
            val process = pb.start()
            processStarted = true
            activeAgentProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val errReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))

            val writeSse: (String) -> Unit = { data ->
                try {
                    synchronized(output) {
                        output.write("data: $data\n\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                } catch (_: Exception) {}
            }

            // Forward stderr as SSE events AND to debug logger and Logcat
            executor.execute {
                try {
                    var errLine: String?
                    while (errReader.readLine().also { errLine = it } != null) {
                        errLine?.let { line ->
                            if (line.isNotEmpty()) {
                                capturedStderr.append(line).append("\n")
                                DebugLogger.e("agy stderr: $line")
                                try {
                                    val errData = JSONObject().apply {
                                        put("event", "step_update")
                                        put("step_update", JSONObject().apply {
                                            put("step_index", 0)
                                            put("state", "ACTIVE")
                                            put("step_type", "agent_response")
                                            put("text_delta", "⚠️ $line\n")
                                        })
                                    }.toString()
                                    writeSse(errData)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val stdoutThread = Thread {
                try {
                    var l: String?
                    while (reader.readLine().also { l = it } != null) {
                        val trimmed = l?.trim() ?: continue
                        if (trimmed.isNotEmpty()) {
                            capturedStdout.append(trimmed).append("\n")
                            DebugLogger.d("agy stdout: $trimmed")
                            writeSse(trimmed)
                        }
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            val completedNormally = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (!completedNormally) {
                process.destroyForcibly()
                capturedStderr.append("\n[Process timed out after 120 seconds]")
                DebugLogger.w("agy process timed out after 120s and was killed")
            }
            try { stdoutThread.join(2000) } catch (_: Exception) {}
            var exitCode = if (completedNormally) process.exitValue() else -1
            DebugLogger.recordProcessRun(cmd.joinToString(" "), exitCode, capturedStdout.toString(), capturedStderr.toString())

            if ((exitCode == 159 || exitCode == 139) && RuntimeManager.findProotBinary() != null && !RuntimeManager.requiresProot) {
                DebugLogger.w("agy exited with code $exitCode (seccomp). Retrying with PRoot syscall emulation...")
                try {
                    val pbProot = RuntimeManager.buildProcess(cmd, File(currentWorkspace), forceProot = true)
                    DebugLogger.i("Spawning PRoot process: ${pbProot.command().joinToString(" ")}")
                    pbProot.redirectErrorStream(false)
                    val prootProcess = pbProot.start()
                    activeAgentProcess = prootProcess
                    val prootReader = BufferedReader(InputStreamReader(prootProcess.inputStream, Charsets.UTF_8))
                    val prootErrReader = BufferedReader(InputStreamReader(prootProcess.errorStream, Charsets.UTF_8))
                    executor.execute {
                        try {
                            var errL: String?
                            while (prootErrReader.readLine().also { errL = it } != null) {
                                errL?.let { l ->
                                    if (l.isNotEmpty()) {
                                        capturedStderr.append(l).append("\n")
                                        DebugLogger.e("proot agy stderr: $l")
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    val prootStdoutThread = Thread {
                        try {
                            var pLine: String?
                            while (prootReader.readLine().also { pLine = it } != null) {
                                val l = pLine?.trim() ?: continue
                                if (l.isNotEmpty()) {
                                    capturedStdout.append(l).append("\n")
                                    DebugLogger.d("proot agy stdout: $l")
                                    writeSse(l)
                                }
                            }
                        } catch (_: Exception) {}
                    }.apply { isDaemon = true; start() }

                    val prootDone = prootProcess.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                    if (!prootDone) {
                        prootProcess.destroyForcibly()
                    }
                    try { prootStdoutThread.join(2000) } catch (_: Exception) {}
                    exitCode = if (prootDone) prootProcess.exitValue() else -1
                    DebugLogger.recordProcessRun(cmd.joinToString(" "), exitCode, capturedStdout.toString(), capturedStderr.toString())
                    if (exitCode == 0) {
                        RuntimeManager.requiresProot = true
                    }
                } catch (pe: Exception) {
                    DebugLogger.e("proot fallback execution error", pe)
                }
            }

            if (exitCode != 0) {
                val authError = capturedStderr.contains("authentication required", ignoreCase = true) ||
                        capturedStderr.contains("Run 'agy' to log in", ignoreCase = true) ||
                        capturedStdout.contains("authentication failed", ignoreCase = true)

                if (authError) {
                    DebugLogger.w("agy reported authentication error. Forcing token refresh and retrying once...")
                    val freshToken = getValidAccessToken(forceRefresh = true)
                    if (freshToken.isNotEmpty()) {
                        capturedStderr.setLength(0)
                        capturedStdout.setLength(0)
                        try {
                            val pbRetry = RuntimeManager.buildProcess(cmd, File(currentWorkspace))
                            DebugLogger.i("Spawning retry process with fresh token: ${pbRetry.command().joinToString(" ")}")
                            pbRetry.redirectErrorStream(false)
                            val retryProcess = pbRetry.start()
                            activeAgentProcess = retryProcess
                            val retryReader = BufferedReader(InputStreamReader(retryProcess.inputStream, Charsets.UTF_8))
                            val retryErrReader = BufferedReader(InputStreamReader(retryProcess.errorStream, Charsets.UTF_8))
                            executor.execute {
                                try {
                                    var errL: String?
                                    while (retryErrReader.readLine().also { errL = it } != null) {
                                        errL?.let { l ->
                                            if (l.isNotEmpty()) {
                                                capturedStderr.append(l).append("\n")
                                                DebugLogger.e("retry agy stderr: $l")
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            val retryStdoutThread = Thread {
                                try {
                                    var rLine: String?
                                    while (retryReader.readLine().also { rLine = it } != null) {
                                        val l = rLine?.trim() ?: continue
                                        if (l.isNotEmpty()) {
                                            capturedStdout.append(l).append("\n")
                                            DebugLogger.d("retry agy stdout: $l")
                                            writeSse(l)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }.apply { isDaemon = true; start() }

                            val retryDone = retryProcess.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                            if (!retryDone) {
                                retryProcess.destroyForcibly()
                            }
                            try { retryStdoutThread.join(2000) } catch (_: Exception) {}
                            exitCode = if (retryDone) retryProcess.exitValue() else -1
                            DebugLogger.recordProcessRun(cmd.joinToString(" "), exitCode, capturedStdout.toString(), capturedStderr.toString())
                        } catch (re: Exception) {
                            DebugLogger.e("Retry on auth refresh failed", re)
                        }
                    }
                }
            }

            if (exitCode != 0) {
                val errTrimmed = capturedStderr.toString().trim()
                val detailedMsg = if (errTrimmed.isNotEmpty()) {
                    "Process exited with code $exitCode:\n$errTrimmed"
                } else {
                    "Process exited with code $exitCode"
                }
                val errData = JSONObject().apply {
                    put("event", "error")
                    put("error", detailedMsg)
                    put("exit_code", exitCode)
                    put("stderr", errTrimmed)
                    put("stdout", capturedStdout.toString().takeLast(2000))
                    put("command", cmd.joinToString(" "))
                }.toString()
                writeSse(errData)
            }
            writeSse("{\"event\":\"exit\",\"exit_code\":$exitCode}")
        } catch (e: Exception) {
            DebugLogger.e("agy agent execution error", e)
            val errData = JSONObject().put("event", "error").put("error", e.message).toString()
            try {
                synchronized(output) {
                    output.write("data: $errData\n\n".toByteArray(Charsets.UTF_8))
                    output.write("data: {\"event\":\"exit\",\"exit_code\":1}\n\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            } catch (_: Exception) {}
        } finally {
            activeAgentProcess = null
            if (processStarted) {
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun handleDebugCliTest(payload: JSONObject, output: OutputStream) {
        val testArg = payload.optString("arg", "--version")
        val agy = RuntimeManager.findAgyBinary()
        if (agy == null || !agy.exists()) {
            sendJson(output, JSONObject().apply {
                put("error", "agy binary not found or not ready")
                put("standalone_ready", RuntimeManager.isStandaloneRuntimeReady())
                put("report", DebugLogger.generateFullReport())
            }, 500)
            return
        }

        try {
            val cmd = listOf(agy.absolutePath, testArg)
            val pb = RuntimeManager.buildProcess(cmd, File(currentWorkspace), forceProot = true)
            DebugLogger.logBanner("EXECUTING DEBUG CLI TEST", mapOf("Command" to pb.command().joinToString(" ")))
            val p = pb.start()
            val stdoutBuf = StringBuilder()
            val stderrBuf = StringBuilder()
            val tOut = Thread {
                try {
                    p.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { stdoutBuf.append(it).append("\n") }
                    }
                } catch (_: Exception) {}
            }.apply { start() }
            val tErr = Thread {
                try {
                    p.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { stderrBuf.append(it).append("\n") }
                    }
                } catch (_: Exception) {}
            }.apply { start() }

            val completed = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                p.destroyForcibly()
                stderrBuf.append("\n[Process killed: timed out after 15 seconds]")
            }
            tOut.join(1000)
            tErr.join(1000)

            val exitCode = if (completed) p.exitValue() else -1
            val stdout = stdoutBuf.toString().trim()
            val stderr = stderrBuf.toString().trim()

            DebugLogger.recordProcessRun(cmd.joinToString(" "), exitCode, stdout, stderr)

            sendJson(output, JSONObject().apply {
                put("command", pb.command().joinToString(" "))
                put("exit_code", exitCode)
                put("stdout", stdout)
                put("stderr", stderr)
                put("report", DebugLogger.generateFullReport())
            })
        } catch (e: Exception) {
            DebugLogger.e("handleDebugCliTest error", e)
            sendJson(output, JSONObject().apply {
                put("error", e.message)
                put("report", DebugLogger.generateFullReport())
            }, 500)
        }
    }

    private fun handleDebugReport(output: OutputStream) {
        val report = DebugLogger.generateFullReport()
        sendJson(output, JSONObject().put("report", report))
    }

    private fun handleGetMcps(output: OutputStream) {
        val mcpFile = File(RuntimeManager.homeDir, ".gemini/antigravity-cli/mcp_config.json")
        if (mcpFile.exists()) {
            try {
                sendJson(output, JSONObject(mcpFile.readText()))
                return
            } catch (_: Exception) {}
        }
        val defaultMcps = JSONObject().apply {
            val servers = JSONObject().apply {
                put("chrome-devtools-plugin", JSONObject().apply {
                    put("command", "npx")
                    put("args", JSONArray().put("-y").put("chrome-devtools-mcp@latest"))
                    put("enabled", true)
                })
                put("graphify", JSONObject().apply {
                    put("command", "graphify")
                    put("args", JSONArray().put("serve"))
                    put("enabled", true)
                })
            }
            put("mcpServers", servers)
        }
        sendJson(output, defaultMcps)
    }

    private fun handleSaveMcps(payload: JSONObject, output: OutputStream) {
        try {
            val mcpFile = File(RuntimeManager.homeDir, ".gemini/antigravity-cli/mcp_config.json")
            mcpFile.parentFile?.mkdirs()
            mcpFile.writeText(payload.toString(2))
            sendJson(output, JSONObject().put("status", "ok"))
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun handleGetCustomizations(output: OutputStream) {
        val skills = JSONArray()
        val workflows = JSONArray()
        val rules = JSONArray()

        val skillDirs = listOf(
            File(RuntimeManager.homeDir, ".gemini/config/skills"),
            File(RuntimeManager.homeDir, ".agents/skills"),
            File(currentWorkspace, ".agents/skills")
        )
        for (dir in skillDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.isDirectory }?.forEach { s ->
                    val skillMd = File(s, "SKILL.md")
                    skills.put(JSONObject().apply {
                        put("name", s.name)
                        put("path", s.absolutePath)
                        put("has_spec", skillMd.exists())
                    })
                }
            }
        }
        if (skills.length() == 0) {
            val builtIn = listOf("caveman", "a11y-architect", "tdd-guide", "seo", "security-reviewer", "graphify-windows", "chief-of-staff")
            for (b in builtIn) {
                skills.put(JSONObject().apply {
                    put("name", b)
                    put("path", "builtin/$b")
                    put("has_spec", true)
                })
            }
        }

        val wfDirs = listOf(
            File(RuntimeManager.homeDir, ".gemini/config/global_workflows"),
            File(RuntimeManager.homeDir, ".gemini/workflows"),
            File(currentWorkspace, "workflows")
        )
        for (dir in wfDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile && it.name.endsWith(".md") }?.forEach { w ->
                    workflows.put(JSONObject().apply {
                        put("name", w.nameWithoutExtension)
                        put("path", w.absolutePath)
                    })
                }
            }
        }
        if (workflows.length() == 0) {
            workflows.put(JSONObject().put("name", "ftp-upload").put("command", "/ftp-upload"))
            workflows.put(JSONObject().put("name", "sales-automator").put("command", "/sales-automator"))
            workflows.put(JSONObject().put("name", "goal").put("command", "/goal"))
            workflows.put(JSONObject().put("name", "grill-me").put("command", "/grill-me"))
        }

        rules.put(JSONObject().apply {
            put("name", "caveman")
            put("description", "Ultra-compressed communication mode. Cuts token usage ~75%")
            put("enabled", getSettings().optBoolean("rule_caveman", true))
        })
        val geminiMd = File(currentWorkspace, "GEMINI.md")
        if (geminiMd.exists()) {
            rules.put(JSONObject().apply {
                put("name", "GEMINI.md (Workspace Rule)")
                put("description", "Workspace-level rules and behavioral guidelines")
                put("enabled", true)
            })
        }

        val resp = JSONObject().apply {
            put("skills", skills)
            put("workflows", workflows)
            put("rules", rules)
        }
        sendJson(output, resp)
    }

    private fun handleSaveCustomization(payload: JSONObject, output: OutputStream) {
        val ruleName = payload.optString("name")
        val enabled = payload.optBoolean("enabled", true)
        val settings = getSettings()
        settings.put("rule_$ruleName", enabled)
        saveSettings(settings)
        sendJson(output, JSONObject().put("status", "ok"))
    }

    private fun handleGetLimits(output: OutputStream) {
        val settings = getSettings()
        val resp = JSONObject().apply {
            put("tier", settings.optString("tier", "FREE"))
            put("credit_overcharge", settings.optBoolean("credit_overcharge", false))
            put("limits", JSONObject().apply {
                put("requests_per_day", 1500)
                put("requests_remaining", settings.optInt("requests_remaining", 1340))
                put("tokens_per_minute", 1000000)
                put("tokens_remaining", settings.optInt("tokens_remaining", 948200))
            })
        }
        sendJson(output, resp)
    }

    private fun handleSetLimits(payload: JSONObject, output: OutputStream) {
        val settings = getSettings()
        if (payload.has("credit_overcharge")) {
            settings.put("credit_overcharge", payload.getBoolean("credit_overcharge"))
        }
        saveSettings(settings)
        sendJson(output, JSONObject().put("status", "ok"))
    }

    private fun handleGetBrowserSettings(output: OutputStream) {
        val settings = getSettings()
        val browser = settings.optJSONObject("browser") ?: JSONObject().apply {
            put("enable_browser_tools", true)
            put("javascript_policy", "Request Review")
            put("enable_notifications", true)
            put("enable_sounds", false)
            put("actuation_rules", JSONArray().put("*"))
        }
        sendJson(output, browser)
    }

    private fun handleSetBrowserSettings(payload: JSONObject, output: OutputStream) {
        val settings = getSettings()
        settings.put("browser", payload)
        saveSettings(settings)
        sendJson(output, JSONObject().put("status", "ok"))
    }

    private fun handleUploadImage(payload: JSONObject, output: OutputStream) {
        try {
            val filename = payload.optString("filename", "image_${System.currentTimeMillis()}.png")
            val base64Data = payload.optString("data", "")
            if (base64Data.isEmpty()) {
                sendJson(output, JSONObject().put("error", "Missing base64 data"), 400)
                return
            }
            val cleanB64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = android.util.Base64.decode(cleanB64, android.util.Base64.DEFAULT)

            val attachDir = File(File(currentWorkspace), ".gemini/attachments").apply { mkdirs() }
            val cleanName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(attachDir, "${System.currentTimeMillis()}_$cleanName")
            targetFile.writeBytes(bytes)

            val relPath = targetFile.relativeTo(File(currentWorkspace)).path.replace('\\', '/')
            sendJson(output, JSONObject().apply {
                put("status", "ok")
                put("path", relPath)
                put("abs_path", targetFile.absolutePath)
                put("size", targetFile.length())
            })
        } catch (e: Exception) {
            sendJson(output, JSONObject().put("error", e.message), 500)
        }
    }

    private fun getSettings(): JSONObject {
        val file = RuntimeManager.agySettingsFile
        if (file.exists()) {
            try {
                return JSONObject(file.readText())
            } catch (e: Exception) {}
        }
        return JSONObject().put("model", "Gemini 3.8 Flash (Medium)").put("effort", "medium")
    }

    private fun saveSettings(json: JSONObject) {
        try {
            val file = RuntimeManager.agySettingsFile
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendJson(output: OutputStream, json: JSONObject, code: Int = 200) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, code, if (code == 200) "OK" else "Error", "application/json; charset=utf-8", bytes)
    }

    private fun sendResponse(output: OutputStream, code: Int, status: String, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $code $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isEmpty()) return map
        for (pair in queryString.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            } else if (pair.isNotEmpty()) {
                map[URLDecoder.decode(pair, "UTF-8")] = ""
            }
        }
        return map
    }
}
