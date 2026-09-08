# PocketGravity Handoff Document

## 1. Project Goal
Make **PocketGravity** (`com.antigravity.pocketgravity`) a **100% self-contained standalone Android application** that embeds its own Linux/POSIX runtime (glibc + official Google Antigravity CLI binary `agy`) directly inside the APK.
- **No external Termux app** required.
- **No PC tethering / external daemon** required.
- **No Google AI Studio / Gemini API key (`AIza...`)** — strictly banned by user; uses Google OAuth credentials.
- **UI & Protocol**: Stream responses using **ACP (Agent Client Protocol)** formatting ([antigravity-acp](https://github.com/shubzkothekar/antigravity-acp)): reasoning chips (`thought`/`thoughtDelta`), tool badges (`toolCall`/`tool_name`), and streaming tokens (`textDelta`).

---

## 2. Key Architecture & Current State

### A. Embedded Standalone Engine
- **ARM64 Linux Glibc Runtime**:
  - Located in `app/src/main/assets/glibc_arm64/`:
    - `ld-linux-aarch64.so.1` (dynamic linker)
    - `libc.so.6`, `libdl.so.2`, `libm.so.6`, `libpthread.so.0`, `libresolv.so.2`, `librt.so.1`
  - Extracted automatically on first run to `/data/data/com.antigravity.pocketgravity/files/runtime/glibc/`.
- **Official Antigravity Linux ARM64 Binary (`agy`)**:
  - Bundled in `app/src/main/assets/bin/agy_arm64.tar.gz` (extracted to `.../files/runtime/bin/agy`).
  - **Verified on physical device (Xiaomi/POCO 2412DPC0AG, Android 16 ARM64)**:
    ```bash
    /data/data/com.antigravity.pocketgravity/files/runtime/glibc/ld-linux-aarch64.so.1 \
      --library-path /data/data/com.antigravity.pocketgravity/files/runtime/glibc \
      /data/data/com.antigravity.pocketgravity/files/runtime/bin/agy --version
    # OUTPUT: 1.1.27
    ```
    Executes directly on the Android kernel with **zero ptrace, zero root, and zero SELinux violations**.

### B. Daemon & Bridge Service
- `BridgeService.kt`: Foreground service that runs `BridgeServer.kt` (embedded HTTP/SSE server).
- `RuntimeManager.kt`:
  - Extracts assets (`glibc` and `bin/agy`).
  - Implements `buildProcess(command, cwd)` which prefixes `agy` executions with `ld-linux-aarch64.so.1 --library-path <glibcDir>`.
  - Sets `$HOME` to `/data/data/com.antigravity.pocketgravity/files/home` and `$TMPDIR`.
- `BridgeServer.kt`:
  - Serves HTTP REST & SSE streaming endpoints on `127.0.0.1:8765`.
  - `/api/health`: Health status of standalone runtime & auth.
  - `/api/agent/stream`: Streams responses from `agy -p <prompt> --output-format stream-json`.
  - `/api/terminal/exec`: Runs terminal commands in the embedded workspace.
  - Handles Google OAuth PKCE login and token persistence.

---

## 3. The 403 Error: Cause & Next Step

### Root Cause
When the user sends a prompt, if `findAgyBinary()` fails or `process.start()` threw an error, `BridgeServer.kt` was previously falling back to `executeStandaloneAgentStream()`.
`executeStandaloneAgentStream()` makes direct raw REST requests to:
`https://cloudcode-pa.googleapis.com/v1internal:streamGenerateContent`
Google's API gateway rejects direct consumer OAuth bearer tokens with:
`403 SUBSCRIPTION_REQUIRED`
because consumer accounts must talk via the official `agy` binary protocol, not the Cloud Code internal endpoint.

### Solution
1. **Ensure `agy` process always spawns**:
   In `BridgeServer.kt`, verify `RuntimeManager.findAgyBinary()` correctly resolves `.../files/runtime/bin/agy` (make sure file permissions `chmod 755` are set).
2. **Pass OAuth tokens to `agy`**:
   The official `agy` binary looks for credentials in:
   - `$HOME/.gemini/oauth_creds.json`
   - `$HOME/.gemini/antigravity-cli/antigravity-oauth-token`
   Verify that `persistOAuthCredentials()` writes the token to `$HOME/.gemini/oauth_creds.json` with the structure:
   ```json
   {
     "access_token": "ya29...",
     "refresh_token": "...",
     "token_type": "Bearer",
     "expiry_date": 1234567890
   }
   ```
3. **Inspect `agy stderr`**:
   When `agy -p ... --output-format stream-json` runs, capture any error emitted to `errReader` to confirm if it requires `oauth_creds.json` or flags like `--dangerously-skip-permissions`.

---

## 4. Key Files
- [RuntimeManager.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/RuntimeManager.kt) — Asset extraction and `ld-linux` execution builder.
- [BridgeServer.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/BridgeServer.kt) — Embedded HTTP/SSE bridge daemon & token persistence.
- [AgentPanel.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/ui/AgentPanel.kt) — ACP protocol UI (thinking chips, tool calls, streaming text).
- [BridgeService.kt](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/BridgeService.kt) — Android foreground service keeping bridge alive.
- [build.gradle](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/build.gradle) — `aaptOptions { noCompress 'gz', 'tar.gz', 'so', 'tar' }`.
