# Termux Bridge Implementation & Integration

## 1. Environment Detection Strategy

The Android application determines environment state across five tiers:

| Tier | Detection Mechanism | Status Indicator |
|---|---|---|
| **1. Termux** | Intent package check (`com.termux`) & filesystem check (`/data/data/com.termux`) | Termux Installed / Missing |
| **2. Ubuntu PRoot** | Path check for container rootfs & distro configuration | Ubuntu Container Found / Missing |
| **3. Antigravity CLI** | Check for `/root/.local/bin/agy` or `agy` in PATH | Binary Detected (`v1.1.27`) |
| **4. Authentication** | Presence & validity of `~/.gemini/antigravity-cli/antigravity-oauth-token` | Authenticated / Login Required |
| **5. Bridge Service** | HTTP health probe to `http://127.0.0.1:8765/api/health` | Connected / Offline |

---

## 2. Zero-Setup Experience

1. **Already Running**:
   If the bridge daemon is active in Ubuntu, the app detects it within 100ms, skips all setup screens, and opens the active project workspace immediately.

2. **First Run or Cold Start**:
   - The app detects whether Termux is installed.
   - If Termux is installed but the bridge is not yet active, the app presents a one-tap "Start Bridge in Termux" button that sends an Intent to Termux or copies the minimal command:
     ```bash
     proot-distro login ubuntu -- /downloads/clifrontend/bridge/start.sh
     ```
   - We also provide an auto-starting service script for Termux's `~/.bashrc` or `~/.termux/boot/` so that whenever Termux starts, the bridge daemon is automatically ready.

---

## 3. Bridge Process Details

The bridge daemon (`bridge/server.py`) is written in standard Python 3 (available in both Ubuntu and Termux):
- Uses standard library `http.server` and lightweight async WebSocket protocol.
- Handles pseudo-terminal allocation (`pty.fork()` or `openpty()`) for genuine interactive shell execution.
- Bridges stdin/stdout/stderr for `agy` sessions with `--input-format stream-json --output-format stream-json`.
- Exposes clean REST endpoints for file browsing, reading, saving, and Git diff generation.
