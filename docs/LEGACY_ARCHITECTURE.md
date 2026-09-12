# Legacy Architecture: Termux + PRoot Ubuntu Baseline

## 1. Executive Summary

Before transitioning to a 100% standalone APK, the working prototype of CLIFrontend relied on an external multi-tier stack:

```
+-------------------------------------------------------------+
|                      Android Host OS                        |
|                                                             |
|  +-------------------------------------------------------+  |
|  |             Android Native Frontend App               |  |
|  |   Package: com.antigravity.clifrontend (or pocketgravity) |  |
|  |   UI: Agent Panel, Editor, Explorer, Changes, Term    |  |
|  +---------------------------|---------------------------+  |
|                              | HTTP/SSE (127.0.0.1:8765)    |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |            Termux Environment (com.termux)            |  |
|  |   PRoot Distro Manager (proot-distro login ubuntu)   |  |
|  |   Termux Wake-Lock                                    |  |
|  +---------------------------|---------------------------+  |
|                              | Subshell container           |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |                    Ubuntu PRoot                       |  |
|  |   glibc Userspace, standard root filesystem           |  |
|  |   Python 3 Daemon (bridge/server.py via start.sh)     |  |
|  |   Port: 8765                                          |  |
|  +---------------------------|---------------------------+  |
|                              | Process execution (fork/exec)|
|                              v                              |
|  +-------------------------------------------------------+  |
|  |            Real Antigravity CLI Binary (agy)          |  |
|  |   Version: 1.1.27 (aarch64 Linux ELF Rust binary)    |  |
|  |   Path: /root/.local/bin/agy                          |  |
|  |   Config: ~/.gemini/antigravity-cli/                  |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---
 
## 2. Component Breakdown

### A. The Android Frontend (`app/`)
- Native Android application built with Kotlin.
- Features:
  - **Agent Chat**: ACP / stream-json viewer rendering reasoning chips, tool calls, and text deltas.
  - **File Explorer**: Tree view navigating workspace files.
  - **Code Editor**: Syntax-highlighted code editor with file read/write APIs.
  - **Git Diff & Changes**: Shows `git status` changes and colored unified diffs.
  - **Terminal**: Interactive shell execution.
  - **Diagnostics & Settings**: Model selector, reasoning effort selector, and authentication wizard.
- Communicated exclusively over localhost HTTP and Server-Sent Events (SSE) to `http://127.0.0.1:8765`.

### B. Bridge Daemon Management (`bridge/start.sh`)
- Executed inside the Ubuntu PRoot container or directly in Termux:
  ```bash
  proot-distro login ubuntu -- /downloads/clifrontend/bridge/start.sh start
  ```
- Capabilities:
  - Discovered Python 3 (`python3` or `python`).
  - Auto-discovered `server.py` across standard paths or downloaded it from repository if missing.
  - Managed PID (`$HOME/.frontendcli/bridge.pid`) and logs (`$HOME/.frontendcli/bridge.log`).
  - Acquired `termux-wake-lock` to avoid background suspension by Android doze mode.
  - Health check verification loop before confirming successful launch.

### C. Bridge Server Daemon (`bridge/server.py`)
- Zero-dependency Python 3 HTTP daemon (`ThreadingMixIn`, `HTTPServer`, `BaseHTTPRequestHandler`).
- Listened on `127.0.0.1:8765`.
- Endpoints:
  - `GET /api/health`: Detection of Termux, Ubuntu `/etc/os-release`, `agy` binary existence, and OAuth token status.
  - `GET /api/models`: Ran `agy models` to fetch live model catalog from Google.
  - `GET /POST /api/effort`: Managed reasoning effort (`low`, `medium`, `high`) in `~/.gemini/antigravity-cli/settings.json`.
  - `POST /api/model`: Set selected model in `settings.json`.
  - `GET /api/auth/status`: Verified existence and size of OAuth token file (`~/.gemini/antigravity-cli/antigravity-oauth-token`).
  - `POST /api/auth/login`: Triggered `agy auth login` or initiated Google OAuth 2.0 PKCE flow.
  - `POST /api/auth/token`: Exchanged OAuth authorization codes or persisted access tokens.
  - `POST /api/auth/logout`: Cleared local session credentials.
  - `GET /POST /api/workspace`: Read/updated current working directory.
  - `GET /api/fs/tree`: Directory tree listing with file sizes, directory flags, and mtimes.
  - `GET /POST /api/fs/file`: File reading and writing.
  - `POST /api/fs/create`, `/delete`, `/rename`: File manipulation.
  - `GET /api/git/changes`: Subprocess invocation of `git status --porcelain`.
  - `GET /api/git/diff`: Subprocess invocation of `git diff HEAD [-- path]`.
  - `GET /api/sessions`: Listed past conversation JSON files in `~/.gemini/antigravity-cli/conversations/`.
  - `POST /api/terminal/exec`: Ran arbitrary shell commands in the container workspace.
  - `POST /api/agent/stream`: Spawned `agy -p "<prompt>" --output-format stream-json --dangerously-skip-permissions` and piped stdout lines as SSE events (`data: <line>\n\n`).
  - `POST /api/agent/stop`: Terminated the active `agy` subprocess.

### D. The Real Antigravity CLI Binary (`agy`)
- Official Google Antigravity binary (`v1.1.27`).
- Native 64-bit ARM Linux ELF binary (`aarch64`), compiled from Rust (~200 MB uncompressed).
- Dynamically linked against standard glibc (`libc.so.6`, `libm.so.6`, `libdl.so.2`, `libpthread.so.0`, `libresolv.so.2`, `librt.so.1`).
- Did **not** require Python, Node.js, or complex desktop dependencies.
- Required glibc dynamic linker (`ld-linux-aarch64.so.1`) and CA root certificates for TLS (`SSL_CERT_FILE`).

---

## 3. Why the Legacy Implementation Succeeded
1. **Separation of Concerns**: The Android app functioned as a clean, responsive UI layer without needing to manage Linux process quirks directly.
2. **Standard POSIX Environment**: Running inside Ubuntu PRoot provided glibc, DNS resolution (`/etc/resolv.conf`), CA certificates (`/etc/ssl/certs/ca-certificates.crt`), and a standard `/bin/sh` shell.
3. **Official Antigravity Protocol**: By invoking `agy` with `--output-format stream-json`, all thinking deltas, tool executions, and file edits were executed by Google's official client library, completely avoiding `403 SUBSCRIPTION_REQUIRED` errors caused by raw API gateway rejections.

---

## 4. Why It Must Be Embedded
- **High Friction**: Required user to manually install Termux, run `pkg install proot-distro`, install Ubuntu, download `start.sh`, run the bridge in the background, and manage port forwarding.
- **Fragility**: Android OEM battery killers regularly killed Termux in the background.
- **Security**: Public localhost port was exposed to all other apps on the device unless guarded by app-private tokens.
- **Standalone Goal**: The target is a **single APK** that bundles the runtime, extracts the required glibc dynamic loader, configures CA certs and environment variables, and manages the daemon internally as an Android Service.
