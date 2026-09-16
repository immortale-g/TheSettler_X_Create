#!/usr/bin/env bash
# Prints Gradle -P overrides for the newest releases of the dependencies that break most often:
# MineColonies, Structurize, Create (with the Ponder it bundles) and Create Factory Logistics for
# Minecraft 1.21.1. Only release
# builds are considered, not snapshots, because releases are what players install from CurseForge
# and Modrinth.
#
# Usage: ./gradlew test $(.github/scripts/resolve-latest-deps.sh)
set -euo pipefail

LDTTEAM="https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam"
MODRINTH="https://api.modrinth.com/maven/maven/modrinth"

# latest <metadata-url> <extended-regex matching the wanted version strings>
latest() {
  curl -fsSL "$1" \
    | grep -oE '<version>[^<]+</version>' \
    | sed -E 's#</?version>##g' \
    | grep -E "$2" \
    | sort -V \
    | tail -n 1
}

minecolonies=$(latest "$LDTTEAM/minecolonies/maven-metadata.xml" '^1\.1\.[0-9]+-1\.21\.1$')
structurize=$(latest "$LDTTEAM/structurize/maven-metadata.xml" '^1\.0\.[0-9]+-1\.21\.1$')
create=$(latest "$MODRINTH/create/maven-metadata.xml" '^[0-9]+\.[0-9]+\.[0-9]+\+mc1\.21\.1$')

# Create ships Ponder inside its jar and FML refuses to load it next to an older Ponder on the test
# classpath, so ponder_version follows whatever the resolved Create release bundles.
create_jar=$(mktemp)
curl -fsSL "$MODRINTH/create/$create/create-$create.jar" -o "$create_jar"
ponder=$(unzip -Z1 "$create_jar" \
  | grep -oE '^META-INF/jarjar/ponder-neoforge-[^/]+\.jar$' \
  | sed -E 's#^META-INF/jarjar/ponder-neoforge-(.+)\.jar$#\1#' \
  | head -n 1)
rm -f "$create_jar"

# CFL version numbers carry no Minecraft version, and its Maven metadata also lists 1.20.1 Forge
# builds, so the newest NeoForge 1.21.1 release has to come from the Modrinth API, which returns
# versions newest first. The version number is the same one the Maven repository uses.
cfl=$(curl -fsSL -G "https://api.modrinth.com/v2/project/create_factory_logistics/version" \
  --data-urlencode 'loaders=["neoforge"]' \
  --data-urlencode 'game_versions=["1.21.1"]' \
  | grep -oE '"version_number":"[^"]+"' \
  | head -n 1 \
  | sed -E 's/^"version_number":"([^"]+)"$/\1/')

for value in "$minecolonies" "$structurize" "$create" "$ponder" "$cfl"; do
  # Accept only plain version strings, so a mangled lookup fails here instead of passing an
  # unusable -P value to Gradle.
  if ! [[ "$value" =~ ^[0-9A-Za-z.+-]+$ ]]; then
    echo "could not resolve a latest version (got '$value')" >&2
    exit 1
  fi
done

# Structurize 1.0.808 introduced the IPlacementContext handler API.
structurize_build=$(echo "$structurize" | sed -E 's/^1\.0\.([0-9]+)-.*/\1/')
if [ "$structurize_build" -ge 808 ]; then
  structurize_api=modern
else
  structurize_api=legacy
fi

echo "-Pminecolonies_version=$minecolonies" \
  "-Pstructurize_version=$structurize" \
  "-Pstructurize_compile_version=$structurize" \
  "-Pstructurize_api=$structurize_api" \
  "-Pcreate_version=$create"   "-Pponder_version=$ponder" \
  "-Pcfl_version=$cfl"
