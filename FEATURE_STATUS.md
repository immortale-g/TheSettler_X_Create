Project Summary: TheSettler_x_Create (MineColonies + Create integration)

Note: Keep this file updated whenever related functionality changes.

Last reviewed: 2026-02-16

Provenance / Attribution
- This project is developed independently using only public MineColonies and Create APIs.
- Similar resolver/request patterns are expected for mods integrating these APIs and are not evidence of code copying.
- No third-party bridge code is included; integration logic and UI copy are authored for this project.
- If external ideas or references are used in the future, they will be explicitly credited here and in docs/provenance.md.

Overview
This mod adds a custom MineColonies building called "Create Shop" that bridges MineColonies requests with
the Create logistics network. The Create Shop lets a colony request items from Create networks and from
Warehouses, and exposes special pickup/output blocks for automation.

Core concepts
- Create Shop building (MineColonies hut): custom building with Level 1 and Level 2 blueprints.
- Pickup block (CreateShopBlockEntity): represents the pickup endpoint and tracks reservations.
- Output block (CreateShopOutputBlockEntity): emits Create packages for perma-requested items when an output address is set.
- Addresses: Create Shop has separate receive and output addresses, plus an optional output override.
- Perma requests: the shop can auto-request all items from ore tags, gated to building level 2.
- Inflight tracking: Create stock network orders are tracked until items arrive in shop racks; overdue orders
  trigger a shopkeeper notification.
- Pending delivery tracking: a single source of truth tracks pending counts, cooldowns, and delivery-created
  state per request; reconciliation ticks resume delivery creation even if the shopkeeper was idle.
- Belts: internal belt blueprints are spawned around the shop during placement/upgrade/repair.
- Deliveries: generated only from rack positions (not pickup block) to match MineColonies deliveryman rules.
- Inflight orders that never arrive no longer trigger deliveries; only rack inventory is eligible.

How it works (high level)
1. Create Shop is placed (level 1) and later upgraded to level 2.
2. The building registers its request resolvers and integrates with MineColonies' request system.
3. Perma requests (level 2 only):
   - The player enables ore items in the Perma module GUI.
   - The shop periodically checks Warehouses for those items.
   - It creates MineColonies requests so couriers deliver to the shop.
4. Output block:
   - Packages the perma-requested items into Create packages.
   - Packages only emit when an output address is configured in the hut GUI.
   - Output override takes priority over the output address if set.

Blueprint pack layout (important)
Structurize/MineColonies requires this layout in the mod JAR:
- `blueprints/<modid>/<packname>/pack.json`
Current pack path:
- `src/main/resources/blueprints/thesettler_x_create/thesettler_x_create/pack.json`
- L1 blueprint:
  `src/main/resources/blueprints/thesettler_x_create/thesettler_x_create/craftsmanship/storage/createshop1.blueprint`
- L2 blueprint:
  `src/main/resources/blueprints/thesettler_x_create/thesettler_x_create/craftsmanship/storage/createshop2.blueprint`

Internal belt blueprints (hidden from players)
These are used by BuildingCreateShop to spawn belts:
- `src/main/resources/assets/thesettler_x_create/blueprints_internal/createshop1_belt.blueprint`
- `src/main/resources/assets/thesettler_x_create/blueprints_internal/createshop2_belt.blueprint`
- `src/main/resources/data/thesettler_x_create/blueprints_internal/createshop1_belt.blueprint`
- `src/main/resources/data/thesettler_x_create/blueprints_internal/createshop2_belt.blueprint`

Key code locations
- Building logic: `src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java`
  - Max level 2
  - Perma request tick loop
  - Belt placement on placement/upgrade/repair
- Perma GUI: `src/main/java/com/thesettler_x_create/minecolonies/client/gui/CreateShopPermaModuleWindow.java`
  - Search filter
  - All On / All Off buttons
- Network payloads:
  - `SetCreateShopPermaOrePayload`
  - `SetCreateShopPermaWaitPayload`
- Debug logging (courier delivery lifecycle and enqueue diagnostics):
  - `src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java`
  - `src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java`

Gameplay constraints
- Perma requests are only active at building level 2.
- Shopkeeper max = 1 at all levels.
- Courier max = 1 at all levels (custom courier module).
- Hut inventory button uses the standard MineColonies hut inventory menu; Create Shop addresses are configured
  via the module tab in the hut GUI (not via the inventory button).
- Delivery creation is gated to a working shopkeeper, but reconciliation continues for pending deliveries.
- Delivery pickup targets racks; pickup block is used for reservations and Create network integration only.

Known gaps / follow-ups
- Server-side strict gating for `setPermaOre` / `setPermaWaitFullStack` is not enforced; selection can be stored before level 2, though perma requests only tick at level 2.
- UI still lists ores even when locked; it only changes empty text.
- Inflight tracking requires a configured shop address to report a meaningful address in notifications.

TODO (Refactor / Quality Improvements)
- Step 2: Add input validation + safer error handling to blueprint CLI tools (`BlueprintDump`, `BlueprintFixer`, `BlueprintTypeFixer`).
- Step 3: Add unit tests for tools/utilities (at minimum blueprint tools).
- Step 4: Add concise Javadoc for public APIs/classes touched by Create Shop and tools.
