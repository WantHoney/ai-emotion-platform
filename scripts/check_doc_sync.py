#!/usr/bin/env python3
"""
Check whether DB migration docs are aligned with the latest migration version.

Usage:
  python scripts/check_doc_sync.py
"""

from __future__ import annotations

import re
from pathlib import Path
import sys


REPO_ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS_DIR = REPO_ROOT / "backend" / "docs" / "db" / "migrations"
DOC_FILES = [
    REPO_ROOT / "README.md",
    REPO_ROOT / "backend" / "README.md",
]


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


def check_docs(latest_version: int, latest_name: str) -> list[str]:
    issues: list[str] = []
    version_tag = f"V{latest_version}"
    for doc in DOC_FILES:
        text = doc.read_text(encoding="utf-8")
        has_version = version_tag in text
        has_filename = latest_name in text
        if not (has_version and has_filename):
            issues.append(
                f"{doc.relative_to(REPO_ROOT)} is missing latest migration reference "
                f"({latest_name})."
            )
    return issues


def main() -> int:
    latest_version, latest_name = find_latest_migration()
    issues = check_docs(latest_version, latest_name)
    if issues:
        print("Doc sync check failed:")
        for issue in issues:
            print(f"- {issue}")
        return 1
    print(
        "Doc sync check passed: latest migration "
        f"{latest_name} is present in README docs."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
