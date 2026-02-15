Installation information
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
