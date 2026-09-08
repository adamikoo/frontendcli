# Runtime Requirements: Minimum Viable Environment Analysis

## 1. Architectural Question: Is Ubuntu Required?

**Conclusion: NO. Full Ubuntu is NOT required.**

The initial Termux prototype utilized `proot-distro login ubuntu` solely as an easy path to obtain:
1. An `aarch64` Linux glibc runtime (`libc.so.6`, `ld-linux-aarch64.so.1`, etc.).
2. A standard Linux filesystem structure (`/etc/ssl/certs`, `/tmp`, `/bin/sh`).
3. A pre-packaged Python 3 installation to run `server.py`.

However, deep binary forensics on the real `agy` executable (`antigravity` v1.1.27) show that:
- `agy` is a standalone compiled **Rust** native binary.
- It does **not** link against Python, Node.js, GTK, X11, or complex distro-specific packages.
- It depends strictly on standard `glibc` (version `>= 2.26`):
  - `ld-linux-aarch64.so.1` (dynamic loader)
  - `libc.so.6`
  - `libdl.so.2`
  - `libm.so.6`
  - `libpthread.so.0`
  - `libresolv.so.2`
  - `librt.so.1`
- These 7 shared object files total less than 3 MB!

---

## 2. Experimental Proof: Direct Android Kernel Execution

On physical Android hardware (verified on POCO 2412DPC0AG, ARM64 Android 16):
```bash
/data/data/com.antigravity.pocketgravity/files/runtime/glibc/ld-linux-aarch64.so.1 \
  --library-path /data/data/com.antigravity.pocketgravity/files/runtime/glibc \
  /data/data/com.antigravity.pocketgravity/files/runtime/bin/agy --version
```
**Result**:
- Output: `1.1.27`
- Exit Code: `0`
- Zero root permissions needed.
- Zero `ptrace` system calls (which are often restricted or flagged by Android SELinux).
- Zero PRoot virtualization overhead.
- Direct execution on the native Linux kernel powering Android.

---

## 3. The Real Minimum Environment Matrix

| Component | Minimum Required | Provided By Standalone App |
|---|---|---|
| **CPU Architecture** | `arm64-v8a` (`aarch64`) | Device hardware |
| **Kernel** | Linux `>= 3.10` (Android 8.0+) | Android OS kernel |
| **C Library** | glibc `>= 2.26` | Bundled in `app/src/main/assets/glibc_arm64/` (~2.8 MB) |
| **Dynamic Linker** | `ld-linux-aarch64.so.1` | Bundled in `glibc_arm64/` |
| **Antigravity CLI** | `agy` (v1.1.27 aarch64 ELF) | Bundled in `app/src/main/assets/bin/agy_arm64.tar.gz` |
| **CA Root Certificates** | Mozilla PEM CA bundle | Bundled in `app/src/main/assets/ca-certificates.crt` via `$SSL_CERT_FILE` |
| **Shell for Tools** | POSIX-compatible shell | Android native `/system/bin/sh` |
| **Bridge Daemon** | HTTP + SSE server on 127.0.0.1:8765 | Embedded pure-Kotlin daemon (`BridgeServer.kt`) running as Android Foreground Service |
| **Disk Storage** | App-private storage (`Context.filesDir`) | Extracted on first run |

---

## 4. Comparison of Approaches

### Approach A: Full Embedded Ubuntu Distribution (PRoot)
- **Size**: ~350 MB to 1 GB rootfs.
- **Overhead**: PRoot intercepts every syscall using `ptrace` or `seccomp`, degrading file I/O and process spawning speeds by 2x to 5x.
- **Failures**: Known issues with Android 14+ phantom process killing, seccomp filters, and storage permissions.
- **Verdict**: Unnecessarily heavy, slow, and bloated.

### Approach B: Embedded Micro-glibc Runtime (Current Recommended Solution)
- **Size**: ~55 MB APK total (compressed).
- **Overhead**: 0% virtualization overhead. Direct syscalls to the Android Linux kernel.
- **Compatibility**: Runs seamlessly in app-private storage (`/data/data/com.antigravity.pocketgravity/files/`).
- **Verdict**: Optimal, fast, minimal, and fully self-contained in a single APK.
