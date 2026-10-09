#!/usr/bin/env python3
"""Check the standalone Markdown tree using only the Python standard library."""

from __future__ import annotations

import argparse
import re
import sys
import unicodedata
from collections import defaultdict, deque
from pathlib import Path
from urllib.parse import unquote, urlsplit

from check_complexity_relationships import run as check_complexity_relationships


REQUIRED_DIRECTORIES = {
    "governance",
    "standards/model-schema",
    "standards/protocol-v1",
    "concepts",
    "architecture/network",
    "architecture/model-management",
    "architecture/animation",
    "architecture/rendering",
    "future",
    "status/known-issues",
    "tools",
}
REQUIRED_FILES = {
    "README.md",
    "migration-overview.md",
    "glossary.md",
    "governance/documentation-policy.md",
    "status/support-and-verification.md",
    "status/known-issues/format-and-schema.md",
    "status/known-issues/network.md",
    "status/known-issues/model-management.md",
    "status/known-issues/animation.md",
    "status/known-issues/rendering.md",
}
LINK_RE = re.compile(r"!?\[[^\]]+\]\(\s*(?P<target><[^>]+>|[^\s)]+)")
HEADING_RE = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.MULTILINE)
LOCAL_PATH_RE = re.compile(
    r"(?ix)(?:^|[\s`(\[])"
    r"(?:[a-z]:[\\/]|\\\\(?:\?\\)?[^\\/\s]+[\\/]|"
    r"/(?:Users|home)/[^/\s]+(?:/|$)|/mnt/[a-z](?:/|$)|~[\\/]|"
    r"%(?:USERPROFILE|HOMEPATH)%[\\/]|\$HOME[\\/])"
)
LOCAL_DIRECTORY_ALIAS_RE = re.compile(
    r"(?i)(?<![\w-])(?:ysm-ng|ysm-native|YesSteveModel)(?![\w-])"
)
LOCAL_METADATA_RE = re.compile(
    r"(?im)^\s*(?:[-*]\s*)?(?:文档编写时间|文档更新时间|最后更新|提交时间|"
    r"核查时间|验证时间|commit\s*(?:time|时间))\s*[:：]"
    r"|^\s*(?:[-*]\s*)?(?:created|updated|last\s+updated|authored)\s*[:：]"
    r"\s*20\d{2}[-/]\d{1,2}[-/]\d{1,2}"
    r"|^\s*(?:[-*]\s*)?(?:编写于|更新于|核查于|验证于)\s*20\d{2}[-/]\d{1,2}[-/]\d{1,2}"
)
VCS_METADATA_RE = re.compile(
    r"(?i)(?:\bcommit\s*(?:id|hash|sha)?\s*[:=]?\s*[0-9a-f]{7,40}\b|"
    r"提交\s*(?:哈希|版本)?\s*[:：]?\s*[0-9a-f]{7,40}\b)"
)
CORRECTION_HISTORY_RE = re.compile(
    r"(?i)(?:原始\s*(?:prompt|提示词|要求|文本|说法)|prompt\s*原文|"
    r"修正前(?:的)?(?:prompt|提示词|要求|文本|说法)|(?:修正|更正|纠正)原因|"
    r"用户(?:修正|更正|纠正))"
)
REPOSITORY_CONTEXT_RE = re.compile(
    r"(?i)(?:\b(?:checkout|worktree)\b|本地仓库|仓库中存在|仓库内源码)"
)


def markdown_files(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*.md") if ".git" not in path.parts)


def mask_code(text: str) -> str:
    text = re.sub(r"```.*?```|~~~.*?~~~", "", text, flags=re.DOTALL)
    return re.sub(r"`[^`\n]*`", "", text)


def slug(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).strip().lower()
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"[^\w\-\u4e00-\u9fff ]", "", value)
    return re.sub(r"[-\s]+", "-", value).strip("-")


def headings(path: Path) -> set[str]:
    return {slug(match.group(1)) for match in HEADING_RE.finditer(mask_code(path.read_text("utf-8")))}


def run(root: Path) -> list[str]:
    root = root.resolve()
    errors: list[str] = []
    for relative in REQUIRED_DIRECTORIES:
        if not (root / relative).is_dir():
            errors.append(f"{relative}: required directory is missing")
    for relative in REQUIRED_FILES:
        if not (root / relative).is_file():
            errors.append(f"{relative}: required file is missing")

    files = markdown_files(root)
    entry = (root / "README.md").resolve()
    graph: dict[Path, set[Path]] = defaultdict(set)
    for path in files:
        text = path.read_text("utf-8")
        visible = mask_code(text)
        if LOCAL_PATH_RE.search(text) or "file://" in text.lower():
            errors.append(f"{path.relative_to(root)}: absolute local path is forbidden")
        if LOCAL_DIRECTORY_ALIAS_RE.search(text):
            errors.append(f"{path.relative_to(root)}: local project directory alias is forbidden")
        if LOCAL_METADATA_RE.search(text) or VCS_METADATA_RE.search(text):
            errors.append(f"{path.relative_to(root)}: local revision or timestamp metadata is forbidden")
        if CORRECTION_HISTORY_RE.search(text):
            errors.append(f"{path.relative_to(root)}: prompt correction history is forbidden")
        if REPOSITORY_CONTEXT_RE.search(text):
            errors.append(f"{path.relative_to(root)}: local repository context is forbidden")

        for match in LINK_RE.finditer(visible):
            raw = match.group("target").strip("<>")
            parsed = urlsplit(raw)
            if parsed.scheme in {"http", "https", "mailto"}:
                continue
            if parsed.scheme or raw.startswith(("/", "//")):
                errors.append(f"{path.relative_to(root)}: invalid link {raw}")
                continue
            destination = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path.resolve()
            if destination.is_dir():
                destination /= "README.md"
            try:
                destination.relative_to(root)
            except ValueError:
                errors.append(f"{path.relative_to(root)}: link escapes repository: {raw}")
                continue
            if not destination.exists():
                errors.append(f"{path.relative_to(root)}: broken link {raw}")
                continue
            if destination.suffix.lower() == ".md":
                graph[path.resolve()].add(destination)
                if parsed.fragment and slug(parsed.fragment) not in headings(destination):
                    errors.append(f"{path.relative_to(root)}: broken anchor {raw}")

    reachable = {entry}
    queue = deque([entry])
    while queue:
        for target in graph.get(queue.popleft(), set()):
            if target not in reachable:
                reachable.add(target)
                queue.append(target)
    for path in files:
        if path.resolve() not in reachable:
            errors.append(f"{path.relative_to(root)}: orphan Markdown page")
    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    root = parser.parse_args().root
    errors = run(root)
    errors.extend(f"complexity-map: {error}" for error in check_complexity_relationships(root))
    errors = sorted(set(errors))
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"FAILED: {len(errors)} documentation error(s)")
        return 1
    print("OK: documentation tree is self-contained")
    return 0


if __name__ == "__main__":
    sys.exit(main())
