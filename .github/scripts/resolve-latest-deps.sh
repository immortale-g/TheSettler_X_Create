#!/usr/bin/env bash
# Prints Gradle -P overrides for the newest releases of the dependencies that break most often:
# MineColonies, Structurize and Create for Minecraft 1.21.1. Only release builds are considered,
# not snapshots, because releases are what players install from CurseForge and Modrinth.
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

for value in "$minecolonies" "$structurize" "$create"; do
  if [ -z "$value" ]; then
    echo "could not resolve a latest version" >&2
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
  "-Pcreate_version=$create"
