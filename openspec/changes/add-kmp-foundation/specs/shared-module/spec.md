# Shared Module Specification

## ADDED Requirements

### Requirement: Shared Data Models for Heart Rate Monitoring
The system SHALL provide platform-independent data models in the shared module that represent heart rate measurements and device information, enabling consistent data representation across Wear OS, Android, and Desktop platforms.

#### Scenario: Serialize and deserialize heart rate data
- **WHEN** a HeartRateData object is serialized to JSON
- **THEN** the JSON contains timestamp, heartRate, deviceId, batteryLevel, and signalQuality fields
- **AND** the JSON can be deserialized back to an equivalent HeartRateData object

#### Scenario: Create heart rate data with required fields
- **WHEN** a HeartRateData object is created with timestamp, heartRate, and deviceId
- **THEN** the object is successfully instantiated
- **AND** optional fields (batteryLevel, signalQuality) default to null

#### Scenario: Platform-specific device info representation
- **WHEN** DeviceInfo is queried on any platform (Wear OS, Android, Desktop)
- **THEN** the object contains platform identifier, OS version, and app version
- **AND** the format is consistent across platforms

### Requirement: Shared Domain Layer with Repository Pattern
The system SHALL provide a domain layer in the shared module with repository interfaces and use cases, encapsulating business logic independently of platform-specific implementations.

#### Scenario: Abstract heart rate repository interface
- **WHEN** HeartRateRepository interface is defined in commonMain
- **THEN** it declares methods for observing heart rate flow and getting battery level
- **AND** each platform provides actual implementation via expect/actual

#### Scenario: Use case observes heart rate data
- **WHEN** ObserveHeartRate use case is invoked
- **THEN** it returns a Flow<HeartRateData> from the repository
- **AND** the use case can be used in any platform's ViewModel

#### Scenario: Use case retrieves battery level
- **WHEN** GetBatteryLevel use case is invoked
- **THEN** it returns the current battery percentage from the repository
- **AND** the use case logic is identical across all platforms

### Requirement: Expect/Actual Platform Abstractions
The system SHALL use Kotlin's expect/actual mechanism to provide platform-specific implementations for communication APIs (Data Layer, WebSocket, BLE) while maintaining a common interface in shared code.

#### Scenario: Data Layer API abstraction for Wear OS and Phone
- **WHEN** DataLayerClient is used in commonMain code
- **THEN** it provides methods for sending and receiving data between Watch and Phone
- **AND** the actual implementation uses Wear OS Data Layer API on Android
- **AND** a mock implementation is provided for Desktop

#### Scenario: WebSocket client abstraction for Phone and Desktop
- **WHEN** WebSocketClient is used in commonMain code
- **THEN** it provides methods for connecting, sending messages, and receiving data
- **AND** the actual implementation uses OkHttp on Android
- **AND** the actual implementation uses Ktor on Desktop

#### Scenario: BLE client abstraction for Phone and Desktop
- **WHEN** BleClient is used in commonMain code
- **THEN** it provides methods for scanning, connecting, and reading heart rate data
- **AND** the actual implementation uses Android Bluetooth APIs on Phone
- **AND** the actual implementation uses SimpleBLE or equivalent on Desktop

### Requirement: Kotlin Multiplatform Build Configuration
The system SHALL be configured as a KMP project with targets for Android (Wear OS and Phone) and JVM (Desktop), enabling compilation of shared code to platform-specific binaries.

#### Scenario: Compile shared code for Android
- **WHEN** the shared module is built for Android target
- **THEN** commonMain and androidMain source sets are compiled
- **AND** expect declarations are resolved with actual implementations from androidMain

#### Scenario: Compile shared code for Desktop JVM
- **WHEN** the shared module is built for JVM target
- **THEN** commonMain and desktopMain source sets are compiled
- **AND** expect declarations are resolved with actual implementations from desktopMain

#### Scenario: Version catalog manages dependencies
- **WHEN** dependencies are declared in gradle/libs.versions.toml
- **THEN** all modules use consistent versions for KMP, Compose, and libraries
- **AND** updating a version in one place updates all usages

### Requirement: Dependency Injection Setup
The system SHALL provide a dependency injection framework (Koin) configured in each platform app, enabling shared ViewModels and use cases to be resolved consistently.

#### Scenario: Resolve shared ViewModel in Wear OS app
- **WHEN** HeartRateViewModel is injected in Wear OS app
- **THEN** Koin provides the ViewModel with platform-specific repository implementation
- **AND** the ViewModel behaves identically to other platforms

#### Scenario: Resolve shared use case in Phone app
- **WHEN** ObserveHeartRate use case is injected in Phone app
- **THEN** Koin provides the use case with Android-specific repository
- **AND** the use case logic matches the shared definition

#### Scenario: Resolve shared use case in Desktop app
- **WHEN** ObserveHeartRate use case is injected in Desktop app
- **THEN** Koin provides the use case with Desktop-specific repository
- **AND** the use case behavior matches other platforms

### Requirement: Shared Presentation Layer with ViewModels
The system SHALL provide ViewModels and UI state models in the shared module, enabling UI logic to be reused across platforms while delegating platform-specific UI rendering to native Compose implementations.

#### Scenario: Heart rate ViewModel manages UI state
- **WHEN** HeartRateViewModel is created
- **THEN** it exposes heart rate data as StateFlow<HeartRateUiState>
- **AND** it uses ObserveHeartRate use case to fetch data
- **AND** it handles loading, success, and error states

#### Scenario: Platform-specific UI consumes shared ViewModel
- **WHEN** Wear OS, Phone, and Desktop UIs observe the same ViewModel
- **THEN** all platforms receive identical UI state updates
- **AND** each platform renders the state using its native Compose framework

### Requirement: Modular Project Structure
The system SHALL be organized as a monorepo with separate modules for shared code, Wear OS app, Phone app, and Desktop app, enabling independent development and deployment while maximizing code sharing.

#### Scenario: Shared module compiles independently
- **WHEN** only the shared module is built
- **THEN** it compiles without requiring platform app modules
- **AND** all tests in shared module pass

#### Scenario: Platform apps depend on shared module
- **WHEN** Wear OS, Phone, or Desktop app is built
- **THEN** it automatically compiles the shared module as a dependency
- **AND** the app can access all shared APIs

#### Scenario: Independent platform app deployment
- **WHEN** a platform app is deployed (e.g., Wear OS APK)
- **THEN** only that platform's app binary is distributed
- **AND** the shared code is compiled into the app binary

### Requirement: Serialization and Deserialization
The system SHALL use kotlinx.serialization to convert data models to/from JSON format, enabling efficient data transmission over WebSocket and storage.

#### Scenario: Serialize heart rate data for WebSocket transmission
- **WHEN** HeartRateData is serialized to JSON string
- **THEN** the JSON is valid and contains all required fields
- **AND** the JSON string is suitable for transmission over WebSocket

#### Scenario: Deserialize received WebSocket data
- **WHEN** a JSON string is received from WebSocket
- **THEN** it can be deserialized to HeartRateData object
- **AND** the deserialized object matches the original data

#### Scenario: Handle serialization errors gracefully
- **WHEN** invalid JSON is provided for deserialization
- **THEN** the operation throws a SerializationException
- **AND** the error can be caught and handled by the caller
