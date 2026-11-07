# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 InkReader contributors
#
# Black-box open-latency benchmark for InkReader.
#
# Usage:
#   .\scripts\bench-open.ps1 -Runs 5
#   .\scripts\bench-open.ps1 -Runs 10 -Cold -CsvPath .\artifacts\bench-open.csv
#
# The script measures from tapping the Home "继续阅读" button until the reader
# top bar (返回 / 目录) is visible. It uses the public UI hierarchy only; no
# app-internal instrumentation.

param(
    [int]$Runs = 5,
    [switch]$Cold,
    [int]$TimeoutSec = 30,
    [string]$CsvPath = ""
)

$ErrorActionPreference = "Stop"
$adb = if ($env:ADB) {
    $env:ADB
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}
if (-not (Test-Path $adb)) {
    throw "adb not found. Set ADB to the platform-tools adb.exe path."
}

$package = "com.inkreader.app"
$activity = "$package/.MainActivity"
$uiFile = "/sdcard/inkreader-bench-ui.xml"
$times = [System.Collections.Generic.List[long]]::new()
$launchTimes = [System.Collections.Generic.List[long]]::new()

function Dump-Ui {
    & $adb shell uiautomator dump $uiFile 2>&1 | Out-Null
    $xml = (& $adb shell cat $uiFile) -join "`n"
    if ([string]::IsNullOrWhiteSpace($xml)) { return $null }
    return [xml]$xml
}

function Find-TextNode([xml]$doc, [string]$pattern) {
    if ($null -eq $doc) { return $null }
    return $doc.SelectNodes("//node") |
        Where-Object { $_.text -like $pattern } |
        Select-Object -First 1
}

function Tap-Node($node) {
    if ($null -eq $node) { throw "UI node not found" }
    $m = [regex]::Match($node.bounds, "\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
    if (-not $m.Success) { throw "Invalid bounds: $($node.bounds)" }
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    & $adb shell input tap $x $y | Out-Null
}

function Start-ToHome {
    if ($Cold) {
        & $adb shell am force-stop $package | Out-Null
        $launch = [Diagnostics.Stopwatch]::StartNew()
        $out = & $adb shell am start -W -n $activity 2>&1
        $launch.Stop()
        $launchTimes.Add($launch.ElapsedMilliseconds)
        Start-Sleep -Milliseconds 800
        return
    }

    # Return to Home without killing the process.
    for ($i = 0; $i -lt 5; $i++) {
        $doc = Dump-Ui
        if ($null -ne (Find-TextNode $doc "*继续阅读*")) { return }
        & $adb shell input keyevent 4 | Out-Null
        Start-Sleep -Milliseconds 350
    }

    # App may have been stopped before the first run.
    & $adb shell am start -W -n $activity 2>&1 | Out-Null
    for ($i = 0; $i -lt 8; $i++) {
        Start-Sleep -Milliseconds 300
        $doc = Dump-Ui
        if ($null -ne (Find-TextNode $doc "*继续阅读*")) { return }
    }
    throw "Could not reach Home; close dialogs/other apps first."
}

function Measure-Once {
    Start-ToHome
    $doc = Dump-Ui
    $continue = Find-TextNode $doc "*继续阅读*"
    if ($null -eq $continue) { throw "Continue button not found" }

    $sw = [Diagnostics.Stopwatch]::StartNew()
    Tap-Node $continue
    $seen = $false
    $last = ""
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        Start-Sleep -Milliseconds 150
        $current = Dump-Ui
        if ($null -eq $current) { continue }
        $text = $current.OuterXml
        if ($text -match 'text="返回"' -and $text -match 'text="目录"') {
            $seen = $true
            break
        }
        if ($text -match '打开失败|加载失败|插件错误') {
            $last = "error"
            break
        }
    }
    $sw.Stop()
    if (-not $seen) {
        throw "Reader did not appear within ${TimeoutSec}s ($last)"
    }
    return $sw.ElapsedMilliseconds
}

try {
    $version = (& $adb shell dumpsys package $package 2>&1 |
        Select-String -Pattern "versionName=" |
        Select-Object -First 1).ToString().Trim()
    Write-Host "InkReader benchmark"
    Write-Host "  adb: $adb"
    Write-Host "  package: $package"
    Write-Host "  mode: $(if ($Cold) { 'cold' } else { 'warm/back-to-home' })"
    Write-Host "  runs: $Runs"
    Write-Host "  $version"

    for ($i = 1; $i -le $Runs; $i++) {
        $ms = Measure-Once
        $times.Add($ms)
        Write-Host ("  run {0}: {1} ms" -f $i, $ms)
    }

    $sorted = $times | Sort-Object
    $median = $sorted[[int][Math]::Floor($sorted.Count / 2)]
    $p95Index = [int][Math]::Ceiling($sorted.Count * 0.95) - 1
    $p95 = $sorted[[Math]::Max(0, [Math]::Min($p95Index, $sorted.Count - 1))]
    Write-Host ""
    Write-Host ("Open latency: median={0} ms  p95={1} ms  min={2} ms  max={3} ms" -f `
        $median, $p95, $sorted[0], $sorted[-1])
    if ($Cold -and $launchTimes.Count -gt 0) {
        Write-Host ("Cold app launch: avg={0} ms" -f ([int]($launchTimes | Measure-Object -Average).Average))
    }

    if (-not [string]::IsNullOrWhiteSpace($CsvPath)) {
        $rows = for ($i = 0; $i -lt $times.Count; $i++) {
            [pscustomobject]@{
                run = $i + 1
                open_ms = $times[$i]
                cold = $Cold.IsPresent
                version = $version
            }
        }
        $dir = Split-Path -Parent $CsvPath
        if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
        $rows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $CsvPath
        Write-Host "CSV: $CsvPath"
    }
} finally {
    & $adb shell rm $uiFile 2>&1 | Out-Null
}
