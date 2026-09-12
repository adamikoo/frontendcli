# Known Good Baseline: Protocol & Execution Forensics

## 1. Proven Baseline Architecture

The known-working baseline runs `agy` under a glibc POSIX environment with the following chain:

```
Termux -> proot-distro login ubuntu -> bridge/start.sh -> bridge/server.py -> agy CLI -> Android App UI
```

### Process Chain Execution
1. **Ubuntu Invocation**:
   `proot-distro login ubuntu -- /downloads/clifrontend/bridge/start.sh start`
   - PRoot provides an emulated `/` mount point providing standard glibc binary lookups, `/etc/resolv.conf`, `/tmp`, and `/proc`.
2. **Bridge Startup**:
   - `start.sh` discovers `python3` inside the Ubuntu rootfs.
   - Executes: `nohup python3 server.py >> $HOME/.frontendcli/bridge.log 2>&1 &`
   - Listens on `http://127.0.0.1:8765`.
3. **Antigravity CLI Invocation**:
   - Executable path: `/root/.local/bin/agy` or `~/.local/bin/agy`
   - Invocation command for turn execution:
     ```bash
     agy -p "<prompt>" --output-format stream-json --dangerously-skip-permissions [--model <model>] [--effort <effort>] [--conversation <id>]
     ```
   - Working directory: The user's active project root (e.g. `/downloads/clifrontend` or `/sdcard/...`).
   - Standard POSIX environment variables:
     ```bash
     HOME=/root
     TMPDIR=/tmp
     LANG=en_US.UTF-8
     LC_ALL=en_US.UTF-8
     PATH=/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
     ```

---

## 2. Antigravity Stream-JSON Protocol (Empirically Verified)

Running `agy -p "..." --output-format stream-json` emits line-delimited JSON (NDJSON) lines over `stdout`:

### A. Initialization (`init`)
```json
{
  "event": "init",
  "conversation_id": "766886bc-264a-4d22-a3bd-968501ed046a",
  "init": {
    "cwd": "/root",
    "tools": ["ask_question", "write_to_file", "replace_file_content", "run_command", ...],
    "permission_mode": "request-review"
  }
}
```

### B. Step Update — User Input State (`step_update`)
```json
{
  "event": "step_update",
  "step_update": {
    "conversation_id": "766886bc-264a-4d22-a3bd-968501ed046a",
    "step_index": 0,
    "state": "DONE",
    "step_type": "user_input"
  }
}
```

### C. Step Update — Agent Response with Thinking & Text Delta
```json
{
  "event": "step_update",
  "step_update": {
    "conversation_id": "766886bc-264a-4d22-a3bd-968501ed046a",
    "step_index": 1,
    "state": "DONE",
    "step_type": "agent_response",
    "text_delta": "Hello, ready to build.\n",
    "duration_seconds": 1.7062,
    "usage": {
      "input_tokens": 16746,
      "output_tokens": 287,
      "thinking_tokens": 281,
      "cache_read_tokens": 8184,
      "total_tokens": 17033
    }
  }
}
```

### D. Result Event
```json
{
  "event": "result",
  "result": {
    "conversation_id": "766886bc-264a-4d22-a3bd-968501ed046a",
    "status": "SUCCESS",
    "response": "Hello, ready to build.\n",
    "duration_seconds": 13.429,
    "num_turns": 1,
    "usage": {
      "input_tokens": 16746,
      "output_tokens": 287,
      "thinking_tokens": 281,
      "cache_read_tokens": 8184,
      "total_tokens": 17033
    }
  }
}
```

---

## 3. Stdin/Stdout/Stderr Behavior

1. **Stdout**: Emits pure NDJSON events as shown above. The bridge pipes each line over SSE with `data: <json>\n\n`.
2. **Stderr**: Emits diagnostic logs, warnings, or errors. The bridge captures stderr and logs it or surfaces it if fatal.
3. **Exit Code**: `0` on successful prompt completion.
4. **SSE Termination**: The bridge emits `data: {"event":"exit","exit_code":0}\n\n` when the process exits.

---

## 4. Authentication Mechanism & Files

The official `agy` binary resolves credentials using the following priority:
1. `$HOME/.gemini/antigravity-cli/antigravity-oauth-token` (raw `ya29...` bearer token)
2. `$HOME/.gemini/oauth_creds.json` or `~/.gemini/antigravity-cli/oauth_credentials.json`
3. `$HOME/.gemini/google_accounts.json`

When these files are in place in `$HOME`, `agy` executes seamlessly without interactive popups.
