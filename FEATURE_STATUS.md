Project Summary: TheSettler_x_Create (MineColonies + Create integration)

Note: Keep this file updated whenever related functionality changes.

Last reviewed: 2026-02-20

Provenance / Attribution
- This project is developed independently using only public MineColonies and Create APIs.
- Similar resolver/request patterns are expected for mods integrating these APIs and are not evidence of code copying.
- No third-party bridge code is included; integration logic and UI copy are authored for this project.
- If external ideas or references are used in the future, they will be explicitly credited here and in docs/provenance.md.

Overview
This mod adds a custom MineColonies building called "Create Shop" that bridges MineColonies requests with
the Create logistics network. The Create Shop lets a colony request items from Create networks and from
Warehouses, and exposes special pickup/output blocks for automation.

Core concepts
- Create Shop building (MineColonies hut): custom building with Level 1 and Level 2 blueprints.
- Pickup block (CreateShopBlockEntity): represents the pickup endpoint and tracks reservations.
- Output block (CreateShopOutputBlockEntity): emits Create packages for perma-requested items when an output address is set.
- Addresses: Create Shop has separate receive and output addresses, plus an optional output override.
- Perma requests: the shop can auto-request all items from ore tags, gated to building level 2.
- Inflight tracking: Create stock network orders are tracked until items arrive in shop racks; overdue orders
  trigger a shopkeeper notification.
- Lost-package recovery interaction: overdue inflight notifications now offer chat actions to restart
  orders or hand over a package manually.
- Pending delivery tracking: a single source of truth tracks pending counts, cooldowns, and delivery-created
  state per request; reconciliation ticks resume delivery creation even if the shopkeeper was idle.
- Belts: internal belt blueprints are spawned around the shop during placement/upgrade/repair.
- Deliveries: generated only from rack positions (not pickup block) to match MineColonies deliveryman rules.
- Inflight orders that never arrive no longer trigger deliveries; only rack inventory is eligible.
- Manual package handover writes into shop racks first, then hut inventory as overflow buffer.
- Create stock requests are gated by inbound capacity (rack/hut) to avoid ordering items with no landing space.

How it works (high level)
1. Create Shop is placed (level 1) and later upgraded to level 2.
2. The building registers its request resolvers and integrates with MineColonies' request system.
3. Perma requests (level 2 only):
   - The player enables ore items in the Perma module GUI.
   - The shop periodically checks Warehouses for those items.
   - It creates MineColonies requests so couriers deliver to the shop.
4. Output block:
   - Packages the perma-requested items into Create packages.
   - Packages only emit when an output address is configured in the hut GUI.
   - Output override takes priority over the output address if set.

Blueprint pack layout (important)
Structurize/MineColonies requires this layout in the mod JAR:
- `blueprints/<modid>/<packname>/pack.json`
Current pack path:
- `src/main/resources/blueprints/thesettler_x_create/thesettler_x_create/pack.json`
- L1 blueprint:
  `src/main/resources/blueprints/thesettler_x_create/thesettler_x_create/craftsmanship/storage/createshop1.blueprint`
- L2 blueprint:
  `src/main/resources/blueprints/thesettler_x_create/thesettler_x_create/craftsmanship/storage/createshop2.blueprint`

Internal belt blueprints (hidden from players)
These are used by BuildingCreateShop to spawn belts:
- `src/main/resources/assets/thesettler_x_create/blueprints_internal/createshop1_belt.blueprint`
- `src/main/resources/assets/thesettler_x_create/blueprints_internal/createshop2_belt.blueprint`
- `src/main/resources/data/thesettler_x_create/blueprints_internal/createshop1_belt.blueprint`
- `src/main/resources/data/thesettler_x_create/blueprints_internal/createshop2_belt.blueprint`

Key code locations
- Building logic: `src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java`
  - Max level 2
  - Perma request tick loop
  - Belt placement on placement/upgrade/repair
- Perma GUI: `src/main/java/com/thesettler_x_create/minecolonies/client/gui/CreateShopPermaModuleWindow.java`
  - Search filter
  - All On / All Off buttons
- Network payloads:
  - `SetCreateShopPermaOrePayload`
  - `SetCreateShopPermaWaitPayload`
- Debug logging (courier delivery lifecycle and enqueue diagnostics):
  - `src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java`
  - `src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java`
- Lost-package recovery interaction and inflight reconciliation:
  - `src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java`
  - `src/main/java/com/thesettler_x_create/minecolonies/building/ShopInflightTracker.java`
  - `src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java`
- Capacity-aware stock ordering:
  - `src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java`
  - `src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java`

Gameplay constraints
- Perma requests are only active at building level 2.
- Shopkeeper max = 1 at all levels.
- Courier max = 1 at all levels (custom courier module).
- Hut inventory button uses the standard MineColonies hut inventory menu; Create Shop addresses are configured
  via the module tab in the hut GUI (not via the inventory button).
- Delivery creation is gated to a working shopkeeper, but reconciliation continues for pending deliveries.
- Delivery pickup targets racks; pickup block is used for reservations and Create network integration only.
- Lost-package chat interactions remain active until the requested overdue amount is fully satisfied.

Recent fixes (v0.0.11)
- Fixed duplicate resolver token registration during Create Shop placement/build flow (crash fix).
- Fixed unknown resolver token assignment injection by adding only registered resolver tokens to request lists.
- Fixed stale Create Shop resolver assignments that kept requests IN_PROGRESS without creating follow-up deliveries.
- Added stale-assignment reassignment path so existing stuck requests in active worlds can recover and complete.

Current fixes in progress (post-v0.0.11)
- Pending parent requests now drop terminal/missing child delivery links during reconciliation so
  requests do not remain blocked behind completed/cancelled child tokens.
- Create network request stack normalization now consolidates equal stacks before broadcast and
  chunks by the Create package order limit (99) to reduce request fragmentation.
- Pending reconciliation now derives missing pending counts from the live request payload
  (`deliverable count - leftovers - reserved`) so late stock arrivals can resume delivery flow
  without requiring manual request resets.
- Resolver reassignment now treats unresolved assigned resolver tokens as unassigned and reroutes
  those requests instead of leaving them in a formally assigned but dead state.
- Create stock network requests are now queued and grouped per server tick by
  `network + address + requester`, then flushed as consolidated broadcasts to reduce
  multi-request package fragmentation.
- Grouped Create network request flush now re-queues failed broadcasts instead of dropping
  queued stacks, so transient Create/network errors do not lose pending grouped requests.
- Delivery child creation now uses the original MineColonies requester instance (no SafeRequester
  wrapping) so parent-child linkage remains intact and courier deliveries cannot get stuck
  `IN_PROGRESS` due to missing parent links.
- Delivery reassignment fallback now treats long-running `IN_PROGRESS` delivery requests as stale
  with a faster retry cadence, so stuck warehouse delivery assignments are retried instead of
  staying blocked indefinitely.
- Pending reconciliation now clears stale `deliveries-created` markers when no active delivery
  child remains, preventing parent requests from being permanently skipped after child loss or
  terminal child-state cleanup.
- Worker-availability validation now has dedicated regression coverage (defer/resume/keep-pending
  behavior) to prevent network ordering while the shopkeeper is unavailable.
- Resolver callback regression tests now cover delivery-link cleanup and cancel-state cleanup for
  pending/delivery-created markers.
- Resolver cooldown and delivery callback debug logging now use safe guards in headless test
  contexts so unit tests do not fail when NeoForge config is not loaded.

Current refactor branch updates
- Started static-inspection cleanup in `CreateShopResolverInjector` to remove redundant null checks,
  simplify duplicated conditions, and keep request reassignment logic behavior-equivalent while reducing
  warning noise for future reviews.
- Branch `refactor/strict-bridge-state-machine` introduces an explicit Create Shop request
  state-machine (`CreateShopFlowState`, `CreateShopFlowRecord`, `CreateShopRequestStateMachine`)
  and wires transitions to resolver lifecycle hooks (`attemptResolve`, pending tick, delivery
  callbacks, request complete/cancel callbacks).
- State-machine transitions now emit chat-visible flow steps with request-token context:
  ordered, arrived at shop, reserved for pickup, delivery created, delivery completed,
  request completed, cancelled, and timeout reset.
- Timeout cleanup now resets stale resolver-local state (`pending`, cooldown, delivery-created
  markers, reservations) for long-idle flows to prevent blocked/zombie resolver progress.
- Removed legacy global resolver-injector runtime path and SafeRequester factory registration from
  startup/server-tick wiring to keep resolver ownership on normal building provider registration.
- Removed legacy deliverable-assignment mutation hook (`ensureDeliverableAssignment`); resolver
  type assignment now follows native MineColonies resolver registration behavior.
- Added new tests for the state-machine monotonic/timeout behavior and a headless guard that
  enforces no private-field reflection in `CreateShopRequestResolver` manager unwrapping.
- Delivery child creation now hard-links child tokens to their parent request (`parent.addChild`)
  with rollback cancellation if linking fails, preventing orphan delivery children that cannot
  terminate the parent flow.
- Resolver tick-path now syncs to the currently registered provider resolver token (prioritizing
  DELIVERABLE assignments) before processing pending deliveries, preventing assignment drift where
  the shop used a stale local resolver id and saw "no assignments" forever.
- Global debug tick logging is now throttled to a fixed interval to keep logs readable while
  preserving ongoing resolver-flow diagnostics.
- Delivery child linkage now sets both sides (`parent.addChild(child)` and `child.setParent(parent)`)
  so MineColonies `onRequestCompleted` can propagate completion back to the parent request.
- Create Shop no longer provides a custom courier-assignment module; courier handling now uses the
  native MineColonies `WAREHOUSE_COURIERS` module path only.

Known gaps / follow-ups
- Server-side strict gating for `setPermaOre` / `setPermaWaitFullStack` is not enforced; selection can be stored before level 2, though perma requests only tick at level 2.
- UI still lists ores even when locked; it only changes empty text.
- Inflight tracking requires a configured shop address to report a meaningful address in notifications.

TODO (Refactor / Quality Improvements)
- Step 2: Add input validation + safer error handling to blueprint CLI tools (`BlueprintDump`, `BlueprintFixer`, `BlueprintTypeFixer`).
- Step 3: Add unit tests for tools/utilities (at minimum blueprint tools).
- Step 4: Add concise Javadoc for public APIs/classes touched by Create Shop and tools.
