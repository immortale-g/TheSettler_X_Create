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
- Debug-log gating hardening for headless/test execution (safe fallback when NeoForge config is not
  loaded yet) is authored in this codebase and does not depend on external bridge implementations.
