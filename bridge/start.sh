#!/usr/bin/env bash
# ==============================================================================
# CLIFrontend Bridge Management Script (Termux & Ubuntu PRoot compatible)
# ==============================================================================

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PORT="${PORT:-8765}"

# Ensure a safe, writable POSIX directory for pid and log files
if [ -d "$HOME" ] && [ -w "$HOME" ]; then
    RUN_DIR="$HOME/.frontendcli"
else
    RUN_DIR="$DIR"
fi
mkdir -p "$RUN_DIR" 2>/dev/null || RUN_DIR="/tmp/.frontendcli"
mkdir -p "$RUN_DIR" 2>/dev/null || true

PID_FILE="$RUN_DIR/bridge.pid"
LOG_FILE="$RUN_DIR/bridge.log"

action="${1:-start}"

find_python() {
    if command -v python3 >/dev/null 2>&1; then
        echo "python3"
    elif command -v python >/dev/null 2>&1; then
        echo "python"
    else
        echo ""
    fi
}

find_server_script() {
    # 1. Check relative to start.sh location
    if [ -f "$DIR/server.py" ]; then
        echo "$DIR/server.py"
        return 0
    fi
    if [ -f "$DIR/bridge/server.py" ]; then
        echo "$DIR/bridge/server.py"
        return 0
    fi

    # 2. Check current working directory
    if [ -f "$(pwd)/server.py" ]; then
        echo "$(pwd)/server.py"
        return 0
    fi
    if [ -f "$(pwd)/bridge/server.py" ]; then
        echo "$(pwd)/bridge/server.py"
        return 0
    fi

    # 3. Check home & project clone locations
    if [ -f "$HOME/frontendcli/bridge/server.py" ]; then
        echo "$HOME/frontendcli/bridge/server.py"
        return 0
    fi
    if [ -f "$RUN_DIR/server.py" ]; then
        echo "$RUN_DIR/server.py"
        return 0
    fi

    # 4. Check Android standard download folders
    for cand in \
        "/sdcard/Download/server.py" \
        "/sdcard/Download/bridge/server.py" \
        "/sdcard/Download/frontendcli/bridge/server.py" \
        "/storage/emulated/0/Download/server.py" \
        "/storage/emulated/0/Download/bridge/server.py" \
        "/storage/emulated/0/Download/frontendcli/bridge/server.py" \
        "$HOME/storage/downloads/server.py" \
        "$HOME/storage/downloads/bridge/server.py"; do
        if [ -f "$cand" ]; then
            echo "$cand"
            return 0
        fi
    done

    # 5. Search with find (capped depth)
    local found
    found=$(find "$HOME" /sdcard/Download /storage/emulated/0/Download "$HOME/storage/downloads" -maxdepth 4 -name "server.py" 2>/dev/null | head -n 1)
    if [ -n "$found" ] && [ -f "$found" ]; then
        echo "$found"
        return 0
    fi

    # 6. Fallback: Automatically download server.py from GitHub
    echo "[INFO] server.py not found on device; auto-downloading from GitHub repository..." >&2
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/server.py" -o "$RUN_DIR/server.py" 2>/dev/null
    elif command -v wget >/dev/null 2>&1; then
        wget -q "https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/server.py" -O "$RUN_DIR/server.py" 2>/dev/null
    fi

    if [ -f "$RUN_DIR/server.py" ]; then
        echo "$RUN_DIR/server.py"
        return 0
    fi

    return 1
}

kill_port_process() {
    local target_port="$1"
    # Kill by PID file if recorded
    if [ -f "$PID_FILE" ]; then
        old_pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
            kill "$old_pid" 2>/dev/null || true
            sleep 0.5
            kill -9 "$old_pid" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
    fi

    # Kill any existing server.py process
    pkill -f "[s]erver\.py" 2>/dev/null || true

    # If fuser or lsof is available, release the port
    if command -v fuser >/dev/null 2>&1; then
        fuser -k "${target_port}/tcp" >/dev/null 2>&1 || true
    fi
}

is_running() {
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
    fi
    pgrep -f "[s]erver\.py" >/dev/null 2>&1 && return 0
    return 1
}

check_health() {
    local url="http://127.0.0.1:$PORT/api/health"
    if command -v curl >/dev/null 2>&1; then
        curl -s -f -m 2 "$url" >/dev/null 2>&1
        return $?
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O - --timeout=2 "$url" >/dev/null 2>&1
        return $?
    else
        $PY_BIN -c "import urllib.request; urllib.request.urlopen('$url', timeout=2)" >/dev/null 2>&1
        return $?
    fi
}

case "$action" in
    stop)
        echo "Stopping CLIFrontend Bridge..."
        kill_port_process "$PORT"
        echo "CLIFrontend Bridge stopped."
        exit 0
        ;;

    status)
        if is_running; then
            pid=$(cat "$PID_FILE" 2>/dev/null || pgrep -f "[s]erver\.py" | head -n 1)
            echo "CLIFrontend Bridge: RUNNING"
            echo "  PID:  $pid"
            echo "  Port: $PORT"
            if check_health; then
                echo "  API:  http://127.0.0.1:$PORT/api/health [HEALTHY]"
            else
                echo "  API:  http://127.0.0.1:$PORT/api/health [UNRESPONSIVE]"
            fi
        else
            echo "CLIFrontend Bridge: STOPPED"
        fi
        exit 0
        ;;

    logs)
        if [ -f "$LOG_FILE" ]; then
            tail -n 40 "$LOG_FILE"
        else
            echo "No log file found at $LOG_FILE"
        fi
        exit 0
        ;;

    restart|start)
        PY_BIN=$(find_python)
        if [ -z "$PY_BIN" ]; then
            echo "[INFO] Python 3 is not installed. Attempting auto-install..."
            if command -v pkg >/dev/null 2>&1; then
                pkg install -y python || true
            elif command -v apt-get >/dev/null 2>&1; then
                apt-get update && apt-get install -y python3 || true
            fi
            PY_BIN=$(find_python)
        fi

        if [ -z "$PY_BIN" ]; then
            echo "[ERROR] Python 3 is required but could not be installed."
            echo "  In Termux, please run: pkg install -y python"
            exit 1
        fi

        # Find server.py anywhere on device or auto-fetch
        SERVER_SCRIPT=$(find_server_script)
        if [ -z "$SERVER_SCRIPT" ] || [ ! -f "$SERVER_SCRIPT" ]; then
            echo "[ERROR] Could not find or download server.py!"
            echo "Please check internet connection or clone repo: git clone https://github.com/adamikoo/frontendcli.git"
            exit 1
        fi

        # Acquire Termux wake lock so Android doesn't kill the bridge in background
        if command -v termux-wake-lock >/dev/null 2>&1; then
            termux-wake-lock
        fi

        # Stop previous instance cleanly
        if is_running; then
            echo "Stopping existing CLIFrontend Bridge..."
            kill_port_process "$PORT"
            sleep 1
        fi

        echo "Starting CLIFrontend Bridge from $SERVER_SCRIPT on port $PORT..."
        > "$LOG_FILE"
        nohup $PY_BIN "$SERVER_SCRIPT" >> "$LOG_FILE" 2>&1 &
        NEW_PID=$!
        echo "$NEW_PID" > "$PID_FILE"

        # Wait 1.5s to ensure the process did not immediately crash
        sleep 1.5

        if ! kill -0 "$NEW_PID" 2>/dev/null; then
            echo ""
            echo "=================================================="
            echo "[ERROR] CLIFrontend Bridge (PID $NEW_PID) failed to start!"
            echo "Log output ($LOG_FILE):"
            echo "--------------------------------------------------"
            cat "$LOG_FILE"
            echo "=================================================="
            rm -f "$PID_FILE"
            exit 1
        fi

        # Probe health endpoint
        healthy=0
        for i in 1 2 3 4 5; do
            if check_health; then
                healthy=1
                break
            fi
            sleep 0.8
        done

        if [ $healthy -eq 1 ]; then
            echo ""
            echo "=================================================="
            echo "✓ CLIFrontend Bridge is running and healthy!"
            echo "  PID:       $NEW_PID"
            echo "  Script:    $SERVER_SCRIPT"
            echo "  URL:       http://127.0.0.1:$PORT"
            echo "  Health:    http://127.0.0.1:$PORT/api/health"
            echo "  Logs:      $LOG_FILE"
            echo "=================================================="
            echo "You can now open the CLIFrontend Android APK."
        else
            echo ""
            echo "[WARNING] Bridge process is alive (PID $NEW_PID) but health check timed out."
            echo "Check log output:"
            cat "$LOG_FILE"
        fi
        ;;

    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        exit 1
        ;;
esac

