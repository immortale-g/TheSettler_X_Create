"""A small Java class file reader.

Only what the comparisons need: the constant pool (what a class refers to), the member table
(what a class declares) and method bodies (what a method actually does). No dependency on ASM
or anything else, so the tool runs wherever Python does.
"""

import io
import struct
import zipfile

# Constant pool tags carrying a single two-byte index, by tag.
_INDEX_TAGS = {7: 'Class', 8: 'String', 16: 'MethodType', 19: 'Module', 20: 'Package'}
# Constant pool tags carrying two two-byte indices, by tag.
_PAIR_TAGS = {9: 'Fieldref', 10: 'Methodref', 11: 'InterfaceMethodref', 12: 'NameAndType',
              17: 'Dynamic', 18: 'InvokeDynamic'}


def _read_pool(stream):
    """Read the header and constant pool, leaving the stream on the access flags."""
    magic, _minor, _major, count = struct.unpack('>IHHH', stream.read(10))
    if magic != 0xCAFEBABE:
        raise ValueError('not a class file')
    pool = [None] * count
    i = 1
    while i < count:
        tag = stream.read(1)[0]
        if tag == 1:
            length = struct.unpack('>H', stream.read(2))[0]
            pool[i] = ('Utf8', stream.read(length).decode('utf-8', 'replace'))
        elif tag in (3, 4):
            pool[i] = ('Number', stream.read(4))
        elif tag in (5, 6):
            pool[i] = ('Number', stream.read(8))
            i += 1                                    # longs and doubles take two slots
        elif tag in _INDEX_TAGS:
            pool[i] = (_INDEX_TAGS[tag], struct.unpack('>H', stream.read(2))[0])
        elif tag in _PAIR_TAGS:
            first, second = struct.unpack('>HH', stream.read(4))
            pool[i] = (_PAIR_TAGS[tag], first, second)
        elif tag == 15:
            pool[i] = ('MethodHandle', stream.read(3))
        else:
            raise ValueError('unknown constant pool tag %d' % tag)
        i += 1
    return pool


def _utf8(pool, index):
    entry = pool[index]
    return entry[1] if entry and entry[0] == 'Utf8' else None


def _internal(name):
    """Strip array and object decoration off a class reference."""
    return name.lstrip('[').removeprefix('L').rstrip(';')


def references(data):
    """Everything this class refers to: (class names, {(owner, name, descriptor, kind)}).

    Kind is 'M' for a method and 'F' for a field.
    """
    pool = _read_pool(io.BytesIO(data))
    classes, members = set(), set()
    for entry in pool:
        if not entry:
            continue
        if entry[0] == 'Class':
            name = _utf8(pool, entry[1])
            if name:
                classes.add(_internal(name))
        elif entry[0] in ('Fieldref', 'Methodref', 'InterfaceMethodref'):
            owner = _utf8(pool, pool[entry[1]][1])
            name_and_type = pool[entry[2]]
            name = _utf8(pool, name_and_type[1])
            descriptor = _utf8(pool, name_and_type[2])
            if owner and name and descriptor:
                members.add((_internal(owner), name, descriptor,
                             'F' if entry[0] == 'Fieldref' else 'M'))
    return classes, members


def _skip_attributes(stream, count):
    for _ in range(count):
        _name, length = struct.unpack('>HI', stream.read(6))
        stream.read(length)


ACC_ABSTRACT = 0x0400
ACC_STATIC = 0x0008


def methods(data):
    """{(method name, descriptor): access flags} for every method this class declares."""
    stream = io.BytesIO(data)
    pool = _read_pool(stream)
    stream.read(6)
    interface_count = struct.unpack('>H', stream.read(2))[0]
    stream.read(2 * interface_count)
    for _ in range(struct.unpack('>H', stream.read(2))[0]):       # fields, skipped
        _access, _name, _descriptor, attribute_count = struct.unpack('>HHHH', stream.read(8))
        _skip_attributes(stream, attribute_count)
    result = {}
    for _ in range(struct.unpack('>H', stream.read(2))[0]):
        access, name_index, descriptor_index, attribute_count = struct.unpack(
            '>HHHH', stream.read(8))
        result[(_utf8(pool, name_index), _utf8(pool, descriptor_index))] = access
        _skip_attributes(stream, attribute_count)
    return result


def declaration(data):
    """What this class declares: (name, {(member name, descriptor, kind)}, [supertypes])."""
    stream = io.BytesIO(data)
    pool = _read_pool(stream)
    _access, this_index, super_index = struct.unpack('>HHH', stream.read(6))
    name = _utf8(pool, pool[this_index][1])
    interface_count = struct.unpack('>H', stream.read(2))[0]
    interfaces = [_utf8(pool, pool[i][1])
                  for i in struct.unpack('>%dH' % interface_count,
                                         stream.read(2 * interface_count))]
    supertypes = [_utf8(pool, pool[super_index][1])] if super_index else []
    supertypes += [i for i in interfaces if i]
    members = set()
    for kind in ('F', 'M'):
        for _ in range(struct.unpack('>H', stream.read(2))[0]):
            _access, name_index, descriptor_index, attribute_count = struct.unpack(
                '>HHHH', stream.read(8))
            members.add((_utf8(pool, name_index), _utf8(pool, descriptor_index), kind))
            _skip_attributes(stream, attribute_count)
    return name, members, supertypes


# Instruction lengths, indexed by opcode. Anything not listed is a single byte. wide,
# tableswitch and lookupswitch are handled separately: their length depends on their operands.
_LENGTHS = [1] * 202
for _opcode, _length in {
        16: 2, 17: 3, 18: 2, 19: 3, 20: 3, 21: 2, 22: 2, 23: 2, 24: 2, 25: 2, 54: 2, 55: 2,
        56: 2, 57: 2, 58: 2, 132: 3, 153: 3, 154: 3, 155: 3, 156: 3, 157: 3, 158: 3, 159: 3,
        160: 3, 161: 3, 162: 3, 163: 3, 164: 3, 165: 3, 166: 3, 167: 3, 168: 3, 169: 2,
        178: 3, 179: 3, 180: 3, 181: 3, 182: 3, 183: 3, 184: 3, 185: 5, 186: 5, 187: 3,
        188: 2, 189: 3, 192: 3, 193: 3, 197: 4, 198: 3, 199: 3, 200: 5, 201: 5}.items():
    _LENGTHS[_opcode] = _length

_WIDE = 196
_TABLESWITCH = 170
_LOOKUPSWITCH = 171
_INVOKES = (182, 183, 184, 185)
_LDC, _LDC_W = 18, 19


def _instructions(code):
    """Yield (opcode, operand bytes) across a method's bytecode."""
    position, end = 0, len(code)
    while position < end:
        opcode = code[position]
        if opcode == _WIDE:
            length = 6 if code[position + 1] == 132 else 4
            yield opcode, code[position + 1:position + length]
            position += length
            continue
        if opcode in (_TABLESWITCH, _LOOKUPSWITCH):
            cursor = position + 1
            cursor += (4 - (cursor % 4)) % 4               # pad to the next four byte boundary
            if opcode == _TABLESWITCH:
                low = int.from_bytes(code[cursor + 4:cursor + 8], 'big', signed=True)
                high = int.from_bytes(code[cursor + 8:cursor + 12], 'big', signed=True)
                cursor += 12 + 4 * (high - low + 1)
            else:
                pairs = int.from_bytes(code[cursor + 4:cursor + 8], 'big', signed=True)
                cursor += 8 + 8 * pairs
            yield opcode, b''
            position = cursor
            continue
        length = _LENGTHS[opcode] if opcode < len(_LENGTHS) else 1
        yield opcode, code[position + 1:position + length]
        position += length


def bodies(data):
    """{(method name, descriptor): {'calls', 'strings', 'instructions'}} for every method with code.

    'calls' are the methods it invokes as 'owner#name', 'strings' the string constants it loads
    and 'instructions' how many instructions it has. Together these describe what a method does
    closely enough to tell two versions of it apart, while staying stable against the compiler
    and against how local variables happen to be numbered.
    """
    stream = io.BytesIO(data)
    pool = _read_pool(stream)
    stream.read(6)
    interface_count = struct.unpack('>H', stream.read(2))[0]
    stream.read(2 * interface_count)
    result = {}
    for kind in ('F', 'M'):
        for _ in range(struct.unpack('>H', stream.read(2))[0]):
            _access, name_index, descriptor_index, attribute_count = struct.unpack(
                '>HHHH', stream.read(8))
            code = None
            for _ in range(attribute_count):
                attribute_name, length = struct.unpack('>HI', stream.read(6))
                payload = stream.read(length)
                if kind == 'M' and _utf8(pool, attribute_name) == 'Code':
                    code = payload
            if kind != 'M' or code is None:
                continue
            code_length = struct.unpack('>I', code[4:8])[0]
            calls, strings, count = [], [], 0
            for opcode, operand in _instructions(code[8:8 + code_length]):
                count += 1
                if opcode in _INVOKES and len(operand) >= 2:
                    entry = pool[struct.unpack('>H', operand[:2])[0]]
                    if entry and entry[0] in ('Methodref', 'InterfaceMethodref'):
                        owner = _utf8(pool, pool[entry[1]][1])
                        calls.append('%s#%s' % (owner, _utf8(pool, pool[entry[2]][1])))
                elif opcode in (_LDC, _LDC_W):
                    index = operand[0] if opcode == _LDC else struct.unpack('>H', operand[:2])[0]
                    entry = pool[index]
                    if entry and entry[0] == 'String':
                        strings.append(_utf8(pool, entry[1]))
            result[(_utf8(pool, name_index), _utf8(pool, descriptor_index))] = {
                'calls': calls, 'strings': strings, 'instructions': count}
    return result


def open_classes(paths, nested=True):
    """{class name: (archive, entry name)} across the given jars, jar-in-jar libraries included.

    The first jar carrying a class wins, which is how a classpath resolves it too.
    """
    entries = {}

    def take(archive, names):
        for name in names:
            if name.endswith('.class'):
                entries.setdefault(name[:-6], (archive, name))

    for path in paths:
        archive = zipfile.ZipFile(path)
        take(archive, archive.namelist())
        if not nested:
            continue
        for name in archive.namelist():
            if name.endswith('.jar') and ('jarjar' in name or name.startswith('META-INF/jars')):
                try:
                    inner = zipfile.ZipFile(io.BytesIO(archive.read(name)))
                except zipfile.BadZipFile:
                    continue
                take(inner, inner.namelist())
    return entries


def read_class(entries, name):
    """Raw bytes of one class from an open_classes index, or None."""
    entry = entries.get(name)
    if entry is None:
        return None
    return entry[0].read(entry[1])
