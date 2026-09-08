# Pocket Gravity — Fix Plan for Sept 8

## Context

Two issues remain blocking a working backend:
1. **Backend crash on message send** — `agy` process fails to produce usable output when the user sends a message in the Agent panel
2. **Logout button broken** — tapping "Log Out of Google" in Settings doesn't properly clear all credential locations

---

## Issue 1: Backend (`agy`) Not Working

### Root Cause Chain

The `agy` binary is a Linux aarch64 ELF, executed in-process via the glibc dynamic linker (`ld-linux-aarch64.so.1`). The execution chain is:

```
AgentPanel → BridgeClient.sendMessage() → POST /api/agent/stream
  → BridgeServer.handleAgentStream()
    → RuntimeManager.buildProcess(["agy", "-p", prompt, ...])
      → ld-linux-aarch64.so.1 --library-path <glibc> <agy> -p "..." --output-format stream-json
```

**Known blockers discovered in this session:**

| # | Problem | Status |
|---|---------|--------|
| 1 | `GLIBC_TUNABLES=glibc.pthread.rseq=0` missing → immediate crash (exit 159) | ✅ Fixed in `buildProcess()` L291 |
| 2 | Token file format mismatch — `agy` expects `StoredToken` JSON struct, not raw token string | ✅ Fixed in `persistOAuthCredentials()` L545-606 |
| 3 | DNS resolution fails in Android sandbox — `agy` can't resolve `generativelanguage.googleapis.com` | ❌ **Not fixed** |
| 4 | Auth token not present/valid when `agy` starts — `"error getting token source"` | ⚠️ Partially fixed — depends on user completing OAuth flow first |

### What to Do Tomorrow

#### Step 1: Verify `agy` starts at all (5 min)

```bash
adb shell "run-as com.antigravity.pocketgravity env \
  HOME=/data/user/0/com.antigravity.pocketgravity/files/home \
  GLIBC_TUNABLES=glibc.pthread.rseq=0 \
  /data/user/0/com.antigravity.pocketgravity/files/runtime/glibc/ld-linux-aarch64.so.1 \
  --library-path /data/user/0/com.antigravity.pocketgravity/files/runtime/glibc \
  /data/user/0/com.antigravity.pocketgravity/files/runtime/bin/agy --version"
```

- If this prints a version string → binary loads OK
- If exit code 159 → `GLIBC_TUNABLES` not injected, check `buildProcess()`
- If segfault / other → glibc libs incomplete, re-extract

#### Step 2: Fix DNS resolution (CRITICAL — 30 min)

`agy` uses Go's net resolver which tries `getaddrinfo()` via cgo. In Android sandbox, `/etc/resolv.conf` doesn't exist and system DNS is unreachable from the sandbox.

**Options (pick one):**

**A) Inject `GODEBUG=netdns=go` to force pure-Go resolver + provide resolv.conf**
```kotlin
// In RuntimeManager.buildProcess(), add:
env["GODEBUG"] = "netdns=go"

// Create resolv.conf in home dir:
val resolvConf = File(homeDir, "etc/resolv.conf")
resolvConf.parentFile?.mkdirs()
resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
```

**B) Write `/etc/hosts` entries for Google API endpoints**
```kotlin
val hostsFile = File(homeDir, "etc/hosts")
hostsFile.parentFile?.mkdirs()
// Pre-resolve from the Kotlin side (which CAN do DNS) and write IPs
val addrs = java.net.InetAddress.getAllByName("generativelanguage.googleapis.com")
val lines = addrs.joinToString("\n") { "${it.hostAddress} generativelanguage.googleapis.com" }
hostsFile.writeText(lines + "\n")
```
Then set `HOSTALIASES` env var or use Android's system resolver.

**C) Proxy approach — route `agy` network through a local SOCKS5/HTTP proxy that runs in the Android process** (most complex, skip unless A/B fail)

> **Recommendation: Try Option A first.** If `agy` is statically linked with CGo DNS, try B.

#### Step 3: Verify SSL certificates work (5 min)

`buildProcess()` already sets `SSL_CERT_FILE` to the extracted CA bundle. Verify:

```bash
adb shell "run-as com.antigravity.pocketgravity ls -la \
  /data/user/0/com.antigravity.pocketgravity/files/runtime/ca-certificates.crt"
```

If 0 bytes or missing → asset extraction failed. Force re-extract:
```bash
adb shell "run-as com.antigravity.pocketgravity rm \
  /data/user/0/com.antigravity.pocketgravity/files/runtime/ca-certificates.crt"
```
Then restart app (triggers `extractEmbeddedRuntime()`).

#### Step 4: Test authenticated `agy` call (10 min)

After OAuth flow is complete, test a real prompt:

```bash
adb shell "run-as com.antigravity.pocketgravity env \
  HOME=/data/user/0/com.antigravity.pocketgravity/files/home \
  GLIBC_TUNABLES=glibc.pthread.rseq=0 \
  GODEBUG=netdns=go \
  SSL_CERT_FILE=/data/user/0/com.antigravity.pocketgravity/files/runtime/ca-certificates.crt \
  /data/user/0/com.antigravity.pocketgravity/files/runtime/glibc/ld-linux-aarch64.so.1 \
  --library-path /data/user/0/com.antigravity.pocketgravity/files/runtime/glibc \
  /data/user/0/com.antigravity.pocketgravity/files/runtime/bin/agy \
  -p 'say hello' --output-format stream-json --dangerously-skip-permissions"
```

Check stderr carefully for:
- `"error getting token source"` → token file format wrong or missing
- `"dial tcp: lookup ... on [::1]:53"` → DNS still broken
- `"x509: certificate signed by unknown authority"` → CA cert bundle not loaded
- Actual JSON output → 🎉 working!

#### Step 5: Apply fixes to `buildProcess()` (10 min)

File: [`RuntimeManager.kt`](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/RuntimeManager.kt#L263-L306)

Add DNS fix and resolv.conf creation:

```kotlin
// After L291 (GLIBC_TUNABLES line):
env["GODEBUG"] = "netdns=go"

// In ensureDirectories() or buildProcess():
val etcDir = File(homeDir, "etc")
etcDir.mkdirs()
val resolvConf = File(etcDir, "resolv.conf")
if (!resolvConf.exists()) {
    resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
}
```

---

## Issue 2: Broken Logout Button

### Current Behavior

The logout handler in [`BridgeServer.kt` L458-479](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/BridgeServer.kt#L458-L479) only deletes **2 files**:

```kotlin
// Currently deleted:
RuntimeManager.agyTokenFile          // ~/.gemini/antigravity-cli/antigravity-oauth-token
File(agyConfigDir, "oauth_credentials.json")
```

### Problem

`persistOAuthCredentials()` writes to **7 locations**, but logout only clears 2. Leftover credentials cause `getValidAccessToken()` to still find tokens and report `authenticated=true`.

### Files written by persist (L559-583) but NOT cleaned by logout:

| File | Cleaned? |
|------|----------|
| `agyTokenFile` (antigravity-oauth-token) | ✅ |
| `agyConfigDir/antigravity-oauth-token` | ❌ (same as above only if first candidate exists) |
| `~/.gemini/antigravity-cli/antigravity-oauth-token` | ❌ |
| `agyConfigDir/oauth_credentials.json` | ✅ |
| `~/.gemini/oauth_creds.json` | ❌ |
| `~/.gemini/antigravity/mcp_oauth_tokens.json` | ❌ |
| `~/.config/agy/credentials.json` | ❌ |
| `~/.gemini/google_accounts.json` | ❌ |

### Fix

Replace `handleAuthLogout` with a comprehensive cleanup:

```kotlin
private fun handleAuthLogout(output: OutputStream) {
    try {
        // Delete ALL token files that persistOAuthCredentials writes
        val filesToDelete = listOf(
            RuntimeManager.agyTokenFile,
            File(RuntimeManager.agyConfigDir, "antigravity-oauth-token"),
            File(RuntimeManager.homeDir, ".gemini/antigravity-cli/antigravity-oauth-token"),
            File(RuntimeManager.agyConfigDir, "oauth_credentials.json"),
            File(RuntimeManager.homeDir, ".gemini/oauth_creds.json"),
            File(RuntimeManager.homeDir, ".gemini/antigravity/mcp_oauth_tokens.json"),
            File(RuntimeManager.homeDir, ".config/agy/credentials.json"),
            File(RuntimeManager.homeDir, ".gemini/google_accounts.json")
        )
        for (f in filesToDelete) {
            try { if (f.exists()) f.delete() } catch (_: Exception) {}
        }

        // Clear modelProvider from settings
        val settings = getSettings()
        if (settings.has("modelProvider")) {
            settings.remove("modelProvider")
            saveSettings(settings)
        }

        DebugLogger.i("User logged out, all token files deleted")
        sendJson(output, JSONObject().put("status", "ok").put("authenticated", false))
    } catch (e: Exception) {
        sendJson(output, JSONObject().put("error", e.message), 500)
    }
}
```

### Secondary issue: `agyTokenFile` getter fallback

[`RuntimeManager.kt` L49-57](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/RuntimeManager.kt#L49-L57) — the `agyTokenFile` getter returns the *first existing* file from candidates. After logout deletes `candidates[0]`, the getter could return `candidates[1]` (Termux path) if a leftover token exists there. Logout must delete ALL candidates.

---

## Execution Order

```
1. Build + deploy current code to device
2. Verify agy --version works via adb shell
3. Add GODEBUG=netdns=go + resolv.conf to buildProcess()
4. Fix handleAuthLogout to clear all 8 credential files
5. Rebuild + deploy
6. Complete OAuth login flow in browser
7. Test sending a message in Agent panel
8. Test logout button → verify /api/auth/status returns authenticated=false
9. Test re-login after logout
```

## Files to Edit

| File | Changes |
|------|---------|
| [`RuntimeManager.kt`](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/RuntimeManager.kt) | Add `GODEBUG=netdns=go` env var, create `resolv.conf` |
| [`BridgeServer.kt`](file:///c:/Users/adamk/Downloads/websites/clifrontend/app/src/main/kotlin/com/antigravity/pocketgravity/api/BridgeServer.kt) | Rewrite `handleAuthLogout()` to delete all 8 credential files |

## Success Criteria

- [ ] `agy -p "hello" --output-format stream-json` produces JSON output via adb shell
- [ ] Sending a message in Agent panel shows streaming response (no crash)
- [ ] Logout button clears ALL tokens; `/api/auth/status` returns `authenticated: false`
- [ ] Re-login after logout works end-to-end

---

## Issue 3: `agy` Process Exits with Code 159 (`SIGSYS` - Bad System Call)

### Symptom (from Device Diagnostics)
```
[06:38:18.568] i Spawning agy agent: /data/user/0/com.antigravity.pocketgravity.app/files/runtime/bin/agy -p Hello --output-format stream-json --dangerously-skip-permissions
[06:38:18.573] i agy process exited with code 159
```

### Root Cause Analysis
- Exit code `159` is POSIX `$128 + 31$ = SIGSYS` (Bad System Call).
- Android OS kernel enforces an application sandbox **SECCOMP BPF filter** that kills any process calling unpermitted Linux syscalls.
- Typical syscall triggers in glibc/Rust binaries:
  1. `rseq` (Restartable Sequences, syscall 383 on arm64) during thread initialization.
  2. `clone3` (syscall 435 on arm64) used by newer glibc `pthread_create()`.
  3. Memory management / cacheflush or sysinfo syscalls disallowed by Android zygote seccomp policy.
- Even though `GLIBC_TUNABLES=glibc.pthread.rseq=0` is set in `buildProcess()`, invoking `ld-linux-aarch64.so.1` directly can drop or fail to parse environment tunables in certain glibc builds, or another syscall (`clone3`) is the trigger.

### Remediation Strategies (For Future Work - Do Not Fix Yet)

1. **Option A: PRoot Syscall Interception (Bundled)**
   - The app already bundles PRoot in `proot_pkg/aarch64/bin/proot` with `libtalloc.so` and `libandroid-shmem.so`.
   - PRoot uses `ptrace(PTRACE_SYSCALL)` to intercept and emulate system calls in user space before the kernel seccomp filter can trap them.
   - Update `buildProcess()` to route through `proot -0 -r <rootfs> ...` if direct `ld-linux` encounters seccomp.

2. **Option B: Identify Exact Syscall via Logcat**
   - Check Android system log for audit messages:
     ```bash
     adb logcat | grep -i "seccomp"
     # e.g.: type=1326 audit(...): syscall=383 compat=0 ip=... code=0x0
     ```
   - Matches the syscall number to arm64 syscall table.

3. **Option C: Explicit Tunables via CLI Arguments**
   - Pass tunables directly to the dynamic linker:
     `ld-linux-aarch64.so.1 --tunables glibc.pthread.rseq=0 --library-path ...`
   - Or test `LD_PRELOAD` stub that intercepts the failing syscall and returns `ENOSYS` so glibc cleanly falls back.

