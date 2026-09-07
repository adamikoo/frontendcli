# CLIFrontend — Native Android Mobile IDE for Antigravity CLI

**CLIFrontend** is a genuine, high-performance native Android IDE and mobile interface for the **Google Antigravity CLI (`agy`)** running inside Termux & Ubuntu on Android (`aarch64`).

```
Android Phone
│
├── CLIFrontend Native Android App
│   ├── Mobile IDE UI (Developer-focused Dark Theme)
│   ├── Agent Chat & Live NDJSON Streaming Interface
│   ├── Multi-Tab Code Editor (Line numbers, save, quick keys)
│   ├── Real Filesystem Explorer (Create, rename, delete, tree)
│   ├── Unified Git Changes & Diff Viewer
│   ├── Interactive Terminal Console (ANSI styling)
│   ├── Dynamic Model & Thinking Effort Selector
│   ├── 5-Tier Diagnostics & Reconnect Engine
│   └── Settings
│
└── Termux (Android Localhost)
    └── Ubuntu PRoot Container
        ├── Local Bridge Daemon (http://127.0.0.1:8765)
        └── Antigravity CLI (agy 1.1.27)
```

---

## 1. Features

- **Genuine Native Android Experience**: Built with modern Kotlin, Android SDK 34, native UI components, and developer-grade dark theme (slate/navy palette). No fake WebView wraps.
- **Deep Antigravity Integration**: Communicates directly with the real installed `agy` binary (`1.1.27`) via its native `--input-format stream-json --output-format stream-json` protocol.
- **Real-Time Streaming**: Live streaming token deltas, tool calls (`view_file`, `replace_file_content`, `run_command`), step status, and token usage metrics.
- **Dynamic Model Catalog**: Dynamically queries `agy models` from the installed environment (`Gemini 3.8 Flash`, `Gemini 3.7 Flash`, `Gemini 3.1 Pro`, `Claude Sonnet 4.6`, `GPT-OSS 120B`, etc.).
- **Thinking / Reasoning Effort Controls**: Allows toggling reasoning effort directly (`low`, `medium`, `high`) mapping to `agy --effort`.
- **Full Code Editor**: Multiple file tabs, line number gutter, syntax styling, dirty indicator (`*`), direct save back to the Ubuntu filesystem, search bar, and programmer accessory key strip (`{`, `}`, `(`, `)`, `[`, `]`, `;`, `Tab`, etc.).
- **Real Filesystem Explorer**: Direct access to Ubuntu workspaces (`/root` or project paths), with folder navigation, file size/date, file creation, deletion, and renaming.
- **Unified Git Changes & Diff Viewer**: Displays Git modified/added/deleted files with colored diff views (green additions, red deletions, chunk headers).
- **Interactive Terminal**: Monospace shell console with ANSI VT100 colors, quick action keys (`Ctrl+C`, `Clear`, `ls -la`, `pwd`, `git status`), and persistent working directory sync.
- **Zero-Setup Detection**: Automatically probes Termux, Ubuntu container, Antigravity binary, OAuth credentials, and bridge daemon.

---

## 2. Zero-Setup Flow

The zero-setup experience operates seamlessly across five tiers:

1. **Install & Launch**: Install `CLIFrontend.apk` and open it.
2. **Detection**:
   - The app probes `http://127.0.0.1:8765/api/health`.
   - If the bridge daemon is running, it connects immediately (<100ms) and opens the active workspace.
   - If offline, the built-in Diagnostics screen displays status indicators for each tier and allows one-tap reconnect.
3. **Authentication**:
   - Existing credentials in `~/.gemini/antigravity-cli/antigravity-oauth-token` are automatically detected and reused.
   - If unauthenticated, the app initiates the legitimate Antigravity OAuth flow and launches the browser for Google login.

---

## 3. Architecture & Security

- **Localhost Loopback IPC**: Termux and PRoot share the Android Linux kernel network namespace. The bridge daemon listens strictly on `127.0.0.1:8765`.
- **No Remote Exposure**: No ports are exposed to the public internet or local Wi-Fi.
- **Credential Protection**: Google OAuth tokens remain strictly inside the user's container directory (`~/.gemini/antigravity-cli/antigravity-oauth-token`).
- **Path Sanitization**: Filesystem operations are strictly bound to permitted workspace trees to prevent directory traversal.

---

## 4. Project Layout

```
/downloads/clifrontend/
├── CLIFrontend.apk                  # Release installable APK
├── README.md                        # Documentation
├── release.keystore                 # Release signing keystore
├── app/
│   ├── build/                       # Build artifacts and DEX
│   └── src/main/
│       ├── AndroidManifest.xml      # App manifest (API 26-34)
│       ├── res/                     # Vector icons, colors, styles, layouts
│       └── kotlin/com/antigravity/clifrontend/
│           ├── MainActivity.kt      # Main IDE activity & navigation
│           ├── api/
│           │   ├── BridgeClient.kt  # Localhost REST & SSE stream client
│           │   └── Models.kt        # Data structures & JSON schemas
│           └── ui/
│               ├── AgentPanel.kt    # Agent chat & streaming canvas
│               ├── EditorPanel.kt   # Multi-tab code editor
│               ├── ExplorerPanel.kt # Filesystem tree & manager
│               ├── ChangesPanel.kt  # Git changes & diff viewer
│               ├── TerminalPanel.kt # Interactive ANSI terminal
│               ├── DiagnosticsPanel.kt # 5-tier diagnostic & setup wizard
│               ├── SettingsPanel.kt # App & model configuration
│               └── Components.kt    # ANSI parser & diff colorizer
├── bridge/
│   ├── server.py                    # Localhost Python bridge daemon
│   └── start.sh                     # Daemon launcher script
├── docs/
│   ├── ANTIGRAVITY_INTERFACE.md     # Full agy protocol specification
│   ├── ARCHITECTURE.md              # Multi-tier system architecture
│   ├── TERMUX_BRIDGE.md             # Termux & Ubuntu integration guide
│   ├── BUILD.md                     # Build instructions
│   └── TROUBLESHOOTING.md           # Diagnostics & troubleshooting
└── scripts/
    └── build.sh                     # 1-command APK compilation script
```

---

## 5. Building the APK

To rebuild the APK at any time, run:
```bash
/downloads/clifrontend/scripts/build.sh
```
This automatically compiles resources, runs Kotlin compilation with Java 17, generates Dalvik DEX via D8, 4-byte page-aligns the archive, signs with release v2/v3 signatures, and outputs:
- `/downloads/clifrontend/CLIFrontend.apk`
- `/sdcard/Download/CLIFrontend.apk` (directly accessible in Android file manager)

---

## 6. Running the Bridge

To start the bridge daemon:
```bash
/downloads/clifrontend/bridge/start.sh
```
Or directly:
```bash
python3 /downloads/clifrontend/bridge/server.py
```

---

## 7. Known Android System Constraints

1. **Localhost Binding**: Android requires `android:usesCleartextTraffic="true"` in the manifest to allow standard HTTP to `127.0.0.1` without HTTPS certificate requirements. This is preconfigured.
2. **Shared Storage vs POSIX**: Android's `/sdcard` uses Android MediaProvider with restricted UNIX file permissions (cannot chmod/symlink). For best results with Git and Antigravity, workspaces should reside inside PRoot (`/root` or `/home`) or Termux app storage (`/data/data/com.termux`).
