# Security Architecture & Policies

## 1. Zero Trust IPC & Network Isolation
- **Localhost Only**: The embedded bridge daemon binds strictly to `127.0.0.1:8765`. It never exposes a public network interface (`0.0.0.0` or external Wi-Fi / cellular interfaces).
- **No Remote Access**: Incoming connections from non-loopback addresses are prohibited.
- **TLS Integrity**: Strict TLS verification is enforced on all external outbound connections. TLS certificate validation is NEVER disabled.

## 2. Authentication & Credential Storage
- **Google OAuth 2.0 PKCE Flow**: PocketGravity uses standard RFC 7636 PKCE (Proof Key for Code Exchange) with `SHA-256` challenges.
- **No Password Access**: The application never requests, collects, or stores the user's Google password.
- **Private Sandbox Storage**: Credentials (`antigravity-oauth-token`, `oauth_credentials.json`) are stored exclusively in the app's internal sandbox:
  `/data/data/com.antigravity.pocketgravity/files/home/.gemini/`
  Android file permissions restrict read/write access to this application's Linux UID.
- **No Secret Logging**: Bearer tokens and refresh tokens are excluded from `DebugLogger` and logcat outputs.

## 3. Sandboxed Workspace Execution
- **Scoped Filesystem**: Workspace operations are restricted to `files/workspace/` and user-selected project paths.
- **Directory Traversal Prevention**: All file paths supplied to `/api/fs/*` are resolved with canonical path verification to prevent path traversal outside authorized project boundaries.
- **Command Approvals**: Antigravity agent tool executions are subject to approval policies.
