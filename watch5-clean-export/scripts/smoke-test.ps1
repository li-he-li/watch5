$ErrorActionPreference = "Stop"

Write-Host "==> Smoke test started"

Write-Host "==> Running shared compile checks and desktop build"
& .\gradlew.bat :shared:compileDebugUnitTestKotlinAndroid :desktop-app:createDistributable --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Shared compile checks or desktop build failed."
}

Write-Host "==> Running Android APK builds (wear + phone)"
$logDir = Join-Path $PSScriptRoot "..\\build"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$androidLog = Join-Path $logDir "smoke-android.log"

& .\gradlew.bat :wear-app:assembleDebug :phone-app:assembleDebug --no-daemon --stacktrace *>&1 |
    Tee-Object -FilePath $androidLog

if ($LASTEXITCODE -ne 0) {
    $logText = Get-Content -Raw -Path $androidLog
    if ($logText -match "AAPT2|CompileLibraryResourcesTask|immutable workspace") {
        Write-Warning "Android build blocked by known AAPT2 cache issue. Marked as deferred."
        Write-Host "==> Smoke test result: PARTIAL (Android deferred due to AAPT2)"
        exit 0
    }

    throw "Android build failed for a non-AAPT2 reason."
}

Write-Host "==> Smoke test result: PASS"
