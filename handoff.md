# PocketGravity Handoff Document

## 1. Project Goal
Make **PocketGravity** (`com.antigravity.pocketgravity`) a premier mobile IDE for Google Antigravity, running on a stable, proven three-tier architecture: **Termux + Python Bridge Daemon + Android Frontend App**.
- **Execution Environment**: Termux (with Ubuntu PRoot) hosting the official Google Antigravity CLI (`agy 1.1.27 aarch64`).
- **Bridge Daemon**: Python 3 HTTP & SSE daemon (`bridge/server.py` on `127.0.0.1:8765`).
- **Frontend App**: Native Android app communicating over localhost HTTP/SSE.
- **Authentication**: Google OAuth credentials stored in standard `~/.gemini/antigravity-cli/` paths.
- **UI & Protocol**: Full ACP (Agent Client Protocol) rendering — thinking chips (`thought_delta`), tool execution badges (`toolCall`), and streaming tokens (`textDelta`).

---

## 2. Key Architecture & Current State

### A. Termux & Ubuntu Linux Environment
- Termux provides the POSIX environment with full DNS resolution and standard Linux syscalls, avoiding Android Zygote seccomp traps (`SIGSYS 159`).
- `proot-distro login ubuntu` runs the glibc userspace without requiring custom kernel patches.
- The official `agy` binary (`v1.1.27 aarch64`) executes directly inside this environment.

### B. Python Bridge Daemon (`bridge/server.py` / `bridge/start.sh`)
- Automated by `bridge/start.sh`, which provisions `proot-distro`, Ubuntu rootfs, and Python 3 if missing.
- Listens on `127.0.0.1:8765`.
- Serves:
  - `/api/health`: Status of Termux, Ubuntu, `agy` binary, OAuth token, and workspace.
  - `/api/agent/stream`: Streams responses from `agy -p <prompt> --output-format stream-json`.
  - `/api/terminal/exec`: Shell command execution in workspace.
  - `/api/fs/*`: File tree, read, write, create, delete, rename.
  - `/api/git/*`: Git status and unified diffs.
  - `/api/auth/*`: Google OAuth PKCE and credential management.

### C. Android Frontend (`app/`)
- Native Material 3 UI.
- Automatically connects to `http://127.0.0.1:8765`.
- Exports bridge scripts to `/sdcard/Download/frontendcli/` on launch.
- Diagnostics panel provides 1-tap "Copy Termux Command" and "Open Termux" actions.

---

## 3. Key Files
- [bridge/server.py](file:///c:/Users/adamk/Downloads/websites/clifrontend/bridge/server.py) — Python bridge daemon.
- [bridge/start.sh](file:///c:/Users/adamk/Downloads/websites/clifrontend/bridge/start.sh) — Provisioning and daemon lifecycle management.
- [BridgeClient.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/BridgeClient.kt) — Android HTTP/SSE client.
- [AgentPanel.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/ui/AgentPanel.kt) — ACP protocol chat canvas.
- [DiagnosticsPanel.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/ui/DiagnosticsPanel.kt) — System health & Termux launcher.
