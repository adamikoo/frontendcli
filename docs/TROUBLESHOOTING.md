# Troubleshooting Guide

## 1. Bridge Connection Issues ("Bridge Offline")

### Symptom
The app displays `"Bridge Offline at http://127.0.0.1:8765"` or Diagnostics indicates Bridge Daemon is red.

### Resolution Steps
1. **Open Termux** on your device.
2. Check if the bridge is running:
   ```bash
   bash ~/start.sh status
   ```
3. If not running, start it:
   ```bash
   bash ~/start.sh start
   ```
4. Verify port 8765 responds locally:
   ```bash
   curl -s http://127.0.0.1:8765/api/health
   ```
5. Inspect logs for any errors:
   ```bash
   cat ~/.frontendcli/bridge.log
   ```

---

## 2. Antigravity CLI Not Found

### Symptom
Diagnostics shows `"Antigravity CLI (agy) not found in Linux environment"`.

### Resolution Steps
1. In Termux or inside your Ubuntu PRoot container, verify `agy` is installed:
   ```bash
   which agy || ls -la ~/.local/bin/agy || ls -la /root/.local/bin/agy
   ```
2. If missing, ensure `agy` is placed in `~/.local/bin/agy` and marked executable:
   ```bash
   chmod +x ~/.local/bin/agy
   ```
3. Test running `agy --version` directly in the shell to confirm it returns `1.1.27`.

---

## 3. Google Authentication & OAuth

### "Google Authentication Required"
- In CLIFrontend, go to **Settings** or **Diagnostics** and tap **Sign in with Google**.
- Complete authentication in your browser. The redirect will send the authorization code to the bridge to complete the token exchange.
- Alternatively, if you have a valid token: tap **Paste OAuth Token** and paste your token string directly.

### 403 Errors Eliminated
- Direct calls to `cloudcode-pa.googleapis.com` are rejected for consumer Google accounts.
- The bridge exclusively spawns the official `agy` binary with `--output-format stream-json`, which uses Google's authorized consumer routing protocols.

---

## 4. Background Persistence & Sleep Prevention

### Symptom
Long code generation turns stop when the screen turns off or the app is minimized.

### Resolution Steps
1. In Termux, ensure wake-lock is acquired:
   ```bash
   termux-wake-lock
   ```
2. In Android Settings -> Apps -> **Termux**:
   - Set Battery usage to **"Unrestricted"**.
3. In Android Settings -> Apps -> **CLIFrontend**:
   - Set Battery usage to **"Unrestricted"**.
