"""Method name mappings, so jars built for different Minecraft versions can be read alike.

A 1.21.1 NeoForge mod jar names vanilla methods the way Mojang does, `getBlockState`. A 1.20.1
Forge jar names the same method `m_58900_`, its SRG name. Comparing the two without translating
would report every vanilla call as a difference.

The translation is built by joining two published files over the obfuscated names they share:
Mojang's own mappings (Mojang name to obfuscated name) and MCPConfig's joined.tsrg (obfuscated
name to SRG name). Neither is derived from the other, and both are fetched, never bundled.
"""

import functools
import re
import zipfile

import artifacts

_MOJANG_METHOD = re.compile(r'^(?:\d+:\d+:)?\S+ (\w+)\((.*)\) -> (\S+)$')
_MCP_CONFIG = ('https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/'
               '{version}/mcp_config-{version}.zip')


def argument_count(descriptor):
    """Number of arguments in a JVM method descriptor."""
    arguments = descriptor[1:descriptor.index(')')]
    count, position = 0, 0
    while position < len(arguments):
        while arguments[position] == '[':
            position += 1
        if arguments[position] == 'L':
            position = arguments.index(';', position) + 1
        else:
            position += 1
        count += 1
    return count


def _obfuscated_methods(mojang_mappings_path):
    """{obfuscated class: {(obfuscated method, argument count): Mojang name}}."""
    result = {}
    current = None
    with open(mojang_mappings_path, encoding='utf-8') as handle:
        for line in handle:
            if line.startswith('#'):
                continue
            if not line.startswith(' '):
                parts = line.strip().rstrip(':').split(' -> ')
                current = parts[1] if len(parts) == 2 else None
            elif current:
                match = _MOJANG_METHOD.match(line.strip())
                if match:
                    arguments = len([a for a in match.group(2).split(',') if a])
                    result.setdefault(current, {})[(match.group(3), arguments)] = match.group(1)
    return result


@functools.lru_cache(maxsize=4)
def srg_to_mojang(minecraft_version):
    """{SRG method name: Mojang method name} for one Minecraft version."""
    mojang = _obfuscated_methods(artifacts.mojang_mappings(minecraft_version))
    config = artifacts.download(_MCP_CONFIG.format(version=minecraft_version),
                                'mcp_config-%s.zip' % minecraft_version)
    result = {}
    with zipfile.ZipFile(config) as archive:
        with archive.open('config/joined.tsrg') as handle:
            current = None
            next(handle)                                    # the tsrg2 header line
            for raw in handle:
                line = raw.decode('utf-8')
                if not line.startswith('\t'):
                    current = line.split(' ')[0].strip()
                    continue
                parts = line.strip().split(' ')
                if len(parts) >= 3 and parts[1].startswith('('):
                    name = mojang.get(current, {}).get((parts[0], argument_count(parts[1])))
                    if name:
                        result[parts[2]] = name
    return result


def translator(minecraft_version):
    """A function mapping an SRG method name onto its Mojang name, others unchanged.

    Passing None gives the identity, for a side that already uses Mojang names.
    """
    if minecraft_version is None:
        return lambda name: name
    table = srg_to_mojang(minecraft_version)
    return lambda name: table.get(name, name)
