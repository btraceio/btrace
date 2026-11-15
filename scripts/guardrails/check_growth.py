#!/usr/bin/env python3
"""Growth ratchets for main-source Java (gate G13, principle P8).

Three rules, each reported on its own line:

  size       A `*/src/main/**/*.java` file (every source set, nested modules included) with more
             than 1,000 lines at HEAD fails when it is new or longer than at the base, unless it is
             listed in scripts/guardrails/growth_allow.txt as `<path>  # reason`. Renames are
             followed, so a renamed file that did not grow passes; a file that crosses the limit in
             this change fails. Allowlist entries whose file is gone or no longer over the limit are
             stale and fail. Lines are counted the same way at the base and at HEAD.
  api        The sorted public top-level types (class, interface, enum, @interface) declared under
             btrace-core/src/main/java must equal scripts/guardrails/api/btrace-core-public-types.txt.
  test-hook  A method declaration named `...ForTest` or `...ForTests` in main sources fails unless it
             is listed in scripts/guardrails/baselines/main-test-hooks.txt as
             `test-hook <path>:<method>  # reason`; a second declaration of the same name in a file
             (an overload) is `<method>#2`, a third `<method>#3`.

Usage: check_growth.py ROOT BASE [HEAD]

ROOT is the repository root. HEAD defaults to the work tree. An empty or all-zero BASE (a push or
merge-queue event without a previous commit) skips the size comparison and says so; the api and
test-hook rules need no base and still run.
"""

import os
import re
import subprocess
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import _baseline  # noqa: E402
from _common import NO_BASE, CheckError, code_of, run_git  # noqa: E402

LIMIT = 1000
MAIN_RE = re.compile(r"(^|/)src/main/.+\.java$")
CORE_SRC = "btrace-core/src/main/java"
ALLOW = "scripts/guardrails/growth_allow.txt"
SNAPSHOT = "scripts/guardrails/api/btrace-core-public-types.txt"
HOOKS = "scripts/guardrails/baselines/main-test-hooks.txt"

PACKAGE_RE = re.compile(r"\bpackage\s+([\w.]+)\s*;")
TYPE_RE = re.compile(
    r"(?<![\w@])public\s+(?:(?:abstract|final|strictfp|static)\s+)*(?:class|interface|enum|@\s*interface)\s+(\w+)"
)
HOOK_RE = re.compile(r"([\w>\]]+)\s+(\w*ForTests?)\s*\(")
NOT_A_TYPE = {"return", "new", "else", "throw", "case", "assert", "yield", "do"}


class Source:
    """Java files of the repository at HEAD (a commit) or in the work tree."""

    def __init__(self, root, head):
        self.root, self.head = root, head

    def paths(self):
        if self.head:
            out = run_git("ls-tree", "-r", "-z", "--name-only", self.head, root=self.root, binary=True)
        else:
            out = run_git("ls-files", "-z", "--cached", "--others", "--exclude-standard", root=self.root, binary=True)
        names = sorted({p.decode("utf-8", "replace") for p in out.split(b"\0") if p})
        return [p for p in names if p.endswith(".java")]

    def read_all(self, paths):
        """Returns {path: text}; raises CheckError for a file that cannot be read."""
        if not self.head:
            return {p: self._read_file(p) for p in paths}
        proc = subprocess.run(
            ["git", "-C", self.root, "cat-file", "--batch"],
            input="".join("%s:%s\n" % (self.head, p) for p in paths).encode("utf-8"),
            capture_output=True,
        )
        data, pos, out = proc.stdout, 0, {}
        for p in paths:
            nl = data.find(b"\n", pos)
            header = data[pos:nl].decode("utf-8", "replace").split() if nl >= 0 else []
            if len(header) != 3 or header[1] != "blob":
                raise CheckError("cannot read %s at %s" % (p, self.head))
            size = int(header[2])
            out[p] = data[nl + 1 : nl + 1 + size].decode("utf-8", "replace")
            pos = nl + 1 + size + 1
        return out

    def _read_file(self, path):
        try:
            with open(os.path.join(self.root, path), "rb") as f:
                return f.read().decode("utf-8", "replace")
        except OSError as e:
            raise CheckError("cannot read %s: %s" % (path, e))


def count_lines(text):
    """Number of lines; a final line without a newline counts, so base and HEAD agree."""
    return text.count("\n") + (1 if text and not text.endswith("\n") else 0)


def changes(root, base, head):
    """Returns {head path: (status, base path)} for added, modified and renamed Java files."""
    cmd = ["diff", "--name-status", "-z", "-M", "--no-color", "--no-ext-diff", "--no-textconv",
           "--diff-filter=AMR"]
    cmd += [base + "..." + head] if head else [base]
    fields = [f for f in run_git(*cmd, "--", "*.java", root=root, binary=True).split(b"\0") if f]
    out, i = {}, 0
    while i < len(fields):
        status = fields[i].decode("utf-8", "replace")
        kind = status[:1]
        if kind in "AM" and i + 1 < len(fields):
            path = fields[i + 1].decode("utf-8", "replace")
            out[path] = (kind, path)
            i += 2
        elif kind == "R" and i + 2 < len(fields):
            out[fields[i + 2].decode("utf-8", "replace")] = ("R", fields[i + 1].decode("utf-8", "replace"))
            i += 3
        else:
            raise CheckError("cannot map diff entry %r to a file path" % status)
    return out


def check_size(root, source, texts, base, head):
    """Returns (errors, message when the rule could not compare)."""
    lengths = {p: count_lines(t) for p, t in texts.items() if MAIN_RE.search(p)}
    over = {p for p, n in lengths.items() if n > LIMIT}
    errors = []
    allowed = _baseline.load(os.path.join(root, ALLOW))
    for entry in allowed:
        if entry not in lengths:
            errors.append("stale allowlist entry (file is gone; remove it from %s): %s" % (ALLOW, entry))
        elif entry not in over:
            errors.append("stale allowlist entry (file is no longer over %d lines; remove it from %s): %s"
                          % (LIMIT, ALLOW, entry))
    if base in NO_BASE:
        return errors, "growth: no base commit given; file sizes not compared"
    changed = changes(root, base, head)
    base_ref = run_git("merge-base", base, head, root=root).strip() if head else base
    for path in sorted(over - set(allowed)):
        if path not in changed:
            continue
        status, old = changed[path]
        if status == "A":
            errors.append("%s: new file has %d lines (limit %d)" % (path, lengths[path], LIMIT))
            continue
        try:
            before = count_lines(run_git("show", "%s:%s" % (base_ref, old), root=root))
        except CheckError as e:
            raise CheckError("cannot read %s at the base commit: %s" % (old, e))
        if lengths[path] > before:
            errors.append("%s: grew from %d to %d lines (limit %d)" % (path, before, lengths[path], LIMIT))
    return errors, None


def public_types(source):
    """Returns the sorted fully-qualified public top-level types under btrace-core."""
    paths = [p for p in source.paths() if p.startswith(CORE_SRC + "/")]
    if not paths:
        raise CheckError("no Java sources found under %s; the public API cannot be listed" % CORE_SRC)
    found = set()
    for path, text in source.read_all(paths).items():
        flat = code_of(text)
        package = PACKAGE_RE.search(flat)
        depth, pos = 0, 0
        for m in TYPE_RE.finditer(flat):
            depth += flat.count("{", pos, m.start()) - flat.count("}", pos, m.start())
            pos = m.start()
            if depth == 0:
                found.add((package.group(1) + "." if package else "") + m.group(1))
    if not found:
        raise CheckError("no public top-level types found under %s; the parser matched nothing" % CORE_SRC)
    return sorted(found)


def check_api(root, source):
    found = public_types(source)
    snapshot_path = os.path.join(root, SNAPSHOT)
    if not os.path.isfile(snapshot_path):
        raise CheckError("snapshot %s is missing" % SNAPSHOT)
    with open(snapshot_path, encoding="utf-8") as f:
        listed = [line.strip() for line in f if line.strip()]
    if not listed:
        raise CheckError("snapshot %s is empty" % SNAPSHOT)
    hint = "; update %s in the same change" % SNAPSHOT
    errors = ["public type added: %s%s" % (t, hint) for t in sorted(set(found) - set(listed))]
    errors += ["public type removed or no longer public: %s%s" % (t, hint) for t in sorted(set(listed) - set(found))]
    return errors, len(found)


def test_hooks(texts):
    """Returns sorted `test-hook <path>:<method>[#n]` entries, one per ForTest(s) declaration."""
    found = set()
    for path, text in texts.items():
        if not MAIN_RE.search(path):
            continue
        seen = {}
        for m in HOOK_RE.finditer(code_of(text)):
            if m.group(1) not in NOT_A_TYPE:
                seen[m.group(2)] = seen.get(m.group(2), 0) + 1
                suffix = "" if seen[m.group(2)] == 1 else "#%d" % seen[m.group(2)]
                found.add("test-hook %s:%s%s" % (path, m.group(2), suffix))
    return sorted(found)


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    root, base, head = argv[1], argv[2], (argv[3] if len(argv) > 3 else None)
    try:
        source = Source(root, head)
        main_paths = [p for p in source.paths() if MAIN_RE.search(p)]
        if not main_paths:
            raise CheckError("no main-source Java files found; nothing to check")
        texts = source.read_all(main_paths)
        results = []
        size_errors, size_note = check_size(root, source, texts, base, head)
        results.append(("size", size_errors, size_note or "OK"))
        api_errors, count = check_api(root, source)
        results.append(("api", api_errors, "OK (%d public types)" % count))
        hook_errors = _baseline.compare(test_hooks(texts), os.path.join(root, HOOKS))
        results.append(("test-hook", hook_errors, "OK"))
    except CheckError as e:
        print("growth: %s; nothing was checked" % e)
        return 1
    failed = 0
    for rule, errors, ok in results:
        for e in errors:
            print("growth: %s: %s" % (rule, e))
        failed += len(errors)
        if ok.startswith("growth:"):
            print(ok)  # a rule that could not compare says so even when other findings exist
        elif not errors:
            print("growth: %s %s" % (rule, ok))
    if failed:
        print("growth: %d violation(s). P8: oversized classes do not grow, the btrace-core public API "
              "changes deliberately, and test hooks stay out of main sources." % failed)
        return 1
    if base not in NO_BASE:
        print("growth: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
