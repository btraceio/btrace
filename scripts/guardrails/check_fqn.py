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
"""

import re
import subprocess
import sys

# A lower-case package chain of at least two segments followed by a capitalised type name,
# e.g. java.io.IOException, javax.management.ObjectName, io.btrace.core.BTraceUtils.
FQN_RE = re.compile(r"(?<![\w.@])((?:[a-z][a-z0-9_]*\.){2,}[A-Z]\w*)")
PACKAGE_ROOTS = ("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.", "io.", "com.", "net.")
MARKER_RE = re.compile(r"//\s*fqn:\s*\S")
CODE, BLOCK, TEXT = "code", "block", "text"


def strip_code(line, state):
    """Returns (the line's code without comments and literals, state after the line).

    state is CODE, BLOCK (inside /* */) or TEXT (inside a \"\"\" text block); both may span lines.
    """
    out, i, n = [], 0, len(line)
    while i < n:
        if state == BLOCK:
            end = line.find("*/", i)
            if end < 0:
                return "".join(out), BLOCK
            i, state = end + 2, CODE
        elif state == TEXT:
            end = line.find('"""', i)
            if end < 0:
                return "".join(out), TEXT
            i, state = end + 3, CODE
        elif line.startswith('"""', i):
            i, state = i + 3, TEXT
        elif line.startswith("/*", i):
            i, state = i + 2, BLOCK
        elif line.startswith("//", i):
            break
        elif line[i] in "\"'":
            quote, i = line[i], i + 1
            while i < n and line[i] != quote:
                i += 2 if line[i] == "\\" else 1
            i += 1
            out.append('""')
        else:
            out.append(line[i])
            i += 1
    return "".join(out), state


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
    cmd = ["git", "diff", "-U0", "--no-color", "--diff-filter=AM"]
    cmd += [base + "..." + head] if head else [base]
    cmd += ["--", "*.java"]
    diff = subprocess.run(cmd, check=True, capture_output=True, text=True).stdout
    path, line_no = None, 0
    for raw in diff.splitlines():
        if raw.startswith("+++ "):
            path = raw[6:] if raw.startswith("+++ b/") else None
        elif raw.startswith("@@"):
            line_no = int(re.search(r"\+(\d+)", raw).group(1))
        elif raw.startswith("+") and path:
            yield path, line_no, raw[1:]
            line_no += 1


def all_lines():
    files = subprocess.run(
        ["git", "ls-files", "--", "*.java"], check=True, capture_output=True, text=True
    ).stdout.split()
    for path in files:
        with open(path, encoding="utf-8", errors="replace") as f:
            for no, line in enumerate(f, 1):
                yield path, no, line.rstrip("\n")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    lines = all_lines() if argv[1] == "--all" else added_lines(argv[1], argv[2] if len(argv) > 2 else None)
    errors, state, current = [], CODE, None
    for path, no, line in lines:
        if path != current:
            current, state = path, CODE
        found, state = violations(line, state)
        for fqn in found:
            errors.append("%s:%d: fully-qualified type %s; import it and use the simple name" % (path, no, fqn))
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
