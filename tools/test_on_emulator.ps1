# Demo / emulator checks for H9 Cluster (no car required).
# Prerequisites: JDK 17, Android SDK platform-tools, running emulator or device.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\test_on_emulator.ps1
#   powershell -ExecutionPolicy Bypass -File tools\test_on_emulator.ps1 -DualDisplay
#   powershell -ExecutionPolicy Bypass -File tools\test_on_emulator.ps1 -SkipInstall

param(
    [switch]$DualDisplay,
    [switch]$SkipInstall,
    [switch]$PowerOnly
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

function Find-Adb {
    $candidates = @(
        (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
        "C:\Users\Igorek\Android\sdk\platform-tools\adb.exe",
        "C:\Users\Igorek\Android\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    ) | Where-Object { $_ -and (Test-Path $_) }
    if (-not $candidates) {
        throw "adb not found. Install Android SDK Platform-Tools or Android Studio."
    }
    return $candidates[0]
}

function Find-Java17Home {
    $candidates = @(
        $env:JAVA_HOME,
        "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot",
        "C:\Program Files\Microsoft\jdk-17*",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Java\jdk-17*",
        "C:\Program Files\Android\Android Studio\jbr"
    )
    foreach ($pattern in $candidates) {
        if (-not $pattern) { continue }
        $resolved = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved -and (Test-Path (Join-Path $resolved.FullName "bin\java.exe"))) {
            $ver = & (Join-Path $resolved.FullName "bin\java.exe") -version 2>&1 | Out-String
            if ($ver -match '"17\.') {
                return $resolved.FullName
            }
        }
    }
    throw "JDK 17 not found. Install Microsoft OpenJDK 17 and retry."
}

$adb = Find-Adb
Write-Host "adb: $adb"

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $devices) {
    throw "No emulator/device online. Start an Android emulator first."
}
Write-Host "device: $($devices[0])"

if (-not $PowerOnly) {
    $javaHome = Find-Java17Home
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;" + $env:Path
    Write-Host "JAVA_HOME: $javaHome"

    if (-not $SkipInstall) {
        Write-Host "Building and installing Demo APK..."
        & .\gradlew.bat installDemo --quiet
        if ($LASTEXITCODE -ne 0) { throw "gradlew installDemo failed" }
    }

    & $adb shell am force-stop net.adminrunet.h9cluster.demo

    if ($DualDisplay) {
        Write-Host "Enabling overlay display 1920x720/240..."
        & $adb shell settings put global overlay_display_devices '1920x720/240'
    } else {
        & $adb shell settings delete global overlay_display_devices 2>$null
        & $adb shell wm size 1920x720
        & $adb shell wm density 240
    }

    Write-Host "Starting SettingsActivity..."
    & $adb shell am start -W -n net.adminrunet.h9cluster.demo/net.adminrunet.h9cluster.SettingsActivity
    Write-Host ""
    Write-Host "Manual checks:"
    Write-Host "  1) Theme picker -> Заводская -> Save  (overlay must close)"
    Write-Host "  2) Theme picker -> Classic/Sport -> Save  (overlay returns)"
    Write-Host ""
}

Write-Host "Power cycle simulation (SCREEN_OFF / SCREEN_ON):"
Write-Host "  Watching logcat for H9ClusterPower (Ctrl+C to stop after test)..."
Write-Host "  Sending SLEEP in 3s..."
Start-Sleep -Seconds 3
& $adb shell input keyevent KEYCODE_SLEEP
Start-Sleep -Seconds 2
Write-Host "  Sending WAKEUP..."
& $adb shell input keyevent KEYCODE_WAKEUP
& $adb shell input keyevent KEYCODE_MENU

Write-Host ""
Write-Host "Recent power logs:"
& $adb logcat -d -s H9ClusterPower:I H9Cluster:I GWMClusterLauncher:I | Select-Object -Last 40
