# CreateShop Refactor Test Tasks

## Goal
Validate request lifecycle, in-flight recovery, delivery completion, supply rules, gauge orders and
shop storage after each refactor slice or before a release.

This file is the source of truth. `docs/testing/TheSettler_x_Create_0.7.1_Test_Form.gs` turns the
numbered scenarios into a Google Form for testers; when a scenario changes here, change the
generator in the same commit.

## Pre-flight
1. Start world with one `Warehouse`, one `Create Shop`, valid rack space, assigned courier.
2. Ensure Create stock network contains test item (`create:shaft`) in sufficient amount.
3. Enable debug logging for CreateShop.

## Runtime Commands
1. `/thesettlerxcreate reset_live_state`
2. `/thesettlerxcreate run_live_test requests=1 amount=8 item=create:shaft`
3. `/thesettlerxcreate auto_test_harness start <requests> <amount>` for the scripted run
4. `/thesettlerxcreate auto_test_harness lost_inject|lost_handover_sim|lost_reorder|lost_cancel`
   to reach the lost-package scenarios without stealing a package by hand
5. `/thesettlerxcreate tracking-reset <colonyId> [scope]` (scopes include `inflight`) when a shop
   is stuck on goods that will never arrive
6. `/thesettlerxcreate diag_output_block` and `test_output_packaging` for the shop output block

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

## Shopkeeper Gate & NPE Fix

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

## Supply Rules (0.6)

### 13. Block list keeps an item in the network
- Put an item the colony wants (e.g. `create:shaft`) on the shop's **Forbidden** list.
- Trigger a colony request for it while the network holds plenty.
- **Expected:**
  - The shop does not claim the request; it goes to the Clipboard or another resolver.
  - Items on the **Allowed** list in the same tab are still served normally.
  - Both tabs keep their own icon and survive closing and reopening the hut.

### 14. Network minimum holds stock back
- Set a network minimum for an item, e.g. 64 while the network holds 80.
- Let the colony request 32 of it.
- **Expected:**
  - At most 16 are ordered; the rest of the request waits or goes elsewhere.
  - The shop's own gauge orders respect the same floor.

### 15. Amount picker
- Type `16.1k` into the amount field, confirm, reopen the window.
- **Expected:** the value reads back as written, not rejected or reset.
- Guard thirty kinds of item, then press **Add**.
- **Expected:** the picker opens on the already guarded kinds so one can be raised or lowered,
  instead of the button doing nothing.
- For an item that is already guarded, the picker pre-fills its current amount.

## Colony Factory Gauge

### 16. Two gauges, one waiting on a crafter
- Two gauges on the same shop, one asking for an item only a crafter can make.
- **Expected:**
  - The shop serves the gauge whose goods have arrived first; the crafting one does not block it.
  - The crafting order reaches the colony's crafters, not the player's request list.

### 17. Partial fulfilment closes the order
- Let a gauge ask for more than a drained warehouse can give.
- **Expected:**
  - The colony hands over what it has and closes the order.
  - The gauge notices the shortfall and asks again instead of sitting satisfied.

### 18. Large order, several packages
- Order more than nine stacks through one gauge.
- **Expected:**
  - The order travels as several packages, the remainder shows as `gauge_order_open`.
  - Save, quit, reload mid-transit: no package vanishes with its chunk.
  - A package split over several slots is counted in full on arrival.

### 19. Gauge goods are not a rack reservation
- With gauge goods waiting in the hut inventory, start a colony pickup and a shop delivery.
- **Expected:**
  - Gauge goods are held back from both, exactly as a rack reservation used to hold them.
  - A world that still has gauge goods in its racks empties those out first.

### 20. Building level gate
- Set `gaugeMinBuildingLevel` in the config, and separately a hand-written
  `permaMinBuildingLevel` in an old config file.
- **Expected:** the old key is carried over; a shop lowered to level 1 stays at level 1.

## Shop Storage

### 21. Courier goods go to the hut
- Have a courier bring goods to the shop while the racks are empty.
- **Expected:**
  - Goods land in the hut inventory, not the racks.
  - An order from Create can still arrive, because the racks stay free.

### 22. Hut full, racks as fallback
- Fill the hut inventory, then have a courier arrive.
- **Expected:**
  - The courier uses the racks.
  - The shopkeeper carries those goods back to the hut on its next round, not after five minutes.

### 23. Shop full, courier keeps its goods
- Fill hut and racks, then have a courier arrive.
- **Expected:**
  - The courier keeps its goods and says so.
  - No stack is taken out of a rack to make room.
  - With the shop's pickup priority at zero, the message says so instead of asking for couriers.

### 24. Arrival rack stays clear
- Let Create unpack into one rack until it is nearly full (`arrivalRackMinFreeSlots`, default 5).
- **Expected:**
  - Below the threshold the shopkeeper carries goods to the other racks.
  - Setting the value to 0 turns the behaviour off.
  - After Create could not deliver for want of room, the shopkeeper moves unreserved goods out
    without waiting the usual five minutes.

## In-flight Pool and Stock Ledger (0.4/0.5)

### 25. Many open orders for one item
- Create several colony requests for the same item in quick succession.
- **Expected:**
  - Nothing is ordered twice.
  - The shop keeps at most two notes per item, and a folded note's amount is added to the one kept.
  - What is on its way still adds up to what was ordered.

### 26. In-flight entry with an owner must expire
- Leave an order waiting for goods that never arrive.
- **Expected:** the entry expires on its own. If it does not, `tracking-reset <colonyId> inflight`
  is the workaround and the finding belongs in a report.

### 27. Courier dismissed and rehired
- Book a pickup against a courier, dismiss them, hire a new one.
- **Expected:** the pickup is found again; no stuck request needing manual surgery.

## World Reload Stability
1. Save/quit during:
- parent waiting inflight
- parent with delivery child `IN_PROGRESS`
2. Reload world and verify:
- no phantom reorders
- no duplicate parents
- queue recovers without manual courier rehire
3. Load a world written by an older version and verify:
- the stock ledger migrates from NBT without loss
- the shop output block takes the transition path
- gauge orders are not lost on load

## Dedicated Server and Several Shops
1. Run client and server separately; check payload authorisation and that the hut GUI stays in sync.
2. Build at least two shops in one colony, one of them with a block list and a network minimum.
3. Verify each shop keeps its own rules, ledger and in-flight pool.

## Cleanup Safety
1. With active delivery child, ensure housekeeping does not move reserved pickup items.
2. After child terminal, ensure unreserved cleanup can continue.

## Log Assertions (must hold)
1. No repeated `lost-package` dialog spam for same tuple in one unresolved window.
2. No parent with `c=0` remaining indefinitely in `IN_PROGRESS`.
3. No `Create Shop ordered from network` for a tuple already inflight unless explicit user reorder.
4. No shopkeeper/courier role cross-assignment in delivery resolver logs.
5. Grep the whole log for `[CreateShop][problem]`. Those warnings are written whether or not
   `debugLogging` is on, and every hit belongs in the report with the run it came from.
6. Grep for `MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL`. A hit means a delivery left the courier queue
   without a terminal state and is a finding of its own.
7. No `ClassCastException` out of a logger call, and no citizen dialog showing a raw number where
   a name belongs.

## Exit Criteria
1. All scenarios pass twice in one world session.
2. World reload scenarios pass once.
3. No stuck requests requiring courier fire/rehire.
4. No manual request graph surgery needed.
5. No `tracking-reset` needed to get a shop moving again.
6. The log holds no `[CreateShop][problem]` warning that is not already a written-down finding.
