# Design: KMP Foundation for Heart Rate Monitoring System

## Context

We are building a three-tier heart rate monitoring system:
1. **Wear OS App** (Galaxy Watch 5) - Heart rate data collection via BioActive sensor
2. **Android Phone App** - Data relay using Data Layer API + WebSocket/BLE transmission
3. **Desktop App** - Real-time visualization and analysis

The current project is a fresh Android template. We need to establish a KMP architecture that maximizes code sharing while respecting platform-specific constraints.

## Goals / Non-Goals

### Goals
- Set up KMP project structure with shared, wear-app, phone-app, desktop-app modules
- Implement shared data models for heart rate data transmission
- Create shared domain layer (repositories, use cases) for business logic
- Establish platform-specific communication layer abstractions
- Configure build system for multi-platform compilation
- Provide scaffolding for all three platform applications
- Provide desktop dual display modes for monitoring (full dashboard and compact transparent overlay)

### Non-Goals
- Complete implementation of heart rate sensor (Phase 2)
- Complete implementation of data transmission (Phase 2-3)
- Desktop visualization UI implementation (Phase 4)
- Production-ready error handling and edge cases (later phases)
- Performance optimization and battery optimization (later phases)

## Decisions

### Decision 1: Use Kotlin Multiplatform (KMP)
**What**: Adopt KMP for code sharing across Android, Wear OS, and Desktop

**Why**:
- Only technology that supports Wear OS + code sharing (Flutter/React Native don't support Wear OS)
- Native performance on all platforms
- 80-90% code sharing achievable for business logic
- Strong Kotlin ecosystem and IDE support

**Alternatives considered**:
- **Flutter**: No Wear OS support, requires Dart
- **React Native**: No Wear OS support, requires JavaScript/TypeScript
- **Separate native apps**: 0% code sharing, 3x development effort
- **Cross-platform C++**: Complex build setup, no Kotlin interop for Wear OS

**Trade-off**: KMP learning curve vs long-term maintainability → Accept learning curve for sustainable architecture

### Decision 2: Module Structure - Monorepo with KMP Shared Module
**What**: Use monorepo structure with shared/ module and separate platform app modules

**Structure**:
```
heart-rate-monitor/
├── shared/              # KMP module (commonMain, androidMain, desktopMain)
├── wear-app/            # Wear OS app (depends on shared)
├── phone-app/           # Android phone app (depends on shared)
└── desktop-app/         # Desktop app (depends on shared)
```

**Why**:
- Clear separation of concerns
- Platform apps can be built and deployed independently
- Shared module contains all business logic
- Fits Android Studio project structure expectations

**Alternatives considered**:
- **Multi-repo**: Harder to coordinate changes, no atomic commits across platforms
- **Single KMP module**: Can't target Wear OS and Android separately (different APK requirements)
- **Nested modules**: More complex Gradle configuration, harder to navigate

**Trade-off**: Slightly more complex Gradle setup vs clearer separation → Prefer separation for team productivity

### Decision 3: Clean Architecture with MVVM
**What**: Apply Clean Architecture principles in shared module, MVVM in platform UI layers

**Layers**:
```
shared/commonMain/
├── data/               # Data models, DTOs, API interfaces
├── domain/             # Business logic, use cases, repository interfaces
└── presentation/       # ViewModels, UI state (platform-specific implementations)
```

**Why**:
- Domain logic independent of platforms (testable, reusable)
- Platform-specific implementations in data/presentation layers
- ViewModels can be shared (using expect/actual for platform-specific differences)
- Aligns with Android best practices

**Alternatives considered**:
- **MVVM without Clean Architecture**: Faster to start, harder to maintain as complexity grows
- **Redux-style unidirectional flow**: Less idiomatic for Android/Kotlin community
- **Pure functional architecture**: Higher learning curve, less library support

**Trade-off**: More boilerplate initially vs long-term testability → Accept boilerplate for architecture benefits

### Decision 4: Expect/Actual for Platform-Specific APIs
**What**: Use Kotlin's `expect/actual` mechanism for platform-specific implementations

**Examples**:
```kotlin
// commonMain
expect class HeartRateSensorManager {
    suspend fun startListening(): Flow<HeartRateData>
    fun stopListening()
}

// androidMain/wear
actual class HeartRateSensorManager {
    // Wear OS SensorManager implementation
}

// desktopMain
actual class HeartRateSensorManager {
    // Mock or stub (desktop doesn't have sensors)
}
```

**Why**:
- Type-safe platform abstraction
- Compile-time guarantees that all platforms provide implementation
- Shared business logic can use platform APIs through common interface
- No reflection or runtime class loading

**Alternatives considered**:
- **Interface injection**: More flexible, but requires manual DI setup
- **Separate implementations per platform**: Duplicates code, harder to maintain
- **Service locator**: Runtime failures, less type-safe

**Trade-off**: Slightly more complex Gradle source sets vs type-safe abstractions → Prefer type safety

### Decision 5: Coroutine-Based Reactive Architecture
**What**: Use Kotlin Flow and coroutines for async operations throughout

**Why**:
- Native Kotlin support, no reactive extensions dependency
- Backpressure support for high-frequency sensor data
- Structured concurrency for lifecycle management
- Well-suited for sensor streams and network operations

**Alternatives considered**:
- **RxJava**: More mature, but Java-centric, steeper learning curve
- **Callbacks**: Harder to compose, no backpressure
- **Blocking I/O**: Not suitable for sensor streams

**Trade-off**: None - coroutines/Flow is the clear modern choice

### Decision 6: Serialization with kotlinx.serialization
**What**: Use kotlinx.serialization for JSON/data serialization

**Why**:
- Kotlin-first, compile-time safety
- Supports KMP across all targets
- Better performance than reflection-based (GSON/Moshi)
- JSON format for WebSocket transmission

**Alternatives considered**:
- **GSON/Moshi**: Java-centric, reflection-based, no KMP support
- **Jackson**: Heavy dependency, Java-centric
- **Manual serialization**: Error-prone, maintenance burden

**Trade-off**: Slightly less mature ecosystem vs KMP compatibility → Accept for KMP benefits

## Risks / Trade-offs

### Risk: KMP Build Complexity
- **Issue**: Gradle configuration for KMP is complex, especially with Compose
- **Mitigation**: Use version catalog for dependency management, document build process thoroughly
- **Fallback**: Simplify to Android-only if KMP proves too complex (but we'll validate early)

### Risk: Desktop Library Ecosystem
- **Issue**: Fewer mature KMP libraries for desktop (BLE, charts)
- **Mitigation**: Use well-established libraries (SimpleBLE, Compose Charts), fallback to platform-specific via JNI if needed
- **Fallback**: Implement desktop features later if library support is insufficient

### Risk: Wear OS Compose for KMP
- **Issue**: Compose for Wear OS is relatively new, KMP integration evolving
- **Mitigation**: Use stable Compose for Wear OS 1.3+, keep Wear OS UI code separate from shared
- **Fallback**: Keep Wear OS as pure Android module if Compose KMP doesn't work well

### Risk: Team Learning Curve
- **Issue**: KMP, expect/actual, and Compose Multiplatform are new to many developers
- **Mitigation**: Comprehensive documentation, code examples, pair programming for initial setup
- **Fallback**: Provide training resources and reference materials

## Migration Plan

### Step 1: Configure Gradle and Version Catalog
- Add KMP plugin to root build.gradle.kts
- Update libs.versions.toml with KMP dependencies
- Test build configuration compiles

### Step 2: Create Shared Module Structure
- Set up shared/build.gradle.kts with KMP targets
- Create source sets (commonMain, androidMain, desktopMain)
- Verify compilation with "Hello World" in each source set

### Step 3: Implement Shared Data Models
- Add kotlinx.serialization dependency
- Create HeartRateData, DeviceInfo, etc. in commonMain
- Test serialization/deserialization

### Step 4: Create Domain Layer
- Define repository interfaces in commonMain
- Create use case classes
- Implement mock repositories for testing

### Step 5: Set Up Platform Modules
- Create wear-app module with Compose for Wear OS
- Create phone-app module with Jetpack Compose
- Create desktop-app module with Compose Multiplatform
- Add desktop mode switch between full monitor UI and compact transparent overlay
- Configure dependencies on shared module

### Step 6: Implement Platform-Specific Communication (Scaffold)
- Create expect/actual for Data Layer API (wear/phone)
- Create expect/actual for WebSocket client (phone/desktop)
- Create expect/actual for BLE (phone/desktop)
- Add placeholder implementations (no actual logic yet)

### Step 7: Validation and Testing
- Build all three platform apps
- Run unit tests on shared module
- Verify app installation on target platforms

### Rollback Plan
- Each step is independently reversible
- Keep current `app/` module until new structure validated
- Git branches per step for easy rollback
- Document decisions in commit messages

## Open Questions

1. **Desktop BLE Library**: SimpleBLE KMP support is limited - should we use JNA binding to BlueZ (Linux) and native APIs (Windows/macOS)?
   - **Answer**: Start with WebSocket only, add BLE later if needed (WebSocket is primary per requirements)

2. **Desktop Charts Library**: Compose Charts is immature - should we use JavaFX/swing via interop?
   - **Answer**: Use Compose Multiplatform for UI, draw charts using Canvas API for Phase 1, evaluate libraries for Phase 4

3. **Health Connect Integration**: Should we integrate in Phase 1 or later?
   - **Answer**: Defer to Phase 3 (phone app development) - not needed for KMP foundation

4. **CI/CD Platform**: GitHub Actions vs GitLab CI vs others?
   - **Answer**: Use GitHub Actions (most common, free for public repos, good Android support)

## Performance Considerations

- **Build Time**: KMP builds take longer - we'll use Gradle build cache and parallel execution
- **Runtime Overhead**: Expect/actual has zero runtime cost (resolved at compile time)
- **Binary Size**: Slightly larger due to KMP runtime, but negligible for our use case
- **Memory Usage**: No significant impact - shared code is compiled to platform binaries

## Security Considerations

- **Data in Transit**: WebSocket should use WSS (TLS), but Phase 1 only scaffolds (no actual transmission)
- **Permissions**: Declare needed permissions in AndroidManifest (BODY_SENSORS, etc.) but don't request until Phase 2
- **Data Storage**: No storage implementation in Phase 1, but design supports encrypted storage later

## Testing Strategy

- **Unit Tests**: Shared business logic in commonMain (multi-platform tests run on JVM)
- **Integration Tests**: Platform-specific code tested separately
- **UI Tests**: Instrumented tests for Android/Wear OS (Phase 2+)
- **Manual Testing**: Build APK/installers for each platform to verify deployment

## References

- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
- [Wear OS Compose](https://developer.android.com/training/wearables/compose)
- [Project Feasibility Study](md/三端应用开发方案.md)
- [Project Context](openspec/project.md)
