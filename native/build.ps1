[CmdletBinding()]
param(
    [switch]$Release,
    [switch]$Offline,
    [string]$JavaHome,
    [string]$AndroidSdk,
    [string]$RobolectricDir,
    [string]$ExpectedCertificateSha256
)
$ErrorActionPreference = 'Stop'
$nativeRoot = $PSScriptRoot
$projectRoot = Split-Path -Parent $nativeRoot
$variant = if ($Release) { 'release' } else { 'debug' }
$taskVariant = if ($Release) { 'Release' } else { 'Debug' }
$onWindows = $env:OS -eq 'Windows_NT'
$suffix = if ($onWindows) { '.exe' } else { '' }
$environmentNames = @('JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','FIVEG_ROBOLECTRIC_OFFLINE','FIVEG_ROBOLECTRIC_DIR')
$previousEnvironment = @{}
foreach ($name in $environmentNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
function Assert-NativeSuccess([string]$stage) { if ($LASTEXITCODE -ne 0) { throw "$stage failed, exit $LASTEXITCODE" } }
try {
    if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
    if (-not $JavaHome) {
        $compiler = Get-Command "javac$suffix" -ErrorAction SilentlyContinue
        if ($compiler) { $JavaHome = Split-Path -Parent (Split-Path -Parent $compiler.Source) }
    }
    if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome "bin\java$suffix") -PathType Leaf)) {
        throw 'Set JAVA_HOME to JDK 21, add JDK 21 to PATH, or pass -JavaHome.'
    }
    if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_HOME }
    if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
    if (-not $AndroidSdk -or -not (Test-Path -LiteralPath $AndroidSdk -PathType Container)) {
        throw 'Set ANDROID_HOME (or ANDROID_SDK_ROOT), or pass -AndroidSdk.'
    }
    if ($Release) {
        $missing = @('FIVEG_STORE_FILE','FIVEG_STORE_PASSWORD','FIVEG_KEY_ALIAS','FIVEG_KEY_PASSWORD') |
            Where-Object { [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($_,'Process')) }
        if ($missing) { throw "Signed release requires: $($missing -join ', '). See docs/BUILDING.md." }
    }
    if ($ExpectedCertificateSha256 -and $ExpectedCertificateSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw '-ExpectedCertificateSha256 must contain exactly 64 hexadecimal characters.'
    }
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    if ($RobolectricDir) {
        $env:FIVEG_ROBOLECTRIC_DIR = (Resolve-Path -LiteralPath $RobolectricDir).Path
        $env:FIVEG_ROBOLECTRIC_OFFLINE = 'true'
    }
    $java = Join-Path $env:JAVA_HOME "bin\java$suffix"
    $buildTools = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0'
    foreach ($tool in @("zipalign$suffix", "aapt2$suffix", 'lib\apksigner.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $buildTools $tool) -PathType Leaf)) {
            throw 'Android SDK Build-Tools 36.0.0 is required. See docs/BUILDING.md.'
        }
    }
    $verificationRoot = Join-Path $nativeRoot "app\build\verification\$variant"
    $artifact = Join-Path $projectRoot "dist\5G-Switch-v1.1.1-$variant.apk"
    New-Item -ItemType Directory -Path $verificationRoot,(Split-Path -Parent $artifact) -Force | Out-Null
    $gradleArgs = @('--no-daemon','--console=plain','-p',$nativeRoot,':app:testDebugUnitTest',":app:lint$taskVariant",":app:assemble$taskVariant")
    if ($Offline) { $gradleArgs = @('--offline') + $gradleArgs }
    if ($onWindows) { & (Join-Path $nativeRoot 'gradlew.bat') @gradleArgs }
    else { & sh (Join-Path $nativeRoot 'gradlew') @gradleArgs }
    Assert-NativeSuccess 'Tests, lint and APK build'
    $built = Join-Path $nativeRoot "app\build\outputs\apk\$variant\app-$variant.apk"
    if (-not (Test-Path -LiteralPath $built -PathType Leaf)) { throw "Expected APK is missing: $built" }
    & (Join-Path $buildTools "zipalign$suffix") -c -P 16 4 $built
    Assert-NativeSuccess 'APK alignment'
    $signer = Join-Path $buildTools 'lib\apksigner.jar'
    $signature = & $java -jar $signer verify --verbose --print-certs $built
    Assert-NativeSuccess 'APK signature'
    $signature | Out-File -LiteralPath (Join-Path $verificationRoot 'signature.txt') -Encoding utf8
    if ($Release) {
        $signatureV2 = & $java -jar $signer verify --verbose --min-sdk-version 24 --max-sdk-version 27 $built
        Assert-NativeSuccess 'APK v2 signature block'
        $signatureV2 | Out-File -LiteralPath (Join-Path $verificationRoot 'signature-v2.txt') -Encoding utf8
    }
    if ($ExpectedCertificateSha256) {
        $certificate = $signature | Select-String '^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})$'
        if (-not $certificate -or $certificate.Matches[0].Groups[1].Value -ine $ExpectedCertificateSha256) {
            throw 'APK signing certificate does not match -ExpectedCertificateSha256.'
        }
    }
    $badging = & (Join-Path $buildTools "aapt2$suffix") dump badging $built
    Assert-NativeSuccess 'APK metadata'
    $badging | Out-File -LiteralPath (Join-Path $verificationRoot 'badging.txt') -Encoding utf8
    if ($badging[0] -notmatch "name='com.ysy.switcherfiveg.hyperos4' versionCode='26102003' versionName='1.1.1'") {
        throw 'Unexpected APK identity/version.'
    }
    Copy-Item -LiteralPath $built -Destination $artifact
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$artifact.sha256", "$hash  $([IO.Path]::GetFileName($artifact))`n", [Text.UTF8Encoding]::new($false))
    Write-Output "APK: $artifact"
    Write-Output "SHA256: $hash"
    Write-Output "Verification: $verificationRoot"
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name,$previousEnvironment[$name],'Process') }
}
