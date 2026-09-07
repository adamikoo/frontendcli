#!/usr/bin/env python3
"""
CLIFrontend Localhost Bridge Daemon
Connects Android CLIFrontend native app to Antigravity CLI, Ubuntu environment,
and the local workspace filesystem.
"""

import os
import sys
import json
import time
import pty
import select
import fcntl
import termios
import struct
import signal
import shutil
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
import subprocess
from pathlib import Path

PORT = int(os.environ.get("PORT", "8765"))

def resolve_agy_bin():
    env_agy = os.environ.get("AGY_BIN")
    if env_agy and os.path.exists(env_agy):
        return env_agy
    which_agy = shutil.which("agy")
    if which_agy:
        return which_agy
    candidates = [
        os.path.expanduser("~/.local/bin/agy"),
        "/root/.local/bin/agy",
        "/data/data/com.termux/files/home/.local/bin/agy",
        "/data/data/com.termux/files/usr/bin/agy",
        "/usr/local/bin/agy",
        "/usr/bin/agy"
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    return env_agy or "/root/.local/bin/agy"

def resolve_default_workspace():
    env_ws = os.environ.get("DEFAULT_WORKSPACE")
    if env_ws and os.path.exists(env_ws):
        return os.path.abspath(env_ws)
    candidates = [
        "/root",
        os.path.expanduser("~"),
        "/data/data/com.termux/files/home",
        os.getcwd()
    ]
    for c in candidates:
        if os.path.exists(c) and os.access(c, os.R_OK):
            return os.path.abspath(c)
    return os.path.abspath(os.getcwd())

def resolve_token_file():
    candidates = [
        os.path.expanduser("~/.gemini/antigravity-cli/antigravity-oauth-token"),
        "/root/.gemini/antigravity-cli/antigravity-oauth-token",
        "/data/data/com.termux/files/home/.gemini/antigravity-cli/antigravity-oauth-token"
    ]
    for c in candidates:
        if os.path.exists(c) and os.path.getsize(c) > 0:
            return c
    return candidates[0]

AGY_BIN = resolve_agy_bin()
DEFAULT_WORKSPACE = resolve_default_workspace()
SETTINGS_FILE = os.path.expanduser("~/.gemini/antigravity-cli/settings.json")
TOKEN_FILE = resolve_token_file()

current_workspace = os.path.abspath(DEFAULT_WORKSPACE)
active_agent_process = None

def get_agy_version():
    try:
        res = subprocess.run([AGY_BIN, "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=3)
        return res.stdout.strip()
    except Exception:
        return "unknown"

def get_models():
    try:
        res = subprocess.run([AGY_BIN, "models"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=10)
        lines = res.stdout.strip().splitlines()
        models = []
        for line in lines:
            line = line.strip()
            if not line or line.startswith("⠋") or line.startswith("⠙") or line.startswith("⠹") or "Fetching" in line:
                continue
            parts = line.split(None, 1)
            if len(parts) >= 2:
                models.append({"id": parts[0], "name": parts[1]})
            elif len(parts) == 1:
                models.append({"id": parts[0], "name": parts[0]})
        return models
    except Exception as e:
        return [
            {"id": "gemini-3.8-flash-high", "name": "Gemini 3.8 Flash (High)"},
            {"id": "gemini-3.8-flash-medium", "name": "Gemini 3.8 Flash (Medium)"},
            {"id": "gemini-3.8-flash-low", "name": "Gemini 3.8 Flash (Low)"},
            {"id": "gemini-3.7-flash-high", "name": "Gemini 3.7 Flash (High)"},
            {"id": "gemini-3.1-pro-high", "name": "Gemini 3.1 Pro (High)"},
            {"id": "claude-sonnet-4-6", "name": "Claude Sonnet 4.6 (Thinking)"},
        ]

def get_settings():
    if os.path.exists(SETTINGS_FILE):
        try:
            with open(SETTINGS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"model": "Gemini 3.8 Flash (Medium)", "permissions": {"allow": []}}

def save_settings(data):
    try:
        os.makedirs(os.path.dirname(SETTINGS_FILE), exist_ok=True)
        with open(SETTINGS_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        return True
    except Exception:
        return False

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

class BridgeHandler(BaseHTTPRequestHandler):
    def send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_cors_headers()
        self.end_headers()

    def send_json(self, data, code=200):
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_cors_headers()
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        global current_workspace

        if path == "/" or path == "/api/health":
            termux_detected = os.path.exists("/data/data/com.termux")
            ubuntu_detected = os.path.exists("/etc/os-release")
            agy_detected = os.path.exists(AGY_BIN)
            auth_detected = os.path.exists(TOKEN_FILE) and os.path.getsize(TOKEN_FILE) > 0
            settings = get_settings()

            self.send_json({
                "status": "ok",
                "termux": termux_detected,
                "ubuntu": ubuntu_detected,
                "antigravity": {
                    "installed": agy_detected,
                    "version": get_agy_version() if agy_detected else None,
                    "path": AGY_BIN,
                },
                "auth": {
                    "authenticated": auth_detected,
                    "token_file": TOKEN_FILE
                },
                "workspace": current_workspace,
                "model": settings.get("model", "Gemini 3.8 Flash (Medium)"),
                "effort": settings.get("effort", "medium"),
                "timestamp": time.time()
            })

        elif path == "/api/models":
            models = get_models()
            settings = get_settings()
            current_model = settings.get("model", "")
            for m in models:
                m["selected"] = (m["id"] == current_model or m["name"] == current_model)
            self.send_json({"models": models, "current": current_model})

        elif path == "/api/effort":
            settings = get_settings()
            self.send_json({
                "current": settings.get("effort", "medium"),
                "options": ["low", "medium", "high"]
            })

        elif path == "/api/auth/status":
            auth_detected = os.path.exists(TOKEN_FILE) and os.path.getsize(TOKEN_FILE) > 0
            self.send_json({"authenticated": auth_detected, "token_file": TOKEN_FILE})

        elif path == "/api/workspace":
            self.send_json({"workspace": current_workspace})

        elif path == "/api/fs/tree":
            target = query.get("path", [current_workspace])[0]
            target = os.path.abspath(target)
            include_hidden = query.get("include_hidden", ["false"])[0].lower() == "true"

            if not os.path.exists(target):
                self.send_json({"error": "Path not found", "path": target}, 404)
                return

            items = []
            try:
                for entry in sorted(os.scandir(target), key=lambda e: (not e.is_dir(), e.name.lower())):
                    if not include_hidden and entry.name.startswith("."):
                        continue
                    try:
                        stat = entry.stat()
                        items.append({
                            "name": entry.name,
                            "path": entry.path,
                            "is_dir": entry.is_dir(),
                            "size": stat.st_size if not entry.is_dir() else 0,
                            "modified": stat.st_mtime
                        })
                    except Exception:
                        pass
                self.send_json({"path": target, "items": items})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/fs/file":
            target = query.get("path", [""])[0]
            target = os.path.abspath(target)
            if not os.path.exists(target) or os.path.isdir(target):
                self.send_json({"error": "File not found", "path": target}, 404)
                return
            try:
                with open(target, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()
                self.send_json({
                    "path": target,
                    "name": os.path.basename(target),
                    "size": len(content),
                    "content": content
                })
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/git/changes":
            target = query.get("workspace", [current_workspace])[0]
            try:
                res = subprocess.run(["git", "status", "--porcelain"], cwd=target, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=5)
                files = []
                for line in res.stdout.splitlines():
                    if len(line) >= 4:
                        status = line[:2].strip()
                        filepath = line[3:].strip()
                        files.append({"status": status, "path": filepath})
                self.send_json({"is_git": True, "workspace": target, "changes": files})
            except Exception as e:
                self.send_json({"is_git": False, "workspace": target, "changes": [], "error": str(e)})

        elif path == "/api/git/diff":
            target = query.get("workspace", [current_workspace])[0]
            filepath = query.get("path", [""])[0]
            cmd = ["git", "diff", "HEAD"]
            if filepath:
                cmd.extend(["--", filepath])
            try:
                res = subprocess.run(cmd, cwd=target, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=5)
                self.send_json({"diff": res.stdout, "path": filepath})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/sessions":
            conv_dir = os.path.expanduser("~/.gemini/antigravity-cli/conversations")
            sessions = []
            if os.path.exists(conv_dir):
                for f in sorted(os.listdir(conv_dir), reverse=True):
                    if f.endswith(".json"):
                        sessions.append({"id": f[:-5], "file": f})
            self.send_json({"sessions": sessions})

        else:
            self.send_json({"error": "Endpoint not found"}, 404)

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(body) if body else {}
        except Exception:
            payload = {}

        global current_workspace, active_agent_process

        if path == "/api/workspace":
            new_ws = payload.get("workspace", "")
            if new_ws and os.path.exists(new_ws):
                current_workspace = os.path.abspath(new_ws)
                self.send_json({"status": "ok", "workspace": current_workspace})
            else:
                self.send_json({"error": "Invalid workspace directory"}, 400)

        elif path == "/api/effort":
            effort = payload.get("effort", "medium")
            settings = get_settings()
            settings["effort"] = effort
            save_settings(settings)
            self.send_json({"status": "ok", "effort": effort})

        elif path == "/api/model":
            model = payload.get("model", "")
            settings = get_settings()
            settings["model"] = model
            save_settings(settings)
            self.send_json({"status": "ok", "model": model})

        elif path == "/api/fs/file":
            filepath = payload.get("path", "")
            content = payload.get("content", "")
            if not filepath:
                self.send_json({"error": "Missing path"}, 400)
                return
            try:
                os.makedirs(os.path.dirname(filepath), exist_ok=True)
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(content)
                self.send_json({"status": "ok", "path": filepath, "size": len(content)})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/fs/create":
            filepath = payload.get("path", "")
            is_dir = payload.get("is_dir", False)
            if not filepath:
                self.send_json({"error": "Missing path"}, 400)
                return
            try:
                if is_dir:
                    os.makedirs(filepath, exist_ok=True)
                else:
                    os.makedirs(os.path.dirname(filepath), exist_ok=True)
                    with open(filepath, "a", encoding="utf-8") as f:
                        pass
                self.send_json({"status": "ok", "path": filepath, "is_dir": is_dir})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/fs/delete":
            filepath = payload.get("path", "")
            if not filepath or not os.path.exists(filepath):
                self.send_json({"error": "File or folder not found"}, 404)
                return
            try:
                if os.path.isdir(filepath):
                    shutil.rmtree(filepath)
                else:
                    os.remove(filepath)
                self.send_json({"status": "ok", "path": filepath})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/fs/rename":
            src = payload.get("src", "")
            dest = payload.get("dest", "")
            if not src or not dest or not os.path.exists(src):
                self.send_json({"error": "Invalid source or destination"}, 400)
                return
            try:
                os.rename(src, dest)
                self.send_json({"status": "ok", "src": src, "dest": dest})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/terminal/exec":
            cmd = payload.get("command", "")
            cwd = payload.get("cwd", current_workspace)
            if not cmd:
                self.send_json({"error": "Empty command"}, 400)
                return
            try:
                res = subprocess.run(cmd, shell=True, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=30)
                self.send_json({
                    "stdout": res.stdout,
                    "stderr": res.stderr,
                    "exit_code": res.returncode
                })
            except subprocess.TimeoutExpired:
                self.send_json({"error": "Command timed out after 30 seconds"}, 504)
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/agent/stop":
            if active_agent_process and active_agent_process.poll() is None:
                try:
                    active_agent_process.terminate()
                    active_agent_process.wait(timeout=2)
                except Exception:
                    active_agent_process.kill()
                self.send_json({"status": "stopped"})
            else:
                self.send_json({"status": "no_active_agent"})

        elif path == "/api/agent/stream":
            prompt = payload.get("prompt", "")
            model = payload.get("model")
            effort = payload.get("effort")
            conversation_id = payload.get("conversation_id")
            dangerously_skip_permissions = payload.get("dangerously_skip_permissions", True)

            if not prompt:
                self.send_json({"error": "Missing prompt"}, 400)
                return

            cmd = [
                AGY_BIN,
                "-p", prompt,
                "--output-format", "stream-json"
            ]
            if model:
                cmd.extend(["--model", model])
            if effort:
                cmd.extend(["--effort", effort])
            if conversation_id:
                cmd.extend(["--conversation", conversation_id])
            if dangerously_skip_permissions:
                cmd.append("--dangerously-skip-permissions")

            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_cors_headers()
            self.end_headers()

            try:
                active_agent_process = subprocess.Popen(
                    cmd,
                    cwd=current_workspace,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1
                )

                for line in iter(active_agent_process.stdout.readline, ''):
                    if not line:
                        break
                    line = line.strip()
                    if line:
                        sse_payload = f"data: {line}\n\n".encode("utf-8")
                        self.wfile.write(sse_payload)
                        self.wfile.flush()

                active_agent_process.wait()
                exit_code = active_agent_process.returncode
                self.wfile.write(f"data: {json.dumps({'event': 'exit', 'exit_code': exit_code})}\n\n".encode("utf-8"))
                self.wfile.flush()
            except Exception as e:
                err_data = json.dumps({"event": "error", "error": str(e)})
                self.wfile.write(f"data: {err_data}\n\n".encode("utf-8"))
                self.wfile.flush()
            finally:
                active_agent_process = None

        else:
            self.send_json({"error": "Endpoint not found"}, 404)

PID_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bridge.pid")

def cleanup_pid():
    if os.path.exists(PID_FILE):
        try:
            os.remove(PID_FILE)
        except Exception:
            pass

def run():
    cleanup_pid()
    try:
        with open(PID_FILE, "w") as f:
            f.write(str(os.getpid()))
    except Exception:
        pass

    server_address = ("0.0.0.0", PORT)
    try:
        httpd = ThreadedHTTPServer(server_address, BridgeHandler)
    except OSError as e:
        print(f"[ERROR] Failed to bind to port {PORT}: {e}", file=sys.stderr)
        print(f"[HINT] Another process may already be using port {PORT}.", file=sys.stderr)
        print(f"       Run './start.sh stop' or kill the existing process and retry.", file=sys.stderr)
        cleanup_pid()
        sys.exit(1)

    print(f"==================================================")
    print(f"CLIFrontend Bridge Daemon active!")
    print(f"  Local URL:  http://127.0.0.1:{PORT}")
    print(f"  Bind:       0.0.0.0:{PORT}")
    print(f"  Workspace:  {current_workspace}")
    print(f"  AGY Binary: {AGY_BIN} (found: {os.path.exists(AGY_BIN)})")
    print(f"  PID:        {os.getpid()}")
    print(f"==================================================")
    sys.stdout.flush()

    def signal_handler(signum, frame):
        print(f"\nShutting down CLIFrontend Bridge (signal {signum})...")
        cleanup_pid()
        try:
            httpd.server_close()
        except Exception:
            pass
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    try:
        httpd.serve_forever()
    except Exception as e:
        print(f"\nBridge server terminated: {e}")
    finally:
        cleanup_pid()
        try:
            httpd.server_close()
        except Exception:
            pass

if __name__ == "__main__":
    run()
