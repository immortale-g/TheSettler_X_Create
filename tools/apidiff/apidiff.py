"""Compare the upstream API this mod uses against another set of mod jars.

Three questions, three subcommands:

  surface    Does everything we call still exist there? Classes and members, inheritance
             followed. Answers "would this even compile".
  behaviour  Do the methods we call still do the same thing? Reads their bytecode on both
             sides and compares what they call, which string constants they carry (NBT keys
             live there) and how big they are. Answers "would this still behave the same",
             which a signature check cannot.
  vanilla    Which Minecraft classes and members we use exist in another Minecraft version,
             read off Mojang's own mappings.

Run `gradlew classes` first: all three read the compiled mod, not the sources.
"""

import argparse
import collections
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import artifacts
import classfile
import mappings

COMPILED = os.path.join(artifacts.PROJECT, 'build', 'classes', 'java', 'main')
# The upstream packages worth following. Vanilla and the loader are handled separately,
# because their names differ between versions in ways a package prefix cannot express.
UPSTREAM = ('com/simibubi/', 'com/ldtteam/', 'net/createmod/', 'dev/engine_room/')


BASELINE_HEADER = """# Upstream behaviour drift that has been read and found harmless.
#
# Each line is a method whose body differs between the pinned versions and %s, and that
# somebody looked at and decided we survive. The compat run reports the drift that is NOT in
# this file and fails on it, so a change upstream is loud exactly once instead of sitting in a
# job summary nobody opens. What turns out to be dangerous belongs in a test, not in here.
#
# Sizes and call lists are deliberately absent: they move with every upstream build and would
# make this file churn without saying anything.
#
# After reading the findings, record them:
#   gradlew apiDiff -PapiDiffArgs="behaviour %s --baseline tools/apidiff/drift-baseline.txt --write-baseline"
"""


def _our_classes():
    """Every compiled class of this mod.

    An empty output directory must not read as a clean result: with --fail-on-drift in CI that
    would turn a build that compiled nothing into a green run.
    """
    if not os.path.isdir(COMPILED):
        raise SystemExit('no compiled classes in %s, run: gradlew classes' % COMPILED)
    found = 0
    for root, _directories, files in os.walk(COMPILED):
        for name in files:
            if name.endswith('.class'):
                found += 1
                with open(os.path.join(root, name), 'rb') as handle:
                    yield handle.read()
    if found == 0:
        raise SystemExit('%s holds no class files, run: gradlew classes' % COMPILED)


def our_references():
    """(class names, members) this mod refers to, across every compiled class."""
    classes, members = set(), set()
    for data in _our_classes():
        found_classes, found_members = classfile.references(data)
        classes |= found_classes
        members |= found_members
    return classes, members


def _upstream(name):
    return any(name.startswith(prefix) for prefix in UPSTREAM)


def _mod_of(name):
    for prefix, label in (('com/simibubi/', 'Create'), ('net/createmod/', 'Create (Catnip/Ponder)'),
                          ('dev/engine_room/', 'Flywheel'),
                          ('com/ldtteam/minecolonies', 'MineColonies'),
                          ('com/ldtteam/structurize', 'Structurize'),
                          ('com/ldtteam/blockui', 'BlockUI')):
        if name.startswith(prefix):
            return label
    return name.split('/')[1] if '/' in name else name


def command_surface(args):
    paths, _minecraft, _naming = artifacts.resolve(args.target)
    entries = classfile.open_classes(paths)
    declared = {}
    for name in entries:
        if _upstream(name):
            try:
                _this, members, supertypes = classfile.declaration(
                    classfile.read_class(entries, name))
            except Exception:
                continue
            declared[name] = (members, supertypes)

    classes, members = our_references()
    wanted_classes = sorted(name for name in classes if _upstream(name))
    missing_classes = [name for name in wanted_classes if name not in declared]

    def find(owner, name, descriptor, kind, seen=None):
        """'present', 'name only', 'absent' or 'unknown' once the hierarchy leaves upstream."""
        seen = seen or set()
        if owner in seen:
            return 'absent'
        seen.add(owner)
        entry = declared.get(owner)
        if entry is None:
            return 'absent' if _upstream(owner) else 'unknown'
        own, supertypes = entry
        if (name, descriptor, kind) in own:
            return 'present'
        results = [find(parent, name, descriptor, kind, seen) if _upstream(parent) else 'unknown'
                   for parent in supertypes]
        if 'present' in results:
            return 'present'
        if any(n == name and k == kind for n, _d, k in own) or 'name only' in results:
            return 'name only'
        return 'unknown' if 'unknown' in results else 'absent'

    verdicts = collections.Counter()
    detail = collections.defaultdict(list)
    for owner, name, descriptor, kind in sorted(members):
        if not _upstream(owner):
            continue
        verdict = 'absent' if owner not in declared else find(owner, name, descriptor, kind)
        verdicts[verdict] += 1
        if verdict in ('absent', 'name only'):
            detail[verdict].append('%s#%s %s' % (owner, name, descriptor))

    print('=== classes we reference, against %s ===' % args.target)
    print('  present %d, missing %d' % (len(wanted_classes) - len(missing_classes),
                                        len(missing_classes)))
    per_mod = collections.Counter(_mod_of(c) for c in wanted_classes)
    per_mod_missing = collections.Counter(_mod_of(c) for c in missing_classes)
    for mod, count in per_mod.most_common():
        print('    %-24s %3d referenced, %3d missing' % (mod, count, per_mod_missing.get(mod, 0)))
    for name in missing_classes:
        print('      - %s' % name)

    print('\n=== members we reference ===')
    for verdict, count in verdicts.most_common():
        print('  %-12s %d' % (verdict, count))
    for verdict in ('absent', 'name only'):
        for line in detail[verdict][:args.limit]:
            print('    [%s] %s' % (verdict, line))
        if len(detail[verdict]) > args.limit:
            print('    ... %d more' % (len(detail[verdict]) - args.limit))
    return 1 if (missing_classes or detail['absent']) and args.fail_on_drift else 0


class _Side:
    """One target's classes, read lazily and translated into Mojang names if needed."""

    def __init__(self, target):
        paths, minecraft, naming = artifacts.resolve(target)
        self.entries = classfile.open_classes(paths)
        self.translate = mappings.translator(minecraft if naming == 'srg' else None)
        self.cache = {}

    def bodies(self, name):
        if name in self.cache:
            return self.cache[name]
        data = classfile.read_class(self.entries, name)
        result = {}
        if data is not None:
            try:
                result = classfile.bodies(data)
            except Exception:
                result = {}
            result = {(self.translate(method), descriptor):
                      dict(body, calls=[call.split('#')[0] + '#' + self.translate(call.split('#')[1])
                                        for call in body['calls'] if '#' in call])
                      for (method, descriptor), body in result.items()}
        self.cache[name] = result
        return result


def _fingerprint(body):
    """What a method does, in terms that survive recompilation against another Minecraft."""
    calls = tuple(call for call in body['calls'] if _upstream(call))
    return calls, tuple(body['strings']), body['instructions']


def command_behaviour(args):
    print('reading %s' % args.base, flush=True)
    base = _Side(args.base)
    print('reading %s' % args.target, flush=True)
    target = _Side(args.target)

    _classes, members = our_references()
    frontier = {(owner, name, descriptor) for owner, name, descriptor, kind in members
                if kind == 'M' and _upstream(owner) and name != '<init>'}

    verdicts = collections.Counter()
    drift = []
    seen = set()
    for level in range(args.depth + 1):
        following = set()
        for owner, name, descriptor in sorted(frontier):
            if (owner, name, descriptor) in seen:
                continue
            seen.add((owner, name, descriptor))
            here = base.bodies(owner).get((name, descriptor))
            there = target.bodies(owner).get((name, descriptor))
            if here is None or there is None:
                verdicts['not comparable (abstract, or absent one side)'] += 1
                continue
            base_calls, base_strings, base_size = _fingerprint(here)
            target_calls, target_strings, target_size = _fingerprint(there)
            if base_strings != target_strings:
                verdicts['string constants differ'] += 1
                drift.append(('strings', level, owner, name,
                              list(base_strings), list(target_strings)))
            elif base_calls != target_calls:
                verdicts['calls differ'] += 1
                drift.append(('calls', level, owner, name,
                              [c for c in base_calls if c not in target_calls][:3],
                              [c for c in target_calls if c not in base_calls][:3]))
            elif abs(base_size - target_size) / max(base_size, target_size, 1) > 0.10:
                verdicts['size differs by more than 10%'] += 1
                drift.append(('size', level, owner, name, base_size, target_size))
            else:
                verdicts['identical'] += 1
            if level < args.depth:
                for call in base_calls:
                    call_owner, call_name = call.split('#')
                    for method, call_descriptor in base.bodies(call_owner):
                        if method == call_name:
                            following.add((call_owner, method, call_descriptor))
        frontier = following
        print('  level %d: %d methods compared' % (level, len(seen)), file=sys.stderr, flush=True)

    print('\n=== %s against %s, %d levels deep, %d methods ==='
          % (args.base, args.target, args.depth, len(seen)))
    for verdict, count in verdicts.most_common():
        print('  %-46s %5d' % (verdict, count))

    print('\n=== drift (%d) ===' % len(drift))
    for kind, level, owner, name, here, there in drift[:args.limit]:
        print('\n  [level %d, %s] %s#%s' % (level, kind, owner, name))
        print('      %-14s %s' % (args.base + ':', here))
        print('      %-14s %s' % (args.target + ':', there))
    if len(drift) > args.limit:
        print('\n  ... %d more' % (len(drift) - args.limit))
    found = {(kind, owner, name) for kind, _level, owner, name, _here, _there in drift}
    if args.write_baseline:
        write_baseline(args.baseline, found, args.target)
        print()
        print('  wrote %d entries to %s' % (len(found), args.baseline))
        return 0
    if not args.baseline:
        return 1 if drift and args.fail_on_drift else 0

    known = read_baseline(args.baseline)
    new = sorted(found - known)
    print()
    print('=== new since %s (%d) ===' % (args.baseline, len(new)))
    for kind, owner, name in new:
        print('  [%s] %s#%s' % (kind, owner, name))
    gone = len(known - found)
    if gone:
        print()
        print('  %d baseline entries no longer drift, drop them when convenient' % gone)
    if new:
        print()
        print('  Read each one, then write a guard test for it or record it:')
        print('    gradlew apiDiff -PapiDiffArgs="behaviour %s --baseline %s'
              ' --write-baseline"' % (args.target, args.baseline))
    return 1 if new and args.fail_on_new_drift else 0


def read_baseline(path):
    """The drift somebody has already read and accepted. A missing file reads as none."""
    known = set()
    if not os.path.exists(path):
        return known
    with open(path, 'r', encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            kind, _separator, member = line.partition(' ')
            owner, _separator, name = member.strip().rpartition('#')
            if owner and name:
                known.add((kind, owner, name))
    return known


def write_baseline(path, found, target):
    """Record the drift there is now, sorted, so the file only moves when the drift does."""
    if not path:
        raise SystemExit('--write-baseline needs --baseline <file>')
    with open(path, 'w', encoding='utf-8', newline='') as handle:
        print(BASELINE_HEADER % (target, target), file=handle)
        for kind, owner, name in sorted(found):
            print('%s %s#%s' % (kind, owner, name), file=handle)


def command_implements(args):
    """Would our own types still satisfy the upstream types they extend over there?

    `surface` and `behaviour` both look outwards, at what we call. This looks inwards: an
    upstream interface that gains an abstract method breaks us without any call of ours
    changing, and the failure is an AbstractMethodError at placement time rather than a
    compile error. The Structurize placement handlers are exactly that shape.
    """
    paths, _minecraft, _naming = artifacts.resolve(args.target)
    entries = classfile.open_classes(paths)

    def upstream_methods(name, seen=None):
        """{(name, descriptor): is abstract} across an upstream type and everything above it."""
        seen = seen or set()
        if name in seen or not _upstream(name):
            return {}
        seen.add(name)
        data = classfile.read_class(entries, name)
        if data is None:
            return {}
        collected = {}
        try:
            _this, _members, supertypes = classfile.declaration(data)
            declared = classfile.methods(data)
        except Exception:
            return {}
        for parent in supertypes:
            collected.update(upstream_methods(parent, seen))
        for signature, access in declared.items():
            if not access & classfile.ACC_STATIC:
                collected[signature] = bool(access & classfile.ACC_ABSTRACT)
        return collected

    findings = []
    checked = 0
    for data in _our_classes():
        try:
            name, _members, supertypes = classfile.declaration(data)
            ours = set(classfile.methods(data))
        except Exception:
            continue
        upstream_parents = [p for p in supertypes if _upstream(p)]
        if not upstream_parents:
            continue
        checked += 1
        required = {}
        for parent in upstream_parents:
            required.update(upstream_methods(parent))
        for signature, is_abstract in sorted(required.items()):
            if is_abstract and signature not in ours:
                findings.append((name, upstream_parents[0], signature))

    print('=== our types against %s ===' % args.target)
    print('  %d of our classes extend or implement an upstream type' % checked)
    print('  %d abstract methods left unimplemented' % len(findings))
    for name, parent, (method, descriptor) in findings[:args.limit]:
        print('\n  %s\n      extends/implements %s\n      missing %s %s'
              % (name, parent, method, descriptor))
    if len(findings) > args.limit:
        print('\n  ... %d more' % (len(findings) - args.limit))
    if not findings:
        print('\n  Nothing missing: every upstream type we build on is fully implemented there.')
    return 1 if findings and args.fail_on_drift else 0


_MOJANG_MEMBER = re.compile(r'^(?:\d+:\d+:)?(\S+) (\w+)\((.*)\)$')


def command_vanilla(args):
    """Which vanilla classes and members we use exist in another Minecraft version."""
    path = artifacts.mojang_mappings(args.minecraft_version)
    methods, fields = collections.defaultdict(set), collections.defaultdict(set)
    current = None
    with open(path, encoding='utf-8') as handle:
        for line in handle:
            if line.startswith('#'):
                continue
            if not line.startswith(' '):
                current = line.split(' -> ')[0].strip().replace('.', '/')
                methods.setdefault(current, set())
            elif current:
                body = line.strip().split(' -> ')[0]
                match = _MOJANG_MEMBER.match(body)
                if match:
                    methods[current].add(
                        (match.group(2), len([a for a in match.group(3).split(',') if a])))
                elif ' ' in body:
                    fields[current].add(body.split(' ')[1])

    known_names = {name for members in methods.values() for name, _arity in members}
    classes, members = our_references()
    vanilla_classes = sorted(c for c in classes if c.startswith('net/minecraft/'))
    missing = [c for c in vanilla_classes if c not in methods]

    print('=== Minecraft classes we reference, against %s ===' % args.minecraft_version)
    print('  present %d, absent %d' % (len(vanilla_classes) - len(missing), len(missing)))
    for name in missing:
        print('    - %s' % name)

    verdicts = collections.Counter()
    gone = []
    for owner, name, descriptor, kind in sorted(members):
        if not owner.startswith('net/minecraft/'):
            continue
        if owner not in methods:
            verdicts['class absent'] += 1
            continue
        if kind == 'F':
            if name in fields[owner]:
                verdicts['present'] += 1
            elif any(name in values for values in fields.values()):
                verdicts['inherited or moved'] += 1
            else:
                verdicts['absent'] += 1
                gone.append('%s#%s (field)' % (owner, name))
            continue
        if name in ('<init>', '<clinit>'):
            verdicts['constructor, not checked'] += 1
            continue
        arity = mappings.argument_count(descriptor)
        if (name, arity) in methods[owner]:
            verdicts['present'] += 1
        elif any(n == name for n, _a in methods[owner]):
            verdicts['same name, other arity'] += 1
            gone.append('%s#%s %s (arity differs)' % (owner, name, descriptor))
        elif name in known_names:
            verdicts['inherited or moved'] += 1
        else:
            verdicts['absent'] += 1
            gone.append('%s#%s %s' % (owner, name, descriptor))

    print('\n=== Minecraft members we reference ===')
    for verdict, count in verdicts.most_common():
        print('  %-26s %d' % (verdict, count))
    print('\n--- not found under that name ---')
    for line in gone[:args.limit]:
        print('   ', line)
    if len(gone) > args.limit:
        print('    ... %d more' % (len(gone) - args.limit))
    return 1 if (missing or gone) and args.fail_on_drift else 0


def main(argv=None):
    # Shared options, on every subcommand rather than in front of it, so the order a person
    # would naturally type them in works.
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument('--limit', type=int, default=40, help='how many findings to print')
    common.add_argument('--fail-on-drift', action='store_true',
                        help='exit non-zero when anything was found, for CI')

    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command', required=True)

    surface = sub.add_parser('surface', parents=[common],
                             help='does everything we call still exist there')
    surface.add_argument('target', help='a target from targets.json')
    surface.set_defaults(run=command_surface)

    behaviour = sub.add_parser('behaviour', parents=[common],
                               help='do the methods we call still do the same thing')
    behaviour.add_argument('target', help='the target to compare against')
    behaviour.add_argument('--base', default='current', help='the target to compare from')
    behaviour.add_argument('--depth', type=int, default=2,
                           help='how far to follow the call graph outwards')
    behaviour.add_argument('--baseline', default=None,
                           help='drift already read; only what is missing from it counts')
    behaviour.add_argument('--fail-on-new-drift', action='store_true',
                           help='exit non-zero on drift the baseline does not list')
    behaviour.add_argument('--write-baseline', action='store_true',
                           help='rewrite --baseline with the drift there is now')
    behaviour.set_defaults(run=command_behaviour)

    implements = sub.add_parser('implements', parents=[common],
                                help='do our own types still satisfy what they build on there')
    implements.add_argument('target', help='a target from targets.json')
    implements.set_defaults(run=command_implements)

    vanilla = sub.add_parser('vanilla', parents=[common],
                             help='which Minecraft API we use exists in a version')
    vanilla.add_argument('minecraft_version', help='for example 1.20.1')
    vanilla.set_defaults(run=command_vanilla)

    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == '__main__':
    sys.exit(main())
