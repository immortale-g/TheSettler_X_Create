# Provenance

TheSettler_X_Create is developed independently using the public MineColonies and Create APIs.
The architecture follows the patterns those APIs require (resolver factories, token serialization,
logistics summaries). Similar structure in other mods that integrate these APIs is expected and does
not imply shared code or shared implementation.

No third-party bridge code is included. All integration logic is authored specifically for this
project, and the feature set is documented in ARCHITECTURE.md and FEATURE_STATUS.md.

If external references, ideas, or sample snippets are used in the future, they will be explicitly
credited here (with source and scope), along with the corresponding implementation notes.

Scope and constraints:
- API-driven integration: request resolution and logistics flows follow MineColonies/Create contracts.
- Compatibility first: serialization keys and IDs remain stable unless a migration is provided.
- Independent implementation: behavior, edge cases, and UX (perma requests, belt placement, pickup/output
  reservation handling) are implemented for this mod.

Implementation notes:
- This codebase intentionally uses MineColonies standard hut windows and modules where possible to
  reduce maintenance and keep UI behavior consistent with upstream.
- Public API usage and platform constraints explain most structural similarity with other integrations.
- UX copy and configuration guidance are written specifically for this mod (for example, in-hut
  address panel descriptions that clarify expected routing behavior).
- Inflight request tracking and shopkeeper notifications are authored for this project using MineColonies
  interaction handlers and Create stock network APIs.
- Delivery creation logic for Create Shop is authored here and intentionally restricted to physical rack
  inventory (not virtual/pickup stock) to avoid false deliveries for inflight items.
- Ongoing refactors (resolver module splits and upcoming stock/validator/delivery extracts) are purely
  internal reorganizations of this codebase; no external implementations or third-party bridge code are
  incorporated or adapted.
- The pending delivery tracker and reconciliation tick are authored for this project to improve
  consistency when multiple Create deliveries arrive close together or when the shopkeeper is idle.
- Lost-package recovery interactions (shopkeeper chat actions, manual package handover, restart order
  flow), inflight consumption semantics, and storage-capacity request gating are authored specifically
  for this project using MineColonies interaction handlers and Create logistics APIs.
- Resolver lifecycle hardening in v0.0.11 is authored specifically for this project:
  duplicate resolver registration prevention, registered-token-only assignment injection, and stale
  Create Shop resolver reassignment for recovering stuck IN_PROGRESS requests after resolver/token
  churn in existing saves.
- Ongoing static-inspection cleanup (warning reduction and readability refactors) is authored in this
  codebase and limited to behavior-preserving changes in existing Create Shop request-system logic.
- Headless regression coverage for resolver reassignment edge cases (stale vs. active Create Shop
  resolver assignments) is authored in this project as local JUnit/Mockito tests.
- Additional headless regression coverage for delivery-resolver disable/enable behavior, reassignment
  cooldown gating, child-chain reassignment, and burst-arrival request handling is authored in this
  project as local JUnit/Mockito tests for the Create Shop resolver injector flow.
- Delivery self-loop prevention (guarding against pickup==target routing such as Postbox->Postbox) and
  accompanying headless unit tests are authored in this project within Create Shop delivery management.
- Parent-request reconciliation now removes terminal/missing child delivery links before continuing
  pending processing, and Create network request stacks are consolidated/chunked to the package limit;
  both behaviors are authored in this project for Create Shop request-flow stability.
- Pending reconciliation now derives missing pending counts from active request payload state
  (including non-exhaustive leftovers and existing reservations) to recover late-arrival routing
  without external resets; this behavior is authored in this project.
- Resolver reassignment now explicitly handles stale assignment tokens whose resolver can no longer
  be resolved, rerouting those requests instead of preserving dead formal assignments.
- Create stock ordering now includes per-tick grouped broadcast batching (keyed by network/address/
  requester) to reduce fragmented package emission under concurrent request bursts; this behavior is
  authored in this project.
- Grouped broadcast flushing now retries failed Create-network broadcasts by re-queuing the failed
  grouped bucket for the next tick; this reliability behavior is authored in this project.
- Debug-log gating hardening for headless/test execution (safe fallback when NeoForge config is not
  loaded yet) is authored in this codebase and does not depend on external bridge implementations.
- Additional headless-safe debug-log gating in Create Shop resolver callback/cooldown paths is
  authored in this project so callback regression tests can run without requiring NeoForge config
  bootstrap in the unit-test JVM.
- Delivery child-request creation now preserves MineColonies parent linkage by using the original
  requester instance (without SafeRequester wrapping) in Create Shop delivery creation; this fix is
  authored in this project for resolver-chain stability in live worlds.
- Delivery reassignment and pending reconciliation hardening in v0.0.12 is authored in this project:
  stale `IN_PROGRESS` delivery requests are retried on a shorter cadence, and stale
  `deliveries-created` parent markers are cleared when no active child remains to prevent
  long-lived blocked parent requests in existing saves.
- Worker-availability guard validation tests and resolver callback cleanup regression tests are
  authored in this project to reduce repeat regressions around pending-delivery stalls and
  cancellation/cleanup state handling.
- Strict branch state-machine refactor (branch `refactor/strict-bridge-state-machine`, 2026-02-21)
  is authored in this project: Create Shop request-flow tracking is now explicit and monotonic via
  `CreateShopFlowState` + `CreateShopFlowRecord` + `CreateShopRequestStateMachine`, with lifecycle
  transitions wired to resolver attempt/tick/callback paths and timeout-based stale-flow cleanup.
- Flow-step chat prompts (ordered, arrived, reserved, delivery-created, delivery-completed,
  request-completed, cancelled, timeout reset) are authored in this project to make resolver flow
  observable in live testing without external tooling.
- Legacy global resolver injection (`CreateShopResolverInjector`) and SafeRequester factory runtime
  registration were removed on the strict branch; resolver/provider ownership now relies on
  standard MineColonies building-provider registration paths in this codebase.
- Legacy manual requestable-type assignment mutation (`ensureDeliverableAssignment`) was removed in
  favor of MineColonies-native resolver registration assignment behavior.
- Headless tests introduced on the strict branch (state-machine monotonic/timeout behavior and
  no-private-reflection guard for resolver manager unwrapping) are authored in this project.
- Parent-child link hardening on the strict branch is authored in this project: delivery child
  requests are now explicitly linked via `request.addChild(token)` and rolled back via
  `updateRequestState(..., CANCELLED)` if link creation fails, reducing orphan-child risk in
  parent completion flow.
- Resolver assignment-drift hardening on the strict branch is authored in this project: the shop
  now synchronizes to the active MineColonies-registered resolver token for its provider before
  pending tick processing, and triggers provider repair only when registration/type membership is
  inconsistent.
- Parent/child delivery-chain completion fix on the strict branch is authored in this project:
  created delivery requests now set both parent-child edges (`addChild` + `setParent`) so
  MineColonies parent completion callbacks can fire normally after child completion.
- Custom Create Shop courier-module removal on the strict branch is authored in this project:
  Create Shop now uses MineColonies native warehouse courier module wiring instead of a mod-specific
  courier-assignment implementation.
- Resolver drift repair hardening on the strict branch is authored in this project: the resolver
  factory no longer reuses stale cached shop-resolver instances across request-system rebuilds, and
  tick resolver selection can recover via assignment-backed Create Shop resolver discovery.
- Delivery child requester binding on the strict branch is authored in this project: Create Shop
  delivery children are now created with the Create Shop resolver as requester (aligned to
  MineColonies warehouse-resolver pattern) instead of the parent requester, reducing resolver-chain
  drift and stuck `IN_PROGRESS` child risk.
- Tick-pending cancellation logging hotfix on the strict branch is authored in this project: a
  logger call in Create Shop pending processing was corrected to avoid a runtime
  `ClassCastException` (`StandardToken` being routed to a throwable overload), preventing colony
  tick interruption during cancelled-request handling.
- Parent-child duplication guard on the strict branch is authored in this project: Create Shop
  pending processing now removes duplicate child-token entries from parent requests, and delivery
  child-link creation deduplicates repeated same-token parent links to avoid multi-processing of a
  single delivery child.
- Resolver assignment-drift fallback improvement on the strict branch is authored in this project:
  tick resolver selection now prefers an assignment-backed Create Shop resolver when the
  provider-prioritized resolver has no assigned requests, reducing no-assignment stalls after
  request-system token churn.
- Delivery link idempotency hardening on the strict branch is authored in this project: Create
  Shop delivery child linking now skips `addChild` when MineColonies flow already linked the token
  on the parent, preventing repeated same-token child duplication in wrapped resolver flows.
- Wrapped-manager delivery fallback support on the strict branch is authored in this project:
  Create Shop delivery fallback now handles `WrappedBlacklistAssignmentRequestManager` paths by
  using generic request-manager assignment checks and queue enqueue fallback, preventing
  `notified=0` delivery stalls when standard-manager unwrapping is unavailable.
- Wrapped-manager delivery creation deferral on the strict branch is authored in this project:
  `attemptResolve` now defers rack-based delivery-child creation when running under wrapped request
  managers and leaves child creation to `tickPending` under the standard manager, avoiding
  unregistered-child tokens and subsequent cancel/reorder loops.
- Queue-only courier dispatch hardening on the strict branch is authored in this project: Create
  Shop delivery dispatch no longer injects requests directly into deliveryman jobs and now relies
  on MineColonies warehouse queue dispatch only, with queue deduplication to avoid repeated
  token insertion.
- Resolver followup behavior alignment on the strict branch is authored in this project:
  Create Shop resolver followup completion now returns `null` (no explicit followups), matching
  MineColonies delivery-resolver semantics while avoiding unsafe warehouse-tile casts.
- Tick-pending assignment drift recovery on the strict branch is authored in this project:
  when the active Create Shop resolver token has no direct assignment entry, pending processing now
  recovers assignments from local Create Shop resolver tokens in the manager assignment map, so
  partial-delivery continuations do not stall on resolver-id drift.
- Partial-delivery network top-up on the strict branch is authored in this project: pending
  processing now issues an idempotent Create-network top-up request for outstanding deficits
  (`pendingCount - reservedForRequest`) as soon as stock becomes available, and reserves pulled
  items to prevent duplicate reordering while delivery children are still in flight.
- Assignment ownership fallback on the strict branch is authored in this project: pending
  processing now performs a second recovery pass that inspects assignment tokens by request
  ownership (`getResolverForRequest`) and only keeps tokens owned by a local Create Shop resolver,
  so request handling can continue when resolver-id keyed assignment maps drift.
- Worker-gated ordering split on the strict branch is authored in this project: Create-network
  ordering/top-up remains gated by `isWorkerWorking()`, while rack-only delivery-child creation
  can continue during temporary worker idle to avoid blocking already-arrived items; daytime
  worker-status fallback was added to reduce false idle gating caused by transient AI metadata
  drift.
- MineColonies-style shopkeeper worker-state alignment on the strict branch is authored in this
  project: Create Shop AI now updates `JobStatus`/`VisibleStatus` during working vs idle states,
  worker gating prefers `JobStatus.WORKING` over deliveryman-specific `isWorking()` flags, and
  resolver-active workload signaling is exposed so daytime shop work does not idle while resolver
  tasks are pending.
- Resolver-token drift recovery on the strict branch is authored in this project: Create Shop
  resolver selection now includes ownership-based fallback (`getResolverForRequest`) when
  assignment-key/token maps are out-of-sync, allowing pending local requests to continue without
  cancel/recreate churn.
- Dynamic request-owner pending processing on the strict branch is authored in this project:
  `tickPending` now prioritizes per-request owner resolution (`getResolverForRequest`) over
  resolver-token assignment keys, reducing repeated stalls when resolver IDs drift while requests
  remain valid in MineColonies.
- Delivery callback routing on the strict branch is authored in this project: child-delivery
  callback resolution no longer depends on stored resolver tokens and instead resolves through
  child->parent linkage plus MineColonies owner lookup for the parent request.
- Live resolver-health synchronization on the strict branch is authored in this project:
  `BuildingCreateShop` now resolves local Create Shop resolvers from manager provider/assignment/
  ownership data before health-repair decisions, reducing stale cached resolver-token influence.
- Remaining delivery-parent token cache removal on the strict branch is authored in this project:
  delivery callback parent lookup now uses live request linkage (`request.getParent`) with dynamic
  parent discovery from current assignments as fallback, and pending tick drops tokens no longer
  owned by the local Create Shop resolver.
- Uninstall-preparation maintenance command on the strict branch is authored in this project:
  `/thesettlerxcreate prepare_uninstall` performs best-effort Create Shop provider unregistration
  and cancellation of active Create Shop-owned requests via MineColonies APIs before jar removal.
- Retrying-owner handoff for late stock availability on the strict branch is authored in this
  project: when a request is owned by MineColonies `StandardRetryingRequestResolver` and Create
  Shop `canResolve` flips to true (e.g. stock added later), the request is re-assigned via
  `reassignRequest` to avoid waiting indefinitely on native retry cadence.
- Retrying-owner handoff iteration hardening on the strict branch is authored in this project:
  reassignment scanning now snapshots assignment entries/tokens before mutation and limits to one
  successful reassignment per tick, preventing `ConcurrentModificationException` during colony tick
  and reducing assignment churn after late stock updates.
- PR-scope boundary note on the strict branch is authored in this project: Create network
  extraction implementation in `CreateNetworkFacade.extract(...)` remains intentionally out of
  scope for this PR and is tracked as a local follow-up TODO, while current resolver flow continues
  to use existing network-order pathways.
- Hotfix 0.0.13 legacy-save compatibility support is authored in this project: requester factory
  `3001` (`SafeRequesterFactory`) and wrapper type (`SafeRequester`) were reintroduced as a
  compatibility-only deserialization path for worlds created before removal, while current request
  flow keeps native requester usage and does not re-enable legacy wrapping for new requests.
- Inbound capacity gating hardening is authored in this project: Create-network order normalization
  now uses `TileEntityCreateShop.planInboundAcceptedStacks(...)` virtual slot simulation across
  rack + hut handlers, clamps requested counts to actually insertable amounts for mixed stacks, and
  logs skipped/clamped requests instead of relying on one-item probe checks.
- Pending reorder timing hardening is authored in this project: `tickPending` no longer clears
  the delivery-created marker preemptively and now blocks network top-up while active delivery work
  exists for the parent request, preventing premature duplicate ordering before courier pickup.
- Capacity-stall player feedback hardening is authored in this project: Create Shop now records
  rack/hut capacity stalls during network order normalization, marks the shopkeeper as
  `JobStatus.STUCK` while stalled, and raises a rate-limited citizen interaction with actionable
  guidance (upgrade hut/rack capacity or assign more couriers) instead of falling back to player
  resolver for stock that is still available in the Create network.
- Capacity planner source-of-truth hardening is authored in this project: inbound order planning
  now evaluates rack-container capacity only (no hut inventory fallback), so blocked entrance/rack
  states are not hidden by hut buffer space and top-up orders are not re-triggered from non-rack
  storage headroom.
- Stock module data-source alignment is authored in this project: Create Shop stock module view
  serialization now publishes aggregated contents from registered local storage containers
  (rack and other item-handler containers registered to the shop) instead of querying Create network
  summary directly, so the in-game stock tab reflects actual shop-side storage state.
- Stock-tab simplification and native pickup alignment are authored in this project: the Create Shop
  stock module window is read-only again (registered storage view only, no custom request/order
  actions), while forced pickup behavior stays on the MineColonies-native worker-module action
  path (`forcePickup`) used by other huts.
- Planned task-tab observability extension is authored for this project scope: the Create Shop task
  UI will expose inflight package progress and shop-triggered MineColonies pickup/delivery request
  references as read-only diagnostics, without introducing client-authoritative request state.
- Stale delivery-child recovery hardening is authored in this project scope: Create Shop resolver
  tracks active delivery-child runtime and applies timeout-based API cancellation + parent cleanup
  for long-running non-terminal children (`CREATED/ASSIGNED/IN_PROGRESS`) to unblock parent
  progression without modifying MineColonies internals.
- Delivery dispatch diagnostics/accounting hardening is authored in this project scope: courier
  module notification scans now increment `notified` counters alongside `checked`, preventing
  false `notified=0` diagnostics during active dispatch attempts.
- Pending top-up coverage hardening is authored in this project scope: Create Shop `tickPending`
  now subtracts rack-available stock from top-up deficit calculation before issuing Create network
  orders, preventing duplicate network reorders when enough items are already in shop racks.
- Reservation-refresh hardening is authored in this project scope: `tickPending` now reconstructs
  missing per-request reservations from currently available rack stock after request refresh,
  reducing post-reload reservation drift and avoiding premature unreserved treatment of pending
  request goods.
- Incoming-rack housekeeping is authored in this project scope: Create Shop now performs timed
  rack->hut transfers for unreserved items only, preserving reserved quantities for MineColonies
  delivery flow while keeping local storage cleanup server-authoritative and API-driven.
- Shopkeeper urgent-work AI gating is authored in this project scope: non-daytime idle transition
  is blocked while resolver pending work or incoming-rack housekeeping work exists, so request and
  cleanup progression continues without MineColonies internals changes.
- Incoming-rack housekeeping availability gating is authored in this project scope: transfer work
  now requires an assigned Create Shop worker that is not in MineColonies unavailable statuses
  (sleep/eat/sick/mourning/raided and related unavailable states), preventing background moves
  while the worker is unavailable.
- Housekeeping duplication fix is authored in this project scope: rack->hut transfer now computes
  insertable counts with `IItemHandler.insertItem(..., true)` simulation before extracting from
  racks, removing prior pre-extract probe insertion that could duplicate moved stacks.
- Housekeeping rack-discovery recovery is authored in this project scope: before transfer, shop
  racks are re-synchronized from registered containers and, when that set is empty (post-reload
  drift), a bounded local rack scan is used so unreserved transfer work still executes.
- Housekeeping cadence catch-up is authored in this project scope: per-run transfer budget now
  scales by elapsed server ticks since the previous run (bounded cap), compensating for coarse
  MineColonies building tick cadence while preserving the intended average stack transfer rate.
- Housekeeping live-diagnostics instrumentation is authored in this project scope: rate-limited
  debug logs now expose worker gate reasons, cooldown waits, rack discovery counts, unreserved
  transfer budget, and moved stack counts for live-world verification without client-side hacks.
- Housekeeping transfer target reliability hardening is authored in this project scope: rack->hut
  transfer now prioritizes hut-internal inventory as destination and rack capability handlers for
  extraction, reducing false-positive move accounting on aggregate handlers and improving visible
  in-hut transfer behavior.
- Lost-package interaction compatibility hardening is authored in this project scope: interaction
  validator/response/answer components were migrated to translatable keys (with localized lang
  entries) so MineColonies interaction response handling does not crash on literal-content casts.
- Lost-package recovery consistency hardening is authored in this project scope: replacement-order
  actions are now one-shot independent of strict inflight tuple consumption, package handover uses
  rack-only insertion of unpacked contents, and inflight consumption includes a stack-key fallback
  when requester/address fields drift across reloads or naming changes.
- Lost-package interaction lifecycle hardening is authored in this project scope: interaction IDs
  now use a stable translatable ID, successful reorder/handover actions deterministically
  invalidate the active dialog, and additional server-side debug logging was added for end-to-end
  handover tracing (button response, inventory scan, package unpack, rack insert, inflight
  consumption).
- Create Shop chat-noise reduction is authored in this project scope: detailed flow-step chat is
  now controlled by a dedicated config flag (`flowChatMessagesEnabled`, default `false`) with
  same-tick dedupe, while player-facing status chat was reduced to one concise line per stage.
- Interaction-ID compatibility hardening is authored in this project scope: Create Shop
  `ShopCapacityStallInteraction` and `ShopLostPackageInteraction` now use translatable ID
  components (stable for lost-package and capacity-stall) instead of literal ID components,
  preventing MineColonies client response handling from hitting literal/translatable
  content-cast mismatches on interaction button handling.
- Lost-package response-routing locale hardening is authored in this project scope:
  `com.thesettler_x_create.interaction.createshop.lost_package.id` now resolves to the same
  machine token across language files (`createshop_lost_package`) to avoid locale-dependent
  interaction-id mismatches between client and server.
- Diagnostics private-reflection cleanup is authored in this project scope:
  `ShopCourierDiagnostics` removed private-field fallback mutation (`setAccessible`/declared-field
  entityId writes) and now remains on public/API repair paths only (`updateEntityIfNecessary`,
  `setEntity`, citizen manager APIs).
- Delivery requester resolver-selection hardening is authored in this project scope:
  `CreateShopDeliveryManager` replaced resolver simple-classname string matching with type-safe
  checks (`AbstractWarehouseRequestResolver` and `IRequester`, excluding
  `CreateShopRequestResolver`) for warehouse-delivery requester binding.
- Warehouse-count null-safety hardening is authored in this project scope:
  `CreateShopRequestResolver.getWarehouseInternalCount(...)` now fail-opens with `0` when request,
  deliverable, colony manager, colony, or building manager context is missing.
- Shop-courier module decoupling hardening is authored in this project scope:
  Create Shop building registration no longer adds `BuildingModules.WAREHOUSE_COURIERS`,
  warehouse access for delivery is role-based (`JobDeliveryman`), and delivery notify/dispatch
  paths stay warehouse-queue-native without shop-courier module dependence.
- Legacy courier-module load migration is authored in this project scope:
  `BuildingCreateShop` performs a best-effort, one-time cleanup of assigned citizens/entities if a
  legacy shop courier module instance is still present in existing saves, while keeping fail-open
  behavior when that module is absent.
- Lost-package partial-response lifecycle hardening is authored in this project scope:
  reorder/handover handlers now return consumed inflight quantity, interaction state accumulates
  consumed amounts across retries, and dialog closure occurs only when accumulated consumption
  clears the overdue target.
- Partial inflight consumption recovery hardening is authored in this project scope:
  unresolved entry remainders reset `notified` on partial consume, allowing overdue scanning to
  surface unresolved amounts again instead of leaving them stranded after first notification.
- Delivery-child mutation scope hardening is authored in this project scope:
  stale-recovery/callback mutation paths in `CreateShopRequestResolver` are constrained to local
  Create Shop delivery children by pickup/start location checks, while unresolved/non-local child
  lookups remain fail-open (no forced cancel/remove mutation).
- Parent-scoped stale-clock hardening is authored in this project scope:
  stale child timeout tracking now keys on parent request identity (`parentDeliveryActiveSince`)
  with child tokens as transient observations, reducing drift from child token refresh/rotation.
- Two-phase stale-recovery hardening is authored in this project scope:
  stale local delivery children are first marked for recheck (`parentStaleRecoveryArmedAt`) and
  only mutated (cancel/remove/requeue) if still stale after a short recheck window, reducing
  one-tick false-positive mutation against transient MineColonies assignment/state jitter.
- Recovery ownership revalidation hardening is authored in this project scope:
  stale/extra delivery-child recovery rechecks `getResolverForRequest(parent)` immediately before
  mutation and skips mutation when ownership drifted away from the local Create Shop resolver.
- Lost-package prompt dedupe hardening is authored in this project scope:
  overdue inflight scanning now emits at most one lost-package interaction per tick (oldest first)
  and prompt dedupe uses `(item, address)` to avoid duplicate prompts from requester-name drift.
- Lost-package inflight history cleanup is authored in this project scope:
  load/record-time compaction removes exact duplicate segment entries and caps retained open
  segments per `(item, requester, address)` tuple to prevent legacy duplicate prompt floods while
  keeping partial-package recovery semantics.
- Lost-package handover matching hardening is authored in this project scope:
  package-content matching now accepts same-item fallback when full component equality drifts,
  improving manual handover recovery robustness without bypassing server-side inflight consumption.
- Lost-package handover amount-limiting hardening is authored in this project scope:
  one handover action now iterates multiple matching player packages as needed but limits inflight
  consumption to the interaction target (`remaining`) so recovery does not over-consume requests.
- Lost-package reload-order hardening is authored in this project scope:
  inflight tuple consumption now supports same-item fallback for component drift, and restart
  reorder requests are clamped to currently tracked inflight remainder for the tuple, reducing
  duplicate/stacking reorders after world save reload cycles.
- Lost-package reload-prompt hardening is authored in this project scope:
  persisted inflight entries now reload with prompt state re-armed (`notified=false`) when still
  unresolved, because MineColonies interactions are not reliably restored from prior runtime state.
- Diagnostics decoupling hardening is authored in this project scope:
  `ShopCourierDiagnostics` no longer executes courier-module assignment comparison paths tied to
  `CourierAssignmentModule`, aligning diagnostics with shop-courier module removal.
