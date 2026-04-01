# Project Context

## Purpose
Build a three-tier smart watch heart rate monitoring system that:
- Collects real-time heart rate data from Samsung Galaxy Watch 5 (Wear OS)
- Relays data through an Android phone app
- Visualizes and analyzes data on a desktop application
- Enables long-term health monitoring and analysis

## Tech Stack
- **Languages:** Kotlin (primary), Java (legacy compatibility)
- **Build System:** Gradle with Kotlin DSL, version catalog management
- **Android:**
  - minSdk: 34 (Android 14)
  - targetSdk: 36 (Android 14)
  - compileSdk: 36
- **Planned Architecture:** Kotlin Multiplatform (KMP) for code sharing
- **UI Frameworks:**
  - Wear OS: Jetpack Compose for Wear OS 1.3+
  - Phone: Jetpack Compose
  - Desktop: Compose Multiplatform
- **Communication:**
  - Watch→Phone: Wear OS Data Layer API
  - Phone→Desktop: WebSocket (primary), BLE GATT (fallback)

## Project Conventions

### Code Style
- **Kotlin:** Follow Android Kotlin style guide, use coroutines for async
- **Naming:** kebab-case for change IDs (verb-led: `add-`, `update-`, `refactor-`)
- **Architecture:** MVVM + Clean Architecture
- **Files:** Organize by feature (data/domain/ui presentation layers)

### Architecture Patterns
- **MVVM:** ViewModels hold UI state, Repositories handle data
- **Clean Architecture:** Domain layer independent of platforms
- **KMP Structure:** `commonMain/` for shared logic, platform-specific for native APIs
- **Data Flow:** Unidirectional data flow (StateFlow/Flow)

### Testing Strategy
- **Unit Tests:** JUnit for business logic, mock external dependencies
- **Integration Tests:** Test data flow between tiers
- **Instrumented Tests:** AndroidX Test for sensor/Bluetooth behavior
- **Target:** >80% coverage for shared business logic

### Git Workflow
- **Branching:** feature/`change-id` branches from main
- **Commit Messages:** Conventional commits (feat:, fix:, refactor:, etc.)
- **OpenSpec:** Create proposal before implementing features
- **PRs:** Require OpenSpec proposal approval before merging

## Domain Context

### Heart Rate Monitoring
- **Sampling Rates:** Dynamic based on activity (1-5 Hz)
- **Data Accuracy:** Target >99% accuracy vs medical devices
- **Battery Constraints:** Watch battery limits continuous monitoring
- **BLE Standards:** Heart Rate Service (0x180D), Measurement (0x2A37)

### Wear OS Constraints
- **Background Services:** Terminated after 1 minute without foreground service
- **Sensor Permissions:** BODY_SENSORS, BODY_SENSORS_BACKGROUND required
- **Battery Optimization:** Must whitelist app for background operation
- **Sampling Limits:** ~5Hz normal, up to 10Hz with special permissions

### Data Pipeline
```
Watch Sensor (PPG) → Data Layer API → Phone Buffer → WebSocket/BLE → Desktop → Storage + Visualization
```

## Important Constraints

### Hardware Constraints
- **Watch Battery:** 398mAh (44mm) / 276mAh (40mm) - limits continuous monitoring
- **BLE Range:** 10-20 meters for direct connection
- **Network:** Phone→Desktop requires same WiFi or BLE proximity
- **Processor:** Exynos W920 dual-core 1.18GHz (watch)

### Platform Constraints
- **Wear OS:** Data Layer API only works with paired phone (no internet-independent mode)
- **BLE Peripheral:** Requires special certificates on Galaxy Watch → use Data Layer instead
- **Foreground Service:** Must display persistent notification
- **Android 12+:** New Bluetooth permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT)

### Performance Targets
- **End-to-end Latency:** < 1 second from sensor to display
- **Watch Battery Life:** > 4 hours continuous monitoring (dynamic sampling)
- **Data Accuracy:** > 99%
- **Connection Stability:** > 95% uptime
- **Desktop Memory:** < 500MB

### Battery Optimization Requirements
- Use sensor batching (1-second aggregation)
- Dynamic sampling rate based on activity state
- Dark theme for AMOLED displays
- Sample only when skin contact detected
- Avoid keepScreenOn

## External Dependencies

### Wear OS APIs
- **Google Play Services:** Wearable Data Layer API
- **Android Sensors:** SensorManager for heart rate (TYPE_HEART_RATE)
- **Foreground Service:** For background monitoring

### Phone APIs
- **OkHttp:** WebSocket client implementation
- **BluetoothAdapter:** BLE GATT Server (backup transmission)
- **Health Connect:** (Optional) Integration with Android health platform

### Desktop Libraries
- **Compose Multiplatform:** UI framework
- **SimpleBLE:** Cross-platform BLE client
- **Ktor:** WebSocket server
- **SQLite:** Local data storage

### Development Tools
- **Android Studio:** Hedgehog+ for Wear OS support
- **OpenSpec CLI:** Spec-driven development workflow
- **Gradle:** Build automation with version catalogs
