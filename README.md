# CLIFrontend — Standalone Antigravity Mobile IDE

**CLIFrontend** (PocketGravity) is a 100% self-contained, one-APK standalone mobile IDE and native Android frontend for the real **Google Antigravity CLI (`agy`)**.

It runs the real Antigravity binary directly on the Android Linux kernel (`aarch64`) without requiring Termux, Termux:API, Ubuntu, PC tethering, or any manually started services.

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
|  |             Embedded Kotlin Bridge Daemon (:8765)     |  |
|  |  * Native Android Foreground Service                  |  |
|  |  * REST: /api/fs, /api/models, /api/auth, /api/git    |  |
|  |  * SSE: /api/agent/stream (Official stream-json)      |  |
|  +---------------------------|---------------------------+  |
|                              | Subprocess Execution         |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |           Embedded Linux Runtime (< 3 MB glibc)       |  |
|  |  * Glibc dynamic linker (ld-linux-aarch64.so.1)       |  |
|  |  * Mozilla root CA certificates (ca-certificates.crt)  |  |
|  |  * POSIX shell (/system/bin/sh)                       |  |
|  |  * Real Antigravity CLI binary (agy 1.1.27 aarch64)   |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---

## ⚡ Zero-Setup User Experience

1. **Install** `CLIFrontend.apk`.
2. **Open** CLIFrontend.
3. The app automatically extracts its embedded glibc loader, CA certificates, and `agy` binary on first launch.
4. Sign in with Google via the standard OAuth PKCE flow if not already authenticated.
5. Send prompts, edit code, run terminal commands, and inspect Git diffs directly on mobile!

---

## 🚀 Key Features

- **Genuine Antigravity CLI**: Executes the official Google Antigravity binary (`1.1.27`) compiled in Rust. No mocks, no simulated responses, and no fake API endpoints.
- **Zero-Dependency One-APK**: Everything needed is bundled inside `CLIFrontend.apk`.
- **403 Errors Eliminated**: Communicates exclusively through the official `agy` binary protocol, completely avoiding consumer account rejections caused by raw `cloudcode-pa` calls.
- **Live Model Discovery**: Queries `agy models` dynamically from Google's catalog (`Gemini 3.8 Flash`, `Gemini 3.7 Flash`, `Gemini 3.1 Pro`, `Claude Sonnet 4.6`, `GPT-OSS 120B`).
- **Thinking / Reasoning Effort**: Direct selector for `low`, `medium`, or `high` reasoning effort.
- **ACP / Stream-JSON UI**: Renders live thinking chips (`thought_delta`), tool execution badges (`write_to_file`, `replace_file_content`, `run_command`), and streaming text deltas.
- **Full Code Editor**: Tabbed editing, line numbering, dirty state tracking, and quick programmer accessory keys.
- **File Explorer**: Full project browsing, file creation, deletion, and renaming in app-private storage.
- **Git Diff Viewer**: Unified syntax-highlighted diffs for tracked workspace repositories.
- **Diagnostics Screen**: Health monitoring of runtime extraction, glibc loader, CLI version, OAuth credentials, and bridge connection.

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
- [Legacy Architecture Baseline](docs/LEGACY_ARCHITECTURE.md)
- [Known-Good Baseline Protocol](docs/KNOWN_GOOD_BASELINE.md)
- [Runtime Requirements Analysis](docs/RUNTIME_REQUIREMENTS.md)
- [Embedded Runtime Design](docs/EMBEDDED_RUNTIME.md)
- [Antigravity Interface & Protocol](docs/ANTIGRAVITY_INTERFACE.md)
- [Error Root Cause Forensics](docs/ERRORS.md)
- [Security Architecture](docs/SECURITY.md)
- [Testing & Verification](docs/TESTING.md)
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md)
