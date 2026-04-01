# Change: Implement Real Sensor Integration and Data Transmission (Milestone Delivery)

## Why

Phase 1 delivered the KMP foundation and mock end-to-end shape. The current Phase 2 scope is technically correct but too broad to execute safely as a single batch.

Current facts:
- `add-kmp-foundation` is nearly complete, but start gate `14.5` (remote CI green) is still pending.
- Local engineering baseline is stable (multi-module compile and shared tests pass).
- Real sensor and transport implementations are still mock-backed.

To reduce integration risk, Phase 2 is restructured into three delivery milestones with explicit gates.

## What Changes

### Delivery Model Change
- Replace one-shot full Phase 2 execution with milestone-driven delivery:
  - **P2-A (Core Path):** Wear sensor -> Data Layer -> Phone WebSocket server -> Desktop WebSocket client.
  - **P2-B (Stability):** foreground services, reconnection, runtime permission UX, battery and failure handling.
  - **P2-C (Completeness):** BLE fallback and persistence/export.

### Scope Change
- Keep the same target capabilities overall, but defer BLE and persistence until the core path is validated.
- Prioritize measurable end-to-end usability before adding fallback complexity.

### Operational Fallback UX
- Add an operator-assisted fallback path for daily use:
  - Phone displays current WebSocket endpoint (`ws://<phone-lan-ip>:<port>/heartrate`).
  - Phone displays BLE relay details (state, advertised name, service UUIDs, best-effort identifier).
  - Desktop supports manual endpoint/target input when auto-discovery is unavailable.

### Interface Strategy
- Preserve existing shared `expect/actual` public API shapes during P2-A.
- Introduce app-layer services and DI wiring for platform-specific behavior instead of immediate shared contract expansion.

## Impact

- **Affected specs**: sensor-integration, data-layer-api, websocket-communication, ble-fallback.
- **Execution impact**: lower risk and faster first value by validating one production path early.
- **Compatibility**: no required breaking change to shared public contracts in P2-A.
- **Dependencies**: Play Services Wearable, Ktor websocket stack, BLE stack (in P2-C), Room/KSP (in P2-C).

## Risks & Mitigations

### Risk 1: Start Gate Drift
- **Issue**: Phase 2 starts before remote CI closure from Phase 1.
- **Mitigation**: hard gate; do not begin P2-A until `add-kmp-foundation` task `14.5` is done.

### Risk 2: Multi-protocol Complexity
- **Issue**: implementing WebSocket + BLE + persistence simultaneously increases defect rate.
- **Mitigation**: stage BLE and persistence to P2-C after core path validation.

### Risk 3: App-layer vs shared-layer coupling
- **Issue**: direct shared-contract rewrites can destabilize all apps.
- **Mitigation**: keep shared interfaces stable first; add app-layer wiring and adapters.

## Non-Goals

- Desktop visualization redesign.
- Advanced analytics/ML.
- Multi-device mesh support.
- Cloud sync and remote internet-first architecture.

## Dependencies

### Requires
- `add-kmp-foundation` completion, including task `14.5` remote CI green.
- Galaxy Watch 5 + Android phone + desktop test machine.

### Blocks
- Phase 3 protocol optimization and analytics work should wait for P2-A and P2-B acceptance.

## Success Criteria

### P2-A acceptance
1. Wear reads real heart rate (SensorManager).
2. Phone receives data via Data Layer.
3. Desktop displays live heart rate via WebSocket.
4. End-to-end latency p95 < 1.5s on LAN.

### P2-B acceptance
5. Foreground/background behavior is stable on watch and phone.
6. Reconnection and permission-denied paths are user-visible and non-crashing.
7. Battery policy and sampling fallback work as designed.

### P2-C acceptance
8. BLE fallback can replace WebSocket when unavailable.
9. Phone-side persistence and export work for offline periods.
10. Integration and manual test matrix passes.

## Estimated Timeline

- **P2-A (Weeks 1-2)**: core path usable on real devices.
- **P2-B (Weeks 3-4)**: stability hardening.
- **P2-C (Weeks 5-6)**: fallback and persistence completion.

**Total**: 6 weeks (risk-adjusted milestone execution)
