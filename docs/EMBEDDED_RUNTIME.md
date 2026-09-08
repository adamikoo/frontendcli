# Embedded Runtime Architecture

## 1. Design Principles

1. **Zero External Dependencies**: User installs `CLIFrontend.apk` and nothing else. No Termux, no Termux:API, no Ubuntu, no Python, no Node.js.
2. **Direct Kernel Execution**: Runs via the glibc dynamic linker `ld-linux-aarch64.so.1` directly on the Android Linux kernel. No ptrace, no root, no virtualization penalty.
3. **App-Private Isolation**: All files, configs, binaries, and workspaces reside within the app-private sandbox (`/data/data/com.antigravity.pocketgravity/files/`).
4. **Native Android Lifecycle Management**: The bridge daemon is implemented as an Android Foreground Service (`BridgeService.kt`) with an ongoing notification, preventing Android OS from suspending the background process.

---

## 2. Directory Layout Inside the App Sandbox

```
/data/data/com.antigravity.pocketgravity/files/
├── runtime/
│   ├── glibc/
│   │   ├── ld-linux-aarch64.so.1       # Dynamic linker (chmod 755)
│   │   ├── libc.so.6                   # Core C library
│   │   ├── libdl.so.2                  # Dynamic library loading
│   │   ├── libm.so.6                   # Math library
│   │   ├── libpthread.so.0             # POSIX threads
│   │   ├── libresolv.so.2              # DNS resolver
│   │   └── librt.so.1                  # Real-time extensions
│   ├── bin/
│   │   └── agy                         # Official Antigravity CLI binary (chmod 755)
│   └── ca-certificates.crt             # Mozilla root CA certificates
├── home/                               # Target of $HOME
│   └── .gemini/
│       ├── antigravity-cli/
│       │   ├── antigravity-oauth-token # Raw OAuth access token
│       │   ├── oauth_credentials.json  # Full OAuth payload (refresh_token, expiry)
│       │   ├── settings.json           # Model & effort preferences
│       │   └── conversations/          # Saved session transcripts
│       ├── oauth_creds.json            # Secondary token lookup path
│       └── google_accounts.json        # Active Google account email
├── workspace/                          # Target of active project files
│   └── README.md
└── tmp/                                # Target of $TMPDIR
```

---

## 3. Process Execution Pipeline

When executing any Antigravity CLI command (e.g. prompt turn or model listing), `RuntimeManager.buildProcess` constructs the command line:

```
/data/data/.../files/runtime/glibc/ld-linux-aarch64.so.1 \
  --library-path /data/data/.../files/runtime/glibc \
  /data/data/.../files/runtime/bin/agy \
  -p "<prompt>" \
  --output-format stream-json \
  --dangerously-skip-permissions
```

### Environment Variable Injection:
- `HOME=/data/data/.../files/home`
- `TMPDIR=/data/data/.../files/tmp`
- `PATH=/data/data/.../files/runtime/bin:/system/bin:/system/xbin`
- `SSL_CERT_FILE=/data/data/.../files/runtime/ca-certificates.crt`
- `LANG=en_US.UTF-8`
- `LC_ALL=en_US.UTF-8`

---

## 4. First-Launch Extraction Workflow

1. App boots `MainActivity`.
2. `RuntimeManager.init(context)` is called asynchronously in a background thread.
3. Checks if `ld-linux-aarch64.so.1` exists in `files/runtime/glibc/`. If missing, extracts all files from `assets/glibc_arm64/` and marks them executable (`chmod 755`).
4. Checks if `ca-certificates.crt` exists. If missing, extracts from `assets/ca-certificates.crt`.
5. Checks if `agy` exists in `files/runtime/bin/`. If missing or corrupt, streams and un-tars `assets/bin/agy_arm64.tar.gz` and sets executable bit.
6. Initializes `files/workspace/` with welcome project files if empty.
7. Dispatches initialization completion event to UI.
