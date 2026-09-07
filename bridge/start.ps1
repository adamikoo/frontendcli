# ==============================================================================
# CLIFrontend Bridge Windows Launcher (PowerShell)
# ==============================================================================

param (
    [string]$Action = "start",
    [string]$Port = "8765"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$PidFile = Join-Path $ScriptDir "bridge.pid"

function Stop-BridgeProcess {
    param([string]$targetPort)
    Write-Host "Checking for processes on port $targetPort..." -ForegroundColor Yellow
    
    # Check pid file
    if (Test-Path $PidFile) {
        $savedPid = Get-Content $PidFile -ErrorAction SilentlyContinue
        if ($savedPid -and (Get-Process -Id $savedPid -ErrorAction SilentlyContinue)) {
            Write-Host "Stopping CLIFrontend Bridge (PID $savedPid)..." -ForegroundColor Yellow
            Stop-Process -Id $savedPid -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }

    # Find any netstat process on port
    try {
        $netstatOutput = netstat -ano | Select-String ":$targetPort\s+LISTENING"
        foreach ($line in $netstatOutput) {
            $parts = ($line -replace '\s+', ' ').Trim().Split(' ')
            $procId = $parts[-1]
            if ($procId -and $procId -ne "0") {
                Write-Host "Killing process on port $targetPort (PID $procId)..." -ForegroundColor Yellow
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        # Ignore
    }
}

switch ($Action.ToLower()) {
    "stop" {
        Write-Host "Stopping CLIFrontend Bridge..." -ForegroundColor Cyan
        Stop-BridgeProcess -targetPort $Port
        Write-Host "CLIFrontend Bridge stopped." -ForegroundColor Green
        break
    }

    "status" {
        $isRunning = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet
        if ($isRunning) {
            Write-Host "CLIFrontend Bridge: RUNNING on port $Port" -ForegroundColor Green
            try {
                $res = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/health" -TimeoutSec 2
                Write-Host "Health API: OK" -ForegroundColor Green
                Write-Host "Workspace:  $($res.workspace)" -ForegroundColor White
            } catch {
                Write-Host "Health API: UNRESPONSIVE ($($_))" -ForegroundColor Red
            }
        } else {
            Write-Host "CLIFrontend Bridge: STOPPED (Port $Port not responding)" -ForegroundColor Yellow
        }
        break
    }

    Default {
        # Default is start / run
        $env:PORT = $Port
        $env:DEFAULT_WORKSPACE = $ProjectRoot

        Stop-BridgeProcess -targetPort $Port

        Write-Host "==================================================" -ForegroundColor Cyan
        Write-Host "Starting CLIFrontend Bridge Daemon..." -ForegroundColor Cyan
        Write-Host "  Port:      $Port" -ForegroundColor White
        Write-Host "  Workspace: $ProjectRoot" -ForegroundColor White
        Write-Host "  Health:    http://127.0.0.1:$Port/api/health" -ForegroundColor White
        Write-Host "==================================================" -ForegroundColor Cyan
        Write-Host "Press Ctrl+C to stop the bridge." -ForegroundColor Gray
        Write-Host ""

        # Run python server.py directly in current console
        python "$ScriptDir\server.py"
    }
}
