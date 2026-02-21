# Feature Status

## Strict Bridge State Machine

Status: In Progress (stable core flow, ongoing live-world hardening)

Scope:
- MineColonies stays authoritative for request and delivery state.
- Create Shop resolves availability and creates delivery children through official APIs.
- No mixins, no reflection on private MineColonies internals.

Current behavior:
- Network ordering is worker-gated (`isWorkerWorking()`).
- Delivery creation from already-present rack items can continue during temporary worker idle.
- Pending partials can top up from Create network when worker is working and stock becomes available.
- Parent/child link dedupe and drift recovery are active in pending reconciliation.

Known focus area:
- Live-world validation for long-running colonies under resolver-token drift and worker status churn.
