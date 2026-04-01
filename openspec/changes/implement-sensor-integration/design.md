# Design: Real Sensor Integration and Data Transmission (Milestone Execution)

## Context

Phase 1 completed the KMP baseline with mock implementations. Phase 2 must deliver real hardware and transport integration, but now uses staged milestones to reduce integration risk.

Current state:
- `HeartRateRepositoryImpl` in Android/Desktop is mock-based.
- `DataLayerClient`, `WebSocketClient`, and `BleClient` are mock implementations.
- UI and DI scaffolding already exists across Wear/Phone/Desktop.

Execution constraints:
- Shared `androidMain` has a single `actual` set; Wear sensor path and Phone relay path must be composed via app-layer services/DI.
- Start gate: `add-kmp-foundation` task `14.5` must be complete before P2-A coding.

## Delivery Model

### Milestone P2-A (Core Path, Weeks 1-2)
Goal: first usable real pipeline.

Scope:
1. Wear real sensor integration (`SensorManager`, permission flow, battery read).
2. Wear -> Phone real Data Layer transfer.
3. Phone WebSocket server.
4. Desktop WebSocket client and connection UI.

Acceptance:
- Real BPM appears on desktop from watch.
- p95 end-to-end latency < 1.5s on LAN.

### Milestone P2-B (Stability, Weeks 3-4)
Goal: production-like behavior for the core path.

Scope:
1. Foreground services and lifecycle hardening.
2. Reconnection strategy and state transitions.
3. Permission denied/revoked UX and graceful degradation.
4. Dynamic sampling policy and low-battery behavior.

Acceptance:
- No crash on expected failure paths.
- Reconnect behavior and user-visible states are deterministic.

### Milestone P2-C (Completeness, Weeks 5-6)
Goal: fallback and offline resilience.

Scope:
1. Phone BLE GATT server + desktop BLE client fallback path.
2. Phone persistence (Room) and offline buffering/export.
3. Final integration matrix and manual device validation.

Acceptance:
- BLE takeover works when WebSocket is unavailable.
- Offline periods can be persisted and exported.

## Architectural Decisions

### Decision 1: Preserve shared public contracts in P2-A
- Keep current shared `expect/actual` API shapes stable.
- Add app-layer adapters/services first; evolve shared contracts only when required by validated behavior.

### Decision 2: WebSocket remains primary, BLE remains fallback
- WebSocket is lower complexity for LAN and multi-client behavior.
- BLE is introduced only after core path is stable.

### Decision 3: Phone is central relay + persistence node
- Watch remains sampling-focused and lightweight.
- Phone owns protocol bridging, buffering, and export.

### Decision 4: Gate-based rollout
- Each milestone has explicit acceptance tests.
- Next milestone starts only after previous acceptance is met.

### Decision 5: Operator-assisted manual connect fallback
- For environments where discovery is unstable, phone UI exposes current WS/BLE connection details.
- Desktop can connect using manual user input copied from phone.
- This fallback is treated as a first-class operational path, not only a debug workaround.

## Interfaces and Data Flow

Data path:
1. Wear SensorManager produces heart-rate readings.
2. Data Layer sends readings to phone listener service.
3. Phone WebSocket server broadcasts readings to desktop.
4. Desktop client updates UI and connection state.
5. If WebSocket unavailable (P2-C), BLE fallback is activated.
6. If discovery is unavailable, user copies WS/BLE connection details from phone UI and manually connects on desktop.

Public interface policy:
- `HeartRateRepository`, `DataLayerClient`, `WebSocketClient`, `BleClient` public contracts stay source-compatible during P2-A.
- New behavior is introduced behind existing contracts or app-layer wiring.

## Testing and Validation Strategy

### P2-A tests
- Unit: sensor manager/data serialization.
- Integration: watch->phone and phone->desktop on real devices.
- KPI: p95 latency < 1.5s.

### P2-B tests
- Lifecycle and reconnection tests.
- Permission and service interruption scenarios.
- KPI: no crash in planned failure-path matrix.

### P2-C tests
- BLE fallback path tests.
- Persistence/export verification.
- Full end-to-end smoke with disconnect/reconnect scenarios.

## Risks and Controls

1. Gate bypass risk.
- Control: enforce `14.5` completion before P2-A.

2. Scope creep risk.
- Control: lock milestone boundaries; defer non-core capabilities.

3. Platform drift risk.
- Control: preserve shared contract stability until core behavior is proven.

## Migration / Rollback

- Implement by milestone branches:
  - `feature/p2a-core-path`
  - `feature/p2b-stability`
  - `feature/p2c-completeness`
- Rollback granularity is per milestone merge.
- Keep mock implementations available behind toggles or branch history for fast fallback during bring-up.
