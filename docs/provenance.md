# Provenance

TheSettler_X_Create is developed independently using the public MineColonies and Create APIs.
The architecture follows the patterns those APIs require (resolver factories, token serialization,
logistics summaries). Similar structure in other mods that integrate these APIs is expected and does
not imply shared code or shared implementation.

No third-party bridge code is included. All integration logic is authored specifically for this
project, and the feature set is documented in ARCHITECTURE.md and FEATURE_STATUS.md.

If external references, ideas, or sample snippets are used in the future, they will be explicitly
credited here (with source and scope), along with the corresponding implementation notes.

Scope and constraints:
- API-driven integration: request resolution and logistics flows follow MineColonies/Create contracts.
- Compatibility first: serialization keys and IDs remain stable unless a migration is provided.
- Independent implementation: behavior, edge cases, and UX (perma requests, belt placement, pickup/output
  reservation handling) are implemented for this mod.

Implementation notes:
- This codebase intentionally uses MineColonies standard hut windows and modules where possible to
  reduce maintenance and keep UI behavior consistent with upstream.
- Public API usage and platform constraints explain most structural similarity with other integrations.
- UX copy and configuration guidance are written specifically for this mod (for example, in-hut
  address panel descriptions that clarify expected routing behavior).
- Inflight request tracking and shopkeeper notifications are authored for this project using MineColonies
  interaction handlers and Create stock network APIs.
- Delivery creation logic for Create Shop is authored here and intentionally restricted to physical rack
  inventory (not virtual/pickup stock) to avoid false deliveries for inflight items.
- Ongoing refactors (resolver module splits and upcoming stock/validator/delivery extracts) are purely
  internal reorganizations of this codebase; no external implementations or third-party bridge code are
  incorporated or adapted.
- The pending delivery tracker and reconciliation tick are authored for this project to improve
  consistency when multiple Create deliveries arrive close together or when the shopkeeper is idle.
- Lost-package recovery interactions (shopkeeper chat actions, manual package handover, restart order
  flow), inflight consumption semantics, and storage-capacity request gating are authored specifically
  for this project using MineColonies interaction handlers and Create logistics APIs.
- Resolver lifecycle hardening in v0.0.11 is authored specifically for this project:
  duplicate resolver registration prevention, registered-token-only assignment injection, and stale
  Create Shop resolver reassignment for recovering stuck IN_PROGRESS requests after resolver/token
  churn in existing saves.
- Ongoing static-inspection cleanup (warning reduction and readability refactors) is authored in this
  codebase and limited to behavior-preserving changes in existing Create Shop request-system logic.
- Headless regression coverage for resolver reassignment edge cases (stale vs. active Create Shop
  resolver assignments) is authored in this project as local JUnit/Mockito tests.
- Additional headless regression coverage for delivery-resolver disable/enable behavior, reassignment
  cooldown gating, child-chain reassignment, and burst-arrival request handling is authored in this
  project as local JUnit/Mockito tests for the Create Shop resolver injector flow.
- Delivery self-loop prevention (guarding against pickup==target routing such as Postbox->Postbox) and
  accompanying headless unit tests are authored in this project within Create Shop delivery management.
- Parent-request reconciliation now removes terminal/missing child delivery links before continuing
  pending processing, and Create network request stacks are consolidated/chunked to the package limit;
  both behaviors are authored in this project for Create Shop request-flow stability.
- Pending reconciliation now derives missing pending counts from active request payload state
  (including non-exhaustive leftovers and existing reservations) to recover late-arrival routing
  without external resets; this behavior is authored in this project.
- Resolver reassignment now explicitly handles stale assignment tokens whose resolver can no longer
  be resolved, rerouting those requests instead of preserving dead formal assignments.
- Create stock ordering now includes per-tick grouped broadcast batching (keyed by network/address/
  requester) to reduce fragmented package emission under concurrent request bursts; this behavior is
  authored in this project.
- Grouped broadcast flushing now retries failed Create-network broadcasts by re-queuing the failed
  grouped bucket for the next tick; this reliability behavior is authored in this project.
- Debug-log gating hardening for headless/test execution (safe fallback when NeoForge config is not
  loaded yet) is authored in this codebase and does not depend on external bridge implementations.
- Additional headless-safe debug-log gating in Create Shop resolver callback/cooldown paths is
  authored in this project so callback regression tests can run without requiring NeoForge config
  bootstrap in the unit-test JVM.
- Delivery child-request creation now preserves MineColonies parent linkage by using the original
  requester instance (without SafeRequester wrapping) in Create Shop delivery creation; this fix is
  authored in this project for resolver-chain stability in live worlds.
- Delivery reassignment and pending reconciliation hardening in v0.0.12 is authored in this project:
  stale `IN_PROGRESS` delivery requests are retried on a shorter cadence, and stale
  `deliveries-created` parent markers are cleared when no active child remains to prevent
  long-lived blocked parent requests in existing saves.
- Worker-availability guard validation tests and resolver callback cleanup regression tests are
  authored in this project to reduce repeat regressions around pending-delivery stalls and
  cancellation/cleanup state handling.
- Strict branch state-machine refactor (branch `refactor/strict-bridge-state-machine`, 2026-02-21)
  is authored in this project: Create Shop request-flow tracking is now explicit and monotonic via
  `CreateShopFlowState` + `CreateShopFlowRecord` + `CreateShopRequestStateMachine`, with lifecycle
  transitions wired to resolver attempt/tick/callback paths and timeout-based stale-flow cleanup.
- Flow-step chat prompts (ordered, arrived, reserved, delivery-created, delivery-completed,
  request-completed, cancelled, timeout reset) are authored in this project to make resolver flow
  observable in live testing without external tooling.
- Legacy global resolver injection (`CreateShopResolverInjector`) and SafeRequester factory runtime
  registration were removed on the strict branch; resolver/provider ownership now relies on
  standard MineColonies building-provider registration paths in this codebase.
- Legacy manual requestable-type assignment mutation (`ensureDeliverableAssignment`) was removed in
  favor of MineColonies-native resolver registration assignment behavior.
- Headless tests introduced on the strict branch (state-machine monotonic/timeout behavior and
  no-private-reflection guard for resolver manager unwrapping) are authored in this project.
- Parent-child link hardening on the strict branch is authored in this project: delivery child
  requests are now explicitly linked via `request.addChild(token)` and rolled back via
  `updateRequestState(..., CANCELLED)` if link creation fails, reducing orphan-child risk in
  parent completion flow.
- Resolver assignment-drift hardening on the strict branch is authored in this project: the shop
  now synchronizes to the active MineColonies-registered resolver token for its provider before
  pending tick processing, and triggers provider repair only when registration/type membership is
  inconsistent.
- Parent/child delivery-chain completion fix on the strict branch is authored in this project:
  created delivery requests now set both parent-child edges (`addChild` + `setParent`) so
  MineColonies parent completion callbacks can fire normally after child completion.
- Custom Create Shop courier-module removal on the strict branch is authored in this project:
  Create Shop now uses MineColonies native warehouse courier module wiring instead of a mod-specific
  courier-assignment implementation.
- Resolver drift repair hardening on the strict branch is authored in this project: the resolver
  factory no longer reuses stale cached shop-resolver instances across request-system rebuilds, and
  tick resolver selection can recover via assignment-backed Create Shop resolver discovery.
- Delivery child requester binding on the strict branch is authored in this project: Create Shop
  delivery children are now created with the Create Shop resolver as requester (aligned to
  MineColonies warehouse-resolver pattern) instead of the parent requester, reducing resolver-chain
  drift and stuck `IN_PROGRESS` child risk.
- Tick-pending cancellation logging hotfix on the strict branch is authored in this project: a
  logger call in Create Shop pending processing was corrected to avoid a runtime
  `ClassCastException` (`StandardToken` being routed to a throwable overload), preventing colony
  tick interruption during cancelled-request handling.
- Parent-child duplication guard on the strict branch is authored in this project: Create Shop
  pending processing now removes duplicate child-token entries from parent requests, and delivery
  child-link creation deduplicates repeated same-token parent links to avoid multi-processing of a
  single delivery child.
- Resolver assignment-drift fallback improvement on the strict branch is authored in this project:
  tick resolver selection now prefers an assignment-backed Create Shop resolver when the
  provider-prioritized resolver has no assigned requests, reducing no-assignment stalls after
  request-system token churn.
- Delivery link idempotency hardening on the strict branch is authored in this project: Create
  Shop delivery child linking now skips `addChild` when MineColonies flow already linked the token
  on the parent, preventing repeated same-token child duplication in wrapped resolver flows.
- Wrapped-manager delivery fallback support on the strict branch is authored in this project:
  Create Shop delivery fallback now handles `WrappedBlacklistAssignmentRequestManager` paths by
  using generic request-manager assignment checks and queue enqueue fallback, preventing
  `notified=0` delivery stalls when standard-manager unwrapping is unavailable.
- Wrapped-manager delivery creation deferral on the strict branch is authored in this project:
  `attemptResolve` now defers rack-based delivery-child creation when running under wrapped request
  managers and leaves child creation to `tickPending` under the standard manager, avoiding
  unregistered-child tokens and subsequent cancel/reorder loops.
- Queue-only courier dispatch hardening on the strict branch is authored in this project: Create
  Shop delivery dispatch no longer injects requests directly into deliveryman jobs and now relies
  on MineColonies warehouse queue dispatch only, with queue deduplication to avoid repeated
  token insertion.
- Resolver followup behavior alignment on the strict branch is authored in this project:
  Create Shop resolver followup completion now returns `null` (no explicit followups), matching
  MineColonies delivery-resolver semantics while avoiding unsafe warehouse-tile casts.
- Tick-pending assignment drift recovery on the strict branch is authored in this project:
  when the active Create Shop resolver token has no direct assignment entry, pending processing now
  recovers assignments from local Create Shop resolver tokens in the manager assignment map, so
  partial-delivery continuations do not stall on resolver-id drift.
- Partial-delivery network top-up on the strict branch is authored in this project: pending
  processing now issues an idempotent Create-network top-up request for outstanding deficits
  (`pendingCount - reservedForRequest`) as soon as stock becomes available, and reserves pulled
  items to prevent duplicate reordering while delivery children are still in flight.
- Assignment ownership fallback on the strict branch is authored in this project: pending
  processing now performs a second recovery pass that inspects assignment tokens by request
  ownership (`getResolverForRequest`) and only keeps tokens owned by a local Create Shop resolver,
  so request handling can continue when resolver-id keyed assignment maps drift.
