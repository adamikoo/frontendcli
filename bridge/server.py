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
import signal
import shutil
import re
import hashlib
import base64
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
import subprocess
import threading
from pathlib import Path

PORT = int(os.environ.get("PORT", "8765"))

def resolve_agy_bin():
    env_agy = os.environ.get("AGY_BIN")
    if env_agy and os.path.exists(env_agy):
        return env_agy
    which_agy = shutil.which("agy") or shutil.which("agy.exe")
    if which_agy:
        return which_agy
    candidates = [
        os.path.abspath("agy.exe"),
        os.path.abspath("staging_x64/antigravity"),
        os.path.expanduser("~/.local/bin/agy"),
        "/root/.local/bin/agy",
        "/root/termux_home/.local/bin/agy",
        "/data/data/com.termux/files/home/.local/bin/agy",
        "/data/data/com.termux/files/usr/bin/agy",
        "/usr/local/bin/agy",
        "/usr/bin/agy",
        "/bin/agy"
    ]
    for c in candidates:
        if os.path.exists(c) and os.access(c, os.X_OK):
            return c
    for c in candidates:
        if os.path.exists(c):
            return c
    return env_agy or "agy"

def resolve_default_workspace():
    env_ws = os.environ.get("DEFAULT_WORKSPACE")
    if env_ws and os.path.exists(env_ws):
        return os.path.abspath(env_ws)
    cwd = os.getcwd()
    if os.path.exists(os.path.join(cwd, ".git")) or os.path.exists(os.path.join(cwd, "CLIFrontend.apk")):
        return os.path.abspath(cwd)
    # Check if parent has .git (e.g. if run from bridge/)
    parent = os.path.dirname(cwd)
    if os.path.exists(os.path.join(parent, ".git")) or os.path.exists(os.path.join(parent, "CLIFrontend.apk")):
        return os.path.abspath(parent)
    candidates = [
        "/root",
        "/data/data/com.termux/files/home",
        os.path.expanduser("~"),
        cwd
    ]
    for c in candidates:
        if os.path.exists(c) and os.access(c, os.R_OK):
            return os.path.abspath(c)
    return os.path.abspath(cwd)

def resolve_token_file():
    candidates = [
        os.path.expanduser("~/.gemini/antigravity-cli/antigravity-oauth-token"),
        os.path.expanduser("~/.gemini/antigravity-cli/oauth_credentials.json"),
        os.path.expanduser("~/.gemini/oauth_creds.json"),
        os.path.expanduser("~/.config/gcloud/application_default_credentials.json"),
        "/root/.gemini/antigravity-cli/antigravity-oauth-token",
        "/root/.gemini/antigravity-cli/oauth_credentials.json",
        "/root/.gemini/oauth_creds.json",
        "/root/.config/gcloud/application_default_credentials.json",
        "/data/data/com.termux/files/home/.gemini/antigravity-cli/antigravity-oauth-token",
        "/data/data/com.termux/files/home/.gemini/antigravity-cli/oauth_credentials.json",
        "/data/data/com.termux/files/home/.gemini/oauth_creds.json"
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
last_pkce_verifier = ""

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
    protocol_version = "HTTP/1.1"

    def send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Connection", "close")

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
        try:
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            query = urllib.parse.parse_qs(parsed.query)

            global current_workspace

            if path == "/" or path == "/index.html" or path == "/web":
                web_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "index.html")
                if os.path.exists(web_file):
                    with open(web_file, "rb") as f:
                        content = f.read()
                    self.send_response(200)
                    self.send_header("Content-Type", "text/html; charset=utf-8")
                    self.send_header("Content-Length", str(len(content)))
                    self.send_cors_headers()
                    self.end_headers()
                    self.wfile.write(content)
                    return

            if path == "/api/health":
                termux_detected = os.path.exists("/data/data/com.termux")
                ubuntu_detected = os.path.exists("/etc/os-release")
                agy_detected = os.path.exists(AGY_BIN)
                tf = resolve_token_file()
                auth_detected = os.path.exists(tf) and os.path.getsize(tf) > 0
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
                        "token_file": tf
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
                tf = resolve_token_file()
                auth_detected = os.path.exists(tf) and os.path.getsize(tf) > 0
                self.send_json({"authenticated": auth_detected, "token_file": tf})

            elif path == "/api/workspace":
                self.send_json({"workspace": current_workspace})

            elif path == "/api/mcps":
                mcp_file = os.path.expanduser("~/.gemini/antigravity-cli/mcp_config.json")
                if os.path.exists(mcp_file):
                    try:
                        with open(mcp_file, "r", encoding="utf-8") as f:
                            self.send_json(json.load(f))
                            return
                    except Exception:
                        pass
                self.send_json({
                    "mcpServers": {
                        "chrome-devtools-plugin": {
                            "command": "npx",
                            "args": ["-y", "chrome-devtools-mcp@latest"],
                            "enabled": True
                        },
                        "graphify": {
                            "command": "graphify",
                            "args": ["serve"],
                            "enabled": True
                        }
                    }
                })

            elif path == "/api/customizations":
                skills = []
                skill_dirs = [
                    os.path.expanduser("~/.gemini/config/skills"),
                    os.path.expanduser("~/.agents/skills"),
                    os.path.join(current_workspace, ".agents/skills")
                ]
                for sd in skill_dirs:
                    if os.path.isdir(sd):
                        for name in os.listdir(sd):
                            p = os.path.join(sd, name)
                            if os.path.isdir(p):
                                skills.append({
                                    "name": name,
                                    "path": p,
                                    "has_spec": os.path.exists(os.path.join(p, "SKILL.md"))
                                })
                if not skills:
                    for b in ["caveman", "a11y-architect", "tdd-guide", "seo", "security-reviewer", "graphify-windows"]:
                        skills.append({"name": b, "path": f"builtin/{b}", "has_spec": True})

                workflows = []
                wf_dirs = [
                    os.path.expanduser("~/.gemini/config/global_workflows"),
                    os.path.expanduser("~/.gemini/workflows"),
                    os.path.join(current_workspace, "workflows")
                ]
                for wd in wf_dirs:
                    if os.path.isdir(wd):
                        for name in os.listdir(wd):
                            if name.endswith(".md"):
                                workflows.append({
                                    "name": os.path.splitext(name)[0],
                                    "path": os.path.join(wd, name)
                                })
                if not workflows:
                    workflows = [
                        {"name": "ftp-upload", "command": "/ftp-upload"},
                        {"name": "sales-automator", "command": "/sales-automator"},
                        {"name": "goal", "command": "/goal"},
                        {"name": "grill-me", "command": "/grill-me"}
                    ]

                settings = get_settings()
                rules = [
                    {"name": "caveman", "description": "Ultra-compressed communication mode. Cuts token usage ~75%", "enabled": settings.get("rule_caveman", True)}
                ]
                gemini_md = os.path.join(current_workspace, "GEMINI.md")
                if os.path.exists(gemini_md):
                    rules.append({"name": "GEMINI.md (Workspace Rule)", "description": "Workspace-level rules and guidelines", "enabled": True})

                self.send_json({"skills": skills, "workflows": workflows, "rules": rules})

            elif path == "/api/limits":
                settings = get_settings()
                self.send_json({
                    "tier": settings.get("tier", "FREE"),
                    "credit_overcharge": settings.get("credit_overcharge", False),
                    "limits": {
                        "requests_per_day": 1500,
                        "requests_remaining": settings.get("requests_remaining", 1340),
                        "tokens_per_minute": 1000000,
                        "tokens_remaining": settings.get("tokens_remaining", 948200)
                    }
                })

            elif path == "/api/browser/settings":
                settings = get_settings()
                browser = settings.get("browser", {
                    "enable_browser_tools": True,
                    "javascript_policy": "Request Review",
                    "enable_notifications": True,
                    "enable_sounds": False,
                    "actuation_rules": ["*"]
                })
                self.send_json(browser)

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
        except Exception as e:
            import traceback
            traceback.print_exc()
            self.send_json({"error": str(e)}, 500)

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(body) if body else {}
        except Exception:
            payload = {}

        global current_workspace, active_agent_process, last_pkce_verifier

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
            src = payload.get("src") or payload.get("oldPath", "")
            dest = payload.get("dest") or payload.get("newPath", "")
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

        elif path == "/api/auth/logout":
            try:
                targets = [
                    TOKEN_FILE,
                    os.path.join(os.path.dirname(TOKEN_FILE), "oauth_credentials.json"),
                    os.path.expanduser("~/.gemini/antigravity-cli/antigravity-oauth-token"),
                    os.path.expanduser("~/.gemini/antigravity-cli/oauth_credentials.json"),
                    os.path.expanduser("~/.gemini/oauth_creds.json"),
                    os.path.expanduser("~/.gemini/antigravity/mcp_oauth_tokens.json"),
                    os.path.expanduser("~/.config/agy/credentials.json"),
                    os.path.expanduser("~/.gemini/google_accounts.json"),
                    os.path.expanduser("~/.config/gcloud/application_default_credentials.json"),
                    "/root/.gemini/antigravity-cli/antigravity-oauth-token",
                    "/root/.gemini/antigravity-cli/oauth_credentials.json",
                    "/root/.gemini/oauth_creds.json",
                    "/root/.gemini/antigravity/mcp_oauth_tokens.json",
                    "/root/.config/agy/credentials.json",
                    "/root/.gemini/google_accounts.json",
                    "/root/.config/gcloud/application_default_credentials.json",
                    "/data/data/com.termux/files/home/.gemini/antigravity-cli/antigravity-oauth-token",
                    "/data/data/com.termux/files/home/.gemini/oauth_creds.json"
                ]
                for t in targets:
                    if os.path.exists(t):
                        try:
                            os.remove(t)
                        except Exception:
                            pass
                settings = get_settings()
                if "modelProvider" in settings:
                    del settings["modelProvider"]
                    save_settings(settings)
                self.send_json({"status": "ok", "authenticated": False})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/auth/token":
            token = payload.get("token", "").strip()
            if not token:
                self.send_json({"error": "Empty token"}, 400)
                return
            
            try:
                if token.startswith("4/") or (len(token) > 40 and not token.startswith("AIza") and not token.startswith("ya29")):
                    # OAuth Authorization Code - exchange for tokens with Google
                    data = urllib.parse.urlencode({
                        "code": token,
                        "client_id": "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
                        "client_secret": "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",
                        "code_verifier": last_pkce_verifier,
                        "redirect_uri": "https://antigravity.google/oauth-callback",
                        "grant_type": "authorization_code"
                    }).encode("utf-8")
                    req = urllib.request.Request("https://oauth2.googleapis.com/token", data=data, headers={"Content-Type": "application/x-www-form-urlencoded"})
                    try:
                        with urllib.request.urlopen(req) as response:
                            resp_json = json.loads(response.read().decode("utf-8"))
                            access_token = resp_json.get("access_token")
                            expires_in = resp_json.get("expires_in", 3600)
                            resp_json["expires_at"] = int(time.time() * 1000) + (expires_in - 300) * 1000
                            creds_file = os.path.join(os.path.dirname(TOKEN_FILE), "oauth_credentials.json")
                            os.makedirs(os.path.dirname(creds_file), exist_ok=True)
                            with open(creds_file, "w", encoding="utf-8") as f:
                                json.dump(resp_json, f, indent=2)
                            if access_token:
                                with open(TOKEN_FILE, "w", encoding="utf-8") as f:
                                    f.write(access_token)
                                self.send_json({"status": "ok", "authenticated": True})
                                return
                    except urllib.error.HTTPError as e:
                        err_body = e.read().decode("utf-8")
                        print(f"Token exchange HTTPError: {err_body}")
                elif token.startswith("AIza"):
                    settings = get_settings()
                    settings["modelProvider"] = "gemini"
                    save_settings(settings)
                
                os.makedirs(os.path.dirname(TOKEN_FILE), exist_ok=True)
                with open(TOKEN_FILE, "w", encoding="utf-8") as f:
                    f.write(token)
                self.send_json({"status": "ok", "authenticated": True})
            except Exception as e:
                os.makedirs(os.path.dirname(TOKEN_FILE), exist_ok=True)
                with open(TOKEN_FILE, "w", encoding="utf-8") as f:
                    f.write(token)
                self.send_json({"status": "ok", "authenticated": True})

        elif path == "/api/auth/login":
            # Triggers real agy auth login and captures OAuth URL emitted by CLI
            if os.path.exists(AGY_BIN) or shutil.which(AGY_BIN):
                try:
                    proc = subprocess.Popen(
                        [AGY_BIN, "auth", "login"],
                        cwd=current_workspace,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True,
                        bufsize=1
                    )
                    oauth_url = None
                    start_t = time.time()
                    while time.time() - start_t < 6:
                        line = proc.stdout.readline()
                        if not line:
                            break
                        match = re.search(r"https://accounts\.google\.com/[^\s]+", line)
                        if match:
                            oauth_url = match.group(0)
                            break
                    if oauth_url:
                        self.send_json({"status": "ok", "url": oauth_url, "source": "agy_cli"})
                        return
                except Exception as e:
                    pass
            random_bytes = os.urandom(32)
            last_pkce_verifier = base64.urlsafe_b64encode(random_bytes).decode('utf-8').rstrip('=')
            challenge = base64.urlsafe_b64encode(hashlib.sha256(last_pkce_verifier.encode('utf-8')).digest()).decode('utf-8').rstrip('=')
            
            client_id = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
            redirect_uri = "https://antigravity.google/oauth-callback"
            scope = "https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/cclog https://www.googleapis.com/auth/experimentsandconfigs https://www.googleapis.com/auth/aicode openid"
            auth_url = (
                "https://accounts.google.com/o/oauth2/v2/auth?"
                + urllib.parse.urlencode({
                    "client_id": client_id,
                    "redirect_uri": redirect_uri,
                    "response_type": "code",
                    "scope": scope,
                    "code_challenge": challenge,
                    "code_challenge_method": "S256",
                    "access_type": "offline",
                    "prompt": "consent"
                })
            )
            self.send_json({"status": "ok", "url": auth_url, "source": "standalone_oauth"})

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

        elif path == "/api/mcps":
            try:
                mcp_file = os.path.expanduser("~/.gemini/antigravity-cli/mcp_config.json")
                os.makedirs(os.path.dirname(mcp_file), exist_ok=True)
                with open(mcp_file, "w", encoding="utf-8") as f:
                    json.dump(payload, f, indent=2)
                self.send_json({"status": "ok"})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/customizations":
            try:
                rule_name = payload.get("name")
                enabled = payload.get("enabled", True)
                settings = get_settings()
                settings[f"rule_{rule_name}"] = enabled
                save_settings(settings)
                self.send_json({"status": "ok"})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/limits":
            try:
                settings = get_settings()
                if "credit_overcharge" in payload:
                    settings["credit_overcharge"] = bool(payload["credit_overcharge"])
                save_settings(settings)
                self.send_json({"status": "ok"})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/browser/settings":
            try:
                settings = get_settings()
                settings["browser"] = payload
                save_settings(settings)
                self.send_json({"status": "ok"})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/upload/image":
            try:
                filename = payload.get("filename", f"image_{int(time.time()*1000)}.png")
                b64_data = payload.get("data", "")
                if not b64_data:
                    self.send_json({"error": "Missing base64 data"}, 400)
                    return
                if "," in b64_data:
                    b64_data = b64_data.split(",", 1)[1]
                img_bytes = base64.b64decode(b64_data)

                attach_dir = os.path.join(current_workspace, ".gemini", "attachments")
                os.makedirs(attach_dir, exist_ok=True)
                clean_name = re.sub(r"[^a-zA-Z0-9._-]", "_", filename)
                target_file = os.path.join(attach_dir, f"{int(time.time()*1000)}_{clean_name}")
                with open(target_file, "wb") as f:
                    f.write(img_bytes)

                rel_path = os.path.relpath(target_file, current_workspace).replace("\\", "/")
                self.send_json({
                    "status": "ok",
                    "path": rel_path,
                    "abs_path": target_file,
                    "size": len(img_bytes)
                })
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/agent/stream":
            prompt = payload.get("prompt", "")
            images = payload.get("images", [])
            if images:
                prompt = f"[Attached Media: {', '.join(images)}]\n\n{prompt}"
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

            # Dev fallback if AGY is not installed locally on this test PC
            if not os.path.exists(AGY_BIN) and not shutil.which(AGY_BIN):
                mock_chunks = [
                    "**[Bridge Dev Mode]** Antigravity CLI (`agy`) is not installed on this local testing machine.\n\n",
                    f"• **Received Prompt:** *{prompt}*\n",
                    f"• **Selected Model:** `{model or 'Gemini 3.8 Flash'}`\n",
                    f"• **Reasoning Effort:** `{effort or 'medium'}`\n",
                    f"• **Target Workspace:** `{current_workspace}`\n\n",
                    "When deployed to your phone in Termux or Ubuntu PRoot, the bridge will stream responses directly from your authenticated `agy` agent!\n"
                ]
                for chunk in mock_chunks:
                    payload_line = json.dumps({"event": "chunk", "text": chunk})
                    self.wfile.write(f"data: {payload_line}\n\n".encode("utf-8"))
                    self.wfile.flush()
                    time.sleep(0.08)
                self.wfile.write(f"data: {json.dumps({'event': 'exit', 'exit_code': 0})}\n\n".encode("utf-8"))
                self.wfile.flush()
                self.close_connection = True
                return

            try:
                active_agent_process = subprocess.Popen(
                    cmd,
                    cwd=current_workspace,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1
                )

                stderr_lines = []
                def drain_stderr():
                    try:
                        for eline in iter(active_agent_process.stderr.readline, ''):
                            if not eline:
                                break
                            stderr_lines.append(eline)
                    except Exception:
                        pass

                stderr_thread = threading.Thread(target=drain_stderr, daemon=True)
                stderr_thread.start()

                has_output = False
                for line in iter(active_agent_process.stdout.readline, ''):
                    if not line:
                        break
                    line = line.strip()
                    if line:
                        has_output = True
                        sse_payload = f"data: {line}\n\n".encode("utf-8")
                        self.wfile.write(sse_payload)
                        self.wfile.flush()

                active_agent_process.wait()
                stderr_thread.join(timeout=1.0)
                exit_code = active_agent_process.returncode

                if exit_code != 0:
                    err_msg = "".join(stderr_lines).strip() or f"Antigravity CLI exited with code {exit_code}"
                    err_payload = json.dumps({
                        "event": "error",
                        "error": f"Process exited with code {exit_code}:\n{err_msg}",
                        "exit_code": exit_code,
                        "stderr": err_msg
                    })
                    self.wfile.write(f"data: {err_payload}\n\n".encode("utf-8"))
                    self.wfile.flush()

                self.wfile.write(f"data: {json.dumps({'event': 'exit', 'exit_code': exit_code})}\n\n".encode("utf-8"))
                self.wfile.flush()
            except Exception as e:
                err_data = json.dumps({"event": "error", "error": str(e)})
                self.wfile.write(f"data: {err_data}\n\n".encode("utf-8"))
                self.wfile.flush()
            finally:
                self.close_connection = True
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
