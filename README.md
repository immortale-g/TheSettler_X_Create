Installation information

## ⚠️ Known Issue – Colony marked as “Abandoned” after updating to 0.0.12

**Affected versions:**
Updating from **0.0.11 → 0.0.12**

### Description
When updating an existing world from version `0.0.11` to `0.0.12`, some players may experience their colony being marked as **abandoned** or appearing to be missing.

This is caused by a serialization compatibility issue with legacy request data.
Version `0.0.12` no longer registers a request factory that existed in `0.0.11`, which prevents certain saved request objects from being properly deserialized during world load.

As a result, MineColonies may fail to fully restore the colony state.

---

### Temporary Workaround

If you already updated and encounter this issue:

1. Restore your world **from a backup created before updating to 0.0.12**
2. Reinstall **0.0.11**
3. Load the restored backup world
4. Properly save and close the game
5. Wait for the upcoming compatibility patch before updating again

---

### Fix Status
This issue will be resolved in the next patch release (0.0.13+), which restores backward compatibility for legacy request serialization.

---

### Recommendation
**Always create a full world backup before updating between minor versions.**

## ⚠ Disclaimer

This mod has been tested on the **client side only**.
It has **not** been tested in a dedicated server environment.

If you choose to install or run this mod on a server, you do so **at your own risk**.
Compatibility issues, unexpected behavior, or even world corruption may occur.

Please make sure to create proper backups before installing this mod on any server.
=======

Project Positioning
===================

TheSettler_X_Create is built on top of the public MineColonies and Create APIs. The overall architecture (resolver
factories, request handlers, logistics summaries) necessarily follows the patterns dictated by those APIs. Similar
structure across mods is expected when integrating with MineColonies and Create; it is not evidence of code copying.

Differentiators in this project include perma-request workflows, belt blueprint placement logic, Create Shop
pickup/output blocks with reservation handling, and Create-specific courier integration.

This mod is designed as a bridge/adapter layer: the Create Shop uses MineColonies standard hut windows and
module tabs, while Create integration lives behind the adapter components.

Provenance / Attribution
========================
This project is developed independently using only public APIs from MineColonies and Create. No third-party
bridge code is included. If external references or ideas are used in the future, they will be credited in
`docs/provenance.md`.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources:
==========
Community Documentation: https://docs.neoforged.net/
NeoForged Discord: https://discord.neoforged.net/
