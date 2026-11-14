#!/usr/bin/env python3
"""Derive the ASM-only files of the instrumentation backend (input to check_pr_metadata.py).

Starts from the two seed files, Instrumentor.java and AsmInstrumentationBackend.java, and adds
every other main-source file in io/btrace/instr whose simple name is referenced (outside comments)
only from files already in the set. A file that backend-neutral code or the ClassFile API backend
(btrace-agent/src/main/java24/) also names stays out; that is why Assembler.java and
BTraceClassWriter.java are not listed.

Usage: python3 -I scripts/guardrails/derive_asm_backend.py   (prints one repository-relative path per line)
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INSTR_DIR = "btrace-agent/src/main/java/io/btrace/instr/"
SEEDS = ("Instrumentor", "AsmInstrumentationBackend")


def strip_comments(text):
    return re.sub(r"//.*", "", re.sub(r"/\*.*?\*/", "", text, flags=re.S))


def derive(root=ROOT):
    files = subprocess.check_output(
        ["git", "ls-files", "*.java"], cwd=root, text=True
    ).split("\n")
    sources = {}
    for path in files:
        if path and "/src/test/" not in path:
            with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
                sources[path] = strip_comments(f.read())
    names = sorted(
        os.path.basename(p)[:-5]
        for p in sources
        if p.startswith(INSTR_DIR) and "/" not in p[len(INSTR_DIR):]
    )
    members = set(SEEDS)
    grew = True
    while grew:
        grew = False
        for name in names:
            if name in members:
                continue
            word = re.compile(r"\b%s\b" % name)
            referrers = [
                p for p, text in sources.items()
                if os.path.basename(p)[:-5] != name and word.search(text)
            ]
            if referrers and all(
                p.startswith(INSTR_DIR) and os.path.basename(p)[:-5] in members for p in referrers
            ):
                members.add(name)
                grew = True
    return sorted(INSTR_DIR + n + ".java" for n in members)


if __name__ == "__main__":
    print("\n".join(derive()))
    sys.exit(0)
