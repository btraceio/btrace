#!/usr/bin/env python3
"""btrace-runtime never calls `Thread.interrupted()` (gate G11, application-thread safety).

`Thread.interrupted()` returns the interrupt flag and clears it. Probe code runs on application
threads, so calling it there loses an interrupt the application was waiting for;
`Thread.currentThread().isInterrupted()` reads the flag without clearing it and is fine.

Every `*.java` file under btrace-runtime/src/main (all source sets: java, java9, java11, ...) is
read, comments and string/char literals are removed, and these are reported:

  Thread.interrupted()        any spelling of the call, including `java.lang.Thread . interrupted ( )`
  interrupted()               a bare call, when the same file has `import static java.lang.Thread.interrupted;`
                              or `import static java.lang.Thread.*;`
  Thread::interrupted         the method reference, with any whitespace or fully qualified

Each of these is an error, never a pass: btrace-runtime/src/main is missing, it holds no Java file,
or a file cannot be read as UTF-8.

Usage:
  check_runtime_safety.py [ROOT]
"""

import os
import re
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import CheckError, read_code  # noqa: E402

SOURCE_ROOT = "btrace-runtime/src/main"

QUALIFIED_RE = re.compile(r"(?<![\w$])Thread\s*\.\s*interrupted\s*\(\s*\)")
STATIC_IMPORT_RE = re.compile(
    r"\bimport\s+static\s+java\s*\.\s*lang\s*\.\s*Thread\s*\.\s*(?:interrupted|\*)\s*;"
)
BARE_RE = re.compile(r"(?<![\w$.])interrupted\s*\(\s*\)")
METHOD_REF_RE = re.compile(r"(?<![\w$])Thread\s*::\s*interrupted(?![\w$])")


def java_files(root):
    base = os.path.join(root, SOURCE_ROOT)
    if not os.path.isdir(base):
        raise CheckError("%s does not exist, so there is nothing to check" % SOURCE_ROOT)
    found = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames.sort()
        for name in sorted(filenames):
            if name.endswith(".java"):
                found.append(os.path.join(dirpath, name))
    if not found:
        raise CheckError("no .java file under %s, so there is nothing to check" % SOURCE_ROOT)
    return found


def line_of(code, index):
    return code.count("\n", 0, index) + 1


def check(root):
    """Returns (file count, problems)."""
    files = java_files(root)
    problems = []
    for path in files:
        rel = os.path.relpath(path, root).replace(os.sep, "/")
        code = read_code(path, rel)
        hits = [m.start() for m in QUALIFIED_RE.finditer(code)]
        if STATIC_IMPORT_RE.search(code):
            hits += [m.start() for m in BARE_RE.finditer(code)]
        for index in sorted(hits):
            problems.append("%s:%d calls Thread.interrupted()" % (rel, line_of(code, index)))
        for m in METHOD_REF_RE.finditer(code):
            problems.append("%s:%d references Thread::interrupted" % (rel, line_of(code, m.start())))
    return len(files), problems


def main(argv):
    root = argv[1] if len(argv) > 1 else "."
    try:
        count, problems = check(root)
    except CheckError as e:
        print("runtime-safety: " + str(e))
        return 1
    for p in problems:
        print("runtime-safety: " + p)
    if problems:
        print(
            "runtime-safety: %d problem(s). Thread.interrupted() clears the application's interrupt "
            "flag; use Thread.currentThread().isInterrupted() (docs/architecture/DesignPrinciples.md, P6)."
            % len(problems)
        )
        return 1
    print("runtime-safety: OK (%d files)" % count)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
