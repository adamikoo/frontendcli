# ==============================================================================
# CLIFrontend Release Build Script for Windows (PowerShell)
# ==============================================================================

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "=== Building CLIFrontend Standalone APK ===" -ForegroundColor Cyan

# Configure JDK 21
if (Test-Path "C:\Program Files\Java\jdk-21") {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    Write-Host "Using JDK 21 at: $env:JAVA_HOME" -ForegroundColor Green
}

# Run Gradle assembleRelease
Write-Host "Running Gradle assembleRelease..." -ForegroundColor Cyan
& ".\gradlew.bat" :app:assembleRelease --console=plain

if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# Locate built release APK
$ReleaseApk = "$ProjectRoot\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $ReleaseApk)) {
    $ReleaseApk = Get-ChildItem "$ProjectRoot\app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1 -ExpandProperty FullName
}

if (-not (Test-Path $ReleaseApk)) {
    Write-Error "Could not find built APK in $ProjectRoot\app\build\outputs\apk\release"
    exit 1
}

# Copy to root CLIFrontend.apk
$TargetApk = "$ProjectRoot\CLIFrontend.apk"
Copy-Item -Force $ReleaseApk $TargetApk

$SizeMB = [math]::Round((Get-Item $TargetApk).Length / 1MB, 2)
Write-Host "=== BUILD SUCCESSFUL ===" -ForegroundColor Green
Write-Host "Release APK created at: $TargetApk ($SizeMB MB)" -ForegroundColor Green
