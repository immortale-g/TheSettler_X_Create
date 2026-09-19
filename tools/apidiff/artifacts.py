"""Fetching and caching the jars a comparison runs against.

Everything lands in build/apidiff/cache, which Gradle already ignores, so a second run costs
nothing. Versions come from targets.json: either pinned, or the newest release matching a
pattern, or taken straight from gradle.properties so a comparison uses exactly what the mod is
built against today.
"""

import json
import os
import re
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(ROOT, '..', '..'))
CACHE = os.path.join(PROJECT, 'build', 'apidiff', 'cache')

REPOSITORIES = {
    'ldtteam': 'https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/{artifact}',
    'modrinth': 'https://api.modrinth.com/maven/maven/modrinth/{artifact}',
    'neoforged': 'https://maven.neoforged.net/releases/{group}/{artifact}',
}
_VERSION_MANIFEST = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
# Some of these repositories answer 403 to urllib's default user agent.
_HEADERS = {'User-Agent': 'thesettler-x-create-apidiff'}


def _open(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=_HEADERS))


def download(url, filename):
    """Fetch a url into the cache once and return the local path."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, filename)
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        print('  fetching %s' % filename, flush=True)
        partial = path + '.part'
        with _open(url) as response, open(partial, 'wb') as out:
            while True:
                chunk = response.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
        os.replace(partial, path)                     # never leave a half file in the cache
    return path


def _read(url):
    with _open(url) as response:
        return response.read().decode('utf-8')


def gradle_properties():
    """gradle.properties as a plain dict, for the versions the mod is built against."""
    values = {}
    with open(os.path.join(PROJECT, 'gradle.properties'), encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                values[key.strip()] = value.strip()
    return values


def _versions(base):
    """Every version in a Maven repository's metadata, oldest first."""
    metadata = _read(base + '/maven-metadata.xml')
    return re.findall(r'<version>([^<]+)</version>', metadata)


def _sort_key(version):
    """Sort versions by their numeric parts, so 1.0.90 stays below 1.0.100."""
    return [int(part) if part.isdigit() else part
            for part in re.split(r'[.\-+]', version)]


def latest(base, pattern):
    """Newest version in a repository matching a pattern."""
    matching = [v for v in _versions(base) if re.match(pattern, v)]
    if not matching:
        raise SystemExit('no version in %s matches %s' % (base, pattern))
    return sorted(matching, key=_sort_key)[-1]


def mojang_mappings(minecraft_version):
    """Mojang's official mappings for a Minecraft version, as a local path."""
    manifest = json.loads(_read(_VERSION_MANIFEST))
    entry = next((v for v in manifest['versions'] if v['id'] == minecraft_version), None)
    if entry is None:
        raise SystemExit('Mojang publishes no version %s' % minecraft_version)
    details = json.loads(_read(entry['url']))
    return download(details['downloads']['client_mappings']['url'],
                    'mappings-%s.txt' % minecraft_version)


def targets():
    """The named jar sets from targets.json, without the leading-underscore comment keys."""
    with open(os.path.join(ROOT, 'targets.json'), encoding='utf-8') as handle:
        return {name: value for name, value in json.load(handle).items()
                if not name.startswith('_')}


def resolve(name):
    """(jar paths, minecraft version, naming) for one target from targets.json.

    Naming is 'srg' when the jars name vanilla methods the SRG way and need translating, and
    'mojang' when they do not.
    """
    target = targets().get(name)
    if target is None:
        raise SystemExit('unknown target %r, known: %s' % (name, ', '.join(targets())))
    properties = gradle_properties()
    paths = []
    for entry in target['jars']:
        base = REPOSITORIES[entry['repository']].format(
            artifact=entry['artifact'], group=entry.get('group', '').replace('.', '/'))
        if 'version' in entry:
            version = entry['version'].format(**properties)
        else:
            version = latest(base, entry['version_pattern'])
        classifier = '-%s' % entry['classifier'] if entry.get('classifier') else ''
        filename = '%s-%s%s.jar' % (entry['artifact'], version, classifier)
        paths.append(download('%s/%s/%s' % (base, version, filename), filename))
    return paths, target['minecraft_version'], target['naming']
