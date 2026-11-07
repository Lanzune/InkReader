param(
    [ValidateSet('build', 'install', 'launch', 'screenshot', 'all')]
    [string]$Action = 'all',
    [string]$Serial = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = 'C:\Users\34553\AppData\Local\Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$package = 'com.inkreader.app'
$activity = 'com.inkreader.app/.MainActivity'
$deviceDir = Join-Path $root 'artifacts\device'

if (-not (Test-Path $adb)) {
    throw "adb not found: $adb"
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    if ($Serial) {
        & $adb -s $Serial @AdbArgs
    } else {
        & $adb @AdbArgs
    }
}

function Assert-Device {
    $lines = & $adb devices
    $ready = $lines | Where-Object { $_ -match '\tdevice$' }
    if (-not $ready) {
        throw 'No authorized device. Enable USB debugging, connect the phone, and allow this PC. For wireless debugging run adb pair first.'
    }
}

function Invoke-Build {
    $env:JAVA_HOME = 'C:\Users\34553\scoop\apps\openjdk21\current'
    $env:GRADLE_USER_HOME = Join-Path $root '.gradle-user-home'
    $env:ANDROID_USER_HOME = Join-Path $root '.android-user-home'
    Push-Location $root
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Invoke-Install {
    if (-not (Test-Path $apk)) { throw "APK not found: $apk" }
    Invoke-Adb install -r -t $apk
    if ($LASTEXITCODE -ne 0) { throw "Install failed: $LASTEXITCODE" }
}

function Invoke-Launch {
    Invoke-Adb shell am force-stop $package | Out-Null
    Invoke-Adb shell am start -n $activity | Out-Null
    Start-Sleep -Milliseconds 900
}

function Invoke-Screenshot {
    New-Item -ItemType Directory -Force -Path $deviceDir | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $remote = '/sdcard/inkreader-debug.png'
    $local = Join-Path $deviceDir "inkreader-$stamp.png"
    Invoke-Adb shell screencap -p $remote | Out-Null
    Invoke-Adb pull $remote $local | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
    Write-Output $local
}

switch ($Action) {
    'build' { Invoke-Build }
    'install' { Assert-Device; Invoke-Install }
    'launch' { Assert-Device; Invoke-Launch }
    'screenshot' { Assert-Device; Invoke-Screenshot }
    'all' {
        Assert-Device
        Invoke-Build
        Invoke-Install
        Invoke-Launch
        Invoke-Screenshot
    }
}
