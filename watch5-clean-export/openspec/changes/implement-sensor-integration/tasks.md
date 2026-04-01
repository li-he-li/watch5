# Tasks: Implement Real Sensor Integration and Data Transmission (Milestones)

## Overview

This change is executed in three milestones to reduce risk and deliver usable value earlier.

- **Estimated Duration**: 6 weeks
- **Execution Model**: P2-A (Core Path) -> P2-B (Stability) -> P2-C (Completeness)
- **Hard Start Gate**: Complete `add-kmp-foundation` task `14.5` (remote CI green) before coding P2-A
- **Current Execution Note (2026-03-12)**: P2-A acceptance is validation-blocked on unstable device chain; selected P2-B coding tasks are developed in parallel and remain pending milestone validation.

---

## 0. Start Gate

- [ ] 0.1 Confirm remote CI workflow is green for `add-kmp-foundation` task `14.5`
- [ ] 0.2 Record gate evidence in change notes (workflow URL/run ID)

---

## P2-A: Core Path (Weeks 1-2)

### A1 Wear real sensor path
- [x] A1.1 Create `HeartRateSensorManager` using `SensorManager` (`TYPE_HEART_RATE`)
- [x] A1.2 Implement runtime permission flow for `BODY_SENSORS` (+ background if required)
- [x] A1.3 Replace Wear mock repository wiring with real sensor source via app-layer DI
- [x] A1.4 Add battery level read and attach to outgoing data

### A2 Watch -> Phone transport
- [x] A2.1 Implement Wear-side Data Layer sender service/adapter
- [x] A2.2 Implement Phone `WearableListenerService` receiver and data parse
- [x] A2.3 Wire receiver output into phone relay pipeline

### A3 Phone -> Desktop transport
- [x] A3.1 Implement phone WebSocket server service (default port 8080, configurable)
- [x] A3.2 Replace desktop WebSocket mock with real client implementation
- [x] A3.3 Add desktop connection input/state UI (connect/disconnect/error)

### A4 P2-A validation
- [ ] A4.1 Real-device test: watch heart rate displayed on desktop
- [ ] A4.2 Verify p95 end-to-end latency < 1.5s on LAN
- [x] A4.3 Keep existing shared tests passing

### P2-A Exit Criteria
- [ ] A5.1 Core path is demoable on physical devices
- [ ] A5.2 No mock dependency in active core data path

---

## P2-B: Stability (Weeks 3-4)

### B1 Lifecycle and service hardening
- [x] B1.1 Add/finish foreground service lifecycle for watch monitoring
- [x] B1.2 Add/finish foreground/background policy for phone relay service
- [ ] B1.3 Validate behavior across app pause/resume and process recreation

### B2 Resilience and UX
- [x] B2.1 Implement deterministic reconnection strategy (Data Layer + WebSocket)
- [x] B2.2 Implement connection-state model and consistent user-visible status
- [x] B2.3 Handle permission denied/revoked paths with non-crashing UX

### B3 Battery and sampling policy
- [x] B3.1 Implement dynamic sampling policy and transition guards
- [x] B3.2 Add low-battery fallback policy (force lower sampling + user notice)

### B4 P2-B validation
- [ ] B4.1 Failure-path matrix test: disconnect/reconnect/permission/service interruption
- [ ] B4.2 Confirm no crash on planned failure paths

### P2-B Exit Criteria
- [ ] B5.1 Core path stable for repeated 30+ minute run
- [ ] B5.2 Reconnection and permission behavior is deterministic and test-backed

---

## P2-C: Completeness (Weeks 5-6)

### C1 BLE fallback
- [x] C1.1 Implement phone BLE GATT server (Heart Rate Service 0x180D, Measurement 0x2A37)
- [x] C1.2 Implement desktop BLE client for at least one target OS
- [x] C1.2a Linux desktop BLE backend via BlueZ CLI (`bluetoothctl` + `gatttool`)
- [x] C1.2b Windows desktop BLE backend via PowerShell WinRT bridge (supports `HRM_BLE_TARGET_MAC` override)
- [x] C1.3 Implement automatic fallback orchestration (WebSocket -> BLE -> WebSocket retry)
- [x] C1.4 Phone connection page displays current WS endpoint (`ws://<lan-ip>:<port>/heartrate`) for manual desktop input
- [ ] C1.5 Phone connection page displays BLE relay details (state/name/service UUIDs/best-effort identifier) for manual desktop input

### C2 Persistence and export
- [x] C2.1 Enable KSP and add Room dependencies in phone module
- [x] C2.2 Implement `HeartRateEntity`, `HeartRateDao`, `HeartRateDatabase`
- [x] C2.3 Implement buffering repository and offline flush policy
- [x] C2.4 Implement export (CSV/JSON)

### C3 Final verification
- [x] C3.0 Phone<->Desktop BLE propagation verified on Windows test machine (`.tmp/p2c_phone_desktop_ble_win_verify.ps1`, report: `.tmp/p2c_phone_desktop_ble_win_verify_report.md`)
- [ ] C3.1 Integration tests for BLE fallback and persistence paths
- [ ] C3.2 Full end-to-end smoke (normal + disconnect + reconnect + offline)
- [ ] C3.3 Document known limitations and operational notes
- [ ] C3.4 Manual connect fallback verification: copy endpoint/identifier from phone and connect from desktop input

### P2-C Exit Criteria
- [ ] C4.1 WebSocket unavailable scenario recovers through BLE
- [ ] C4.2 Offline data can be persisted and exported successfully
- [ ] C4.3 End-to-end test matrix completed and documented

---

## Cross-cutting Documentation Tasks

- [x] D1 Update `CLAUDE.md` with P2-A/B/C architecture and operations
- [x] D2 Update `README.md` setup/runtime sections for real sensor + transport
- [x] D3 Keep this task list status synchronized after each milestone

---

## Execution Rules

1. Do not start P2-A before gate 0 is complete.
2. Do not start P2-B before all P2-A exit criteria are checked.
3. Do not start P2-C before all P2-B exit criteria are checked.
4. Preserve shared public contract compatibility during P2-A unless a blocker is proven.
