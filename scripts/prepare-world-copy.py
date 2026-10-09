"""Copy a legacy world and stage YSM records without upgrading Minecraft data.

Requires nbtlib==2.0.4. A model map binds each old model path to an installed,
unaltered source: {old_path: {path, root, source_sha256, old_roaming_key?,
verified_model_hash?}}. Do not point --target at an existing world.
"""
import argparse
import copy
import hashlib
import gzip
import io
import json
from pathlib import Path
import shutil
import sys
import struct
import zlib

ENTITY_SLOTS = ('projectile_model_id', 'vehicle_model_id')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def protected_nbt(value, allowed):
    """Retain all fields, including other mods' attachments, except staged slots."""
    result = copy.deepcopy(value)
    attachments = result.get('neoforge:attachments')
    if attachments is not None:
        for key in allowed:
            attachments.pop(key, None)
    return result.snbt()


def stage_entity(entity, model_map, nbt):
    attachments = entity.get('neoforge:attachments')
    if attachments is None:
        return 0, 0
    slots = [k for k in ENTITY_SLOTS if 'yes_steve_model:' + k in attachments]
    if not slots:
        return 0, 0
    if any('ysm:' + k in attachments for k in (*slots, 'legacy_entity_data')):
        raise ValueError('Entity already has current YSM data; refusing to replace it')
    original = nbt.Compound()
    refs = set()
    for slot in slots:
        old = attachments['yes_steve_model:' + slot]
        owner = str(old.get('owner_model_id', ''))
        current = copy.deepcopy(old)
        current.pop('owner_model_id', None)
        current['owner_model_hash'] = nbt.String('')
        attachments['ysm:' + slot] = nbt.Compound({'data': current})
        # Empty, uninitialized default slots have no model behavior to resolve.
        if owner != 'default' or int(old.get('initialized', 0)) != 0 or old.get('molang_vars_server_bound'):
            original[slot] = copy.deepcopy(old)
            refs.add(owner)
        attachments.pop('yes_steve_model:' + slot)
    if original:
        paths = nbt.Compound({ref: nbt.Compound({k: nbt.String(v) for k, v in model_map[ref].items()})
                              for ref in sorted(refs) if ref in model_map})
        attachments['ysm:legacy_entity_data'] = nbt.Compound({'data': nbt.Compound({
            'schema': nbt.Int(1), 'original': original, 'paths': paths, 'applied': nbt.Compound()})})
    return len(slots), len(original)


def all_entities(entities):
    for entity in entities:
        yield entity
        yield from all_entities(entity.get('Passengers', []))


def region_chunks(raw, nbt):
    # Minecraft can leave a zero-byte placeholder for an empty entity region.
    if not raw:
        return
    if len(raw) < 8192 or len(raw) % 4096:
        raise ValueError('Invalid region size')
    occupied = {0, 1}
    for index in range(1024):
        location = int.from_bytes(raw[index * 4:index * 4 + 4], 'big')
        if not location:
            continue
        offset, sectors = location >> 8, location & 255
        if offset < 2 or not sectors or (offset + sectors) * 4096 > len(raw):
            raise ValueError('Invalid region chunk location')
        used = set(range(offset, offset + sectors))
        if occupied & used:
            raise ValueError('Overlapping region chunks')
        occupied.update(used)
        start = offset * 4096
        length = int.from_bytes(raw[start:start + 4], 'big')
        if length < 1 or length + 4 > sectors * 4096:
            raise ValueError('Invalid region chunk length')
        compression = raw[start + 4]
        payload = raw[start + 5:start + 4 + length]
        if compression == 1:
            decoded = gzip.decompress(payload)
        elif compression == 2:
            decoded = zlib.decompress(payload)
        elif compression == 3:
            decoded = payload
        else:
            raise ValueError('Unsupported or external region chunk compression')
        yield index, compression, nbt.File.parse(io.BytesIO(decoded))


def stage_entity_regions(target, model_map, backup, nbt):
    """Stage entity regions in an independently copied world; back up before writes."""
    changed, total_slots, total_pending = [], 0, 0
    allowed = {'yes_steve_model:' + k for k in ENTITY_SLOTS}
    allowed.update('ysm:' + k for k in (*ENTITY_SLOTS, 'legacy_entity_data'))
    regions = [p for p in sorted(target.rglob('*.mca')) if p.parent.name == 'entities']
    # Validate all input regions before changing any file in this copied world.
    for path in regions:
        for _ in region_chunks(path.read_bytes(), nbt):
            pass
    for path in regions:
        raw = path.read_bytes()
        output = bytearray(raw)
        expected = {}
        modified = set()
        file_slots = file_pending = 0
        for index, compression, root in region_chunks(raw, nbt):
            changed_chunk = False
            for entity in all_entities(root.get('Entities', [])):
                # Passenger records are verified separately; parent comparisons
                # must not include the descendant's YSM-only changes.
                before = copy.deepcopy(entity)
                before.pop('Passengers', None)
                slots, pending = stage_entity(entity, model_map, nbt)
                after = copy.deepcopy(entity)
                after.pop('Passengers', None)
                if protected_nbt(before, allowed) != protected_nbt(after, allowed):
                    raise ValueError('Non-YSM entity data changed')
                changed_chunk |= slots > 0
                file_slots += slots
                file_pending += pending
            expected[index] = root.snbt()
            if not changed_chunk:
                continue
            stream = io.BytesIO()
            root.write(stream)
            decoded = stream.getvalue()
            payload = gzip.compress(decoded, mtime=0) if compression == 1 else (
                zlib.compress(decoded) if compression == 2 else decoded)
            chunk = struct.pack('>I', len(payload) + 1) + bytes([compression]) + payload
            sectors = (len(chunk) + 4095) // 4096
            offset = len(output) // 4096
            if sectors > 255 or offset > 0xffffff:
                raise ValueError('Staged chunk exceeds region location bounds')
            output.extend(chunk)
            output.extend(b'\0' * (sectors * 4096 - len(chunk)))
            output[index * 4:index * 4 + 4] = ((offset << 8) | sectors).to_bytes(4, 'big')
            modified.add(index)
        if not modified:
            continue
        actual = {i: root.snbt() for i, _, root in region_chunks(output, nbt)}
        if actual != expected or output[4096:8192] != raw[4096:8192]:
            raise ValueError('Region round-trip changed chunk data or timestamps')
        saved = backup / path.relative_to(target)
        if saved.exists() and sha(saved) != sha(path):
            raise ValueError('Region backup already exists with different bytes')
        saved.parent.mkdir(parents=True, exist_ok=True)
        if not saved.exists():
            shutil.copy2(path, saved)
        temporary = path.with_name(path.name + '.ysm-migration-tmp')
        if temporary.exists():
            raise ValueError('Migration temporary file already exists')
        temporary.write_bytes(output)
        if temporary.read_bytes() != output:
            raise ValueError('Region write verification failed')
        temporary.replace(path)
        changed.append({'path': path.relative_to(target).as_posix(),
                        'before_sha256': sha(saved), 'after_sha256': sha(path),
                        'changedChunks': len(modified), 'stagedSlots': file_slots,
                        'pendingSlots': file_pending})
        total_slots += file_slots
        total_pending += file_pending
    return {'regions': changed, 'stagedSlots': total_slots, 'pendingSlots': total_pending}


def stage_player(player, model_map, nbt):
    attachments = player.get('neoforge:attachments')
    if attachments is None:
        return False
    keys = ['model_id', 'own_models', 'star_models']
    if not any('yes_steve_model:' + k in attachments for k in keys):
        return False
    if any('ysm:' + k in attachments for k in keys + ['legacy_player_data']):
        raise ValueError('Player already has current YSM data; refusing to replace it')
    original = nbt.Compound({k: copy.deepcopy(attachments['yes_steve_model:' + k])
                             for k in keys if 'yes_steve_model:' + k in attachments})
    refs = set(str(v) for k in ['own_models', 'star_models'] for v in original.get(k, []))
    selection = original.get('model_id', nbt.Compound())
    refs.add(str(selection.get('model_id', '')))
    paths = nbt.Compound({ref: nbt.Compound({k: nbt.String(v) for k, v in model_map[ref].items()})
                          for ref in sorted(refs) if ref in model_map})
    attachments['ysm:legacy_player_data'] = nbt.Compound({'data': nbt.Compound({
        'schema': nbt.Int(1), 'original': original, 'paths': paths, 'applied': nbt.Compound()})})
    current = copy.deepcopy(selection)
    current.pop('model_id', None)
    current['model_hash'] = nbt.String('')
    current['ignore_grants'] = nbt.Byte(0)
    attachments['ysm:model_id'] = nbt.Compound({'data': current})
    for key in ['own_models', 'star_models']:
        attachments['ysm:' + key] = nbt.Compound({'data': nbt.Compound({'values': nbt.List[nbt.String]()})})
    for key in keys:
        attachments.pop('yes_steve_model:' + key, None)
    return True


def prepare(source, target, model_map, backup, level_name, nbt):
    source = source.resolve(strict=True)
    target = target.resolve()
    backup = backup.resolve()
    if target.exists() or target == source or source in target.parents:
        raise ValueError('Target must be a new directory outside the source world')
    if backup == source or source in backup.parents or backup == target or target in backup.parents:
        raise ValueError('Backup must be outside the source and target worlds')
    if not (source / 'level.dat').is_file():
        raise ValueError('Source is not a Minecraft world')
    shutil.copytree(source, target, copy_function=shutil.copy2)
    source_hashes = {p.relative_to(source).as_posix(): sha(p) for p in source.rglob('*') if p.is_file()}
    if source_hashes != {p.relative_to(target).as_posix(): sha(p) for p in target.rglob('*') if p.is_file()}:
        raise ValueError('World copy does not match source')
    changed = []
    count = 0
    files = [target / 'level.dat', *sorted((target / 'playerdata').glob('*.dat'))]
    for path in files:
        root = nbt.load(path)
        if path.name == 'level.dat':
            player = root.get('Data', {}).get('Player')
        else:
            player = root
        before_vanilla = None
        if player is not None:
            before_vanilla = protected_nbt(player, {
                prefix + key for prefix in ('yes_steve_model:', 'ysm:')
                for key in ('model_id', 'own_models', 'star_models', 'legacy_player_data')})
        staged = player is not None and stage_player(player, model_map, nbt)
        if staged:
            count += 1
        renamed = path.name == 'level.dat' and level_name is not None
        if renamed:
            root['Data']['LevelName'] = nbt.String(level_name)
        if not staged and not renamed:
            continue
        relative = path.relative_to(target)
        saved = backup / relative
        saved.parent.mkdir(parents=True, exist_ok=True)
        if saved.exists() and sha(saved) != sha(path):
            raise ValueError('Player NBT backup already exists with different bytes')
        if not saved.exists():
            shutil.copy2(path, saved)
        root.save(path, gzipped=True)
        written = nbt.load(path)
        actual_player = written.get('Data', {}).get('Player') if path.name == 'level.dat' else written
        if before_vanilla is not None and before_vanilla != protected_nbt(actual_player, {
                prefix + key for prefix in ('yes_steve_model:', 'ysm:')
                for key in ('model_id', 'own_models', 'star_models', 'legacy_player_data')}):
            raise ValueError('Non-YSM player data changed')
        if staged and actual_player['neoforge:attachments']['ysm:legacy_player_data'] != player['neoforge:attachments']['ysm:legacy_player_data']:
            raise ValueError('Staged YSM data did not survive NBT serialization')
        changed.append(relative.as_posix())
    entities = stage_entity_regions(target, model_map, backup, nbt)
    changed.extend(item['path'] for item in entities['regions'])
    target_hashes = {p.relative_to(target).as_posix(): sha(p) for p in target.rglob('*') if p.is_file()}
    if source_hashes.keys() != target_hashes.keys():
        raise ValueError('World-copy file inventory changed unexpectedly')
    differences = sorted(k for k in source_hashes if source_hashes[k] != target_hashes[k])
    if differences != sorted(changed):
        raise ValueError('Unexpected world-copy file changes')
    return {'source': str(source), 'target': str(target), 'backup': str(backup),
            'files': len(source_hashes), 'stagedPlayers': count, 'entities': entities, 'changedFiles': changed,
            'minecraftDataVersionUpgraded': False, 'sourceHashes': source_hashes,
            'targetHashes': target_hashes}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['source', 'target', 'model-map', 'backup-dir', 'receipt']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--level-name')
    parser.add_argument('--nbtlib-path', type=Path)
    args = parser.parse_args()
    if args.nbtlib_path:
        sys.path.insert(0, str(args.nbtlib_path.resolve()))
    import nbtlib
    model_map = json.loads(args.model_map.read_text(encoding='utf-8'))
    receipt = prepare(args.source, args.target, model_map, args.backup_dir, args.level_name, nbtlib)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in receipt.items() if not k.endswith('Hashes')}, ensure_ascii=True))


if __name__ == '__main__':
    main()
