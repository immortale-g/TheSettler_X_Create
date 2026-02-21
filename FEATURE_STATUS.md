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

## In Progress
- End-to-end child completion closure reliability (parent closes after child delivery completion)
  in live-world courier routing scenarios.

## Next
- Re-run live scenario with current logs and confirm child transitions to terminal state.
- If needed, align queue/assignment handoff with MineColonies warehouse queue semantics without
  reintroducing invasive request mutation.
