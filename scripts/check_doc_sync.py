#!/usr/bin/env python3
"""
Project doc sync guard.

Checks:
1) DB migration latest version is referenced in README docs.
2) Backend controller API endpoints are in docs/api.md (bidirectional).
3) WebSocket endpoint path exists in docs/api.md.
4) Required docs exist and include "最后同步日期".

Usage:
  python scripts/check_doc_sync.py
"""

from __future__ import annotations

import re
from pathlib import Path
import sys
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS_DIR = REPO_ROOT / "backend" / "docs" / "db" / "migrations"
README_DOC_FILES = [
    REPO_ROOT / "README.md",
    REPO_ROOT / "backend" / "README.md",
]
API_DOC_FILE = REPO_ROOT / "docs" / "api.md"
REQUIRED_DOCS = [
    REPO_ROOT / "docs" / "README.md",
    REPO_ROOT / "docs" / "api.md",
    REPO_ROOT / "docs" / "architecture.md",
    REPO_ROOT / "docs" / "db.md",
    REPO_ROOT / "docs" / "experiments.md",
    REPO_ROOT / "docs" / "thesis_notes.md",
]
CONTROLLERS_DIR = REPO_ROOT / "backend" / "src" / "main" / "java" / "com" / "wuhao" / "aiemotion" / "controller"
WEBSOCKET_CONFIG_FILE = (
    REPO_ROOT
    / "backend"
    / "src"
    / "main"
    / "java"
    / "com"
    / "wuhao"
    / "aiemotion"
    / "config"
    / "WebSocketConfig.java"
)

CLASS_MAPPING_RE = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"')
METHOD_MAPPING_RE = re.compile(
    r'@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?"([^"]+)"',
    re.IGNORECASE,
)
METHOD_NOARG_RE = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping\s*$', re.IGNORECASE)
DOC_ENDPOINT_RE = re.compile(r"`(GET|POST|PUT|DELETE|PATCH)\s+(/api[^`\s]*)`")
PATH_VAR_REGEX_RE = re.compile(r"\{([a-zA-Z_]\w*):[^}]+\}")
WS_PATH_RE = re.compile(r'addHandler\([^,]+,\s*"([^"]+)"\)')
LAST_SYNC_RE = re.compile(r"最后同步日期")


def find_latest_migration() -> tuple[int, str]:
    pattern = re.compile(r"^V(\d+)__.+\.sql$")
    latest_version = -1
    latest_name = ""
    for file in MIGRATIONS_DIR.glob("V*__*.sql"):
        match = pattern.match(file.name)
        if not match:
            continue
        version = int(match.group(1))
        if version > latest_version:
            latest_version = version
            latest_name = file.name
    if latest_version < 0:
        raise RuntimeError(f"No migration files found in {MIGRATIONS_DIR}")
    return latest_version, latest_name


def check_readme_migration_refs(latest_version: int, latest_name: str) -> list[str]:
    issues: list[str] = []
    version_tag = f"V{latest_version}"
    for doc in README_DOC_FILES:
        if not doc.exists():
            issues.append(f"{doc.relative_to(REPO_ROOT)} does not exist.")
            continue
        text = doc.read_text(encoding="utf-8")
        has_version = version_tag in text
        has_filename = latest_name in text
        if not (has_version and has_filename):
            issues.append(
                f"{doc.relative_to(REPO_ROOT)} is missing latest migration reference "
                f"({latest_name})."
            )
    return issues


def normalize_path(path: str) -> str:
    path = path.strip()
    path = PATH_VAR_REGEX_RE.sub(r"{\1}", path)
    path = re.sub(r"/{2,}", "/", path)
    return path


def join_mapping(base_path: str, method_path: str | None) -> str:
    if method_path is None:
        return normalize_path(base_path)
    if method_path.startswith("/api"):
        return normalize_path(method_path)
    if not base_path:
        return normalize_path(method_path)
    if method_path.startswith("/"):
        return normalize_path(base_path.rstrip("/") + method_path)
    return normalize_path(base_path.rstrip("/") + "/" + method_path)


def parse_controller_endpoints() -> set[tuple[str, str]]:
    if not CONTROLLERS_DIR.exists():
        raise RuntimeError(f"Controller directory not found: {CONTROLLERS_DIR}")

    endpoints: set[tuple[str, str]] = set()
    for java_file in CONTROLLERS_DIR.glob("*.java"):
        lines = java_file.read_text(encoding="utf-8").splitlines()
        base_path = ""
        for line in lines:
            class_match = CLASS_MAPPING_RE.search(line)
            if class_match:
                base_path = class_match.group(1).strip()
                break

        for raw_line in lines:
            line = raw_line.strip()
            method_match = METHOD_MAPPING_RE.search(line)
            if method_match:
                method = method_match.group(1).upper()
                method_path = method_match.group(2).strip()
                full_path = join_mapping(base_path, method_path)
                if full_path.startswith("/api"):
                    endpoints.add((method, full_path))
                continue

            noarg_match = METHOD_NOARG_RE.search(line)
            if noarg_match and base_path:
                method = noarg_match.group(1).upper()
                full_path = join_mapping(base_path, None)
                if full_path.startswith("/api"):
                    endpoints.add((method, full_path))
    return endpoints


def parse_doc_api_endpoints() -> set[tuple[str, str]]:
    if not API_DOC_FILE.exists():
        raise RuntimeError(f"Missing API doc file: {API_DOC_FILE}")
    text = API_DOC_FILE.read_text(encoding="utf-8")
    return {
        (match.group(1), normalize_path(match.group(2)))
        for match in DOC_ENDPOINT_RE.finditer(text)
    }


def check_api_doc_coverage() -> list[str]:
    code_endpoints = parse_controller_endpoints()
    doc_endpoints = parse_doc_api_endpoints()

    missing_in_doc = sorted(code_endpoints - doc_endpoints)
    stale_in_doc = sorted(doc_endpoints - code_endpoints)

    issues: list[str] = []
    if missing_in_doc:
        issues.append("docs/api.md is missing endpoints:")
        issues.extend([f"  - {method} {path}" for method, path in missing_in_doc])
    if stale_in_doc:
        issues.append("docs/api.md has stale endpoints not found in code:")
        issues.extend([f"  - {method} {path}" for method, path in stale_in_doc])
    return issues


def check_ws_doc_coverage() -> list[str]:
    if not WEBSOCKET_CONFIG_FILE.exists():
        return [f"Missing WebSocket config file: {WEBSOCKET_CONFIG_FILE.relative_to(REPO_ROOT)}"]
    if not API_DOC_FILE.exists():
        return [f"Missing API doc file: {API_DOC_FILE.relative_to(REPO_ROOT)}"]

    ws_text = WEBSOCKET_CONFIG_FILE.read_text(encoding="utf-8")
    api_text = API_DOC_FILE.read_text(encoding="utf-8")

    issues: list[str] = []
    ws_paths = {normalize_path(m.group(1)) for m in WS_PATH_RE.finditer(ws_text)}
    if not ws_paths:
        issues.append("No WebSocket handler path found in WebSocketConfig.java")
        return issues

    for ws_path in sorted(ws_paths):
        if ws_path not in api_text:
            issues.append(f"docs/api.md is missing websocket path: {ws_path}")
    return issues


def check_required_docs() -> list[str]:
    issues: list[str] = []
    for doc in REQUIRED_DOCS:
        if not doc.exists():
            issues.append(f"Missing required doc file: {doc.relative_to(REPO_ROOT)}")
            continue
        text = doc.read_text(encoding="utf-8")
        if not LAST_SYNC_RE.search(text):
            issues.append(f"{doc.relative_to(REPO_ROOT)} is missing '最后同步日期'.")
    return issues


def flatten(items: Iterable[list[str]]) -> list[str]:
    merged: list[str] = []
    for group in items:
        merged.extend(group)
    return merged


def main() -> int:
    latest_version, latest_name = find_latest_migration()
    issues = flatten(
        [
            check_readme_migration_refs(latest_version, latest_name),
            check_required_docs(),
            check_api_doc_coverage(),
            check_ws_doc_coverage(),
        ]
    )
    if issues:
        print("Doc sync check failed:")
        for issue in issues:
            print(f"- {issue}")
        return 1
    print("Doc sync check passed:")
    print(f"- latest migration referenced: {latest_name}")
    print("- API endpoints match docs/api.md")
    print("- WebSocket endpoint documented")
    print("- required docs present with sync date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
