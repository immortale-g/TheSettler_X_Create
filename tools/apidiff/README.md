# apidiff

Reads the compiled mod, collects every upstream class and method it touches, and asks what those
look like somewhere else: in another Minecraft version, or in a newer release of the same mods.

`compileJava` already tells us whether the mod still builds against a given set of jars. That is
what the nightly compat workflow does. This answers two questions a compile cannot:

- What exactly would be missing if we built against a set of jars we cannot compile against at
  all, such as the 1.20.1 line.
- Whether an upstream method still *does* the same thing, even though its signature is unchanged.
  That is the failure we hit in 0.3.5.1, where `broadcastPackageRequest` kept its name and started
  meaning something else.

Nothing here is bundled: every jar and mapping file is fetched on first use into
`build/apidiff/cache`, which Gradle ignores.

## Running it

Through Gradle, which compiles first:

```
gradlew apiDiff                                              # what 1.20.1 would be missing
gradlew apiDiff -PapiDiffArgs="behaviour 1.20.1 --depth 2"   # and what behaves differently there
gradlew apiDiff -PapiDiffArgs="behaviour latest-1.21.1"      # what the newest release changed
```

Or directly, once `gradlew classes` has run:

```
python tools/apidiff/apidiff.py surface 1.20.1
python tools/apidiff/apidiff.py vanilla 1.20.1
python tools/apidiff/apidiff.py behaviour 1.20.1 --depth 2
```

Python 3.9 or newer, no packages needed. `-PapiDiffPython=` points at another interpreter.

If a run stops with "holds no class files", Gradle is reporting `classes` as up to date while the
output directory is empty, which happens after switching branches. `gradlew compileJava --rerun`
fixes it. Never `gradlew clean`: `build/libs` holds released jars that are not in the repository.

## The three questions

| Command | Asks | Reads |
|---|---|---|
| `surface` | Does everything we call still exist there? | the target's jars, inheritance followed |
| `behaviour` | Do the methods we call still do the same thing? | the bytecode of those methods on both sides |
| `implements` | Do our own types still satisfy what they build on? | the abstract methods of every upstream type we extend |
| `vanilla` | Which Minecraft API we use exists in that version? | Mojang's own mappings for it |

`surface` and `behaviour` look outwards, at what we call. `implements` looks inwards, and catches
a break neither of the others can see: an upstream interface that gains an abstract method breaks
us without a single call of ours changing, and it fails as an AbstractMethodError in play rather
than as a compile error. The Structurize placement handlers are exactly that shape, which is why
they carry both the old and the new signatures.

`--fail-on-drift` makes any finding a non-zero exit, for use in a workflow. `--limit` caps how
many findings are printed.

## How `behaviour` decides

For each method it compares three things that survive recompilation: which upstream methods it
calls, which string constants it carries, and how many instructions it has. NBT keys live in the
string constants, so a storage change shows up plainly, as `PackageItem#setOrder` carrying
`OrderId`, `LinkIndex` and `IsFinalLink` on 1.20.1 and nothing on 1.21.1.

Calls into Minecraft itself are dropped from the comparison rather than compared, because a
1.20.1 jar names them the SRG way (`m_58900_`) and a 1.21.1 jar the Mojang way (`getBlockState`).
For the method names it does compare, `mappings.py` builds a translation table by joining Mojang's
mappings with MCPConfig's `joined.tsrg` over the obfuscated names they share. Without that
translation almost every method looks changed; with it, the noise is gone.

`--depth` follows the call graph outwards, so drift that sits under the method we call is found
too. Depth 2 covers about 300 methods and takes a couple of minutes on a warm cache.

What it cannot see: anything that only exists at runtime. Event order, tick timing, when
MineColonies decides a request is done. No static comparison reaches that, and neither does this
one. It also cannot compare abstract or interface methods, which have no body; those are counted
separately as not comparable.

## Targets

`targets.json` names the jar sets. `current` is whatever `gradle.properties` pins, so a comparison
runs against exactly what the mod is built against today. `latest-1.21.1` and `1.20.1` resolve the
newest release matching a pattern, so they stay current without being edited.

Create Factory Logistics is absent on purpose: the shop reaches it reflectively, so it leaves no
bytecode references for any of this to follow. `testFactoryLogistics` covers that API instead.
