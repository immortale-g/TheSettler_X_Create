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

| Structurize | 1.0.807 or newer |

BlockUI, Multi-Piston and Domum Ornamentum come in transitively through MineColonies.

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

The jar lands in `build/libs/`. `build` also runs the tests, Spotless and `testModernStructurize`.

### Dependency versions

MineColonies, Structurize, Create and the other mods are pulled from Maven at the exact versions in
the `Dependency Versions` section of `gradle.properties`. These are the versions the build and the
test suite are validated against. `minecolonies_version` and `structurize_version` also end up as
the minimum versions in `neoforge.mods.toml`, so raising them raises what players need.

Structurize 1.0.808 changed the placement handler API. The mod is compiled against
`structurize_compile_version` (new API) but runs its normal tests on `structurize_version` (old
API); `testModernStructurize` repeats the placement handler compat test on the new API.

To try other versions without editing the file, override them on the command line:

```
./gradlew test -Pminecolonies_version=1.1.1368-1.21.1 -Pstructurize_version=1.0.832-1.21.1 \
  -Pstructurize_compile_version=1.0.832-1.21.1 -Pstructurize_api=modern
```

Jars in `libs/` are only added to the dev client as optional extra mods.

### Latest release check

The `Compat (latest releases)` workflow runs daily and on demand. It asks
`.github/scripts/resolve-latest-deps.sh` for the newest MineColonies, Structurize and Create
releases and compiles and tests against them. It is an early warning for upstream API breaks, not a
release gate: the pinned build stays the reference.

Spotless enforces google-java-format. If the build fails with
`Spotless JVM-local cache is stale`, delete `.gradle/configuration-cache` and run again.

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module layout, core concepts, request flow |
| [docs/provenance.md](docs/provenance.md) | Independent authorship, design constraints |
| [ROADMAP.md](ROADMAP.md) | Status, open items for 1.0, and the state-drift analysis behind them |
| [docs/test_tasks_refactor.md](docs/test_tasks_refactor.md) | Manual test procedure for lifecycle work |

## Provenance

Developed independently against the public MineColonies and Create APIs. No third-party bridge code
is included. Details and the design constraints that follow from those APIs are in
[docs/provenance.md](docs/provenance.md).

## Mappings

The MDK is configured to use the official Mojang mapping names for methods and fields. Those names
are covered by a specific license; the reference copy is at
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
