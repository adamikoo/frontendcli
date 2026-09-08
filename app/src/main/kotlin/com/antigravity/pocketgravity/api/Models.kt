package com.antigravity.pocketgravity.api

import org.json.JSONObject
import org.json.JSONArray

data class HealthResponse(
    val status: String,
    val termux: Boolean,
    val ubuntu: Boolean,
    val antigravityInstalled: Boolean,
    val antigravityVersion: String?,
    val antigravityPath: String?,
    val authenticated: Boolean,
    val tokenFile: String?,
    val workspace: String,
    val model: String,
    val effort: String,
    val timestamp: Double
) {
    companion object {
        fun fromJson(json: JSONObject): HealthResponse {
            val agy = json.optJSONObject("antigravity")
            val auth = json.optJSONObject("auth")
            return HealthResponse(
                status = json.optString("status", "unknown"),
                termux = json.optBoolean("termux", false),
                ubuntu = json.optBoolean("ubuntu", false),
                antigravityInstalled = agy?.optBoolean("installed", false) ?: false,
                antigravityVersion = agy?.optString("version", null),
                antigravityPath = agy?.optString("path", null),
                authenticated = auth?.optBoolean("authenticated", false) ?: false,
                tokenFile = auth?.optString("token_file", null),
                workspace = json.optString("workspace", "/root"),
                model = json.optString("model", "Gemini 3.8 Flash (Medium)"),
                effort = json.optString("effort", "medium"),
                timestamp = json.optDouble("timestamp", 0.0)
            )
        }
    }
}

data class ModelItem(
    val id: String,
    val name: String,
    val selected: Boolean
)

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Double
)

data class GitChange(
    val status: String,
    val path: String
)

data class TerminalExecResponse(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

data class ChatMessage(
    val id: String,
    val role: Role,
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var status: MessageStatus = MessageStatus.DONE,
    var toolName: String? = null,
    var tokenUsage: String? = null
) {
    enum class Role { USER, AGENT, TOOL, SYSTEM }
    enum class MessageStatus { ACTIVE, DONE, ERROR }
}
