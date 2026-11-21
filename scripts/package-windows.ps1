param(
    [string]$JdkHome,
    [string]$NsisHome,
    [string]$RuntimeHome,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
[xml]$taskPom = Get-Content -LiteralPath (Join-Path $taskProjectRoot 'pom.xml') -Raw -Encoding UTF8
$taskVersion = [string]$taskPom.project.version
if ($taskVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Project version must use major.minor.patch format.' }
$taskTools = Join-Path $taskProjectRoot 'target\packaging-tools'
if (-not $JdkHome) { $JdkHome = Join-Path $taskTools 'jdk-21.0.13.0.2+1' }
if (-not $NsisHome) { $NsisHome = Join-Path $taskTools 'nsis-3.11' }
foreach ($taskRequired in @((Join-Path $JdkHome 'bin\jpackage.exe'), (Join-Path $NsisHome 'makensis.exe'),
        (Join-Path $JdkHome 'bin\jlink.exe'))) {
    if (-not (Test-Path -LiteralPath $taskRequired)) { throw "Required tool not found: $taskRequired" }
}
$env:JAVA_HOME = $JdkHome
$env:PATH = (Join-Path $JdkHome 'bin') + ';' + $env:PATH
if (-not $SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') -Test -JdkHome $JdkHome }
$taskStage = Join-Path $taskProjectRoot "target\windows-package\$taskVersion"
$taskInput = Join-Path $taskStage 'input'
$taskImageDir = Join-Path $taskStage 'image'
$taskDist = Join-Path $taskProjectRoot 'dist'
New-Item -ItemType Directory -Force -Path $taskInput,$taskImageDir,$taskDist | Out-Null
Copy-Item -LiteralPath (Join-Path $taskProjectRoot 'target\MinesweeperEX.jar') -Destination $taskInput -Force
if (-not $RuntimeHome) {
    $RuntimeHome = Join-Path $taskStage 'runtime'
    if (Test-Path -LiteralPath $RuntimeHome) {
        $taskResolvedRuntime = (Resolve-Path -LiteralPath $RuntimeHome).Path
        $taskAllowedStage = [IO.Path]::GetFullPath($taskStage) + [IO.Path]::DirectorySeparatorChar
        if (-not $taskResolvedRuntime.StartsWith($taskAllowedStage, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe runtime output path' }
        Remove-Item -LiteralPath $taskResolvedRuntime -Recurse -Force
    }
    # Swing needs java.desktop and its transitive modules. Keep Chinese/English locale data.
    & (Join-Path $JdkHome 'bin\jlink.exe') --module-path (Join-Path $JdkHome 'jmods') `
        --add-modules java.desktop,jdk.localedata --include-locales en,zh --strip-debug `
        --no-header-files --no-man-pages --compress=zip-6 --output $RuntimeHome
    if ($LASTEXITCODE -ne 0) { throw 'Windows minimal runtime generation failed' }
}
if (-not (Test-Path -LiteralPath (Join-Path $RuntimeHome 'bin\java.exe'))) { throw 'Runtime must contain bin\java.exe' }
$taskImage = Join-Path $taskImageDir 'MinesweeperEX'
# Only remove previous generated application output after checking its absolute parent.
if (Test-Path -LiteralPath $taskImage) {
    $taskResolvedImage = (Resolve-Path -LiteralPath $taskImage).Path
    $taskAllowedParent = [IO.Path]::GetFullPath($taskImageDir) + [IO.Path]::DirectorySeparatorChar
    if (-not $taskResolvedImage.StartsWith($taskAllowedParent, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe output path' }
    Remove-Item -LiteralPath $taskResolvedImage -Recurse -Force
}
& (Join-Path $JdkHome 'bin\jpackage.exe') --type app-image --name MinesweeperEX --app-version $taskVersion `
    --input $taskInput --main-jar MinesweeperEX.jar --main-class com.minesweeper.MinesweeperApplication `
    --runtime-image $RuntimeHome --dest $taskImageDir --vendor MinesweeperEX --description 'MinesweeperEX dynamic minesweeper'
if ($LASTEXITCODE -ne 0) { throw 'Windows application image failed' }
$taskInstaller = Join-Path $taskDist "MinesweeperEX-Windows-x64-$taskVersion.exe"
# Generate an exact uninstall manifest; never recursively remove the user's chosen folder.
$taskUninstallManifest = Join-Path $taskStage 'uninstall-files.nsh'
$taskImagePrefix = [IO.Path]::GetFullPath($taskImage) + [IO.Path]::DirectorySeparatorChar
$taskUninstallLines = [Collections.Generic.List[string]]::new()
foreach ($taskFile in (Get-ChildItem -LiteralPath $taskImage -File -Recurse -Force)) {
    $taskRelative = $taskFile.FullName.Substring($taskImagePrefix.Length).Replace('$', '$$')
    $taskUninstallLines.Add('Delete "$INSTDIR\' + $taskRelative + '"')
}
foreach ($taskDir in (Get-ChildItem -LiteralPath $taskImage -Directory -Recurse -Force | Sort-Object { $_.FullName.Length } -Descending)) {
    $taskRelative = $taskDir.FullName.Substring($taskImagePrefix.Length).Replace('$', '$$')
    $taskUninstallLines.Add('RMDir "$INSTDIR\' + $taskRelative + '"')
}
[IO.File]::WriteAllLines($taskUninstallManifest, $taskUninstallLines, [Text.UTF8Encoding]::new($true))
$taskArchive = Join-Path $taskStage 'application.zip'
if (Test-Path -LiteralPath $taskArchive) { Remove-Item -LiteralPath $taskArchive }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($taskImage, $taskArchive, [IO.Compression.CompressionLevel]::Optimal, $false)
$taskSizeKB = [int][Math]::Ceiling((Get-ChildItem -LiteralPath $taskImage -File -Recurse -Force | Measure-Object Length -Sum).Sum / 1KB)
& (Join-Path $NsisHome 'makensis.exe') /INPUTCHARSET UTF8 /V3 /WX "/DAPP_ARCHIVE=$taskArchive" `
    "/DAPP_VERSION=$taskVersion" "/DOUTPUT_FILE=$taskInstaller" "/DUNINSTALL_MANIFEST=$taskUninstallManifest" "/DSIZE_KB=$taskSizeKB" `
    (Join-Path $PSScriptRoot 'windows-installer.nsi')
if ($LASTEXITCODE -ne 0) { throw 'Windows EXE installer failed' }
Write-Output "Windows installer: $taskInstaller"
