param(
    [switch]$Test,
    [switch]$Preview,
    [switch]$DesktopTest,
    [switch]$Run,
    [string]$JdkHome
)

$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskTarget = Join-Path $taskProjectRoot 'target'
$taskClasses = Join-Path $taskTarget 'classes'
$taskTestClasses = Join-Path $taskTarget 'test-classes'
$taskMainSources = Join-Path $taskProjectRoot 'src\main\java'
$taskTestSources = Join-Path $taskProjectRoot 'src\test\java'
$taskJar = Join-Path $taskTarget 'MinesweeperEX.jar'

# Resolve a complete JDK so double-click startup does not depend on PATH configuration.
$taskJdkCandidates = @($JdkHome)
if (-not $JdkHome) {
    $taskJdkCandidates = @($env:JAVA_HOME,
        (Join-Path $taskTarget 'packaging-tools\jdk-21.0.13.0.2+1'),
        (Join-Path $taskTarget 'packaging-tools\jdk-21.0.2'))
    $taskPathCompiler = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($taskPathCompiler) { $taskJdkCandidates += Split-Path -Parent (Split-Path -Parent $taskPathCompiler.Source) }
}
$taskSelectedJdk = $null
foreach ($taskCandidate in $taskJdkCandidates) {
    if (-not $taskCandidate) { continue }
    $taskComplete = $true
    foreach ($taskTool in @('java.exe', 'javac.exe', 'jar.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $taskCandidate ('bin\' + $taskTool)))) { $taskComplete = $false }
    }
    if (-not $taskComplete) { continue }
    $taskJavacVersion = & (Join-Path $taskCandidate 'bin\javac.exe') -version 2>&1
    if (($taskJavacVersion -join ' ') -match 'javac (\d+)' -and [int]$Matches[1] -ge 21) {
        $taskSelectedJdk = [IO.Path]::GetFullPath($taskCandidate)
        break
    }
}
if (-not $taskSelectedJdk) { throw 'JDK 21 or newer is required. Set JAVA_HOME or pass -JdkHome with a complete JDK directory.' }
$env:JAVA_HOME = $taskSelectedJdk
$env:PATH = (Join-Path $taskSelectedJdk 'bin') + ';' + $env:PATH

# Build only under the project target directory, using the local JDK.
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
# Discard stale classes after source files have been removed or renamed.
foreach ($taskOutputDirectory in @($taskClasses, $taskTestClasses)) {
    if (Test-Path -LiteralPath $taskOutputDirectory) {
        $taskResolvedOutput = (Resolve-Path -LiteralPath $taskOutputDirectory).Path
        $taskResolvedTarget = [System.IO.Path]::GetFullPath($taskTarget) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $taskResolvedOutput.StartsWith($taskResolvedTarget, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Build output must stay inside the project target directory.'
        }
        Get-ChildItem -LiteralPath $taskResolvedOutput -Filter '*.class' -File -Recurse |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    }
}
$taskSourceFiles = @(Get-ChildItem -LiteralPath $taskMainSources -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d $taskClasses @taskSourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }

if ($Test -or $Preview -or $DesktopTest) {
    New-Item -ItemType Directory -Path $taskTestClasses -Force | Out-Null
    $taskTestFiles = @(Get-ChildItem -LiteralPath $taskTestSources -Filter '*.java' -Recurse |
        ForEach-Object { $_.FullName })
    & javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $taskClasses -d $taskTestClasses @taskTestFiles
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    if ($Test) {
        & java '-Djava.awt.headless=true' -cp "$taskClasses;$taskTestClasses" com.minesweeper.RegressionSuite
        if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed.' }
    }
    if ($Preview) {
        Push-Location -LiteralPath $taskProjectRoot
        try {
            & java '-Djava.awt.headless=true' -cp "$taskClasses;$taskTestClasses" com.minesweeper.ui.UiInteractionChecks 'target/ui-preview.png'
            if ($LASTEXITCODE -ne 0) { throw 'UI preview failed.' }
        } finally { Pop-Location }
    }
    if ($DesktopTest) {
        Push-Location -LiteralPath $taskProjectRoot
        try {
            & java -cp "$taskClasses;$taskTestClasses" com.minesweeper.ui.DesktopInteractionSmoke
            if ($LASTEXITCODE -ne 0) { throw 'Desktop interaction test failed.' }
        } finally { Pop-Location }
    }
}

& jar --create --file $taskJar --main-class com.minesweeper.MinesweeperApplication -C $taskClasses .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
# Remove the legacy name only after the current JAR has been built successfully.
$taskLegacyJar = Join-Path $taskTarget 'minesweeper-pro.jar'
if (Test-Path -LiteralPath $taskLegacyJar) { Remove-Item -LiteralPath $taskLegacyJar }
Write-Output "Built: $taskJar"

if ($Run) {
    & java -jar $taskJar
    if ($LASTEXITCODE -ne 0) { throw 'Application failed.' }
}
