# Change: Add Kotlin Multiplatform Foundation for Heart Rate Monitoring System

## Why

The project requires a three-tier heart rate monitoring system (Watch → Phone → Desktop) with 80-90% code sharing across platforms. Currently, the project is a fresh Android template without any implementation. We need to establish the KMP architecture foundation to enable:
- Shared business logic across Wear OS, Android, and Desktop platforms
- Unified data models for heart rate data transmission
- Efficient development workflow with platform-specific implementations only where necessary
- Reduced maintenance burden through code reuse

## What Changes

- **BREAKING**: Restructure project from single Android app to KMP multi-module project
- Add KMP gradle configuration and version catalog setup
- Create `shared/` module with commonMain, androidMain, and desktopMain source sets
- Implement shared data models (`HeartRateData`, `DeviceInfo`, etc.)
- Create shared domain layer (repositories, use cases)
- Set up platform-specific communication protocols (Data Layer API, WebSocket, BLE)
- Add Wear OS app module with heart rate sensor scaffold
- Add Phone app module with data relay scaffold
- Add Desktop app module with visualization scaffold
- Add desktop dual display modes: full monitor view + compact transparent heart-rate overlay
- Configure CI/CD pipeline for multi-platform builds

## Impact

- **Affected specs**: New capabilities added (none modified)
- **Affected code**: Complete project restructure from monolithic Android app to multi-module KMP project
- **Migration**: Current `app/` module will be replaced with targeted platform modules (wear-app, phone-app, desktop-app)
- **Dependencies**: Adds KMP plugin, Compose Multiplatform, Wear OS Compose, OkHttp, Ktor, SimpleBLE
- **Build time**: Initial build will take longer due to KMP compilation for multiple targets

## Benefits

- **Code sharing**: 80-90% of business logic shared across platforms
- **Type safety**: Shared data models ensure consistency across tiers
- **Maintainability**: Single source of truth for business rules
- **Testing**: Shared business logic can be tested once
- **Development speed**: Platform-specific code only where absolutely necessary

## Risks & Mitigations

- **Risk**: KMP learning curve for team
  - **Mitigation**: Comprehensive documentation in `design.md`, reference to official KMP guides

- **Risk**: Increased initial setup complexity
  - **Mitigation**: Detailed `tasks.md` with step-by-step implementation, validation at each step

- **Risk**: Desktop app limited by KMP library ecosystem
  - **Mitigation**: Use mature libraries (Compose Multiplatform, Ktor), fallback to platform-specific where needed

- **Risk**: Wear OS Compose for KMP still evolving
  - **Mitigation**: Use stable Compose for Wear OS 1.3+, keep platform-specific code isolated

## Non-Goals

- Implement complete heart rate sensor integration (Phase 2)
- Implement complete data transmission protocols (Phase 2-3)
- Implement desktop visualization UI (Phase 4)
- Add analytics or ML features (future phases)
- Support iOS platforms (out of scope for current requirements)
