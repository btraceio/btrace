#!/usr/bin/env python3
"""Keep the BTrace module layering fixed (gate G6).

Read from source and build scripts alone, no build needed:

  edge           A `project(...)` dependency in a build.gradle whose `from -> to` pair is not in the
                 `edges` list of layering_rules.json. The list must also equal the declared edges, so
                 an allowed edge nobody declares any more is reported as stale.
  import         A Java `import` (single-type, static or wildcard) of a forbidden package prefix.
  literal        A string literal naming a class of a higher layer, e.g. "io.btrace.agent.Foo".
  split-package  The same Java package directory in the main sources of two modules.

Violations that predate the rule live in baselines/layering.txt (see _baseline.py): the check
fails on a violation that is not listed and on a listed one that is gone. Rules, allowed edges and
per-file exceptions live in layering_rules.json; an exception that no longer suppresses anything is
reported as stale, as the baseline is.

Imports and literals are read with _common.strip_code, so names in comments, Javadoc and (for
imports) string literals are ignored. Every source set under src/<set>/java* is scanned, which
includes the java9, java11 and java24 roots and the api and impl sets of extensions. Text-block
contents are not scanned for literals.

Every `project(':path')` or `project(path: ':path', ...)` anywhere in a build.gradle (comments
removed) is an edge, so a dependency written outside a `dependencies { }` block is still checked; a
reference to the project itself is not. A `project(<expression>)` that is not a literal path is an
error unless layering_rules.json lists that file and expression under `dynamic_project_references`
with a reason; an entry that matches nothing is stale.

Baseline entries for imports and literals name the full imported or literal class name (a wildcard
import as written, `io.btrace.agent.*`), so a baselined file cannot gain a second violation under
the same prefix unnoticed. Per-file `exceptions` list those same full names.

Usage:
  check_layering.py [ROOT] [--rules FILE] [--baseline FILE]
"""

import argparse
import json
import os
import re
import sys

# CI runs checks as `python3 -I`, which drops the script directory from sys.path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import _baseline  # noqa: E402
from _common import strip_lines  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_RULES = os.path.join(HERE, "layering_rules.json")
DEFAULT_BASELINE = os.path.join(HERE, "baselines", "layering.txt")

# <module>/src/<source set>/<java root>/<package dirs>/<File>.java
JAVA_PATH_RE = re.compile(r"^(?P<module>.+?)/src/(?P<set>[^/]+)/(?P<root>java\d*)/(?:(?P<pkg>.*)/)?[^/]+\.java$")
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w$.]+(?:\.\*)?)\s*;")
PROJECT_RE = re.compile(r"\bproject\s*\(")
PROJECT_ARG_RE = re.compile(r"^\s*(?:path\s*:\s*)?(['\"])(:[\w:.-]+)\1\s*(?:,[^()]*)?$")
# Source sets whose packages must not be shared with another module (not tests, benchmarks, mocks).
MAIN_SETS = ("main", "api", "impl")
SKIP_DIRS = ("build", "node_modules", "out")


class RuleError(Exception):
    pass


def walk(root, separate_builds):
    """Yields (repo-relative path, file name) for files, skipping hidden, build and separate-build dirs."""
    for dirpath, dirnames, filenames in os.walk(root):
        rel_dir = os.path.relpath(dirpath, root).replace(os.sep, "/")
        dirnames[:] = sorted(
            d
            for d in dirnames
            if not d.startswith(".")
            and d not in SKIP_DIRS
            and (rel_dir == "." and d or rel_dir + "/" + d) not in separate_builds
        )
        for name in sorted(filenames):
            yield (name if rel_dir == "." else rel_dir + "/" + name), name


def has_prefix(name, prefix):
    return name == prefix or name.startswith(prefix + ".")


def applies(rule, module, pkg, source_set):
    target = rule["module"]
    if not (module == target or module.startswith(target + "/")):
        return False
    if "package" in rule and not has_prefix((pkg or "").replace("/", "."), rule["package"]):
        return False
    return "source_sets" not in rule or source_set in rule["source_sets"]


def strip_gradle_comments(text):
    """Returns text without // and /* */ comments; quoted strings are kept, newlines preserved."""
    out, i, n = [], 0, len(text)
    while i < n:
        two = text[i : i + 2]
        if text.startswith(('"""', "'''"), i):
            end = text.find(text[i : i + 3], i + 3)
            end = n if end < 0 else end + 3
            out.append(text[i:end])
            i = end
        elif text[i] in "\"'":
            j = i + 1
            while j < n and text[j] != text[i] and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(text[i : j + 1])
            i = j + 1
        elif two == "//":
            while i < n and text[i] != "\n":
                i += 1
        elif two == "/*":
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append("\n" * text.count("\n", i, end))
            i = end
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def call_argument(text, start):
    """Returns the text between the parentheses of the call whose '(' is at text[start - 1]."""
    depth, i = 1, start
    while i < len(text) and depth:
        if text[i] in "\"'":
            i = text.find(text[i], i + 1)
            i = len(text) if i < 0 else i
        elif text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
        i += 1
    return text[start : i - 1] if depth == 0 else None


def project_references(path, text):
    """Returns ([(from, to)], [(expression, line)]) for every project(...) in one build.gradle."""
    owner = os.path.dirname(path).replace("/", ":")
    text = strip_gradle_comments(text)
    edges, dynamic = [], []
    for m in PROJECT_RE.finditer(text):
        arg = call_argument(text, m.end())
        line = text.count("\n", 0, m.start()) + 1
        literal = PROJECT_ARG_RE.match(arg) if arg is not None else None
        if literal:
            edges.append((owner, literal.group(2).lstrip(":")))
        else:
            dynamic.append(((arg or "").strip(), line))
    return edges, dynamic


def check_edges(root, rules, found, errors):
    declared, used = set(), set()
    allowed_dynamic = {(d["file"], d["expression"]) for d in rules.get("dynamic_project_references", [])}
    for path, name in walk(root, rules.get("separate_builds", [])):
        if name != "build.gradle":
            continue
        with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
            edges, dynamic = project_references(path, f.read())
        for expression, line in dynamic:
            if (path, expression) in allowed_dynamic:
                used.add((path, expression))
            else:
                errors.append(
                    "cannot parse project reference in %s:%d: project(%s); use a literal path or list it under "
                    "dynamic_project_references in layering_rules.json" % (path, line, expression)
                )
        for pair in edges:
            if pair[0] == pair[1]:
                continue
            declared.add(pair)
            if list(pair) not in rules["edges"]:
                found.add("edge %s:%s->%s" % (path, pair[0], pair[1]))
    for path, expression in sorted(allowed_dynamic - used):
        errors.append(
            "stale dynamic_project_references entry (remove it from layering_rules.json): %s project(%s)"
            % (path, expression)
        )
    for a, b in rules["edges"]:
        if (a, b) not in declared:
            errors.append(
                "allowed edge is not declared in any build.gradle (remove it from layering_rules.json): %s->%s" % (a, b)
            )


def literal_matches(literal, prefix):
    """Returns the class names in a literal that start with the dotted prefix; '/' counts as '.'."""
    pattern = r"(?<![\w$])%s(?![\w$])(?:\.[\w$]+)*" % re.escape(prefix)
    return [m.group(0) for m in re.finditer(pattern, literal.replace("/", "."))]


def scan_java(root, rules, found, errors):
    exceptions_used = set()
    packages = {}
    for path, _ in walk(root, rules.get("separate_builds", [])):
        match = JAVA_PATH_RE.match(path) if path.endswith(".java") else None
        if not match:
            continue
        module, source_set, pkg = match.group("module"), match.group("set"), match.group("pkg") or ""
        if source_set in MAIN_SETS and pkg:
            packages.setdefault(pkg, {}).setdefault(module, []).append(path.rsplit("/", 1)[0])
        imports_rules = [r for r in rules["imports"] if applies(r, module, pkg, source_set)]
        literal_rules = [r for r in rules["upper_layer_literals"] if applies(r, module, pkg, source_set)]
        if not imports_rules and not literal_rules:
            continue
        with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()
        per_line = []
        for code, literals in zip(strip_lines(lines, per_line), per_line):
            imported = IMPORT_RE.match(code)
            if imported:
                for rule in imports_rules:
                    flag_import(rule, path, imported.group(1), found, exceptions_used, "import")
            for text in literals:
                for rule in literal_rules:
                    flag_literal(rule, path, text, found, exceptions_used)
    for pkg, by_module in sorted(packages.items()):
        if len(by_module) > 1:
            first, *others = sorted(by_module)
            found.add("split-package %s:%s" % (sorted(by_module[first])[0], ",".join(others)))
    check_stale_exceptions(rules, root, exceptions_used, errors)


def flag_import(rule, path, name, found, used, rule_id):
    bare = name[:-2] if name.endswith(".*") else name
    for prefix in rule["forbidden"]:
        if not has_prefix(bare, prefix) or any(has_prefix(bare, a) for a in rule.get("allow", [])):
            continue
        if name in rule.get("exceptions", {}).get(path, []):
            used.add((id(rule), path, name))
        else:
            found.add("%s %s:%s" % (rule_id, path, name))


def flag_literal(rule, path, text, found, used):
    for prefix in rule["forbidden"]:
        for name in literal_matches(text, prefix):
            if name in rule.get("exceptions", {}).get(path, []):
                used.add((id(rule), path, name))
            else:
                found.add("literal %s:%s" % (path, name))


def check_stale_exceptions(rules, root, used, errors):
    for section in ("imports", "upper_layer_literals"):
        for rule in rules[section]:
            for path, names in sorted(rule.get("exceptions", {}).items()):
                for name in names:
                    if (id(rule), path, name) not in used:
                        errors.append(
                            "stale exception (remove it from layering_rules.json): %s allows %s but does not need to"
                            % (path, name)
                        )


def load_rules(path):
    try:
        with open(path, encoding="utf-8") as f:
            rules = json.load(f)
        rules.setdefault("imports", [])
        rules.setdefault("upper_layer_literals", [])
        rules.setdefault("edges", [])
        rules.setdefault("dynamic_project_references", [])
        return rules
    except (OSError, ValueError) as e:
        raise RuleError("cannot read rules %s: %s" % (path, e))


def main(argv):
    parser = argparse.ArgumentParser(description="Module layering check (G6)")
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--rules", default=DEFAULT_RULES)
    parser.add_argument("--baseline", default=DEFAULT_BASELINE)
    args = parser.parse_args(argv[1:])
    try:
        rules = load_rules(args.rules)
    except RuleError as e:
        print("layering: " + str(e))
        return 1
    found, errors = set(), []
    check_edges(args.root, rules, found, errors)
    scan_java(args.root, rules, found, errors)
    try:
        errors.extend(_baseline.compare(found, args.baseline))
    except _baseline.BaselineError as e:
        errors.append(str(e))
    for e in errors:
        print("layering: " + e)
    if errors:
        print(
            "layering: %d problem(s). Fix the dependency, or (for an existing violation or a deliberate "
            "exception) update scripts/guardrails/baselines/layering.txt or layering_rules.json with a reason." % len(errors)
        )
        return 1
    print("layering: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
