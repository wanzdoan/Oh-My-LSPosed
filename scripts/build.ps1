param([switch]$Release)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$taskJdk = $env:JAVA_HOME
$cachedJdk = Join-Path $env:USERPROFILE '.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
if (Test-Path -LiteralPath $cachedJdk) { $taskJdk = $cachedJdk }
if (-not $taskJdk -or -not (Test-Path -LiteralPath (Join-Path $taskJdk 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to JDK 21.'
}
$env:JAVA_HOME = $taskJdk
$variant = if ($Release) { 'Release' } else { 'Debug' }
& ./gradlew.bat "-Dorg.gradle.java.home=$taskJdk" ":app:assemble$variant" testDebugUnitTest ":app:lint$variant" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
if (-not $Release) {
    python scripts/audit_apk.py app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'APK audit failed' }
}
