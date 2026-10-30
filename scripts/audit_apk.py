"""Inspect the actual APK: descriptors, declared DEX classes and bundled native libraries."""
import json
from pathlib import Path
import struct
import sys
from zipfile import ZipFile

def u32(data, offset): return struct.unpack_from('<I', data, offset)[0]
def string_at(data, offset):
    while data[offset] & 0x80: offset += 1
    offset += 1
    return data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace')
def classes(data):
    strings = [string_at(data, u32(data, u32(data, 60) + 4*i)) for i in range(u32(data, 56))]
    types = [strings[u32(data, u32(data, 68) + 4*i)] for i in range(u32(data, 64))]
    return {types[u32(data, u32(data, 100) + 32*i)] for i in range(u32(data, 96))}

apk = Path(sys.argv[1])
with ZipFile(apk) as zip:
    entries = zip.read('META-INF/xposed/java_init.list').decode().splitlines()
    scope = zip.read('META-INF/xposed/scope.list').decode().splitlines()
    props = zip.read('META-INF/xposed/module.prop').decode()
    assert len(entries) == len(set(entries)) == 6, entries
    assert set(scope) == {'com.zing.zalo','com.locket.Locket','com.zhiliaoapp.musically','com.ss.android.ugc.trill','com.facebook.katana','com.facebook.orca','com.android.chrome'}, scope
    assert 'minApiVersion=102' in props
    assert 'assets/xposed_init' not in zip.namelist(), 'Unexpected legacy entry'
    defined = set().union(*(classes(zip.read(name)) for name in zip.namelist() if name.endswith('.dex')))
    for entry in entries:
        assert 'L'+entry.replace('.','/')+';' in defined, f'Missing entry {entry}'
    forbidden = [name for name in defined if name.startswith(('Lio/github/libxposed/api/', 'Lde/robv/android/xposed/', 'Lcom/highcapable/yukihookapi/'))]
    assert not forbidden, forbidden
    natives = [name for name in zip.namelist() if name.startswith('lib/') and name.endswith('.so')]
    assert any(name.endswith('/libdexkit.so') for name in natives), 'Missing DexKit native library'
    report = {'apk': str(apk), 'entries': entries, 'scope': scope, 'defined_classes': len(defined), 'native_libraries': natives, 'runtime_xposed_api_classes': 0}
    print(json.dumps(report, indent=2))
