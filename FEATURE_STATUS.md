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

Out of scope for this PR:
- `CreateNetworkFacade.extract(...)` still uses availability-based placeholder logic and is tracked
  as a follow-up TODO for dedicated Create API extraction wiring.
