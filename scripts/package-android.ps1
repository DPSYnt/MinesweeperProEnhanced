param(
    [string]$JdkHome,
    [string]$AndroidSdk,
    [string]$Gradle,
    [switch]$WithTests
)
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskAndroidRoot = Join-Path $taskProjectRoot 'android'
$taskBuildConfig = Get-Content -LiteralPath (Join-Path $taskAndroidRoot 'app\build.gradle') -Raw
$taskVersionMatch = [regex]::Match($taskBuildConfig, '(?m)^\s*versionName\s+''([^'']+)''')
if (-not $taskVersionMatch.Success) { throw 'Android versionName was not found in app/build.gradle.' }
$taskVersion = $taskVersionMatch.Groups[1].Value
if ($taskVersion -notmatch '^\d+(\.\d+){1,3}$') { throw 'Android versionName must be a numeric dotted version.' }
if (-not $JdkHome) { $JdkHome = Join-Path $taskProjectRoot 'target\packaging-tools\jdk-21.0.13.0.2+1' }
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $Gradle) {
    $taskGradleRoot = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.11.1-bin'
    $Gradle = Get-ChildItem -LiteralPath $taskGradleRoot -Filter gradle.bat -Recurse | Select-Object -First 1 -ExpandProperty FullName
}
foreach ($taskRequired in @((Join-Path $JdkHome 'bin\javac.exe'), (Join-Path $AndroidSdk 'platforms\android-35\android.jar'), $Gradle)) {
    if (-not $taskRequired -or -not (Test-Path -LiteralPath $taskRequired)) { throw "Required Android tool not found: $taskRequired" }
}
$env:JAVA_HOME = $JdkHome
$env:ANDROID_HOME = $AndroidSdk
$env:PATH = (Join-Path $JdkHome 'bin') + ';' + $env:PATH
$taskSigningDir = Join-Path $taskAndroidRoot 'signing'
$taskProperties = Join-Path $taskSigningDir 'release.properties'
if (-not (Test-Path -LiteralPath $taskProperties)) {
    New-Item -ItemType Directory -Path $taskSigningDir -Force | Out-Null
    $taskKeystore = Join-Path $taskSigningDir 'minesweeperex-release.p12'
    if (Test-Path -LiteralPath $taskKeystore) { throw 'Signing key exists but properties are missing; preserve the key and recover its password.' }
    $taskSigningPassword = [Guid]::NewGuid().ToString('N')
    $env:MINESWEEPER_SIGNING_PASSWORD = $taskSigningPassword
    try {
        & (Join-Path $JdkHome 'bin\keytool.exe') -genkeypair -keystore $taskKeystore -storetype PKCS12 `
            -storepass:env MINESWEEPER_SIGNING_PASSWORD -keypass:env MINESWEEPER_SIGNING_PASSWORD `
            -alias minesweeperex -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=MinesweeperEX, O=Local Game Project, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Android signing key generation failed' }
        [IO.File]::WriteAllLines($taskProperties, @('storeFile=signing/minesweeperex-release.p12',
            "storePassword=$taskSigningPassword", 'keyAlias=minesweeperex', "keyPassword=$taskSigningPassword"))
    } finally { Remove-Item Env:MINESWEEPER_SIGNING_PASSWORD }
}
$taskTasks = @(':app:assembleRelease', ':app:lintRelease')
if ($WithTests) { $taskTasks += @(':app:assembleDebug', ':app:assembleDebugAndroidTest') }
& $Gradle -p $taskAndroidRoot @taskTasks --console plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
$taskDist = Join-Path $taskProjectRoot 'dist'
New-Item -ItemType Directory -Path $taskDist -Force | Out-Null
$taskApk = Join-Path $taskDist ("MinesweeperEX-Android-$taskVersion.apk")
$taskBuiltApk = Join-Path $taskAndroidRoot 'app\build\outputs\apk\release\app-release.apk'
& (Join-Path $AndroidSdk 'build-tools\35.0.0\apksigner.bat') verify --verbose --print-certs $taskBuiltApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Copy-Item -LiteralPath $taskBuiltApk -Destination $taskApk -Force
Write-Output "Android APK: $taskApk"
