# Troubleshooting CLIFrontend

## 1. Connection & Diagnostics
If the app shows "Bridge Offline" or "Can't connect":
- Verify the bridge status in Termux or Ubuntu:
  ```bash
  cd /downloads/clifrontend/bridge   # or path to your clifrontend folder
  ./status.sh
  ```
- If stopped or unresponsive, restart it with health check verification:
  ```bash
  ./start.sh restart
  ```
  The script automatically checks if the process crashed, probes `http://127.0.0.1:8765/api/health`, and shows the exact log if it failed.

- Check the live log output:
  ```bash
  ./start.sh logs
  ```

- Prevent Android from sleeping/freezing the Termux process:
  ```bash
  termux-wake-lock
  ```
  Ensure Android Battery Optimization for Termux is set to "Unrestricted".

- If running inside Ubuntu PRoot:
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
