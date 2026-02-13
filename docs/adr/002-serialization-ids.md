# ADR 002: Serialization IDs and Tags

## Status
Accepted

## Context
MineColonies resolver factories require token and location serialization. IDs and tag keys must remain
stable to ensure world compatibility. Changing keys or IDs requires migration logic and is treated as a
breaking change.

## Decision
Keep existing serialization IDs and tag keys unless a migration path is explicitly implemented. Any
future change must include backward-compatible reads and documented rationale.

## Consequences
- Stable saves across versions.
- Less churn when addressing external claims about similarity, at the cost of retaining existing keys.
