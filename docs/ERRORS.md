# Error Analysis & Forensic Root Cause Investigation (404, 403, 500)

## 1. Trace Matrix

```
[Android UI] ---> [Local Bridge Server] ---> [Embedded Runtime] ---> [agy CLI] ---> [Google Cloud API]
```

---

## 2. Detailed Root Cause Breakdown

### A. The 403 Error: `SUBSCRIPTION_REQUIRED`
- **Component**: External Service (`cloudcode-pa.googleapis.com`)
- **Root Cause**:
  In earlier iterations, if `findAgyBinary()` was unresolved or failed to launch, `BridgeServer.kt` attempted an ad-hoc fallback method named `executeStandaloneAgentStream()`. This method took the user's OAuth bearer token and sent a raw REST POST request to:
  `https://cloudcode-pa.googleapis.com/v1internal:streamGenerateContent`
  Google's Cloud Code API gateway immediately rejects consumer Google accounts with:
  ```json
  {
    "error": {
      "code": 403,
      "message": "Subscription required for Cloud AI Companion",
      "status": "PERMISSION_DENIED"
    }
  }
  ```
- **Remediation**:
  The official `agy` binary does NOT use this endpoint. It uses Google's authenticated Antigravity CLI routing protocol. Therefore, **we must NEVER make raw direct REST calls to cloudcode-pa**. The bridge must **always** launch the real `agy` binary. If `agy` is not found or fails, return an informative diagnostic error rather than attempting unsupported raw API calls.

### B. The 404 Error: Endpoint Not Found
- **Component**: Bridge Routing (`BridgeServer.kt`)
- **Root Causes**:
  1. **Query String Contamination**: If a request was sent to `/api/fs/tree?path=/data/...`, simple equality checks (`path == "/api/fs/tree"`) failed because `path` contained `?path=...`.
  2. **OAuth Callback Path Variations**: Google OAuth redirect URIs can arrive as `/oauth2callback`, `/oauth/callback`, or custom scheme intents. If the HTTP server did not recognize both, it returned HTTP 404.
  3. **Port Mismatch**: If the bridge stopped or bound to a different port, the client received connection refused or hit a stale daemon.
- **Remediation**:
  Extract and strip the query string from `fullPath` prior to routing. Implement exact routing matchers for all API endpoints. Ensure `BridgeClient` defaults to `http://127.0.0.1:8765`.

### C. The 500 Error: Internal Server Error
- **Component**: Runtime & Process Spawning
- **Root Causes**:
  1. **Execute Permissions (`chmod +x`)**: Android asset extraction creates files without executable bits (`0600` or `0644`). Spawning `ld-linux-aarch64.so.1` or `bin/agy` threw `java.io.IOException: error=13, Permission denied`.
  2. **Missing Glibc Libraries**: If any of the 6 glibc dependencies (`libc.so.6`, `libdl.so.2`, `libm.so.6`, `libpthread.so.0`, `libresolv.so.2`, `librt.so.1`) were missing or not specified in `--library-path`, the dynamic linker aborted immediately with code 127.
  3. **Missing Root CA Certificates**: Without `SSL_CERT_FILE`, `agy` threw TLS certificate verification errors when connecting to Google's OAuth or Gemini endpoints.
  4. **Uncaught JSON Parsing Exceptions**: Requests with malformed or empty payloads caused uncaught exceptions in thread handlers.
- **Remediation**:
  - Explicitly invoke `.setExecutable(true, false)` on all extracted binaries and `.so` files.
  - Automatically bundle and extract `ca-certificates.crt` and export `SSL_CERT_FILE`.
  - Wrap all request handlers in try/catch blocks with JSON error responses and detailed logging to `DebugLogger`.
