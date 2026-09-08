# Verification & Testing Protocol

## 1. Automated Test Suite Matrix

| Test Category | Target Component | Method | Gate Condition |
|---|---|---|---|
| **Binary Integrity** | `assets/bin/agy_arm64.tar.gz` | SHA-256 hash & ELF validation | Valid 64-bit ELF ARM binary |
| **Glibc Loader** | `assets/glibc_arm64/` | Dependency presence check | All 7 `.so` libraries present |
| **Asset Extraction** | `RuntimeManager.kt` | Extraction to `filesDir` | File sizes match, `canExecute()` is true |
| **Execution Smoke Test** | `agy --version` | ProcessBuilder execution via `ld-linux` | Returns `1.1.27` with exit code 0 |
| **Model Discovery** | `/api/models` | Subprocess `agy models` | Returns live Google Gemini & Claude catalog |
| **Protocol Stream** | `/api/agent/stream` | SSE streaming test turn | NDJSON `init`, `step_update`, and `result` received |
| **Workspace File I/O** | `/api/fs/*` | Create, Read, Write, Diff | Real filesystem modification verified |

---

## 2. Clean Device Validation Checklist
1. Deploy `CLIFrontend.apk` to an Android device where Termux is NOT installed.
2. Launch the application.
3. Observe extraction indicator: "Preparing Antigravity...".
4. Navigate to **Diagnostics** panel:
   - Verify Runtime: `✓ Ready`
   - Verify Glibc Loader: `✓ Detected`
   - Verify Antigravity CLI: `✓ 1.1.27`
   - Verify Bridge: `✓ Connected (127.0.0.1:8765)`
5. Authenticate via Google OAuth PKCE.
6. Submit test prompt in Agent Chat: `"Create a test file named hello.py with a hello world function"`.
7. Verify ACP UI displays:
   - Tool execution badge: `⚙ write_to_file`
   - Thinking chip: `💭 Thinking...`
   - Streaming text response: `"Created hello.py"`
8. Navigate to **Explorer** & **Editor**:
   - Verify `hello.py` appears in the file tree.
   - Open `hello.py` in the editor and verify contents.
9. Navigate to **Changes**:
   - Verify Git/workspace diff displays added file.
