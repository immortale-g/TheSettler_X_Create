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
- Shopkeeper AI now marks MineColonies-native worker signals (`JobStatus`/`VisibleStatus`) and
  keeps daytime work active while resolver work exists.
- Resolver selection now includes ownership-based drift recovery, so active local Create Shop
  requests can re-bind to the resolver that MineColonies currently considers owner.
- Pending processing is now request-owner dynamic: assignment ownership (`getResolverForRequest`)
  is used as primary source each tick, with resolver-token key maps only as fallback hints.
- Delivery callback resolution is now parent-request based (child -> parent -> owner lookup),
  removing resolver-token map coupling from delivery callback routing.
- Building resolver health/sync now resolves local Create Shop resolver instances live from
  provider/assignment/owner data before considering provider repair, reducing cached-token bias.
- Delivery parent resolution is now live-only (request parent or dynamic parent scan via current
  assignments), and pending processing drops requests no longer owned by the local resolver.
- Added admin maintenance command `/thesettlerxcreate prepare_uninstall` to scrub Create Shop
  provider bindings and cancel active Create Shop-owned requests before removing the mod jar.
- Tool/Deliverable requests currently owned by MineColonies `StandardRetryingRequestResolver`
  are now opportunistically reassigned to the Create Shop resolver once `canResolve` becomes true.
- Retrying-owner reassignment now iterates assignment snapshots and performs at most one reassignment
  per tick, preventing `ConcurrentModificationException` and reducing assignment churn/drift.
- Legacy requester factory compatibility (`serialization id 3001`) is restored for load-time
  backward compatibility with pre-removal saves; new request flow still does not create SafeRequester
  wrappers.
- Create-network ordering now uses a virtual rack/hut slot-capacity simulation before broadcast,
  clamping requested amounts to actually insertable counts and preventing over-ordering when free
  slots for mixed item types are exhausted.
- Pending top-up ordering is now blocked while delivery children are still in progress for the
  same parent request, preventing premature reorders before couriers pick up shop-arrived items.
- Capacity stalls now surface as shopkeeper `STUCK` state and a rate-limited citizen interaction
  with guidance to expand rack capacity (hut upgrade) or assign more couriers.
- Inbound order capacity planning is rack-only (shop rack containers), so hut-internal buffer space
  no longer masks rack jams or triggers premature reorder while entrance packages are blocked.
- Create Shop stock tab now renders contents from registered shop storage containers
  (rack/vault-capable handlers) instead of direct Create-network summary, matching actual local
  pickup inventory context.
- Create Shop stock UI is read-only again (no in-tab order/request actions), showing registered
  shop storage state only.
- `Pickup now` remains MineColonies-native via the worker-module actions flow (`forcePickup`)
  instead of custom stock-tab controls.
- Lost-package interaction responses now use translatable components (including validator/response
  and answer texts) to stay compatible with MineColonies interaction client handling and avoid
  `TranslatableContents` cast crashes on response button clicks.
- Capacity-stall and lost-package interaction IDs use translatable components, while lost-package
  inquiry text now uses a deterministic literal payload to keep MineColonies response lookup stable
  on button clicks.
- Shop courier diagnostics no longer attempts private-field entityId mutation fallback
  (`setAccessible`/declared-field write) and stays on API/public-method paths only.
- Delivery requester selection now uses type-safe resolver checks
  (`AbstractWarehouseRequestResolver` excluding `CreateShopRequestResolver`) instead of fragile
  resolver-classname string matching.
- Warehouse-internal stock count path now fail-opens with `0` for missing request/location/colony
  context, preventing null-dereference failures during resolver suitability/count checks.
- Create Shop building registration no longer adds `BuildingModules.WAREHOUSE_COURIERS`; access and
  dispatch remain warehouse-native (`JobDeliveryman` access check + warehouse queue notify path).
- Legacy-save compatibility for prior shop-courier data is handled best-effort during colony tick:
  if a legacy shop courier module is still present, assigned citizens/entities are cleared once and
  normal warehouse-native dispatch continues.
- Lost-package responses now accumulate consumed inflight amounts across repeated button clicks:
  reorder/handover handlers return consumed quantities, interaction `remaining` is reduced per
  attempt, and dialog closes only once accumulated consumption clears the overdue target.
- Remaining overdue entries stay re-promptable after partial inflight consumption
  (`notified` reset on partial consume), so partial success cannot strand unresolved remainder.
- Stale delivery-child recovery and delivery callbacks are now local-shop scoped: mutation paths
  (cancel/remove/requeue/release) run only for delivery requests whose pickup/start location
  matches the local Create Shop pickup block; non-local or unresolved child lookups stay fail-open.
- Stale-child timeout tracking is now parent-scoped (`parentDeliveryActiveSince`) instead of relying
  on child-token-only clocks, reducing false reset/drift when child tokens refresh.
- Stale delivery-child mutation is now two-phase: first stale observation only arms/schedules
  recheck (`parentStaleRecoveryArmedAt`), and cancel/remove/requeue mutation executes only when
  stale persists after the recheck window, reducing one-tick false-positive churn.
- Stale/extra-child recovery revalidates live MineColonies ownership before mutation; cancel/remove/
  requeue only runs when the parent request is still owned by the local Create Shop resolver.
- Delivery-child locality now accepts both pickup-block starts and registered shop container/rack
  starts, so rack-sourced native MineColonies deliveries are no longer misclassified as foreign.
- Parent pending reconciliation now removes terminal delivery children before locality gating, so
  completed/cancelled child tokens cannot keep parent requests stuck in `IN_PROGRESS`.
- Lost-package recovery flow is now one-shot and rack-oriented: `Reorder` no longer stays blocked
  by strict inflight tuple cleanup, `Handover` inserts unpacked contents into rack flow (no hut
  fallback), and inflight cleanup now has a stack-key fallback when requester/address text drifts.
- Lost-package interaction identity is now stable per overdue segment
  (`item + address + remaining + requestedAt`) instead of inquiry text, preventing duplicate
  blocking dialogs when requester label text drifts.
- Overdue inflight prompting is now single-interaction per tick (oldest overdue first) and uses a
  prompt key based on `(item, address)` to avoid duplicate dialogs caused by requester-name drift.
- Inflight load/record cleanup now removes exact duplicate lost-package segments and caps open
  segment history per `(item, requester, address)` tuple to avoid legacy prompt floods after
  repeated reloads or previously stuck handover loops.
- Lost-package handover matching now falls back to same-item matching when item components drift,
  so package contents can still be accepted/recovered after component-text/metadata skew.
- Lost-package handover now processes multiple matching player packages in one action and caps
  inflight consumption to the overdue target amount (`remaining`), preventing unnecessary extra
  package removal beyond the required recovery amount.
- Lost-package interaction now includes a third action (`Cancel request`) that cancels matching
  local Create Shop-owned requests via MineColonies request-state updates and clears matching
  inflight tuple tracking, enabling clean native re-request from colony side when recovery is
  intentionally aborted.
- Lost-package reorder failures caused by insufficient stock-network availability now open a
  dedicated one-button info interaction (`Back`) and then return to the lost-package options,
  instead of silently closing without player-visible feedback.
- Lost-package handover now pre-checks preview-accepted package content against remaining inflight
  before removing a player package, and stops further package removals after a consume-miss to
  avoid multi-package loss when tuple consumption fails.
- Lost-package inflight consumption now falls back to same-item matching for component drift, and
  restart reorder volume is bounded by currently tracked inflight remainder for the tuple to avoid
  duplicate over-ordering after world reloads.
- Lost-package interactions now carry a shop-local runtime epoch that is bumped during
  live-test reset and world-load cleanup so stale dialogs cannot re-arm after tuple state was
  already consumed/cleared in a newer runtime epoch.
- Resolver attempt-ordering flow no longer reaches into resolver passthrough helpers for delivery
  creation; the attempt service now receives `CreateShopDeliveryManager` directly via constructor
  injection, reducing resolver-as-god-object coupling while keeping behavior unchanged.
  `/thesettlerxcreate reset_live_state`, so stale pre-reset dialogs cannot mutate new post-reset
  runtime state.
- Lost-package root-request cancellation matching (item/requester/address/request scope) is now
  isolated in a dedicated helper (`ShopLostPackageRequestCanceller`) to keep
  `BuildingCreateShop` lifecycle orchestration smaller without changing cancel semantics.
- Pending network-topup decision logic during resolver reconciliation is now centralized in
  `CreateShopPendingTopupService`, reducing resolver tick complexity while preserving current
  topup guards (`started-order`, `wait-inflight`, `network-topup`).
- Resolver assignment-drift/ownership locality checks are now centralized in
  `CreateShopResolverOwnership`, reducing duplicated local-owner detection logic in
  `CreateShopRequestResolver`.
- Pending rack-delivery creation planning (wait/plan/create-failed/create-success) is now
  centralized in `CreateShopPendingDeliveryCreationService`, further reducing
  `CreateShopRequestResolver.tickPendingDeliveries` complexity.
- Delivery callback resolver/parent-token lookup and unresolved-callback diagnostics are now
  centralized in `CreateShopDeliveryResolverLocator`, reducing static callback lookup complexity
  in `CreateShopRequestResolver`.
- Terminal request cleanup paths now share a unified resolver cleanup routine
  (`cleanupTerminalRequest`), reducing duplicated cancel/complete cleanup branches.
- Reservation-release and cancelled-request lost-package inflight cleanup are now centralized in
  `CreateShopReservationReleaseService`, reducing resolver responsibility and keeping terminal
  cleanup wiring in one shared service.
- Warehouse internal stock-count bridging to MineColonies is now centralized in
  `CreateShopWarehouseCountService`, reducing resolver-side null/context guard sprawl while keeping
  the same fail-open semantics.
- Flow-timeout cleanup (`collectTimedOut` + failed transition + reservation/cooldown/pending
  cleanup) is now centralized in `CreateShopFlowTimeoutCleanupService`, reducing resolver tick
  orchestration responsibility while preserving existing timeout behavior.
- Delivery-completion reconciliation (parent-flow transition, local pickup reservation consumption,
  pending/cooldown reconciliation, and completion diagnostics/recheck) is now centralized in
  `CreateShopDeliveryCompletionService`, reducing callback complexity in
  `CreateShopRequestResolver`.
- Retrying-request reassignment orchestration is now centralized in
  `CreateShopRetryingReassignService`, reducing resolver tick-branching while preserving guarded
  reassignment to Create Shop when requests become resolvable.
- Assignment/ownership pending-token collection is now centralized in
  `CreateShopPendingTokenCollectorService`, reducing `tickPendingDeliveries` setup complexity and
  keeping resolver ownership-recovery behavior in a dedicated pre-processing step.
- Cancelled-pending cleanup now avoids duplicate cooldown-clear calls on the same token in
  `tickPendingDeliveries`, reducing redundant state-mutation noise during cancellation cleanup.
- Pending pre-gates for ownership/cancel/not-deliverable are now centralized in
  `CreateShopPendingRequestGateService`, and `state == CANCELLED` now clears pending/cooldown/
  delivery-created tracking immediately in tick-pending processing to prevent cancelled-token
  tracker drift.
- Tick-pending candidate debug logging is now isolated in a dedicated helper method, reducing
  top-level `tickPendingDeliveries` control-flow noise while keeping existing debug output.
- Parent-child reconciliation for pending parents (duplicate child removal, fail-open missing-child
  grace, stale/extra-active local-delivery recovery, and local-scope child diagnostics) is now
  centralized in `CreateShopChildReconciliationService`, reducing `tickPendingDeliveries`
  branching and isolating child-lifecycle handling into one dedicated flow.
- Tick-pending now actively recovers stale cooldown-only parents (`ordered/cooldown` set but
  no pending count, no active child delivery, no children) by clearing stale cooldown/pending
  tracking instead of leaving requests silently blocked in no-progress loops.
- Pending-count normalization and worker-availability gating decisions are now centralized in
  `CreateShopPendingStateDecisionService`, reducing `tickPendingDeliveries` branching and keeping
  derived-pending, stale-cooldown recovery, and worker-unavailable pending retention in one flow.
- Post-delivery-creation updates (flow transitions, player chat, pending/cooldown update after
  created child deliveries) are now centralized in `CreateShopPostCreationUpdateService`, and
  remaining-count values are normalized to non-negative before state updates.
- Delivery-cancel callback reconciliation is now centralized in
  `CreateShopDeliveryCancelService` (parent requeue/pending source/cooldown + missing-pickup
  fallback + diagnostics/recheck). Missing-pickup fallback now safely handles null delivery start
  locations before block lookup.
- Root-cause delivery snapshot diagnostics are now centralized in
  `CreateShopDeliveryRootCauseSnapshotService`, reducing resolver debug/forensics density while
  preserving rate-limited warehouse/courier assignment snapshot emission.
- Delivery-child recovery mutation path (ownership revalidation, local child scope check,
  cancel/remove/requeue mutation, and recheck scheduling) is now centralized in
  `CreateShopDeliveryChildRecoveryService`, reducing resolver-side stale/extra-active recovery
  branch density.
- Per-request pending lifecycle processing is now centralized in
  `CreateShopPendingRequestProcessorService`, so `tickPendingDeliveries` mainly orchestrates
  context setup and token iteration.
- Rack reservation refresh reconciliation is now centralized in
  `CreateShopReservationSyncService`, isolating reservation-refresh semantics and reducing
  reservation drift fixes spread across resolver code paths.
- Tick-pending top-level orchestration (manager/level guards, assignment snapshot collection,
  token-loop dispatch, timeout processing, and perf logging handoff) is now centralized in
  `CreateShopTickPendingService`, reducing `CreateShopRequestResolver` to a thin tick facade.
- Child lookup failure handling now uses the same grace/drop lifecycle as missing children
  (instead of infinite fail-open), so repeated lookup exceptions eventually prune stale child
  links and unblock parent progress.
- Lost-package response handling now verifies tuple liveness (`stack + requester + address + requestedAt`)
  before processing, and stale dialogs self-invalidate instead of triggering empty reorders or
  phantom follow-up interactions.
- Open inflight overdue entries are now re-armed for prompting on world load (`notified=false`
  during load), so blocked/lost-package interactions can reappear after reload when still unresolved.
- Cancelled requests now clear matching lost-package inflight entries immediately, so cancelling a
  build/request also removes stale missing-package prompts for that cancelled demand.
- Delivery-cancel callbacks now requeue parent pending state even when pickup BE lookup is
  temporarily unavailable, so parent requests retry instead of remaining blocked as
  delivery-in-progress.
- Courier diagnostics no longer probes the legacy `CourierAssignmentModule` path at all, so
  diagnostics cannot trigger module-missing tick exceptions after shop-courier decoupling.
- Successful lost-package actions now close the blocking interaction deterministically (`Reorder`
  accepted by stock network or `Handover` package consumed and processed), and new blocking
  interactions only appear again when a fresh overdue notice is generated.
- Flow-step chat lines are now independently configurable and default to disabled
  (`flowChatMessagesEnabled=false`), while player-facing Create Shop chat keeps a single concise
  message per stage (order and delivery-start) to avoid duplicate/near-duplicate chat spam.
- Mixed-source (rack + network) request handling now defers delivery-child creation from
  `attemptResolve` to `tickPending`, so MineColonies courier pickup is only scheduled after
  rack-side availability/reconciliation on the pending path.
- Reservation consumption timing is now delivery-completion based for local Create Shop delivery
  starts (pickup or registered rack containers), so rack housekeeping no longer pulls items into
  hut inventory before MineColonies delivery children have actually completed pickup/transport.
- Attempt-resolve orchestration is now centralized in `CreateShopAttemptResolveService`, so
  `CreateShopRequestResolver.attemptResolveRequest(...)` is a thin delegate and request ordering /
  defer / delivery-creation decision flow is isolated from resolver lifecycle scaffolding.
- Resolver runtime trackers for cancelled/pending-notice/retrying-reassign are now instance-local
  (not static), eliminating cross-instance/world carryover risk that could resurrect stale tokens
  after reloads and amplify request drift.
- Delivery-child stale/recovery-arm lifecycle and tracked-child cleanup are now centralized in
  `CreateShopDeliveryChildLifecycleService`, reducing resolver state mutation fan-out and keeping
  stale timeout ownership in one place.
- Terminal request lifecycle handling (`resolveRequest` skip/complete path + completion/cancel
  cleanup hooks) is now centralized in `CreateShopTerminalRequestLifecycleService`, reducing
  request-end mutation duplication inside `CreateShopRequestResolver`.
- Local delivery-origin matching and stack count/label helpers are now shared utilities
  (`CreateShopDeliveryOriginMatcher`, `CreateShopStackMetrics`) used directly by resolver
  services, removing duplicate helper logic and narrowing resolver surface area.
- Delivery callback routing (`onDeliveryCancelled` / `onDeliveryComplete`) is now centralized in
  `CreateShopDeliveryCallbackService`, keeping resolver callback entrypoints as thin delegates.
- Pending tick orchestration wiring now injects processor/collector dependencies directly into
  `CreateShopTickPendingService` and `CreateShopPendingRequestProcessorService` constructors,
  allowing removal of several resolver service-getter passthroughs from the ops surface.
- Shop lookup access is now unified on `getShopForOps(...)` across validator/pending/delivery
  services, removing the duplicate validator-specific resolver facade.
- Pending/cooldown request state writes are now centralized through
  `CreateShopRequestStateMutatorService` (ordered+pending and clear+remove), reducing split
  mutation paths across attempt/cancel/complete/timeout/terminal flows.
- Child reconciliation now depends directly on delivery lifecycle/recovery/snapshot services
  instead of resolver stale-recovery forwarding methods, shrinking resolver orchestration surface
  and isolating child-recovery behavior inside dedicated components.
- Additional resolver forwarder cleanup removed obsolete internal delegation methods
  (`processTimedOutFlows`, `clearTrackedChildrenForParent`, `unwrapStandardManagerForOps`, etc.),
  with callers switched to direct service/static usage.
- Request-state mutator usage is now constructor-injected into the affected lifecycle services
  (attempt/cancel/recovery/completion/timeout/pending-decision/post-creation/reservation/terminal),
  removing the corresponding resolver ops getter dependency.
- Ownership and worker-availability checks are now constructor-injected into pending/recovery
  services (`CreateShopPendingRequestGateService`, `CreateShopPendingTokenCollectorService`,
  `CreateShopDeliveryChildRecoveryService`, `CreateShopPendingStateDecisionService`), removing
  additional resolver ops getter dependencies.
- Resolver messaging is now constructor-injected into attempt/post-creation services
  (`CreateShopAttemptResolveService`, `CreateShopPostCreationUpdateService`), allowing removal of
  the resolver messaging ops getter.
- Delivery callback services now receive `deliveryManager`/`diagnostics`/`recheck` via constructor
  injection (`CreateShopDeliveryCancelService`, `CreateShopDeliveryCompletionService`) instead of
  resolving those collaborators through resolver ops getters.
- Outstanding-needed calculation (`requested - leftover - reservedForRequest`) is now centralized in
  `CreateShopOutstandingNeededService` and used by validator/attempt/pending-decision flows,
  removing duplicated arithmetic paths that previously lived behind resolver helper forwarding.
- Pending gate/reconciliation services now call shared resolver state operations directly
  (`clearPendingTokenState`, `touchFlow`, `shouldDropMissingChild`) instead of `*ForOps`
  forwarding aliases, reducing duplicated resolver ops surface while keeping behavior unchanged.
- Terminal resolve lifecycle now owns its own skip/completion diagnostics (ordered/cooldown gate +
  post-resolve state log) with constructor-injected cooldown/diagnostics dependencies, reducing
  resolver helper forwarding in the `resolveRequest` path.
- Tick-pending telemetry/perf state is now centralized in `CreateShopTickPendingTelemetryService`
  (debug cadence, candidate snapshot logs, perf emission) and injected into pending token
  collection/tick orchestration, removing additional resolver-level telemetry forwarding/state.
- Terminal-state gate in pending processing now uses shared static classification
  (`CreateShopRequestResolver.isTerminalRequestState`) directly, removing one more resolver
  forwarding alias while preserving terminal-skip behavior.
- Delivery callback and pending tick orchestration now call direct resolver methods
  (`handleDeliveryComplete`, `handleDeliveryCancelled`, `reassignResolvableRetryingRequests`,
  `getRecheck`) instead of `*ForOps` aliases, continuing resolver ops-surface reduction without
  behavior changes.
- Flow-transition entrypoints now use the shared direct resolver method `transitionFlow(...)`
  across attempt/pending/timeout/terminal/completion services, removing the last
  `transitionFlowForOps(...)` forwarding layer.
- Resolver shop lookup now uses direct `getShop(...)` across validator/attempt/tick/delivery
  services, removing the `getShopForOps(...)` wrapper and reducing resolver facade duplication.
- Resolver flow/timeout/debug/retry helper calls now use direct method names
  (`resolveNowTick`, `getFlowStateMachine`, `getInflightTimeoutTicksSafe`,
  `clearStaleRecoveryArm`, `clearTrackedChildrenForParent`, `isDebugLoggingEnabled`,
  `getRetryingReassignAttempts`, `logDeliveryLinkState`) instead of `*ForOps` aliases.
- Resolver diagnostics access now uses direct `getDiagnostics()` across attempt/pending/recovery/
  post-creation services, removing the `getDiagnosticsForOps()` forwarding alias.

Known focus area:
- Live-world validation for long-running colonies under resolver-token drift and worker status churn.
- Create Shop task-tab observability: show inflight packages and shop-triggered pickup/delivery
  request references directly in the task UI for easier live diagnosis.
- Stale delivery-child recovery hardening: detect long-running `CREATED/ASSIGNED/IN_PROGRESS`
  delivery children and trigger controlled cancel/requeue cleanup so parent requests can continue.
- Delivery dispatch notify accounting is corrected: courier module scan now increments `notified`
  counters consistently (instead of always logging `notified=0`) for clearer delivery diagnostics.
- Pending top-up now subtracts already-available rack stock before ordering from Create network,
  preventing duplicate reorders when requested quantity is already physically present in shop racks.
- Pending top-up now hard-blocks network reorders while matching inflight stock for the same
  request tuple is still open, preventing repeated order loops before overdue/lost-package
  resolution.
- Pending reconciliation now refreshes missing request reservations from currently available rack
  stock after request-state refresh, improving reload recovery when reservation maps drift or reset.
- Shopkeeper AI now treats resolver work and unreserved incoming-rack housekeeping as urgent work,
  so it does not drop into idle while pending resolvable requests or rack cleanup work remain.
- Incoming rack housekeeping now runs in small timed batches, moving only unreserved rack items into
  hut inventory and leaving reserved quantities in place for MineColonies delivery creation.
- Incoming rack housekeeping is now resolver-work gated: rack->hut transfers pause while the local
  Create Shop resolver still has active request work, reducing reservation-race drift where fresh
  incoming request items could be moved before delivery linkage settles.
- Resolver active-work detection now ignores terminal flow-history records and only treats
  non-terminal flow states as active work, so completed/cancelled history cannot permanently block
  housekeeping.
- Incoming rack housekeeping is availability-gated: it pauses while the assigned shopkeeper is in
  unavailable citizen states (for example sleep/eat/sick/mourning/raided) and resumes afterward.
- Housekeeping transfer insert-capacity checks are now simulation-only before extraction, preventing
  pre-extract ghost inserts and eliminating rack->hut item duplication.
- Housekeeping rack discovery now refreshes registered rack containers and falls back to a local
  rack scan around the shop when container registrations are temporarily empty after reload.
- Housekeeping now includes cadence catch-up budgeting: when MineColonies building ticks are sparse,
  each run can move multiple due stacks (capped) to approximate the configured one-stack-per-interval
  behavior over wall-clock time.
- Housekeeping now targets hut-internal inventory first (with fallback to capability handler), so
  successful transfers are visible in the hut storage view instead of only through aggregate handlers.
- Housekeeping rack extraction now prefers rack capability handlers over generic inventory handlers,
  improving reliability of real extraction on live racks.
- Rate-limited housekeeping diagnostics are available under debug logging to surface gate reasons,
  rack discovery, unreserved budget, cooldown waits, and moved counts during live validation.

Out of scope for this PR:
- `CreateNetworkFacade.extract(...)` still uses availability-based placeholder logic and is tracked
  as a follow-up TODO for dedicated Create API extraction wiring.
