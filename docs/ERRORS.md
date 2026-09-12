# Error Analysis & Forensic Root Cause Investigation

## 1. Trace Matrix

```
[Android UI] ---> [Local Python Bridge (:8765)] ---> [Termux / Ubuntu Linux] ---> [agy CLI] ---> [Google Cloud API]
```

---

## 2. Detailed Root Cause Breakdown

### A. The 403 Error: `SUBSCRIPTION_REQUIRED`
- **Component**: External Service (`cloudcode-pa.googleapis.com`)
- **Root Cause**:
  Direct raw REST calls to `https://cloudcode-pa.googleapis.com/v1internal:streamGenerateContent` using consumer Google OAuth bearer tokens are rejected by Google's API gateway with `403 SUBSCRIPTION_REQUIRED`.
- **Remediation**:
  The official `agy` binary does NOT use this endpoint; it uses Google's authenticated Antigravity CLI routing protocol. The bridge must **always** launch the real `agy` binary with `--output-format stream-json`. Raw direct REST calls to cloudcode-pa are strictly prohibited.

### B. In-App Standalone Runtime Failures: `SIGSYS 159` & PRoot Denied
- **Component**: Android Process Sandbox (`untrusted_app`)
- **Root Causes**:
  1. **Seccomp Filters**: Android's Zygote installs a BPF seccomp sandbox that blocks Linux syscall 293 (`rseq`) and other glibc calls, triggering signal 31 (`SIGSYS` / exit code 159).
  2. **W^X Enforcement**: Android 10+ untrusted app sandboxes prevent executing memory that has been modified, blocking userspace loaders from `execve`.
  3. **DNS Absence**: Android sandboxes do not provide `/etc/resolv.conf`, causing glibc net resolvers to fail connecting to Google's API servers.
- **Remediation**:
  Operate via **Termux & PRoot Distro Ubuntu**. Termux runs as a native shell process with standard Linux syscall allowances, dynamic linking, and full DNS resolution.

### C. The 404 Error: Endpoint Not Found
- **Component**: Bridge Routing (`server.py`)
- **Root Causes**:
  1. **Query String Contamination**: If a request was sent to `/api/fs/tree?path=/root/...`, simple string equality checks failed.
  2. **OAuth Callback Path Variations**: Google OAuth redirect URIs can arrive as `/oauth2callback` or `/oauth/callback`.
- **Remediation**:
  Strip the query string from `path` prior to routing. Support both `/oauth/callback` and `/oauth2callback`.

### D. The 500 Error: Internal Server Error
- **Component**: Bridge Execution & Subprocess Spawning
- **Root Causes**:
  1. **Missing Executable Permissions**: `agy` binary missing `chmod +x`.
  2. **Port Conflict**: Stale Python process holding port 8765.
- **Remediation**:
  Use `start.sh stop` or `fuser -k 8765/tcp` before starting. Ensure `chmod +x` on `agy`.
