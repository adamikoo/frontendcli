# Verification & Testing Protocol

## 1. Automated Test Suite Matrix

| Test Category | Target Component | Method | Gate Condition |
|---|---|---|---|
| **Python Bridge Daemon** | `bridge/server.py` | Standalone Python execution | Serves HTTP REST on `127.0.0.1:8765` |
| **Health Probe** | `/api/health` | HTTP GET | Returns JSON with `termux`, `ubuntu`, `antigravity`, and `auth` |
| **Model Discovery** | `/api/models` | Subprocess `agy models` | Returns live Google Gemini & Claude catalog |
| **Protocol Stream** | `/api/agent/stream` | SSE streaming test turn | NDJSON `init`, `step_update`, and `result` received |
| **Workspace File I/O** | `/api/fs/*` | Create, Read, Write, Diff | Real filesystem modification verified |
| **Terminal Exec** | `/api/terminal/exec` | Shell execution | Returns stdout, stderr, and exitCode |

---

## 2. End-to-End Device Validation Checklist

1. **Deploy APK**:
   Install `CLIFrontend.apk` on an Android test device.
2. **Launch Termux & Bridge**:
   In Termux, launch the bridge:
   ```bash
   bash ~/start.sh start
   ```
3. **Open CLIFrontend**:
   Open the app. Confirm the top status indicator shows:
   - Status: `✓ Connected`
4. **Inspect Diagnostics Screen**:
   - Verify Termux / Linux Environment: `✓ Ready`
   - Verify Bridge Daemon: `✓ Connected (127.0.0.1:8765)`
   - Verify Antigravity CLI: `✓ 1.1.27`
   - Verify Authentication: `✓ Authenticated` (or complete Google Sign-In)
5. **Submit Test Prompt in Agent Chat**:
   - Send: `"Create a test file named hello.py with a hello world function"`
   - Verify ACP streaming UI displays:
     - Thinking chip: `💭 Thinking...`
     - Tool execution badge: `⚙ write_to_file`
     - Streaming text delta: `"Created hello.py"`
6. **Verify Explorer & Editor**:
   - Open Explorer panel -> confirm `hello.py` appears.
   - Open `hello.py` in Editor -> confirm contents.
7. **Verify Changes / Diff**:
   - Open Changes panel -> verify Git diff highlights added file.
