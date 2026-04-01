param(
    [string]$DeviceSerial,
    [string]$ApkPath = "wear-app/build/outputs/apk/debug/wear-app-debug.apk"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )

    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        & adb @Args
    } else {
        & adb -s $DeviceSerial @Args
    }

    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($Args -join ' ')"
    }
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb not found in PATH."
}

$fullApkPath = Resolve-Path $ApkPath -ErrorAction Stop
$target = "default adb target"
if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $target = $DeviceSerial
}

Write-Host "==> Target device: $target"
Write-Host "==> APK: $fullApkPath"

Invoke-Adb devices
Invoke-Adb install -r $fullApkPath
Invoke-Adb shell am start -n com.heartrate.wear/.MainActivity

Write-Host "==> Wear app deployed and launched."
