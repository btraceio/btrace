#!/usr/bin/env python3
"""
Strip Oracle GPL / Sun BSD copyright headers from Java source files.
After running this, `./gradlew spotlessApply` will insert the new header.

Usage:
    python3 scripts/replace_oracle_headers.py [--dry-run]
"""
import argparse
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).parent.parent

# Match the leading block comment that contains Oracle or Sun copyright
HEADER_PATTERN = re.compile(
    r"^/\*.*?(?:Oracle and/or its affiliates|Sun Microsystems).*?\*/\s*",
    re.DOTALL,
)

def strip_header(text: str) -> tuple[str, bool]:
    m = HEADER_PATTERN.match(text)
    if m:
        return text[m.end():], True
    return text, False

def process(dry_run: bool) -> int:
    changed = 0
    for java_file in REPO_ROOT.rglob("*.java"):
        # Skip build output directories
        if any(part in ("build", ".gradle") for part in java_file.parts):
            continue
        content = java_file.read_text(encoding="utf-8")
        stripped, did_strip = strip_header(content)
        if did_strip:
            changed += 1
            if dry_run:
                print(f"[dry-run] would strip: {java_file.relative_to(REPO_ROOT)}")
            else:
                java_file.write_text(stripped, encoding="utf-8")
                print(f"stripped: {java_file.relative_to(REPO_ROOT)}")
    print(f"\n{'Would modify' if dry_run else 'Modified'} {changed} files.")
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    sys.exit(process(args.dry_run))
