# Provenance

TheSettler_X_Create is developed independently using the public MineColonies and Create APIs.
The architecture follows the patterns those APIs require (resolver factories, token serialization,
logistics summaries). Similar structure in other mods that integrate these APIs is expected and does
not imply shared code.

No third-party bridge code is included. All integration logic is authored specifically for this
project, and the feature set is documented in ARCHITECTURE.md and FEATURE_STATUS.md.

Scope and constraints:
- API-driven integration: request resolution and logistics flows follow MineColonies/Create contracts.
- Compatibility first: serialization keys and IDs remain stable unless a migration is provided.
- Independent implementation: behavior, edge cases, and UX (perma requests, belt placement, pickup/output
  reservation handling) are implemented for this mod.
