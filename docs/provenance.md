# Provenance

TheSettler_x_Create is developed independently using the public MineColonies and Create APIs.
The architecture follows the patterns those APIs require (resolver factories, token serialization,
logistics summaries). Similar structure in other mods that integrate these APIs is expected and does
not imply shared code or shared implementation.

No third-party bridge code is included. All integration logic is authored specifically for this
project, and the feature set is documented in ARCHITECTURE.md.

If external references, ideas, or sample snippets are used in the future, they will be explicitly
credited here (with source and scope), along with the corresponding implementation notes.

---

## Design constraints

**API-driven integration.** Request resolution and logistics flows follow MineColonies/Create
contracts. The mod observes, diagnoses, and extends native behavior — it does not replace or bypass
MineColonies internals.

**Serialization stability.** NBT keys and request-system IDs remain stable across versions unless
an explicit migration is provided. `SafeRequester` (factory 3001) is retained as a deserialization
shim for saves predating its removal; it is not used for new requests.

**No courier injection.** Delivery dispatch goes through the MineColonies warehouse queue only.
The mod does not assign jobs to couriers directly or maintain a parallel assignment structure.

**Storage scope.** Capacity planning and delivery reservation use rack-registered containers only.
Hut inventory is a transfer target, not a capacity source, so blocked rack states are not hidden by
hut buffer space.

---

## Architectural notes

The main integration complexity is the two-state problem: MineColonies tracks request lifecycle
through its own graph (IToken, RequestState, resolver assignments), while Create stock delivery
requires a separate inflight tracking layer (InflightEntry, reservations) that persists across
server reloads. Keeping these two stores consistent is the primary design challenge and the source
of most hardening work in the codebase.

The resolver system (`minecolonies/requestsystem/resolver/`) is split into focused single-purpose
services rather than a central resolver class. Each service owns one concern (pending token
collection, delivery creation, child reconciliation, etc.) and communicates through
`CreateShopRequestStateMutatorService` for lifecycle writes and `CreateShopLifecycleStateStore`
for runtime state. This split is a local refactor; no external implementations are adapted.

Lost-package recovery (overdue notices, reorder, handover, cancel) is implemented entirely through
MineColonies interaction handlers and standard request-state transitions. The interaction system
uses translatable IDs throughout to avoid locale-dependent response-key mismatches.
