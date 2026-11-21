param([switch]$SkipLaunch)
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskInstaller = Join-Path $taskProjectRoot 'dist\MinesweeperEX-Windows-x64-1.0.1.exe'
$taskImage = Join-Path $taskProjectRoot 'target\windows-package\image\MinesweeperEX'
$taskDestination = Join-Path $taskProjectRoot 'target\windows-install-test\Verified-MinesweeperEX'
$taskRegistryPath = 'HKCU:\Software\MinesweeperEX'
if (Test-Path -LiteralPath $taskRegistryPath) { throw 'Use a dedicated test user without an existing MinesweeperEX installation.' }
if (Test-Path -LiteralPath $taskDestination) { throw 'Test destination already exists; preserve or inspect it before retrying.' }
foreach ($taskRequired in @($taskInstaller, $taskImage)) {
    if (-not (Test-Path -LiteralPath $taskRequired)) { throw "Missing installer/image: $taskRequired" }
}
$taskApp = $null
$taskInstalled = $false
try {
    # NSIS requires /D last, without surrounding quotes, including paths with spaces.
    $taskProcess = Start-Process -FilePath $taskInstaller -ArgumentList ('/S /D=' + $taskDestination) -WindowStyle Hidden -Wait -PassThru
    if ($taskProcess.ExitCode -ne 0) { throw "Installer failed: $($taskProcess.ExitCode)" }
    $taskInstalled = $true
    $taskRegistered = (Get-ItemProperty -LiteralPath $taskRegistryPath).InstallDir
    if ($taskRegistered -ne $taskDestination) { throw 'Installation registry path mismatch' }
    $taskPrefix = [IO.Path]::GetFullPath($taskImage) + [IO.Path]::DirectorySeparatorChar
    $taskFileCount = 0
    foreach ($taskFile in (Get-ChildItem -LiteralPath $taskImage -File -Recurse -Force)) {
        $taskInstalledFile = Join-Path $taskDestination $taskFile.FullName.Substring($taskPrefix.Length)
        if ((Get-FileHash -LiteralPath $taskFile.FullName).Hash -ne (Get-FileHash -LiteralPath $taskInstalledFile).Hash) {
            throw "Installed file differs: $taskInstalledFile"
        }
        $taskFileCount++
    }
    Write-Output "PASS silent installation to a Chinese path; $taskFileCount installed files match their source hashes"
    if (-not $SkipLaunch) {
        $taskApp = Start-Process -FilePath (Join-Path $taskDestination 'MinesweeperEX.exe') -PassThru
        $taskWindowFound = $false
        for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
            Start-Sleep -Milliseconds 250
            $taskApp.Refresh()
            if ($taskApp.HasExited) { throw "Native launcher exited early: $($taskApp.ExitCode)" }
            if ($taskApp.MainWindowTitle.StartsWith('MinesweeperEX')) { $taskWindowFound = $true; break }
        }
        if (-not $taskWindowFound) { throw 'Native launcher did not open the game window' }
        Write-Output 'PASS installed native EXE launches the MinesweeperEX window'
        $taskApp.CloseMainWindow() | Out-Null
        if (-not $taskApp.WaitForExit(5000)) { throw 'Game window did not close cleanly' }
        if ($taskApp.ExitCode -ne 0) { throw "Game exited with error: $($taskApp.ExitCode)" }
        $taskApp = $null
        Write-Output 'PASS game window closes cleanly'
    }
    # A user-added file must survive uninstall, even inside the application directory.
    $taskSentinel = Join-Path $taskDestination 'user-added-file.txt'
    [IO.File]::WriteAllText($taskSentinel, 'preserve this user file')
} finally {
    if ($null -ne $taskApp -and -not $taskApp.HasExited) {
        $taskApp.CloseMainWindow() | Out-Null
        if (-not $taskApp.WaitForExit(3000)) { Stop-Process -Id $taskApp.Id -Force }
    }
    if ($taskInstalled -and (Test-Path -LiteralPath (Join-Path $taskDestination 'Uninstall.exe'))) {
        $taskUninstall = Start-Process -FilePath (Join-Path $taskDestination 'Uninstall.exe') -ArgumentList '/S' -WindowStyle Hidden -Wait -PassThru
        if ($taskUninstall.ExitCode -ne 0) { throw "Uninstall failed: $($taskUninstall.ExitCode)" }
    }
}
if (Test-Path -LiteralPath $taskRegistryPath) { throw 'Uninstall left the application registry key' }
if (Test-Path -LiteralPath (Join-Path $taskDestination 'MinesweeperEX.exe')) { throw 'Uninstall left the game launcher' }
if ((Get-Content -LiteralPath $taskSentinel -Raw) -ne 'preserve this user file') { throw 'Uninstall changed a user file' }
Remove-Item -LiteralPath $taskSentinel
# Empty-directory removal only: never recursively delete the user's test destination.
if (@(Get-ChildItem -LiteralPath $taskDestination -Force).Count -ne 0) { throw 'Uninstall left unexpected files in the test directory' }
Remove-Item -LiteralPath $taskDestination
Write-Output 'PASS silent uninstall, registry cleanup and preservation of user-added files'
