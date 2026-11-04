#!/usr/bin/env python3
"""Reject documented Gradle commands that succeed while doing nothing (gate G4).

Some tasks are guarded by `onlyIf` on a project property: invoked without it, Gradle marks them
SKIPPED and the build still reports BUILD SUCCESSFUL. A document that shows such a command teaches
readers and agents to treat a no-op as a passing run. Each rule below names the build-script guard
it mirrors; when a guard changes, change its rule in the same commit.

Commands are read from agent and contributor documents and shell scripts tracked by git; a command
continued with a trailing backslash is joined first.

Usage: check_doc_commands.py [REPO_ROOT]
"""

import os
import re
import subprocess
import sys

# (task pattern, required argument, why) — the task pattern matches a task path in a gradlew command.
RULES = (
    (
        re.compile(r"(?<![\w:-]):?integration-tests:test\b"),
        "-Pintegration",
        "integration-tests/build.gradle: test { onlyIf { project.hasProperty(\"integration\") } }"
        " skips the task without it",
    ),
)

SCOPE_PREFIXES = (".github/", ".agents/", "docs/", "scripts/")
SCOPE_FILES = ("AGENTS.md", "CLAUDE.md", "CONTRIBUTING.md", "README.md")
GRADLE_RE = re.compile(r"(?:^|[\s`(;&|])(?:\./)?gradlew(?:\.bat)?\s")


def tracked_files(root):
    out = subprocess.run(
        ["git", "-C", root, "ls-files", "-z"], check=True, capture_output=True
    ).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def in_scope(path):
    if not path.endswith((".md", ".sh")):
        return False
    return path in SCOPE_FILES or path.startswith(SCOPE_PREFIXES) or path.endswith(".sh")


def commands(path):
    """Yields (line number, logical line) with backslash continuations joined."""
    with open(path, encoding="utf-8", errors="replace") as f:
        start, parts = None, []
        for no, line in enumerate(f, 1):
            text = line.rstrip("\n")
            if start is None:
                start = no
            if text.rstrip().endswith("\\"):
                parts.append(text.rstrip()[:-1])
                continue
            parts.append(text)
            yield start, " ".join(parts)
            start, parts = None, []
        if parts:
            yield start, " ".join(parts)


def check(root):
    errors = []
    for path in tracked_files(root):
        if not in_scope(path):
            continue
        for no, line in commands(os.path.join(root, path)):
            for m in GRADLE_RE.finditer(line):
                # The command runs to the end of the inline code span, sentence or shell statement.
                rest = re.split(r"`|\s(?:&&|\|\||;|\|)\s|$", line[m.end():], maxsplit=1)[0]
                # Excluded tasks (-x / --exclude-task) are not invoked.
                invoked = re.sub(r"(?:^|\s)(?:-x|--exclude-task)(?:\s+|=)\S+", " ", rest)
                for task_re, required, why in RULES:
                    task = task_re.search(invoked)
                    if task and not re.search(r"(?<!\S)%s(?:[=\s]|$)" % re.escape(required), rest):
                        errors.append(
                            "%s:%d: '%s' without %s is skipped yet reports success (%s)"
                            % (path, no, task.group(0).strip(), required, why)
                        )
    return errors


def main(argv):
    errors = check(os.path.abspath(argv[1] if len(argv) > 1 else "."))
    for e in errors:
        print("doc-commands: " + e)
    if errors:
        print("doc-commands: %d command(s) would run nothing" % len(errors))
        return 1
    print("doc-commands: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
