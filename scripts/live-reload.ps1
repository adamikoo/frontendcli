# ==============================================================================
# CLIFrontend Live Dev Watcher & Auto-Deploy for Android Emulator / Device
# ==============================================================================
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  CLIFrontend Live Dev Watcher" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$AdbPath = "adb"
if (-not (Get-Command "adb" -ErrorAction SilentlyContinue)) {
    $SdkCands = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "C:\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    )
    foreach ($cand in $SdkCands) {
        if (Test-Path $cand) {
            $AdbPath = $cand
            break
        }
    }
}

Write-Host "Using ADB: $AdbPath" -ForegroundColor Gray

# Forward localhost bridge port
& $AdbPath reverse tcp:8765 tcp:8765 2>$null

function Deploy-App {
    Write-Host "[LIVE-RELOAD] Building & deploying APK..." -ForegroundColor Yellow
    & $AdbPath install -r -d "$PSScriptRoot\..\CLIFrontend.apk"
    & $AdbPath shell am start -n com.antigravity.pocketgravity/.MainActivity
    Write-Host "[LIVE-RELOAD] App deployed and active!" -ForegroundColor Green
}

# Initial deploy
Deploy-App

Write-Host "`nWatching app/src for changes... (Press Ctrl+C to stop)" -ForegroundColor Cyan
$Watcher = New-Object System.IO.FileSystemWatcher
$Watcher.Path = "$PSScriptRoot\..\app\src"
$Watcher.IncludeSubdirectories = $true
$Watcher.EnableRaisingEvents = $true

$Action = {
    $srcPath = $Event.SourceEventArgs.FullPath
    $typeStr = $Event.SourceEventArgs.ChangeType
    Write-Host "[CHANGE] ${typeStr}: ${srcPath}" -ForegroundColor Magenta
    Start-Sleep -Milliseconds 500
    Deploy-App
}

Register-ObjectEvent $Watcher 'Changed' -Action $Action | Out-Null
Register-ObjectEvent $Watcher 'Created' -Action $Action | Out-Null

while ($true) {
    Start-Sleep -Seconds 1
}
