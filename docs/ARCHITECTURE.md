# CLIFrontend Architecture
 
## 1. System Overview
 
CLIFrontend is a standalone native Android mobile IDE and frontend for the Google Antigravity CLI (`agy`) running directly on Android (`aarch64`) with an embedded zero-dependency Kotlin daemon.
 
```
+-------------------------------------------------------------+
|                     Android Phone                           |
|                                                             |
|  +-------------------------------------------------------+  |
|  |                 CLIFrontend Native App                |  |
|  |  * Modern Jetpack Compose UI (Material 3 Dark)        |  |
|  |  * File Explorer & Tree (Real filesystem)             |  |
|  |  * Code Editor (Syntax highlighting, multi-tab, save) |  |
|  |  * Agent Chat Canvas (Streaming NDJSON deltas)        |  |
|  |  * Changes & Diff Viewer (Unified diffs, additions)   |  |
|  |  * Interactive Terminal (ANSI VT100 / Shell PTY)      |  |
|  |  * Model & Reasoning Selector (Live agy catalog)      |  |
|  |  * Diagnostics & Setup Wizard                         |  |
|  +---------------------------|---------------------------+  |
|                              | Localhost IPC (HTTP/SSE)     |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |             Embedded Kotlin Bridge Daemon (:8765)     |  |
|  |  * Built-in Android Service (Zero Python overhead)    |  |
|  |  * REST: /api/fs, /api/models, /api/auth, /api/git    |  |
|  |  * SSE: /api/agent/stream (NDJSON streaming)          |  |
|  +---------------------------|---------------------------+  |
|                              | Subprocess / JNI             |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |           Micro Runtime Engine (< 20 MB)              |  |
|  |  * Shell Environment (Busybox POSIX)                  |  |
|  |  * Embedded PRoot / glibc loader                      |  |
|  |  * Antigravity CLI (agy 1.1.27)                       |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---

## 2. IPC & Bridge Design

Because PRoot and Termux share the Android Linux kernel network namespace, the local loopback interface (`127.0.0.1`) is accessible between Android applications and processes running inside Termux/PRoot without requiring root or custom kernel drivers.

### Endpoints
1. **`GET /api/health`**:
   Diagnostics reporting status of Termux, Ubuntu, Antigravity binary, OAuth token, and current workspace.
2. **`GET /api/models`**:
   Dynamically queries `agy models` and returns model IDs and labels.
3. **`GET /api/auth/status` & `POST /api/auth/login`**:
   Detects `~/.gemini/antigravity-cli/antigravity-oauth-token` or starts the OAuth flow.
4. **`GET /api/fs/tree` & `GET /api/fs/file` & `POST /api/fs/file`**:
   Direct filesystem access with path normalization and validation against directory traversal attacks.
5. **`GET /api/git/changes` & `GET /api/git/diff`**:
   Returns modified, added, deleted files and unified diffs.
6. **`WS /ws/agent`**:
   Streams agent turns via `agy --input-format stream-json --output-format stream-json`.
7. **`WS /ws/terminal`**:
   Interactive pseudo-terminal (PTY) session running `/bin/bash` in the current workspace.

---

## 3. Security Considerations
- **Localhost Binding**: All bridge sockets listen strictly on `127.0.0.1`.
- **Zero Credential Exposure**: Google OAuth tokens remain strictly inside `~/.gemini/antigravity-cli/antigravity-oauth-token`.
- **Path Sanitization**: All file requests are verified to reside within allowed workspace roots.
- **Permission Model**: Respects Antigravity's approval flow and user permission prompts.
