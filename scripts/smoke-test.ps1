$ErrorActionPreference = "Stop"

Write-Host "==> Smoke test started"

$androidUserHome = Join-Path $PSScriptRoot "..\\.android"
New-Item -ItemType Directory -Force -Path $androidUserHome | Out-Null
$env:ANDROID_USER_HOME = (Resolve-Path $androidUserHome).Path
Write-Host "==> ANDROID_USER_HOME=$env:ANDROID_USER_HOME"

Write-Host "==> Running shared unit tests and compile checks"
& .\gradlew.bat :shared:testDebugUnitTest :desktop-app:compileKotlinDesktop :wear-app:compileDebugKotlin :phone-app:compileDebugKotlin --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Smoke test failed."
}

Write-Host "==> Smoke test result: PASS"
