@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0device-debug.ps1" %*
