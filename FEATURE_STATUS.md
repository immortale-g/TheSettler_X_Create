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

Known focus area:
- Live-world validation for long-running colonies under resolver-token drift and worker status churn.

Out of scope for this PR:
- `CreateNetworkFacade.extract(...)` still uses availability-based placeholder logic and is tracked
  as a follow-up TODO for dedicated Create API extraction wiring.
