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
