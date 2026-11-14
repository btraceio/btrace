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

Imports and literals are read with check_fqn.strip_code, so names in comments, Javadoc and (for
imports) string literals are ignored. Every source set under src/<set>/java* is scanned, which
includes the java9, java11 and java24 roots and the api and impl sets of extensions. Text-block
contents are not scanned for literals.

A project reference is a dependency edge only inside a `dependencies { }` block; elsewhere
(`def p = project(':x')`, `dependsOn project(...)`) it is task wiring. Inside a `dependencies { }`
block every `project(` must be `project(':path')` or `project(path: ':path', ...)`; anything else is
an error rather than silently skipped.

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
import check_fqn  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_RULES = os.path.join(HERE, "layering_rules.json")
DEFAULT_BASELINE = os.path.join(HERE, "baselines", "layering.txt")

# <module>/src/<source set>/<java root>/<package dirs>/<File>.java
JAVA_PATH_RE = re.compile(r"^(?P<module>.+?)/src/(?P<set>[^/]+)/(?P<root>java\d*)/(?:(?P<pkg>.*)/)?[^/]+\.java$")
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w$.]+?)(?:\.\*)?\s*;")
DEPENDENCIES_RE = re.compile(r"\bdependencies\s*\{")
PROJECT_RE = re.compile(r"\bproject\s*\(")
PROJECT_ARG_RE = re.compile(r"project\s*\(\s*(?:path\s*:\s*)?(['\"])(:[\w:.-]+)\1\s*(?:,[^()]*)?\)")
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


def project_edges(path, text):
    """Returns ([(from, to)], [error]) for the project dependencies declared in one build.gradle."""
    owner = os.path.dirname(path).replace("/", ":")
    edges, errors, depth = [], [], 0
    for no, raw in enumerate(text.splitlines(), 1):
        line = re.sub(r"//.*", "", raw)
        if depth == 0:
            match = DEPENDENCIES_RE.search(line)
            if not match:
                continue
            line, depth = line[match.start() :], 0
        for m in PROJECT_RE.finditer(line):
            arg = PROJECT_ARG_RE.match(line, m.start())
            if arg:
                edges.append((owner, arg.group(2).lstrip(":")))
            else:
                errors.append("cannot parse project reference in %s:%d: %s" % (path, no, raw.strip()))
        depth += line.count("{") - line.count("}")
    return edges, errors


def check_edges(root, rules, found, errors):
    declared = set()
    for path, name in walk(root, rules.get("separate_builds", [])):
        if name != "build.gradle":
            continue
        with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
            edges, parse_errors = project_edges(path, f.read())
        errors.extend(parse_errors)
        for pair in edges:
            declared.add(pair)
            if list(pair) not in rules["edges"]:
                found.add("edge %s:%s->%s" % (path, pair[0], pair[1]))
    for a, b in rules["edges"]:
        if (a, b) not in declared:
            errors.append("allowed edge is not declared in any build.gradle (remove it from layering_rules.json): %s->%s" % (a, b))


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
        state = check_fqn.CODE
        for line in lines:
            literals = []
            code, state = check_fqn.strip_code(line, state, literals)
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
    for prefix in rule["forbidden"]:
        if not has_prefix(name, prefix) or any(has_prefix(name, a) for a in rule.get("allow", [])):
            continue
        if prefix in rule.get("exceptions", {}).get(path, []):
            used.add((id(rule), path, prefix))
        else:
            found.add("%s %s:%s" % (rule_id, path, prefix))


def flag_literal(rule, path, text, found, used):
    for prefix in rule["forbidden"]:
        for name in literal_matches(text, prefix):
            if prefix in rule.get("exceptions", {}).get(path, []):
                used.add((id(rule), path, prefix))
            else:
                found.add("literal %s:%s" % (path, name))


def check_stale_exceptions(rules, root, used, errors):
    for section in ("imports", "upper_layer_literals"):
        for rule in rules[section]:
            for path, prefixes in sorted(rule.get("exceptions", {}).items()):
                for prefix in prefixes:
                    if (id(rule), path, prefix) not in used:
                        errors.append(
                            "stale exception (remove it from layering_rules.json): %s allows %s but does not need to"
                            % (path, prefix)
                        )


def load_rules(path):
    try:
        with open(path, encoding="utf-8") as f:
            rules = json.load(f)
        rules.setdefault("imports", [])
        rules.setdefault("upper_layer_literals", [])
        rules.setdefault("edges", [])
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
    errors.extend(_baseline.compare(found, args.baseline))
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
