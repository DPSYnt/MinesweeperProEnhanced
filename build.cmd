@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" %*
if errorlevel 1 (
    echo Build or launch failed. See the error above.
    pause
    exit /b 1
)
