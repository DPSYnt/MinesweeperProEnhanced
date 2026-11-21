param([string]$Serial = 'emulator-5554', [string]$AndroidSdk)
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$taskAdb = Join-Path $AndroidSdk 'platform-tools\adb.exe'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This smoke test is restricted to an emulator, not a personal device.' }
foreach ($taskApk in @('android\app\build\outputs\apk\debug\app-debug.apk',
        'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) {
    & $taskAdb -s $Serial install -r (Join-Path $taskProjectRoot $taskApk)
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed' }
}
& $taskAdb -s $Serial shell wm dismiss-keyguard
$taskResult = & $taskAdb -s $Serial shell am instrument -w 'com.minesweeper.ex.debug.test/com.minesweeper.android.SmokeInstrumentation'
$taskResult | Tee-Object -FilePath (Join-Path $taskProjectRoot 'target\android-instrumentation.txt')
if ($LASTEXITCODE -ne 0 -or ($taskResult -join "`n") -notmatch 'PASS Android native') { throw 'Android instrumentation failed' }
$taskPreviews = Join-Path $taskProjectRoot 'target\android-previews'
New-Item -ItemType Directory -Force -Path $taskPreviews | Out-Null
foreach ($taskPreview in @('android-menu.png', 'android-game-18.png', 'android-game-25.png', 'android-game-30.png', 'android-game-35.png')) {
    & $taskAdb -s $Serial pull "/sdcard/Android/data/com.minesweeper.ex.debug/files/$taskPreview" (Join-Path $taskPreviews $taskPreview)
    if ($LASTEXITCODE -ne 0) { throw 'Android preview export failed' }
}
