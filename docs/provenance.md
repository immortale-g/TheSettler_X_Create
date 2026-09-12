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
MineColonies internals. The adapter is deliberately thin, so upstream API changes can be absorbed
in the adapter components without redesigning the mod. No mixins, and no reflection on private
MineColonies internals.

**Serialization stability.** NBT keys and request-system IDs remain stable across versions unless
an explicit migration is provided. Changing a key or an ID is a breaking change and must ship with
backward-compatible reads and a documented rationale. `SafeRequester` (factory 3001) is retained as
a deserialization shim for saves predating its removal; it is not used for new requests.

**No courier injection.** Delivery dispatch goes through the MineColonies warehouse queue only.
The mod does not assign jobs to couriers or maintain a parallel assignment structure, and it does
not decide when a delivery is finished. MineColonies owns the delivery lifecycle from
`DELIVERY_CREATED` onward; the shop reacts to terminal callbacks.

Two earlier violations of this constraint were removed in 0.3.2: a heuristic that called
`finishRequest` on a courier task it judged stuck, and a patch that re-added tokens to the courier's
ongoing-delivery set. Both reported deliveries as complete without any item moving. One path
remains under review, `finalizeOrphanDeliveryChild`, which clears a warehouse queue entry and the
matching courier task when a delivery child has been orphaned; it is scoped to recovery and is
tracked for removal or narrowing before 1.0.

**Parents are closed through MineColonies.** A completed delivery child stays linked to its parent,
so `RequestHandler#onRequestCompleted` calls the resolver's `resolveRequest` once no child is open.
The resolver resolves the parent only when the delivered amount covers the request, the same split
MineColonies uses for crafters. Until 0.3.2 the completion callback detached the child first, which
made MineColonies skip that call. The shop then closed parents from its own tick, and gaps in that
replacement left fully delivered requests open and blocked top-ups after partial deliveries.

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
services rather than a central resolver class, currently around fifty of them. Each service owns one
concern (pending token collection, delivery creation, child reconciliation, etc.) and communicates
through `CreateShopRequestStateMutatorService` for lifecycle writes and
`CreateShopLifecycleStateStore` for runtime state. `BuildingCreateShop` is split the same way into
`Shop*` collaborators. Both splits are local refactors; no external implementations are adapted.

Outstanding amounts are derived from the MineColonies request itself, including what it already
recorded as delivered through `IRequest#getDeliveries()`, rather than from a parallel counter. The
mod keeps no shadow ledger of what the colony believes it has received.

Lost-package recovery (overdue notices, reorder, handover, cancel) is implemented entirely through
MineColonies interaction handlers and standard request-state transitions. The interaction system
uses translatable IDs throughout to avoid locale-dependent response-key mismatches.
