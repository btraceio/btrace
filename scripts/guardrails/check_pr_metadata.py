#!/usr/bin/env python3
"""Check a pull request's title and description (gate G5).

PRs are squash-merged, so the title becomes the commit subject on develop. Fails when:
  - the title is not a Conventional Commit subject, `type(scope)!: summary`, with a type the
    history uses (TYPES below);
  - a human-authored PR's description has no verification content: a Testing / Tests / Test plan /
    How verified / Verification / Validation / Evidence section with at least one line that is not a template placeholder,
    an unticked checkbox, or an HTML comment;
  - the PR changes golden instrumentation data and the description does not say it was
    regenerated with -PupdateTestData (the data must never be edited by hand);
  - the PR changes exactly one of the two instrumentation backends (ASM, ClassFile API) and the
    description has no `Sibling: <reason>` line saying why the other needs no change. The line
    must be real text: inside an HTML comment or left as the template's `<...>` it does not count.
    The rule checks that a reason is given, not that it is adequate.

Bot-authored PRs (Renovate) are held to the title rule only (so also exempt from the Sibling rule).

Usage: check_pr_metadata.py EVENT_JSON [CHANGED_FILES]
  EVENT_JSON     the workflow's $GITHUB_EVENT_PATH; non-pull_request events pass trivially
  CHANGED_FILES  a file listing the PR's changed paths, one per line
"""

import json
import re
import sys

TYPES = ("feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore", "revert")
TITLE_RE = re.compile(r"^(%s)(\([\w./, -]+\))?!?: \S" % "|".join(TYPES))
SECTION_RE = re.compile(
    r"^\s*(?:#{1,6}\s*|\*\*)\s*(testing|tests|test plan|test results|how verified|how it was verified|verification|verified|validation|evidence)\b",
    re.I,
)
HEADING_RE = re.compile(r"^\s*#{1,6}\s")
# The two instrumentation backends (principle P4). A change to one is a change to both or says why not.
CLASSFILE_BACKEND = ("btrace-agent/src/main/java24/",)
# Exact paths. Regenerate with `python3 -I scripts/guardrails/derive_asm_backend.py`: the two seeds
# (Instrumentor, AsmInstrumentationBackend) plus every io/btrace/instr main-source file referenced
# only from files already in the set. Assembler.java and BTraceClassWriter.java are deliberately
# absent: backend-neutral code (MethodTrackingContext, InstrumentUtils) references them too.
# test_check_pr_metadata.py fails when this tuple differs from the derivation's output.
ASM_BACKEND = tuple(
    "btrace-agent/src/main/java/io/btrace/instr/%s.java" % name
    for name in (
        "ArrayAccessInstrumentor",
        "ArrayAllocInstrumentor",
        "AsmInstrumentationBackend",
        "BTraceMethodVisitor",
        "CatchInstrumentor",
        "ErrorReturnInstrumentor",
        "FieldAccessInstrumentor",
        "InstrumentingMethodVisitor",
        "Instrumentor",
        "LastFrameAtOffsetVisitor",
        "LineNumberInstrumentor",
        "MethodCallInstrumentor",
        "MethodEntryExitInstrumentor",
        "MethodEntryInstrumentor",
        "MethodInstrumentor",
        "MethodReturnInstrumentor",
        "ObjectAllocInstrumentor",
        "SynchronizedInstrumentor",
        "ThrowInstrumentor",
        "TypeCheckInstrumentor",
        "VariableMapper",
    )
)
SIBLING_RE = re.compile(r"^Sibling:\s*(\S.*)$", re.M)
# The template's unfilled prompt, `Sibling: <...>`, carries no reason.
SIBLING_PLACEHOLDER_RE = re.compile(r"^<[^<>]*>$")
GOLDEN_PREFIX = "btrace-agent/src/test/resources/instrumentorTestData/"
# Lines of .github/pull_request_template.md that carry no information when left as they are.
PLACEHOLDERS = (
    re.compile(r"^\s*$"),
    re.compile(r"^\s*```"),
    re.compile(r"^\s*-\s*\[ \]"),
    re.compile(r"^\s*\*\*commands and results:\*\*\s*$", re.I),
    re.compile(r"^\s*#\s*Example:\s*$"),
    re.compile(r"^\s*#\s*GRADLE_USER_HOME=\$\(pwd\)/\.gradle-user \./gradlew :(?:<module>|module):test\s*$"),
)


def verification_lines(body):
    """Informative lines inside the verification section(s)."""
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    lines, inside, fenced = [], False, False
    for line in body.splitlines():
        if re.match(r"^\s*(```|~~~)", line):
            fenced = not fenced
            continue
        if fenced:
            # Shell comments inside code blocks are not Markdown headings.
            if inside and not any(p.match(line) for p in PLACEHOLDERS):
                lines.append(line)
            continue
        if SECTION_RE.match(line):
            inside = True
            rest = re.sub(r"^\s*\*\*[^*]*\*\*:?", "", line).strip()
            if rest and not line.lstrip().startswith("#"):
                lines.append(rest)
            continue
        if inside and HEADING_RE.match(line):
            inside = False
        if inside and not any(p.match(line) for p in PLACEHOLDERS):
            lines.append(line)
    return lines


def sibling_reason(body):
    """The text after a `Sibling:` line outside HTML comments, or None."""
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    for m in SIBLING_RE.finditer(body):
        if not SIBLING_PLACEHOLDER_RE.match(m.group(1).strip()):
            return m.group(1).strip()
    return None


def one_sided_backend_change(changed):
    """('ASM', 'ClassFile API') or the reverse when exactly one backend changed, else None."""
    asm = any(p in ASM_BACKEND for p in changed)
    classfile = any(p.startswith(CLASSFILE_BACKEND) for p in changed)
    if asm and not classfile:
        return ("ASM", "ClassFile API")
    if classfile and not asm:
        return ("ClassFile API", "ASM")
    return None


def check(event, changed):
    pr = event.get("pull_request")
    if pr is None:
        return []
    errors = []
    title = pr.get("title") or ""
    if not TITLE_RE.match(title):
        errors.append(
            "title %r is not a Conventional Commit subject: use 'type(scope): summary' with type one "
            "of %s; it becomes the squash-merge commit subject" % (title, ", ".join(TYPES))
        )
    if (pr.get("user") or {}).get("type") == "Bot":
        return errors
    body = pr.get("body") or ""
    if not verification_lines(body):
        errors.append(
            "the description has no verification content: fill the Testing section (or a "
            "'How verified' section) with the commands run and their result lines"
        )
    if any(p.startswith(GOLDEN_PREFIX) for p in changed) and "updateTestData" not in body:
        errors.append(
            "golden instrumentation data changed (%s); say in the description that it was "
            "regenerated with ./gradlew test -PupdateTestData and why the bytecode changed"
            % GOLDEN_PREFIX
        )
    sides = one_sided_backend_change(changed)
    if sides and sibling_reason(body) is None:
        errors.append(
            "this PR changes the %s instrumentation backend but not the %s one; add a line "
            "'Sibling: <reason>' to the description saying why the %s backend needs no "
            "change (or change it too)" % (sides + (sides[1],))
        )
    return errors


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    with open(argv[1], encoding="utf-8") as f:
        event = json.load(f)
    changed = []
    if len(argv) > 2:
        with open(argv[2], encoding="utf-8") as f:
            changed = [line.strip() for line in f if line.strip()]
    if event.get("pull_request") is None:
        print("pr-metadata: not a pull_request event; nothing to check")
        return 0
    errors = check(event, changed)
    for e in errors:
        print("pr-metadata: " + e)
    if errors:
        print("pr-metadata: %d problem(s); edit the PR title or description to re-run" % len(errors))
        return 1
    print("pr-metadata: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
