# Troubleshooting PocketGravity & Standalone Antigravity IDE

## 1. Standalone Embedded Engine

### First Run Asset Initialization
On the very first launch, the app unpacks:
- The glibc ARM64 dynamic loader (`ld-linux-aarch64.so.1`) and system libraries.
- The Mozilla Root CA certificates bundle (`ca-certificates.crt`).
- The official Antigravity CLI binary (`agy_arm64`).

If you tap Send immediately after opening a fresh install, you may see:
`"⚠️ Antigravity CLI binary not ready. The embedded runtime is initializing or extracting assets."`
- **Solution**: The extraction completes within 3 to 8 seconds depending on device flash speed. Wait a moment and check **Diagnostics** panel to confirm all indicators show green `✓`.

### Daemon Not Running / Connection Offline
The embedded engine runs as a native Android Foreground Service with an ongoing notification.
- Tap **Diagnostics** (top right icon) to see current health.
- Tap **🚀 Start Engine** if the daemon was stopped.
- Ensure Android's Battery Optimization is set to **"Unrestricted"** for PocketGravity so Android OS does not sleep the foreground service during long AI operations.

---

## 2. Authentication & Google Login

### "Google Authentication Required"
- PocketGravity uses standard RFC 7636 PKCE Google OAuth.
- Navigate to **Settings** or **Diagnostics**, tap **Sign in with Google**.
- Complete authentication in your mobile browser. Google redirects back to `http://127.0.0.1:8765/oauth2callback`, where the token is automatically exchanged and saved into your private sandbox.
- Alternatively, if you already have an active Antigravity CLI token, tap **Paste Token** and paste your OAuth access token.

### 403 Errors Eliminated
- Direct raw calls to `cloudcode-pa.googleapis.com` are blocked by Google for personal accounts with `SUBSCRIPTION_REQUIRED`.
- The standalone engine ALWAYS spawns the official `agy` binary with `--output-format stream-json`, which uses Google's authorized consumer routing protocols.

---

## 3. Network & SSL Certificate Verification
- Android does not place standard Linux CA bundles in `/etc/ssl/certs/ca-certificates.crt`.
- PocketGravity automatically configures `SSL_CERT_FILE` pointing to `/data/data/com.antigravity.pocketgravity/files/runtime/ca-certificates.crt`.
- Never disable TLS verification. If a network error occurs, check your cellular/Wi-Fi connection.

---

## 4. Workspaces & File Permissions
- The default workspace is isolated in:
  `/data/data/com.antigravity.pocketgravity/files/workspace/`
- Standard POSIX permissions (`chmod`, file execution, directory creation) are 100% supported.
- You can create, edit, delete, and inspect unified Git diffs directly inside this workspace.

---

## 5. Legacy Termux / External Daemon Fallback (Optional)
If you wish to connect PocketGravity to an external Termux daemon running on your device or a remote PC:
1. Start your bridge daemon:
   ```bash
   python3 bridge/server.py
   ```
2. In PocketGravity **Settings** -> **Bridge URL**, change `http://127.0.0.1:8765` to your desired host/port.
