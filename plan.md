# Pocket Gravity — Project Plan & Architecture Roadmap

## 1. Core Architecture: Termux + Bridge + App

Pocket Gravity utilizes the proven, production-grade three-tier architecture:

```
[CLIFrontend Android App] <--(HTTP/SSE 127.0.0.1:8765)--> [Python Bridge Daemon] <--(POSIX Subprocess)--> [agy CLI in Termux/Ubuntu]
```

### Why Termux + Bridge:
1. **Linux Kernel Compatibility**: Termux avoids Android's Zygote seccomp traps (`SIGSYS` 159 on glibc syscalls).
2. **Standard Networking**: Standard POSIX `/etc/resolv.conf` and DNS lookups allow `agy` to resolve Google API endpoints reliably.
3. **Official Antigravity Protocol**: Connects directly via the official `agy` binary with `--output-format stream-json`, eliminating 403 `SUBSCRIPTION_REQUIRED` errors.

---

## 2. Component Responsibilities

| Component | Responsibility | Location |
|---|---|---|
| **Android Native App** | Material 3 UI, Agent chat canvas, code editor, file explorer, git changes, ANSI terminal | `app/` |
| **Bridge Daemon** | Localhost HTTP/SSE server, subprocess manager, OAuth handler, filesystem & git APIs | `bridge/server.py` & `bridge/start.sh` |
| **Linux Environment** | Termux host environment with Ubuntu PRoot container providing standard glibc & `agy` | `com.termux` |

---

## 3. Deployment & Execution Checklist

- [x] **Python Bridge Server (`bridge/server.py`)**: Complete with `/api/health`, `/api/models`, `/api/agent/stream`, `/api/fs/*`, `/api/git/*`, `/api/auth/*`.
- [x] **Automated Setup Script (`bridge/start.sh`)**: Installs `proot-distro`, Ubuntu container, Python 3, acquires wake-lock, and mounts `/sdcard`.
- [x] **Asset Exporting**: App automatically exports `start.sh` and `server.py` to `/sdcard/Download/frontendcli/` on launch.
- [x] **Google OAuth PKCE**: Full token exchange and persistence to `~/.gemini/antigravity-cli/`.
- [x] **Comprehensive Token Cleanup on Logout**: Deletes all credential files across sandbox and Termux directories.
- [x] **Android Client Integration**: App auto-detects `127.0.0.1:8765`, with 1-tap launcher helpers in Diagnostics for Termux.
