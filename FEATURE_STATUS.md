# Feature Status

Updated: 2026-02-21
Branch: `refactor/strict-bridge-state-machine`

## Completed
- Strict flow-state-machine scaffolding is active for Create Shop request handling.
- Legacy global resolver injection and SafeRequester registration paths are removed.
- Parent-child link guard is active (`addChild` + `setParent` + rollback cancel on link failure).
- Resolver drift recovery is active for stale assignment-token scenarios.
- Delivery child requester binding now uses Create Shop resolver (native MineColonies pattern).
- Headless guard test added for requester binding in delivery child creation.
- Cancelled-request tick handling no longer crashes on logger overload mismatch
  (`ClassCastException` in `tickPendingDeliveries` hotfix).
- Duplicate child-token guard is active: parent child lists are deduplicated during pending tick,
  and delivery link creation normalizes duplicate same-token links.
- Resolver selection now falls back to assignment-backed Create Shop resolver tokens when the
  current provider-selected resolver has zero assignments.
- Delivery child linking is idempotent for wrapped resolve paths (`addChild` skipped when already
  linked by manager flow).
- Delivery fallback now supports wrapped request managers (assignment attempt + queue enqueue
  fallback when `IStandardRequestManager` unwrapping is unavailable).
- `attemptResolve` now defers rack-delivery child creation for wrapped managers and hands off
  creation to standard-manager `tickPending`.
- Courier dispatch now uses warehouse request queue only (no direct `JobDeliveryman.addRequest`
  injection) with queue-token dedupe.
- Create Shop resolver completion followup now returns `null` (instead of an empty list) to align
  with MineColonies delivery-resolver semantics and avoid unsafe warehouse-followup casts.
- `tickPending` now recovers assignment tokens from local Create Shop resolvers when the current
  resolver token drifts and has no direct assignment entry (`assignmentsKeys` fallback), preventing
  no-assignment stalls after partial/cancel churn.
- Partial-delivery continuation now performs idempotent network top-up in `tickPending`: if a
  request has outstanding `pendingCount` beyond current reservation, missing items are requested
  from the Create stock network immediately when available and reserved to avoid duplicate pulls.

## In Progress
- End-to-end child completion closure reliability (parent closes after child delivery completion)
  in live-world courier routing scenarios.

## Next
- Re-run live scenario with current logs and confirm child transitions to terminal state.
- If needed, align queue/assignment handoff with MineColonies warehouse queue semantics without
  reintroducing invasive request mutation.
