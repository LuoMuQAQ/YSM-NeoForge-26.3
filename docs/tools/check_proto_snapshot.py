#!/usr/bin/env python3
"""Check normative Proto snapshots and the Model Schema packed-field profile."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


MODEL_ROOTS = (
    Path("common"),
    Path("manifest"),
    Path("asset/model"),
    Path("asset/strings"),
    Path("expression"),
)
MODEL_ENTRYPOINTS = (
    Path("manifest/manifest.proto"),
    Path("asset/model/ModelData.proto"),
    Path("asset/strings/StringData.proto"),
)
PROTOCOL_ROOTS = (
    Path("network"),
)
PROTOCOL_ENTRYPOINTS = (
    Path("network/common.proto"),
    Path("network/control.proto"),
    Path("network/minecraft_state.proto"),
    Path("network/player_state.proto"),
    Path("network/model_session.proto"),
)
IMPORT_RE = re.compile(r'^\s*import\s+"([^"]+)"\s*;', re.MULTILINE)
REPEATED_FIELD_RE = re.compile(
    r'^\s*repeated\s+'
    r'(?P<type>\.?[A-Za-z_][A-Za-z0-9_.]*)\s+'
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*\d+'
    r'\s*(?P<options>\[[^\]]*\])?\s*;',
    re.MULTILINE,
)
PACKED_TRUE_RE = re.compile(r'\bpacked\s*=\s*true\b')
PACKABLE_SCALAR_TYPES = frozenset(
    {
        "double",
        "float",
        "int32",
        "int64",
        "uint32",
        "uint64",
        "sint32",
        "sint64",
        "fixed32",
        "fixed64",
        "sfixed32",
        "sfixed64",
        "bool",
    }
)


def normalized(path: Path) -> str:
    text = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
    return text.rstrip("\n") + "\n"


def proto_set(root: Path, public_roots: tuple[Path, ...]) -> set[Path]:
    files: set[Path] = set()
    for public_root in public_roots:
        subtree = root / public_root
        if subtree.is_dir():
            files.update(path.relative_to(root) for path in subtree.rglob("*.proto"))
    return files


def import_closure(
    root: Path, entrypoints: tuple[Path, ...], errors: list[str], label: str
) -> set[Path]:
    closure: set[Path] = set()
    pending = list(entrypoints)
    while pending:
        path = pending.pop()
        if path in closure:
            continue
        absolute = root / path
        if not absolute.is_file():
            errors.append(f"missing {label} import target: {path.as_posix()}")
            continue
        closure.add(path)
        for imported in IMPORT_RE.findall(normalized(absolute)):
            pending.append(Path(imported))
    return closure


def without_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", lambda match: "\n" * match.group().count("\n"), text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", text)


def check_model_packed_fields(root: Path, closure: set[Path], errors: list[str]) -> int:
    texts = {path: without_comments(normalized(root / path)) for path in closure}
    count = 0
    for path, text in texts.items():
        for match in REPEATED_FIELD_RE.finditer(text):
            field_type = match.group("type").rsplit(".", 1)[-1]
            if field_type not in PACKABLE_SCALAR_TYPES:
                continue
            count += 1
            options = match.group("options") or ""
            if not PACKED_TRUE_RE.search(options):
                line = text.count("\n", 0, match.start()) + 1
                errors.append(
                    "source model repeated numeric/bool primitive must explicitly use "
                    f"[packed = true]: {path.as_posix()}:{line} {match.group('name')}"
                )
    return count


def check_snapshot(
    source_root: Path,
    snapshot_root: Path,
    public_roots: tuple[Path, ...],
    entrypoints: tuple[Path, ...],
    label: str,
    errors: list[str],
) -> int:
    if not snapshot_root.is_dir():
        errors.append(f"{label} snapshot root does not exist: {snapshot_root}")
        return 0
    source_files = proto_set(source_root, public_roots)
    snapshot_files = proto_set(snapshot_root, public_roots)
    source_closure = import_closure(source_root, entrypoints, errors, f"source {label}")
    snapshot_closure = import_closure(snapshot_root, entrypoints, errors, f"snapshot {label}")
    for path in sorted(source_files - source_closure):
        errors.append(f"source {label} Proto is outside normative closure: {path.as_posix()}")
    for path in sorted(snapshot_files - snapshot_closure):
        errors.append(f"snapshot {label} Proto is outside normative closure: {path.as_posix()}")
    if source_closure != source_files:
        errors.append(f"source {label} Proto set does not equal its normative closure")
    if snapshot_closure != snapshot_files:
        errors.append(f"snapshot {label} Proto set does not equal its normative closure")
    for path in sorted(source_files - snapshot_files):
        errors.append(f"missing from {label} snapshot: {path.as_posix()}")
    for path in sorted(snapshot_files - source_files):
        errors.append(f"not present in {label} source: {path.as_posix()}")
    for path in sorted(source_files & snapshot_files):
        if normalized(source_root / path) != normalized(snapshot_root / path):
            errors.append(f"{label} content differs after newline normalization: {path.as_posix()}")
    return len(snapshot_files)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-root",
        required=True,
        type=Path,
        help="Proto source tree to compare",
    )
    parser.add_argument(
        "--snapshot-root",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "standards/model-schema/proto",
        help="model snapshot root",
    )
    parser.add_argument(
        "--protocol-snapshot-root",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "standards/protocol-v1/proto",
        help="current protocol snapshot root",
    )
    args = parser.parse_args()
    source_root = args.source_root.resolve()
    snapshot_root = args.snapshot_root.resolve()
    protocol_snapshot_root = args.protocol_snapshot_root.resolve()
    errors: list[str] = []

    if not source_root.is_dir():
        errors.append(f"source root does not exist: {source_root}")
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    model_count = check_snapshot(
        source_root, snapshot_root, MODEL_ROOTS, MODEL_ENTRYPOINTS, "model", errors
    )
    model_closure = import_closure(source_root, MODEL_ENTRYPOINTS, errors, "source model")
    packed_count = check_model_packed_fields(source_root, model_closure, errors)
    protocol_count = check_snapshot(
        source_root,
        protocol_snapshot_root,
        PROTOCOL_ROOTS,
        PROTOCOL_ENTRYPOINTS,
        "protocol",
        errors,
    )

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        print(f"FAILED: {len(errors)} Proto snapshot error(s)")
        return 1

    print(
        f"OK: snapshots match {model_count} model and {protocol_count} protocol Proto file(s); "
        f"{packed_count} model repeated numeric/bool primitive field(s) explicitly packed"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
