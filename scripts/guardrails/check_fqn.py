#!/usr/bin/env python3
"""Reject new fully-qualified type names in Java source (gate G2).

AGENTS.md: "In Java code, import types and use simple names; do not introduce fully qualified type
names in source." Only lines added relative to BASE are checked, so existing code is not re-litigated.

Ignored: `import` and `package` lines, comments (including Javadoc), and string or char literals.
A genuine simple-name clash is allowed with a trailing marker on the same line:

    java.util.List<String> names; // fqn: clashes with io.btrace.core.List

Usage:
  check_fqn.py BASE [HEAD]      check lines added in BASE...HEAD (HEAD defaults to the work tree)
  check_fqn.py --all            report every occurrence in tracked files (for measurement only)

An empty or all-zero BASE (a push or merge-queue event without a previous commit) checks nothing
and says so.
"""

import os
import re
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import CODE, NO_BASE, CheckError, run_git, strip_code  # noqa: E402

# A lower-case package chain of at least two segments followed by a capitalised type name,
# e.g. java.io.IOException, javax.management.ObjectName, io.btrace.core.BTraceUtils.
FQN_RE = re.compile(r"(?<![\w.@])((?:[a-z][a-z0-9_]*\.){2,}[A-Z]\w*)")
PACKAGE_ROOTS = ("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.", "io.", "com.", "net.")
MARKER_RE = re.compile(r"//\s*fqn:\s*\S")
def violations(line, state):
    code, state = strip_code(line, state)
    stripped = code.strip()
    if stripped.startswith(("import ", "package ")) or MARKER_RE.search(line):
        return [], state
    # A line inside a Javadoc/block comment that the diff shows without its opener.
    if stripped.startswith("*") and not stripped.startswith("*/"):
        return [], state
    found = [m for m in FQN_RE.findall(code) if m.startswith(PACKAGE_ROOTS)]
    return found, state


def added_lines(base, head):
    # Pin the diff format so user git config (diff.noprefix, diff.mnemonicPrefix, external diff or
    # textconv drivers) cannot change what the parser sees; run_git also sets core.quotepath=off.
    cmd = ["diff", "-U0", "--no-color", "--no-ext-diff", "--no-textconv",
           "--src-prefix=a/", "--dst-prefix=b/", "--diff-filter=AMR"]
    cmd += [base + "..." + head] if head else [base]
    diff = run_git(*cmd, "--", "*.java")
    path, line_no = None, 0
    for raw in diff.splitlines():
        if raw.startswith("+++ "):
            if raw == "+++ /dev/null":
                path = None
            elif raw.startswith("+++ b/"):
                path = raw[6:].rstrip("\t")
            else:
                raise ValueError("cannot map diff header to a file path: " + raw)
        elif raw.startswith("@@"):
            line_no = int(re.search(r"\+(\d+)", raw).group(1))
        elif raw.startswith("+") and path:
            yield path, line_no, raw[1:]
            line_no += 1


def all_lines():
    files = [p for p in run_git("ls-files", "--", "*.java").split("\n") if p]
    for path in files:
        with open(path, encoding="utf-8", errors="replace") as f:
            for no, line in enumerate(f, 1):
                yield path, no, line.rstrip("\n")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    if argv[1] != "--all" and argv[1] in NO_BASE:
        print("fqn: no base commit given; nothing checked")
        return 0
    lines = all_lines() if argv[1] == "--all" else added_lines(argv[1], argv[2] if len(argv) > 2 else None)
    errors, state, current = [], CODE, None
    try:
        for path, no, line in lines:
            if path != current:
                current, state = path, CODE
            found, state = violations(line, state)
            for fqn in found:
                errors.append("%s:%d: fully-qualified type %s; import it and use the simple name" % (path, no, fqn))
    except (ValueError, CheckError) as e:
        print("fqn: %s; the diff was not checked" % e)
        return 1
    for e in errors:
        print("fqn: " + e)
    if errors:
        print(
            "fqn: %d occurrence(s). AGENTS.md requires imports and simple names; for a genuine "
            "simple-name clash add '// fqn: <reason>' to the line." % len(errors)
        )
        return 1
    print("fqn: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
