"""Prefix imported resources so changes in one feature cannot override another."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
for feature in (ROOT/'features').iterdir():
    res = feature/'src/main/res'
    if not res.exists(): continue
    names = {}
    def register(kind, name):
        if name and not name.startswith(feature.name+'_'):
            names[(kind, name)] = feature.name+'_'+name
    for file in res.glob('values*/*.xml'):
        for child in ET.parse(file).getroot():
            kind = child.tag
            if kind in ['string-array','integer-array']: kind = 'array'
            if kind == 'item': kind = child.get('type')
            register(kind, child.get('name'))
    for folder in res.iterdir():
        if folder.name.startswith('values'): continue
        for file in folder.iterdir():
            register(folder.name.split('-')[0], file.name.split('.')[0])
    for file in res.rglob('*.xml'):
        for name in re.findall(r'@\+id/(\w+)', file.read_text(encoding='utf-8')):
            register('id', name)

    def replace(text):
        for (kind, old), new in names.items():
            text = re.sub(r'(@\+?'+re.escape(kind)+'/)'+re.escape(old)+r'(?=[\s"\'<>?]|$)', lambda m:m[1]+new, text)
            text = re.sub(r'(\bR\.'+re.escape(kind)+r'\.)'+re.escape(old.replace('.','_'))+r'\b', lambda m:m[1]+new.replace('.','_'), text)
            text = text.replace('name="'+old+'"', 'name="'+new+'"') if kind != 'id' else text
            if kind == 'style': text = text.replace('parent="'+old+'"', 'parent="'+new+'"')
        return text
    for file in (feature/'src').rglob('*'):
        if file.suffix in ['.xml','.kt','.java']:
            text = file.read_text(encoding='utf-8')
            file.write_text(replace(text), encoding='utf-8')
    for folder in res.iterdir():
        if folder.name.startswith('values'): continue
        for file in list(folder.iterdir()):
            old = file.name.split('.')[0]
            new = names.get((folder.name.split('-')[0], old))
            if new: file.rename(file.with_name(new+file.name[len(old):]))
    # Host references to imported resources (currently only Facebook's theme).
    for file in (ROOT/'app/src').rglob('*.xml'):
        file.write_text(replace(file.read_text(encoding='utf-8')), encoding='utf-8')
