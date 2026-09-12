# Embedded Runtime Forensics & Technical Retrospective

## 1. Context & Objective of Previous Prototype

An ambitious investigation was conducted to explore whether the official Google Antigravity CLI binary (`agy`) could run 100% standalone inside the Android app sandbox (`untrusted_app`) without requiring Termux.

The prototype bundled:
- The glibc dynamic loader (`ld-linux-aarch64.so.1`) and core glibc libraries (`libc.so.6`, `libdl`, `libm`, `libpthread`, `libresolv`, `librt`).
- Mozilla Root CA certificates (`ca-certificates.crt`).
- Userspace PRoot emulation binaries (`proot`, `loader`, `loader32`).
- The official `agy` 1.1.27 aarch64 binary.

---

## 2. Technical Blockers Discovered

While `agy --version` successfully printed under specific test commands, full execution of agent turns inside the Android app sandbox encountered fundamental Linux/Android kernel security barriers:

### A. Android Zygote Seccomp Filter (`SIGSYS` Exit 159)
- Android's app spawning architecture (`app_process` / Zygote) installs a strict BPF seccomp policy for all `untrusted_app` contexts.
- Glibc 2.36+ unconditionally invokes system call 293 (`rseq` - restartable sequences) during thread initialization.
- Android seccomp immediately traps this syscall with `SECCOMP_RET_TRAP`, killing the process with signal 31 (`SIGSYS` / exit code 159).
- Even after binary patching the `svc #0` instructions out of `ld-linux` and `libc.so.6`, additional glibc system calls consistently risk tripping modern Android seccomp filters.

### B. Userspace Emulation Restrictions (PRoot W^X & ptrace)
- PRoot requires either `ptrace` or seccomp userspace emulation.
- On Android 10+ (API 29+), `W^X` memory protection prevents executing memory segments that have been written to, causing userspace loaders to throw `Permission denied` on `execve`.
- Android seccomp prevents PRoot from installing its own seccomp tracer inside an already-seccomp-constrained container.

### C. Network & DNS Resolver Absence in App Sandbox
- Android does not use `/etc/resolv.conf`. System DNS requests go through `netd` via Bionic's `android_getaddrinfofornet()`.
- Glibc binaries (like `agy`) expect a standard POSIX `/etc/resolv.conf`. In the app sandbox, writing to root `/etc` is impossible without full rootfs chroot/proot redirection.
- When `agy` initiates HTTPS calls to Google APIs (`generativelanguage.googleapis.com`), the glibc net resolver fails to reach DNS nameservers.

---

## 3. The Solution: Termux + Bridge Architecture

Termux operates outside the rigid Android Zygote sandbox:
1. **Unrestricted Linux Syscalls**: Termux is designed from the ground up for CLI tools and standard Linux syscalls without BPF seccomp traps.
2. **Proper DNS & Network Stack**: Full network stack resolution, loopback binding, and socket handling.
3. **Official Glibc Container Support**: Via `proot-distro login ubuntu`, standard glibc libraries run without any patching required.
4. **Stable Daemon Execution**: Termux supports background wake-locks (`termux-wake-lock`) ensuring long AI code generation turns complete uninterrupted.

---

## 4. Conclusion

Attempting to run a standalone glibc dynamic linker and userspace PRoot directly inside an unrooted Android `untrusted_app` sandbox introduces high fragility across different Android OEM builds. The **Termux + Python Bridge Daemon + Android App** architecture provides the genuine, rock-solid foundation for running Google Antigravity on mobile.
