# Troubleshooting CLIFrontend

## 1. Connection & Diagnostics
If the app shows "Bridge Offline":
- Check if Termux is running in the background.
- Open Termux and verify the bridge is running:
  ```bash
  proot-distro login ubuntu -- ps aux | grep server.py
  ```
- If not running, start it:
  ```bash
  proot-distro login ubuntu -- /downloads/clifrontend/bridge/start.sh
  ```

## 2. Authentication
If the app prompts for authentication:
- Check if `~/.gemini/antigravity-cli/antigravity-oauth-token` exists in Ubuntu.
- Run `agy models` manually in Ubuntu to test token validity.
- If expired, initiate login via the app's "Sign in with Google" button or run `agy` interactively in Ubuntu to refresh credentials.

## 3. Terminal Issues
- If the terminal does not accept input, ensure WebSocket connection `/ws/terminal` is open.
- If characters look garbled, verify UTF-8 locale in Ubuntu (`export LANG=en_US.UTF-8`).

## 4. File Permission Errors
- Ensure the project workspace directory is readable and writable by the user running the bridge.
- On Android shared storage (`/sdcard`), file permissions are managed by Android's MediaProvider; working inside `/root` or `/data/data/com.termux` provides standard POSIX filesystem permissions.
