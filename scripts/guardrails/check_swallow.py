#!/usr/bin/env python3
"""Reject new catch blocks that swallow a failure silently (gate G10, principle P5).

A violation is a `catch` of Throwable, Exception or RuntimeException (alone or in a multi-catch)
whose block is empty, or whose statements are all `log.debug` / `log.trace` calls or a bare
`return;`, `return null;`, `return false;`, `continue;` or `break;` -- and where the `catch`
header or any line of the block is an added line, or lines were deleted from inside the block.
The whole file is read at HEAD, so a block whose header is unchanged but whose body was edited
(or the reverse) is still attributed correctly. Existing blocks are not re-litigated.

Scope: Java files under `*/src/main/` (every source set). Comments and string or char literals
are ignored when parsing. A deliberate swallow is allowed with a reason on the `catch` line:

    } catch (Throwable t) { // swallow: the probe must never fail the target application

Usage:
  check_swallow.py BASE [HEAD]  check blocks touched in BASE...HEAD (HEAD defaults to the work tree)
  check_swallow.py --all        report every such block in tracked files (measurement only)

An empty or all-zero BASE (a push or merge-queue event without a previous commit) checks nothing
and says so.
"""

import os
import re
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import NO_BASE, CheckError, run_git, strip_lines  # noqa: E402

SCOPE_RE = re.compile(r"(^|/)src/main/.+\.java$")
CATCH_RE = re.compile(r"\bcatch\s*\(([^)]*)\)\s*\{")
BROAD = {"Throwable", "Exception", "RuntimeException"}
MARKER_RE = re.compile(r"//\s*swallow:\s*\S")
LOG_RE = re.compile(r"^(?:\w+\.)*\w*[Ll][Oo][Gg]\w*\.(?:debug|trace)\s*\(.*\)$", re.DOTALL)
BARE_RE = re.compile(r"^(?:return(?:\s+(?:null|false))?|continue|break)$")


def is_broad(params):
    """True when a catch parameter list names a broad type; the last token is the variable."""
    tokens = params.split()
    types = " ".join(tokens[:-1])
    return any(t.split(".")[-1] in BROAD for t in re.findall(r"[\w.]+", types))


def swallows(body):
    """True when every statement of the block body is a debug/trace log call or a bare jump."""
    statements = [s.strip() for s in body.split(";")]
    if statements and statements[-1] == "":
        statements.pop()
    return all(LOG_RE.match(s) or BARE_RE.match(s) for s in statements)


def swallowed_blocks(text):
    """Yields (first line, last line) of each swallowing broad catch.

    A broad catch whose braces do not balance cannot be classified; it is yielded as
    (first line, None) so the caller reports it instead of skipping it.
    """
    raw = text.split("\n")
    code = strip_lines(raw)
    flat = "\n".join(code)
    starts, pos = [], 0
    for line in code:
        starts.append(pos)
        pos += len(line) + 1

    def line_of(offset):
        lo, hi = 0, len(starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if starts[mid] <= offset:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    for m in CATCH_RE.finditer(flat):
        if not is_broad(m.group(1)):
            continue
        depth, i = 1, m.end()
        while i < len(flat) and depth:
            depth += {"{": 1, "}": -1}.get(flat[i], 0)
            i += 1
        if depth:
            yield line_of(m.start()), None
            continue
        if not swallows(flat[m.end() : i - 1]):
            continue
        first, header_end, last = line_of(m.start()), line_of(m.end() - 1), line_of(i - 1)
        if any(MARKER_RE.search(raw[n - 1]) for n in range(first, header_end + 1)):
            continue
        yield first, last


def touched(diff):
    """Parses `git diff -U0` into {path: (added line numbers, deletion gaps as n + 0.5)}."""
    out, path = {}, None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            if line == "+++ /dev/null":
                path = None
                continue
            if not line.startswith("+++ b/"):
                raise ValueError("cannot map diff header to a file path: " + line)
            path = line[6:].rstrip("\t")
            out.setdefault(path, (set(), set()))
        elif line.startswith("@@") and path:
            m = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", line)
            start, count = int(m.group(1)), int(m.group(2) if m.group(2) is not None else 1)
            added, gaps = out[path]
            if count:
                added.update(range(start, start + count))
            else:
                gaps.add(start + 0.5)  # lines deleted between `start` and `start + 1`
    return out


def check_diff(base, head):
    # Pin the diff format so user git config (diff.noprefix, diff.mnemonicPrefix, external diff or
    # textconv drivers) cannot change what the parser sees; run_git also sets core.quotepath=off.
    cmd = ["diff", "-U0", "--no-color", "--no-ext-diff", "--no-textconv",
           "--src-prefix=a/", "--dst-prefix=b/", "--diff-filter=AMR"]
    cmd += [base + "..." + head] if head else [base]
    changes = touched(run_git(*cmd, "--", "*.java"))
    for path in sorted(changes):
        if not SCOPE_RE.search(path):
            continue
        added, gaps = changes[path]
        if head:
            text = run_git("show", head + ":" + path)
        else:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read()
        marks = added | gaps
        for first, last in swallowed_blocks(text):
            if last is None:
                yield path, first, False
            elif any(first <= n <= last for n in marks):
                yield path, first, True


def check_all():
    for path in run_git("ls-files", "--", "*.java").split("\n"):
        if path and SCOPE_RE.search(path):
            with open(path, encoding="utf-8", errors="replace") as f:
                for first, last in swallowed_blocks(f.read()):
                    yield path, first, last is not None


def module_of(path):
    return path.split("/src/main/")[0]


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    everything = argv[1] == "--all"
    if not everything and argv[1] in NO_BASE:
        print("swallow: no base commit given; nothing checked")
        return 0
    try:
        found = list(check_all() if everything else check_diff(argv[1], argv[2] if len(argv) > 2 else None))
    except (ValueError, CheckError) as e:
        print("swallow: %s; the diff was not checked" % e)
        return 1
    for path, no, parsed in found:
        if parsed:
            print("swallow: %s:%d: catch swallows the failure; log at warn or above, rethrow, or add "
                  "'// swallow: <reason>' to the catch line" % (path, no))
        else:
            print("swallow: %s:%d: could not parse this catch block (unbalanced braces); "
                  "it was not checked" % (path, no))
    if everything:
        per_module = {}
        for path, _, _ in found:
            per_module[module_of(path)] = per_module.get(module_of(path), 0) + 1
        for module in sorted(per_module):
            print("swallow: %s: %d" % (module, per_module[module]))
    if found:
        print("swallow: %d swallowed failure(s). P5: a dropped handler, failed transform or ignored "
              "option is logged at warn or above and reaches the client." % len(found))
        return 1
    print("swallow: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
