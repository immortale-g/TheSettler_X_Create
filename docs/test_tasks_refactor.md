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

## Shopkeeper Gate & NPE Fix (0.2.3-alpha)

These scenarios target the fix where the resolver was claiming requests even without a working
shopkeeper, causing a recurring NPE in `sendShopChat` and leaving workers like the Forester
permanently stuck.

### Pre-conditions (shared)
- Create Shop built and stocked (e.g. `create:shaft`, stripped logs, an axe in the rack).
- Debug logging enabled so resolver decisions are visible.

### 7. No shopkeeper — resolver must not claim requests
- Do **not** assign a citizen to the Create Shop.
- Trigger a colony request for an item the shop has in stock (e.g. let the Forester work).
- Wait 5–10 seconds.
- **Expected:**
  - Log shows `canResolve=false (no shopkeeper working)` for each candidate request.
  - Request appears on the Clipboard **or** reaches the correct worker directly.
  - **No** `NullPointerException` in `sendShopChat` in the log.

### 8. Shopkeeper working — resolver claims and fulfills
- Assign a shopkeeper and wait until they are `WORKING`.
- Trigger a request for a stocked item.
- **Expected:**
  - Resolver claims the request (no `canResolve=false (no shopkeeper)` log line).
  - Delivery child is created, courier delivers, parent goes terminal.

### 9. Shopkeeper unavailable (sleeping / sick / blocked)
- Assign a shopkeeper but put them in a state where `isWorkerWorking()` is false
  (e.g. night-time sleep, injury, or blocked pathing).
- Trigger a colony request.
- **Expected:**
  - Resolver returns false, request is not claimed.
  - Once the shopkeeper becomes available again and a new request arrives, the resolver
    claims it normally.

### 10. Shopkeeper leaves mid-delivery — in-flight delivery must complete
- Let the resolver claim a request with the shopkeeper working.
- Wait until the delivery child is created (`IN_PROGRESS`).
- Fire / unassign the shopkeeper.
- **Expected:**
  - `canResolveRequest` still returns `true` for that request (delivery window held).
  - Courier finishes the delivery, parent goes terminal.
  - No stuck request requiring manual intervention.

### 11. NPE regression — null result stack must never reach `sendShopChat`
- Remove the shopkeeper so no deliveries are created (rack stays empty).
- Create requests for multiple item types including a Tool request (e.g. the Forester needs
  an axe).
- Let 30+ seconds pass (multiple cooldown cycles).
- **Expected:**
  - Absolutely no `NullPointerException: Cannot invoke "ItemStack.isEmpty()"` in the log.
  - All requests are visible on the Clipboard or assigned to other resolvers.

### 12. Shopkeeper rehired — new requests are claimed again
- Start with no shopkeeper (requests go to Clipboard / other resolvers).
- Hire a shopkeeper and wait until `WORKING`.
- Create a new request for a stocked item.
- **Expected:**
  - Resolver now claims the new request.
  - Previously unresolved requests that were rerouted are **not** double-processed.

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
