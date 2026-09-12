# TheSettler_x_Create

A bridge between [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) and
[Create](https://www.curseforge.com/minecraft/mc-mods/create). The mod adds a Create Shop hut that
registers as a request resolver inside the colony: when a colonist or building asks for something the
colony cannot cover, the shop orders it from a Create stock network and a courier delivers it.

Colonist requests keep their normal MineColonies lifecycle throughout. The shop is a supply source,
not a replacement for the request system.

Minecraft 1.21.1, NeoForge. MIT licensed.

---

## Status

Early and experimental. Expect updates, reworks and the occasional breakage.

- Back up your world before installing or updating.
- Not recommended for important long-term saves yet.
- Removing the mod from an active world can break it. Run `/thesettlerxcreate prepare_uninstall`
  first (available since `0.0.12`).
- Dedicated server support is progressing, but broader real-world validation is still ongoing.

Debug logging is **on by default** so request and delivery flows can be traced during testing.
Turn it off in `config/thesettler_x_create-common.toml` with `debugLogging = false` once you are
done validating.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219 or newer |
| Create | 6.0.10 or newer |
| MineColonies | 1.1.1264 or newer |
| JEI | 19.21.0 or newer, optional, client side |

Structurize, BlockUI, Multi-Piston and Domum Ornamentum come in transitively through MineColonies.

## What it adds

| | |
|---|---|
| Create Shop Hut | The building, with its own Create Shopkeeper colonist |
| Create Shop Pickup | The shop's interface to the logistics network |
| Create Shop Output | Ships colony goods back out to a package address |
| Colony Gauge | Requests items from the colony, mounted on a Colony Packager |
| Colony Packager | Paired with the Colony Gauge; the two only work with each other |
| Network Link Tuner | Copies a stock network from a Stock Ticker or Checker onto the hut |

See [ARCHITECTURE.md](ARCHITECTURE.md) for how these fit together.

## Building from source

```
./gradlew build
```

The jar lands in `build/libs/`. Runtime dependencies are expected in `libs/`; the versions there are
what the build and the test suite are validated against.

```
./gradlew test spotlessCheck
```

Spotless enforces google-java-format. If the build fails with
`Spotless JVM-local cache is stale`, delete `.gradle/configuration-cache` and run again.

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module layout, core concepts, request flow |
| [docs/provenance.md](docs/provenance.md) | Independent authorship, design constraints |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [ROADMAP.md](ROADMAP.md) | Cleanup and hardening plan |
| [docs/test_tasks_refactor.md](docs/test_tasks_refactor.md) | Manual test procedure for lifecycle work |
| [AGENTS.md](AGENTS.md) | Working rules for contributors and coding agents |

## Provenance

Developed independently against the public MineColonies and Create APIs. No third-party bridge code
is included. Details and the design constraints that follow from those APIs are in
[docs/provenance.md](docs/provenance.md).

## Mappings

The MDK is configured to use the official Mojang mapping names for methods and fields. Those names
are covered by a specific license; the reference copy is at
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
