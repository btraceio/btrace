#!/usr/bin/env python3
"""Both instrumentation backends dispatch every probe Kind (gate G9, probe kinds).

The constants of the `Kind` enum are read from btrace-core. Each one must appear

  ASM backend         as a `case <NAME>` label of the `switch (loc.getValue())` in Instrumentor.java.
                      Only labels of that switch's own body count (labels of a switch nested in one
                      of its arms do not); several labels in one group or fallthrough are fine.
  ClassFile backend   as `Kind.<NAME>` in the handler-partition loop of ClassFileApiBackend.java: the
                      `for` loop whose body assigns `Kind <v> = ...getLocation().getValue();`. The
                      mention must be a comparison with that variable (`<v> == Kind.<NAME>`).

Comments and string literals are removed before matching, so a kind named only there does not count.

Each of these is an error, never a pass: the Kind file or its constant list cannot be found or parsed,
an anchor is missing or appears more than once, or the source cannot be read as UTF-8.

Usage:
  check_kind_dispatch.py [ROOT]
"""

import os
import re
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import check_fqn  # noqa: E402

KIND_FILE = "btrace-core/src/main/java/io/btrace/core/annotations/Kind.java"
ASM_FILE = "btrace-agent/src/main/java/io/btrace/instr/Instrumentor.java"
CLASSFILE_FILE = "btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java"

IDENT = re.compile(r"[A-Za-z_]\w*")
SWITCH_RE = re.compile(r"\bswitch\s*\(\s*loc\s*\.\s*getValue\s*\(\s*\)\s*\)\s*\{")
CASE_RE = re.compile(r"\bcase\s+([A-Za-z_]\w*)\s*:")
ASSIGN_RE = re.compile(r"\bKind\s+(\w+)\s*=\s*[\w.()\s]*?\.\s*getLocation\s*\(\s*\)\s*\.\s*getValue\s*\(\s*\)\s*;")
ENUM_RE = re.compile(r"\benum\s+Kind\s*\{")


class CheckError(Exception):
    pass


def read_code(root, rel):
    """Returns the file's code with comments and string/char literals removed (line structure kept)."""
    path = os.path.join(root, rel)
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        raise CheckError("cannot read %s: %s" % (rel, e.strerror or e))
    except UnicodeDecodeError:
        raise CheckError("%s is not valid UTF-8" % rel)
    state, out = check_fqn.CODE, []
    for line in text.splitlines():
        code, state = check_fqn.strip_code(line, state)
        out.append(code)
    return "\n".join(out)


def matching_brace(code, open_index, rel, what):
    depth = 0
    for i in range(open_index, len(code)):
        if code[i] == "{":
            depth += 1
        elif code[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    raise CheckError("%s: the block of %s is not closed" % (rel, what))


def kind_constants(code):
    m = ENUM_RE.search(code)
    if not m:
        raise CheckError("%s: no `enum Kind {` found" % KIND_FILE)
    start = m.end()
    end = matching_brace(code, m.end() - 1, KIND_FILE, "enum Kind")
    body, depth, cut = code[start:end], 0, None
    for i, ch in enumerate(body):
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
        elif ch == ";" and depth == 0:
            cut = i
            break
    constants_text = body if cut is None else body[:cut]
    names, depth, item = [], 0, ""
    for ch in constants_text + ",":
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
        if ch == "," and depth == 0:
            item = item.strip()
            if item:
                name = IDENT.match(item)
                if not name:
                    raise CheckError("%s: cannot parse enum constant %r" % (KIND_FILE, item))
                names.append(name.group(0))
            item = ""
        else:
            item += ch
    if not names:
        raise CheckError("%s: enum Kind has no constants" % KIND_FILE)
    if len(set(names)) != len(names):
        raise CheckError("%s: enum Kind lists a constant twice" % KIND_FILE)
    return names


def asm_cases(code):
    found = list(SWITCH_RE.finditer(code))
    if len(found) != 1:
        raise CheckError(
            "%s: expected exactly one `switch (loc.getValue())`, found %d" % (ASM_FILE, len(found))
        )
    start = found[0].end()
    end = matching_brace(code, start - 1, ASM_FILE, "switch (loc.getValue())")
    body, depth, top = code[start:end], 0, []
    for ch in body:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        top.append(ch if depth == 0 else " ")
    return set(CASE_RE.findall("".join(top)))


def classfile_kinds(code):
    found = list(ASSIGN_RE.finditer(code))
    if len(found) != 1:
        raise CheckError(
            "%s: expected exactly one `Kind <v> = ...getLocation().getValue();` handler-partition loop, found %d"
            % (CLASSFILE_FILE, len(found))
        )
    var = found[0].group(1)
    depth, open_index = 0, None
    for i in range(found[0].start() - 1, -1, -1):
        if code[i] == "}":
            depth += 1
        elif code[i] == "{":
            if depth == 0:
                open_index = i
                break
            depth -= 1
    if open_index is None or not re.search(r"\bfor\s*\([^;{}]*\)\s*$", code[:open_index]):
        raise CheckError(
            "%s: the `Kind %s = ...getValue();` statement is not directly inside a `for` loop" % (CLASSFILE_FILE, var)
        )
    end = matching_brace(code, open_index, CLASSFILE_FILE, "the handler-partition loop")
    body = code[open_index:end]
    v = re.escape(var)
    pattern = r"\b%s\s*==\s*Kind\s*\.\s*(\w+)|\bKind\s*\.\s*(\w+)\s*==\s*%s\b" % (v, v)
    return {a or b for a, b in re.findall(pattern, body)}


def check(root):
    """Returns (kinds, problems)."""
    kinds = kind_constants(read_code(root, KIND_FILE))
    asm = asm_cases(read_code(root, ASM_FILE))
    classfile = classfile_kinds(read_code(root, CLASSFILE_FILE))
    problems = []
    for name in kinds:
        if name not in asm:
            problems.append(
                "Kind.%s has no `case %s` in the `switch (loc.getValue())` of %s" % (name, name, ASM_FILE)
            )
        if name not in classfile:
            problems.append(
                "Kind.%s is not compared in the handler-partition loop of %s" % (name, CLASSFILE_FILE)
            )
    return kinds, problems


def main(argv):
    root = argv[1] if len(argv) > 1 else "."
    try:
        kinds, problems = check(root)
    except CheckError as e:
        print("kind-dispatch: " + str(e))
        return 1
    for p in problems:
        print("kind-dispatch: " + p)
    if problems:
        print(
            "kind-dispatch: %d problem(s). Handle the kind in both backends (see "
            "docs/architecture/InstrumentationBackends.md)." % len(problems)
        )
        return 1
    print("kind-dispatch: OK (%d kinds, both sides)" % len(kinds))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
