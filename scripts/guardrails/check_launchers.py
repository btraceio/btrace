#!/usr/bin/env python3
"""Keep each shell launcher and its .bat twin in step (gate G9, launchers).

For every `btrace-dist/src/main/resources/bin/<name>` the check reads `<name>` and `<name>.bat` as
text and compares

  flags       the JVM flags: tokens starting `-X`, `-XX:`, `-D` or `--add-exports`/`--add-opens`
              (the module and package argument is part of the flag). Flags are found anywhere in the
              script, including inside a variable such as JAVA_ARGS that is expanded on the java line.
  main class  the class named on each java command line (the first non-option argument after the
              classpath), so a launcher that starts another class is reported.

Line endings are normalised, `\\` (shell) and `^` (batch) continuations are joined, comment lines
(`#`, `rem`, `::`) and `echo` lines are ignored. Conditions around a flag (`if [ -f .../lib/modules ]`)
are not evaluated: a flag guarded on one side and unguarded on the other is not seen.

Differences that predate the rule live in baselines/launchers.txt (see _baseline.py), as
`launcher-flag bin/<name>:<flag>:missing-in-<sh|bat>` and `launcher-main bin/<name>:sh=<classes>:bat=<classes>`.

These are errors on their own and cannot be baselined, because nothing was compared: a script without
a twin, a file that cannot be read as UTF-8, a script with no java command line, and a script
from which no JVM flag at all could be read.

Usage:
  check_launchers.py [ROOT] [--baseline FILE]
"""

import argparse
import os
import re
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import _baseline  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_BASELINE = os.path.join(HERE, "baselines", "launchers.txt")
BIN_DIR = "btrace-dist/src/main/resources/bin"

FLAG_RE = re.compile(
    r"""(?<![\w-])(
        --add-(?:exports|opens)(?:=|\s+)[^\s"'=]+=?[^\s"']*
        | -XX:[^\s"']+
        | -X[A-Za-z][^\s"']*
        | -D[A-Za-z_][^\s"']*
    )""",
    re.VERBOSE,
)
JAVA_CMD_RE = re.compile(r"[/\\]bin[/\\]java(?:\.exe)?\b|(?:^|\s)java\s")
CLASS_RE = re.compile(r"^[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.[A-Z][\w$]*$")
OPTIONS_WITH_VALUE = ("-cp", "-classpath", "--class-path", "-p", "--module-path")


def logical_lines(text, continuation):
    """Splits text into lines, dropping CRs and joining lines that end in the continuation character."""
    lines, current = [], ""
    for raw in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        stripped = raw.rstrip()
        if stripped.endswith(continuation):
            current += stripped[: -len(continuation)] + " "
            continue
        lines.append(current + raw)
        current = ""
    if current:
        lines.append(current)
    return lines


def is_ignored(line, is_batch):
    s = line.strip()
    if not s:
        return True
    low = s.lower()
    if is_batch:
        return low.startswith("rem ") or low == "rem" or s.startswith("::") or low.startswith("echo")
    return s.startswith("#") or low.startswith("echo ") or low.startswith("echo\t")


def normalize_flag(flag):
    flag = flag.rstrip("\"'")
    m = re.match(r"^(--add-(?:exports|opens))(?:=|\s+)(.*)$", flag)
    return m.group(1) + " " + m.group(2) if m else flag


def main_class(line):
    """Returns the main class of a java command line, or None (e.g. a -javaagent launch)."""
    tokens = [t.strip("\"'") for t in line.split()]
    for i, t in enumerate(tokens):
        if JAVA_CMD_RE.search(" " + t) and re.search(r"(?:^|[/\\])java(?:\.exe)?$", t):
            tokens = tokens[i + 1 :]
            break
    else:
        return None
    skip = False
    for t in tokens:
        if skip:
            skip = False
        elif t in OPTIONS_WITH_VALUE or t in ("--add-exports", "--add-opens"):
            skip = True
        elif t.startswith("-") or t.startswith("%") or t.startswith("$"):
            continue
        elif CLASS_RE.match(t):
            return t
        else:
            return None
    return None


def parse(path, is_batch):
    """Returns (flags, main classes, java line count) of a launcher script."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    flags, mains, java_lines = set(), set(), 0
    for line in logical_lines(text, "^" if is_batch else "\\"):
        if is_ignored(line, is_batch):
            continue
        for m in FLAG_RE.finditer(line):
            flags.add(normalize_flag(m.group(1)))
        if JAVA_CMD_RE.search(line):
            java_lines += 1
            cls = main_class(line)
            if cls:
                mains.add(cls)
    return flags, mains, java_lines


def check(root, found, errors):
    bin_dir = os.path.join(root, *BIN_DIR.split("/"))
    if not os.path.isdir(bin_dir):
        errors.append("%s not found: no launchers to compare" % BIN_DIR)
        return
    names = sorted(n for n in os.listdir(bin_dir) if os.path.isfile(os.path.join(bin_dir, n)))
    scripts = sorted(set(n[:-4] if n.endswith(".bat") else n for n in names))
    if not scripts:
        errors.append("%s holds no launcher scripts" % BIN_DIR)
    for name in scripts:
        rel = "bin/" + name
        if name not in names:
            errors.append("%s.bat has no shell twin %s" % (rel, rel))
            continue
        if name + ".bat" not in names:
            errors.append("%s has no batch twin %s.bat" % (rel, rel))
            continue
        sides = {}
        for side, fname, is_batch in (("sh", name, False), ("bat", name + ".bat", True)):
            try:
                sides[side] = parse(os.path.join(bin_dir, fname), is_batch)
            except (OSError, UnicodeDecodeError) as e:
                errors.append("%s/%s cannot be read: %s" % (BIN_DIR, fname, e))
        if len(sides) < 2:
            continue
        unparsed = False
        for side, (flags, _mains, java_lines) in sorted(sides.items()):
            if java_lines == 0:
                errors.append("%s (%s): no java command line found, cannot compare" % (rel, side))
                unparsed = True
            elif not flags:
                errors.append("%s (%s): no JVM flag found, cannot compare" % (rel, side))
                unparsed = True
        if unparsed:
            continue
        sh_flags, sh_mains, _ = sides["sh"]
        bat_flags, bat_mains, _ = sides["bat"]
        for flag in sh_flags - bat_flags:
            found.add("launcher-flag %s:%s:missing-in-bat" % (rel, flag))
        for flag in bat_flags - sh_flags:
            found.add("launcher-flag %s:%s:missing-in-sh" % (rel, flag))
        if sh_mains != bat_mains:
            found.add(
                "launcher-main %s:sh=%s:bat=%s" % (rel, ",".join(sorted(sh_mains)) or "-", ",".join(sorted(bat_mains)) or "-")
            )


def main(argv):
    parser = argparse.ArgumentParser(description="Launcher parity check (G9)")
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--baseline", default=DEFAULT_BASELINE)
    args = parser.parse_args(argv[1:])
    found, errors = set(), []
    check(args.root, found, errors)
    errors.extend(_baseline.compare(found, args.baseline))
    for e in errors:
        print("launchers: " + e)
    if errors:
        print(
            "launchers: %d problem(s). Pass the same flags in both launchers, or (for an existing "
            "difference) list it with a reason in scripts/guardrails/baselines/launchers.txt." % len(errors)
        )
        return 1
    print("launchers: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
