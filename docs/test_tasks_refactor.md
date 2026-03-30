# CreateShop Refactor Test Tasks

## Goal
Validate request lifecycle, inflight recovery, and delivery completion after each refactor slice.

## Pre-flight
1. Start world with one `Warehouse`, one `Create Shop`, valid rack space, assigned courier.
2. Ensure Create stock network contains test item (`create:shaft`) in sufficient amount.
3. Enable debug logging for CreateShop.

## Runtime Commands
1. `/thesettlerxcreate reset_live_state`
2. `/thesettlerxcreate run_live_test requests=1 amount=8 item=create:shaft`

## Core Scenarios
1. Happy path single order:
`run_live_test` once, verify:
- order from network is logged
- items arrive in shop rack
- delivery child is created
- courier brings to warehouse
- parent becomes terminal (no stuck `IN_PROGRESS`)

2. Burst path two orders:
Run `run_live_test` twice quickly, verify:
- no over-reservation
- no duplicate child for same parent
- both parents terminal

3. Lost package -> handover:
- steal package before shop arrival
- wait overdue interaction
- choose `handover package`
- verify tuple inflight consumed
- verify delivery is created from rack and completes
- verify interaction closes and does not duplicate

4. Lost package -> reorder:
- create overdue situation
- choose `re-order from network`
- verify old inflight tuple consumed/replaced
- verify exactly one new order for that tuple
- verify terminal completion

5. Lost package -> cancel:
- create overdue situation
- choose `cancel request`
- verify only intended tuple/root request is canceled
- verify unrelated same-item requests stay active

6. Reorder unavailable:
- force insufficient network stock
- choose `re-order`
- verify unavailable dialog appears
- return and recover via handover/cancel path

## World Reload Stability
1. Save/quit during:
- parent waiting inflight
- parent with delivery child `IN_PROGRESS`
2. Reload world and verify:
- no phantom reorders
- no duplicate parents
- queue recovers without manual courier rehire

## Cleanup Safety
1. With active delivery child, ensure housekeeping does not move reserved pickup items.
2. After child terminal, ensure unreserved cleanup can continue.

## Log Assertions (must hold)
1. No repeated `lost-package` dialog spam for same tuple in one unresolved window.
2. No parent with `c=0` remaining indefinitely in `IN_PROGRESS`.
3. No `Create Shop ordered from network` for a tuple already inflight unless explicit user reorder.
4. No shopkeeper/courier role cross-assignment in delivery resolver logs.

## Exit Criteria
1. All scenarios pass twice in one world session.
2. World reload scenarios pass once.
3. No stuck requests requiring courier fire/rehire.
4. No manual request graph surgery needed.
