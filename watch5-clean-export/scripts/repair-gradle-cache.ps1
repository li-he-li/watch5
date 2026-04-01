param(
    [switch]$CleanAllTransforms
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $projectRoot
try {
    if (Test-Path ".\gradlew.bat") {
        & .\gradlew.bat --stop | Out-Null
    }
} catch {
    Write-Warning "Unable to stop Gradle via wrapper: $($_.Exception.Message)"
} finally {
    Pop-Location
}

$gradleProcesses = Get-CimInstance Win32_Process |
    Where-Object { $_.Name -like "java*" -and $_.CommandLine -match "GradleDaemon|org.gradle.launcher.daemon" }

foreach ($proc in $gradleProcesses) {
    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
}

$gradleHome = Join-Path $env:USERPROFILE ".gradle"
$transformsRoot = Join-Path $gradleHome "caches\9.2.1\transforms"
$knownBrokenTransform = Join-Path $transformsRoot "665eff122a5e52a2d46f0b2965cb8909"

if (Test-Path $transformsRoot) {
    if ($CleanAllTransforms) {
        Get-ChildItem $transformsRoot -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "Cleared all transform caches under: $transformsRoot"
    } elseif (Test-Path $knownBrokenTransform) {
        Remove-Item $knownBrokenTransform -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "Removed known broken transform: $knownBrokenTransform"
    } else {
        Write-Host "Known broken transform not found, nothing to delete."
    }

    Get-ChildItem $transformsRoot -Filter "*.lock" -Recurse -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Repair finished."
Write-Host "Next step: run '.\\gradlew clean :shared:compileDebugLibraryResources :phone-app:compileDebugNavigationResources :wear-app:compileDebugNavigationResources'"
