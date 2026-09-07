# Antigravity CLI (`agy`) Interface Specification

This document details the interface, execution model, streaming protocol, authentication state, and configuration of the Google Antigravity CLI (`agy`) as installed and verified in the Termux/Ubuntu environment.

## 1. Binary & System Environment
- **Executable**: `/root/.local/bin/agy` (also accessible as `agy`)
- **Version**: `1.1.27`
- **Host Architecture**: `aarch64` GNU/Linux (PRoot-Distro Ubuntu 26.04 under Android Termux)
- **Configuration Directory**: `~/.gemini/antigravity-cli/`
- **Settings File**: `~/.gemini/antigravity-cli/settings.json`
- **OAuth Token**: `~/.gemini/antigravity-cli/antigravity-oauth-token`
- **Conversations & History**: `~/.gemini/antigravity-cli/conversations/` and `history.jsonl`

---

## 2. Authentication State & Login Flow

### Detection
The CLI stores its OAuth credential at:
```
~/.gemini/antigravity-cli/antigravity-oauth-token
```
- When this file is present and contains a valid token, running `agy models` or `agy -p "..."` runs immediately without any login prompt.
- If the token is absent or expired, `agy` prompts the user with an OAuth authentication URL (e.g., `https://accounts.google.com/o/oauth2/...`).

### Frontend Handling
1. **Preserve Existing Login**: If `antigravity-oauth-token` exists, CLIFrontend detects it immediately and bypasses any login prompt.
2. **First-Run / Unauthenticated**:
   - The bridge triggers the legitimate `agy` login flow.
   - It captures the authentication URL emitted by `agy`.
   - Android's native browser or Custom Tabs opens the Google OAuth URL.
   - Upon completion, the token file is generated and detected, transitioning the app to the IDE view.

---

## 3. Command-Line Options & Execution Flags

| Flag | Purpose | Values / Format |
|---|---|---|
| `--input-format` | Input stream format | `text`, `stream-json` (NDJSON per line) |
| `--output-format` | Output stream format | `text`, `json`, `stream-json` |
| `--model` | Model override | E.g. `gemini-3.8-flash-high`, `gemini-3.8-flash-medium` |
| `--effort` | Reasoning / thinking effort | `low`, `medium`, `high` |
| `--mode` | Agent execution mode | `accept-edits`, `plan` |
| `--continue` (`-c`) | Continue most recent conversation | Boolean |
| `--conversation <id>` | Resume conversation by UUID | UUID string |
| `--add-dir <path>` | Add directory to workspace | Path (repeatable) |
| `--dangerously-skip-permissions` | Auto-approve tool permissions | Flag |
| `--print` (`-p`) | Run single prompt non-interactively | String |
| `--prompt-interactive` (`-i`) | Run initial prompt and remain interactive | String |
| `--sandbox` | Run in sandbox with terminal restrictions | Flag |

---

## 4. Machine-Readable Streaming Protocol (`stream-json`)

When invoked with:
```bash
agy -p "<prompt>" --input-format stream-json --output-format stream-json
```
The CLI emits newline-delimited JSON (`NDJSON`) events on stdout.

### Event Schema

#### 1. Initialization Event (`init`)
Emitted at session startup:
```json
{
  "event": "init",
  "conversation_id": "44c01203-d704-4093-8c6d-f4b5e11eaea0",
  "init": {
    "cwd": "/root",
    "tools": [
      "view_file", "write_to_file", "replace_file_content",
      "run_command", "send_command_input", "list_dir",
      "find_by_name", "grep_search", "ask_question",
      "ask_permission", "manage_task", "search_web"
    ],
    "permission_mode": "request-review"
  }
}
```

#### 2. Turn Step Updates (`step_update`)
Emitted for agent progress, text deltas, tool calls, and completions:
```json
{
  "event": "step_update",
  "step_update": {
    "conversation_id": "44c01203-d704-4093-8c6d-f4b5e11eaea0",
    "step_index": 1,
    "state": "ACTIVE",
    "step_type": "agent_response",
    "text_delta": "Refactoring authentication..."
  }
}
```
Step completion includes token usage metrics:
```json
{
  "event": "step_update",
  "step_update": {
    "conversation_id": "44c01203-d704-4093-8c6d-f4b5e11eaea0",
    "step_index": 1,
    "state": "DONE",
    "step_type": "agent_response",
    "text_delta": "\n",
    "duration_seconds": 2.237,
    "usage": {
      "input_tokens": 13404,
      "output_tokens": 24,
      "thinking_tokens": 23,
      "cache_read_tokens": 0,
      "total_tokens": 13428
    }
  }
}
```

#### 3. Final Result (`result`)
```json
{
  "event": "result",
  "result": {
    "conversation_id": "44c01203-d704-4093-8c6d-f4b5e11eaea0",
    "status": "SUCCESS",
    "response": "...",
    "duration_seconds": 2.436,
    "num_turns": 1,
    "usage": {
      "input_tokens": 13404,
      "output_tokens": 24,
      "thinking_tokens": 23,
      "total_tokens": 13428
    }
  }
}
```

---

## 5. Model Catalog

Dynamically queried via `agy models`:
- `gemini-3.8-flash-high` — Gemini 3.8 Flash (High)
- `gemini-3.8-flash-medium` — Gemini 3.8 Flash (Medium)
- `gemini-3.8-flash-low` — Gemini 3.8 Flash (Low)
- `gemini-3.7-flash-high` — Gemini 3.7 Flash (High)
- `gemini-3.7-flash-medium` — Gemini 3.7 Flash (Medium)
- `gemini-3.7-flash-low` — Gemini 3.7 Flash (Low)
- `gemini-3.6-flash-high` — Gemini 3.6 Flash (High)
- `gemini-3.6-flash-medium` — Gemini 3.6 Flash (Medium)
- `gemini-3.6-flash-low` — Gemini 3.6 Flash (Low)
- `gemini-3.1-pro-high` — Gemini 3.1 Pro (High)
- `gemini-3.1-pro-low` — Gemini 3.1 Pro (Low)
- `claude-sonnet-4-6` — Claude Sonnet 4.6 (Thinking)
- `claude-opus-4-6-thinking` — Claude Opus 4.6 (Thinking)
- `gpt-oss-120b-medium` — GPT-OSS 120B (Medium)

---

## 6. Reasoning / Thinking Effort Levels
Configurable in CLI and `settings.json`:
- `low`
- `medium` (default)
- `high`

---

## 7. Permission Model & Approvals
Configured via `~/.gemini/antigravity-cli/settings.json`:
```json
{
  "permissions": {
    "allow": [
      "command(echo)",
      "command(ls)",
      "command(mkdir)",
      "command(which)",
      "command(agy)",
      "command(cat)"
    ]
  }
}
```
When tools or commands execute outside the allow-list, the CLI requests review (`ask_permission` / `ask_custom_permission`). The bridge translates these requests into native Android approval dialogs or notifications.
