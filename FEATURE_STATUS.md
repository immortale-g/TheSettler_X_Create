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
- Capacity-stall and lost-package interaction IDs now use translatable components as well
  (including tuple-stable runtime lost-package IDs), removing remaining literal-ID paths that could
  trigger MineColonies interaction button-id cast failures.
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
- Lost-package recovery flow is now one-shot and rack-oriented: `Reorder` no longer stays blocked
  by strict inflight tuple cleanup, `Handover` inserts unpacked contents into rack flow (no hut
  fallback), and inflight cleanup now has a stack-key fallback when requester/address text drifts.
- Lost-package interaction identity is now stable per `(item, requester, address)` tuple instead
  of inquiry-text identity, preventing duplicate blocking dialogs for the same unresolved package
  when notice text/amount drifts across ticks.
- Overdue inflight notices are now aggregated per `(item, requester, address)` tuple before
  interaction trigger, preventing duplicate same-tuple lost-package prompts in one scan cycle.
- Lost-package handover matching now falls back to same-item matching when item components drift,
  so package contents can still be accepted/recovered after component-text/metadata skew.
- Successful lost-package actions now close the blocking interaction deterministically (`Reorder`
  accepted by stock network or `Handover` package consumed and processed), and new blocking
  interactions only appear again when a fresh overdue notice is generated.
- Flow-step chat lines are now independently configurable and default to disabled
  (`flowChatMessagesEnabled=false`), while player-facing Create Shop chat keeps a single concise
  message per stage (order and delivery-start) to avoid duplicate/near-duplicate chat spam.

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
- Pending reconciliation now refreshes missing request reservations from currently available rack
  stock after request-state refresh, improving reload recovery when reservation maps drift or reset.
- Shopkeeper AI now treats resolver work and unreserved incoming-rack housekeeping as urgent work,
  so it does not drop into idle while pending resolvable requests or rack cleanup work remain.
- Incoming rack housekeeping now runs in small timed batches, moving only unreserved rack items into
  hut inventory and leaving reserved quantities in place for MineColonies delivery creation.
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
