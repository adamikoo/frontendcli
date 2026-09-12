# CLIFrontend Architecture

## 1. System Overview

CLIFrontend operates as a robust three-tier mobile development environment:
1. **Frontend App (`app/`)**: Native Android application providing a rich, responsive Material mobile IDE UI.
2. **Bridge Daemon (`bridge/server.py`)**: Lightweight, zero-dependency Python daemon listening on `127.0.0.1:8765`.
3. **Linux Environment (Termux / PRoot Ubuntu)**: POSIX-compliant userspace supplying glibc runtime, DNS networking, bash shell, and the official Google Antigravity CLI (`agy`).

```
+-------------------------------------------------------------+
|                     Android Phone                           |
|                                                             |
|  +-------------------------------------------------------+  |
|  |                 CLIFrontend Native App                |  |
|  |  * Modern Android UI (Material 3 Dark)                |  |
|  |  * File Explorer & Tree (Real filesystem)             |  |
|  |  * Code Editor (Line numbers, multi-tab, save)        |  |
|  |  * Agent Chat Canvas (Streaming NDJSON deltas / ACP)  |  |
|  |  * Changes & Diff Viewer (Unified diffs, additions)   |  |
|  |  * Interactive Terminal (ANSI VT100 / Shell PTY)      |  |
|  |  * Model & Reasoning Selector (Live agy catalog)      |  |
|  |  * Diagnostics & Setup Wizard                         |  |
|  +---------------------------|---------------------------+  |
|                              | Localhost IPC (HTTP/SSE)     |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |             Python Bridge Daemon (:8765)              |  |
|  |  * Runs in Termux or Ubuntu PRoot (server.py)         |  |
|  |  * Subprocess management & PTY streaming              |  |
|  |  * REST: /api/fs, /api/models, /api/auth, /api/git    |  |
|  |  * SSE: /api/agent/stream (NDJSON streaming)          |  |
|  +---------------------------|---------------------------+  |
|                              | Subprocess Execution         |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |             Termux / Ubuntu Linux Runtime             |  |
|  |  * Full glibc Userspace & dynamic linker              |  |
|  |  * Standard Linux DNS resolver (/etc/resolv.conf)     |  |
|  |  * CA root certificates (/etc/ssl/certs)              |  |
|  |  * Official Antigravity CLI (agy 1.1.27 aarch64)      |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---

## 2. IPC & Bridge Design

Because Termux shares the Android Linux kernel network namespace, the local loopback interface (`127.0.0.1`) is directly accessible between the Android application and processes running inside Termux or PRoot Ubuntu without requiring root permissions.

### Primary Endpoints
1. **`GET /api/health`**:
   Reports operational health of Termux, Ubuntu container, Antigravity binary, OAuth token, and active workspace path.
2. **`GET /api/models`**:
   Dynamically queries `agy models` from Google and returns model IDs and labels.
3. **`GET /api/effort` & `POST /api/effort`**:
   Reads and updates reasoning effort (`low`, `medium`, `high`) in `settings.json`.
4. **`GET /api/auth/status` & `POST /api/auth/login`**:
   Detects `~/.gemini/antigravity-cli/antigravity-oauth-token` or initiates the Google OAuth 2.0 PKCE login flow.
5. **`POST /api/auth/token`**:
   Persists exchanged tokens into standard Antigravity configuration paths.
6. **`POST /api/auth/logout`**:
   Cleans all token and credential files across sandbox and Termux directories.
7. **`GET /api/fs/tree` & `GET /POST /api/fs/file`**:
   Direct filesystem browsing, reading, saving, creating, and deleting within the workspace.
8. **`GET /api/git/changes` & `GET /api/git/diff`**:
   Returns modified, added, and deleted files with unified Git diffs.
9. **`POST /api/terminal/exec`**:
   Executes shell commands in the workspace environment and streams ANSI-formatted stdout/stderr.
10. **`POST /api/agent/stream`**:
    Spawns `agy -p "<prompt>" --output-format stream-json --dangerously-skip-permissions` and pipes NDJSON output directly to the UI as Server-Sent Events (SSE).
11. **`POST /api/agent/stop`**:
    Immediately halts the active `agy` agent turn.

---

## 3. Why the Three-Tier Architecture?

- **Sandbox Security Restrictions in Android**: Direct execution of unbundled Linux glibc binaries inside Android app sandboxes (`untrusted_app`) is blocked by Android seccomp filters (`SIGSYS` exit 159 on `rseq`), W^X memory restrictions, and lack of system `/etc/resolv.conf`.
- **Termux Privileges**: Termux runs as a full terminal emulator environment with standard Linux permissions, full DNS resolution, dynamic library support, and background wake-lock capability (`termux-wake-lock`).
- **Optimal Separation of Concerns**: Python daemon handles low-level process spawning and POSIX signals; Android app provides the hardware-accelerated mobile touch interface.

---

## 4. Security Considerations

- **Localhost Binding**: Bridge daemon binds strictly to `127.0.0.1:8765`. It never exposes a public network interface.
- **Credential Protection**: Google OAuth tokens remain strictly inside `~/.gemini/antigravity-cli/` protected by Linux permissions.
- **Path Sanitization**: Workspace operations are validated to prevent directory traversal outside designated project roots.
