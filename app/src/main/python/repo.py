import io
import os
import gzip
import json
import time
import shutil
import hashlib
import zipfile
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

UA = 'Mozilla/5.0 (Linux; Android 11; TV) LiteCenter/0.2'
OFFICIAL = ('https://mirrors.kodi.tv/addons/omega/addons.xml.gz',
            'https://mirrors.kodi.tv/addons/omega/')
INDEX_TTL = 12 * 3600


def _get(url, timeout=60):
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _root(addons_dir, aid):
    try:
        return ET.parse(os.path.join(addons_dir, aid, 'addon.xml')).getroot()
    except Exception:
        return None


def _installed_ids(addons_dir):
    if not os.path.isdir(addons_dir):
        return []
    return sorted(d for d in os.listdir(addons_dir)
                  if os.path.exists(os.path.join(addons_dir, d, 'addon.xml')))


def list_installed(addons_dir, prefix='plugin.video'):
    out = []
    for aid in _installed_ids(addons_dir):
        if prefix and not aid.startswith(prefix):
            continue
        r = _root(addons_dir, aid)
        out.append({'id': aid, 'name': (r.get('name') if r is not None else '') or aid,
                    'version': (r.get('version') if r is not None else '') or ''})
    out.sort(key=lambda x: x['name'].lower())
    return json.dumps(out)


def _sources(addons_dir):
    """Repositorios de terceiros instalados (sem o oficial)."""
    srcs = []
    for aid in _installed_ids(addons_dir):
        root = _root(addons_dir, aid)
        if root is None:
            continue
        for ext in root.findall('extension'):
            if ext.get('point') != 'xbmc.addon.repository':
                continue
            for d in (ext.findall('dir') or [ext]):
                info = (d.findtext('info') or '').strip()
                data = (d.findtext('datadir') or '').strip()
                if info and data and (info, data) not in srcs:
                    srcs.append((info, data))
    return srcs


def _cache_dir(addons_dir):
    d = os.path.join(os.path.dirname(os.path.abspath(addons_dir)), 'cache')
    os.makedirs(d, exist_ok=True)
    return d


def _load_index(info_url, cdir, force=False):
    """Baixa e resume o addons.xml (id -> nome, versao, imports). Guarda cache em disco."""
    path = os.path.join(cdir, hashlib.md5(info_url.encode()).hexdigest() + '.json')
    if not force and os.path.exists(path) and time.time() - os.path.getmtime(path) < INDEX_TTL:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            pass
    data = _get(info_url, 25)
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    idx = {}
    for _, el in ET.iterparse(io.BytesIO(data), events=('end',)):
        if el.tag == 'addon' and el.get('id'):
            idx[el.get('id')] = {
                'name': el.get('name') or el.get('id'),
                'version': el.get('version', ''),
            }
            el.clear()
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(idx, f)
    return idx


def _catalog(addons_dir, srcs, force=False):
    cdir = _cache_dir(addons_dir)

    def load(src):
        try:
            return _load_index(src[0], cdir, force), None
        except Exception as e:
            return None, e

    cat, errs = {}, []
    if not srcs:
        return cat, errs
    with ThreadPoolExecutor(max_workers=4) as ex:
        results = list(ex.map(load, srcs))
    for (info, data), (idx, err) in zip(srcs, results):
        if err is not None:
            errs.append('%s: %s' % (urllib.parse.urlparse(info).netloc or info, err))
            continue
        for aid, meta in idx.items():
            cat.setdefault(aid, (meta, data))
    return cat, errs


def _lookup(addons_dir, holder, aid):
    """Procura primeiro nos repositorios de terceiros e so depois no oficial."""
    if 'cat' not in holder:
        holder['cat'], holder['errs'] = _catalog(addons_dir, _sources(addons_dir))
    if aid in holder['cat']:
        return holder['cat'][aid]
    if 'off' not in holder:
        holder['off'], _ = _catalog(addons_dir, [OFFICIAL])
    return holder['off'].get(aid)


def _extract(addons_dir, blob):
    zf = zipfile.ZipFile(io.BytesIO(blob))
    members = [m for m in zf.infolist() if m.filename and not m.filename.startswith('__MACOSX')]
    if not members:
        raise RuntimeError('zip vazio')
    top = members[0].filename.split('/')[0]
    if not any(m.filename == top + '/addon.xml' for m in members):
        raise RuntimeError('zip invalido: falta %s/addon.xml' % top)
    base = os.path.realpath(addons_dir)
    os.makedirs(base, exist_ok=True)
    shutil.rmtree(os.path.join(base, top), ignore_errors=True)
    for m in members:
        dest = os.path.realpath(os.path.join(base, m.filename))
        if not (dest == base or dest.startswith(base + os.sep)):
            continue
        if m.filename.endswith('/'):
            os.makedirs(dest, exist_ok=True)
        else:
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with zf.open(m) as s, open(dest, 'wb') as o:
                shutil.copyfileobj(s, o)
    return top


def _install_entry(addons_dir, aid, entry):
    meta, data = entry
    if not data.endswith('/'):
        data += '/'
    url = '%s%s/%s-%s.zip' % (data, aid, aid, meta.get('version'))
    return _extract(addons_dir, _get(url, 180))


def _deps(addons_dir, aid, holder, installed, missing):
    root = _root(addons_dir, aid)
    if root is None:
        return
    for imp in root.findall('requires/import'):
        dep = imp.get('addon')
        if not dep or dep.startswith(('xbmc.', 'kodi.')):
            continue
        if os.path.isdir(os.path.join(addons_dir, dep)) or imp.get('optional') == 'true':
            continue
        entry = _lookup(addons_dir, holder, dep)
        if not entry:
            missing.append(dep)
            continue
        try:
            installed.append(_install_entry(addons_dir, dep, entry))
            _deps(addons_dir, dep, holder, installed, missing)
        except Exception:
            missing.append(dep)


def available(addons_dir, prefix='plugin.video', refresh=False):
    try:
        srcs = _sources(addons_dir)
        origem = 'terceiros'
        if not srcs:
            srcs, origem = [OFFICIAL], 'oficial'
        cat, errs = _catalog(addons_dir, srcs, force=refresh)
        items = [{'id': k, 'name': m.get('name') or k, 'version': m.get('version', '')}
                 for k, (m, d) in cat.items() if k.startswith(prefix)]
        items.sort(key=lambda x: x['name'].lower())
        return json.dumps({'items': items, 'errors': errs, 'source': origem})
    except Exception as e:
        return json.dumps({'items': [], 'errors': [str(e)], 'source': ''})


def install(addons_dir, aid):
    try:
        holder = {}
        entry = _lookup(addons_dir, holder, aid)
        if not entry:
            errs = holder.get('errs') or []
            return json.dumps({'error': 'addon nao encontrado. ' + ' | '.join(errs)})
        top = _install_entry(addons_dir, aid, entry)
        installed, missing = [top], []
        _deps(addons_dir, top, holder, installed, missing)
        return json.dumps({'id': top, 'installed': installed, 'missing': missing})
    except Exception as e:
        return json.dumps({'error': str(e)})


def install_zip(addons_dir, src):
    try:
        if src.startswith('http'):
            blob = _get(src, 180)
        else:
            with open(src, 'rb') as f:
                blob = f.read()
        top = _extract(addons_dir, blob)
        installed, missing = [top], []
        _deps(addons_dir, top, {}, installed, missing)
        return json.dumps({'id': top, 'installed': installed, 'missing': missing})
    except Exception as e:
        return json.dumps({'error': str(e)})


def uninstall(addons_dir, home, aid):
    shutil.rmtree(os.path.join(addons_dir, aid), ignore_errors=True)
    shutil.rmtree(os.path.join(home, 'userdata', 'addon_data', aid), ignore_errors=True)
    return json.dumps({'id': aid})
