# Termux Bridge Implementation & Integration

## 1. Overview

The Termux Bridge provides a local HTTP and SSE daemon running on `127.0.0.1:8765` inside Termux or Ubuntu PRoot. It bridges the native Android app UI directly to the official Google Antigravity CLI (`agy`) binary.

---

## 2. One-Command Setup & Launch

Inside Termux, execute:

```bash
pkg install -y python curl && curl -sL https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/start.sh -o ~/start.sh && bash ~/start.sh
```

Or, if the app has already exported the scripts to storage:
```bash
termux-setup-storage
bash /sdcard/Download/frontendcli/start.sh
```

---

## 3. Bridge Management Commands

The `start.sh` script accepts standard lifecycle commands:

| Command | Action |
|---|---|
| `bash ~/start.sh start` | Starts the bridge daemon in background |
| `bash ~/start.sh stop` | Gracefully stops the bridge daemon |
| `bash ~/start.sh restart` | Restarts the bridge daemon |
| `bash ~/start.sh status` | Checks PID, port 8765 health, and prints recent logs |

---

## 4. Automatic Startup on Termux Launch

To make the bridge start automatically whenever you open Termux:

Add the following line to `~/.bashrc`:
```bash
if [ -f "$HOME/start.sh" ]; then
    bash "$HOME/start.sh" start >/dev/null 2>&1
fi
```

Or configure Termux:Boot (`~/.termux/boot/start-bridge.sh`):
```bash
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
bash /data/data/com.termux/files/home/start.sh start
```

---

## 5. Log Files & Diagnostics

- **Log File**: `~/.frontendcli/bridge.log`
- **PID File**: `~/.frontendcli/bridge.pid`
- **Health Check Probe**:
  ```bash
  curl http://127.0.0.1:8765/api/health
  ```
  Expected output:
  ```json
  {
    "status": "ok",
    "termux": true,
    "ubuntu": true,
    "antigravity": {
      "installed": true,
      "version": "1.1.27",
      "path": "/root/.local/bin/agy"
    },
    "auth": {
      "authenticated": true
    },
    "workspace": "/root"
  }
  ```

---

## 6. How the Android App Connects

1. When CLIFrontend launches, `BridgeClient` probes `http://127.0.0.1:8765/api/health`.
2. Once the 200 OK response is received, the app transitions seamlessly to the IDE workspace.
3. If the bridge is not yet running, the **Diagnostics** panel provides a 1-tap "Copy Termux Command" and "Open Termux" button to quickly launch the daemon.
