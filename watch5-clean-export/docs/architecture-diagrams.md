# Architecture Diagrams

## 1) End-to-End Data Flow

```mermaid
flowchart LR
    A[Galaxy Watch 5<br/>Wear OS App] -->|Data Layer API| B[Android Phone App]
    B -->|WebSocket Primary| C[Desktop App]
    B -->|BLE GATT Fallback| C
    C --> D[(Local Storage)]
    C --> E[Real-Time Visualization]
```

## 2) Module Structure

```mermaid
flowchart TD
    SHARED[shared module<br/>data/domain/presentation]
    WEAR[wear-app]
    PHONE[phone-app]
    DESKTOP[desktop-app]

    WEAR --> SHARED
    PHONE --> SHARED
    DESKTOP --> SHARED
```

## 3) Shared Layer Architecture

```mermaid
flowchart LR
    U[UI Layer] --> VM[HeartRateViewModel]
    VM --> UC1[ObserveHeartRate]
    VM --> UC2[GetBatteryLevel]
    UC1 --> REPO[HeartRateRepository]
    UC2 --> REPO
    REPO --> IMPL[Platform Implementations]
```

## 4) Delivery Pipeline

```mermaid
flowchart LR
    CODE[Code Push / PR] --> TEST[Shared Tests]
    TEST --> ABUILD[Android APK Build]
    TEST --> DBUILD[Desktop Package Build]
    ABUILD --> APK[(APK Artifacts)]
    DBUILD --> DIST[(Desktop Artifacts)]
```

## Notes

- Phone to desktop transmission is standardized as **WebSocket primary** with **BLE fallback**.
- The project remains Phase 1 scaffold-first, with real sensor and transport logic scheduled for later phases.
