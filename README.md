# Heart Rate Monitoring System

A Kotlin Multiplatform heart-rate monitoring system spanning Wear OS, Android, and Desktop clients.

## Overview

The system collects heart-rate data from a Galaxy Watch 5, relays it through an Android phone, and renders it on a desktop app.

### Architecture

```text
Galaxy Watch 5 (wear-app)
  -> Samsung Health Sensor SDK / platform heart-rate sensor / Health Services fallback
  -> Android Data Layer relay
Android phone (phone-app)
  -> WebSocket relay server
  -> BLE GATT fallback server
Desktop app (desktop-app)
  -> Real-time visualization
  -> Independent WebSocket and BLE transport controls
```

## Tech Stack

- Language: Kotlin Multiplatform
- UI: Jetpack Compose, Compose Multiplatform
- Architecture: Clean Architecture + MVVM
- DI: Koin
- Serialization: kotlinx.serialization
- Async: Kotlin Coroutines + Flow

## Features

### Phase 1 Foundation

- Shared data, domain, and presentation layers
- Platform-specific Wear, phone, and desktop shells
- Desktop dual display modes, including compact transparent overlay
- Shared utilities and unit tests

### Implemented Platform Pipeline

- Wear real sensor access with runtime permission handling
- Wear source selection across Samsung Health Sensor SDK, direct platform sensor, and Health Services fallback
- Wear off-body detection that stops monitoring when the watch is removed
- Wear dynamic sampling policy with low-battery fallback
- Phone relay over WebSocket and BLE
- Desktop WebSocket client and BLE fallback client
- Desktop connection controls with separate WebSocket and BLE switches
- Windows BLE verification scripts and reports under `.tmp/`

## Project Structure

```text
MyApplication/
|- shared/
|- wear-app/
|- phone-app/
|- desktop-app/
|- scripts/
|- githooks/
`- gradle/
```

## Getting Started

### Prerequisites

- JDK 17+
- Android Studio Hedgehog or later
- Android phone on API 34+
- Wear OS watch or emulator
- Windows 10+, macOS 12+, or Linux for desktop

### Build

```bash
./gradlew build
./gradlew :wear-app:assembleDebug
./gradlew :phone-app:assembleDebug
./gradlew :desktop-app:installDist
```

### Tests

```bash
./gradlew test
./gradlew :shared:testDebugUnitTest
```

### Device Deployment

```bash
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-wear.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-phone.ps1
```

### Desktop Usage Notes

- Use `Enter Compact Overlay` to switch into transparent overlay mode.
- In compact mode, long-press and drag to move the overlay.
- Double-click the red heart icon to return to the full window.
- On the `Connection` screen, use the `WebSocket` and `BLE` switches to enable or disable each transport independently.

## P2-A Runbook

1. Install and open the Wear and phone apps on paired devices.
2. Grant `BODY_SENSORS` on the watch.
3. Keep phone and desktop on the same LAN.
4. Run the desktop app and open the `Connection` page.
5. Set `ws://<PHONE_LAN_IP>:8080/heartrate` and enable the `WebSocket` switch.
6. Start monitoring on the watch and verify BPM appears on desktop.

## Git Hooks

This repository includes versioned hooks in `githooks/`.

### Install Hooks

```bash
git config core.hooksPath githooks
```

### Hook Policy

- `pre-commit`: blocks generated/local artifacts and checks staged text files for merge conflict markers and trailing whitespace
- `commit-msg`: enforces conventional commits with allowed types `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, and `revert`
- `pre-push`: runs `scripts/smoke-test.ps1` for shared unit tests plus desktop and Android compile validation

Use `--no-verify` only as an escape hatch.

## Development Commands

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew connectedAndroidTest
./gradlew :shared:dependencies --configuration runtimeClasspath
```
