# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Smart Watch Heart Rate Monitoring System** - a three-tier application platform consisting of:
1. **Wear OS App** (Galaxy Watch 5) - Heart rate data collection
2. **Android Phone App** - Data relay and processing
3. **Desktop App** - Real-time visualization and analysis

**Current State:** KMP foundation is implemented with shared module and three platform applications (Wear OS, Phone, Desktop). Phase 2 P2-A core path is coded (real watch sensor -> Data Layer -> phone WebSocket relay -> desktop WebSocket client), but P2-A acceptance validation is currently blocked by device/emulator limitations. P2-B is being developed in parallel with explicit pending regression validation.

**Tech Stack:**
- **Language:** Kotlin Multiplatform (KMP)
- **Build System:** Gradle with Kotlin DSL (version catalog in `gradle/libs.versions.toml`)
- **Platforms:**
  - Wear OS (minSdk 34, targetSdk 36)
  - Android Phone (minSdk 34, targetSdk 36)
  - Desktop (JVM target)
- **UI Frameworks:**
  - Wear OS: Traditional Views (future: Compose for Wear OS)
  - Phone: Jetpack Compose
  - Desktop: Compose Multiplatform
- **Architecture:** Clean Architecture with MVVM, 80-90% code sharing via KMP

## Common Development Commands

```bash
# Build all modules
./gradlew build

# Build specific platform
./gradlew :wear-app:assembleDebug
./gradlew :phone-app:assembleDebug
./gradlew :desktop-app:installDist

# Run unit tests (shared module)
./gradlew :shared:test

# Run instrumented tests (requires connected device/emulator)
./gradlew :wear-app:connectedAndroidTest
./gradlew :phone-app:connectedAndroidTest

# Install to connected device
./gradlew :wear-app:installDebug
./gradlew :phone-app:installDebug

# Run desktop application
./desktop-app/build/install/desktop-app/bin/desktop-app

# Clean build artifacts
./gradlew clean
```

## Architecture and Data Flow

The planned system follows this data flow:

```
Galaxy Watch 5 (Wear OS)
    ↓ [Data Layer API]
Android Phone App
    ↓ [WebSocket (primary) / BLE GATT (fallback)]
Desktop App
```

**Key Design Decisions:**
- **Data Layer API** used for Watch→Phone communication (Wear OS optimized, low power)
- **WebSocket** prioritized for Phone→Desktop transmission (cross-network, unlimited range)
- **BLE GATT Server** as backup for short-range Phone→Desktop (10-20 meters)
- **Shared business logic** via Kotlin Multiplatform (80-90% code reuse target)

## Target Hardware Specifications

**Watch Device:** Samsung Galaxy Watch 5 (44mm/40mm)
- Processor: Exynos W920 (5nm, dual-core 1.18GHz)
- RAM: 1.5GB, Storage: 16GB
- Battery: 398mAh (44mm) / 276mAh (40mm)
- Sensor: BioActive (3-in-1: Heart Rate + ECG + BIA)
- 8-PD PPG sensor, max 200Hz sampling
- Expected battery life: 3-5 hours continuous monitoring (dynamic sampling)

**Phone Recommendation:** Samsung Galaxy S23/S24 series (best Data Layer API stability)

## Key Technical Constraints

### Wear OS Limitations
- **Background services** terminated after 1 minute → Must use **Foreground Services**
- **Battery capacity** limits continuous high-frequency monitoring → Implement **dynamic sampling rate**
- **BLE Peripheral mode** requires special certificates → Use **Data Layer API** instead
- **Sampling rate** limited to ~5Hz normally (up to 10Hz with special permissions)

### Permissions Required (Wear OS)
```xml
BODY_SENSORS
BODY_SENSORS_BACKGROUND
ACCESS_BACKGROUND_LOCATION
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```

### Permissions Required (Phone)
```xml
BLUETOOTH_SCAN (Android 12+)
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE (BLE fallback)
FOREGROUND_SERVICE
INTERNET (WebSocket)
```

## Battery Optimization Strategies

1. **Dynamic Sampling Rate:**
   - Sleeping: 1Hz
   - Sitting: 1Hz
   - Walking: 3Hz
   - Running: 5Hz

2. **Sensor Optimization:**
   - Use sensor batching (aggregate 1 second)
   - Only sample when skin contact detected
   - Dark theme for AMOLED power saving

3. **Foreground Service Requirements:**
   - Must display persistent notification
   - Request battery optimization whitelist
   - Avoid `keepScreenOn`

## Project Structure

```
heart-rate-monitor/
├── shared/                    # KMP shared module (80-90% business logic)
│   ├── commonMain/            # Shared code across all platforms
│   │   ├── kotlin/com/heartrate/shared/
│   │   │   ├── data/          # Data models, DTOs, API interfaces
│   │   │   │   ├── model/     # HeartRateData, DeviceInfo, SensorReading
│   │   │   │   └── communication/  # DataLayerClient, WebSocketClient, BleClient
│   │   │   ├── domain/        # Business logic, use cases, repository interfaces
│   │   │   │   ├── repository/  # HeartRateRepository interface
│   │   │   │   └── usecase/     # ObserveHeartRate, GetBatteryLevel
│   │   │   └── presentation/  # ViewModels, UI state
│   │   │       ├── model/     # HeartRateUiState, ConnectionStatus
│   │   │       ├── viewmodel/ # HeartRateViewModel
│   │   │       └── ui/        # Theme, Formatters, UI constants
│   ├── androidMain/           # Android-specific implementations
│   │   └── kotlin/com/heartrate/shared/
│   │       ├── data/repository/       # HeartRateRepositoryImpl (Android)
│   │       └── data/communication/    # DataLayerClient, WebSocketClient, BleClient
│   ├── desktopMain/           # Desktop-specific implementations
│   │   └── kotlin/com/heartrate/shared/
│   │       ├── data/repository/       # HeartRateRepositoryImpl (Desktop)
│   │       └── data/communication/    # WebSocketClient, BleClient
│   └── commonTest/            # Shared unit tests
│       └── kotlin/com/heartrate/shared/
│           ├── data/model/    # Serialization tests
│           ├── domain/usecase/ # Use case tests
│           └── presentation/viewmodel/ # ViewModel tests
├── wear-app/                  # Wear OS application
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/heartrate/wear/
│   │   │   └── MainActivity.kt
│   │   └── res/               # Resources
│   └── build.gradle.kts
├── phone-app/                 # Android phone application
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/heartrate/phone/
│   │   │   ├── MainActivity.kt
│   │   │   └── theme/         # Compose theming
│   │   └── res/               # Resources
│   └── build.gradle.kts
├── desktop-app/               # Desktop application
│   ├── src/main/
│   │   └── kotlin/com/heartrate/desktop/
│   │       └── Main.kt        # Compose Desktop entry point
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml     # Version catalog
├── build.gradle.kts           # Root build configuration
└── settings.gradle.kts        # Project structure settings
```

## Architecture Layers

### Data Layer (shared/commonMain/kotlin/com/heartrate/shared/data/)
- **Models**: Data transfer objects with serialization support
  - `HeartRateData`: Core heart rate measurement
  - `DeviceInfo`: Device metadata
  - `SensorReading`: Raw sensor data with accuracy
- **Communication**: Platform-specific API abstractions
  - `DataLayerClient`: Wear OS Data Layer API (expect/actual)
  - `WebSocketClient`: WebSocket communication (expect/actual)
  - `BleClient`: Bluetooth Low Energy (expect/actual)

### Domain Layer (shared/commonMain/kotlin/com/heartrate/shared/domain/)
- **Repositories**: Platform-agnostic interfaces
  - `HeartRateRepository`: Sensor data access abstraction
- **Use Cases**: Business logic encapsulation
  - `ObserveHeartRate`: Stream heart rate data
  - `GetBatteryLevel`: Retrieve battery status

### Presentation Layer (shared/commonMain/kotlin/com/heartrate/shared/presentation/)
- **ViewModels**: Shared business logic for UI
  - `HeartRateViewModel`: Manages heart rate monitoring state
- **UI Models**: State management
  - `HeartRateUiState`: Immutable UI state
  - `ConnectionStatus`: Communication state enum
- **UI Utilities**: Cross-platform UI helpers
  - `Theme`: Color schemes, typography, spacing
  - `Formatters`: Display formatting functions
  - `UiConstants`: App-wide constants

## Data Models

**Standard Heart Rate Data (KMP shared):**
```kotlin
@Serializable
data class HeartRateData(
    val timestamp: Long,           // Unix timestamp (ms)
    val heartRate: Int,            // BPM
    val deviceId: String,
    val batteryLevel: Int? = null,
    val signalQuality: Int? = null
)
```

**BLE GATT Standard Format (0x2A37):**
```kotlin
data class BleHeartRateMeasurement(
    val flags: Byte,
    val heartRate: Short,
    val energyExpended: Short? = null,
    val rrIntervals: List<Short>? = null
)
```

## Performance Targets

- **End-to-end latency:** < 1 second
- **Watch battery life:** > 4 hours continuous monitoring
- **BLE connection stability:** > 95%
- **Desktop memory usage:** < 500MB
- **Data accuracy:** > 99%

## File Organization

### Build Configuration
- `build.gradle.kts` - Root project build configuration with KMP plugins
- `settings.gradle.kts` - Module structure settings (shared, wear-app, phone-app, desktop-app)
- `gradle/libs.versions.toml` - Version catalog for dependency management

### Module Build Files
- `shared/build.gradle.kts` - KMP configuration with android/jvm targets
- `wear-app/build.gradle.kts` - Wear OS app dependencies
- `phone-app/build.gradle.kts` - Android phone app with Compose
- `desktop-app/build.gradle.kts` - Desktop app with Compose Multiplatform

### Documentation
- `CLAUDE.md` - This file, AI assistant guidance
- `README.md` - Project overview and getting started guide
- `md/` - Technical documentation and feasibility studies
- `openspec/` - OpenSpec proposal system documentation

### Implementation Status

**Phase 1 (Complete):**
- ✅ KMP project structure
- ✅ Shared data models with serialization
- ✅ Domain layer (repositories, use cases)
- ✅ Presentation layer (ViewModels, UI state)
- ✅ Platform app scaffolds (Wear OS, Phone, Desktop)
- ✅ Mock implementations for communication layers
- ✅ Dependency injection with Koin
- ✅ Unit tests for data models and use cases
- ✅ Shared UI utilities (theming, formatting)

**Phase 2 (P2-A coded, P2-B partial):**
- ✅ Real Wear OS sensor integration (`SensorManager.TYPE_HEART_RATE`)
- ✅ Data Layer message sender/receiver path
- ✅ Phone WebSocket relay server (`:8080/heartrate`)
- ✅ Desktop WebSocket client and connection UI
- ✅ Wear/Phone foreground services for monitoring/relay continuity (P2-B)
- ✅ Deterministic reconnection strategy (Data Layer + WebSocket backoff) (P2-B)
- ✅ Dynamic sampling policy + low-battery fallback (P2-B)
- ⏳ Pending P2-A acceptance rerun on stable physical-device chain
- ⏳ Pending P2-B validation matrix (`B1.3`, `B4.x`, `B5.x`)

**Phase 3 (Planned):**
- ⏳ BLE fallback communication
- ⏳ Data persistence layer

**Phase 4 (Planned):**
- ⏳ Desktop visualization UI
- ⏳ Real-time charts and graphs
- ⏳ Data export functionality

## OpenSpec Integration

This project uses the **OpenSpec** proposal system for managing changes. When working on features that involve:
- Planning or proposals
- New capabilities or breaking changes
- Architecture shifts
- Major performance/security work

Always reference `@/openspec/AGENTS.md` for the authoritative specification before coding.

<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->
