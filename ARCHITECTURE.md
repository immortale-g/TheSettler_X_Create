# Architecture

## Purpose

TheSettler_X_Create extends MineColonies with a Create-based shop building that fulfils colony
requests from Create stock networks and from its own racks. The mod adds the Create Shop building
and its colonist, a request resolver, helper blocks for pickup and output, and the Colony
Gauge/Packager pair for requesting colony goods from the Create side.

## Provenance and API constraints

The module layout and class patterns are constrained by the MineColonies request system and the
Create logistics API. Resolver factories, token-based serialization and logistics summary calls
follow the canonical patterns those APIs expect. Similar structure across mods integrating the same
APIs is therefore expected and does not imply shared code. See [docs/provenance.md](docs/provenance.md).

The mod differentiates itself through Create Shop specific behaviour: the Colony Gauge/Packager
pair, pickup and output block reservation logic, Create network routing, and Structurize placement
handlers for Create blocks the colony builder cannot otherwise place.

## Core concepts

- **Create Shop building** — a MineColonies building with its own request resolver, able to fulfil
  requests from Create stock or from its racks.
- **Pickup and output blocks** — block entities that coordinate deliveries, reservations and the
  packaging of fulfilled requests.
- **Request resolver** — decides whether the shop can satisfy a request, orders from the network,
  and creates the delivery children.
- **Create network facade** — abstracts Create stock network inventory and package requests.
- **Colony Gauge and Colony Packager** — a matched pair, mounted on the container to be refilled.
  They request from the Colony Warehouse the way a Create stock gauge requests from a stock network.
  They are deliberately not interoperable with Create's own gauges and packagers.
- **Inflight tracking** — a mod-side record of what was ordered from the network but has not yet
  arrived, persisted across reloads.

Perma requests (a curated ore-tag request list, gated by building level) are **disabled as of
0.3.0**. `BuildingCreateShop.canUsePermaRequests()` returns `false` and the Gauge/Packager pair
replaces the workflow. The module and its GUI tab still exist but are inert.

## High-level flow

1. MineColonies issues a deliverable request.
2. The Create Shop resolver checks ownership, worker availability, outstanding amount and stock.
3. If it can supply, it orders the shortfall from the network and reserves the incoming goods.
4. When goods are in the racks it creates a MineColonies delivery child and hands off.
5. MineColonies owns the delivery from that point. The shop reacts to terminal callbacks only.
6. Completion consumes the reservation, cancellation releases it. The child stays linked to the
   parent so MineColonies can run its own parent handling.
7. Once the parent has no open child, MineColonies calls `resolveRequest`. Like a crafter, the shop
   resolves the parent only when the delivered amount covers the request. Otherwise the parent
   stays `IN_PROGRESS` and the tick orders and delivers the rest.

Step 5 is a hard boundary, drawn in Phase 3.5 (`e2387bd`). The shop does not finish, cancel or
otherwise steer courier tasks. See the "No courier injection" constraint in
[docs/provenance.md](docs/provenance.md). Steps 6 and 7 are the matching boundary for parents,
restored in 0.3.3: `CreateShopResolverCallbackService#finishIfDelivered` is the only place
that resolves a parent.

## Key modules

| Module | Responsibility |
|---|---|
| `minecolonies.building.BuildingCreateShop` | Building behaviour, resolver setup, gauge requests, blueprint behaviour. Delegates to roughly twenty `Shop*` collaborators. |
| `minecolonies.requestsystem.resolver.CreateShopRequestResolver` | Entry point for resolution and delivery creation. |
| `minecolonies.requestsystem.resolver.*Service` | The resolver is split into single-purpose services: pending token collection, state decision, reservation sync, top-up, delivery creation, child reconciliation, terminal lifecycle, diagnostics ledger. Lifecycle writes go through `CreateShopRequestStateMutatorService`; runtime state lives in `CreateShopLifecycleStateStore`. |
| `minecolonies.requestsystem.requesters.*` | `CreateShopDeliveryRequester` routes delivery callbacks back to the owning resolver. `SafeRequester` is a deserialization shim only. |
| `create.CreateNetworkFacade` | Create network summaries and package requests. |
| `create.compat.CreatePlacementHandlers` | Structurize placement handlers for Create belts and for encased shafts and cogwheels, which have no item of their own. |
| `blockentity.CreateShopBlockEntity` | Reservation and inflight data, item handlers for MineColonies interaction. |
| `block.ColonyGaugeBlock`, `block.ColonyPackagerBlock` | The colony-side request panel pair, built on Create's `FactoryPanelBlock` slot model. |

## Data and state

- Reservations are tracked in `CreateShopBlockEntity`, keyed by request UUID.
- Resolver runtime state is instance-scoped per shop resolver to avoid cross-colony leaks.
- Outstanding amounts are derived from the request, its reservation, and what MineColonies already
  recorded as delivered via `IRequest#getDeliveries()`. Ignoring the delivered part caused the
  repeated re-delivery bug fixed in 0.3.2.
- Cooldowns and debug gates live in `Config`.

## Configuration

`Config` defines cooldowns, the gauge building-level gate, chat message toggles and debug logging
flags. Debug logging is gated through `Config.DEBUG_LOGGING` with per-feature cooldowns, and is on
by default.

## Assets and blueprints

Blueprints ship in `src/main/resources/blueprints/thesettler_x_create/`. Local scans live in the
MineColonies and Structurize scan directories during development.

## Build and distribution

Gradle builds the mod jar and includes resources from `src/main/resources`. MineColonies and Create
are required at runtime on both sides; JEI is an optional client-side integration. Runtime jars used
for compilation and tests live in `libs/`.

## Known integration points

- MineColonies request lifecycle callbacks drive delivery completion and cancellation handling.
- The Create logistics API is used for stock summaries and package request broadcasts.
- Structurize `IPlacementHandler` registration teaches the colony builder about Create blocks.
- MineColonies interaction handlers carry the lost-package and rack-capacity prompts.
