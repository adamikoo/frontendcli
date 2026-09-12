# Runtime Requirements: Environment Specification

## 1. System Architecture: Three-Tier Model

CLIFrontend relies on the robust, production-tested three-tier runtime environment:
- **Tier 1**: Termux Android Host Environment (`com.termux`)
- **Tier 2**: Ubuntu PRoot Container (managed via `proot-distro`)
- **Tier 3**: Bridge Server Daemon (`bridge/server.py` on `127.0.0.1:8765`)

---

## 2. Requirements Matrix

| Component | Requirement | Provider / Location |
|---|---|---|
| **CPU Architecture** | `arm64-v8a` (`aarch64`) | Android device hardware |
| **Android OS** | Android 8.0+ (API 26+) | Host system |
| **Terminal Host** | Termux (`com.termux`) | F-Droid / GitHub Releases |
| **Linux Distribution** | Ubuntu 22.04+ (via `proot-distro`) | Installed via `proot-distro install ubuntu` in Termux |
| **C Library** | GNU C Library (glibc `>= 2.31`) | Provided by Ubuntu container |
| **Python Runtime** | Python `>= 3.8` | Installed via `apt-get install python3` in Ubuntu container |
| **Antigravity CLI** | `agy` (v1.1.27 aarch64 Linux ELF) | Installed in `/root/.local/bin/agy` or `~/.local/bin/agy` |
| **Bridge Daemon** | `bridge/server.py` & `bridge/start.sh` | Included in project / exported to `/sdcard/Download/frontendcli/` |
| **Loopback Networking** | `127.0.0.1:8765` | Shared Android Linux kernel network namespace |
| **Android App** | `CLIFrontend.apk` | Native Material IDE frontend |

---

## 3. Automated Environment Setup (`bridge/start.sh`)

The provided `bridge/start.sh` script automates the entire provisioning process when launched from Termux:
1. Detects native Termux execution environment.
2. Automatically installs `proot-distro` if missing (`pkg install -y proot-distro`).
3. Installs the Ubuntu rootfs if missing (`proot-distro install ubuntu`).
4. Installs `python3` and `curl` inside the container.
5. Acquires `termux-wake-lock` to prevent the OS from killing the bridge process.
6. Binds `/sdcard` to `/sdcard` and `$HOME` to `/root/termux_home` inside the container.
7. Starts `server.py` in the background, writing logs to `~/.frontendcli/bridge.log`.

---

## 4. Why This Architecture Works

- **Zero Seccomp Violations**: Termux processes run with full terminal shell privileges, eliminating `SIGSYS` (exit code 159) traps.
- **Native DNS Resolution**: Uses standard glibc network stack with working `/etc/resolv.conf` and `getaddrinfo()`.
- **Background Persistence**: Native Android `termux-wake-lock` ensures long-running agent tool invocations (building, file replacement, test runs) are not suspended by OEM battery managers.
