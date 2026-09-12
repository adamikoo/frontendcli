# CLIFrontend — Antigravity Mobile IDE

**CLIFrontend** (PocketGravity) is a modern mobile IDE and native Android frontend for the official **Google Antigravity CLI (`agy`)**, powered by a robust three-tier architecture: **Termux + Bridge Daemon + Android Native App**.

```
+-------------------------------------------------------------+
|                     Android Phone                           |
|                                                             |
|  +-------------------------------------------------------+  |
|  |                 CLIFrontend Native App                |  |
|  |  * Agent Chat Canvas (ACP / stream-json streaming)    |  |
|  |  * Multi-Tab Code Editor (Line numbers, syntax, save) |  |
|  |  * Real Filesystem Explorer (Create, rename, delete)   |  |
|  |  * Unified Git Changes & Diff Viewer                  |  |
|  |  * Interactive Terminal (ANSI VT100 / Shell PTY)      |  |
|  |  * Dynamic Model & Thinking Effort Selector           |  |
|  |  * Diagnostics & Google OAuth PKCE Wizard             |  |
|  +---------------------------|---------------------------+  |
|                              | Localhost IPC (HTTP/SSE)     |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |             Python Bridge Daemon (127.0.0.1:8765)     |  |
|  |  * Runs in Termux or Ubuntu PRoot (server.py)         |  |
|  |  * Subprocess management & PTY streaming              |  |
|  |  * REST: /api/fs, /api/models, /api/auth, /api/git    |  |
|  |  * SSE: /api/agent/stream (Official stream-json)      |  |
|  +---------------------------|---------------------------+  |
|                              | Linux Execution Environment  |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |             Termux / Ubuntu PRoot Linux               |  |
|  |  * Full POSIX Linux environment & glibc runtime       |  |
|  |  * System DNS resolution & CA root certificates       |  |
|  |  * Python 3 & Bash shell (/bin/bash)                  |  |
|  |  * Real Antigravity CLI binary (agy 1.1.27 aarch64)   |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---

## ⚡ Quick Start (Termux + Bridge + App)

1. **Install Prerequisites**:
   - Install **Termux** on your Android device (from F-Droid or GitHub Releases).
   - Install `CLIFrontend.apk`.
2. **Start the Bridge in Termux**:
   Run this single command inside Termux:
   ```bash
   pkg install -y python curl && curl -sL https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/start.sh -o ~/start.sh && bash ~/start.sh
   ```
   *(Or if files are exported to storage: `bash /sdcard/Download/frontendcli/start.sh`)*
3. **Open CLIFrontend**:
   - The app automatically connects to `http://127.0.0.1:8765`.
   - Sign in with Google via the OAuth PKCE flow (or paste an existing `agy` token).
   - Chat with Antigravity, edit code, run shell commands, and inspect Git diffs directly on mobile!

---

## 🚀 Key Features

- **Genuine Antigravity CLI**: Executes the official Google Antigravity binary (`1.1.27 aarch64`) compiled in Rust. No mocks, no simulated responses, and no fake API endpoints.
- **Robust Linux Sandbox**: Powered by Termux and PRoot glibc, guaranteeing full DNS resolution, dynamic library loading, and POSIX signal handling without Android sandbox seccomp traps.
- **403 Errors Eliminated**: Communicates exclusively through the official `agy` binary protocol, completely avoiding consumer account rejections caused by raw `cloudcode-pa` calls.
- **Live Model Discovery**: Queries `agy models` dynamically from Google's catalog (`Gemini 3.8 Flash`, `Gemini 3.7 Flash`, `Gemini 3.1 Pro`, `Claude Sonnet 4.6`, `GPT-OSS 120B`).
- **Thinking / Reasoning Effort**: Direct selector for `low`, `medium`, or `high` reasoning effort.
- **ACP / Stream-JSON UI**: Renders live thinking chips (`thought_delta`), tool execution badges (`write_to_file`, `replace_file_content`, `run_command`), and streaming text deltas.
- **Full Code Editor**: Tabbed editing, line numbering, dirty state tracking, and quick programmer accessory keys.
- **File Explorer**: Full project browsing, file creation, deletion, and renaming in your project workspace.
- **Git Diff Viewer**: Unified syntax-highlighted diffs for tracked workspace repositories.
- **Diagnostics Screen**: Health monitoring of Termux, Ubuntu container, bridge connectivity, `agy` binary version, and Google OAuth credentials.

---

## 🛠️ Building From Source

### Option A: Windows (PowerShell)
```powershell
.\scripts\build.ps1
```

### Option B: Linux / CI
```bash
./scripts/build.sh
```

The output release APK is generated at:
```
CLIFrontend.apk
```

---

## 📚 Technical Documentation

- [Architecture Overview](docs/ARCHITECTURE.md)
- [Termux Bridge Implementation & Setup](docs/TERMUX_BRIDGE.md)
- [Known-Good Baseline Protocol](docs/KNOWN_GOOD_BASELINE.md)
- [Runtime Requirements Analysis](docs/RUNTIME_REQUIREMENTS.md)
- [Embedded Runtime Analysis & Retrospective](docs/EMBEDDED_RUNTIME.md)
- [Antigravity Interface & Protocol](docs/ANTIGRAVITY_INTERFACE.md)
- [Error Root Cause Forensics](docs/ERRORS.md)
- [Security Architecture](docs/SECURITY.md)
- [Testing & Verification](docs/TESTING.md)
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md)
