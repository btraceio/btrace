#!/usr/bin/env python3
"""Every message key used in main code exists in messages.properties (gate G12, message keys).

Keys are looked up through `Messages.get("key")` and `Messages.format("key", ...)` (btrace-core), and
through the two verifier wrappers that pass their first argument on to `Messages.get`:
`reportError("key"...)` and `reportSafetyError("key"...)`. Only a string literal that is the whole
first argument (followed by `,` or `)`) is a key; a key built by concatenation or held in a variable
cannot be checked and is ignored. Comments are removed before matching and a call inside a string
literal does not count. Only `src/main/java*` source sets are scanned.

Each of these is an error, never a pass: messages.properties cannot be read or has no keys, a source
file is not UTF-8, or no key literal is found at all (the scan would then have checked nothing).

Usage:
  check_message_keys.py [ROOT]
"""

import os
import re
import sys

PROPS = "btrace-core/src/main/resources/io/btrace/core/messages.properties"
SKIP_DIRS = {".git", "build", "node_modules", ".claude", ".superpowers", ".idea"}

# Comments are blanked; string literals stay so a key can be read, and a call-looking text inside a
# string is skipped because the whole literal is consumed as one token.
TOKEN = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
CALL = re.compile(r"\b(?:Messages\s*\.\s*(?:get|format)|reportError|reportSafetyError)\s*\(\s*\x00(\d+)\x00\s*[,)]")


class CheckError(Exception):
    pass


def read_text(path, rel):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError as e:
        raise CheckError("cannot read %s: %s" % (rel, e.strerror or e))
    except UnicodeDecodeError:
        raise CheckError("%s is not valid UTF-8" % rel)


def property_keys(root):
    lines = read_text(os.path.join(root, PROPS), PROPS).splitlines()
    keys, continued = set(), False
    for line in lines:
        if continued:
            continued = (len(line) - len(line.rstrip("\\"))) % 2 == 1
            continue
        stripped = line.lstrip()
        if not stripped or stripped[0] in "#!":
            continue
        continued = (len(line) - len(line.rstrip("\\"))) % 2 == 1
        key = re.match(r"((?:\\.|[^=:\s\\])+)", stripped)
        if key:
            keys.add(re.sub(r"\\(.)", r"\1", key.group(1)))
    if not keys:
        raise CheckError("%s has no keys" % PROPS)
    return keys


def literal_keys(text):
    """Returns the key literals of the calls in text, comments removed, as (key, line) pairs."""
    literals = []

    def repl(m):
        tok = m.group(0)
        if tok.startswith("//") or tok.startswith("/*"):
            return re.sub(r"[^\n]", " ", tok)
        if tok[0] == '"':
            literals.append(tok[1:-1])
            return "\x00%d\x00" % (len(literals) - 1) + "\n" * tok.count("\n")
        return "''"

    code = TOKEN.sub(repl, text)
    return [(literals[int(m.group(1))], code.count("\n", 0, m.start()) + 1) for m in CALL.finditer(code)]


def main_sources(root):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".gradle")]
        rel_dir = os.path.relpath(dirpath, root).replace(os.sep, "/")
        parts = rel_dir.split("/")
        for i in range(len(parts) - 1):
            if parts[i] == "src" and parts[i + 1] == "main":
                break
        else:
            continue
        idx = parts.index("main", i + 1)
        if len(parts) <= idx + 1 or not parts[idx + 1].startswith("java"):
            continue
        for name in sorted(filenames):
            if name.endswith(".java"):
                yield (rel_dir + "/" + name) if rel_dir != "." else name


def check(root):
    keys = property_keys(root)
    problems, total, used = [], 0, set()
    for rel in main_sources(root):
        for key, line in literal_keys(read_text(os.path.join(root, rel), rel)):
            total += 1
            used.add(key)
            if key not in keys:
                problems.append("%s:%d: message key \"%s\" is not in messages.properties" % (rel, line, key))
    if total == 0:
        raise CheckError("no message key literals found in any src/main/java* source: nothing was checked")
    return total, len(used), problems


def main(argv):
    root = argv[1] if len(argv) > 1 else "."
    try:
        total, distinct, problems = check(root)
    except CheckError as e:
        print("message-keys: " + str(e))
        return 1
    for p in problems:
        print("message-keys: " + p)
    if problems:
        print("message-keys: %d problem(s). Add the key to %s." % (len(problems), PROPS))
        return 1
    print("message-keys: OK (%d key literals, %d distinct)" % (total, distinct))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
